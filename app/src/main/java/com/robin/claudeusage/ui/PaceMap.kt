package com.robin.claudeusage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robin.claudeusage.data.MapLayers
import com.robin.claudeusage.data.Projection
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

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

    fun frame(width: Float, height: Float, dpPx: Float, axisLabelPx: Float = 0f): Frame =
        Frame(leftPx(axisLabelPx, dpPx), width - RIGHT_DP * dpPx, TOP_DP * dpPx, height - BOTTOM_DP * dpPx)

    /** The y-axis labels end [AXIS_GAP_DP] left of the plot and keep [AXIS_MARGIN_DP] from the card's edge. */
    const val AXIS_GAP_DP = 5f
    const val AXIS_MARGIN_DP = 1f

    /**
     * CCBG-89 (Map Axis Clip): the left inset is the wireframe's 30 dp, or wider when the widest y label ("100%", measured
     * at the current font scale, [axisLabelPx]) would not fit beside the plot — at font 2.0 it printed "00%".
     */
    fun leftPx(axisLabelPx: Float, dpPx: Float): Float =
        maxOf(LEFT_DP * dpPx, axisLabelPx + (AXIS_GAP_DP + AXIS_MARGIN_DP) * dpPx)

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

    /** Even-pace label: [atPct] along the diagonal (week gone %) and its bounding box once rotated. */
    data class EvenPlacement(val atPct: Double, val rect: Rect)

    private fun hit(a: Rect, b: Rect, pad: Float) =
        a.x < b.right + pad && b.x < a.right + pad && a.y < b.bottom + pad && b.y < a.bottom + pad

    /** Half-size of the hit box kept around a dot (dot 5.5 + ring 2 dp, rounded up). */
    private const val DOT_HIT_DP = 7f

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

    /** The hit box of a dot, for [evenPace]. */
    fun dotBox(cx: Float, cy: Float, d: Float) = Rect(cx - DOT_HIT_DP * d, cy - DOT_HIT_DP * d, 2f * DOT_HIT_DP * d, 2f * DOT_HIT_DP * d)
}

/**
 * One account on the map. Names label every dot (no numbers, CCRM-104 (Pace Line Parity) D7); [key] is the account's
 * (its callout and tap), [estimate] and [resetMs] feed the Projection layer, and the callout's lines are prepared here:
 * the [paceLine] (tinted), else [plainLine] ("Out until Thu 8:00 AM", red when [plainRed]), then [forecast].
 */
data class PaceMapEntry(
    val key: String,
    val name: String,
    val color: Color,
    val gonePct: Double,
    val usedPct: Double,
    val spent: Boolean,
    val trail: List<Pair<Double, Double>>,
    val estimate: Projection.Estimate? = null,
    val resetMs: Long? = null,
    val paceLine: AppPace.Line? = null,
    val plainLine: String? = null,
    val plainRed: Boolean = false,
    val forecast: String? = null,
    val runsOut: Boolean = false,
)

/** CCRM-107 (Day Grid): the map's grid ink, ink at 10% as the wireframe draws it. */
const val MAP_GRID_ALPHA = 0.10f

/** A pinned account's neighbours draw at this strength (wireframe §7: "the others dim to 35%"). */
const val MAP_DIM_ALPHA = 0.35f

/** CCRM-111 (Map Layers): the figure's colour — the severity colour for Used and Left, green / red / ink for Vs pace. */
internal fun mapFigureColor(mode: PaceText.Mode, used: Double, elapsed: Double?, accent: Color, dark: Boolean, ink: Color): Color {
    val usage = Palette.barColor(used, accent, dark)
    return Grammar.vsColor(PaceMapRules.figureTone(mode, used, elapsed), dark, ink, usage)
}

/**
 * Everything the map draws, laid out once per size so the canvas, the tap targets and TalkBack agree (px). The
 * fixed words, the even-pace label, each dot's name (and, with Figures on, its figure) placed by [MapLabels], and
 * the projection rings.
 */
