package com.robin.claudeusage.ui

import com.robin.claudeusage.data.PlanFit

/**
 * CCRM-70 (Plan Fit): the wording for the tonal block above the weekly bars in the 7-day
 * pane — pure string assembly over [PlanFit.Reading], no Android and no Compose, so
 * `PlanFitCopyTest` can pin every one of the design's states without a UI harness. Layout,
 * type and colour come from `design/2026-09-16-plan-fit-and-account-order.html` §1-4; this
 * object only owns the four lines.
 */
object PlanFitCopy {

    /** The block's up-to-four lines, top to bottom. [fiveHourLine] is null exactly when
     * the fourth line doesn't render — a NotEnoughData reading, a zero cap-hit count, or
     * an account with no 5-hour window (which is why its count is always zero). */
    data class Lines(
        val header: String,
        val reading: String,
        val evidence: String,
        val fiveHourLine: String?,
    )

    /**
     * @param fallbackPlan the account's already-known live plan label (e.g. "Max 5x"),
     *   used only for the brand-new-account case — zero closed weeks, where
     *   [PlanFit.Reading.currentPlan] is null by construction (nothing has closed yet to
     *   carry a `pl` tag) even though the plan itself is already known from sign-in
     *   (wireframe state d, "First week in progress."). Every *other* null-plan case — an
     *   account whose plan never resolved at all — leaves the header with no plan token
     *   regardless of what this argument holds, matching the wireframe's "Plan unknown"
     *   state (g) exactly.
     * @param compact true on the Fold 7 cover screen (COMPACT width, §3): the header drops
     *   its period token there, and — only where there's a plain "of N weeks" clause to
     *   fold it into (an ungapped Fitted reading) — the evidence line says "of the last N
     *   weeks" instead. False on the two-pane inner screen (§4) and the wide 7-day pane.
     */
    fun build(reading: PlanFit.Reading, fallbackPlan: String? = null, compact: Boolean = false): Lines {
        val header = header(reading, planToken(reading, fallbackPlan), compact)
        return when (reading) {
            is PlanFit.Reading.NotEnoughData -> Lines(
                header = header,
                reading = notEnoughDataReading(reading),
                evidence = if (reading.planChange != null) {
                    "The reading restarts after four weeks on the new plan."
                } else {
                    "The reading starts after four weeks."
                },
                fiveHourLine = null,
            )
            is PlanFit.Reading.Fitted -> Lines(
                header = header,
                reading = fittedReading(reading.verdict),
                evidence = evidence(reading, compact),
                fiveHourLine = fiveHourLine(reading.fiveHourCapHits),
            )
        }
    }

    private fun planToken(reading: PlanFit.Reading, fallbackPlan: String?): String? {
        reading.currentPlan?.let { return it }
        if (reading is PlanFit.Reading.NotEnoughData && reading.recordedWeeks == 0) return fallbackPlan
        return null
    }

    private fun header(reading: PlanFit.Reading, planToken: String?, compact: Boolean): String {
        val parts = mutableListOf("PLAN FIT")
        planToken?.let { parts += it.uppercase() }
        if (!compact) periodToken(reading)?.let { parts += it }
        return parts.joinToString(" · ")
    }

    /** The header's period token — absent entirely for a brand-new account (nothing to
     * summarise yet), "N WEEK(S)" below the four-week minimum, "LAST 8 WEEKS" (always the
     * full [PlanFit.MAX_WEEKS] target, even when gaps mean fewer were actually recorded —
     * that's what the evidence line's "recorded" wording is for) once a verdict exists. */
    private fun periodToken(reading: PlanFit.Reading): String? = when (reading) {
        is PlanFit.Reading.Fitted ->
            "LAST ${reading.periodTargetWeeks} ${weeksWord(reading.periodTargetWeeks).uppercase()}"
        is PlanFit.Reading.NotEnoughData ->
            if (reading.recordedWeeks == 0) null
            else "${reading.recordedWeeks} ${weeksWord(reading.recordedWeeks).uppercase()}"
    }

