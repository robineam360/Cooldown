package com.robin.claudeusage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.widgets.WidgetHost
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * R8 (CCRM-15 (Above-Pace Verification)): the synthetic series reaches every read seam,
 * never a store, and Off hands back the real data untouched.
 */
@RunWith(RobolectricTestRunner::class)
class SyntheticSeriesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun off() = SyntheticSeries.set(SyntheticSeries.Mode.OFF)

    private val realBody =
        """{"five_hour":{"utilization":12.0,"resets_at":"2099-01-01T00:00:00Z"},""" +
            """"seven_day":{"utilization":5.0,"resets_at":"2099-01-05T00:00:00Z"}}"""

    private fun storesOnDisk(): Map<String, String> {
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        return (context.filesDir.listFiles().orEmpty().toList() + prefsDir.listFiles().orEmpty().toList())
            .filter { it.isFile }
            .associate { it.path to it.readText() }
    }

    @Test
    fun noStoreChangesWhileItIsOn() {
        val cache = UsageCache(context)
        val profile = cache.registry().first()
        val now = System.currentTimeMillis()
        cache.saveSuccess(profile, realBody, now)
        HistoryStore(context).record(profile, cache.realSnapshot(profile).data!!, now)
        SessionLog(context).record(profile, SessionLog.SESSION, now, 50.0, hitLimit = false)
        val before = storesOnDisk()

        for (mode in SyntheticSeries.Mode.entries) {
            SyntheticSeries.set(mode)
            cache.snapshot(profile).data
            HistoryStore(context).points(profile)
            WidgetHost.accounts(context, cache, dark = true, withEstimates = true)
            WidgetHost.snapshots(cache)
        }

        assertEquals(before, storesOnDisk())
    }

    @Test
    fun onReplacesTheNumbers_offReturnsTheRealOnes() {
        val cache = UsageCache(context)
        val profile = cache.registry().first()
        val now = System.currentTimeMillis()
        cache.saveSuccess(profile, realBody, now)
        HistoryStore(context).record(profile, cache.realSnapshot(profile).data!!, now)

        SyntheticSeries.set(SyntheticSeries.Mode.ABOVE_PACE, now)
        val s = cache.snapshot(profile)
        assertTrue(s.synthetic)
        assertEquals(62.0, s.data!!.session!!.percent!!, 0.0)
        assertEquals(31.0, s.data!!.weekly!!.percent!!, 0.0)
        // The real read stays real while it is on.
        assertEquals(12.0, cache.realSnapshot(profile).data!!.session!!.percent!!, 0.0)

        SyntheticSeries.set(SyntheticSeries.Mode.AT_100, now)
        assertEquals(100.0, cache.snapshot(profile).data!!.session!!.percent!!, 0.0)

        SyntheticSeries.set(SyntheticSeries.Mode.NO_DATA, now)
        assertNull(cache.snapshot(profile).data)
        assertTrue(HistoryStore(context).points(profile).isEmpty())

        SyntheticSeries.set(SyntheticSeries.Mode.OFF)
        val back = cache.snapshot(profile)
        assertFalse(back.synthetic)
        assertEquals(12.0, back.data!!.session!!.percent!!, 0.0)
        assertEquals(1, HistoryStore(context).points(profile).size)
    }

    @Test
    fun abovePace_theSessionCurveCrossesTheDiagonal_andBindsToItsWindow() {
        val now = 1_800_000_000_000L
        SyntheticSeries.set(SyntheticSeries.Mode.ABOVE_PACE, now)
        val real = Snapshot(null, 0L, "Never fetched", 0L, AuthState.OK)
        val session = SyntheticSeries.apply(real).data!!.session!!
        val reset = session.resetsAt!!.toEpochMilli()
        val start = reset - Projection.SESSION_MS
        val samples = Projection.sessionSamples(
            SyntheticSeries.points(emptyList()), reset, Projection.SESSION_MS,
        )
        assertTrue(samples.size > 4)
        val pace = { t: Long -> 100.0 * (t - start) / Projection.SESSION_MS }
        assertTrue("starts under pace", samples.first().second < pace(samples.first().first))
        assertTrue("ends over pace", samples.last().second > pace(samples.last().first))
        // The curve ends on the headline figure, at the anchor.
        assertEquals(now, samples.last().first)
        assertEquals(62.0, samples.last().second, 0.0)
    }
}
