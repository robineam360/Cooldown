package com.robin.claudeusage.ui

import kotlin.math.floor

/**
 * CCRM-107 (Day Grid): the grid every 7-day chart, 5h chart and the Pace map draw under everything
 * else, and the ordinals on their time axis (core §6.3, wireframe
 * `design/2026-11-10-v20-feedback-wireframes.html` §3). Pure, so `DayGridTest` pins it against
 * `printed.json` → `dayGrid`.
 *
 * A 7-day window has seven equal columns (start + k × 24 h, window-relative days, not midnights) and
 * lines at k/7 of 100%; a 5-hour window five hourly columns and lines at k/5. Each crossing (k, k)
 * sits on the even-pace diagonal. The ordinals 2…n−1 are centred in their columns; the one in the
 * column holding now is bold; an ordinal gives way to "now" or to an end label it would touch, and
 * every ordinal drops when a column is narrower than [DROP_BELOW_COLUMN].
 */
object DayGrid {

    /** An ordinal gives way when its box is less than this from the "now" label's (the wireframe's words). */
    const val NOW_GAP = 6f

    /** … or less than this from an end label's box (the wireframe's drawing). */
    const val END_GAP = 4f

    /** Columns narrower than this carry no ordinals; the grid lines still draw. */
    const val DROP_BELOW_COLUMN = 14f

    private const val FIVE_HOURS_MS = 5 * 60 * 60_000L
    private const val TWELVE_HOURS_MS = 12 * 60 * 60_000L

    /** Seven columns for a window measured in days, five for one measured in hours (the 5h). */
    fun columns(spanMs: Long): Int = if (spanMs <= TWELVE_HOURS_MS) 5 else 7

    /** The k-th column line's instant, k = 1…n−1: window start + k × (span / n) — exactly + k × 24 h or + k × 1 h. */
    fun verticalAt(windowStartMs: Long, spanMs: Long, k: Int, n: Int = columns(spanMs)): Long =
        windowStartMs + spanMs * k / n

    /** The grid's horizontal lines, k = 1…n: k/n of 100% (the n-th is the 100% guide, which draws over it). */
    fun horizontals(n: Int): List<Double> = (1..n).map { 100.0 * it / n }

    /** 1-based; an instant on a column line belongs to the later column. Clamped to 1…n. */
    fun nowColumn(windowStartMs: Long, spanMs: Long, nowMs: Long, n: Int = columns(spanMs)): Int {
        val f = (nowMs - windowStartMs).toDouble() / spanMs * n
        return (floor(f + 1e-9).toInt() + 1).coerceIn(1, n)
    }

    /** An ordinal's verdict; [centreX] in the plot's units from its left edge. */
    data class Ordinal(val text: String, val column: Int, val drawn: Boolean, val centreX: Float, val bold: Boolean)

    /**
     * The ordinals 2…n−1 on a time axis [plotWidth] wide. [startWidth] / [endWidth]: the end labels, left-
     * aligned at 0 and right-aligned at [plotWidth]; [nowX] the "now" label's centre, null when it is not
     * drawn; [nowWidth] its width; [ordinalWidth] one ordinal's. [nowColumn] 1-based, or 0 for none bold
     * (the Pace map: each account's own week).
     */
    fun ordinals(
        n: Int,
        plotWidth: Float,
        startWidth: Float,
        endWidth: Float,
        ordinalWidth: (String) -> Float,
        nowX: Float?,
        nowWidth: Float,
        nowColumn: Int,
        nowGap: Float = NOW_GAP,
        endGap: Float = END_GAP,
        dropBelowColumn: Float = DROP_BELOW_COLUMN,
    ): List<Ordinal> {
        val col = plotWidth / n
        return (2 until n).map { k ->
            val text = k.toString()
            val cx = plotWidth * (k - 0.5f) / n
            val half = ordinalWidth(text) / 2f
            val left = cx - half
            val right = cx + half
            val clearOfNow = nowX == null || gap(left, right, nowX - nowWidth / 2f, nowX + nowWidth / 2f) >= nowGap
            val clearOfEnds = gap(left, right, 0f, startWidth) >= endGap &&
                gap(left, right, plotWidth - endWidth, plotWidth) >= endGap
            val drawn = col >= dropBelowColumn && clearOfNow && clearOfEnds
            Ordinal(text, k, drawn, cx, drawn && k == nowColumn)
        }
    }

    /** The space between two boxes on one axis; minus the overlap's length when they overlap (the fixture's gaps). */
    fun gap(aLeft: Float, aRight: Float, bLeft: Float, bRight: Float): Float =
        maxOf(aLeft, bLeft) - minOf(aRight, bRight)
}