internal class PaceMapScene(
    val d: Float,
    val width: Float,
    val height: Float,
    val frame: PaceMapGeometry.Frame,
    val words: List<Word>,
    val even: TextLayoutResult,
    val evenAt: PaceMapGeometry.EvenPlacement,
    val items: List<Item>,
    val empty: Pair<TextLayoutResult, TextLayoutResult>?,
) {
    class Word(val layout: TextLayoutResult, val at: Offset)

    class Item(
        val entry: PaceMapEntry,
        val point: Offset,
        val label: TextLayoutResult,
        val figure: String?,
        val projection: PaceMapRules.ProjectionEnd?,
        val tailEnd: Offset?,
        val placement: MapLabels.Placement,
    )

    val targets: List<PaceMapRules.Target> = items.map { PaceMapRules.Target(it.entry.key, it.point.x, it.point.y, it.placement.box) }

    companion object {
        private val NAME = TextStyle(fontSize = 11.5.sp)

        fun build(
            measurer: TextMeasurer,
            d: Float,
            width: Float,
            height: Float,
            entries: List<PaceMapEntry>,
            layers: MapLayers,
            mode: PaceText.Mode,
            empty: PaceMapRules.Empty?,
            ink: Color,
            dark: Boolean,
        ): PaceMapScene {
            val tiny = TextStyle(fontSize = 10.sp)
            val f = PaceMapGeometry.frame(width, height, d, measurer.measure("100%", tiny).size.width.toFloat())
            val label = TextStyle(fontSize = 10.5.sp)
            val pace = Palette.barColor(85.0, ink, dark)
            val words = ArrayList<Word>()
            for (v in listOf(0.0, 50.0, 100.0)) {
                val t = measurer.measure("${v.toInt()}%", tiny.copy(color = ink.copy(alpha = 0.6f)))
                words += Word(t, Offset(f.left - PaceMapGeometry.AXIS_GAP_DP * d - t.size.width, f.y(v) - t.size.height / 2f))
            }
            val muted = label.copy(color = ink.copy(alpha = 0.55f))
            measurer.measure(PaceText.MAP_AHEAD, muted).let { words += Word(it, Offset(f.x(3.0), f.y(91.0) - it.size.height)) }
            measurer.measure(PaceText.MAP_BEHIND, muted).let { words += Word(it, Offset(f.x(97.0) - it.size.width, f.y(6.0) - it.size.height)) }
            val axis = label.copy(color = ink.copy(alpha = 0.7f))
            val foot = height - 4 * d
            val start = measurer.measure(PaceText.MAP_WEEK_START, axis)
            val end = measurer.measure(PaceText.MAP_RESET, axis)
            words += Word(start, Offset(f.x(0.0), foot - start.size.height))
            words += Word(end, Offset(f.x(100.0) - end.size.width, foot - end.size.height))
            // The ordinals 2…6 centred in their columns (each account's own week, so none is bold).
            val ordinalStyle = tiny.copy(color = ink.copy(alpha = 0.6f))
            val ordinals = DayGrid.ordinals(
                n = 7,
                plotWidth = (f.right - f.left) / d,
                startWidth = start.size.width / d,
                endWidth = end.size.width / d,
                ordinalWidth = { measurer.measure(it, ordinalStyle).size.width / d },
                nowX = null,
                nowWidth = 0f,
                nowColumn = 0,
            )
            for (o in ordinals) {
                if (!o.drawn) continue
                val t = measurer.measure(o.text, ordinalStyle)
                words += Word(t, Offset(f.left + o.centreX * d - t.size.width / 2f, foot - t.size.height))
            }
            val fixed = ArrayList(words.map { PaceMapGeometry.Rect(it.at.x, it.at.y, it.layout.size.width.toFloat(), it.layout.size.height.toFloat()) })

            // The dots and, with Projection on, each tail's end and its ring.
            data class Pre(val e: PaceMapEntry, val point: Offset, val figure: String?, val proj: PaceMapRules.ProjectionEnd?, val tailEnd: Offset?)
            val pre = entries.map { e ->
                val proj = if (layers.projection) PaceMapRules.projection(e.estimate, e.usedPct, e.resetMs, Projection.WEEKLY_MS) else null
                Pre(
                    e, Offset(f.x(e.gonePct), f.y(e.usedPct)),
                    if (layers.figures) PaceMapRules.figure(mode, e.usedPct, e.gonePct) else null,
                    proj, proj?.let { Offset(f.x(it.weekGonePct), f.y(it.usedPct)) },
                )
            }
            val ringBoxes = pre.mapNotNull { p -> p.tailEnd?.let { PaceMapGeometry.Rect(it.x - 3.5f * d, it.y - 3.5f * d, 7f * d, 7f * d) } }

            // "even pace" slides along the diagonal to the first stretch no dot or ring sits on.
            val even = measurer.measure(PaceText.MAP_EVEN, tiny.copy(color = pace))
            val dotBoxes = pre.map { PaceMapGeometry.dotBox(it.point.x, it.point.y, d) } + ringBoxes
            val evenAt = PaceMapGeometry.evenPace(f, d, even.size.width.toFloat(), even.size.height.toFloat(), dotBoxes, fixed.toList())
            fixed += evenAt.rect

            // Names (and figures): the core placer, top-down.
            val layouts = pre.map { p ->
                val text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = 0.92f))) { append(p.e.name) }
                    if (p.figure != null) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = mapFigureColor(mode, p.e.usedPct, p.e.gonePct, p.e.color, dark, ink))) {
                            append(" " + p.figure)
                        }
                    }
                }
                measurer.measure(text, NAME)
            }
            val g = MapLabels.Geometry.DP.scaled(d.toDouble())
            val area = MapLabels.Rect(
                f.left - g.margin.left, f.top - g.margin.top,
                (f.right - f.left) + g.margin.left + g.margin.right, (f.bottom - f.top) + g.margin.top + g.margin.bottom,
            )
            fun rect(r: PaceMapGeometry.Rect) = MapLabels.Rect(r.x.toDouble(), r.y.toDouble(), r.w.toDouble(), r.h.toDouble())
            val placed = MapLabels.place(
                dots = pre.mapIndexed { i, p ->
                    MapLabels.Dot(
                        p.e.key, p.point.x.toDouble(), p.point.y.toDouble(),
                        layouts[i].size.width.toDouble(), layouts[i].size.height.toDouble(),
                        p.tailEnd?.let { it.x.toDouble() to it.y.toDouble() },
                    )
                },
                area = area, geometry = g, fixed = fixed.map(::rect), rings = ringBoxes.map(::rect),
            )
            val items = pre.mapIndexed { i, p -> Item(p.e, p.point, layouts[i], p.figure, p.proj, p.tailEnd, placed[i]) }
            val message = if (items.isEmpty() && empty != null) {
                measurer.measure(empty.title, label.copy(fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = 0.55f))) to
                    measurer.measure(empty.detail, label.copy(color = ink.copy(alpha = 0.55f)))
            } else null
            return PaceMapScene(d, width, height, f, words, even, evenAt, items, message)
        }
    }
}

