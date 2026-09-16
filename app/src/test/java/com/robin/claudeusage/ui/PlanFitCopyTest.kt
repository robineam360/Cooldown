package com.robin.claudeusage.ui

import com.robin.claudeusage.data.HistoryStats
import com.robin.claudeusage.data.PlanFit
import com.robin.claudeusage.data.SessionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [PlanFitCopy] — the wording for CCRM-70 (Plan Fit)'s tonal block, pinned against every
 * state in `design/2026-09-16-plan-fit-and-account-order.html` §1-4 (labelled a-m there;
 * referenced by that label below). Builds real [PlanFit.Reading]s via [PlanFit.compute]
 * the same way `PlanFitTest` does, then asserts on the four rendered lines — this is the
 * layer the wireframe's exact strings live in.
 */
class PlanFitCopyTest {

    private val weekMs = HistoryStats.WEEK_MS
    private val now = 2_000_000_000_000L

    /** A closed weekly window [weeksAgo] windows before [now] — 0 is the most recent one. */
    private fun w(weeksAgo: Int, peak: Double, hit: Boolean = false, plan: String? = null, cap: String? = null) =
        SessionLog.Record(SessionLog.WEEKLY, now - weeksAgo * weekMs, peak, hit, plan, cap)

    /** A closed 5-hour window [ago] "week-units" before [now] — spacing doesn't matter, only order. */
    private fun s(ago: Int, hit: Boolean) =
        SessionLog.Record(SessionLog.SESSION, now - ago * weekMs, if (hit) 100.0 else 50.0, hit)

    private fun reading(weekly: List<SessionLog.Record>, sessions: List<SessionLog.Record> = emptyList()) =
        PlanFit.compute(weekly, sessions, now)

    // --- state a: RUNS_OUT, generic cap, with the 5h line ---

    @Test
    fun `state a - runs out, generic cap, 5h line present`() {
        val weekly = (0..7).map { i -> w(i, if (i < 3) 100.0 else 60.0, hit = i < 3, plan = "Max 5x") }
        // 14 closed 5-hour windows hit their limit, all inside the period's ~9-week span
        // (`ago` cycles 0..8 so every timestamp lands within it) — the count is what
        // matters here, not the spacing.
        val sessions = (0 until 14).map { s(it % 9, hit = true) }
        val lines = PlanFitCopy.build(reading(weekly, sessions))
        assertEquals("PLAN FIT · MAX 5X · LAST 8 WEEKS", lines.header)
        assertEquals("This plan runs out on you.", lines.reading)
        assertEquals("Hit the cap in 3 of 8 weeks", lines.evidence)
        assertEquals("5h cap hit 14 times", lines.fiveHourLine)
    }

    @Test
    fun `5h line is singular at exactly one hit`() {
        val weekly = (0..7).map { i -> w(i, if (i < 3) 100.0 else 60.0, hit = i < 3) }
        val lines = PlanFitCopy.build(reading(weekly, listOf(s(0, hit = true))))
        assertEquals("5h cap hit 1 time", lines.fiveHourLine)
    }

    // --- state b: FITS, capped once ---

    @Test
    fun `state b - fits, capped once`() {
        val peaks = listOf(38.0, 55.0, 62.0, 100.0, 48.0, 70.0, 33.0, 58.0)
        val weekly = peaks.mapIndexed { i, p -> w(i, p, hit = p >= 100.0, plan = "Pro") }
        val lines = PlanFitCopy.build(reading(weekly))
        assertEquals("PLAN FIT · PRO · LAST 8 WEEKS", lines.header)
        assertEquals("This plan fits how you use it.", lines.reading)
        assertEquals("Between 25% and 75% most weeks · capped once", lines.evidence)
        assertNull(lines.fiveHourLine)
    }

    @Test
    fun `fits with zero capped weeks reads never capped`() {
        val weekly = (0..7).map { i -> w(i, 40.0, plan = "Pro") }
        val lines = PlanFitCopy.build(reading(weekly))
        assertEquals("Between 25% and 75% most weeks · never capped", lines.evidence)
    }

    @Test
    fun `fits with multiple capped weeks reads capped N times`() {
        val weekly = (0..7).map { i -> w(i, if (i < 2) 100.0 else 45.0, hit = i < 2, plan = "Pro") }
        val lines = PlanFitCopy.build(reading(weekly))
        assertEquals("Between 25% and 75% most weeks · capped 2 times", lines.evidence)
    }

    // --- state c: SMALL_PART, never capped ---

