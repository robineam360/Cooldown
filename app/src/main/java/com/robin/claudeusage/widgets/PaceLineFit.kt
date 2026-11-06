package com.robin.claudeusage.widgets

import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.notify.TextFit
import com.robin.claudeusage.ui.PaceText
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * CCRM-104 (Pace Line Parity): the widgets' pace line and the pure estimator that picks its
 * form. A RemoteViews face cannot measure text at run time, so the form is chosen from the
 * column's width in dp with wireframe §8's per-character em classes, scaled by the font
 * scale (Astra C1-4): larger text drops earlier. The drop order is D10's: full, without the
 * weekday, then the duration and its word alone (the floor, never cut). If even the floor
 * does not fit the text steps to [MIN_SP], and only then ellipsizes.
 *
 * The forms are core's ([PaceText.paceLineForms]); widgets only pin them to 12-hour (R2).
 */
object PaceLineFit {

    /** The Number's "as of 1:46 PM" stamp at 10 sp, with its 8 dp margin: 66 dp at font scale 1.0. */
    private const val STAMP_TEXT_DP = 58f
    private const val STAMP_GAP_DP = 8f
    const val MIN_SP = 10f

    fun stampDp(fontScale: Float): Float = STAMP_TEXT_DP * fontScale + STAMP_GAP_DP

    /** A line's width in em (the notification's estimator, one copy): the tinted span semibold, 3% wider. */
    fun emWidth(line: PaceText.PaceLine): Float = TextFit.em(line).toFloat()

    /** A line's estimated width in dp at [sp] and [fontScale]. */
    fun widthDp(line: PaceText.PaceLine, sp: Float, fontScale: Float): Float = emWidth(line) * sp * fontScale

    /**
     * The forms of a Weekly pace line, longest first: core §6.3's ("21h 50m ahead · Sun 9:30 PM", the weekday
     * dropped when the reset is later today), without the weekday, then the floor. Widgets print every clock in
     * 12-hour form (CCRM-78 (Widgets Reborn) R2), so the forms are asked for in 12-hour, en-US. Empty with no readout.
     */
    fun forms(mt: Pace.MarkTime?, resetsAt: Instant, nowMs: Long, zone: ZoneId): List<PaceText.PaceLine> =
        PaceText.paceLineForms(mt, resetsAt, nowMs, use24h = false, zone = zone, locale = Locale.US)

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
