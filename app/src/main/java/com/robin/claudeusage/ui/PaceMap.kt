package com.robin.claudeusage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2

/**
 * CCRM-98 (Summary Tab): the Pace map — each account's week as a trail, its dot at
 * (week gone, Weekly used), against the even-pace diagonal
 * (`design/2026-11-02-summary-tab.html` rev B, C1/S2/W1). No 5h, no combined figure.
 */
object PaceMapGeometry {

    /** The plot's insets in dp, as the wireframe's 30 / 10 / 12 / 26. */
    const val LEFT_DP = 30f
    const val RIGHT_DP = 10f
    const val TOP_DP = 12f
    const val BOTTOM_DP = 26f

    data class Frame(val left: Float, val right: Float, val top: Float, val bottom: Float) {
        fun x(gonePct: Double): Float = left + (right - left) * (gonePct.coerceIn(0.0, 100.0) / 100.0).toFloat()
        fun y(usedPct: Double): Float = bottom - (bottom - top) * (usedPct.coerceIn(0.0, 100.0) / 100.0).toFloat()
    }

    fun frame(width: Float, height: Float, dpPx: Float): Frame =
        Frame(LEFT_DP * dpPx, width - RIGHT_DP * dpPx, TOP_DP * dpPx, height - BOTTOM_DP * dpPx)

    /**
     * The trail: this week's samples as (week gone %, used %), oldest first, ending on the
     * live point. Samples outside the week are dropped; [samples] are (epoch ms, used %).
     */
    fun trail(
        samples: List<Pair<Long, Double>>,
        resetMs: Long,
        weekMs: Long,
        gonePct: Double,
        usedPct: Double,
    ): List<Pair<Double, Double>> {
        val start = resetMs - weekMs
        val pts = samples.asSequence()
            .filter { it.first in start..resetMs }
            .sortedBy { it.first }
            .map { (t, pct) -> (t - start) * 100.0 / weekMs to pct.coerceIn(0.0, 100.0) }
            .filter { it.first <= gonePct }
            .toMutableList()
        pts += gonePct to usedPct.coerceIn(0.0, 100.0)
        return pts
    }

    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float) {
        val right: Float get() = x + w
        val bottom: Float get() = y + h
    }

    /** A dot at ([cx], [cy]) and the size of its measured name. */
    data class LabelReq(val cx: Float, val cy: Float, val w: Float, val h: Float)

    enum class Side { RIGHT, BELOW, LEFT, ABOVE_LEFT, LEADER }

    data class Leader(val fromX: Float, val fromY: Float, val toX: Float, val toY: Float)

    /** Where one name goes: [rect] is its box; a [Side.LEADER] one also carries its line. */
    data class Placed(val rect: Rect, val side: Side, val leader: Leader?)

    /** Even-pace label: [atPct] along the diagonal (week gone %) and its bounding box once rotated. */
    data class EvenPlacement(val atPct: Double, val rect: Rect)

    private fun hit(a: Rect, b: Rect, pad: Float) =
        a.x < b.right + pad && b.x < a.right + pad && a.y < b.bottom + pad && b.y < a.bottom + pad

    /** Half-size of the hit box kept around a dot (dot 5.5 + ring 2 dp, rounded up). */
    private const val DOT_HIT_DP = 7f

    /** The area names may occupy: a little outside the axes, never over the foot words. */
    private fun bounds(f: Frame, d: Float): Rect {
        val l = f.left - 2f * d; val t = f.top - 6f * d
        return Rect(l, t, (f.right + 8f * d) - l, (f.bottom + 6f * d) - t)
    }

    private fun inside(r: Rect, b: Rect) = r.x >= b.x && r.right <= b.right && r.y >= b.y && r.bottom <= b.bottom

    /**
     * The "even pace" label slides along the diagonal, from week-gone 62% outward in 6% steps, to
     * the first stretch free of every dot box in [dots] and every [fixed] word. [w] and [h] are the
     * unrotated text size; the box returned is the rotated text's bounds. Falls back to 62%.
     */
    fun evenPace(f: Frame, d: Float, w: Float, h: Float, dots: List<Rect>, fixed: List<Rect>): EvenPlacement {
        val ang = Math.atan2((f.y(100.0) - f.y(0.0)).toDouble(), (f.x(100.0) - f.x(0.0)).toDouble())
        val c = Math.abs(Math.cos(ang)).toFloat(); val s = Math.abs(Math.sin(ang)).toFloat()
        val hw = w / 2f * c + h / 2f * s
        val hh = w / 2f * s + h / 2f * c
        fun at(t: Double): Rect {
            val ex = f.x(t); val ey = f.y(t) - 5f * d
            // text centre = pivot + (0, -h/2) rotated by the diagonal's angle
            val cx = ex + (h / 2f) * Math.sin(ang).toFloat()
            val cy = ey - (h / 2f) * Math.cos(ang).toFloat()
            return Rect(cx - hw, cy - hh, hw * 2f, hh * 2f)
        }
        for (k in 0..16) {
            val t = 62.0 + (if (k % 2 == 0) 1 else -1) * ((k + 1) / 2) * 6.0
            if (t < 12.0 || t > 90.0) continue
            val r = at(t)
            if (dots.none { hit(r, it, 0f) } && fixed.none { hit(r, it, 1f) }) return EvenPlacement(t, r)
        }
        return EvenPlacement(62.0, at(62.0))
    }

    /** The hit box of a dot, for [evenPace] and [placeLabels]. */
    fun dotBox(cx: Float, cy: Float, d: Float) = Rect(cx - DOT_HIT_DP * d, cy - DOT_HIT_DP * d, 2f * DOT_HIT_DP * d, 2f * DOT_HIT_DP * d)

    /**
     * Name placement, D7 / review C1-1. Labels go top-down (highest used % first) so the lower
     * label is the one that moves. For each: right of its dot, below, left, above-left; a candidate
     * is refused if it leaves the plot or overlaps a placed label (2 dp apart), any dot, or a [fixed]
     * word (wedge words, axis words, the centre note, "even pace"). All four refused: a short leader
     * to a free spot at the plot's right edge, trying y offsets 0, -18, 18, -36, 36, -54, 54 dp.
     * Results come back in the order of [dots]. If nothing is free the right-hand candidate stands.
     */
    fun placeLabels(f: Frame, d: Float, dots: List<LabelReq>, fixed: List<Rect>): List<Placed> {
        val area = bounds(f, d)
        val dotBoxes = dots.map { dotBox(it.cx, it.cy, d) }
        val placed = ArrayList<Rect>()
        val out = arrayOfNulls<Placed>(dots.size)
        fun free(r: Rect) = inside(r, area) && fixed.none { hit(r, it, 1f) } && dotBoxes.none { hit(r, it, 0f) } && placed.none { hit(r, it, 2f * d) }
        val order = dots.indices.sortedWith(compareBy({ dots[it].cy }, { dots[it].cx }))
        for (i in order) {
            val q = dots[i]
            val cands = listOf(
                Side.RIGHT to Rect(q.cx + 10f * d, q.cy - q.h / 2f, q.w, q.h),
                Side.BELOW to Rect(q.cx + 2f * d, q.cy + q.h / 2f + 3f * d, q.w, q.h),
                Side.LEFT to Rect(q.cx - 10f * d - q.w, q.cy - q.h / 2f, q.w, q.h),
                Side.ABOVE_LEFT to Rect(q.cx - 10f * d - q.w, q.cy - 6f * d - q.h, q.w, q.h),
            )
            var pick: Placed? = cands.firstOrNull { free(it.second) }?.let { Placed(it.second, it.first, null) }
            if (pick == null) {
                for (dy in listOf(0f, -18f, 18f, -36f, 36f, -54f, 54f)) {
                    val yc = (q.cy + dy * d).coerceIn(f.top + 4f * d, f.bottom - 10f * d)
                    val r = Rect(f.right + 4f * d - q.w, yc - q.h / 2f, q.w, q.h)
                    if (!free(r)) continue
                    var vx = r.x - 3f * d - q.cx; var vy = yc - q.cy
                    val len = Math.hypot(vx.toDouble(), vy.toDouble()).toFloat().coerceAtLeast(1e-3f)
                    vx /= len; vy /= len
                    val from = DOT_HIT_DP * d
                    pick = Placed(r, Side.LEADER, Leader(q.cx + vx * from, q.cy + vy * from, r.x - 3f * d, yc))
                    break
                }
            }
            val got = pick ?: Placed(cands[0].second, Side.RIGHT, null)
            placed += got.rect
            out[i] = got
        }
        return out.map { it!! }
    }
}

