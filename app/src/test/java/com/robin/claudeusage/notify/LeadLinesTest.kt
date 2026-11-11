package com.robin.claudeusage.notify

import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.ui.PaceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * RUNBOOK-v2.0 Step 4: the notification's lines once Weekly leads — CCRM-96 (Weekly First),
 * CCRM-97 (Pace Number), CCRM-100 (Vs Pace Display); `design/2026-11-02-weekly-first-pace.html`.
 */
class LeadLinesTest {

    private val hour = 3_600_000L
    private val now = Instant.parse("2026-11-04T14:50:00Z")
    private fun w(pct: Double?, inMs: Long?) = UsageWindow(pct, inMs?.let { now.plusMillis(it) }, null)

    private fun pace(weekly: UsageWindow?, avail: Float, scale: Float = 1f) =
        LeadLines.weeklyPace(weekly, now, false, avail, 12f, scale, ZoneOffset.UTC, Locale.US)

    @Test
    fun `the pace line replaces the band words and the allowance`() {
        // Teams: 52% used, 38.9% of the week gone, 102.7 h left, so 22h 3m ahead of the pace mark.
        val weekly = w(52.0, (102.7 * hour).toLong())
        val full = pace(weekly, 190f)!!
        // CCRM-108 (Reset Named): the reset is named, and a reset days away keeps its weekday to the last.
        assertEquals("22h 3m ahead · resets Sun 9:32 PM", full.form.text)
        assertEquals("22h 3m ahead", full.form.tinted)
        assertEquals(PaceText.VsTone.OVER, full.form.tone)
        assertEquals("22h 3m ahead · Sun 9:32 PM", pace(weekly, 184f)!!.form.text)
        assertEquals("22h 3m ahead · Sun", pace(weekly, 150f)!!.form.text)
        assertEquals("22h 3m ahead", pace(weekly, 100f)!!.form.text)
        // The band words and the allowance leave the line.
        assertTrue(!full.form.text.contains("%/day") && !full.form.text.contains("Slow down"))
    }

    @Test
    fun `under the mark the line says behind, on it the mark`() {
        val under = pace(w(20.0, (102.7 * hour).toLong()), 184f)!!
        assertEquals(PaceText.VsTone.UNDER, under.form.tone)
        assertTrue(under.form.text, under.form.text.contains(" behind · "))
        val on = pace(w(34.7, (110 * hour)), 184f)!!
        assertTrue(on.form.text, on.form.text.startsWith("On the mark · "))
        assertNull(on.form.tinted)
    }

    @Test
    fun `no pace line without a reading or a reset, nor for a spent week`() {
        assertNull(pace(w(null, hour), 184f))
        assertNull(pace(w(10.0, null), 184f))
        assertNull(pace(w(100.0, 50 * hour), 184f))
        assertNull(pace(null, 184f))
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
        // CCRM-104 (Pace Line Parity) D1: the Vs pace figure is signed, −13.1 toward zero (core §6.3).
        assertEquals("−13%", LeadLines.figure(52.0, 38.9, PaceText.Mode.VS_PACE))
        assertEquals("+19%", LeadLines.figure(20.0, 39.0, PaceText.Mode.VS_PACE))
        assertEquals("0%", LeadLines.figure(39.0, 39.0, PaceText.Mode.VS_PACE))
        assertEquals("100%", LeadLines.figure(100.0, 80.0, PaceText.Mode.VS_PACE)) // spent: the usage figure
        assertEquals("—", LeadLines.figure(null, 38.9, PaceText.Mode.VS_PACE))
    }

    @Test
    fun `the ring figure stays unsigned in Vs pace`() {
        assertEquals("13%", LeadLines.ringFigure(52.0, 38.9, PaceText.Mode.VS_PACE))
        assertEquals("19%", LeadLines.ringFigure(20.0, 39.0, PaceText.Mode.VS_PACE))
        assertEquals("52%", LeadLines.ringFigure(52.0, 38.9, PaceText.Mode.USED))
        assertEquals("48%", LeadLines.ringFigure(52.0, 38.9, PaceText.Mode.LEFT))
        assertEquals("—", LeadLines.ringFigure(null, 38.9, PaceText.Mode.VS_PACE))
    }

    @Test
    fun `the under over on pace caption is gone, LEFT stays (D2)`() {
        assertNull(LeadLines.figureCaption(52.0, PaceText.Mode.VS_PACE))
        assertNull(LeadLines.figureCaption(52.0, PaceText.Mode.USED))
        assertEquals("LEFT", LeadLines.figureCaption(52.0, PaceText.Mode.LEFT))
        assertNull(LeadLines.figureCaption(null, PaceText.Mode.LEFT))
    }

    private fun caption(weekly: UsageWindow?, elapsed: Double?, mode: PaceText.Mode, avail: Float) =
        LeadLines.weeklyCaption(weekly, elapsed, mode, avail, 12f, 1f)?.form

    @Test
    fun `line 2 is the pace mark caption, permanently (D4)`() {
        val weekly = w(52.0, (102.7 * hour).toLong())
        assertEquals("39% pace mark · 13 over", caption(weekly, 38.9, PaceText.Mode.USED, 184f))
        assertEquals("61% pace mark · 13 over", caption(weekly, 38.9, PaceText.Mode.LEFT, 184f))
        assertEquals("52% used · 39% pace mark", caption(weekly, 38.9, PaceText.Mode.VS_PACE, 184f))
    }

    @Test
    fun `line 2 falls back to its distance clause in a narrow column, never mid-word`() {
        val weekly = w(52.0, (102.7 * hour).toLong())
        assertEquals("13 over", caption(weekly, 38.9, PaceText.Mode.USED, 100f))
        assertEquals("52% used", caption(weekly, 38.9, PaceText.Mode.VS_PACE, 100f))
        assertEquals("On the mark", caption(w(39.0, hour), 39.0, PaceText.Mode.USED, 60f))
    }

    @Test
    fun `no caption when spent, unclocked or unread`() {
        assertNull(caption(w(100.0, hour), 80.0, PaceText.Mode.USED, 184f))
        assertNull(caption(w(52.0, null), null, PaceText.Mode.USED, 184f))
        assertNull(caption(w(null, hour), 38.9, PaceText.Mode.USED, 184f))
        assertNull(caption(null, null, PaceText.Mode.USED, 184f))
    }
}
