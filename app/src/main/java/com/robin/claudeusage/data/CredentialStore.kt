package com.robin.claudeusage.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.robin.claudeusage.diag.AppLog
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Credentials(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long, // epoch millis; 0 = unknown
    /** ChatGPT sends this back as a header (CCRM-53 (Provider Model)); Claude never has one. */
    val accountId: String? = null,
)

/**
 * Android Keystore-backed storage for the OAuth tokens, one slot per profile.
 *
 * CCBG-46 (Keystore Wedge): opening the store used to throw straight out of the
 * constructor, on every hot path, so a wedged Keystore or an undecryptable file was a
 * crash at every launch. The open now goes through [CredentialStoreOpener], which
 * decides once per process between the real store, a reset one, and an empty
 * in-memory one. Only [resetAllowed] callers may reset — the main screen's repository,
 * never a worker, the pinned service or anything a `BOOT_COMPLETED` starts.
 */
class CredentialStore internal constructor(
    context: Context,
    resetAllowed: Boolean,
    private val backend: CredentialBackend,
) {

    constructor(context: Context, resetAllowed: Boolean = false) :
        this(context, resetAllowed, KeystoreBackend)

    private val appContext = context.applicationContext

    init {
        // The one open with this caller's reset rights: an app launch re-checks a degraded store here.
        CredentialStoreOpener.open(appContext, resetAllowed, backend)
    }

    // Asked on every call, never with reset rights, so a store built while the Keystore was
    // unavailable follows the process onto the real store once it opens — the pinned service
    // keeps its repository for as long as it runs. Each method reads it once, so one call
    // never mixes two stores.
    private val prefs: SharedPreferences
        get() = CredentialStoreOpener.open(appContext, false, backend)

    // v0.5 and earlier stored the single (personal) token without a prefix. Restated as a
    // key comparison for CCRM-6 (Multi-Account), now that Profile is a value type — the
    // legacy key is a storage-format constant, so this exception outlives the enum.
    private fun k(profile: Profile, name: String): String =
        if (profile.key == Profile.LEGACY_KEY) name else "${profile.key}.$name"

    fun load(profile: Profile): Credentials? {
        val prefs = prefs
        val access = prefs.getString(k(profile, "accessToken"), null) ?: return null
        val refresh = prefs.getString(k(profile, "refreshToken"), null) ?: return null
        return Credentials(
            access, refresh,
            prefs.getLong(k(profile, "expiresAt"), 0L),
            prefs.getString(k(profile, "accountId"), null),
        )
    }

    /**
     * stampAdded is true only when the user pastes a token; silent rotations
     * during background refresh keep the original added date and tail label.
     */
    fun save(profile: Profile, creds: Credentials, stampAdded: Boolean = false) {
        val prefs = prefs
        val e = prefs.edit()
            .putString(k(profile, "accessToken"), creds.accessToken)
            .putString(k(profile, "refreshToken"), creds.refreshToken)
            .putLong(k(profile, "expiresAt"), creds.expiresAt)
        if (creds.accountId != null) e.putString(k(profile, "accountId"), creds.accountId)
        else e.remove(k(profile, "accountId"))
        if (stampAdded) {
            e.putLong(k(profile, "addedAt"), System.currentTimeMillis())
            e.putString(k(profile, "tokenTail"), creds.accessToken.takeLast(4))
        }
        e.apply()
        // CCBG-50 (Degraded Store Notice): the next working launch says what was dropped.
        if (CredentialStoreOpener.isMemory(prefs)) HeldChanges.record(appContext, profile.key, HeldChanges.Op.SIGN_IN)
    }

    fun addedAt(profile: Profile): Long = prefs.getLong(k(profile, "addedAt"), 0L)

    fun tokenTail(profile: Profile): String? = prefs.getString(k(profile, "tokenTail"), null)

    fun clear(profile: Profile) {
        val prefs = prefs
        prefs.edit()
            .remove(k(profile, "accessToken"))
            .remove(k(profile, "refreshToken"))
            .remove(k(profile, "expiresAt"))
            .remove(k(profile, "accountId"))
            .remove(k(profile, "addedAt"))
            .remove(k(profile, "tokenTail"))
            .apply()
        if (CredentialStoreOpener.isMemory(prefs)) HeldChanges.record(appContext, profile.key, HeldChanges.Op.SIGN_OUT)
    }

    companion object {
        const val FILE_NAME = "secure_credentials"
    }
}

