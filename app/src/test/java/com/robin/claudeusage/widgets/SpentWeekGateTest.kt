package com.robin.claudeusage.widgets

import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.notify.LeadLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * CCRM-102 (Spent Week Gate): while the week is spent the 5h stays drawn but disabled —
 * on widget faces "Weekly spent", dimmed; in the notification panel, the note.
 */
class SpentWeekGateTest {

    private val now = Instant.parse("2026-11-04T14:50:00Z")
    private val hour = 3_600_000L

    private fun input(weeklyPct: Double, window: FaceWindow): Pair<FaceInput, AccountInput> {
        val data = UsageData(
            session = UsageWindow(12.0, now.plusMillis(2 * hour), null),
            weekly = UsageWindow(weeklyPct, now.plusMillis(50 * hour), null),
            modelCaps = emptyList(),
        )
        val a = AccountInput("k", "Teams", Provider.CLAUDE, 0xFFE59980.toInt(), data, now.toEpochMilli(), AuthState.OK)
        val i = FaceInput(
            face = Face.NUMBER, accountKey = "k", window = window, background = FaceBackground.SOLID,
            dark = true, accounts = listOf(a), usageLeft = false, showOverPace = true, synthetic = false,
            unavailable = false, nowMs = now.toEpochMilli(),
        )
        return i to a
    }

    @Test
    fun `a 5h face greys and says Weekly spent while the week is spent`() {
        val (i, a) = input(100.0, FaceWindow.SESSION)
        val c = FaceStates.cell(i, a, FaceWindow.SESSION, unassigned = false)
        assertTrue(c.weekGated)
        assertTrue(c.dim)
        assertEquals("Weekly spent", c.sub)
        assertEquals("12%", c.figure)
        assertNull(c.estimate)
    }

    @Test
    fun `no gate below the spent line or on a Weekly face`() {
        val (i, a) = input(99.4, FaceWindow.SESSION)
        assertFalse(FaceStates.cell(i, a, FaceWindow.SESSION, unassigned = false).weekGated)
        val (j, b) = input(100.0, FaceWindow.WEEKLY)
        assertFalse(FaceStates.cell(j, b, FaceWindow.WEEKLY, unassigned = false).weekGated)
    }

    @Test
    fun `the note names the weekly reset`() {
        val weekly = UsageWindow(100.0, now.plusMillis(50 * hour), null)
        assertTrue(LeadLines.spentWeekNote(weekly, use24h = false).startsWith("Weekly limit reached — 5h can’t be used until "))
    }
}
