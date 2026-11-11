package com.robin.claudeusage.data

/**
 * CCRM-111 (Map Layers): the Pace map's three layers (wireframe `design/2026-11-10-v20-feedback-wireframes.html` §7).
 * Figures puts the figure after each name, Trails draws each week's path (the last 24 h heavier), Projection a dashed
 * tail to a hollow ring. Defaults: Figures off, Trails on, Projection off; remembered in [UsageCache.mapLayers].
 */
data class MapLayers(
    val figures: Boolean = false,
    val trails: Boolean = true,
    val projection: Boolean = false,
) {
    companion object {
        const val KEY_FIGURES = "paceMap.figures"
        const val KEY_TRAILS = "paceMap.trails"
        const val KEY_PROJECTION = "paceMap.projection"
    }
}
