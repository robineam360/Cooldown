package com.robin.claudeusage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCRM-70 (Plan Fit): the judgement logic in [PlanFit], exercised without any Android
 * runtime — every case here builds [SessionLog.Record]s by hand the way [SessionLog.records]
 * would hand them back, [SessionLog] itself being untestable in this module's plain-JUnit
 * suite (it needs a `Context`, and nothing here mocks Android).
 */
class PlanFitTest {

    private val weekMs = HistoryStats.WEEK_MS
    private val now = 2_000_000_000_000L

    /** A closed weekly window [weeksAgo] windows before [now] — 0 is the most recent one. */
    private fun w(weeksAgo: Int, peak: Double, hit: Boolean = false, plan: String? = null, cap: String? = null) =
        SessionLog.Record(SessionLog.WEEKLY, now - weeksAgo * weekMs, peak, hit, plan, cap)

    /** A closed 5-hour window [ago] "week-units" before [now] — spacing doesn't matter, only order. */
    private fun s(ago: Int, hit: Boolean) =
        SessionLog.Record(SessionLog.SESSION, now - ago * weekMs, if (hit) 100.0 else 50.0, hit)

    private fun compute(weekly: List<SessionLog.Record>, sessions: List<SessionLog.Record> = emptyList()) =
        PlanFit.compute(weekly, sessions, now)

    private fun fitted(weekly: List<SessionLog.Record>, sessions: List<SessionLog.Record> = emptyList()) =
        compute(weekly, sessions) as PlanFit.Reading.Fitted

    // --- not enough data ---

    @Test
    fun `no records at all yields NotEnoughData with zero recorded weeks`() {
        val reading = compute(emptyList())
        val r = reading as PlanFit.Reading.NotEnoughData
        assertEquals(0, r.recordedWeeks)
        assertNull(r.currentPlan)
        assertNull(r.planChange)
    }

    @Test
    fun `fewer than four recorded weeks never yields a verdict`() {
        for (n in 1..3) {
            val records = (0 until n).map { w(it, 40.0) }
            val reading = compute(records)
            val r = reading as PlanFit.Reading.NotEnoughData
            assertEquals("N=$n", n, r.recordedWeeks)
        }
    }

    @Test
    fun `exactly four recorded weeks is enough for a verdict`() {
        val records = (0..3).map { w(it, 40.0) }
        val reading = fitted(records)
        assertEquals(4, reading.recordedWeeks)
        assertEquals(PlanFit.Verdict.FITS, reading.verdict)
    }

    // --- FITS ---

    @Test
    fun `fits verdict for an unremarkable eight weeks`() {
        val peaks = listOf(40.0, 55.0, 30.0, 60.0, 45.0, 65.0, 35.0, 50.0)
        val records = peaks.mapIndexed { i, p -> w(i, p) }
        val reading = fitted(records)
        assertEquals(8, reading.recordedWeeks)
        assertEquals(0, reading.cappedCount)
        assertEquals(0, reading.heavyCount)
        assertEquals(8, reading.middleCount)
        assertEquals(0, reading.lightCount)
        assertEquals(PlanFit.Verdict.FITS, reading.verdict)
    }

    @Test
    fun `a holiday fortnight does not flip an otherwise-fitting account`() {
        // 2 light weeks of 8 (well under the ceil(3*8/4)=6 SMALL_PART threshold).
        val peaks = listOf(10.0, 50.0, 55.0, 15.0, 60.0, 45.0, 50.0, 65.0)
        val records = peaks.mapIndexed { i, p -> w(i, p) }
        val reading = fitted(records)
        assertEquals(2, reading.lightCount)
        assertEquals(PlanFit.Verdict.FITS, reading.verdict)
    }

    @Test
    fun `a single crunch week does not reach RUNS_OUT`() {
        val peaks = listOf(100.0, 40.0, 50.0, 30.0, 45.0, 55.0, 35.0, 50.0)
        val records = peaks.mapIndexed { i, p -> w(i, p, hit = p >= 100.0) }
        val reading = fitted(records)
        assertEquals(1, reading.cappedCount)
        assertEquals(PlanFit.Verdict.FITS, reading.verdict)
    }

    // --- RUNS_OUT boundaries (N=8: ceil(N/3)=3, ceil(2N/3)=6) ---

    @Test
    fun `RUNS_OUT fires at exactly ceil(N over 3) capped weeks`() {
        val records = (0..7).map { i -> w(i, if (i < 3) 100.0 else 40.0, hit = i < 3) }
        val reading = fitted(records)
        assertEquals(3, reading.cappedCount)
        assertEquals(PlanFit.Verdict.RUNS_OUT, reading.verdict)
    }

    @Test
    fun `one capped week short of the ceil(N over 3) threshold does not trigger RUNS_OUT`() {
        val records = (0..7).map { i -> w(i, if (i < 2) 100.0 else 40.0, hit = i < 2) }
        val reading = fitted(records)
        assertEquals(2, reading.cappedCount)
        assertEquals(PlanFit.Verdict.FITS, reading.verdict)
    }

