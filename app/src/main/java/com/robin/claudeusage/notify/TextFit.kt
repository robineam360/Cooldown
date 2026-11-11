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

    /**
     * The pace line's column in dp, chosen for the narrowest shade the notification is checked on.
     *
     * Collapsed: CCBG-78 (Collapsed Pace Line Clip). The wireframe's 328 dp skin gave 184 dp, but on
     * the Fold 7 cover (One UI 8, 411 dp) the collapsed sub line is only 385 px wide, about 147 dp,
     * so a 157 dp full line was ellipsized instead of dropping its weekday (D10). The constant is
     * 147 dp less about 3.5%, because this estimator ran 2% narrow of the rendered caption on that
     * phone. Expanded single and Duet keep the wireframe's numbers: the cover's expanded sub line
     * measured 156 dp, against 141. CCRM-105 (Duet Stack), wireframe §1: the Duet's expanded figure
     * dropped 36 → 24 sp, giving its header 2.25 em × 12 sp more: 159 + 27 ≈ 186 dp (arithmetic;
     * Step 3 measures it on the Fold 7).
     */
    const val COLLAPSED_COLUMN_DP = 142f
    const val EXPANDED_COLUMN_DP = 141f
    const val DUET_EXPANDED_COLUMN_DP = 186f

    // --- CCBG-80 (Fixed Pace Columns): the column derived from the screen ------------------------
    //
    // CCRM-114 (Narrow Duet) build note F2 (design/2026-11-11-v20-narrow-duet-and-row-glyph-wireframes.html):
    // the three constants above were measured on the Fold 7 and fit only its shades. A 360 dp phone's
    // AOSP shade gives the Duet's expanded header 133 dp, so the line picked for 186 was cut mid-clock.
    // The column is now derived from the smallest width, the ring and the figure, for both layouts, and
    // [fitDp] keeps it at or under those constants until Step 3 has measured the Fold 7 again.

    /** The 44 dp ring box beside the column (collapsed single, and every expanded header). */
    const val RING_BOX_DP = 44f
    /** The gap each side of the column; one only, with no ring. */
    const val COLUMN_GAP_DP = 14f
    /** Expanded content is the collapsed content plus the expander's column, which comes back (A2: 133 + 126 = 259). */
    const val EXPANDED_EXTRA_DP = 40f
    /** CCBG-78 (Collapsed Pace Line Clip)'s 3.5% off the derived column, for the estimate's error. */
    const val FIT_FACTOR = 0.965f
    /** N1-Q6: the expanded ring's time line ("in 2h 20m") is sized in dp, not sp. */
    const val TIME_LINE_DP = 11f
    /** The mark (14 dp) and its 4 dp gap on a header's label line. */
    const val LABEL_MARK_DP = 18f

    const val SINGLE_FIGURE_SP = 32f
    const val SINGLE_EXPANDED_FIGURE_SP = 44f
    const val DUET_EXPANDED_FIGURE_SP = 24f
    const val COLLAPSED_TITLE_SP = 13f
    const val EXPANDED_TITLE_SP = 14f

    /**
     * Sizes in dp at the current font scale, as the system converts them — not sp × fontScale: Android 14 scales
     * large text non-linearly (24 sp → 26.4 dp at 1.3, 36 dp at 2.0). The notification passes
     * `TypedValue.applyDimension`; the tests pass AOSP's table.
     */
    fun interface SpToDp { fun dp(sp: Float): Float }

    /** A bold figure's width in em, the "%" at 58% (rev D §1b): 1.04 matches A2's measured "−12%" (49.3 dp at 24). */
    fun figureEm(figure: String): Double {
        if (figure == "—") return 1.0
        val pct = figure.endsWith("%")
        return (em(if (pct) figure.dropLast(1) else figure) + (if (pct) .267 else 0.0)) * 1.04
    }

    /** The column between the ring and the figure: content − (ring box + gap) − gap − the figure. */
    fun columnDp(contentDp: Float, ringBoxDp: Float?, figureDp: Float): Float =
        contentDp - (ringBoxDp?.let { it + COLUMN_GAP_DP } ?: 0f) - COLUMN_GAP_DP - figureDp

    /** The single layout's collapsed column at smallest width [sw]: ring 44, the 32 sp figure. */
    fun collapsedColumnDp(sw: Int, hasRing: Boolean, figure: String, spToDp: SpToDp): Float =
        columnDp(
            Duet.contentDp(sw), if (hasRing) RING_BOX_DP else null,
            (figureEm(figure) * spToDp.dp(SINGLE_FIGURE_SP)).toFloat(),
        )

    /**
     * An expanded header's column (single, 44 sp figure; Duet, 24 sp): content + 40, the ring box the wider of 44 and
     * its time line [ringTime] at [TIME_LINE_DP], none with no 5h.
     */
    fun expandedColumnDp(sw: Int, hasRing: Boolean, ringTime: String?, figure: String, figureSp: Float, spToDp: SpToDp): Float {
        val ringBox = if (!hasRing) null else maxOf(RING_BOX_DP, ringTime?.let { (em(it) * TIME_LINE_DP).toFloat() } ?: 0f)
        return columnDp(
            Duet.contentDp(sw) + EXPANDED_EXTRA_DP, ringBox,
            (figureEm(figure) * spToDp.dp(figureSp)).toFloat(),
        )
    }

    /** What a line is fitted to: the derived column less 3.5%, never wider than today's [capDp] (until Step 3). */
    fun fitDp(columnDp: Float, capDp: Float): Float = minOf(columnDp * FIT_FACTOR, capDp)

    /**
     * N1-Q7: "· Weekly" gives before the name. True keeps "<name> · Weekly" — its bold width at [labelDp] fits the
     * column less the mark; false prints the name alone.
     */
    fun titleKeepsWindow(title: String, columnDp: Float, labelDp: Float, labelMaxDp: Float = Float.MAX_VALUE): Boolean =
        widthDp(em(title, bold = true), labelDp, 1f) <= minOf(columnDp - LABEL_MARK_DP, labelMaxDp)

    /**
     * The Duet header label's own bound (`notif_duet_expanded.xml` maxWidth 200 dp, less the 44 dp ring and its 14 dp gap
     * when there is one): on a wide shade the column is wider than the label may grow, so "· Weekly" is weighed against
     * the smaller of the two (A6 judge F2) — never kept only to be cut inside it.
     */
    fun duetLabelMaxDp(hasRing: Boolean): Float = if (hasRing) DUET_LABEL_MAX_DP - RING_BOX_DP - COLUMN_GAP_DP else DUET_LABEL_MAX_DP
    const val DUET_LABEL_MAX_DP = 200f

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