    private fun notEnoughDataReading(r: PlanFit.Reading.NotEnoughData): String {
        val change = r.planChange
        if (change != null) {
            val ago = "${change.weeksAgo} ${weeksWord(change.weeksAgo)} ago"
            return if (change.previousPlan != null) "Changed from ${change.previousPlan} $ago." else "Changed plans $ago."
        }
        return if (r.recordedWeeks == 0) {
            "First week in progress."
        } else {
            "${r.recordedWeeks} of ${PlanFit.MIN_WEEKS} ${weeksWord(PlanFit.MIN_WEEKS)} recorded."
        }
    }

    private fun fittedReading(verdict: PlanFit.Verdict): String = when (verdict) {
        PlanFit.Verdict.RUNS_OUT -> "This plan runs out on you."
        PlanFit.Verdict.SMALL_PART -> "You use a small part of this plan."
        PlanFit.Verdict.IDLE -> "No use recorded on this account."
        PlanFit.Verdict.FITS -> "This plan fits how you use it."
    }

    private fun evidence(r: PlanFit.Reading.Fitted, compact: Boolean): String {
        val n = r.recordedWeeks
        val target = r.periodTargetWeeks
        val gaps = n < target
        return when (r.verdict) {
            PlanFit.Verdict.RUNS_OUT -> {
                // RUNS_OUT has two triggers and they want different evidence. Reached on the
                // capped rule, the honest sentence names the caps. Reached on the
                // capped+heavy rule, cappedCount can be low or even 0 — "Hit the cap in 0 of
                // 8 weeks" is both ungainly and beside the point, since what earned the
                // verdict was living at the ceiling rather than touching it. CCRM-70 (Plan
                // Fit)'s design carries both forms; this picks between them.
                if (r.cappedCount >= ceilDiv(n, 3)) {
                    val capWord = sharedModelCap(r.weeks)?.let { "the $it cap" } ?: "the cap"
                    "Hit $capWord in ${r.cappedCount} of ${weeksDenominator(n, target, gaps, compact)}"
                } else {
                    val near = r.cappedCount + r.heavyCount
                    "Capped or above 75% in $near of ${weeksDenominator(n, target, gaps, compact)}"
                }
            }
            PlanFit.Verdict.SMALL_PART ->
                "Under 25% in ${r.lightCount} of ${weeksDenominator(n, target, gaps, compact)} · never capped"
            PlanFit.Verdict.IDLE -> {
                val word = if (gaps) "recorded idle ${weeksWord(n)}" else "idle ${weeksWord(n)}"
                "$n $word."
            }
            PlanFit.Verdict.FITS -> "Between 25% and 75% most weeks · ${cappedPhrase(r.cappedCount)}"
        }
    }

    /** "8 weeks" plain; "the last 8 weeks" when [compact] folds the header's dropped
     * period token in here instead; "6 recorded weeks" whenever [gaps] means fewer than
     * [target] weeks actually closed — gaps win over the compact wording since "recorded"
     * is itself already carrying the count that "the last" would otherwise stand in for. */
    private fun weeksDenominator(n: Int, target: Int, gaps: Boolean, compact: Boolean): String = when {
        gaps -> "$n recorded ${weeksWord(n)}"
        compact -> "the last $target ${weeksWord(target)}"
        else -> "$target ${weeksWord(target)}"
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

    private fun cappedPhrase(count: Int): String = when (count) {
        0 -> "never capped"
        1 -> "capped once"
        else -> "capped $count times"
    }

    /** Null when the count is zero — including every account with no 5-hour window at
     * all, e.g. ChatGPT, whose count is always zero by construction ([PlanFit] only ever
     * counts closed `SESSION` records, and ChatGPT never logs any). */
    private fun fiveHourLine(count: Int): String? {
        if (count <= 0) return null
        return "5h cap hit $count ${if (count == 1) "time" else "times"}"
    }

    /** Every CAPPED week shares one non-null model cap → the model-specific cap name
     * ("Hit the Opus cap …" over the generic "Hit the cap …"), per the wireframe's
     * Opus-bound Max state (j). */
    private fun sharedModelCap(weeks: List<PlanFit.WeekReading>): String? {
        val capped = weeks.filter { it.band == PlanFit.Band.CAPPED }
        if (capped.isEmpty()) return null
        val first = capped.first().modelCap ?: return null
        return if (capped.all { it.modelCap == first }) first else null
    }

    private fun weeksWord(n: Int): String = if (n == 1) "week" else "weeks"
}
