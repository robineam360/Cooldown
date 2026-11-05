package com.robin.claudeusage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CCBG-75 (Antigravity Crash on Refresh): an account whose provider has no usage source on
 * this build must never throw on the refresh path. A phone that ran a build with a Gemini
 * account first (same package and versionCode) holds one, and `refreshAll` loops over every
 * account with a token, so `Sources.of(ANTIGRAVITY)` threw `NotImplementedError` out of every
 * background and manual refresh.
 *
 * Claude and ChatGPT are put in a 429 backoff so the loop answers for them without a network
 * call, which is still "attempted": they must not be dropped because of their neighbour.
 */
@RunWith(RobolectricTestRunner::class)
class RefreshNoSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val token = Credentials("access", "refresh", 0L)

    @Before
    fun setUp() {
        CredentialStoreOpener.resetForTest()
    }

    private fun repoWithThreeAccounts(): Triple<UsageRepository, List<Profile>, Profile> {
        val repo = UsageRepository(context)
        val store = CredentialStore(context)
        val cache = UsageCache(context)
        val claude = repo.registry().all().first()
        val chatGpt = repo.addProfile("Codex", Provider.CHATGPT)
        val gemini = repo.addProfile("Gemini", Provider.ANTIGRAVITY)
        val now = System.currentTimeMillis()
        for (p in listOf(claude, chatGpt, gemini)) store.save(p, token)
        for (p in listOf(claude, chatGpt)) cache.bumpBackoff(p, now)
        return Triple(repo, listOf(claude, chatGpt), gemini)
    }

    @Test
    fun `a Gemini account with a token does not crash refreshAll and its neighbours are still attempted`() =
        runBlocking {
            val (repo, others, gemini) = repoWithThreeAccounts()
            assertTrue("the Gemini account must read as configured", gemini in repo.configuredProfiles())

            val results = repo.refreshAll(manual = false)

            for (p in others) {
                assertTrue("$p was attempted", results[p] is FetchResult.BackedOff)
            }
            assertTrue("a provider with no source is not fetched", results[gemini] !is FetchResult.Success)
        }

    @Test
    fun `a manual refresh of a Gemini account answers instead of throwing`() = runBlocking {
        val (repo, _, gemini) = repoWithThreeAccounts()
        val result = repo.refreshNow(gemini, manual = true)
        assertTrue(result is FetchResult.Error)
    }

    @Test
    fun `refreshing a Gemini account leaves its stored data alone`() = runBlocking {
        val (repo, _, gemini) = repoWithThreeAccounts()
        val before = repo.snapshot(gemini)
        repo.refreshNow(gemini, manual = true)
        repo.refreshAll(manual = false)
        val after = repo.snapshot(gemini)
        assertEquals(before.lastStatus, after.lastStatus)
        assertEquals(before.fetchedAt, after.fetchedAt)
        assertEquals(before.lastAttemptAt, after.lastAttemptAt)
    }
}