/** The two things a test must be able to replace: the encrypted open, and the key delete. */
internal interface CredentialBackend {
    /** Opens (or creates) the encrypted store. Throws whatever the Keystore throws. */
    fun open(context: Context): SharedPreferences

    /** Deletes the Keystore entry the store is sealed with. */
    fun deleteMasterKey()
}

internal object KeystoreBackend : CredentialBackend {
    @Suppress("DEPRECATION")
    override fun open(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            CredentialStore.FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // CredentialStore is the only user of this alias (checked 2026-09-27), so deleting it
    // invalidates no other store.
    override fun deleteMasterKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
    }
}

/**
 * CCBG-46 (Keystore Wedge): the one place that decides what a failed open means.
 *
 * - **Permanent** — an `AEADBadTagException` or a `KeyPermanentlyInvalidatedException`
 *   in the cause chain of the store's own open or decrypt. A [resetAllowed] caller
 *   deletes the file and the master key, rebuilds both and proves them with a
 *   write/read probe. Any other caller leaves everything alone and defers the reset to
 *   the next app open.
 * - **Everything else** — `UserNotAuthenticatedException`, a locked device, any other
 *   `InvalidKeyException` or `KeyStoreException`, or a transient error still failing
 *   after one retry: this process runs on an empty in-memory store and the file is
 *   left untouched.
 *
 * The outcome is kept for the process: a healthy store forever, a degraded one until
 * the next app open (a [resetAllowed] caller) tries again. There is at most one reset
 * per process — a probe that fails after it falls back to memory, never to a second wipe.
 *
 * CCBG-50 (Degraded Store Notice): [state] is what the notice draws, published on every
 * outcome and on the first write to the in-memory store.
 */
internal object CredentialStoreOpener {