/** One account on the map. [number] is its list rank (not drawn: names label every dot). */
data class PaceMapEntry(
    val name: String,
    val color: Color,
    val gonePct: Double,
    val usedPct: Double,
    val spent: Boolean,
    val trail: List<Pair<Double, Double>>,
    val number: Int,
)

@Composable
fun PaceMapCard(entries: List<PaceMapEntry>, plotHeight: Dp, modifier: Modifier = Modifier) {
    androidx.compose.material3.Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Pace map", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    "Weekly, where each account stands",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            PaceMap(entries, modifier = Modifier.fillMaxWidth().height(plotHeight))
        }
    }
}

/**
 * CCRM-104 (Pace Line Parity), D7: names label every dot at every count; no numbered dots.
 * The parameter stays only so existing call sites compile.
 */
@Composable
fun PaceMap(entries: List<PaceMapEntry>, modifier: Modifier = Modifier) {
    val dark = appDark()
    val ink = MaterialTheme.colorScheme.onSurface
    val card = MaterialTheme.colorScheme.surfaceContainer
    val pace = Palette.barColor(85.0, ink, dark)
    val red = Palette.barColor(100.0, ink, dark)
    val measurer = rememberTextMeasurer()
    val tiny = TextStyle(fontSize = 10.sp)
    val label = TextStyle(fontSize = 10.5.sp)
    val nameStyle = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = 0.92f))
    Canvas(modifier) {
        val f = PaceMapGeometry.frame(size.width, size.height, density)
        // ahead of pace: the triangle above the diagonal, a faint red
        drawPath(
            Path().apply {
                moveTo(f.x(0.0), f.y(0.0)); lineTo(f.x(100.0), f.y(100.0)); lineTo(f.x(0.0), f.y(100.0)); close()
            },
            red.copy(alpha = if (dark) 0.07f else 0.05f),
        )
        // every fixed word is a refused area for the names
        val fixed = ArrayList<PaceMapGeometry.Rect>()
        fun fixedText(t: androidx.compose.ui.text.TextLayoutResult, o: Offset) {
            fixed += PaceMapGeometry.Rect(o.x, o.y, t.size.width.toFloat(), t.size.height.toFloat())
            drawText(t, topLeft = o)
        }
        for (v in listOf(0.0, 50.0, 100.0)) {
            drawLine(ink.copy(alpha = if (v == 0.0) 0.35f else 0.12f), Offset(f.x(0.0), f.y(v)), Offset(f.x(100.0), f.y(v)), 1.dp.toPx())
            val t = measurer.measure("${v.toInt()}%", tiny.copy(color = ink.copy(alpha = 0.6f)))
            fixedText(t, Offset(f.left - 5.dp.toPx() - t.size.width, f.y(v) - t.size.height / 2f))
        }
        drawLine(
            pace, Offset(f.x(0.0), f.y(0.0)), Offset(f.x(100.0), f.y(100.0)), 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())),
        )
        val muted = label.copy(color = ink.copy(alpha = 0.55f))
        measurer.measure(PaceText.MAP_AHEAD, muted).let { fixedText(it, Offset(f.x(3.0), f.y(91.0) - it.size.height)) }
        measurer.measure(PaceText.MAP_BEHIND, muted).let { fixedText(it, Offset(f.x(97.0) - it.size.width, f.y(6.0) - it.size.height)) }
        val axis = label.copy(color = ink.copy(alpha = 0.7f))
        val foot = size.height - 4.dp.toPx()
        measurer.measure(PaceText.MAP_WEEK_START, axis).let { fixedText(it, Offset(f.x(0.0), foot - it.size.height)) }
        measurer.measure(PaceText.MAP_RESET, axis).let { fixedText(it, Offset(f.x(100.0) - it.size.width, foot - it.size.height)) }
        measurer.measure("each account's own week", tiny.copy(color = ink.copy(alpha = 0.45f))).let {
            fixedText(it, Offset(f.x(50.0) - it.size.width / 2f, foot - it.size.height))
        }
        // "even pace" slides along the diagonal to the first stretch no dot sits on
        val even = measurer.measure(PaceText.MAP_EVEN, tiny.copy(color = pace))
        val dotBoxes = entries.map { PaceMapGeometry.dotBox(f.x(it.gonePct), f.y(it.usedPct), density) }
        val ep = PaceMapGeometry.evenPace(f, density, even.size.width.toFloat(), even.size.height.toFloat(), dotBoxes, fixed.toList())
        fixed += ep.rect
        val angle = Math.toDegrees(atan2((f.y(100.0) - f.y(0.0)).toDouble(), (f.x(100.0) - f.x(0.0)).toDouble())).toFloat()
        val ex = f.x(ep.atPct); val ey = f.y(ep.atPct) - 5.dp.toPx()
        rotate(angle, pivot = Offset(ex, ey)) {
            drawText(even, topLeft = Offset(ex - even.size.width / 2f, ey - even.size.height))
        }
        // trails first, so every dot sits on top of every line
        for (e in entries) {
            if (e.trail.size < 2) continue
            val path = Path()
            e.trail.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(f.x(x), f.y(y)) else path.lineTo(f.x(x), f.y(y)) }
            drawPath(path, e.color.copy(alpha = 0.55f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val names = entries.map { measurer.measure(it.name, nameStyle) }
        val placed = PaceMapGeometry.placeLabels(
            f, density,
            entries.mapIndexed { i, e ->
                PaceMapGeometry.LabelReq(f.x(e.gonePct), f.y(e.usedPct), names[i].size.width.toFloat(), names[i].size.height.toFloat())
            },
            fixed,
        )
        // leader lines first, then the dots, then the names
        entries.forEachIndexed { i, e ->
            placed[i].leader?.let { drawLine(e.color.copy(alpha = 0.55f), Offset(it.fromX, it.fromY), Offset(it.toX, it.toY), 1.dp.toPx()) }
        }
        for (e in entries) dot(e, f, card)
        entries.forEachIndexed { i, _ -> drawText(names[i], topLeft = Offset(placed[i].rect.x, placed[i].rect.y)) }
    }
}

private fun DrawScope.dot(e: PaceMapEntry, f: PaceMapGeometry.Frame, card: Color) {
    val c = Offset(f.x(e.gonePct), f.y(e.usedPct))
    val r = 5.5f * density
    drawCircle(card, r + 2f * density, c)
    drawCircle(e.color, r, c)
    if (e.spent) {
        val onDot = Color(0xFF121212)
        val d = 3f * density
        drawLine(onDot, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), 1.8f * density)
        drawLine(onDot, Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), 1.8f * density)
    }
}
