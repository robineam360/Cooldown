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

    /** A name label sits left of its dot past 70% of the week, and under it near the top. */
    fun labelLeft(gonePct: Double): Boolean = gonePct > 70.0
    fun labelBelow(usedPct: Double): Boolean = usedPct > 92.0
}

/** One account on the map. [number] is its rank, drawn on the dot from six accounts. */
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
fun PaceMapCard(entries: List<PaceMapEntry>, numbered: Boolean, plotHeight: Dp, modifier: Modifier = Modifier) {
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
            PaceMap(entries, numbered, Modifier.fillMaxWidth().height(plotHeight))
        }
    }
}

@Composable
fun PaceMap(entries: List<PaceMapEntry>, numbered: Boolean, modifier: Modifier = Modifier) {
    val dark = appDark()
    val ink = MaterialTheme.colorScheme.onSurface
    val card = MaterialTheme.colorScheme.surfaceContainer
    val pace = Palette.barColor(85.0, ink, dark)
    val red = Palette.barColor(100.0, ink, dark)
    val measurer = rememberTextMeasurer()
    val tiny = TextStyle(fontSize = 10.sp)
    val label = TextStyle(fontSize = 10.5.sp)
    Canvas(modifier) {
        val f = PaceMapGeometry.frame(size.width, size.height, density)
        // over pace: the triangle above the diagonal, a faint red
        drawPath(
            Path().apply {
                moveTo(f.x(0.0), f.y(0.0)); lineTo(f.x(100.0), f.y(100.0)); lineTo(f.x(0.0), f.y(100.0)); close()
            },
            red.copy(alpha = if (dark) 0.07f else 0.05f),
        )
        for (v in listOf(0.0, 50.0, 100.0)) {
            drawLine(ink.copy(alpha = if (v == 0.0) 0.35f else 0.12f), Offset(f.x(0.0), f.y(v)), Offset(f.x(100.0), f.y(v)), 1.dp.toPx())
            val t = measurer.measure("${v.toInt()}%", tiny.copy(color = ink.copy(alpha = 0.6f)))
            drawText(t, topLeft = Offset(f.left - 5.dp.toPx() - t.size.width, f.y(v) - t.size.height / 2f))
        }
        drawLine(
            pace, Offset(f.x(0.0), f.y(0.0)), Offset(f.x(100.0), f.y(100.0)), 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())),
        )
        val muted = label.copy(color = ink.copy(alpha = 0.55f))
        measurer.measure("over pace", muted).let { drawText(it, topLeft = Offset(f.x(3.0), f.y(91.0) - it.size.height)) }
        measurer.measure("room to spare", muted).let {
            drawText(it, topLeft = Offset(f.x(97.0) - it.size.width, f.y(6.0) - it.size.height))
        }
        val angle = Math.toDegrees(atan2((f.y(100.0) - f.y(0.0)).toDouble(), (f.x(100.0) - f.x(0.0)).toDouble())).toFloat()
        val even = measurer.measure("even pace", tiny.copy(color = pace))
        val ex = f.x(84.0); val ey = f.y(79.0)
        rotate(angle, pivot = Offset(ex, ey)) {
            drawText(even, topLeft = Offset(ex - even.size.width / 2f, ey - even.size.height))
        }
        val axis = label.copy(color = ink.copy(alpha = 0.7f))
        val foot = size.height - 4.dp.toPx()
        measurer.measure("week start", axis).let { drawText(it, topLeft = Offset(f.x(0.0), foot - it.size.height)) }
        measurer.measure("reset", axis).let { drawText(it, topLeft = Offset(f.x(100.0) - it.size.width, foot - it.size.height)) }
        measurer.measure("each account's own week", tiny.copy(color = ink.copy(alpha = 0.45f))).let {
            drawText(it, topLeft = Offset(f.x(50.0) - it.size.width / 2f, foot - it.size.height))
        }
        // trails first, so every dot sits on top of every line
        for (e in entries) {
            if (e.trail.size < 2) continue
            val path = Path()
            e.trail.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(f.x(x), f.y(y)) else path.lineTo(f.x(x), f.y(y)) }
            drawPath(path, e.color.copy(alpha = 0.55f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        for (e in entries) dot(e, f, numbered, card, ink, measurer)
    }
}

private fun DrawScope.dot(
    e: PaceMapEntry,
    f: PaceMapGeometry.Frame,
    numbered: Boolean,
    card: Color,
    ink: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val c = Offset(f.x(e.gonePct), f.y(e.usedPct))
    val r = (if (numbered) 8f else 5.5f) * density
    drawCircle(card, r + 2f * density, c)
    drawCircle(e.color, r, c)
    val onDot = Color(0xFF121212)
    if (e.spent) {
        val d = 3f * density
        drawLine(onDot, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), 1.8f * density)
        drawLine(onDot, Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), 1.8f * density)
    }
    if (numbered) {
        if (!e.spent) {
            val t = measurer.measure("${e.number}", TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = onDot))
            drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
        }
    } else {
        val t = measurer.measure(e.name, TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = 0.92f)))
        val left = PaceMapGeometry.labelLeft(e.gonePct)
        val x = if (left) c.x - 10f * density - t.size.width else c.x + 10f * density
        val y = if (PaceMapGeometry.labelBelow(e.usedPct)) c.y + 4f * density else c.y - 4f * density - t.size.height
        drawText(t, topLeft = Offset(x, y))
    }
}