    @Test
    fun `RUNS_OUT fires at exactly ceil(2N over 3) capped-plus-heavy weeks`() {
        // 1 capped + 5 heavy = 6 == ceil(16/3).
        val records = (0..7).map { i ->
            when {
                i == 0 -> w(i, 100.0, hit = true)
                i in 1..5 -> w(i, 80.0)
                else -> w(i, 20.0)
            }
        }
        val reading = fitted(records)
        assertEquals(1, reading.cappedCount)
        assertEquals(5, reading.heavyCount)
        assertEquals(PlanFit.Verdict.RUNS_OUT, reading.verdict)
    }

    @Test
    fun `one heavy week short of the ceil(2N over 3) threshold does not trigger RUNS_OUT`() {
        val records = (0..7).map { i ->
            when {
                i == 0 -> w(i, 100.0, hit = true)
                i in 1..4 -> w(i, 80.0)
                else -> w(i, 20.0)
            }
        }
        val reading = fitted(records)
        assertEquals(1, reading.cappedCount)
        assertEquals(4, reading.heavyCount)
        assertEquals(PlanFit.Verdict.FITS, reading.verdict)
    }

    // --- SMALL_PART boundaries ---

    @Test
    fun `SMALL_PART fires at exactly ceil(3N over 4) light weeks of 8`() {
        val records = (0..7).map { i -> w(i, if (i < 6) 10.0 else 50.0) }
        val reading = fitted(records)
        assertEquals(6, reading.lightCount)
        assertEquals(0, reading.cappedCount)
        assertEquals(PlanFit.Verdict.SMALL_PART, reading.verdict)
    }

    @Test
    fun `one light week short of the ceil(3N over 4) threshold of 8 does not trigger SMALL_PART`() {
        val records = (0..7).map { i -> w(i, if (i < 5) 10.0 else 50.0) }
        val reading = fitted(records)
        assertEquals(5, reading.lightCount)
        assertEquals(PlanFit.Verdict.FITS, reading.verdict)
    }

    @Test
    fun `SMALL_PART threshold at N=6 rounds up to 5 (ceil(18 over 4))`() {
        // Also exercises N less than the MAX_WEEKS target (a gap, or an account this young).
        val records = (0..5).map { i -> w(i, if (i < 5) 10.0 else 50.0) }
        val reading = fitted(records)
        assertEquals(6, reading.recordedWeeks)
        assertEquals(5, reading.lightCount)
        assertEquals(PlanFit.Verdict.SMALL_PART, reading.verdict)
    }

    @Test
    fun `a capped week blocks SMALL_PART even with an otherwise-qualifying light count`() {
        val records = (0..7).map { i -> if (i == 0) w(i, 100.0, hit = true) else w(i, 10.0) }
        val reading = fitted(records)
        assertEquals(7, reading.lightCount)
        assertEquals(1, reading.cappedCount)
        // Not RUNS_OUT either: 1 capped week is nowhere near either RUNS_OUT threshold at N=8.
        assertEquals(PlanFit.Verdict.FITS, reading.verdict)
    }

    // --- IDLE ---

    @Test
    fun `IDLE when every week's peak is zero`() {
        val records = (0..7).map { i -> w(i, 0.0) }
        val reading = fitted(records)
        assertEquals(8, reading.lightCount) // every zero week still bands as LIGHT
        assertEquals(PlanFit.Verdict.IDLE, reading.verdict)
    }

    @Test
    fun `IDLE reachable with as few as the minimum four weeks`() {
        val records = (0..3).map { i -> w(i, 0.0) }
        val reading = fitted(records)
        assertEquals(PlanFit.Verdict.IDLE, reading.verdict)
    }

    // --- per-model weekly cap (`mc`) ---

    @Test
    fun `a week is CAPPED via the mc tag even when the pool peak is low`() {
        val record = w(0, 10.0, hit = false, cap = "Opus")
        val records = listOf(record) + (1..3).map { w(it, 15.0) }
        val reading = fitted(records)
        val opusWeek = reading.weeks.first { it.resetAt == record.resetAt }
        assertEquals(PlanFit.Band.CAPPED, opusWeek.band)
        assertEquals("Opus", opusWeek.modelCap)
    }

    @Test
    fun `Opus-capped weeks alone can reach RUNS_OUT despite a low pool peak`() {
        // 5 of 8 weeks Opus-capped at a pool peak of only 10% each — the whole point of the
        // `mc` tag: a Max user locked out of Opus weekly reads as capped, not as 90% headroom.
        val records = (0..7).map { i ->
            if (i < 5) w(i, 10.0, cap = "Opus") else w(i, 20.0)
        }
        val reading = fitted(records)
        assertEquals(5, reading.cappedCount)
        assertEquals(PlanFit.Verdict.RUNS_OUT, reading.verdict)
        assertTrue(reading.weeks.filter { it.band == PlanFit.Band.CAPPED }.all { it.modelCap == "Opus" })
    }

