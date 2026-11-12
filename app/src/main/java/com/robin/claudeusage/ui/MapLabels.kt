package com.robin.claudeusage.ui

import kotlin.math.hypot

/**
 * CCRM-111 (Map Layers), core §6.3: where the Pace map's names go, closing CCBG-79
 * (Leader Reads as Trail) on Android. Names go top-down (smallest y, then smallest x); each takes the first
 * free of right, below, left and above-left, then a short dotted leader 24, 36 or 48 from its dot in eight
 * directions (E, SE, S, SW, W, NW, N, NE). A spot is refused when it leaves the name area, comes near a fixed
 * word, overlaps a dot's hit box, comes near a placed name, sits on a ring or crosses its own projection tail;
 * a leader also when its path crosses a placed name or another dot. Nothing free: the name is not drawn (its
 * dot stays and still opens its callout). Pure and unit-agnostic (the app calls it in px, with [Geometry.scaled]);
 * `printed.json` → `mapLabels.placement` pins it in dp, as iOS's `MapLabels` is pinned in pt.
 */
object MapLabels {

    data class Rect(val x: Double, val y: Double, val w: Double, val h: Double) {
        val right: Double get() = x + w
        val bottom: Double get() = y + h

        /** Closer than [pad] to [o]: with a [pad] of 0, an overlap (touching edges do not count). */
        fun near(o: Rect, pad: Double): Boolean = x < o.right + pad && o.x < right + pad && y < o.bottom + pad && o.y < bottom + pad
    }

    /** How far past the plot a name may sit. */
    data class Margin(val left: Double, val top: Double, val right: Double, val bottom: Double)

    data class Geometry(
        val dotHalf: Double,
        val nameGap: Double,
        val fixedGap: Double,
        val margin: Margin,
        val rightDx: Double,
        val belowDx: Double,
        val belowDy: Double,
        val leftDx: Double,
        val aboveDy: Double,
        val leaderDistances: List<Double>,
    ) {
        /** The same geometry at [d] px per dp. */
        fun scaled(d: Double): Geometry = Geometry(
            dotHalf * d, nameGap * d, fixedGap * d, Margin(margin.left * d, margin.top * d, margin.right * d, margin.bottom * d),
            rightDx * d, belowDx * d, belowDy * d, leftDx * d, aboveDy * d, leaderDistances.map { it * d },
        )

        companion object {
            /** The fixture's (core `mapLabels`), the offsets the Pace map has always drawn: 7 dp hit box, names 10 dp off the dot. */
            val DP = Geometry(
                dotHalf = 7.0, nameGap = 2.0, fixedGap = 1.0, margin = Margin(2.0, 6.0, 8.0, 6.0),
                rightDx = 10.0, belowDx = 2.0, belowDy = 3.0, leftDx = 10.0, aboveDy = 6.0,
                leaderDistances = listOf(24.0, 36.0, 48.0),
            )
        }
    }

    /** One dot and the size of its label (the name, and with Figures on its figure). */
    data class Dot(
        val key: String,
        val x: Double,
        val y: Double,
        val w: Double,
        val h: Double,
        /** With Projection on: where this dot's tail ends. */
        val tailEnd: Pair<Double, Double>? = null,
    )

    enum class Spot { RIGHT, BELOW, LEFT, ABOVE_LEFT, LEADER, NONE }

    enum class Direction(val ux: Double, val uy: Double) {
        E(1.0, 0.0), SE(1.0, 1.0), S(0.0, 1.0), SW(-1.0, 1.0), W(-1.0, 0.0), NW(-1.0, -1.0), N(0.0, -1.0), NE(1.0, -1.0),
    }

    /** A leader: [distance] along [direction] from the dot centre to ([endX], [endY]), where the name's box touches. */
    data class Leader(val distance: Double, val direction: Direction, val endX: Double, val endY: Double)

    data class Placement(
        val key: String,
        val spot: Spot,
        /** Null when the name is not drawn. */
        val box: Rect?,
        val leader: Leader?,
        /** The reasons met on the way, for reading a case. */
        val refusedFor: Set<String>,
    )

    /** The order names are placed in: smallest y first, then smallest x, then the input's. */
    fun order(dots: List<Dot>): List<Dot> =
        dots.withIndex().sortedWith(compareBy({ it.value.y }, { it.value.x }, { it.index })).map { it.value }

