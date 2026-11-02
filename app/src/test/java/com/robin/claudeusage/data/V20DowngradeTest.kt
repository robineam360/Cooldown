package com.robin.claudeusage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.ui.PaceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RUNBOOK-v2.0 Step 3's downgrade test: preferences written by v2.0 (every new key,
 * `vsPace` included) read through v1.9's accessors give v1.9's defaults. v1.9's
 * accessors are the ones this class still carries unchanged — [UsageCache.usageDisplay],
 * [UsageCache.usageLeft], [UsageCache.layout], [UsageCache.creditsVisible] — plus the raw
 * keys v1.9 reads.
 */
@RunWith(RobolectricTestRunner::class)
class V20DowngradeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `v2 prefs leave v1_9 reads at their defaults`() {
        val cache = UsageCache(context)
        val profile = cache.registry().first()
        val v19LayoutBefore = cache.layout(profile)
        val v19CreditsBefore = cache.creditsVisible(profile)

        // Everything v2.0 writes.
        cache.setUsageDisplay(PaceText.Mode.VS_PACE.key)
        cache.setHeadline(profile, Headline.FIVE_HOUR)
        cache.setShowModelLimits(profile, false)
        cache.setOpenOnSummary(false)
        cache.settleWeeklyFirstNotice()
        cache.markWeeklyFirstNoticeShown()

        // v1.9 reads Vs pace as Used: usageLeft() is true only for "left".
        assertFalse(cache.usageLeft())
        assertEquals("vsPace", cache.usageDisplay())
        // Nothing v1.9 reads moved.
        assertEquals(v19LayoutBefore, cache.layout(profile))
        assertEquals(v19CreditsBefore, cache.creditsVisible(profile))
        val prefs = context.getSharedPreferences("usage_cache", Context.MODE_PRIVATE)
        val v20Keys = setOf("openOnSummary", "weeklyFirstNotice", "usageDisplay")
        val perAccount = prefs.all.keys.filter { it.endsWith("headline") || it.endsWith("showModelLimits") }
        assertEquals(2, perAccount.size)
        assertTrue(prefs.all.keys.containsAll(v20Keys))

        // And v2.0 reads them back.
        assertEquals(PaceText.Mode.VS_PACE, cache.usageMode())
        assertEquals(Headline.FIVE_HOUR, cache.headline(profile))
        assertFalse(cache.showModelLimits(profile))
        assertFalse(cache.openOnSummary())
        assertEquals(WeeklyFirstNotice.DONE, cache.weeklyFirstNotice())
    }

    @Test
    fun `v2 defaults on a fresh install`() {
        val cache = UsageCache(context)
        val profile = cache.registry().first()
        assertEquals(PaceText.Mode.USED, cache.usageMode())
        assertEquals(Headline.WEEKLY, cache.headline(profile))
        assertTrue(cache.showModelLimits(profile))
        assertTrue(cache.openOnSummary())
        cache.settleWeeklyFirstNotice()
        assertEquals(WeeklyFirstNotice.DONE, cache.weeklyFirstNotice())
    }

    @Test
    fun `an install that fetched before v2 is owed the notice, once`() {
        val cache = UsageCache(context)
        val profile = cache.registry().first()
        cache.saveSuccess(profile, "{}", 1_000L)
        // Robolectric's CooldownApp.onCreate already settled this fresh install; replay
        // the first v2.0 start of an upgraded one.
        context.getSharedPreferences("usage_cache", Context.MODE_PRIVATE).edit().remove("weeklyFirstNotice").commit()
        cache.settleWeeklyFirstNotice()
        assertEquals(WeeklyFirstNotice.PENDING, cache.weeklyFirstNotice())
        cache.markWeeklyFirstNoticeShown()
        cache.settleWeeklyFirstNotice()
        assertEquals(WeeklyFirstNotice.DONE, cache.weeklyFirstNotice())
    }

    @Test
    fun `removing the legacy account sweeps its bare v2 keys and nothing app-wide`() {
        val cache = UsageCache(context)
        val legacy = cache.registry().all().first { it.key == Profile.LEGACY_KEY }
        cache.setHeadline(legacy, Headline.FIVE_HOUR)
        cache.setShowModelLimits(legacy, false)
        cache.setUsageDisplay(PaceText.Mode.VS_PACE.key)
        cache.setOpenOnSummary(false)
        cache.clearProfile(legacy)
        assertEquals(Headline.WEEKLY, cache.headline(legacy))
        assertTrue(cache.showModelLimits(legacy))
        assertEquals(PaceText.Mode.VS_PACE, cache.usageMode())
        assertFalse(cache.openOnSummary())
    }

    @Test
    fun `removing an account sweeps its v2 keys`() {
        val cache = UsageCache(context)
        val added = cache.registry().add("Second")
        cache.setHeadline(added, Headline.AUTO)
        cache.setShowModelLimits(added, false)
        cache.clearProfile(added)
        assertEquals(Headline.WEEKLY, cache.headline(added))
        assertTrue(cache.showModelLimits(added))
    }
}