    @Test
    fun `state c - small part, never capped`() {
        val peaks = listOf(10.0, 12.0, 8.0, 30.0, 18.0, 22.0, 35.0, 15.0)
        val weekly = peaks.mapIndexed { i, p -> w(i, p, plan = "Max 5x") }
        val lines = PlanFitCopy.build(reading(weekly))
        assertEquals("PLAN FIT · MAX 5X · LAST 8 WEEKS", lines.header)
        assertEquals("You use a small part of this plan.", lines.reading)
        assertEquals("Under 25% in 6 of 8 weeks · never capped", lines.evidence)
    }

    // --- state d: first run, 0 closed weeks — fallback plan only here ---

    @Test
    fun `state d - first run uses the fallback plan and drops the period entirely`() {
        val lines = PlanFitCopy.build(reading(emptyList()), fallbackPlan = "Max 5x")
        assertEquals("PLAN FIT · MAX 5X", lines.header)
        assertEquals("First week in progress.", lines.reading)
        assertEquals("The reading starts after four weeks.", lines.evidence)
        assertNull(lines.fiveHourLine)
    }

    @Test
    fun `state d with no fallback plan known omits the plan token too`() {
        val lines = PlanFitCopy.build(reading(emptyList()), fallbackPlan = null)
        assertEquals("PLAN FIT", lines.header)
    }

    // --- state e: 1-3 closed weeks recorded ---

    @Test
    fun `state e - two of four weeks recorded`() {
        val weekly = (0..1).map { i -> w(i, 30.0, plan = "Max 5x") }
        val lines = PlanFitCopy.build(reading(weekly), fallbackPlan = "irrelevant here")
        assertEquals("PLAN FIT · MAX 5X · 2 WEEKS", lines.header)
        assertEquals("2 of 4 weeks recorded.", lines.reading)
        assertEquals("The reading starts after four weeks.", lines.evidence)
    }

    @Test
    fun `singular WEEK in the header and reading line at exactly one recorded week`() {
        val weekly = listOf(w(0, 30.0, plan = "Max 5x"))
        val lines = PlanFitCopy.build(reading(weekly))
        assertEquals("PLAN FIT · MAX 5X · 1 WEEK", lines.header)
        assertEquals("1 of 4 weeks recorded.", lines.reading)
    }

    // --- state f: plan changed inside the period, still under the minimum ---

    @Test
    fun `state f - plan changed, three weeks on the new plan`() {
        val oldPlan = (3..7).map { w(it, 90.0, plan = "Max 20x") }
        val newPlan = (0..2).map { w(it, 20.0, plan = "Max 5x") }
        val lines = PlanFitCopy.build(reading(oldPlan + newPlan))
        assertEquals("PLAN FIT · MAX 5X · 3 WEEKS", lines.header)
        assertEquals("Changed from Max 20x 3 weeks ago.", lines.reading)
        assertEquals("The reading restarts after four weeks on the new plan.", lines.evidence)
    }

    @Test
    fun `plan change reading is singular at one week ago`() {
        val oldPlan = (1..7).map { w(it, 90.0, plan = "Max 20x") }
        val newPlan = listOf(w(0, 20.0, plan = "Max 5x"))
        val lines = PlanFitCopy.build(reading(oldPlan + newPlan))
        assertEquals("Changed from Max 20x 1 week ago.", lines.reading)
    }

    // --- state g: plan unknown — the fallback must NOT leak in here ---

    @Test
    fun `state g - plan unknown drops the plan token even with a fallback available`() {
        // No `pl` tag on any record at all, so PlanFit.Reading.currentPlan is null even
        // though recordedWeeks is well past zero — the fallback plan is reserved for the
        // true first-run case (state d) and must be ignored here.
        val weekly = listOf(40.0, 54.0, 62.0, 100.0, 48.0, 70.0, 33.0, 58.0).mapIndexed { i, p ->
            w(i, p, hit = p >= 100.0)
        }
        val lines = PlanFitCopy.build(reading(weekly), fallbackPlan = "Max 5x")
        assertEquals("PLAN FIT · LAST 8 WEEKS", lines.header)
        assertEquals("This plan fits how you use it.", lines.reading)
    }

    // --- state h: idle account ---

    @Test
    fun `state h - idle account`() {
        val weekly = (0..7).map { i -> w(i, 0.0, plan = "Pro") }
        val lines = PlanFitCopy.build(reading(weekly))
        assertEquals("PLAN FIT · PRO · LAST 8 WEEKS", lines.header)
        assertEquals("No use recorded on this account.", lines.reading)
        assertEquals("8 idle weeks.", lines.evidence)
    }

    // --- state i: capped every week ---

    @Test
    fun `state i - capped every week`() {
        val weekly = (0..7).map { i -> w(i, 100.0, hit = true, plan = "Max 5x") }
        val lines = PlanFitCopy.build(reading(weekly))
        assertEquals("This plan runs out on you.", lines.reading)
        assertEquals("Hit the cap in 8 of 8 weeks", lines.evidence)
    }

