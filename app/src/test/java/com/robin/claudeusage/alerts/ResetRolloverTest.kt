package com.robin.claudeusage.alerts

import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.UsageCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the reset-ping rollover rules, and CCBG-25 (Idle Reset Silence) above all: an
 * account that is idle across its reset must still be told the window came back.
 *
 * Plain JUnit, no Robolectric — [ResetRollover] is deliberately free of Android types, so
 * every arm can be checked without an emulator or a shadow `Context`.
 */
class ResetRolloverTest {

    private val now = 1_757_500_000_000L // an arbitrary but fixed "poll happened here"
    private val fiveHours = Projection.SESSION_MS

    /** The previous window's reset instant, half an hour before this poll. */
    private val closedAt = now - 30 * 60_000L

    private fun idle(
        mode: String = UsageCache.RESET_ALWAYS,
        lastSeenKey: Long = closedAt,
        storedPeak: Double = 96.0,
    ) = ResetRollover.decide(
        windowLabel = "5h",
        windowKey = null,
        pct = null,
        lastSeenKey = lastSeenKey,
        storedPeak = storedPeak,
        mode = mode,
        windowLengthMs = fiveHours,
        nowMs = now,
        nextResetPhrase = null,
    )

    // --- CCBG-25 (Idle Reset Silence): no window in the payload ------------------------

    @Test
    fun `an idle rollover pings once, logs the closed window, and clears the key`() {
        val out = idle()

        // The SessionLog entry is the window that closed, with the peak we accumulated.
        assertNotNull("expected a SessionLog entry", out.log)
        val log = out.log!!
        assertEquals(closedAt, log.resetAt)
        assertEquals(96.0, log.peakPct, 0.001)
        assertEquals(false, log.hitLimit)

        // Tight-surface wording, and no "Next reset …" clause: with no window there is no
        // next reset to name — it starts when a message is sent.
        assertNotNull("expected a ping", out.ping)
        val ping = out.ping!!
        assertEquals("5h reset", ping.title)
        assertEquals("Usage is back at 0%.", ping.body)

        // Cleared, so the next poll finds nothing to roll over and the next real window
        // starts from a clean peak.
        assertEquals(0L, out.storeWindowKey)
        assertEquals(0.0, out.storePeak, 0.001)
    }

    @Test
    fun `a second idle poll after the rollover says nothing`() {
        val first = idle()
        // Feed the first outcome's stored values straight back in, as the caller would.
        val second = idle(lastSeenKey = first.storeWindowKey, storedPeak = first.storePeak)
        assertNull(second.log)
        assertNull(second.ping)
        assertEquals(0L, second.storeWindowKey)
        assertEquals(0.0, second.storePeak, 0.001)
    }

    @Test
    fun `a fresh install with no window at all stays silent`() {
        val out = idle(lastSeenKey = 0L, storedPeak = 0.0)
        assertNull(out.log)
        assertNull(out.ping)
        assertEquals(0L, out.storeWindowKey)
        assertEquals(0.0, out.storePeak, 0.001)
    }

    @Test
    fun `a window that has not reached its reset yet is not a rollover`() {
        // The payload can drop the window transiently; only a reset instant already in the
        // past means the window is genuinely over. Nothing is written back either way.
        val future = now + 90 * 60_000L
        val out = idle(lastSeenKey = future, storedPeak = 42.0)
        assertNull(out.log)
        assertNull(out.ping)
        assertEquals(future, out.storeWindowKey)
        assertEquals(42.0, out.storePeak, 0.001)
    }

    @Test
    fun `mode Off still records the closed window, it just does not ping`() {
        val out = idle(mode = UsageCache.RESET_OFF)
        assertNotNull(out.log)
        assertNull(out.ping)
        // The bookkeeping is not the ping's: History must not depend on the ping mode.
        assertEquals(0L, out.storeWindowKey)
    }

    @Test
    fun `If busy pings a hot window and skips a quiet one`() {
        val hot = idle(mode = UsageCache.RESET_SMART, storedPeak = UsageCache.SMART_RESET_MIN_PCT)
        assertNotNull(hot.ping)

        val quiet = idle(mode = UsageCache.RESET_SMART, storedPeak = UsageCache.SMART_RESET_MIN_PCT - 0.5)
        assertNull(quiet.ping)
        assertNotNull("the log is unconditional", quiet.log)
    }

