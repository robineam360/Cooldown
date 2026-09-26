package com.robin.claudeusage.widgets

import com.robin.claudeusage.alerts.Alerts
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.widgets.Transitions.Placed
import com.robin.claudeusage.widgets.Transitions.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** R7: the one transition alarm's next instant — each kind, the earliest, the edges. */
class TransitionsTest {

    private val now = 1_800_000_000_000L
    private val h = 60 * 60_000L

    private fun data(session: Long?, weekly: Long?) = UsageData(
        session = session?.let { UsageWindow(40.0, Instant.ofEpochMilli(it), null) },
        weekly = weekly?.let { UsageWindow(20.0, Instant.ofEpochMilli(it), null) },
        modelCaps = emptyList(),
    )

    private fun next(placed: List<Placed>, vararg s: Snapshot) =
        Transitions.nextTransitionAt(now, placed, s.toList())

    @Test
    fun kindA_theResetOfTheShownWindow() {
        val s = Snapshot("pro", data(now + 2 * h, now + 50 * h), fetchedAt = now)
        assertEquals(now + 2 * h, next(listOf(Placed(Face.NUMBER, "pro", FaceWindow.SESSION)), s))
    }

    @Test
    fun kindB_theStaleDim() {
        // The 5h reset is further out than fetchedAt + 6 h.
        val s = Snapshot("pro", data(now + 8 * h, now + 50 * h), fetchedAt = now - h)
        assertEquals(now - h + Alerts.STALE_DATA_MS, next(listOf(Placed(Face.RING, "pro", FaceWindow.SESSION)), s))
    }

    @Test
    fun kindC_aWeeklyCountdownStartsTickingADayOut() {
        val s = Snapshot("pro", data(null, now + 30 * h), fetchedAt = now)
        assertEquals(now + 6 * h, next(listOf(Placed(Face.COUNTDOWN, "pro", FaceWindow.WEEKLY)), s))
        // …only on a Countdown: a Number on Weekly waits for the stale dim instead.
        assertEquals(now + Alerts.STALE_DATA_MS, next(listOf(Placed(Face.NUMBER, "pro", FaceWindow.WEEKLY)), s))
    }

    @Test
    fun kindD_theStampGainsItsWeekdayADayAfterTheFetch() {
        // Stale already passed and both resets are past: the next change is the "as of"
        // stamp crossing 24 h, one ms past it because widgetClock adds the day beyond 24 h.
        val s = Snapshot("pro", data(now - h, now - h), fetchedAt = now - 20 * h)
        assertEquals(now + 4 * h + 1, next(listOf(Placed(Face.NUMBER, "pro", FaceWindow.SESSION)), s))
    }

    @Test
    fun earliestWins_acrossWidgetsAndKinds() {
        val pro = Snapshot("pro", data(now + 4 * h, now + 90 * h), fetchedAt = now)
        val gpt = Snapshot("gpt", data(null, now + 26 * h), fetchedAt = now - 3 * h)
        val placed = listOf(
            Placed(Face.NUMBER, "pro", FaceWindow.SESSION), // 4 h
            Placed(Face.COUNTDOWN, "gpt", FaceWindow.WEEKLY), // 2 h (26 − 24), stale at 3 h
        )
        assertEquals(now + 2 * h, next(placed, pro, gpt))
    }

    @Test
    fun aPastResetIsNotATransition() {
        // The reset already passed (S6 is drawn now); the next thing is the stale dim.
        val s = Snapshot("pro", data(now - 10 * 60_000L, now + 60 * h), fetchedAt = now - h)
        assertEquals(now - h + Alerts.STALE_DATA_MS, next(listOf(Placed(Face.NUMBER, "pro", FaceWindow.SESSION)), s))
        // Everything in the past — the stamp's weekday included (d) → nothing to arm.
        val old = Snapshot("pro", data(now - h, now - h), fetchedAt = now - 25 * h)
        assertNull(next(listOf(Placed(Face.COUNTDOWN, "pro", FaceWindow.WEEKLY)), old))
    }

    /** Rev H (CCBG-44 (Widget Fill)): a Ring may draw the other window as its companion. */
    @Test
    fun aRingCountsBothWindows() {
        val s = Snapshot("pro", data(now + 5 * h, now + 3 * h), fetchedAt = now)
        assertEquals(now + 3 * h, next(listOf(Placed(Face.RING, "pro", FaceWindow.SESSION)), s))
        // …a Number on 5h still waits for its own window.
        assertEquals(now + 5 * h, next(listOf(Placed(Face.NUMBER, "pro", FaceWindow.SESSION)), s))
    }

    @Test
    fun empty_nothingPlacedOrNoAccounts() {
        val s = Snapshot("pro", data(now + h, now + 50 * h), fetchedAt = now)
        assertNull(next(emptyList(), s))
        assertNull(next(listOf(Placed(Face.RING, "pro", FaceWindow.SESSION))))
    }

    @Test
    fun resolution_unassignedRemovedStripAndWeeklyOnly() {
        val pro = Snapshot("pro", data(now + 3 * h, now + 50 * h), fetchedAt = now)
        val teams = Snapshot("teams", data(now + h, now + 50 * h), fetchedAt = now)
        // Unassigned shows the first account in registry order (R5).
        assertEquals(now + 3 * h, next(listOf(Placed(Face.RING, null, FaceWindow.SESSION)), pro, teams))
        assertEquals(now + 3 * h, next(listOf(Placed(Face.RING, "", FaceWindow.SESSION)), pro, teams))
        // A removed account contributes nothing.
        assertNull(next(listOf(Placed(Face.RING, "gone", FaceWindow.SESSION)), pro, teams))
        // The Strip watches every account it draws.
        assertEquals(now + h, next(listOf(Placed(Face.STRIP, null, FaceWindow.SESSION)), pro, teams))
        // A weekly-only account asked for 5h shows Weekly, so its weekly reset counts.
        val gpt = Snapshot("gpt", data(null, now + 25 * h), fetchedAt = 0)
        assertEquals(now + 25 * h, next(listOf(Placed(Face.NUMBER, "gpt", FaceWindow.SESSION)), gpt))
        assertEquals(now + h, next(listOf(Placed(Face.COUNTDOWN, "gpt", FaceWindow.SESSION)), gpt))
    }
}
