package com.robin.claudeusage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CCRM-110 (Account Tags), as amended 2026-11-11: Personal or Work only, a stored tag that reads as Personal when
 * missing, empty or unknown, the filter in force, and the memory rule — every `filter` case and every `memory`
 * sequence of `spec/mobile/fixtures/tags/cases-personal-work.json` replayed against the real store (the registry, the
 * cache's preferences and [UsageRepository]'s own add / tag / remove paths). iOS reads the same file.
 */
@RunWith(RobolectricTestRunner::class)
class AccountTagsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val token = Credentials("access", "refresh", 0L)

    private val fx: JSONObject by lazy {
        JSONObject(javaClass.classLoader!!.getResourceAsStream("mobile/fixtures/tags/cases-personal-work.json")!!.bufferedReader().readText())
    }

    @Before
    fun setUp() {
        CredentialStoreOpener.resetForTest()
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)

    /** A registry holding exactly [accounts] (name, stored tag exactly as written), each signed in. */
    private fun store(accounts: List<Pair<String, String?>>, storedFilter: String?): UsageRepository {
        val profiles = accounts.mapIndexed { i, (name, raw) -> Profile("p$i", i, name, Provider.CLAUDE, raw) }
        val state = ProfileRegistry.Companion.State(profiles, profiles.size)
        context.getSharedPreferences("profiles", Context.MODE_PRIVATE).edit()
            .putString("list", ProfileRegistry.encode(state)).putInt("nextSlot", state.nextSlot)
            .putBoolean("seeded", true).putInt("rev", 100).commit()
        val cache = UsageCache(context)
        context.getSharedPreferences("usage_cache", Context.MODE_PRIVATE).edit().apply {
            if (storedFilter == null) remove("summaryFilter") else putString("summaryFilter", storedFilter)
        }.commit()
        val creds = creds()
        for (p in cache.registry().all()) creds.save(p, token)
        return UsageRepository(context)
    }

    /** A credential store on a plain file, standing in for the Keystore (the same seam CredentialStoreTest uses). */
    private object PlainBackend : CredentialBackend {
        override fun open(context: Context) = context.getSharedPreferences(CredentialStore.FILE_NAME, Context.MODE_PRIVATE)
        override fun deleteMasterKey() {}
    }

    private fun creds() = CredentialStore(context, true, PlainBackend)

    private fun accounts(c: JSONObject) = c.getJSONArray("accounts").objects().map { it.getString("name") to it.str("storedTag") }

    private fun byName(repo: UsageRepository, name: String) = repo.configuredProfiles().first { it.label == name }

    // ---- the tag itself

    @Test fun `a missing, empty or unknown stored tag reads as Personal and work reads as Work`() {
        assertEquals(AccountTag.PERSONAL, AccountTag.read(null))
        assertEquals(AccountTag.PERSONAL, AccountTag.read(""))
        assertEquals(AccountTag.PERSONAL, AccountTag.read("family"))
        assertEquals(AccountTag.PERSONAL, AccountTag.read("personal"))
        assertEquals(AccountTag.WORK, AccountTag.read("work"))
    }

    @Test fun `registry - the tag is additive, an unknown value survives a round trip, nothing is written for a v1_9 account`() {
        val v19 = JSONArray().put(JSONObject().put("k", "personal").put("s", 0).put("l", "Personal").put("d", "Personal").put("v", "claude"))
            .put(JSONObject().put("k", "work").put("s", 1).put("l", "Work").put("d", "Work").put("v", "claude")).toString()
        val read = ProfileRegistry.decode(v19, 2)
        // Upgrade: both accounts read as Personal, nothing stored.
        assertEquals(listOf(AccountTag.PERSONAL, AccountTag.PERSONAL), read.profiles.map { it.tag })
        assertEquals(listOf(null, null), read.profiles.map { it.tagRaw })
        // A re-save writes no "t" for them.
        assertFalse(JSONArray(ProfileRegistry.encode(read)).getJSONObject(0).has("t"))
        // An unknown value is kept untouched through decode → encode → decode.
        val unknown = JSONArray(v19).also { it.getJSONObject(1).put("t", "family") }.toString()
        val again = ProfileRegistry.decode(ProfileRegistry.encode(ProfileRegistry.decode(unknown, 2)), 2)
        assertEquals("family", again.profiles[1].tagRaw)
        assertEquals(AccountTag.PERSONAL, again.profiles[1].tag)
        // Setting a tag writes its key; setting the one already stored is a no-op (the same instance).
        val set = ProfileRegistry.setTag(again, "work", AccountTag.WORK)
        assertEquals("work", set.profiles[1].tagRaw)
        assertTrue(ProfileRegistry.setTag(set, "work", AccountTag.WORK) === set)
        assertTrue(ProfileRegistry.setTag(set, "nobody", AccountTag.WORK) === set)
        // What v1.9 reads is unchanged: the same five fields, in the same shape, plus the one additive key.
        val o = JSONArray(ProfileRegistry.encode(set)).getJSONObject(1)
        assertEquals(setOf("k", "s", "l", "d", "v", "t"), o.keys().asSequence().toSet())
    }

    @Test fun `an added account carries the tag the sheet chose, Personal by default`() {
        val repo = store(listOf("A" to null, "B" to null), null)
        val work = repo.addProfile("Lab", Provider.CLAUDE, AccountTag.WORK)
        val personal = repo.addProfile("Side")
        assertEquals(AccountTag.WORK, repo.profileTag(work))
        assertEquals(AccountTag.PERSONAL, repo.profileTag(personal))
        assertEquals("personal", repo.registry().all().first { it.key == personal.key }.tagRaw)
    }

    @Test fun `the filter key is new - a v1_9 install never reads it`() {
        val repo = store(listOf("A" to "work", "B" to "personal"), null)
        repo.cacheSettings().setSummaryFilter(SummaryFilter.WORK)
        val prefs = context.getSharedPreferences("usage_cache", Context.MODE_PRIVATE)
        assertEquals("work", prefs.getString("summaryFilter", null))
        assertTrue(prefs.all.keys.contains("summaryFilter"))
    }

    // ---- fixtures: every filter case

    @Test fun `filter cases - the control, the segments, the filter in force, what is shown, the header, what is stored`() {
        val cases = fx.getJSONArray("filter").objects()
        assertTrue(cases.isNotEmpty())
        for (c in cases) {
            val name = c.getString("name")
            val e = c.getJSONObject("expected")
            val repo = store(accounts(c), c.str("rememberedFilter"))
            val cache = repo.cacheSettings()
            val all = repo.configuredProfiles()
            val tags = all.map { cache.tag(it) }
            assertEquals("$name: effective tags", e.getJSONArray("effectiveTags").let { a -> (0 until a.length()).map { a.getString(it) } }, tags.map { it.key })
            val shownControl = SummaryFilters.controlShown(all.size)
            assertEquals("$name: control", e.getBoolean("controlShown"), shownControl)
            if (e.isNull("segmentsEnabled")) {
                assertFalse(name, shownControl)
            } else {
                val segs = e.getJSONObject("segmentsEnabled")
                for (f in SummaryFilter.entries) assertEquals("$name: ${f.key} enabled", segs.getBoolean(f.key), SummaryFilters.enabled(f, tags))
            }
            val filter = cache.summaryFilter(all)
            assertEquals("$name: filter in force", e.getString("filter"), filter.key)
            val shown = all.filter { filter.matches(cache.tag(it)) }.map { it.label }
            assertEquals("$name: shown", e.getJSONArray("shown").let { a -> (0 until a.length()).map { a.getString(it) } }, shown)
            assertEquals("$name: one-line rows", e.getBoolean("oneLineRows"), com.robin.claudeusage.ui.SummaryRows.compact(shown.size))
            assertEquals("$name: snapshot header", e.getString("snapshotHeader"), filter.snapshotHeader)
            // The memory rule, run as the Summary does when it comes on screen. With one account there is no Summary, so
            // nothing is evaluated and the value stays as it was (the memory sequence "a second account added to a
            // single-account install" pins this; the filter case's storedAfter of "all" for one account is the
            // generator's simplification and is not asserted — reported for Step 2).
            repo.settleSummaryFilter()
            val after = cache.summaryFilterStored()
            if (shownControl) assertEquals("$name: stored after", e.str("storedAfter"), after)
            else assertEquals("$name: no Summary, nothing evaluated", c.str("rememberedFilter"), after)
        }
    }

    // ---- fixtures: every memory sequence

    @Test fun `memory sequences - replayed step by step against the real store`() {
        val cases = fx.getJSONArray("memory").objects()
        assertTrue(cases.isNotEmpty())
        for (c in cases) {
            val name = c.getString("name")
            val repo = store(accounts(c), c.str("storedFilter"))
            val cache = repo.cacheSettings()
            val steps = c.getJSONArray("steps").objects()
            val expected = c.getJSONArray("expected").objects()
            assertEquals("$name: one expectation per step", steps.size, expected.size)
            steps.forEachIndexed { i, s ->
                when (val op = s.getString("do")) {
                    "open" -> repo.settleSummaryFilter()
                    "tag" -> repo.setProfileTag(byName(repo, s.getString("account")), AccountTag.read(s.getString("tag")))
                    "add" -> {
                        val p = repo.addProfile(s.getString("account"), Provider.CLAUDE, AccountTag.read(s.getString("tag")))
                        creds().save(p, token) // the sign-in completes
                        repo.settleSummaryFilter()
                    }
                    "remove" -> assertTrue("$name: remove", runBlocking { repo.removeProfile(byName(repo, s.getString("account"))) })
                    "choose" -> cache.setSummaryFilter(SummaryFilter.parse(s.getString("filter"))!!)
                    else -> error("step $op")
                }
                val want = expected[i]
                assertEquals("$name: step ${i + 1} ($s) filter in force", want.getString("filter"), cache.summaryFilter(repo.configuredProfiles()).key)
                assertEquals("$name: step ${i + 1} ($s) stored", want.str("stored"), cache.summaryFilterStored())
            }
        }
    }

    @Test fun `memory - marking an account later never brings the old choice back, and an absent value stays absent`() {
        val repo = store(listOf("A" to "personal", "B" to "personal"), "work")
        val cache = repo.cacheSettings()
        repo.settleSummaryFilter()
        assertEquals("all", cache.summaryFilterStored())
        repo.setProfileTag(repo.configuredProfiles()[0], AccountTag.WORK)
        assertEquals("all", cache.summaryFilterStored())
        val fresh = store(listOf("A" to null, "B" to null), null)
        fresh.settleSummaryFilter()
        assertNull(fresh.cacheSettings().summaryFilterStored())
    }
}