/** The pinned callout: laid out for the draw (wireframe §7). */
internal class PaceMapCallout(
    val topLeft: Offset,
    val size: Size,
    val accent: Color,
    val name: TextLayoutResult,
    val figure: TextLayoutResult,
    val line: TextLayoutResult?,
    val forecast: TextLayoutResult?,
    /** The vertical centres (px from the top) of the name row, the line and the forecast. */
    val centreName: Float,
    val centreLine: Float?,
    val centreForecast: Float?,
) {
    companion object {
        /** The callout for [item], or null when its account is not on the map. */
        fun build(
            measurer: TextMeasurer,
            scene: PaceMapScene,
            item: PaceMapScene.Item,
            mode: PaceText.Mode,
            ink: Color,
            dark: Boolean,
        ): PaceMapCallout {
            val d = scene.d
            val e = item.entry
            val f = scene.frame
            val figureText = PaceMapRules.figure(mode, e.usedPct, e.gonePct) ?: ""
            val name = measurer.measure(e.name, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink), maxLines = 1)
            val figure = measurer.measure(
                figureText,
                TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mapFigureColor(mode, e.usedPct, e.gonePct, e.color, dark, ink)),
            )
            val small = TextStyle(fontSize = 11.sp)
            val maxLine = (f.right - f.left) - 18 * d
            val pace = e.paceLine?.let { line ->
                val tint = Grammar.vsColor(line.tone ?: PaceText.VsTone.ON, dark, ink, ink)
                fun styled(p: PaceText.PaceLine) = buildAnnotatedString {
                    val span = p.tinted ?: p.text.takeIf { p.tone == PaceText.VsTone.ON && p.tinted == null }?.substringBefore(" · ")
                    if (span != null && p.text.startsWith(span)) {
                        withStyle(SpanStyle(color = tint, fontWeight = FontWeight.SemiBold)) { append(span) }
                        append(p.text.removePrefix(span))
                    } else append(p.text)
                }
                val chosen = PaceText.fit(line.forms) { t ->
                    measurer.measure(styled(line.forms.first { it.text == t }), small).size.width <= maxLine
                } ?: line.forms.firstOrNull()
                chosen?.let { measurer.measure(styled(it), small.copy(color = ink.copy(alpha = 0.7f)), maxLines = 1) }
            }
            val line = pace ?: e.plainLine?.let {
                measurer.measure(it, small.copy(color = if (e.plainRed) Palette.barColor(100.0, ink, dark) else ink.copy(alpha = 0.7f)), maxLines = 1)
            }
            val forecast = e.forecast?.let {
                measurer.measure(it, small.copy(color = if (e.runsOut) Palette.barColor(100.0, ink, dark) else ink.copy(alpha = 0.6f)), maxLines = 1)
            }
            val w = max(
                20 * d + name.size.width + 10 * d + figure.size.width + 9 * d,
                max(
                    (line?.size?.width ?: 0) + 18 * d,
                    (forecast?.size?.width ?: 0) + 18 * d,
                ),
            )
            // The lines stack from their measured heights (judge F2): the wireframe's 24 + 15 + 15 dp at scale 1.0, more
            // as the text grows, never overlapping.
            val c1 = max(13 * d, 6 * d + max(name.size.height, figure.size.height) / 2f)
            var bottom = c1 + max(name.size.height, figure.size.height) / 2f
            var prev = c1
            fun next(l: TextLayoutResult): Float {
                val c = max(prev + 15 * d, bottom + 2 * d + l.size.height / 2f)
                prev = c
                bottom = c + l.size.height / 2f
                return c
            }
            val cLine = line?.let { next(it) }
            val cForecast = forecast?.let { next(it) }
            val h = max((24 + (if (line != null) 15 else 0) + (if (forecast != null) 15 else 0)) * d, bottom + 6 * d)
            val (x, y) = PaceMapRules.calloutOrigin(item.point.x, item.point.y, w, h, f.left, f.top, f.right, f.bottom, d)
            return PaceMapCallout(Offset(x, y), Size(w, h), e.color, name, figure, line, forecast, c1, cLine, cForecast)
        }
    }
}

