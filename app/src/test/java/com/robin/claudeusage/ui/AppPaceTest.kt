package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.SummaryRank
import com.robin.claudeusage.data.UsageWindow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

/**
 * RUNBOOK-v2.0 Step 5: the app's words for CCRM-96 (Weekly First), CCRM-97 (Pace Number),
 * CCRM-98 (Summary Tab), CCRM-100 (Vs Pace Display) and CCRM-102 (Spent Week Gate), on the
 * wireframes' fixtures: Wednesday 2:50 pm; Teams Weekly 52% resetting Sun 9:30 pm (38.9%
 * gone); Personal 50% resetting Fri 10:18 pm (67% gone).
 */
class AppPaceTest {

    private lateinit var saved: TimeZone
    private lateinit var savedLocale: java.util.Locale
    private val now = Instant.parse("2026-11-04T14:50:00Z") // a Wednesday
    private val sun = Instant.parse("2026-11-08T21:30:00Z")
    private val fri = Instant.parse("2026-11-06T22:18:00Z")

    @Before fun utc() {
        saved = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        savedLocale = java.util.Locale.getDefault(); java.util.Locale.setDefault(java.util.Locale.US)
    }
    @After fun restore() { TimeZone.setDefault(saved); java.util.Locale.setDefault(savedLocale) }

    private fun w(pct: Double?, resets: Instant?) = UsageWindow(pct, resets, null)

    // --- the Weekly card (A1/A2) ---

    @Test fun `open line is band, allowance and the reset day, emphasised over pace`() {
        val line = AppPace.weeklyOpen(w(52.0, sun), 38.9, use24h = false, now = now)!!
        assertEquals("Slow down · ≈11%/day until Sun", line.text)
        assertEquals(line.text, line.emphasis)
    }

    @Test fun `open line under pace is not emphasised`() {
        val line = AppPace.weeklyOpen(w(50.0, fri), 67.0, use24h = false, now = now)!!
        assertEquals("Room to spare · ≈22%/day until Fri", line.text)
        assertNull(line.emphasis)
    }

    @Test fun `under a day the open line carries hours and no day (E1)`() {
        val reset = now.plusSeconds(6 * 3600)
        val line = AppPace.weeklyOpen(w(60.0, reset), 96.0, use24h = false, now = now)!!
        assertEquals("Use before reset · 40% left, 6h", line.text)
    }

    @Test fun `spent week says out until, and nothing without a reading or a clock`() {
        assertEquals("Out until Sun 9:30 PM", AppPace.weeklyOpen(w(100.0, sun), 38.9, false, now)!!.text)
        assertNull(AppPace.weeklyOpen(w(null, sun), 38.9, false, now))
        assertNull(AppPace.weeklyOpen(w(10.0, null), null, false, now))
    }

    @Test fun `folded line bolds only the tight form`() {
        val line = AppPace.weeklyFolded(w(52.0, sun), 38.9, false, now)
        assertEquals("Slow down · ≈11%/day · resets Sun", line.text)
        assertEquals("Slow down · ≈11%/day", line.emphasis)
        assertEquals("Room to spare · ≈22%/day · resets Fri", AppPace.weeklyFolded(w(50.0, fri), 67.0, false, now).text)
        assertEquals("Starts when a message is sent", AppPace.weeklyFolded(w(0.0, null), null, false, now).text)
    }

    // --- the 5h card ---

    @Test fun `folded 5h line is the projection, never a pace verdict`() {
        val resets = Instant.now().plusSeconds(2 * 3600 + 20 * 60)
        val est = Projection.Estimate(ratePctPerHour = 15.0, hitsLimitAtMs = null, pctAtReset = 69.4)
        val line = AppPace.sessionFolded(w(34.0, resets), est, false)
        assertTrue(line.text, line.text.startsWith("Resets in 2h"))
        assertTrue(line.text.endsWith(" · on track for ~69%"))
        assertNull(line.emphasis)
        assertFalse(line.text.contains("pace"))
    }

    @Test fun `a 5h on course to run out is emphasised, and no estimate leaves the reset alone`() {
        val resets = Instant.now().plusSeconds(3 * 3600)
        val hits = Instant.parse("2026-11-04T15:25:00Z").toEpochMilli()
        val est = Projection.Estimate(ratePctPerHour = 30.0, hitsLimitAtMs = hits, pctAtReset = 100.0)
        val line = AppPace.sessionFolded(w(80.0, resets), est, false)
        assertEquals("on track for 100% at 3:25 PM", line.emphasis)
        assertTrue(line.text.endsWith(" · on track for 100% at 3:25 PM"))
        assertFalse(AppPace.sessionFolded(w(80.0, resets), null, false).text.contains("·"))
    }

    @Test fun `spent week note names the weekly reset`() {
        assertEquals(
            "Weekly limit reached — 5h can’t be used until Sun 9:30 PM",
            AppPace.spentWeekNote(w(100.0, sun), false),
        )
    }

    // --- figures (vs-pace wireframe §3, §5) ---

    @Test fun `figure follows the usage display`() {
        assertEquals("52%" to null, AppPace.figure(52.0, 39.0, PaceText.Mode.USED))
        assertEquals("48%" to "left", AppPace.figure(52.0, 39.0, PaceText.Mode.LEFT))
        assertEquals("−13%" to "vs pace", AppPace.figure(52.0, 39.0, PaceText.Mode.VS_PACE))
        assertEquals("+10%" to "vs pace", AppPace.figure(50.0, 60.0, PaceText.Mode.VS_PACE))
    }