    @Test
    fun `a window run to the limit is logged as having hit it`() {
        val out = idle(storedPeak = 100.0)
        assertEquals(true, out.log!!.hitLimit)
    }

    // --- the live-window arm, unchanged by CCBG-25 -------------------------------------

    @Test
    fun `a rollover seen with a live window still names the next reset`() {
        val out = ResetRollover.decide(
            windowLabel = "5h",
            windowKey = now + 4 * 60 * 60_000L, // the fresh window, four hours to run
            pct = 3.0,
            lastSeenKey = closedAt,
            storedPeak = 88.0,
            mode = UsageCache.RESET_ALWAYS,
            windowLengthMs = fiveHours,
            nowMs = now,
            nextResetPhrase = "in 4h 0m",
        )
        val log = out.log!!
        val ping = out.ping!!
        assertEquals(closedAt, log.resetAt)
        assertEquals(88.0, log.peakPct, 0.001)
        assertEquals("5h reset", ping.title)
        assertEquals("Usage is back at 3%. Next reset in 4h 0m.", ping.body)
        // The fresh window becomes the identity, and its reading is the new peak.
        assertEquals(now + 4 * 60 * 60_000L, out.storeWindowKey)
        assertEquals(3.0, out.storePeak, 0.001)
    }

    @Test
    fun `a live window that is the same one only raises the peak`() {
        // Drift, not a reset: the server slides resets_at on nearly every poll (CCBG-4
        // (Alert Dedup)), so proximity decides — a second inside tolerance is one window.
        val key = now + 60_000L
        val out = ResetRollover.decide(
            windowLabel = "5h",
            windowKey = key,
            pct = 41.0,
            lastSeenKey = key - 1_000L,
            storedPeak = 39.0,
            mode = UsageCache.RESET_ALWAYS,
            windowLengthMs = fiveHours,
            nowMs = now,
            nextResetPhrase = "in 1m",
        )
        assertNull(out.log)
        assertNull(out.ping)
        assertEquals(key, out.storeWindowKey)
        assertEquals(41.0, out.storePeak, 0.001)
        // A dip in the reading never lowers the peak.
        val dipped = ResetRollover.decide(
            windowLabel = "5h",
            windowKey = key,
            pct = 12.0,
            lastSeenKey = key - 1_000L,
            storedPeak = 41.0,
            mode = UsageCache.RESET_ALWAYS,
            windowLengthMs = fiveHours,
            nowMs = now,
            nextResetPhrase = "in 1m",
        )
        assertEquals(41.0, dipped.storePeak, 0.001)
    }

    @Test
    fun `the first window after a fresh install is adopted without a ping`() {
        val key = now + 3 * 60 * 60_000L
        val out = ResetRollover.decide(
            windowLabel = "5h",
            windowKey = key,
            pct = 7.0,
            lastSeenKey = 0L,
            storedPeak = 0.0,
            mode = UsageCache.RESET_ALWAYS,
            windowLengthMs = fiveHours,
            nowMs = now,
            nextResetPhrase = "in 3h 0m",
        )
        assertNull(out.log)
        assertNull(out.ping)
        assertEquals(key, out.storeWindowKey)
        assertEquals(7.0, out.storePeak, 0.001)
    }

    // --- the weekly window, which always carries a resets_at ---------------------------

    @Test
    fun `the weekly window is untouched by the idle arm`() {
        val week = Projection.WEEKLY_MS
        val closedWeek = now - 2 * 60 * 60_000L
        val out = ResetRollover.decide(
            windowLabel = "Weekly",
            windowKey = now + week - 2 * 60 * 60_000L,
            pct = 1.0,
            lastSeenKey = closedWeek,
            storedPeak = 74.0,
            mode = UsageCache.RESET_ALWAYS,
            windowLengthMs = week,
            nowMs = now,
            nextResetPhrase = "in 6d 22h",
        )
        val ping = out.ping!!
        assertEquals("Weekly reset", ping.title)
        assertEquals("Usage is back at 1%. Next reset in 6d 22h.", ping.body)
        assertEquals(closedWeek, out.log!!.resetAt)
    }
}
