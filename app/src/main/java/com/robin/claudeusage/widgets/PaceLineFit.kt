package com.robin.claudeusage.widgets

import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.PaceText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CCRM-104 (Pace Line Parity): the widgets' pace line and the pure estimator that picks its
 * form. A RemoteViews face cannot measure text at run time, so the form is chosen from the
 * column's width in dp with wireframe §8's per-character em classes, scaled by the font
 * scale (Astra C1-4): larger text drops earlier. The drop order is D10's: full, without the
 * weekday, then the duration and its word alone (the floor, never cut). If even the floor
 * does not fit the text steps to [MIN_SP], and only then ellipsizes.
 *
 * Widgets keep their own clock rule ([Fmt.widgetClock]: 12-hour, the weekday only when the
 * reset is more than 24 h away), so the forms are built here rather than by
 * [PaceText.paceLineForms], whose weekday rule is the calendar day's.
 */
object PaceLineFit {

    /** The Number's "as of 1:46 PM" stamp at 10 sp, with its 8 dp margin: 66 dp at font scale 1.0. */
    private const val STAMP_TEXT_DP = 58f
    private const val STAMP_GAP_DP = 8f
    const val MIN_SP = 10f

    fun stampDp(fontScale: Float): Float = STAMP_TEXT_DP * fontScale + STAMP_GAP_DP

    /** Wireframe §8's em classes, per character. */
    private fun em(c: Char): Float = when {
        c.isDigit() -> .56f
        c in "iltrfj" -> .30f
        c in "mw" -> .86f
        c in 'a'..'z' -> .54f
        c in "WMO" -> .80f
        c in 'A'..'Z' -> .62f
        c == ' ' -> .25f
        c == '·' -> .29f
        c == ':' -> .23f
        c == '%' -> .46f
        c == '+' || c == '−' -> .58f
        else -> .5f
    }

    private fun em(s: String): Float = s.sumOf { em(it).toDouble() }.toFloat()

    /** A line's width in em; the tinted span is semibold, 3% wider. */
    fun emWidth(line: PaceText.PaceLine): Float {
        val tint = line.tinted ?: return em(line.text)
        return em(tint) * 1.03f + em(line.text.removePrefix(tint))
    }

    /** A line's estimated width in dp at [sp] and [fontScale]. */
    fun widthDp(line: PaceText.PaceLine, sp: Float, fontScale: Float): Float = emWidth(line) * sp * fontScale

    /**
     * The forms of a Weekly pace line, longest first: full ("21h 50m ahead · Sun 9:30 PM", the weekday
     * only beyond 24 h as [Fmt.widgetClock] has it), without the weekday, then the floor. Empty with no readout.
     */
    fun forms(mt: Pace.MarkTime?, resetsAt: Instant, nowMs: Long, zone: ZoneId): List<PaceText.PaceLine> {
        val full = PaceText.paceLine(mt, Fmt.widgetClock(resetsAt, nowMs, zone)) ?: return emptyList()
        val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(zone).format(resetsAt)
        val noDay = PaceText.paceLine(mt, clock)!!
        val floor = PaceText.PaceLine(full.tinted ?: "On the mark", full.tinted, full.tone)
        return listOf(full, noDay, floor).distinctBy { it.text }
    }

    /** The form shown and the size it is drawn at. */
    data class Choice(val line: PaceText.PaceLine, val sp: Float)

    /** The first form that fits [availDp] at [sp]; if none, the floor at [MIN_SP] (then the view ellipsizes). Null with no forms. */
    fun choose(forms: List<PaceText.PaceLine>, availDp: Float, sp: Float, fontScale: Float): Choice? {
        val line = PaceText.fit(forms) { text -> widthDp(forms.first { it.text == text }, sp, fontScale) <= availDp } ?: return null
        val fits = widthDp(line, sp, fontScale) <= availDp
        return Choice(line, if (fits) sp else minOf(sp, MIN_SP))
    }

    /** [choice]'s stamp verdict. */
    data class Pick(val choice: Choice, val stamp: Boolean)

    /**
     * The Number's sub row, line and stamp sharing [colDp] (Astra C1-2): the stamp stays while some
     * form still fits beside it; otherwise it yields first and the drop order runs on the full column.
     */
    fun pick(
        forms: List<PaceText.PaceLine>, colDp: Float, hasStamp: Boolean, sp: Float, fontScale: Float,
    ): Pick? {
        if (forms.isEmpty()) return null
        if (hasStamp) {
            val beside = colDp - stampDp(fontScale)
            if (forms.any { widthDp(it, sp, fontScale) <= beside }) {
                return Pick(choose(forms, beside, sp, fontScale)!!, stamp = true)
            }
        }
        return Pick(choose(forms, colDp, sp, fontScale)!!, stamp = false)
    }
}
