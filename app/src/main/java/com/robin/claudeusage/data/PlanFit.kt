package com.robin.claudeusage.data

/**
 * CCRM-70 (Plan Fit) — is the plan a profile is on the right size, read off the last eight
 * *closed* weekly windows on the plan currently in force. Pure math over [SessionLog.Record]s,
 * no Android imports, so it is unit-testable without a `Context`.
 *
 * This object computes structured data only — [Reading.Fitted.verdict] plus the counts behind
 * it — and carries no user-facing strings. The History screen (CCRM-70) composes the copy from
 * this; see `design/2026-09-16-plan-fit-and-account-order.html` for the exact lines it builds.
 */
object PlanFit {

    /** The lookback target: the last eight closed weekly windows on the current plan. */
    const val MAX_WEEKS = 8

    /** No reading is offered on fewer than this many recorded weeks on the current plan. */
    const val MIN_WEEKS = 4

    enum class Verdict {
        /** capped ≥ ⌈N/3⌉ or capped+heavy ≥ ⌈2N/3⌉ — "This plan runs out on you." */
        RUNS_OUT,

        /** light ≥ ⌈3N/4⌉ and capped = 0 — "You use a small part of this plan." */
        SMALL_PART,

        /** Every week's peak is 0 (and no per-model cap ever fired) — "No use recorded." */
        IDLE,

        /** None of the above — "This plan fits how you use it." */
        FITS,
    }

    /** One week's band, per the CCRM-70 (Plan Fit) thresholds. */
    enum class Band { CAPPED, HEAVY, MIDDLE, LIGHT }

    /**
     * One closed weekly window inside the period, with its band already resolved — the UI
     * builds the evidence line ("Hit the Opus cap in 5 of 8 weeks" vs the generic "Hit the cap
     * in 3 of 8 weeks") by looking at which weeks are [Band.CAPPED] and whether they share a
     * single non-null [modelCap].
     */
    data class WeekReading(
        val resetAt: Long,
        val peakPct: Double,
        val hitLimit: Boolean,
        val modelCap: String?,
        val band: Band,
    )

    /**
     * A plan change [PlanFit] found while walking backwards from the current plan's most
     * recent week — the boundary that stops the period, per CCRM-70's "last 8 weeks *on the
     * current plan*" rule. Reported even when it isn't the reason for [Reading.NotEnoughData]
     * (a fresh plan with 4+ weeks of its own history still carries the note "changed N weeks
     * ago" per the wireframe).
     */
    data class PlanChange(
        val previousPlan: String?,
        val weeksAgo: Int,
        val msAgo: Long,
    )

    sealed class Reading {
        /** The plan label (and tier, if the caller folds it in) in force on the most recent
         * closed week — null when no weekly record has ever carried a `pl` tag. */
        abstract val currentPlan: String?

        /** N — the recorded weeks in the period, capped at [MAX_WEEKS]. */
        abstract val recordedWeeks: Int

        /** Set when the period was cut short by a change of plan, however long ago. */
        abstract val planChange: PlanChange?

        /**
         * Fewer than [MIN_WEEKS] recorded weeks on the current plan — including a brand new
         * account ([recordedWeeks] = 0) and a profile whose [SessionLog] was cleared
         * (CCRM-14 (Clear History)). No verdict is offered.
         */
        data class NotEnoughData(
            override val recordedWeeks: Int,
            override val currentPlan: String?,
            override val planChange: PlanChange?,
        ) : Reading()

        /** A full reading: [MIN_WEEKS] or more recorded weeks on the current plan. */
        data class Fitted(
            val verdict: Verdict,
            override val currentPlan: String?,
            /** Oldest to newest, size == [recordedWeeks]. */
            val weeks: List<WeekReading>,
            override val recordedWeeks: Int,
            /** The target denominator the UI reads "of 8 recorded weeks" against. */
            val periodTargetWeeks: Int,
            val cappedCount: Int,
            val heavyCount: Int,
            val middleCount: Int,
            val lightCount: Int,
            /** 5h cap hits (closed session records with `hitLimit`) inside the period. */
            val fiveHourCapHits: Int,
            override val planChange: PlanChange?,
        ) : Reading()
    }

