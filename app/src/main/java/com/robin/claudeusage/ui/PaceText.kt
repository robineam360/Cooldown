package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Pace
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        is Pace.Allowance.Hours -> {
            val h = (a.msLeft / 3_600_000.0).roundToInt()
            "${a.pctLeft.roundToInt()}% left, ${if (h < 1) "<1" else "$h"}h"
        }
    }

    /** The tight form: "Slow down · ≈11%/day"; the band alone when there is no allowance. */
    fun tight(band: Pace.Band, allowance: Pace.Allowance?): String? {
        val words = bandWords(band) ?: return null
        return if (allowance == null) words else words + DOT + allowance(allowance)
    }

    /**
     * The figure in Vs pace mode, unsigned since the shape grammar (rev D §7b, CCRM-103
     * (Hybrid Notification)): "10%", "20%", "0%" — [vsPaceTone] carries the side as a
     * colour. Falls back to the Used figure when spent or unclocked, "—" with no reading.
     */
    fun vsPaceFigure(usedPct: Double?, elapsedPct: Double?): String {
        if (usedPct == null) return "—"
        val d = Pace.vsPace(usedPct, elapsedPct) ?: return "${usedPct.toInt()}%"
        return "${abs(d)}%"
    }

    /** Which colour a Vs pace figure takes. [USAGE]: the fallback usage figure, in its severity colours. */
    enum class VsTone { UNDER, OVER, ON, USAGE }

    fun vsPaceTone(usedPct: Double?, elapsedPct: Double?): VsTone {
        val d = Pace.vsPace(usedPct, elapsedPct) ?: return VsTone.USAGE
        return when {
            d > 0 -> VsTone.UNDER
            d < 0 -> VsTone.OVER
            else -> VsTone.ON
        }
    }

    private fun halfUp(x: Double): Int = floor(x + 0.5 + Pace.EDGE_EPSILON).toInt()

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
        val m = markPct(elapsedPct)
        if (mode == Mode.VS_PACE) return "${usedPct.toInt()}% used (mark $m%)"
        val d = elapsedPct - usedPct
        val n = halfUp(abs(d))
        val shown = if (mode == Mode.LEFT) 100 - m else m
        return when {
            abs(d) < 1.0 -> "On the pace mark ($shown%)"
            // Left mode turns the V1 caption round, which also fits the 12 sp line (Fable, 2026-11-02).
            mode == Mode.LEFT -> "${if (d > 0) "$n to spare" else "$n over"}${DOT}pace mark $shown%"
            d > 0 -> "$n under the pace mark ($shown%)"
            else -> "$n over the pace mark ($shown%)"
        }
    }

    /**
     * CCRM-103 (Hybrid Notification)'s tap readout (shape grammar rev D §2b, C4): what a
     * tapped figure's own line says for 3 s. "Pace catches up Thu 5 Nov, 12:50 PM" over the
     * mark, "1d 4h behind pace" under it, "On the pace mark" within a point. Null — the tap
     * does nothing — when [Pace.markTime] is null.
     */
    fun markReadout(
        mt: Pace.MarkTime?,
        use24h: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String? =
        when (mt) {
            null -> null
            Pace.MarkTime.On -> "On the pace mark"
            is Pace.MarkTime.Over -> {
                val pattern = if (use24h) "EEE d MMM, HH:mm" else "EEE d MMM, h:mm a"
                "Pace catches up " + DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(Instant.ofEpochMilli(mt.atMs))
            }
            is Pace.MarkTime.Under -> "${behind(mt.agoMs)} behind pace"
        }

    /** "1d 4h", under a day "14h 57m", under an hour "57m"; every part rounded down. */
    fun behind(ms: Long): String {
        val m = ms / 60_000L
        val d = m / 1_440L
        val h = m % 1_440L / 60L
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m % 60L}m"
            else -> "${m}m"
        }
    }

    /** True = the caption starts 5 dp right of the mark; false = it ends 5 dp left of it. */
    fun captionRightOfMark(barWidth: Float, markPct: Double, captionWidth: Float, gap: Float = 5f): Boolean =
        barWidth * markPct / 100.0 + gap + captionWidth <= barWidth
}