    // --- gaps: a week the phone slept through is simply absent ---

    @Test
    fun `gaps in the log shrink N without breaking the reading`() {
        // Only 6 weekly records exist at all, irregularly spaced — some weeks were missed
        // entirely. N is the count of what's recorded, not a fixed 8.
        val records = listOf(0, 2, 4, 6, 9, 12).map { w(it, 40.0) }
        val reading = fitted(records)
        assertEquals(6, reading.recordedWeeks)
        assertEquals(PlanFit.Verdict.FITS, reading.verdict)
    }

    // --- plan changes ---

    @Test
    fun `a plan change inside the period restarts it and can leave too few weeks`() {
        val oldPlan = (3..7).map { w(it, 90.0, plan = "Pro") } // further back, irrelevant once cut
        val newPlan = (0..2).map { w(it, 40.0, plan = "Max") } // only 3 weeks since the change
        val reading = compute(oldPlan + newPlan) as PlanFit.Reading.NotEnoughData
        assertEquals(3, reading.recordedWeeks)
        assertEquals("Max", reading.currentPlan)
        val change = requireNotNull(reading.planChange)
        assertEquals("Pro", change.previousPlan)
        assertEquals(3, change.weeksAgo)
    }

    @Test
    fun `a plan change inside the period still yields a verdict once past four weeks on the new plan`() {
        val oldPlan = (5..7).map { w(it, 100.0, hit = true, plan = "Pro") } // would be RUNS_OUT if it leaked in
        val newPlan = (0..4).map { w(it, 40.0, plan = "Max") } // 5 weeks, all unremarkable
        val reading = fitted(oldPlan + newPlan)
        assertEquals(5, reading.recordedWeeks)
        assertEquals("Max", reading.currentPlan)
        assertEquals(0, reading.cappedCount) // the old plan's capped weeks must not leak in
        assertEquals(PlanFit.Verdict.FITS, reading.verdict)
        val change = requireNotNull(reading.planChange)
        assertEquals("Pro", change.previousPlan)
        assertEquals(5, change.weeksAgo)
    }

    @Test
    fun `untagged records are treated as the plan first observed after the update`() {
        // The leading weeks predate the CCRM-70 tagging change; only the trailing ones carry
        // a `pl` tag. They must all be read as one continuous period on "Max", not as a
        // phantom plan change.
        val untagged = (4..7).map { w(it, 30.0) }
        val tagged = (0..3).map { w(it, 30.0, plan = "Max") }
        val reading = fitted(untagged + tagged)
        assertEquals(8, reading.recordedWeeks)
        assertEquals("Max", reading.currentPlan)
        assertNull(reading.planChange)
    }

    @Test
    fun `an account with no plan tags anywhere still reads`() {
        val records = (0..7).map { w(it, 30.0) }
        val reading = fitted(records)
        assertEquals(8, reading.recordedWeeks)
        assertNull(reading.currentPlan)
        assertNull(reading.planChange)
        assertEquals(PlanFit.Verdict.FITS, reading.verdict)
    }

    // --- 5h cap hits ---

    @Test
    fun `five-hour cap hits are counted only inside the period`() {
        val weekly = (0..7).map { w(it, 40.0) }
        val sessions = listOf(
            s(1, hit = true),
            s(2, hit = true),
            s(3, hit = true),
            s(2, hit = false), // no hit: not counted
            s(50, hit = true), // long before the period starts: not counted
        )
        val reading = fitted(weekly, sessions)
        assertEquals(3, reading.fiveHourCapHits)
    }

    @Test
    fun `no session records is a plain zero, not a crash`() {
        val reading = fitted((0..7).map { w(it, 40.0) })
        assertEquals(0, reading.fiveHourCapHits)
    }

    // --- band boundaries ---

    @Test
    fun `band thresholds are inclusive at 25, 75 and 100, exclusive just below`() {
        val records = listOf(
            w(0, 24.9),               // light
            w(1, 25.0),               // middle
            w(2, 74.9),               // middle
            w(3, 75.0),               // heavy
            w(4, 99.9),               // heavy
            w(5, 100.0, hit = true),  // capped
            w(6, 10.0),               // light
            w(7, 50.0),               // middle
        )
        val reading = fitted(records)
        assertEquals(2, reading.lightCount)
        assertEquals(3, reading.middleCount)
        assertEquals(2, reading.heavyCount)
        assertEquals(1, reading.cappedCount)
    }

    @Test
    fun `weeks are exposed oldest to newest`() {
        val records = (0..7).map { w(it, 40.0 + it) }
        val reading = fitted(records)
        val resetAts = reading.weeks.map { it.resetAt }
        assertEquals(resetAts.sorted(), resetAts)
        assertFalse(reading.weeks.isEmpty())
    }
}
