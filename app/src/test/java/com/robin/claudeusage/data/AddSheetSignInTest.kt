package com.robin.claudeusage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Add-sheet rule (Robin 2026-11-11: "the same on both platforms"; iOS creates the account only when sign-in
 * succeeds, and a test pins that): a cancelled or ended sign-in started from the Add account sheet leaves **no account
 * and no tag**. Android's card needs a profile to host the sign-in, so the account is minted provisional and removed
 * unless the sign-in saved a token.
 */
@RunWith(RobolectricTestRunner::class)
class AddSheetSignInTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private object PlainBackend : CredentialBackend {
        override fun open(context: Context) = context.getSharedPreferences(CredentialStore.FILE_NAME, Context.MODE_PRIVATE)
        override fun deleteMasterKey() {}
    }

    private lateinit var creds: CredentialStore

    @Before
    fun setUp() {
        CredentialStoreOpener.resetForTest()
        creds = CredentialStore(context, true, PlainBackend)
        OAuthSignIn.clearPending(context)
        CodexDeviceSignIn.clearPending(context)
        AntigravitySignIn.clearPending(context)
    }

    private fun accountKeys(repo: UsageRepository) = repo.profiles().map { it.key }

    @Test fun `a cancelled Add-sheet sign-in leaves no account and no tag, for every provider and tag`() = runBlocking {
        for (provider in Provider.entries) for (tag in AccountTag.entries) {
            val repo = UsageRepository(context)
            val before = repo.registry().all()
            val p = repo.addProvisionalProfile(provider, tag)
            assertTrue(repo.isProvisional(p))
            assertEquals(tag, repo.profileTag(p))
            // The sign-in is cancelled (Claude's Cancel, Gemini's Cancel, the device sheet's dismiss).
            assertTrue(repo.abandonProfile(p))
            assertEquals(before, repo.registry().all())
            assertNull(repo.registry().byKey(p.key))
            assertFalse("the mark leaves with the account", p.key in repo.registry().provisionalKeys())
            // No tag survives anywhere: the registry's stored list has no entry for the key.
            val stored = context.getSharedPreferences("profiles", Context.MODE_PRIVATE).getString("list", "")!!
            assertFalse(stored.contains("\"${p.key}\""))
        }
    }

    @Test fun `slots are never reused after an abandoned add`() = runBlocking {
        val repo = UsageRepository(context)
        val a = repo.addProvisionalProfile(Provider.CLAUDE, AccountTag.WORK)
        repo.abandonProfile(a)
        val b = repo.addProvisionalProfile(Provider.CLAUDE, AccountTag.PERSONAL)
        assertTrue(b.slot > a.slot)
    }

    @Test fun `a sign-in that saved a token keeps its account, and the mark clears`() = runBlocking {
        val repo = UsageRepository(context)
        val p = repo.addProvisionalProfile(Provider.CHATGPT, AccountTag.WORK)
        // What completeDeviceSignIn / completeSignIn / completeAntigravitySignIn do on success: save the token, clear the mark.
        creds.save(p, Credentials("a", "r", 0L), stampAdded = true)
        repo.registry().clearProvisional(p.key)
        assertFalse(repo.abandonProfile(p))
        assertTrue(repo.hasCredentials(p))
        assertEquals(AccountTag.WORK, repo.profileTag(p))
    }

    @Test fun `abandon never touches an account that was not provisional or already has a token`() = runBlocking {
        val repo = UsageRepository(context)
        val existing = repo.addProfile("Keep", Provider.CLAUDE, AccountTag.WORK)
        assertFalse(repo.abandonProfile(existing))
        assertTrue(existing.key in accountKeys(repo))
        // Provisional but already signed in (the token landed before the cancel): kept.
        val p = repo.addProvisionalProfile(Provider.CLAUDE, AccountTag.PERSONAL)
        creds.save(p, Credentials("a", "r", 0L))
        assertFalse(repo.abandonProfile(p))
        assertTrue(p.key in accountKeys(repo))
    }

    @Test fun `the launch sweep removes a provisional account nobody is signing in, and keeps one with a sign-in pending`() = runBlocking {
        val repo = UsageRepository(context)
        val stale = repo.addProvisionalProfile(Provider.CLAUDE, AccountTag.WORK)
        val pendingOne = repo.addProvisionalProfile(Provider.CLAUDE, AccountTag.PERSONAL)
        OAuthSignIn.begin(context, pendingOne)
        repo.settleProvisional()
        assertFalse(stale.key in accountKeys(repo))
        assertTrue("a browser trip in flight keeps its account", pendingOne.key in accountKeys(repo))
        // Once that sign-in is cancelled the next sweep takes it.
        repo.cancelSignIn()
        repo.settleProvisional()
        assertFalse(pendingOne.key in accountKeys(repo))
    }

    @Test fun `a provisional account older than the limit is swept even with a sign-in pending, a fresh one is kept`() = runBlocking {
        val repo = UsageRepository(context)
        val old = repo.addProvisionalProfile(Provider.CLAUDE, AccountTag.WORK)
        val fresh = repo.addProvisionalProfile(Provider.CLAUDE, AccountTag.PERSONAL)
        OAuthSignIn.begin(context, old)
        repo.registry().markProvisional(old.key, System.currentTimeMillis() - UsageRepository.PROVISIONAL_MAX_AGE_MS - 1)
        OAuthSignIn.begin(context, fresh)   // the pending slot holds one at a time; the swept one is the aged account
        repo.settleProvisional()
        assertFalse(old.key in accountKeys(repo))
        assertTrue(fresh.key in accountKeys(repo))
    }

    @Test fun `a dismiss that races the token save keeps the account`() = runBlocking {
        val repo = UsageRepository(context)
        val p = repo.addProvisionalProfile(Provider.CHATGPT, AccountTag.WORK)
        // The token lands first (the exchange holds the lock while it saves); the late dismiss finds it and keeps the account.
        creds.save(p, Credentials("a", "r", 0L), stampAdded = true)
        assertFalse(repo.abandonProfile(p))
        assertTrue(p.key in accountKeys(repo))
    }

    @Test fun `a mark whose account is gone is dropped by the sweep`() = runBlocking {
        val repo = UsageRepository(context)
        repo.registry().markProvisional("p99")
        repo.settleProvisional()
        assertFalse("p99" in repo.registry().provisionalKeys())
    }
}