/**
 * The card: the title line with the chips (wrapping under the title when the line cannot hold them), the map, and
 * the foot line ("each account's own week" left, the count of accounts left off right).
 * [entries] are the plotted accounts only ([PaceMapRules.isPlotted]); a tap on a dot or a name pins its callout, the
 * same account again opens it ([onOpen]), the empty plot clears it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaceMapCard(
    entries: List<PaceMapEntry>,
    foot: PaceMapRules.Foot,
    layers: MapLayers,
    onLayers: (MapLayers) -> Unit,
    mode: PaceText.Mode,
    plotHeight: Dp,
    pinned: String?,
    onPin: (String?) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pin = PaceMapRules.keepPin(pinned, entries.map { it.key })
    // The pin is dropped, not just hidden, when its account leaves the map (judge F1): it must not reopen later.
    LaunchedEffect(pinned, entries.map { it.key }) { if (pin != pinned) onPin(pin) }
    androidx.compose.material3.Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Pace map", style = MaterialTheme.typography.titleSmall)
                // At the largest text the three chips are wider than the card: they wrap among themselves.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapChip("Figures", layers.figures) { onLayers(layers.copy(figures = it)) }
                    MapChip("Trails", layers.trails) { onLayers(layers.copy(trails = it)) }
                    MapChip("Projection", layers.projection) { onLayers(layers.copy(projection = it)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            PaceMap(
                entries, layers, mode, foot.empty, pin,
                onTap = { hit ->
                    val t = PaceMapRules.tap(pin, hit)
                    onPin(t.pinned)
                    t.open?.let(onOpen)
                },
                modifier = Modifier.fillMaxWidth().height(plotHeight),
            )
            PaceMapFoot(foot)
        }
    }
}

/**
 * CCRM-111 (Map Layers): a layer chip — 32 dp high, 12 sp, 10 dp side padding; selected = tonal fill and SemiBold,
 * no check glyph (it would not fit). A switch for TalkBack: the chip turns a layer on or off.
 */
