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
        return kotlin.math.floor(elapsedPct - usedPct + EDGE_EPSILON).toInt()
    }

    /**
     * §6.3 "When the mark meets the usage" (CCRM-103 (Hybrid Notification)'s tap readout):
     * the pace mark passes [usedPct] at `windowStart + usedPct ÷ 100 × windowLength`.
     * Ahead of now when over the mark, behind it when under.
     */
    sealed interface MarkTime {
        /** Over the mark: the mark reaches the usage at [atMs], [inMs] from now. */
        data class Over(val atMs: Long, val inMs: Long) : MarkTime
        /** Under the mark: the mark was at the usage [agoMs] ago. */
        data class Under(val agoMs: Long) : MarkTime
        /** Under a point apart. */
        data object On : MarkTime
    }

    /**
     * Any clocked window; null when spent, with no reset clock ([msUntilReset] null) or
     * no reading. Cases: `spec/core/fixtures/pace-distance/cases.json` → `markTime`.
     */
    fun markTime(usedPct: Double?, msUntilReset: Long?, windowLengthMs: Long, nowMs: Long): MarkTime? {
        if (usedPct == null || msUntilReset == null || windowLengthMs <= 0L || isSpent(usedPct)) return null
        // The side is §6's elapsed percent, clamped 0…100 as the caption's is, so a reading
        // cached past its reset can never say "behind" where the caption says "on it".
        val elapsedPct = ((windowLengthMs - msUntilReset) * 100.0 / windowLengthMs).coerceIn(0.0, 100.0)
        if (abs(elapsedPct - usedPct) < 1.0) return MarkTime.On
        val startMs = nowMs + msUntilReset - windowLengthMs
        val atMs = startMs + Math.round(usedPct / 100.0 * windowLengthMs)
        return if (usedPct > elapsedPct) MarkTime.Over(atMs, atMs - nowMs) else MarkTime.Under(nowMs - atMs)
    }

    /**
     * Added before every floor and half-up rounding of a percentage difference: binary
     * floats put `2.3 − 0.3` at 1.9999999999999998, which would floor to 1 (the spec's
     * display cases pin "+2%").
     */
    const val EDGE_EPSILON = 1e-9
}
