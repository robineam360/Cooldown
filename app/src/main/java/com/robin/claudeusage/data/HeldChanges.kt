package com.robin.claudeusage.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * CCBG-50 (Degraded Store Notice), wireframe rev B §2: what a degraded session changed,
 * kept in ordinary settings so the next working launch can say what was dropped.
 *
 * For each account it keeps its key, its **last** change and when the first change was
 * made — never a token. A sign-in then a Clear counts as a sign-out; two sign-ins count
 * as one. At the first launch where the store opens ([settle]), each change becomes a
 * card item by comparing it with what the file brought back, and the record is dropped.
 * A reset by CCBG-46 (Keystore Wedge) leaves its own one-time card (rev B call 7).
 */
object HeldChanges {

    enum class Op { SIGN_IN, SIGN_OUT }

    enum class Kind {
        /** A sign-in was dropped and the file has none for that account. */
        SIGN_IN_LOST,
        /** A sign-in was dropped and the file brought back an older one. */
        BACK_ON_OLDER,
        /** A sign-out was dropped and the file brought the sign-in back. */
        SIGNED_IN_AGAIN,
    }

    data class Item(val profileKey: String, val kind: Kind)

    sealed interface Card {
        data class Reset(val at: Long) : Card
        data class Changes(val at: Long, val items: List<Item>) : Card
    }

    private const val FILE = "store_held_changes"
    private const val SESSION_KEY = "session"
    private const val FIRST_AT = "firstAt"
    private const val OP = "op."
    private const val RESET_AT = "resetAt"
    private const val PENDING_AT = "pendingAt"
    private const val PENDING = "pending."

    /** This process. A record written under it is this session's own and is never settled here. */
    internal val SESSION: String = UUID.randomUUID().toString()

    private val _changes = MutableStateFlow(0)
    /** Bumped whenever a card appears or goes, so the main screen redraws. */
    val changes: StateFlow<Int> = _changes

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Called by [CredentialStore] for every write that lands in the in-memory store. */
    @Synchronized
    fun record(
        context: Context,
        profileKey: String,
        op: Op,
        at: Long = CredentialStoreOpener.clock(),
        session: String = SESSION, // tests stand in an earlier process
    ) {
        val p = prefs(context)
        val e = p.edit().putString(SESSION_KEY, session).putString(OP + profileKey, op.name)
        if (p.getLong(FIRST_AT, 0L) == 0L) e.putLong(FIRST_AT, at)
        e.commit()
    }

    @Synchronized
    fun recordReset(context: Context, at: Long) {
        prefs(context).edit().putLong(RESET_AT, at).commit()
        _changes.value++
    }

    /**
     * Turns a record from an earlier session into a card. Runs only while the store is
     * [healthy]; until then the record waits. [hasToken] answers for an account key, or
     * null when the account is gone. [forget] clears the cached snapshot of an account the
     * file left without a token (rev B's second build rule), so no surface keeps a dropped
     * sign-in's numbers.
     */
    @Synchronized
    fun settle(
        context: Context,
        healthy: Boolean,
        hasToken: (String) -> Boolean?,
        forget: (String) -> Unit,
    ) {
        if (!healthy) return
        val p = prefs(context)
        val session = p.getString(SESSION_KEY, null) ?: return
        if (session == SESSION) return
        val firstAt = p.getLong(FIRST_AT, 0L)
        val items = mutableListOf<Item>()
        for ((name, value) in p.all) {
            if (!name.startsWith(OP)) continue
            val key = name.removePrefix(OP)
            val op = runCatching { Op.valueOf(value as String) }.getOrNull() ?: continue
            val has = hasToken(key) ?: continue
            if (!has) forget(key)
            when {
                op == Op.SIGN_IN && !has -> Kind.SIGN_IN_LOST
                op == Op.SIGN_IN -> Kind.BACK_ON_OLDER
                has -> Kind.SIGNED_IN_AGAIN
                else -> null // a sign-out the file agrees with: nothing was undone
            }?.let { items += Item(key, it) }
        }
        val e = p.edit().remove(SESSION_KEY).remove(FIRST_AT)
        p.all.keys.filter { it.startsWith(OP) }.forEach { e.remove(it) }
        if (items.isNotEmpty()) {
            // An earlier card nobody dismissed yet merges with this one, keeping its time.
            val pendingAt = p.getLong(PENDING_AT, 0L)
            e.putLong(PENDING_AT, if (pendingAt > 0L) minOf(pendingAt, firstAt) else firstAt)
            items.forEach { e.putString(PENDING + it.profileKey, it.kind.name) }
        }
        e.commit()
        _changes.value++
    }

    /**
     * The card to show now: a reset first, then the dropped changes. Null when there is none.
     * An item whose account is gone, or that no longer reads, is deleted rather than skipped:
     * with no card to dismiss, it would otherwise stay in settings for good (CCBG-51 (Held
     * Change Residue)).
     */
    @Synchronized
    fun card(context: Context, liveKeys: Set<String>): Card? {
        val p = prefs(context)
        p.getLong(RESET_AT, 0L).takeIf { it > 0L }?.let { return Card.Reset(it) }
        val items = mutableListOf<Item>()
        val orphans = mutableListOf<String>()
        for ((name, value) in p.all) {
            if (!name.startsWith(PENDING)) continue
            val key = name.removePrefix(PENDING)
            val kind = runCatching { Kind.valueOf(value as String) }.getOrNull()
            if (kind == null || key !in liveKeys) orphans += name else items += Item(key, kind)
        }
        if (orphans.isNotEmpty()) {
            val e = p.edit()
            orphans.forEach { e.remove(it) }
            if (items.isEmpty()) e.remove(PENDING_AT)
            e.commit()
        }
        if (items.isEmpty()) return null
        return Card.Changes(p.getLong(PENDING_AT, 0L), items.sortedBy { it.profileKey })
    }

    /**
     * A removed account's entries go with it (CCBG-51 (Held Change Residue)): its held
     * change and its notice item. The rest of the record is left for [settle] and [card].
     */
    @Synchronized
    fun forgetAccount(context: Context, profileKey: String) {
        val p = prefs(context)
        val e = p.edit().remove(OP + profileKey).remove(PENDING + profileKey)
        val keys = p.all.keys - setOf(OP + profileKey, PENDING + profileKey)
        if (keys.none { it.startsWith(OP) }) e.remove(SESSION_KEY).remove(FIRST_AT)
        if (keys.none { it.startsWith(PENDING) }) e.remove(PENDING_AT)
        e.commit()
        _changes.value++
    }

    /** OK and Open Settings both end the card for good. */
    @Synchronized
    fun dismiss(context: Context, card: Card) {
        val p = prefs(context)
        val e = p.edit()
        when (card) {
            is Card.Reset -> e.remove(RESET_AT)
            is Card.Changes -> {
                e.remove(PENDING_AT)
                p.all.keys.filter { it.startsWith(PENDING) }.forEach { e.remove(it) }
            }
        }
        e.commit()
        _changes.value++
    }

    /** Tests only. */
    internal fun clearForTest(context: Context) {
        prefs(context).edit().clear().commit()
    }
}
