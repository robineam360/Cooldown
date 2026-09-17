package com.robin.claudeusage.ui

/**
 * CCRM-72 (Main Screen Redesign): the global *Comfortable* / *Compact* chip in
 * Settings → Appearance. *Comfortable* is today's layout — every card open, plus the
 * CCRM-72 status line. *Compact* redraws each card to a title row, a bar and one pace
 * line, with the chart folded away until the card is tapped.
 */
enum class Density(val key: String) {
    COMFORTABLE("comfortable"),
    COMPACT("compact"),
    ;

    companion object {
        val DEFAULT = COMFORTABLE

        fun fromKey(key: String?): Density = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * CCRM-75 (Chart Height): the global *Chart height · Small / Medium / Large* chip in
 * Settings → Appearance, under Density. Orthogonal to [Density] — Density folds the
 * chart away entirely; this sizes it once it's showing.
 *
 * [heightDp] is null for [LARGE], which keeps the width-derived formula in
 * `Adaptive.chartHeight` (180–300dp) rather than a fixed value. [linesOnly] is true
 * only for [SMALL]: at that height the chart drops its guide labels, x-axis dates,
 * value callouts and legend, keeping only the guides, the even-pace diagonal, the
 * curve with its fill, the now divider and the projection dot and dash — the pace
 * readout and estimate lines under the chart carry the numbers instead.
 *
 * Default is [MEDIUM] (Robin's decision 2026-09-17), not [LARGE]/today's behaviour.
 */
enum class ChartSize(val key: String, val heightDp: Int?, val linesOnly: Boolean) {
    SMALL("small", 72, true),
    MEDIUM("medium", 120, false),
    LARGE("large", null, false),
    ;

    companion object {
        val DEFAULT = MEDIUM

        fun fromKey(key: String?): ChartSize = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * CCRM-77 (Transposed Chart): the global *Chart orientation* toggle in Settings →
 * Appearance, painted for decision in the CCRM-72 (Main Screen Redesign) wireframe rev
 * D. [ACROSS] is today's geometry — time on the x axis, usage on the y axis. [DOWN]
 * transposes it: usage runs across, time runs down, so the bar above and the chart
 * below share the x axis outright.
 */
enum class ChartOrientation(val key: String) {
    ACROSS("across"),
    DOWN("down"),
    ;

    companion object {
        val DEFAULT = ACROSS

        fun fromKey(key: String?): ChartOrientation = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
