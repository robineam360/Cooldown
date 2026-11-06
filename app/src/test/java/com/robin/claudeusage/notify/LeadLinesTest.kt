package com.robin.claudeusage.notify

import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.ui.PaceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * RUNBOOK-v2.0 Step 4: the notification's lines once Weekly leads — CCRM-96 (Weekly First),
 * CCRM-97 (Pace Number), CCRM-100 (Vs Pace Display); `design/2026-11-02-weekly-first-pace.html`.
 */
class LeadLinesTest {

    private val hour = 3_600_000L
    private val now = Instant.parse("2026-11-04T14:50:00Z")
    private fun w(pct: Double?, inMs: Long?) = UsageWindow(pct, inMs?.let { now.plusMillis(it) }, null)

    @Test
    fun `pace words are band and allowance`() {
        // Teams: 52% used, 38.9% of the week gone, 102.7 h left → Slow down · ≈11%/day.
        val weekly = w(52.0, (102.7 * hour).toLong())
        assertEquals("Slow down · ≈11%/day", LeadLines.paceWords(weekly, 38.9, now))
        assertEquals("Slow down · ≈11%/day to last", LeadLines.paceWords(weekly, 38.9, now, toLast = true))
    }

    @Test
    fun `to last never follows the hours form`() {
        val lastDay = w(60.0, 6 * hour)
        assertEquals("Use before reset · 40% left, 6h", LeadLines.paceWords(lastDay, 96.4, now, toLast = true))
    }

    @Test
    fun `no verdict without a reading or a reset`() {
        assertNull(LeadLines.paceWords(w(null, hour), 50.0, now))
        assertNull(LeadLines.paceWords(w(10.0, null), null, now))
        assertNull(LeadLines.weeklyLine(w(10.0, null), null, use24h = false))
    }

    @Test
    fun `the ring says its window aloud, since no 5h text is drawn`() {
        // CCRM-103 (Hybrid Notification), shape grammar rev D: the ring replaced "· 5h 34%".
        assertEquals("5-hour window, 34 percent", LeadLines.ringDescription("34%"))
        assertEquals("5-hour window, no reading", LeadLines.ringDescription("—"))
    }

    @Test
    fun `the expanded ring's time line is the 5h's countdown, none without a clock`() {
        val line = LeadLines.ringTimeLeft(UsageWindow(34.0, Instant.now().plusSeconds(2 * 3600 + 20 * 60 + 30), null))
        assertEquals("in 2h 20m", line)
        assertNull(LeadLines.ringTimeLeft(w(34.0, null)))
        assertNull(LeadLines.ringTimeLeft(null))
    }

    @Test
    fun `the header names the Weekly whatever leads`() {
        assertEquals("Teams · Weekly", LeadLines.headerTitle("Teams"))
    }

    @Test
    fun `figures follow the three display modes`() {
        assertEquals("52%", LeadLines.figure(52.0, 38.9, PaceText.Mode.USED))
        assertEquals("48%", LeadLines.figure(52.0, 38.9, PaceText.Mode.LEFT))
        assertEquals("13%", LeadLines.figure(52.0, 38.9, PaceText.Mode.VS_PACE)) // −13.1 toward zero (core §6.3, 2026-11-06)
        assertEquals("—", LeadLines.figure(null, 38.9, PaceText.Mode.VS_PACE))
        assertEquals("OVER PACE", LeadLines.figureCaption(52.0, 38.9, PaceText.Mode.VS_PACE))
        assertNull(LeadLines.figureCaption(100.0, 80.0, PaceText.Mode.VS_PACE))
        assertEquals("LEFT", LeadLines.figureCaption(52.0, 38.9, PaceText.Mode.LEFT))
        assertNull(LeadLines.figureCaption(52.0, 38.9, PaceText.Mode.USED))
    }

    @Test
    fun `second line names the distance and the reset day`() {
        val weekly = w(50.0, 60 * hour)
        val line = LeadLines.weeklySecondLine(weekly, 60.0, PaceText.Mode.USED)!!
        assert(line.startsWith("10 under the pace mark (60%) · resets ")) { line }
        assert(LeadLines.weeklySecondLine(weekly, 60.0, PaceText.Mode.VS_PACE)!!.startsWith("50% used (mark 60%) · resets "))
    }
}