    /** What the notice and the Debug card read. [version] moves on every change. */
    data class State(
        val healthy: Boolean = true,
        val simulated: Boolean = false,
        /** A reset ran this process and its probe failed: nothing is left to try. */
        val resetFailed: Boolean = false,
        /** The failure's class name, never its message. */
        val errorName: String? = null,
        val checkedAt: Long = 0L,
        /** When the in-memory store first took a write; 0 while it holds nothing. */
        val heldSince: Long = 0L,
        val version: Int = 0,
    ) {
        val degraded: Boolean get() = !healthy
        val held: Boolean get() = heldSince > 0L
        /** Try again: only while nothing is held, and never after a failed reset. */
        val canRetry: Boolean get() = degraded && !held && !resetFailed
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    enum class Failure { PERMANENT, PRESERVE }

    /**
     * [deferred]: a permanent failure a caller without reset rights left alone.
     * [at]: when it was worked out, for the retry spacing.
     */
    private class Outcome(
        val prefs: SharedPreferences,
        val healthy: Boolean,
        val deferred: Boolean = false,
        val at: Long = clock(),
        val error: String? = null,
        val simulated: Boolean = false,
    )

    private var outcome: Outcome? = null
    private var resetDone = false
    private var resetFailed = false

    /** Tests move time; production reads the wall clock. */
    @Volatile internal var clock: () -> Long = System::currentTimeMillis

    /** One in-memory store for the whole process, so every degraded caller sees the same one. */
    private val memory: InMemoryPrefs by lazy { InMemoryPrefs { publish() } }

    /** True when [prefs] is the in-memory store, so a write to it is held, not saved. */
    fun isMemory(prefs: SharedPreferences): Boolean = prefs === memory

    internal const val RETRY_DELAY_MS = 200L
    /** A background caller retries a store that was merely unavailable this often, never wiping. */
    internal const val BACKGROUND_RETRY_MS = 5 * 60_000L
    /** The app's own open re-checks a degraded store, unless it was checked just now (onCreate opens it twice). */
    internal const val LAUNCH_RECHECK_MS = 10_000L
    private const val PROBE_KEY = "__cooldown_probe__"

    @Synchronized
    fun open(context: Context, resetAllowed: Boolean, backend: CredentialBackend): SharedPreferences {
        outcome?.let { if (!shouldRetry(it, resetAllowed)) return it.prefs }
        return resolve(context, resetAllowed, backend).also { outcome = it; publish() }.prefs
    }

    /**
     * CCBG-50 (Degraded Store Notice), wireframe rev B §1: the notice's Try again. It
     * re-checks **without reset rights**, past the five-minute spacing and the launch
     * guard, so it can never delete anything: a permanent failure it finds waits for the
     * next app open, as a worker's does. A no-op once a write is held or after a failed
     * reset. While simulating it "still fails", so the Debug switch is the recovery test.
     * Blocks for the retry's 200 ms — call it off the main thread.
     */
    @Synchronized
    fun retryNow(context: Context, backend: CredentialBackend = KeystoreBackend): State {
        val o = outcome
        if (_state.value.canRetry && o != null && !o.healthy) {
            outcome = if (o.simulated) Outcome(memory, false, error = o.error, simulated = true)
            else resolve(context.applicationContext, false, backend)
            publish()
        }
        return _state.value
    }

    /**
     * CCBG-50 wireframe rev B §4: the Debug switch. On makes this process act as if the
     * store had failed with a non-permanent error, in memory only — the Keystore and the
     * file are never touched. Off is refused once a write is held (the policy), and on is
     * refused while the real store has failed. Returns whether it took effect.
     */
    @Synchronized
    fun simulate(context: Context, on: Boolean, backend: CredentialBackend = KeystoreBackend): Boolean {
        val o = outcome
        if (on) {
            if (o != null && !o.healthy) return false
            outcome = Outcome(memory, false, error = "Simulated", simulated = true)
        } else {
            if (o?.simulated != true || memory.written) return false
            outcome = resolve(context.applicationContext, false, backend)
        }
        publish()
        return true
    }

    @Synchronized
    private fun publish() {
        val o = outcome
        val prev = _state.value
        _state.value = State(
            healthy = o?.healthy ?: true,
            simulated = o?.simulated == true,
            resetFailed = o?.healthy == false && resetFailed,
            errorName = o?.error,
            checkedAt = o?.at ?: 0L,
            heldSince = memory.firstWriteAt,
            version = prev.version + 1,
        )
    }

    private fun shouldRetry(o: Outcome, resetAllowed: Boolean): Boolean {
        if (o.healthy) return false
        // The Debug simulation holds until it is switched off.
        if (o.simulated) return false
        // After a reset whose probe failed there is nothing left to try in this process.
        if (resetDone) return false
        // Astra 2026-09-30 (C4-1): once anything was written to the in-memory store — a
        // sign-in, a sign-out — the process stays on it until it ends. Switching to the real
        // store mid-session would drop that write earlier than the restart CCBG-50 (Degraded
        // Store Notice) promises, and merging it would risk overwriting the preserved file.
        // Nothing is merged: the write is discarded at restart, the file is never touched.
        if (memory.written) return false
        val age = clock() - o.at
        return if (resetAllowed) o.deferred || age >= LAUNCH_RECHECK_MS
        else !o.deferred && age >= BACKGROUND_RETRY_MS
    }

    /** Tests only: forget the process state, as a fresh process would. */
    @Synchronized
    internal fun resetForTest() {
        outcome = null
        resetDone = false
        resetFailed = false
        clock = System::currentTimeMillis
        memory.wipe()
        _state.value = State()
    }

    private fun resolve(context: Context, resetAllowed: Boolean, backend: CredentialBackend): Outcome {
        var error = attempt(context, backend).let { (prefs, e) -> prefs?.let { return Outcome(it, true) }; e!! }
        if (classify(error) == Failure.PRESERVE) {
            // One retry for anything that is not established as permanent.
            try { Thread.sleep(RETRY_DELAY_MS) } catch (_: InterruptedException) { }
            error = attempt(context, backend).let { (prefs, e) -> prefs?.let { return Outcome(it, true) }; e!! }
        }
        if (classify(error) == Failure.PRESERVE) {
            log(context, AppLog.Level.WARN, "credential store unavailable (${error.javaClass.simpleName}) — running without saved sign-ins this time")
            return Outcome(memory, false, error = error.javaClass.simpleName)
        }
        if (!resetAllowed) {
            log(context, AppLog.Level.WARN, "credential store unreadable (${rootName(error)}) — reset waits for the app to open")
            return Outcome(memory, false, deferred = true, error = rootName(error))
        }
        if (resetDone) {
            log(context, AppLog.Level.WARN, "credential store still unreadable after a reset — running without saved sign-ins")
            resetFailed = true
            return Outcome(memory, false, error = rootName(error))
        }
        resetDone = true
        return try {
            context.deleteSharedPreferences(CredentialStore.FILE_NAME)
            backend.deleteMasterKey()
            val fresh = backend.open(context)
            if (!probe(fresh)) throw IllegalStateException("probe")
            log(context, AppLog.Level.INFO, "credential store reset — sign in again")
            // CCBG-50 wireframe rev B call 7: the next screen says the sign-ins were cleared.
            HeldChanges.recordReset(context, clock())
            Outcome(fresh, true)
        } catch (e: Exception) {
            log(context, AppLog.Level.WARN, "credential store reset failed (${e.javaClass.simpleName}) — running without saved sign-ins")
            resetFailed = true
            Outcome(memory, false, error = e.javaClass.simpleName)
        }
    }

    /** Open, then decrypt every entry, so a bad value fails here rather than in a later load. */
    private fun attempt(context: Context, backend: CredentialBackend): Pair<SharedPreferences?, Exception?> =
        try {
            val prefs = backend.open(context)
            prefs.all
            prefs to null
        } catch (e: Exception) {
            null to e
        }

    private fun probe(prefs: SharedPreferences): Boolean {
        val value = System.nanoTime().toString()
        if (!prefs.edit().putString(PROBE_KEY, value).commit()) return false
        val ok = prefs.getString(PROBE_KEY, null) == value
        prefs.edit().remove(PROBE_KEY).commit()
        return ok
    }

    /**
     * Walks the cause chain, as the Fix line says. A `UserNotAuthenticatedException` anywhere
     * preserves; otherwise an `AEADBadTagException` or `KeyPermanentlyInvalidatedException`
     * anywhere is permanent — including under Tink's own wrapper, a `KeyStoreException`
     * ("the master key … exists but is unusable") around the invalidated key. Anything else,
     * other `InvalidKeyException`s and `KeyStoreException`s included, preserves.
     *
     * Known and kept as the spec has it — Robin, 2026-09-30, after the Step 4 judge: Tink
     * reports an undecryptable *value* as a bare `GeneralSecurityException("decryption
     * failed")` with no cause, so a corrupted value preserves rather than resets.
     */
    fun classify(error: Throwable): Failure {
        val chain = causeChain(error)
        if (chain.any { it is UserNotAuthenticatedException }) return Failure.PRESERVE
        if (chain.any { it is AEADBadTagException || it is KeyPermanentlyInvalidatedException }) return Failure.PERMANENT
        return Failure.PRESERVE
    }

    private fun causeChain(error: Throwable): List<Throwable> {
        val seen = mutableListOf<Throwable>()
        var t: Throwable? = error
        while (t != null && seen.none { it === t } && seen.size < 16) {
            seen += t
            t = t.cause
        }
        return seen
    }

    private fun rootName(error: Throwable): String = causeChain(error).last().javaClass.simpleName

    private fun log(context: Context, level: AppLog.Level, event: String) =
        AppLog.log(context, level, "auth", event = event)
}

/** The degraded store: empty, per process, never on disk. [onFirstWrite] runs once, outside its lock. */
internal class InMemoryPrefs(private val onFirstWrite: () -> Unit = {}) : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    /** True once anything has been committed — the in-memory store then holds the user's work. */
    @Volatile var written = false
        private set