    @Test fun `vs pace falls back to usage, without its word`() {
        assertEquals("100%" to null, AppPace.figure(100.0, 55.0, PaceText.Mode.VS_PACE))
        assertEquals("0%" to null, AppPace.figure(0.0, null, PaceText.Mode.VS_PACE))
        assertEquals("—" to null, AppPace.figure(null, 40.0, PaceText.Mode.VS_PACE))
    }

    // --- Summary rows (rev B) ---

    @Test fun `row words`() {
        assertEquals("Room to spare · ≈22%/day · resets Fri", SummaryRows.words(w(50.0, fri), 67.0, false, now))
        assertEquals("Out until Sun 9:30 PM", SummaryRows.words(w(100.0, sun), 89.8, false, now))
        assertEquals("No reading yet", SummaryRows.words(w(null, sun), 40.0, false, now))
        assertEquals("Not started yet", SummaryRows.words(w(0.0, null), null, false, now))
    }

    @Test fun `companion per group`() {
        val s5 = w(93.0, Instant.parse("2026-11-04T15:40:00Z"))
        assertEquals("5h 93% · back 3:40 PM", SummaryRows.companion(SummaryRank.Group.HELD, s5, true, PaceText.Mode.USED, false))
        assertEquals("5h 34%", SummaryRows.companion(SummaryRank.Group.ELIGIBLE, w(34.0, sun), true, PaceText.Mode.USED, false))
        assertEquals("5h 66%", SummaryRows.companion(SummaryRank.Group.ELIGIBLE, w(34.0, sun), true, PaceText.Mode.LEFT, false))
        assertEquals("no 5h", SummaryRows.companion(SummaryRank.Group.ELIGIBLE, null, false, PaceText.Mode.USED, false))
        assertNull(SummaryRows.companion(SummaryRank.Group.SPENT, s5, true, PaceText.Mode.USED, false))
        assertNull(SummaryRows.companion(SummaryRank.Group.NO_READING, null, false, PaceText.Mode.USED, false))
    }

    @Test fun `compact words (S2) and the six-account switch`() {
        val s5 = w(93.0, Instant.parse("2026-11-04T15:40:00Z"))
        assertEquals("5h · 3:40 PM", SummaryRows.compactWord(SummaryRank.Group.HELD, w(30.0, fri), 72.0, s5, false))
        assertEquals("Out · Thu", SummaryRows.compactWord(SummaryRank.Group.SPENT, w(100.0, Instant.parse("2026-11-05T08:00:00Z")), 89.8, null, false))
        assertEquals("No reading", SummaryRows.compactWord(SummaryRank.Group.NO_READING, null, null, null, false))
        assertEquals("Slow down", SummaryRows.compactWord(SummaryRank.Group.ELIGIBLE, w(52.0, sun), 38.9, null, false))
        assertFalse(SummaryRows.compact(5))
        assertTrue(SummaryRows.compact(6))
        assertTrue(SummaryRows.dimmed(SummaryRank.Group.SPENT))
        assertFalse(SummaryRows.dimmed(SummaryRank.Group.HELD))
    }

    @Test fun `pace pip sits left for room, centre on pace, right when over`() {
        assertEquals(3f / 40f, SummaryRows.pipFraction(-100.0), 1e-6f)
        assertEquals(0.5f, SummaryRows.pipFraction(0.0), 1e-6f)
        assertEquals(37f / 40f, SummaryRows.pipFraction(150.0), 1e-6f)
        assertEquals(SummaryRows.PipTone.ROOM, SummaryRows.pipTone(Pace.pace(50.0, 67.0)))
        assertEquals(SummaryRows.PipTone.OVER, SummaryRows.pipTone(Pace.pace(52.0, 38.9)))
        assertEquals(SummaryRows.PipTone.ON_PACE, SummaryRows.pipTone(4.0))
        assertEquals(SummaryRows.PipTone.NEARLY_OUT, SummaryRows.pipTone(60.0))
    }

    // --- the Pace map ---

    @Test fun `map frame maps week gone and used onto the plot`() {
        val f = PaceMapGeometry.frame(355f, 255f, 1f)
        assertEquals(30f, f.x(0.0), 1e-4f)
        assertEquals(345f, f.x(100.0), 1e-4f)
        assertEquals(229f, f.y(0.0), 1e-4f)
        assertEquals(12f, f.y(100.0), 1e-4f)
        assertEquals(12f, f.y(130.0), 1e-4f) // clamped
    }

    @Test fun `trail keeps this week's samples in order and ends on the live point`() {
        val week = Projection.WEEKLY_MS
        val reset = sun.toEpochMilli()
        val start = reset - week
        val samples = listOf(
            start + week / 2 to 30.0,
            start - 1000 to 90.0, // last week: dropped
            start + week / 10 to 5.0,
        )
        val t = PaceMapGeometry.trail(samples, reset, week, 60.0, 40.0)
        assertEquals(listOf(10.0 to 5.0, 50.0 to 30.0, 60.0 to 40.0), t.map { (x, y) -> Math.round(x).toDouble() to y })
        assertTrue(PaceMapGeometry.labelLeft(72.0))
        assertFalse(PaceMapGeometry.labelLeft(67.0))
        assertTrue(PaceMapGeometry.labelBelow(100.0))
    }
}