    /**
     * @param weeklyRecords a profile's [SessionLog.Record]s of kind [SessionLog.WEEKLY] — any
     *   other kind is ignored, so the whole [SessionLog.records] list may be passed as-is.
     * @param sessionRecords the same profile's [SessionLog.SESSION] records, for the 5h line;
     *   omit for a provider with no 5-hour window (ChatGPT's weekly-only shape).
     * @param nowMs injectable for tests; defaults to the real clock.
     */
    fun compute(
        weeklyRecords: List<SessionLog.Record>,
        sessionRecords: List<SessionLog.Record> = emptyList(),
        nowMs: Long = System.currentTimeMillis(),
    ): Reading {
        val weekly = weeklyRecords.filter { it.kind == SessionLog.WEEKLY }
        if (weekly.isEmpty()) return Reading.NotEnoughData(0, null, null)

        // Untagged records (written before this field existed) are treated as belonging to
        // the plan first observed once tagging started — never as an unknown plan of their
        // own, which would otherwise read as a plan change that never happened.
        val ascendingAll = weekly.sortedBy { it.resetAt }
        val firstTaggedPlan = ascendingAll.firstOrNull { !it.plan.orNull().isNullOrEmpty() }?.plan
        fun effectivePlan(r: SessionLog.Record): String? = r.plan.orNull() ?: firstTaggedPlan

        val descending = weekly.sortedByDescending { it.resetAt }
        val currentPlan = effectivePlan(descending.first())

        // Walk backwards from the most recent week, collecting up to MAX_WEEKS on the
        // current plan; stop the instant the plan tag disagrees — that boundary is the
        // period's true start, whatever the target lookback would otherwise have been.
        val periodDesc = mutableListOf<SessionLog.Record>()
        var planChange: PlanChange? = null
        for (r in descending) {
            if (effectivePlan(r) != currentPlan) {
                val oldestIncluded = periodDesc.last()
                val changeStartMs = oldestIncluded.resetAt - HistoryStats.WEEK_MS
                val msAgo = (nowMs - changeStartMs).coerceAtLeast(0)
                planChange = PlanChange(
                    previousPlan = effectivePlan(r),
                    weeksAgo = (msAgo / HistoryStats.WEEK_MS).toInt(),
                    msAgo = msAgo,
                )
                break
            }
            periodDesc.add(r)
            if (periodDesc.size == MAX_WEEKS) break
        }

        val weeksAsc = periodDesc.sortedBy { it.resetAt }
        val n = weeksAsc.size

        if (n < MIN_WEEKS) {
            return Reading.NotEnoughData(n, currentPlan, planChange)
        }

        val weekReadings = weeksAsc.map { r ->
            val modelCap = r.modelCap.orNull()
            WeekReading(r.resetAt, r.peakPct, r.hitLimit, modelCap, bandOf(r.peakPct, r.hitLimit, modelCap))
        }
        val cappedCount = weekReadings.count { it.band == Band.CAPPED }
        val heavyCount = weekReadings.count { it.band == Band.HEAVY }
        val middleCount = weekReadings.count { it.band == Band.MIDDLE }
        val lightCount = weekReadings.count { it.band == Band.LIGHT }

        val verdict = when {
            cappedCount >= ceilDiv(n, 3) || cappedCount + heavyCount >= ceilDiv(2 * n, 3) ->
                Verdict.RUNS_OUT

            // Checked ahead of SMALL_PART on purpose. "Every peak is 0" is a subset of the
            // SMALL_PART condition below (a week with peak 0 is always the LIGHT band, so
            // light == N trivially clears ceil(3N/4)) — without checking the more specific
            // condition first, IDLE could never fire, which the wireframe's own "No use
            // recorded on this account." / "8 idle weeks." example rules out.
            weekReadings.all { it.peakPct <= 0.0 && it.modelCap == null } -> Verdict.IDLE

            lightCount >= ceilDiv(3 * n, 4) && cappedCount == 0 -> Verdict.SMALL_PART

            else -> Verdict.FITS
        }

        val periodStartMs = weeksAsc.first().resetAt - HistoryStats.WEEK_MS
        val fiveHourCapHits = sessionRecords.count {
            it.kind == SessionLog.SESSION && it.hitLimit && it.resetAt in periodStartMs..nowMs
        }

        return Reading.Fitted(
            verdict = verdict,
            currentPlan = currentPlan,
            weeks = weekReadings,
            recordedWeeks = n,
            periodTargetWeeks = MAX_WEEKS,
            cappedCount = cappedCount,
            heavyCount = heavyCount,
            middleCount = middleCount,
            lightCount = lightCount,
            fiveHourCapHits = fiveHourCapHits,
            planChange = planChange,
        )
    }

    /**
     * capped = `hitLimit` or peak ≥ 100 on the pool, or any per-model weekly cap (a non-empty
     * [modelCap]) — the last clause is why an Opus-locked week counts as capped even when the
     * pool itself reads a low peak (CCRM-70's whole reason for the `mc` tag).
     */
    private fun bandOf(peakPct: Double, hitLimit: Boolean, modelCap: String?): Band = when {
        hitLimit || peakPct >= 100.0 || !modelCap.isNullOrEmpty() -> Band.CAPPED
        peakPct >= 75.0 -> Band.HEAVY
        peakPct >= 25.0 -> Band.MIDDLE
        else -> Band.LIGHT
    }

    /** Integer ceiling division, for the ⌈N/3⌉ / ⌈2N/3⌉ / ⌈3N/4⌉ thresholds. */
    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

    private fun String?.orNull(): String? = this?.ifEmpty { null }
}
