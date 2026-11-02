package com.robin.claudeusage.data

import kotlin.math.abs
import kotlin.math.max

/**
 * CCRM-97 (Pace Number): where a weekly-length window stands against even burn
 * (spec/core BEHAVIOR-SPEC §6.1), and CCRM-96 (Weekly First)'s session gate (§6.2).
 * Pure maths; the words live in [com.robin.claudeusage.ui.PaceText]. Cases:
 * `spec/core/fixtures/pace/cases.json`.
 *
 * Weekly-length windows only — the 5-hour session keeps [Projection]'s line.
 */
object Pace {

    /** §7.2's exhausted line: at or above this a window is spent, whatever the pace. */
    const val SPENT_PCT = 99.5

    private const val DAY_MS = 86_400_000.0

    enum class Band { SPENT, ON_PACE, ROOM_TO_SPARE, USE_BEFORE_RESET, SLOW_DOWN, NEARLY_OUT }

    sealed interface Allowance {
        /** Under a day to the reset: what is left, and the time it has to last. */
        data class Hours(val pctLeft: Double, val msLeft: Long) : Allowance
        /** The even daily rate that lands on exactly 100% at the reset. */
        data class PerDay(val pctPerDay: Double) : Allowance
    }

    fun isSpent(usedPct: Double?): Boolean = usedPct != null && usedPct >= SPENT_PCT

    /**
     * `(r − q) ÷ max(r, q) × 100` with `q` quota left and `r` time left; bounded
     * −100…+100, positive over pace. `q ≤ 0` is checked first.
     */
    fun pace(usedPct: Double, elapsedPct: Double): Double {
        val q = 100.0 - usedPct
        val r = 100.0 - elapsedPct
        if (q <= 0.0) return 100.0
        if (r <= 0.0) return -100.0
        return (r - q) / max(r, q) * 100.0
    }

    /** First match wins; `|pace| ≤ 5` and the ±50 edges are as §6.1's table. */
    fun band(usedPct: Double, elapsedPct: Double): Band {
        if (usedPct >= SPENT_PCT) return Band.SPENT
        val p = pace(usedPct, elapsedPct)
        return when {
            abs(p) <= 5.0 -> Band.ON_PACE
            p < -50.0 -> Band.USE_BEFORE_RESET
            p < -5.0 -> Band.ROOM_TO_SPARE
            p > 50.0 -> Band.NEARLY_OUT
            else -> Band.SLOW_DOWN
        }
    }

    /**
     * The band, or null when there is no verdict: no reading, or no reset instant
     * (the window has not started). [elapsedPct] null means no reset instant.
     */
    fun bandOrNull(usedPct: Double?, elapsedPct: Double?): Band? =
        if (usedPct == null || elapsedPct == null) null else band(usedPct, elapsedPct)

    fun allowance(usedPct: Double, msUntilReset: Long): Allowance? {
        if (usedPct >= SPENT_PCT || msUntilReset <= 0L) return null
        val q = 100.0 - usedPct
        return if (msUntilReset < DAY_MS) Allowance.Hours(q, msUntilReset)
        else Allowance.PerDay(q / (msUntilReset / DAY_MS))
    }

    /**
     * §6.2: the 5-hour session is the limit about to stop the user. A missing weekly
     * reading reads as not spent; a spent week outranks the session.
     */
    fun sessionBinding(
        sessionPct: Double?,
        weeklyPct: Double?,
        sessionHitsLimitBeforeReset: Boolean,
    ): Boolean {
        if (sessionPct == null) return false
        if (isSpent(weeklyPct)) return false
        return sessionPct > 90.0 || sessionHitsLimitBeforeReset
    }

    /** CCRM-100 (Vs Pace Display): `floor(elapsed − used)`; null when it falls back to usage. */
    fun vsPace(usedPct: Double?, elapsedPct: Double?): Int? {
        if (usedPct == null || elapsedPct == null || isSpent(usedPct)) return null
        return kotlin.math.floor(elapsedPct - usedPct).toInt()
    }
}