    // --- state j: Opus-bound Max — model-specific cap wording ---

    @Test
    fun `state j - opus-bound max names the model in the cap line`() {
        val weekly = (0..7).map { i ->
            if (i % 2 == 0) w(i, 50.0, cap = "Opus", plan = "Max 20x") else w(i, 55.0, plan = "Max 20x")
        }
        val lines = PlanFitCopy.build(reading(weekly))
        assertEquals("PLAN FIT · MAX 20X · LAST 8 WEEKS", lines.header)
        assertEquals("This plan runs out on you.", lines.reading)
        assertEquals("Hit the Opus cap in 4 of 8 weeks", lines.evidence)
    }

    @Test
    fun `mixed model caps across capped weeks fall back to the generic cap wording`() {
        // One week capped on the pool itself (no modelCap), so the CAPPED weeks don't all
        // share a single non-null model — the model-specific phrasing must not fire.
        val weekly = (0..7).map { i ->
            when {
                i < 3 -> w(i, 50.0, cap = "Opus")
                i == 3 -> w(i, 100.0, hit = true)
                else -> w(i, 55.0)
            }
        }
        val lines = PlanFitCopy.build(reading(weekly))
        assertEquals(PlanFit.Verdict.RUNS_OUT, (reading(weekly) as PlanFit.Reading.Fitted).verdict)
        assertEquals("Hit the cap in 4 of 8 weeks", lines.evidence)
    }

    // --- state k: gaps — N < 8, "recorded" wording ---

    @Test
    fun `state k - gaps use the recorded wording in both places it appears`() {
        val peaks = listOf(14.0, 12.0, 8.0, 30.0, 15.0, 9.0)
        // Only 6 records exist at all (2 weeks were skipped entirely, not zero-valued).
        val weekly = listOf(0, 1, 2, 4, 5, 7).mapIndexed { idx, weeksAgo ->
            w(weeksAgo, peaks[idx], plan = "Max 5x")
        }
        val lines = PlanFitCopy.build(reading(weekly))
        assertEquals("PLAN FIT · MAX 5X · LAST 8 WEEKS", lines.header) // header keeps the target, not N
        assertEquals("Under 25% in 5 of 6 recorded weeks · never capped", lines.evidence)
    }

    // --- state l: ChatGPT — weekly-only, no 5h line ever ---

    @Test
    fun `state l - no session records at all means no 5h line, not even zero`() {
        val weekly = listOf(44.0, 58.0, 64.0, 100.0, 50.0, 72.0, 35.0, 60.0).mapIndexed { i, p ->
            w(i, p, hit = p >= 100.0, plan = "Plus")
        }
        // No sessionRecords passed at all — the ChatGPT shape.
        val lines = PlanFitCopy.build(reading(weekly))
        assertEquals("PLAN FIT · PLUS · LAST 8 WEEKS", lines.header)
        assertNull(lines.fiveHourLine)
    }

    // --- Fold 7 cover screen: COMPACT width ---

    @Test
    fun `compact width drops the header period and folds it into the evidence line`() {
        val peaks = listOf(10.0, 12.0, 8.0, 30.0, 18.0, 22.0, 35.0, 15.0)
        val weekly = peaks.mapIndexed { i, p -> w(i, p, plan = "Max 5x") }
        val lines = PlanFitCopy.build(reading(weekly), compact = true)
        assertEquals("PLAN FIT · MAX 5X", lines.header)
        assertEquals("Under 25% in 6 of the last 8 weeks · never capped", lines.evidence)
    }

    @Test
    fun `compact width still drops the header period even below the four-week minimum`() {
        val weekly = (0..1).map { i -> w(i, 30.0, plan = "Max 5x") }
        val lines = PlanFitCopy.build(reading(weekly), fallbackPlan = "Max 5x", compact = true)
        assertEquals("PLAN FIT · MAX 5X", lines.header)
        // The count already lives in the (unaffected) reading line, not the header.
        assertEquals("2 of 4 weeks recorded.", lines.reading)
    }

    // --- RUNS_OUT reached on the capped+heavy rule rather than the capped rule ---

    @Test
    fun `runs out via the heavy rule names the ceiling, not a cap it never hit`() {
        // 6 of 8 weeks at 88% and never capped clears capped+heavy >= ceil(2N/3) = 6.
        // "Hit the cap in 0 of 8 weeks" would be ungainly and wrong about why.
        val weekly = (0..7).map { i -> w(i, if (i < 6) 88.0 else 40.0, plan = "Max 5x") }
        val lines = PlanFitCopy.build(reading(weekly))
        assertEquals("This plan runs out on you.", lines.reading)
        assertEquals("Capped or above 75% in 6 of 8 weeks", lines.evidence)
    }
}