    /**
     * Places every name; the result is in the input's order. [area] is the plot with its margins (where a name
     * may sit), in the same coordinates as the dots; [fixed] the words that stay; [rings] the projection rings.
     */
    fun place(dots: List<Dot>, area: Rect, geometry: Geometry, fixed: List<Rect>, rings: List<Rect>): List<Placement> {
        val g = geometry
        val hitBox = dots.associate { it.key to Rect(it.x - g.dotHalf, it.y - g.dotHalf, 2 * g.dotHalf, 2 * g.dotHalf) }
        val placed = ArrayList<Rect>()
        val results = HashMap<String, Placement>()

        fun inside(r: Rect) = r.x >= area.x && r.y >= area.y && r.right <= area.right && r.bottom <= area.bottom

        fun refuse(r: Rect, me: Dot): String? {
            if (!inside(r)) return "leaves the plot"
            if (fixed.any { r.near(it, g.fixedGap) }) return "overlaps a word"
            for (d in dots) if (r.near(hitBox.getValue(d.key), 0.0)) return if (d.key == me.key) "overlaps its own dot" else "overlaps a dot"
            if (placed.any { r.near(it, g.nameGap) }) return "overlaps a placed name"
            if (rings.any { r.near(it, 0.0) }) return "sits on a ring"
            val t = me.tailEnd
            if (t != null && segmentHits(me.x to me.y, t, r)) return "sits on its own tail"
            return null
        }

        for (d in order(dots)) {
            val w = d.w
            val h = d.h
            val candidates = listOf(
                Spot.RIGHT to Rect(d.x + g.rightDx, d.y - h / 2, w, h),
                Spot.BELOW to Rect(d.x + g.belowDx, d.y + h / 2 + g.belowDy, w, h),
                Spot.LEFT to Rect(d.x - g.leftDx - w, d.y - h / 2, w, h),
                Spot.ABOVE_LEFT to Rect(d.x - g.leftDx - w, d.y - g.aboveDy - h, w, h),
            )
            val refused = HashSet<String>()
            var pick: Placement? = null
            for ((spot, r) in candidates) {
                val why = refuse(r, d)
                if (why != null) { refused += why; continue }
                pick = Placement(d.key, spot, r, null, emptySet())
                break
            }
            if (pick == null) {
                search@ for (dist in g.leaderDistances) {
                    for (dir in Direction.entries) {
                        val n = hypot(dir.ux, dir.uy)
                        val ex = d.x + dist * dir.ux / n
                        val ey = d.y + dist * dir.uy / n
                        val bx = if (dir.ux > 0) ex else if (dir.ux < 0) ex - w else ex - w / 2
                        val by = if (dir.uy > 0) ey else if (dir.uy < 0) ey - h else ey - h / 2
                        val r = Rect(bx, by, w, h)
                        var why = refuse(r, d)
                        if (why == null && placed.any { segmentHits(d.x to d.y, ex to ey, it) }) why = "path crosses a placed name"
                        if (why == null && dots.any { it.key != d.key && segmentHits(d.x to d.y, ex to ey, hitBox.getValue(it.key)) }) {
                            why = "path crosses a dot"
                        }
                        if (why != null) { refused += why; continue }
                        pick = Placement(d.key, Spot.LEADER, r, Leader(dist, dir, ex, ey), emptySet())
                        break@search
                    }
                }
            }
            val final = pick ?: Placement(d.key, Spot.NONE, null, null, emptySet())
            final.box?.let { placed += it }
            results[d.key] = final.copy(refusedFor = refused)
        }
        return dots.map { results.getValue(it.key) }
    }

    /** Does the segment p–q meet the rectangle [r]? Liang–Barsky clipping. */
    fun segmentHits(p: Pair<Double, Double>, q: Pair<Double, Double>, r: Rect): Boolean {
        val dx = q.first - p.first
        val dy = q.second - p.second
        var t0 = 0.0
        var t1 = 1.0
        val checks = listOf(
            -dx to p.first - r.x,
            dx to r.right - p.first,
            -dy to p.second - r.y,
            dy to r.bottom - p.second,
        )
        for ((pv, qv) in checks) {
            if (pv == 0.0) {
                if (qv < 0) return false
            } else {
                val t = qv / pv
                if (pv < 0) t0 = maxOf(t0, t) else t1 = minOf(t1, t)
                if (t0 > t1) return false
            }
        }
        return true
    }
}
