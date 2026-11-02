package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Pace
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * CCRM-97 (Pace Number) and CCRM-100 (Vs Pace Display): the words for [Pace]'s numbers
 * (spec/mobile BEHAVIOR-MOBILE §5). Cases: `spec/mobile/fixtures/display/cases.json`.
 * The signed Pace is never printed.
 */
object PaceText {

    const val MINUS = "−"
    private const val DOT = " · "

    /** The global Used / Left / Vs pace token (CCRM-22 (Used or Left) gained a third value). */
    enum class Mode(val key: String) {
        USED("used"), LEFT("left"), VS_PACE("vsPace");

        companion object {
            fun fromKey(key: String?): Mode = entries.firstOrNull { it.key == key } ?: USED
        }
    }

    /** Null for [Pace.Band.SPENT]: a spent window says "Out until <reset>" instead. */
    fun bandWords(band: Pace.Band): String? = when (band) {
        Pace.Band.ON_PACE -> "On pace"
        Pace.Band.ROOM_TO_SPARE -> "Room to spare"
        Pace.Band.USE_BEFORE_RESET -> "Use before reset"
        Pace.Band.SLOW_DOWN -> "Slow down"
        Pace.Band.NEARLY_OUT -> "Nearly out"
        Pace.Band.SPENT -> null
    }

    /** "≈11%/day", "<1%/day", or "40% left, 6h". */
    fun allowance(a: Pace.Allowance): String = when (a) {
        is Pace.Allowance.PerDay ->
            if (a.pctPerDay < 1.0) "<1%/day" else "≈${a.pctPerDay.roundToInt()}%/day"
        is Pace.Allowance.Hours ->
            "${a.pctLeft.roundToInt()}% left, ${(a.msLeft / 3_600_000.0).roundToInt()}h"
    }

    /** The tight form: "Slow down · ≈11%/day"; the band alone when there is no allowance. */
    fun tight(band: Pace.Band, allowance: Pace.Allowance?): String? {
        val words = bandWords(band) ?: return null
        return if (allowance == null) words else words + DOT + allowance(allowance)
    }

    /**
     * The big figure in Vs pace mode: "+10%", "−20%", "0%"; falls back to the Used
     * figure when spent or unclocked, "—" with no reading.
     */
    fun vsPaceFigure(usedPct: Double?, elapsedPct: Double?): String {
        if (usedPct == null) return "—"
        val d = Pace.vsPace(usedPct, elapsedPct) ?: return "${usedPct.toInt()}%"
        return when {
            d > 0 -> "+$d%"
            d < 0 -> "$MINUS${-d}%"
            else -> "0%"
        }
    }

    private fun halfUp(x: Double): Int = floor(x + 0.5).toInt()

    /** The pace mark's own value, as the caption names it. */
    fun markPct(elapsedPct: Double): Int = halfUp(elapsedPct)

    /**
     * The V1 caption under an in-app bar: "60% pace mark · 10 under" (Used),
     * "40% pace mark · 10 to spare" (Left), "52% used · mark 39%" (Vs pace). Null —
     * no caption — when spent, unclocked or unread.
     */
    fun caption(mode: Mode, usedPct: Double?, elapsedPct: Double?): String? {
        if (usedPct == null || elapsedPct == null || Pace.isSpent(usedPct)) return null
        val m = markPct(elapsedPct)
        if (mode == Mode.VS_PACE) return "${usedPct.toInt()}% used${DOT}mark $m%"
        val d = elapsedPct - usedPct
        val n = halfUp(abs(d))
        val under = if (mode == Mode.LEFT) "$n to spare" else "$n under"
        val words = when {
            abs(d) < 1.0 -> "on it"
            d > 0 -> under
            else -> "$n over"
        }
        val shown = if (mode == Mode.LEFT) 100 - m else m
        return "$shown% pace mark$DOT$words"
    }

    /**
     * The notification's expanded line 2 in Used/Left mode: "10 under the pace mark (60%)".
     * Vs pace mode carries the usage there instead ("52% used").
     */
    fun notificationDistance(mode: Mode, usedPct: Double?, elapsedPct: Double?): String? {
        if (usedPct == null || elapsedPct == null || Pace.isSpent(usedPct)) return null
        if (mode == Mode.VS_PACE) return "${usedPct.toInt()}% used"
        val m = markPct(elapsedPct)
        val d = elapsedPct - usedPct
        val n = halfUp(abs(d))
        val shown = if (mode == Mode.LEFT) 100 - m else m
        return when {
            abs(d) < 1.0 -> "On the pace mark ($shown%)"
            d > 0 -> "$n ${if (mode == Mode.LEFT) "to spare" else "under"} the pace mark ($shown%)"
            else -> "$n over the pace mark ($shown%)"
        }
    }

    /** True = the caption starts 5 dp right of the mark; false = it ends 5 dp left of it. */
    fun captionRightOfMark(barWidth: Float, markPct: Double, captionWidth: Float, gap: Float = 5f): Boolean =
        barWidth * markPct / 100.0 + gap + captionWidth <= barWidth
}
