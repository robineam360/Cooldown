package com.robin.claudeusage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.data.source.Sources
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CCBG-75 (Antigravity Crash on Refresh), closed by the v2.0 merge: v1.9 had no Antigravity
 * source, so `Sources.of(ANTIGRAVITY)` threw out of every refresh once a phone held a Gemini
 * account. v2.0 has `AntigravitySource`, the `hasSource` guard is gone, and this test pins the
 * invariant the guard stood in for: every provider resolves a source, and a refresh loop over
 * Claude, ChatGPT and Gemini answers for all three.
 *
 * All three accounts sit in a 429 backoff so the loop answers without a network call, which
 * still counts as "attempted": no account may be dropped or throw because of a neighbour.
 */
@RunWith(RobolectricTestRunner::class)
class RefreshEveryProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val token = Credentials("access", "refresh", 0L)

    @Before
    fun setUp() {
        CredentialStoreOpener.resetForTest()
    }

    private fun repoWithThreeAccounts(): Pair<UsageRepository, List<Profile>> {
        val repo = UsageRepository(context)
        val store = CredentialStore(context)
        val cache = UsageCache(context)
        val claude = repo.registry().all().first()
        val chatGpt = repo.addProfile("Codex", Provider.CHATGPT)
        val gemini = repo.addProfile("Gemini", Provider.ANTIGRAVITY)
        val all = listOf(claude, chatGpt, gemini)
        val now = System.currentTimeMillis()
        for (p in all) {
            store.save(p, token)
            cache.bumpBackoff(p, now)
        }
        return repo to all
    }

    @Test
    fun `every provider resolves a usage source`() {
        for (provider in Provider.values()) {
            assertNotNull("$provider has a source", Sources.of(provider))
        }
    }

    @Test
    fun `refreshAll attempts every account including Gemini`() = runBlocking {
        val (repo, all) = repoWithThreeAccounts()
        for (p in all) assertTrue("$p reads as configured", p in repo.configuredProfiles())

        val results = repo.refreshAll(manual = false)

        assertEquals(all.toSet(), results.keys)
        for (p in all) assertTrue("$p was attempted", results[p] is FetchResult.BackedOff)
    }
}