@Composable
private fun MapChip(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .heightIn(min = 32.dp) // 32 dp at scale 1.0; grows with the text rather than clipping it
            .clip(shape)
            .background(if (on) ink.copy(alpha = 0.13f) else Color.Transparent)
            .border(1.dp, if (on) Color.Transparent else MaterialTheme.colorScheme.outline, shape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, softWrap = false, modifier = Modifier.padding(vertical = 4.dp))
    }
}

/**
 * CCRM-107 (Day Grid) and CCRM-109 (Quiet Accounts): the foot line — "each account's own week" left, the count of
 * accounts left off the map ("2 at 0% · 1 no reading") right, 11 sp muted.
 */
@Composable
fun PaceMapFoot(foot: PaceMapRules.Foot, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(foot.left, fontSize = 11.sp, color = muted, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        foot.count?.let {
            Spacer(Modifier.size(8.dp))
            Text(it, fontSize = 11.sp, color = muted, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * The map: each account's week as (share gone, used), "ahead of pace" above the diagonal and "behind pace" below it
 * (core §6.3's words), over the day grid; names (and figures) placed by [MapLabels]; spent dots carry an ×; quiet,
 * no-reading and unclocked accounts are left off ([entries] are the plotted ones; the card counts the rest). With
 * [pinned] set, that account's callout opens and the others dim.
 */
@Composable
fun PaceMap(
    entries: List<PaceMapEntry>,
    layers: MapLayers,
    mode: PaceText.Mode,
    empty: PaceMapRules.Empty?,
    pinned: String?,
    onTap: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = appDark()
    val ink = MaterialTheme.colorScheme.onSurface
    val card = MaterialTheme.colorScheme.surfaceContainer
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val d = density.density
    val currentTap by rememberUpdatedState(onTap)
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val scene = remember(entries, layers, mode, empty, dark, ink, w, h, density) {
            PaceMapScene.build(measurer, d, w, h, entries, layers, mode, empty, ink, dark)
        }
        val pinnedItem = pinned?.let { key -> scene.items.firstOrNull { it.entry.key == key } }
        val callout = remember(scene, pinnedItem, mode, ink, dark) {
            pinnedItem?.let { PaceMapCallout.build(measurer, scene, it, mode, ink, dark) }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                // Keyed on the targets, not the scene: the scene is rebuilt on every 5 s tick (the time maths moves).
                .pointerInput(scene.targets) {
                    detectTapGestures { at -> currentTap(PaceMapRules.hit(scene.targets, at.x, at.y, d)) }
                }
                .semantics {
                    contentDescription = "Pace map"
                    // The all-at-0% message is drawn in the canvas; TalkBack reads it as the map's state.
                    if (scene.items.isEmpty() && empty != null) stateDescription = "${empty.title}, ${empty.detail}"
                },
        ) {
            drawMap(scene, layers, mode, pinned, callout, ink, card, dark)
        }
        // TalkBack: one element per dot, on its 48 dp target.
        val half = PaceMapRules.TARGET_DP * d / 2
        for (item in scene.items) {
            val e = item.entry
            val spoken = PaceMapRules.spoken(
                e.name, PaceMapRules.figure(mode, e.usedPct, e.gonePct),
                e.paceLine?.text ?: e.plainLine, e.forecast,
            )
            Box(
                Modifier
                    .offset { IntOffset((item.point.x - half).roundToInt(), (item.point.y - half).roundToInt()) }
                    .size(PaceMapRules.TARGET_DP.dp)
                    .semantics {
                        contentDescription = spoken
                        role = Role.Button
                        onClick(label = if (pinned == e.key) "Open" else "Pin, again to open") { currentTap(e.key); true }
                    },
            )
        }
    }
}

/** CCRM-112 (Summary Snapshot): the map's painter for the share card — the screen's own, drawn into a bitmap. */
internal fun DrawScope.drawPaceMap(
    s: PaceMapScene,
    layers: MapLayers,
    mode: PaceText.Mode,
    pinned: String?,
    callout: PaceMapCallout?,
    ink: Color,
    card: Color,
    dark: Boolean,
) = drawMap(s, layers, mode, pinned, callout, ink, card, dark)

/** Draws [block] at [alpha] (a layer, so overlapping strokes do not double up). */
private fun DrawScope.withAlpha(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 1f) { block(); return }
    drawIntoCanvas { it.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), Paint().apply { this.alpha = alpha }) }
    block()
    drawIntoCanvas { it.restore() }
}

private fun DrawScope.drawMap(
    s: PaceMapScene,
    layers: MapLayers,
    mode: PaceText.Mode,
    pinned: String?,
    callout: PaceMapCallout?,
    ink: Color,
    card: Color,
    dark: Boolean,
) {
    val f = s.frame
    val pace = Palette.barColor(85.0, ink, dark)
    val red = Palette.barColor(100.0, ink, dark)
    fun alphaOf(e: PaceMapEntry) = if (pinned != null && pinned != e.key) MAP_DIM_ALPHA else 1f
    // ahead of pace: the triangle above the diagonal, a faint red
    drawPath(
        Path().apply { moveTo(f.x(0.0), f.y(0.0)); lineTo(f.x(100.0), f.y(100.0)); lineTo(f.x(0.0), f.y(100.0)); close() },
        red.copy(alpha = if (dark) 0.07f else 0.05f),
    )
    // CCRM-107 (Day Grid): the same sevenths both ways, under the 0 / 50 / 100% lines (which stay).
    val gridInk = ink.copy(alpha = MAP_GRID_ALPHA)
    for (k in 1 until 7) {
        val g = 100.0 * k / 7
        drawLine(gridInk, Offset(f.x(g), f.top), Offset(f.x(g), f.bottom), 1.dp.toPx())
        drawLine(gridInk, Offset(f.x(0.0), f.y(g)), Offset(f.x(100.0), f.y(g)), 1.dp.toPx())
    }
    for (v in listOf(0.0, 50.0, 100.0)) {
        drawLine(ink.copy(alpha = if (v == 0.0) 0.35f else 0.12f), Offset(f.x(0.0), f.y(v)), Offset(f.x(100.0), f.y(v)), 1.dp.toPx())
    }
    drawLine(
        pace, Offset(f.x(0.0), f.y(0.0)), Offset(f.x(100.0), f.y(100.0)), 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())),
    )
    for (word in s.words) drawText(word.layout, topLeft = word.at)
    val angle = Math.toDegrees(atan2((f.y(100.0) - f.y(0.0)).toDouble(), (f.x(100.0) - f.x(0.0)).toDouble())).toFloat()
    val ex = f.x(s.evenAt.atPct); val ey = f.y(s.evenAt.atPct) - 5.dp.toPx()
    rotate(angle, pivot = Offset(ex, ey)) {
        drawText(s.even, topLeft = Offset(ex - s.even.size.width / 2f, ey - s.even.size.height))
    }
    // CCRM-109 (Quiet Accounts): every account at 0% — the frame, grid, diagonal and words stay, a message in the behind wedge.
    s.empty?.let { (title, detail) ->
        val right = f.x(96.0)
        val at = f.y(26.0)
        drawText(title, topLeft = Offset(right - title.size.width, at - title.size.height))
        drawText(detail, topLeft = Offset(right - detail.size.width, at + 1.dp.toPx()))
    }
    // Trails first, so every dot sits on top of every line: the week (1.6 dp, accent 55%), the last 24 h heavier
    // (2.6 dp, accent 90%). An account with no history has no trail (as shipped).
    if (layers.trails) {
        for (i in s.items) {
            val e = i.entry
            if (!PaceMapRules.hasTrail(e.trail)) continue
            withAlpha(alphaOf(e)) {
                val path = Path()
                e.trail.forEachIndexed { k, (x, y) -> if (k == 0) path.moveTo(f.x(x), f.y(y)) else path.lineTo(f.x(x), f.y(y)) }
                drawPath(path, e.color.copy(alpha = 0.55f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val recent = PaceMapRules.lastDay(e.trail, e.gonePct)
                if (recent.size > 1) {
                    val heavy = Path()
                    recent.forEachIndexed { k, (x, y) -> if (k == 0) heavy.moveTo(f.x(x), f.y(y)) else heavy.lineTo(f.x(x), f.y(y)) }
                    drawPath(heavy, e.color.copy(alpha = 0.9f), style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
    // Projection: a dashed tail (1.6 dp, 5/4, accent 65%) to a hollow ring (r 3.5 dp, 1.5 dp); where it runs out the
    // ring sits on the 100% line and the tail's end and the ring are red.
    for (i in s.items) {
        val end = i.tailEnd ?: continue
        val proj = i.projection ?: continue
        val e = i.entry
        withAlpha(alphaOf(e)) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            if (proj.runsOut) {
                drawLine(
                    Brush.linearGradient(0f to e.color.copy(alpha = 0.65f), 0.45f to e.color.copy(alpha = 0.65f), 1f to red, start = i.point, end = end),
                    i.point, end, 1.6.dp.toPx(), pathEffect = dash,
                )
            } else {
                drawLine(e.color.copy(alpha = 0.65f), i.point, end, 1.6.dp.toPx(), pathEffect = dash)
            }
            drawCircle(card, 3.5.dp.toPx(), end)
            drawCircle(if (proj.runsOut) red else e.color, 3.5.dp.toPx(), end, style = Stroke(1.5.dp.toPx()))
        }
    }
    // Leaders (CCBG-79 (Leader Reads as Trail)): dotted (1 dp, 1.5/2.5, accent 55%), from the dot's edge to the end the name touches.
    for (i in s.items) {
        val l = i.placement.leader ?: continue
        withAlpha(alphaOf(i.entry)) {
            val dx = (l.endX - i.point.x).toFloat()
            val dy = (l.endY - i.point.y).toFloat()
            val n = max(kotlin.math.hypot(dx, dy), 0.001f)
            val from = Offset(i.point.x + 6.dp.toPx() * dx / n, i.point.y + 6.dp.toPx() * dy / n)
            drawLine(
                i.entry.color.copy(alpha = 0.55f), from, Offset(l.endX.toFloat(), l.endY.toFloat()), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 2.5.dp.toPx())),
            )
        }
    }
    for (i in s.items) withAlpha(alphaOf(i.entry)) { dot(i.entry, i.point, card) }
    // Names, and with Figures on the figure after the name in its colour; a name nothing was free for is not drawn.
    for (i in s.items) {
        val box = i.placement.box ?: continue
        withAlpha(alphaOf(i.entry)) { drawText(i.label, topLeft = Offset(box.x.toFloat(), box.y.toFloat())) }
    }
    callout?.let { drawCallout(it, card) }
}

/**
 * The pinned callout: an 8 dp pill, the card at 97%, a 1 dp border in the account colour at 55%; the name and figure,
 * the pace line (or the row's own words) and the forecast.
 */
private fun DrawScope.drawCallout(c: PaceMapCallout, card: Color) {
    val r = CornerRadius(8.dp.toPx())
    drawRoundRect(card.copy(alpha = 0.97f), c.topLeft, c.size, r)
    drawRoundRect(c.accent.copy(alpha = 0.55f), c.topLeft, c.size, r, style = Stroke(1.dp.toPx()))
    val x = c.topLeft.x
    val y = c.topLeft.y
    drawCircle(c.accent, 3.5.dp.toPx(), Offset(x + 12.dp.toPx(), y + c.centreName))
    drawText(c.name, topLeft = Offset(x + 20.dp.toPx(), y + c.centreName - c.name.size.height / 2f))
    drawText(c.figure, topLeft = Offset(x + c.size.width - 9.dp.toPx() - c.figure.size.width, y + c.centreName - c.figure.size.height / 2f))
    c.line?.let { drawText(it, topLeft = Offset(x + 9.dp.toPx(), y + c.centreLine!! - it.size.height / 2f)) }
    c.forecast?.let { drawText(it, topLeft = Offset(x + 9.dp.toPx(), y + c.centreForecast!! - it.size.height / 2f)) }
}

private fun DrawScope.dot(e: PaceMapEntry, c: Offset, card: Color) {
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