    /** When the first write landed (CCBG-50's "Held in memory since"); 0 while nothing is held. */
    @Volatile var firstWriteAt = 0L
        private set

    @Synchronized fun wipe() {
        map.clear()
        written = false
        firstWriteAt = 0L
    }

    @Synchronized override fun getAll(): Map<String, *> = HashMap(map)
    @Synchronized override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    @Synchronized override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        (map[key] as? Set<String>)?.toSet() ?: defValues
    @Synchronized override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
    @Synchronized override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
    @Synchronized override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
    @Synchronized override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    @Synchronized override fun contains(key: String): Boolean = key in map
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners += l }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners -= l }

    private inner class Editor : SharedPreferences.Editor {
        private val puts = mutableMapOf<String, Any?>()
        private val removes = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { puts[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { puts[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { puts[key] = value }
        override fun putLong(key: String, value: Long) = apply { puts[key] = value }
        override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
        override fun remove(key: String) = apply { removes += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            val first: Boolean
            synchronized(this@InMemoryPrefs) {
                first = !written && (clear || removes.isNotEmpty() || puts.isNotEmpty())
                if (first) {
                    written = true
                    firstWriteAt = CredentialStoreOpener.clock()
                }
                if (clear) map.clear()
                removes.forEach { map.remove(it) }
                puts.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            }
            if (first) onFirstWrite()
            return true
        }
        override fun apply() { commit() }
    }
}
