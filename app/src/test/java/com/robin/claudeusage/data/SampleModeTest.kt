package com.robin.claudeusage.data

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.notify.Surfaces
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * CCRM-113 (Sample Port), BEHAVIOR-MOBILE §7: the four isolation tests of `reviews/v20-sample-mode-findings.md`
 * (C1-1 … C1-4) and what the sample holds. Each runs against the real stores of a Robolectric app: preferences files,
 * the history files in `filesDir`, the registry, the notification manager.
 */
@RunWith(RobolectricTestRunner::class)
class SampleModeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_800_000_000_000L

    private object PlainBackend : CredentialBackend {
        override fun open(context: Context) = context.getSharedPreferences(CredentialStore.FILE_NAME, Context.MODE_PRIVATE)
        override fun deleteMasterKey() {}
    }

    @Before
    fun setUp() {
        CredentialStoreOpener.resetForTest()
        SampleMode.exit()
        SyntheticSeries.set(SyntheticSeries.Mode.OFF)
    }

    @After
    fun tearDown() {
        SampleMode.exit()
        SyntheticSeries.set(SyntheticSeries.Mode.OFF)
    }

    /** Every byte the real app has on disk and in preferences: each prefs file's entries and every file under filesDir. */
    private fun realBytes(): Map<String, String> {
        val out = sortedMapOf<String, String>()
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        dir.listFiles()?.forEach { f ->
            val name = f.name.removeSuffix(".xml")
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            out["prefs/$name"] = prefs.all.toSortedMap().toString()
        }
        context.filesDir.walkTopDown().filter { it.isFile }.forEach { out["files/${it.name}"] = it.readText() }
        return out
    }

    /** A real install: two accounts with a token, a reading, history, a stored theme and a remembered filter. */
    private fun realInstall(): UsageRepository {
        val creds = CredentialStore(context, true, PlainBackend)
        val repo = UsageRepository(context)
        val cache = repo.cacheSettings()
        for (p in repo.profiles()) {
            creds.save(p, Credentials("access", "refresh", 0L))
            cache.saveSuccess(
                p,
                """{"five_hour":{"utilization":41.0,"resets_at":"2026-11-12T10:00:00Z"},"seven_day":{"utilization":12.0,"resets_at":"2026-11-17T10:00:00Z"}}""",
                now,
            )
            repo.history().record(
                p, UsageData(UsageWindow(41.0, null, null), UsageWindow(12.0, null, null), emptyList()), now,
            )
        }
        cache.setThemeMode("dark")
        cache.setDensity(com.robin.claudeusage.ui.Density.COMPACT)
        cache.setSummaryFilter(SummaryFilter.WORK)
        return repo
    }

    // ---- what the sample is

    @Test fun `three made-up accounts, one per provider, with the tags fixed at entry`() {
        SampleMode.enter(context, null, now)
        val repo = SampleMode.repo()!!
        assertTrue(repo.isSample)
        val accounts = repo.profiles()
        assertEquals(listOf("Teams", "Personal", "Lab"), accounts.map { repo.cacheSettings().profileLabel(it) })
        assertEquals(listOf(Provider.CLAUDE, Provider.CHATGPT, Provider.ANTIGRAVITY), accounts.map { it.provider })
        // Teams Work, Personal Personal, Lab Work: both Summary segments are live and the first list mixes both glyphs.
        assertEquals(listOf(AccountTag.WORK, AccountTag.PERSONAL, AccountTag.WORK), accounts.map { repo.profileTag(it) })
        assertEquals(accounts, repo.configuredProfiles())
        for (a in accounts) {
            val data = repo.snapshot(a).data
            assertNotNull("${a.label} has a reading", data)
            assertNotNull(data!!.session)
            assertNotNull(data.weekly)
            assertTrue("${a.label} has history", repo.history().points(a).size > 100)
            assertTrue("${a.label} has closed windows", repo.sessionLog().records(a).isNotEmpty())
        }
        // Teams: Fable limit and credits; Lab above its pace; Personal well under.
        val teams = repo.snapshot(accounts[0]).data!!
        assertEquals(listOf("Fable"), teams.modelCaps.map { it.modelName })
        assertEquals(31.0, teams.credits!!.percent!!, 0.5)
        assertEquals("Max", repo.plan(accounts[0]))
    }

    @Test fun `the sample never fetches, signs in or removes`() = runBlocking {
        SampleMode.enter(context, null, now)
        val repo = SampleMode.repo()!!
        val a = repo.profiles()[0]
        assertEquals("OK", repo.refreshNow(a, manual = true).message)
        assertTrue(repo.refreshAll(manual = true).isEmpty())
        assertFalse(repo.removeProfile(a))
        repo.clearCredentials(a)
        assertTrue(repo.hasCredentials(a))
        // Nothing was written to any file by building and reading the sample.
        assertTrue(context.filesDir.walkTopDown().none { it.isFile && it.name.startsWith("usage-") })
    }

    @Test fun `the sample inherits the Appearance settings and keeps its own copy`() {
        val real = realInstall().cacheSettings()
        SampleMode.enter(context, null, now)
        val copy = SampleMode.repo()!!.cacheSettings()
        assertEquals("dark", copy.themeMode())
        assertEquals(com.robin.claudeusage.ui.Density.COMPACT, copy.density())
        copy.setThemeMode("light")
        copy.setSummaryFilter(SummaryFilter.PERSONAL)
        assertEquals("dark", real.themeMode())
        assertEquals(SummaryFilter.WORK.key, real.summaryFilterStored())
        // The memory rule runs only against the sample's in-memory copy.
        copy.settleSummaryFilter(SampleMode.repo()!!.configuredProfiles())
        assertEquals(SummaryFilter.PERSONAL.key, copy.summaryFilterStored())
        assertEquals(SummaryFilter.WORK.key, real.summaryFilterStored())
    }

    // ---- C1-1: real work keeps running; entering and exiting leave every real store byte-identical

    @Test fun `C1-1 entering, using and leaving the sample leaves the real stores byte-identical`() {
        val real = realInstall()
        val before = realBytes()
        SampleMode.enter(context, "p0", now)
        val sample = SampleMode.repo()!!
        // Use it hard: edit every setting and account detail the sample allows, record history, tag, rename.
        val cache = sample.cacheSettings()
        cache.setThemeMode("light"); cache.setUsageDisplay("left"); cache.setDensity(com.robin.claudeusage.ui.Density.COMFORTABLE)
        cache.setSummaryFilter(SummaryFilter.PERSONAL); cache.setSummaryOrder(SummaryOrder.MOST_USED)
        val teams = sample.profiles()[0]
        sample.renameProfile(teams, "Renamed"); sample.setProfileTag(teams, AccountTag.PERSONAL)
        cache.setAccountAccent(teams, "Gemini Blue"); cache.setHeadline(teams, Headline.FIVE_HOUR)
        sample.history().record(teams, UsageData(UsageWindow(1.0, null, null), null, emptyList()), now + 60_000)
        sample.clearHistory(teams)
        assertEquals("p0", SampleMode.exit())
        assertEquals(before, realBytes())
        assertEquals(real.profiles().map { it.label }, listOf("Personal", "Work"))
    }

    @Test fun `C1-1 real polling carries on into the real stores while the sample is open`() {
        val real = realInstall()
        SampleMode.enter(context, null, now)
        // A real poll (its own repository, as Polling and PinnedService build them) lands during the sample.
        val p = real.profiles()[0]
        UsageRepository(context).cacheSettings().saveSuccess(
            p, """{"five_hour":{"utilization":77.0,"resets_at":"2026-11-12T10:00:00Z"}}""", now + 1_000,
        )
        UsageRepository(context).history().record(
            p, UsageData(UsageWindow(77.0, null, null), null, emptyList()), now + 1_000,
        )
        SampleMode.exit()
        assertEquals(77.0, real.cacheSettings().realSnapshot(p).data!!.session!!.percent!!, 0.0)
        assertEquals(77.0, real.history().realPoints(p).last().sessionPct!!, 0.0)
        // ... and the sample drew none of it.
    }

    // ---- C1-2: settings split by reader

    @Test fun `C1-2 an Appearance change in the sample leaves the live notification and the real settings untouched`() {
        val real = realInstall().cacheSettings()
        real.setPinnedEnabled(true)
        val nm = context.getSystemService(NotificationManager::class.java)
        // The user's live pinned notification, as the real surface posted it.
        val live = androidx.core.app.NotificationCompat.Builder(context, "t").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("live pin").build()
        nm.notify(com.robin.claudeusage.notify.PinnedNotification.NOTIF_ID, live)
        val posted = { shadowOf(nm).getNotification(com.robin.claudeusage.notify.PinnedNotification.NOTIF_ID) }

        SampleMode.enter(context, null, now)
        val copy = SampleMode.repo()!!.cacheSettings()
        assertTrue(copy.isSample)
        assertFalse("the copy has no pin of its own", copy.pinnedEnabled())
        // The Settings handlers redraw through the one refresh seam; it refuses the sample's cache.
        copy.setUsageDisplay("left"); copy.setResetDisplay("clock"); copy.setShowOverPace(false)
        Surfaces.refresh(context, copy)
        // Without the seam's guard the copy (pin off) would cancel the user's real notification.
        assertEquals("the live notification is still posted, unchanged", "live pin",
            posted()?.extras?.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals("used", real.usageDisplay())
        assertEquals("countdown", real.resetDisplay())
        assertTrue(real.showOverPace())
        // The widgets read the real cache: its snapshot of the real accounts is untouched by anything the sample did.
        assertEquals(
            listOf("Personal", "Work"),
            real.registry().all().map { real.profileLabel(it) },
        )
    }

    // ---- C1-3: no late write crosses over

    @Test fun `C1-3 a Rename open across a switch never renames a real account`() {
        val real = realInstall()
        val realLabels = real.profiles().map { real.cacheSettings().profileLabel(it) }
        SampleMode.enter(context, null, now)
        // The dialog binds to the repository it opened with, not to the flag: this is its commit, held across the switch.
        val sample = SampleMode.repo()!!
        val target = sample.profiles()[0]
        val commit = { name: String -> sample.renameProfile(target, name) }
        SampleMode.exit()
        assertNull(SampleMode.repo())
        commit("Late name")
        assertEquals(realLabels, real.profiles().map { real.cacheSettings().profileLabel(it) })
        assertEquals(listOf("Personal", "Work"), UsageRepository(context).profiles().map { it.label })
        // And a real account's key can never be named by the sample.
        assertTrue(sample.profiles().none { p -> real.profiles().any { it.key == p.key } })
    }

    // ---- C1-4: the debug series pauses

    @Test fun `C1-4 the synthetic flag is suspended while the sample is open and restored on exit, nothing written`() {
        val before = realBytes()
        SyntheticSeries.set(SyntheticSeries.Mode.ABOVE_PACE, now)
        SampleMode.enter(context, null, now)
        assertEquals(SyntheticSeries.Mode.OFF, SyntheticSeries.mode.value)
        assertFalse(SyntheticSeries.isOn)
        // A second entry must not lose the original mode.
        SampleMode.enter(context, null, now)
        SampleMode.exit()
        assertEquals(SyntheticSeries.Mode.ABOVE_PACE, SyntheticSeries.mode.value)
        assertTrue(SyntheticSeries.isOn)
        assertEquals(before, realBytes())
    }

    @Test fun `exit hands back where the user was, and a second exit is nothing`() {
        SampleMode.enter(context, "__all__", now)
        assertTrue(SampleMode.isOn)
        assertEquals("__all__", SampleMode.exit())
        assertFalse(SampleMode.isOn)
        assertNull(SampleMode.exit())
        assertEquals(SyntheticSeries.Mode.OFF, SyntheticSeries.mode.value)
    }
}
