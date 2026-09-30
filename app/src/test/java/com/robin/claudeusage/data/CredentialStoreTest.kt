package com.robin.claudeusage.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import javax.crypto.AEADBadTagException

/**
 * CCBG-46 (Keystore Wedge): every way the credential store's open can fail, through an
 * injectable backend that stands in for the Keystore. The real file handling (the
 * delete on reset) is the production code; only the encrypted open and the key delete
 * are faked.
 */
@RunWith(RobolectricTestRunner::class)
class CredentialStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val profile = Profile("p1", 1, "Work")
    private val creds = Credentials("sk-ant-oat01-access", "sk-ant-ort01-refresh", 42L)

    /** Throws each scripted failure in turn, then opens the plain prefs file. */
    private class FakeBackend(
        vararg failures: Exception,
        val always: Exception? = null,
        val openAfterReset: ((Context) -> SharedPreferences)? = null,
    ) : CredentialBackend {
        val script = ArrayDeque(failures.toList())
        var opens = 0
        var keyDeletes = 0

        override fun open(context: Context): SharedPreferences {
            opens++
            always?.let { throw it }
            script.removeFirstOrNull()?.let { throw it }
            if (keyDeletes > 0 && openAfterReset != null) return openAfterReset.invoke(context)
            return context.getSharedPreferences(CredentialStore.FILE_NAME, Context.MODE_PRIVATE)
        }

        override fun deleteMasterKey() { keyDeletes++ }
    }

    private val file: File
        get() = File(context.dataDir, "shared_prefs/${CredentialStore.FILE_NAME}.xml")

    @Before
    fun setUp() {
        CredentialStoreOpener.resetForTest()
        context.deleteSharedPreferences(CredentialStore.FILE_NAME)
        // A signed-in account already on disk, as on a real phone.
        CredentialStore(context, true, FakeBackend()).save(profile, creds)
        context.getSharedPreferences(CredentialStore.FILE_NAME, Context.MODE_PRIVATE).edit().commit()
        CredentialStoreOpener.resetForTest()
        assertTrue(file.exists())
    }

    @After
    fun tearDown() = CredentialStoreOpener.resetForTest()

    private fun assertPreserved(backend: FakeBackend, store: CredentialStore) {
        assertTrue("the file must survive", file.exists())
        assertEquals(0, backend.keyDeletes)
        assertNull("runs empty in memory", store.load(profile))
        // Still on disk, readable the moment the Keystore behaves again.
        CredentialStoreOpener.resetForTest()
        assertEquals(creds, CredentialStore(context, true, FakeBackend()).load(profile))
    }

    // --- preserve: never wipe ---

    @Test
    fun `user not authenticated preserves the file`() {
        val backend = FakeBackend(always = UserNotAuthenticatedException())
        assertPreserved(backend, CredentialStore(context, true, backend))
    }

    @Test
    fun `user not authenticated preserves even with a bad tag deeper in the chain`() {
        val e = UserNotAuthenticatedException("locked").apply { initCause(AEADBadTagException()) }
        val backend = FakeBackend(always = e)
        assertPreserved(backend, CredentialStore(context, true, backend))
    }

    @Test
    fun `locked device keystore exception preserves the file`() {
        val backend = FakeBackend(always = KeyStoreException("Key user not authenticated / locked"))
        assertPreserved(backend, CredentialStore(context, true, backend))
    }

    @Test
    fun `another invalid key exception preserves the file`() {
        val e = InvalidKeyException("Keystore operation failed", KeyStoreException("-26"))
        val backend = FakeBackend(always = e)
        assertPreserved(backend, CredentialStore(context, true, backend))
    }

    @Test
    fun `a transient error still failing after one retry preserves the file`() {
        val backend = FakeBackend(always = IOException("binder hiccup"))
        assertPreserved(backend, CredentialStore(context, true, backend))
        // The first open plus exactly one retry, then the in-memory store.
        assertEquals(2, backend.opens)
    }

    @Test
    fun `a transient error that clears on the retry opens the real store`() {
        val backend = FakeBackend(RuntimeException("blip"))
        val store = CredentialStore(context, true, backend)
        assertEquals(creds, store.load(profile))
        assertEquals(2, backend.opens)
        assertEquals(0, backend.keyDeletes)
    }

    // --- permanent: reset, rebuild, probe ---

    // A synthetic chain: it proves the cause-chain walk. The shapes Tink really throws are
    // pinned in `the classifier` below.
    @Test
    fun `a bad tag in the cause chain resets and a write reads back from a fresh store`() {
        val chain = SecurityException("Could not decrypt value", GeneralSecurityException(AEADBadTagException()))
        val backend = FakeBackend(chain)
        val store = CredentialStore(context, true, backend)
        assertEquals(1, backend.keyDeletes)
        assertNull("signed out after the reset", store.load(profile))

        store.save(profile, creds.copy(accessToken = "sk-ant-oat01-after"))
        context.getSharedPreferences(CredentialStore.FILE_NAME, Context.MODE_PRIVATE).edit().commit()
        CredentialStoreOpener.resetForTest()
        val fresh = CredentialStore(context, true, FakeBackend())
        assertEquals("sk-ant-oat01-after", fresh.load(profile)?.accessToken)
    }

    @Test
    fun `a permanently invalidated key resets`() {
        val backend = FakeBackend(KeyPermanentlyInvalidatedException())
        val store = CredentialStore(context, true, backend)
        assertEquals(1, backend.keyDeletes)
        assertNull(store.load(profile))
    }

    @Test
    fun `a failed probe after the reset runs in memory and never wipes twice`() {
        val broken = { _: Context -> BrokenPrefs() }
        val backend = FakeBackend(AEADBadTagException(), openAfterReset = broken)
        val store = CredentialStore(context, true, backend)
        assertEquals(1, backend.keyDeletes)
        assertNull(store.load(profile))
        // The in-memory store works for this process…
        store.save(profile, creds)
        assertEquals(creds, store.load(profile))
        // …and a second open in the same process neither resets again nor trusts the store.
        val second = CredentialStore(context, true, backend)
        assertEquals(1, backend.keyDeletes)
        assertEquals("the same in-memory store", creds, second.load(profile))
    }

    // --- the boot path ---

    @Test
    fun `a caller not allowed to reset never wipes, and defers to the app open`() {
        val backend = FakeBackend(AEADBadTagException(), AEADBadTagException())
        val background = CredentialStore(context, false, backend)
        assertTrue(file.exists())
        assertEquals(0, backend.keyDeletes)
        assertNull(background.load(profile))
        // Another background open in the same process still leaves it alone.
        CredentialStore(context, backend = backend, resetAllowed = false)
        assertEquals(0, backend.keyDeletes)
        // The app opening is what resets.
        val app = CredentialStore(context, true, backend)
        assertEquals(1, backend.keyDeletes)
        // The background store built before the reset (the pinned service keeps its
        // repository) follows the process onto the rebuilt store.
        app.save(profile, creds)
        assertEquals(creds, background.load(profile))
    }

    @Test
    fun `only the main screen may reset — never a worker, the service or a boot receiver`() {
        val root = File("src/main/java")
        val hits = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val text = f.readText()
                text.contains("credentialResetAllowed = true") || text.contains("resetAllowed = true") ||
                    Regex("(CredentialStore|UsageRepository)\\([^)]*\\btrue\\b").containsMatchIn(text)
            }
            .map { it.name }
            .toList()
        assertEquals(listOf("MainActivity.kt"), hits)
    }

    @Test
    fun `the classifier`() {
        val c = CredentialStoreOpener
        assertEquals(CredentialStoreOpener.Failure.PERMANENT, c.classify(AEADBadTagException()))
        assertEquals(CredentialStoreOpener.Failure.PERMANENT, c.classify(RuntimeException(KeyPermanentlyInvalidatedException())))
        assertEquals(CredentialStoreOpener.Failure.PRESERVE, c.classify(UserNotAuthenticatedException()))
        // Found anywhere in the chain, as the Fix line says.
        assertEquals(CredentialStoreOpener.Failure.PERMANENT, c.classify(InvalidKeyException(AEADBadTagException())))
        assertEquals(CredentialStoreOpener.Failure.PRESERVE, c.classify(IOException()))
        assertFalse(c.classify(KeyStoreException()) == CredentialStoreOpener.Failure.PERMANENT)
        // The shapes tink-android 1.8 (under security-crypto 1.1.0) really throws:
        // a keyset that no longer decrypts is rethrown bare…
        assertEquals(CredentialStoreOpener.Failure.PERMANENT, c.classify(AEADBadTagException("Tag mismatch")))
        // …an unusable master key with no keyset comes wrapped in a KeyStoreException…
        assertEquals(
            CredentialStoreOpener.Failure.PERMANENT,
            c.classify(KeyStoreException("the master key exists but is unusable", KeyPermanentlyInvalidatedException())),
        )
        // …a Keystore that is merely failing is an InvalidKeyException over a KeyStoreException…
        assertEquals(
            CredentialStoreOpener.Failure.PRESERVE,
            c.classify(InvalidKeyException("Keystore operation failed", KeyStoreException("-26"))),
        )
        // …and an undecryptable value is a causeless "decryption failed", which preserves
        // (kept as the spec has it: Robin, 2026-09-30).
        assertEquals(
            CredentialStoreOpener.Failure.PRESERVE,
            c.classify(SecurityException("Could not decrypt value", GeneralSecurityException("decryption failed"))),
        )
    }

    /** Opens, but nothing written reads back — the probe must catch it. */
    private class BrokenPrefs : SharedPreferences by InMemoryPrefs() {
        override fun getString(key: String, defValue: String?): String? = null
    }
}
