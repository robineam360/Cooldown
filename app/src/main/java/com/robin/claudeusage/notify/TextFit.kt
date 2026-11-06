package com.robin.claudeusage.notify

import com.robin.claudeusage.ui.PaceText

/**
 * CCRM-104 (Pace Line Parity), wireframe D10/C1-3/C1-4: a RemoteViews cannot measure text, so
 * the notification picks a pace-line or caption form from the column's width in dp, against an
 * estimate of the string's width (the wireframe's §8 em classes, a "Roboto-like" approximation).
 * Pure, so `TextFitTest` pins it against the wireframe's own numbers.
 *
 * Width in dp = em × text size in sp × font scale: a larger system font drops forms earlier.
 */
internal object TextFit {

    /** Astra C1-4: when even the floor does not fit, the text steps down to this, then ellipsizes. */
    const val STEP_DOWN_SP = 10f

    /** The pace line's column on the tight 328 dp skin (wireframe §5/§8), in dp. */
    const val COLLAPSED_COLUMN_DP = 184f
    const val EXPANDED_COLUMN_DP = 141f
    const val DUET_EXPANDED_COLUMN_DP = 159f

    /** Wireframe §8: a tinted (semi-bold) span is 3% wider. */
    private const val BOLD = 1.03

    private fun emOf(c: Char): Double = when {
        c.isDigit() -> .56
        c in "iltrfj" -> .30
        c in "mw" -> .86
        c in 'a'..'z' -> .54
        c in "WMO" -> .80
        c in 'A'..'Z' -> .62
        c == ' ' -> .25
        c == '·' -> .29
        c == ':' -> .23
        c == '%' -> .46
        c == '+' || c == '−' -> .58
        else -> .5
    }

    /** The string's width in em; [bold] widens it 3%. */
    fun em(s: String, bold: Boolean = false): Double = s.sumOf { emOf(it) } * (if (bold) BOLD else 1.0)

    /** A pace line's width in em: its tinted span bold, the rest plain. */
    fun em(line: PaceText.PaceLine): Double {
        val t = line.tinted ?: return em(line.text)
        return em(t, bold = true) + em(line.text.removePrefix(t))
    }

    fun widthDp(em: Double, sp: Float, fontScale: Float): Float = (em * sp * fontScale).toFloat()

    /** A choice: the [form] to print and the text size to print it at. */
    data class Pick<T>(val form: T, val sp: Float)

    /** [choose] is [PaceText.fit] / [PaceText.fitText]: the first form [fits] accepts, else the floor. */
    private fun <T> pick(
        choose: ((T) -> Boolean) -> T?,
        widthEm: (T) -> Double,
        availDp: Float,
        sp: Float,
        fontScale: Float,
    ): Pick<T>? {
        fun fitsAt(size: Float): (T) -> Boolean = { widthDp(widthEm(it), size, fontScale) <= availDp }
        val atBase = choose(fitsAt(sp)) ?: return null
        if (fitsAt(sp)(atBase) || sp <= STEP_DOWN_SP) return Pick(atBase, sp)
        // Not even the floor fits at [sp]: step down once (C1-4), longest form that fits, else the
        // floor at the small size, which the view ellipsizes as the last resort.
        return Pick(choose(fitsAt(STEP_DOWN_SP))!!, STEP_DOWN_SP)
    }

    fun paceLine(forms: List<PaceText.PaceLine>, availDp: Float, sp: Float, fontScale: Float): Pick<PaceText.PaceLine>? =
        // PaceText.fit hands its predicate the form's text; the forms' texts are distinct.
        pick(
            { fits -> PaceText.fit(forms) { text -> fits(forms.first { it.text == text }) } },
            { em(it) }, availDp, sp, fontScale,
        )

    fun caption(forms: List<String>, availDp: Float, sp: Float, fontScale: Float): Pick<String>? =
        pick({ fits -> PaceText.fitText(forms, fits) }, { em(it) }, availDp, sp, fontScale)
}
