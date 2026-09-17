package com.robin.claudeusage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/** Percent along the even-pace diagonal at [t] — 0% at the window start, 100% at reset. */
fun evenPacePercent(t: Long, windowStartMs: Long, windowEndMs: Long): Double {
    val span = (windowEndMs - windowStartMs).coerceAtLeast(1L)
    return ((t - windowStartMs).toDouble() / span * 100.0).coerceIn(0.0, 100.0)
}

/**
 * Is [percent] far enough past the even-pace line to earn the chart's red wash?
 *
 * CCBG-21 (Zero-Point Shading): strict, and expressed through [PACE_DEAD_ZONE] itself,
 * so it is the *same* comparison `BarGeometry.redSegment` and `RingGeometry.redSegment`
 * make — both of which document themselves as "the identical comparison the chart wash
 * makes". It wasn't. The wash ran from `-PACE_DEAD_ZONE`, two dead-zone widths early, so
 * a fresh window at 0% — the safest state there is, and one whose own caption reads "On
 * even pace" — came up with the whole above-pace triangle in red.
 */
fun abovePaceWash(percent: Double, pacePercent: Double): Boolean =
    percent > pacePercent + PACE_DEAD_ZONE

/**
 * The plot's coordinate system, lifted out of the draw pass.
 *
 * It used to live entirely inside the `Canvas` lambda, which was fine while the chart
 * was only ever drawn. A touch has to run the mapping *backwards* — "which sample is
 * under x=340?" — and a gesture handler can't reach into a DrawScope. So both sides now
 * build one of these from the same inputs: the draw pass from its own `size`, the
 * gesture handler from the size reported by `onSizeChanged`. Same function of the same
 * arguments, so the two can't drift apart.
 *
 * Being free of DrawScope also makes [nearestSample] testable without a Compose UI test,
 * which is why this is public rather than internal — the unit-test source set isn't a
 * friend of the main one here, and the mapping is worth testing more than it's worth
 * hiding.
 *
 * **Two orientations, one mapping (CCRM-77 (Transposed Chart)).** [x] is the position
 * along the **time** axis and [y] the position along the **value** axis, whichever screen
 * axis each happens to be: under [ChartOrientation.ACROSS] time runs left→right and value
 * bottom→top, under [ChartOrientation.DOWN] time runs top→bottom and value left→right.
 * [point] puts the two together into a screen coordinate, and [timeLine]/[valueLine] give
 * the full-plot rule at one instant or one percentage — so the draw pass stays a single
 * path instead of two copies of the same chart.
 *
 * **Full-width plot (CCRM-74 (Chart Polish) item 1).** There is no right-hand gutter any
 * more: [plotRight] *is* the view's width, so the chart's x mapping matches the usage bar
 * above it and the now divider lands exactly under the bar's pace mark. The 80/90/100%
 * labels moved inside the plot to pay for it. [plotTop] carries a 12dp inset instead, so
 * the 100% guide's label has somewhere to sit above its own line.
 */
class SparkGeometry(
    width: Float,
    height: Float,
    density: Density,
    private val windowStartMs: Long,
    private val windowEndMs: Long,
    val orientation: ChartOrientation = ChartOrientation.ACROSS,
    /** CCRM-75 (Chart Height): no axis row to reserve when nothing is labelled. */
    linesOnly: Boolean = false,
) {
    private val down = orientation == ChartOrientation.DOWN

    val plotLeft: Float = 0f
    val plotRight: Float = width

    /**
     * Transposed, the time labels sit inside the plot on their own halos, so nothing is
     * reserved at either end and the 100% guide is a vertical line that needs no headroom.
     */
    val plotTop: Float = if (down) 0f else with(density) { TOP_INSET.toPx() }
    val plotBottom: Float =
        height - if (down || linesOnly) 0f else with(density) { AXIS_HEIGHT.toPx() }

    /** False when the view is too small to plot into at all; callers draw nothing. */
    val usable: Boolean get() = plotRight > plotLeft && plotBottom > plotTop

    private val spanMs = (windowEndMs - windowStartMs).coerceAtLeast(1L)

    private val timeFrom = if (down) plotTop else plotLeft
    private val timeTo = if (down) plotBottom else plotRight

    /** Position along the time axis: a screen x when ACROSS, a screen y when DOWN. */
    fun x(t: Long): Float {
        val f = ((t - windowStartMs).toDouble() / spanMs).coerceIn(0.0, 1.0).toFloat()
        return timeFrom + f * (timeTo - timeFrom)
    }

    /** Position along the value axis: a screen y when ACROSS, a screen x when DOWN. */
    fun y(pct: Double): Float {
        val f = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()
        return if (down) plotLeft + f * (plotRight - plotLeft)
        else plotBottom - f * (plotBottom - plotTop)
    }

    /** [pct] observed at [t], as a screen coordinate in this orientation. */
    fun point(t: Long, pct: Double): Offset =
        if (down) Offset(y(pct), x(t)) else Offset(x(t), y(pct))

    /** The rule across the whole plot at one instant — the now divider, a crosshair. */
    fun timeLine(t: Long): Pair<Offset, Offset> = x(t).let { p ->
        if (down) Offset(plotLeft, p) to Offset(plotRight, p)
        else Offset(p, plotTop) to Offset(p, plotBottom)
    }

    /** The rule across the whole plot at one percentage — the 80/90/100% guides. */
    fun valueLine(pct: Double): Pair<Offset, Offset> = y(pct).let { p ->
        if (down) Offset(p, plotTop) to Offset(p, plotBottom)
        else Offset(plotLeft, p) to Offset(plotRight, p)
    }

    fun paceAt(t: Long): Double = evenPacePercent(t, windowStartMs, windowEndMs)

    /**
     * The sample plotted closest to [pos] along the time axis, or null if there are none.
     * [pos] is a touch's x when ACROSS and its y when DOWN — the same axis [x] returns.
     *
     * Snaps to a real fetch instead of interpolating along the line. The whole reason
     * this chart puts a dot on every sample is that polling gaps should stay visible —
     * reading a value out of the middle of a gap would report a percentage the app never
     * observed, which is the one thing the chart is built not to do.
     */
    fun nearestSample(pos: Float, samples: List<Pair<Long, Double>>): Pair<Long, Double>? =
        samples.minByOrNull { abs(x(it.first) - pos) }

    companion object {
        val AXIS_HEIGHT = 15.dp // bottom: time labels
        val TOP_INSET = 12.dp   // top: room for the 100% guide's label above its own line
    }
}

/**
 * Usage-over-time curve for one window instance. One axis spans the window's full
 * lifetime (start → reset), the other a fixed 0–100% so slopes are comparable between
 * windows and never flattered by autoscaling.
 *
 * What's drawn, and why each earns its space:
 *  - dashed guides at 80/90/100% in the alert colours, so the value axis is readable
 *    and the chart lines up with when notifications actually fire;
 *  - an "even pace" diagonal from (start, 0%) to (reset, 100%) — past it means
 *    usage is outrunning the window;
 *  - the observed fetches as a filled area + line with a dot on every real
 *    sample, so gaps in polling are visible rather than smoothed away;
 *  - a marker on the latest sample, labelled, so the present is locatable;
 *  - the burn-rate extrapolation as a dashed tail to a hollow, labelled endpoint.
 *
 * One series, so there's no legend beyond the pace swatch: the guides carry their own
 * labels and only the two points worth reading — now and the projection — get value
 * labels. Those labels yield to the guide labels, never the other way round
 * (CCRM-74 (Chart Polish) item 1).
 *
 * **Touch (CCRM-20).** Tap selects the nearest fetch and pins a callout on it; tapping
 * it again clears. Long-press then drag scrubs along the curve. The scrub is gated
 * behind the long press on purpose — a bare horizontal drag would fight the
 * `HorizontalPager` this chart sits inside for the pointer, and paging between profiles
 * matters more than a shortcut to scrubbing.
 */
@Composable
fun UsageSparkline(
    samples: List<Pair<Long, Double>>, // (epochMillis, percent), ascending
    windowStartMs: Long,
    windowEndMs: Long,
    projectedEnd: Pair<Long, Double>?,
    color: Color,
    use24h: Boolean,
    /**
     * CCRM-22 (Used or Left): flips only the callout's readout. The axis, guide,
     * "now" and projection labels stay on used — they mark absolute chart positions.
     */
    usageLeft: Boolean = false,
    /**
     * CCRM-75 (Chart Height) at [ChartSize.SMALL]: the lines and nothing else — guides
     * with no labels, the diagonal, the curve and its fill, the wash, the now divider,
     * the current dot and the projection dash and dot. No guide labels, no axis labels,
     * no value callouts, no legend; at 72dp there is no room for them, and the pace and
     * estimate lines under the chart carry the numbers instead.
     */
    linesOnly: Boolean = false,
    /** CCRM-77 (Transposed Chart): [ChartOrientation.DOWN] runs time down the y axis. */
    orientation: ChartOrientation = ChartOrientation.ACROSS,
    modifier: Modifier = Modifier,
) {
    if (samples.size < 2 || windowEndMs <= windowStartMs) return

    // The resolved app theme, not the bare system flag: the per-mode opacities
    // below must follow a CCRM-29 (Display Mode) override, or a light-mode 7%
    // wash over a forced-dark background is invisible.
    val dark = appDark()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    // The card the chart is drawn on (CCRM-60 (Dual Identity) paints this token with the
    // room's card colour), which is what a label halo has to match to disappear into.
    val card = MaterialTheme.colorScheme.surfaceContainer
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val down = orientation == ChartOrientation.DOWN

    // The alert ladder, taken from the same place the bars take it.
    val warn80 = Palette.barColor(85.0, color, dark)
    val warn90 = Palette.barColor(95.0, color, dark)
    val warn100 = Palette.barColor(100.0, color, dark)

    // Is the newest reading past the pace line by more than the dead zone? Drives the
    // wash below, and says the same thing the caption under the chart says.
    val lastSample = samples.last()
    val paceAtNow = evenPacePercent(lastSample.first, windowStartMs, windowEndMs)
    val showPaceWash = abovePaceWash(lastSample.second, paceAtNow)

    // Clock time for a window measured in hours; a date for one measured in days,
    // where the weekday repeats at both ends and reads as a duplicate label.
    val spanMs = windowEndMs - windowStartMs
    val compact = spanMs <= 12 * 60 * 60_000L
    fun stamp(ms: Long): String = Instant.ofEpochMilli(ms).let {
        if (compact) Fmt.timeOnly(it, use24h) else Fmt.dayMonth(it)
    }

    // --- selection ---
    // Held as the selected sample's *timestamp*, not its index: a poll appends a sample
    // every few minutes, and an index would quietly start pointing at a different point
    // underneath the user. An unknown timestamp resolves to null, so a selection also
    // disappears by itself when the window rolls over and the series is replaced.
    var selectedAt by remember { mutableStateOf<Long?>(null) }
    val selected = selectedAt?.let { at -> samples.firstOrNull { it.first == at } }

    // Set when the long press fires, cleared on the next touch down. Without it, a
    // long press released without moving would select a point and then have the tap
    // handler below toggle it straight back off — the finger never travelled, so that
    // release is indistinguishable from a tap by position alone. Ordering is
    // deterministic: down clears it, the long-press timeout sets it, the release reads
    // it.
    val longPressFired = remember { mutableStateOf(false) }

    var measured by remember { mutableStateOf(Size.Zero) }
    // Transposed, the axis a touch has to be resolved against is the vertical one.
    val hitTest: (Offset) -> Pair<Long, Double>? = { at ->
        SparkGeometry(
            measured.width, measured.height, density, windowStartMs, windowEndMs,
            orientation, linesOnly,
        )
            .takeIf { it.usable }
            ?.nearestSample(if (down) at.y else at.x, samples)
    }

    // A one-line spoken summary; the chart was previously invisible to a screen reader.
    val description = buildString {
        append("Usage chart. ")
        append("${lastSample.second.roundToInt()}% at ${stamp(lastSample.first)}, ")
        append(pacePhrase(lastSample.second - paceAtNow).lowercase())
        projectedEnd?.let { (t, pct) ->
            append(". Projected ${pct.roundToInt()}% by ${stamp(t)}")
        }
        if (!linesOnly) append(". Tap a point for its value.")
    }

    // CCRM-75 (Chart Height) Small draws no callout, so there is nothing for a tap to
    // show — and an inert chart hands the gesture back to the card it sits in, which
    // under Compact (CCRM-72 (Main Screen Redesign)) is the thing that folds it away.
    val touch = if (linesOnly) Modifier else Modifier
        .pointerInput(samples, orientation) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    longPressFired.value = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    hitTest(offset)?.let { selectedAt = it.first }
                },
                onDrag = { change, _ ->
                    hitTest(change.position)?.let { selectedAt = it.first }
                    change.consume()
                },
            )
        }
        // Hand-rolled rather than `detectTapGestures`, which consumes the initial
        // down unconditionally. An ancestor sees pointer events only after its
        // descendants, so that one consume was enough to stop the HorizontalPager
        // ever starting — swiping across the chart just did nothing, while swiping
        // across the bar 20dp above it paged to the other profile. Gating the
        // scrub behind a long press was supposed to protect paging; a tap handler
        // that broke it anyway defeated the point.
        .pointerInput(samples, orientation) {
            awaitEachGesture {
                val down0 = awaitFirstDown(requireUnconsumed = false)
                longPressFired.value = false
                val up = waitForUpOrCancellation()
                // Null means somebody else claimed the gesture — the pager, the
                // vertical scroll, or this chart's own scrub above.
                if (up != null &&
                    !longPressFired.value &&
                    (up.position - down0.position).getDistance() <= viewConfiguration.touchSlop
                ) {
                    val hit = hitTest(up.position)?.first
                    // Tapping the pinned point again is how you put the chart back
                    // the way you found it.
                    selectedAt = if (hit != null && hit == selectedAt) null else hit
                }
            }
        }

    Canvas(
        modifier = modifier
            .onSizeChanged { measured = it.toSize() }
            .then(touch)
            .semantics { contentDescription = description },
    ) {
        val geo = SparkGeometry(
            size.width, size.height, density, windowStartMs, windowEndMs, orientation, linesOnly,
        )
        if (!geo.usable) return@Canvas
        val plotRight = geo.plotRight
        val plotTop = geo.plotTop
        val plotBottom = geo.plotBottom
        fun pt(t: Long, pct: Double) = geo.point(t, pct)

        val stroke = 2.5.dp.toPx()
        val tiny = TextStyle(fontSize = 9.5.sp, color = muted)
        val haloPad = 2.dp.toPx()

        /** A label on a card-coloured backing rect, so whatever crosses behind it can't eat it. */
        fun drawHaloed(l: TextLayoutResult, topLeft: Offset) {
            drawRect(
                color = card,
                topLeft = Offset(topLeft.x - haloPad, topLeft.y),
                size = Size(l.size.width + haloPad * 2, l.size.height.toFloat()),
            )
            drawText(l, topLeft = topLeft)
        }

        // --- the guide labels, measured and placed before anything else is drawn ---
        // CCRM-74 (Chart Polish) item 1: they live inside the plot now, so everything else has to know
        // where they are in order to keep off them. Drawn late (below), on a halo, so the
        // curve passing behind can't make them unreadable.
        val guides = listOf(100.0 to warn100, 90.0 to warn90, 80.0 to warn80)
        val guideLabels = if (linesOnly) emptyList() else guides.mapIndexed { i, (pct, c) ->
            val l = measurer.measure("${pct.toInt()}%", tiny.copy(color = c.copy(alpha = 0.85f)))
            val at = if (down) {
                // Transposed the guides are vertical and only ~10% of the width apart —
                // too close for one row of labels — so each label sits to the LEFT of its
                // own line, right-aligned, and the three stack in their own rows, 100%
                // top. Centring instead would clip 100%, whose line *is* the right edge.
                Offset(
                    (geo.y(pct) - 3.dp.toPx() - l.size.width).coerceAtLeast(haloPad),
                    plotTop + 28.dp.toPx() + i * (l.size.height + 2.dp.toPx()),
                )
            } else {
                Offset(
                    (plotRight - 3.dp.toPx() - l.size.width).coerceAtLeast(haloPad),
                    // Just above its own dotted line, or in the top inset for 100%,
                    // whose line has only the inset above it.
                    (geo.y(pct) - 2.dp.toPx() - l.size.height).coerceAtLeast(0f),
                )
            }
            l to Rect(at, l.size.toSize())
        }
        // The halo, not the glyphs, is what another label has to clear.
        val guideRects = guideLabels.map { (_, r) ->
            Rect(r.left - haloPad, r.top, r.right + haloPad, r.bottom)
        }
        fun clearOfGuides(r: Rect) = guideRects.none { it.overlaps(r) }

        // A value label normally sits above its marker, but near the top of the plot
        // that crowds the 80/90/100% guides — so up there it flips underneath instead.
        val topThird = plotTop + (plotBottom - plotTop) / 3f
        fun above(p: Offset, labelH: Int) = (p.y - labelH - 5.dp.toPx()).coerceAtLeast(0f)
        fun below(p: Offset, labelH: Int) =
            (p.y + 5.dp.toPx()).coerceAtMost((plotBottom - labelH).coerceAtLeast(0f))
        fun labelY(p: Offset, labelH: Int): Float =
            if (p.y < topThird) below(p, labelH) else above(p, labelH)

        /**
         * Where a value label may go, best first: the preferred side, then the other
         * one. The caller takes the first candidate that clears the guide labels —
         * CCRM-74 (Chart Polish) item 1, the callouts yield, never the labels.
         */
        fun valueLabelSpots(p: Offset, w: Int, h: Int): List<Rect> {
            val size = Size(w.toFloat(), h.toFloat())
            val leftOf = (p.x - w - 4.dp.toPx()).coerceAtLeast(0f)
            val rightOf = (p.x + 7.dp.toPx()).coerceAtMost((plotRight - w).coerceAtLeast(0f))
            // Transposed there is room beside the marker, since the curve runs down the
            // middle; upright the marker is on a rising line, so the room is above or
            // below it and the fallback is the same side, the other way up.
            return listOf(
                Rect(Offset(if (down) rightOf else leftOf, labelY(p, h)), size),
                Rect(Offset(leftOf, if (p.y < topThird) above(p, h) else below(p, h)), size),
            )
        }

        // --- the selected point's callout, laid out first so the labels it would cover
        // --- can stand down before they're drawn
        val callout = if (linesOnly) null else selected?.let { (t, pct) ->
            val head = measurer.measure(
                if (compact) Fmt.timeOnly(Instant.ofEpochMilli(t), use24h)
                else Fmt.dayTime(Instant.ofEpochMilli(t), use24h),
                tiny,
            )
            val delta = pct - geo.paceAt(t)
            val body = measurer.measure(
                // Left floors the remainder (never overstates); Used keeps the
                // callout's rounding, since it reads a recorded sample.
                (if (usageLeft) "${Fmt.usageInt(pct, true)}% left"
                else "${pct.roundToInt()}%") + " · ${paceLabel(delta)}",
                TextStyle(
                    fontSize = 11.sp,
                    color = if (delta > PACE_DEAD_ZONE) warn90 else onSurface,
                    fontWeight = FontWeight.Bold,
                ),
            )
            val padH = 6.dp.toPx()
            val padV = 4.dp.toPx()
            val w = maxOf(head.size.width, body.size.width) + padH * 2
            val h = head.size.height + body.size.height + padV * 2
            val p = pt(t, pct)
            val gap = 8.dp.toPx()
            fun at(left: Float, top: Float) = Rect(
                Offset(left, top.coerceIn(plotTop, (plotBottom - h).coerceAtLeast(plotTop))),
                Size(w, h),
            )
            // Prefer beside the crosshair, level with the point; then the other side;
            // then the same two pushed down. The pill moves for a guide label, the
            // guide label never moves for the pill.
            val fits = p.x + gap + w <= plotRight
            val spots = buildList {
                if (fits) add(at(p.x + gap, p.y - h / 2f))
                add(at((p.x - gap - w).coerceAtLeast(0f), p.y - h / 2f))
                if (fits) add(at(p.x + gap, p.y + gap))
                add(at((p.x - gap - w).coerceAtLeast(0f), p.y + gap))
            }
            val pill = spots.firstOrNull { clearOfGuides(it) } ?: spots.first()
            Callout(pill, head, body, padH, padV, p, geo.timeLine(t))
        }

        // --- the pace region ---
        // Written through `point` rather than raw corners, so the same two triangles are
        // "past the pace line" and "short of it" in either orientation: ACROSS that's
        // above and below the diagonal, DOWN it's right and left of it.
        fun triangle(corner: Offset) = Path().apply {
            val a = pt(windowStartMs, 0.0)
            val b = pt(windowEndMs, 100.0)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(corner.x, corner.y); close()
        }
        val abovePace = triangle(pt(windowStartMs, 100.0))
        val belowPace = triangle(pt(windowEndMs, 0.0))
        // The wash only appears once usage has actually pulled clear of the line, so
        // its arrival is the signal. Drawn permanently it did the opposite: a window at
        // 0% — the safest state there is — came up two-thirds shaded red, and a
        // region that's always there can't warn about anything. It also goes first,
        // under the guides, so it can't dim them.
        if (showPaceWash) {
            drawPath(abovePace, warn100.copy(alpha = if (dark) 0.10f else 0.07f))
        }

        // --- threshold guides (the lines; their labels come last) ---
        for ((pct, c) in guides) {
            val (from, to) = geo.valueLine(pct)
            drawLine(
                color = c.copy(alpha = 0.32f),
                start = from,
                end = to,
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f)),
            )
        }

        // --- the pace diagonal ---
        val paceFrom = pt(windowStartMs, 0.0)
        val paceTo = pt(windowEndMs, 100.0)
        drawLine(
            color = warn80.copy(alpha = 0.9f),
            start = paceFrom,
            end = paceTo,
            strokeWidth = 2.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
        )

        // --- observed: area (split at the diagonal), line, points ---
        // The fill runs back to 0% — downwards to the floor when time runs across,
        // leftwards to the left edge when it runs down.
        val area = Path().apply {
            val first = pt(samples.first().first, 0.0)
            moveTo(first.x, first.y)
            for ((t, pct) in samples) pt(t, pct).let { lineTo(it.x, it.y) }
            pt(samples.last().first, 0.0).let { lineTo(it.x, it.y) }
            close()
        }
        // The overshoot is shaded in the warning colour so you can see exactly when
        // the curve crossed and by how much — a single colour for the whole curve
        // would flatten that into one verdict, and would also disagree with the
        // usage bar above the chart, which is coloured by absolute percentage.
        clipPath(belowPace) { drawPath(area, color.copy(alpha = if (dark) 0.20f else 0.18f)) }
        clipPath(abovePace) { drawPath(area, warn90.copy(alpha = if (dark) 0.34f else 0.30f)) }

        val line = Path()
        samples.forEachIndexed { i, (t, pct) ->
            val p = pt(t, pct)
            if (i == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
        }
        drawPath(line, color, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Every fetch except the newest, which gets its own bigger marker below.
        for ((t, pct) in samples.dropLast(1)) {
            drawCircle(color, radius = 2.6.dp.toPx(), center = pt(t, pct))
        }

        // --- now marker ---
        val (nowT, nowPct) = samples.last()
        val nowPoint = pt(nowT, nowPct)
        val (nowFrom, nowTo) = geo.timeLine(nowT)
        drawLine(
            color = muted.copy(alpha = 0.28f),
            start = nowFrom,
            end = nowTo,
            // Transposed this line crosses the filled curve rather than empty
            // background, and 1dp of 28% ink disappears into the fill.
            strokeWidth = if (down) 1.5.dp.toPx() else 1.dp.toPx(),
        )
        drawCircle(color, radius = 4.5.dp.toPx(), center = nowPoint)
        var nowLabelRect: Rect? = null
        if (!linesOnly) {
            val nowLabel = measurer.measure(
                "${nowPct.toInt()}%",
                TextStyle(fontSize = 11.sp, color = onSurface, fontWeight = FontWeight.Bold),
            )
            val spots = valueLabelSpots(nowPoint, nowLabel.size.width, nowLabel.size.height)
            val rect = spots.firstOrNull { clearOfGuides(it) } ?: spots.first()
            // The callout is the thing the user just asked for, so it outranks the standing
            // label — which says the same number anyway when the newest point is the one
            // selected.
            if (callout == null || !rect.overlaps(callout.pill)) {
                nowLabelRect = rect
                if (down) drawHaloed(nowLabel, rect.topLeft)
                else drawText(nowLabel, topLeft = rect.topLeft)
            }
        }

        // --- projection tail ---
        projectedEnd?.let { (t, pct) ->
            if (t <= nowT) return@let
            val end = pt(t, pct)
            drawLine(
                color = color.copy(alpha = 0.65f),
                start = nowPoint,
                end = end,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 8f)),
            )
            drawCircle(
                color = color.copy(alpha = 0.8f),
                radius = 4.dp.toPx(),
                center = end,
                style = Stroke(width = 1.8.dp.toPx()),
            )
            if (linesOnly) return@let
            val l = measurer.measure(
                "~${pct.toInt()}%",
                TextStyle(fontSize = 11.sp, color = muted, fontWeight = FontWeight.Bold),
            )
            // Late in a window the projection endpoint sits close to the now marker
            // and the two value labels collide. The caption below spells this number
            // out ("At this pace: ~75% when the window resets"), so the marker keeps
            // its label and this one yields — as it also does to the callout and, since
            // CCRM-74 (Chart Polish) item 1, to the guide labels.
            val spots = valueLabelSpots(end, l.size.width, l.size.height)
            val standing = nowLabelRect
            val rect = spots.firstOrNull { r ->
                clearOfGuides(r) &&
                    (standing == null || !r.overlaps(standing)) &&
                    (callout == null || !r.overlaps(callout.pill))
            }
            if (rect != null) {
                if (down) drawHaloed(l, rect.topLeft) else drawText(l, topLeft = rect.topLeft)
            }
        }

        if (!linesOnly) {
            // --- pace legend, and the time labels ---
            val legendText =
                measurer.measure("even pace", tiny.copy(color = warn80.copy(alpha = 0.95f)))
            val swatchW = 14.dp.toPx()
            val gapAfterSwatch = 4.dp.toPx()
            // Only the transposed layout needs the backing rect: there the legend sits
            // inside a plot the curve starts in, where upright it has the empty top-left
            // corner to itself and a halo would only rub out the 100% guide behind it.
            fun drawLegend(at: Offset, halo: Boolean) {
                if (halo) drawRect(
                    color = card,
                    topLeft = Offset(at.x, at.y),
                    size = Size(
                        swatchW + gapAfterSwatch + legendText.size.width + haloPad,
                        legendText.size.height.toFloat(),
                    ),
                )
                drawLine(
                    color = warn80.copy(alpha = 0.9f),
                    start = Offset(at.x, at.y + legendText.size.height / 2f),
                    end = Offset(at.x + swatchW, at.y + legendText.size.height / 2f),
                    strokeWidth = 2.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
                )
                drawText(legendText, topLeft = Offset(at.x + swatchW + gapAfterSwatch, at.y))
            }

            val startLabel = measurer.measure(stamp(windowStartMs), tiny)
            val endLabel = measurer.measure(stamp(windowEndMs), tiny)

            if (down) {
                // Time runs down, so its two ends stack at the left edge: the window
                // start above the pace legend at the top, the reset at the foot. Both
                // sit inside the plot on a halo — there is no axis strip to put them in.
                drawHaloed(startLabel, Offset(haloPad + 1.dp.toPx(), plotTop))
                drawLegend(
                    Offset(haloPad + 1.dp.toPx(), plotTop + startLabel.size.height + 2.dp.toPx()),
                    halo = true,
                )
                drawHaloed(
                    endLabel,
                    Offset(haloPad + 1.dp.toPx(), plotBottom - endLabel.size.height),
                )
            } else {
                // Top-left is the one corner neither the curve nor the diagonal occupies,
                // since both start bottom-left. The exception is very heavy usage very
                // early, which is the only way the curve reaches up there — then it goes
                // bottom-right, which that same scenario leaves empty.
                val earlyAndHigh = samples.any { (t, pct) ->
                    geo.x(t) < plotRight * 0.45f && pct > 70.0
                }
                val legendW = swatchW + gapAfterSwatch + legendText.size.width
                drawLegend(
                    Offset(
                        if (earlyAndHigh) plotRight - legendW else 0f,
                        if (earlyAndHigh) plotBottom - legendText.size.height - 2.dp.toPx()
                        else plotTop,
                    ),
                    halo = false,
                )

                // --- x axis ---
                drawLine(
                    color = muted.copy(alpha = 0.18f),
                    start = Offset(0f, plotBottom),
                    end = Offset(plotRight, plotBottom),
                    strokeWidth = 1.dp.toPx(),
                )
                val axisY = plotBottom + 2.dp.toPx()
                drawText(startLabel, topLeft = Offset(0f, axisY))
                drawText(endLabel, topLeft = Offset(plotRight - endLabel.size.width, axisY))

                // "now" only when it won't collide with either end label.
                val nowText = measurer.measure("now", tiny)
                val nowLeft = nowPoint.x - nowText.size.width / 2f
                val clearOfStart = nowLeft > startLabel.size.width + 6.dp.toPx()
                val clearOfEnd =
                    nowLeft + nowText.size.width < plotRight - endLabel.size.width - 6.dp.toPx()
                if (clearOfStart && clearOfEnd) {
                    drawText(nowText, topLeft = Offset(nowLeft, axisY))
                }
            }

            // --- the guide labels, over everything that crosses them ---
            for ((l, rect) in guideLabels) drawHaloed(l, rect.topLeft)
        }

        // --- the selection, last, so nothing can be drawn over what was asked for ---
        callout?.let { c ->
            // Heavier and in the series colour, so it doesn't read as a second "now".
            drawLine(
                color = color.copy(alpha = 0.55f),
                start = c.cross.first,
                end = c.cross.second,
                strokeWidth = 1.5.dp.toPx(),
            )
            // A surface-coloured halo lifts the dot off the curve and the area fill.
            drawCircle(surface, radius = 6.5.dp.toPx(), center = c.point)
            drawCircle(color, radius = 4.5.dp.toPx(), center = c.point)

            drawRoundRect(
                color = surface.copy(alpha = 0.95f),
                topLeft = c.pill.topLeft,
                size = c.pill.size,
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawRoundRect(
                color = color.copy(alpha = 0.55f),
                topLeft = c.pill.topLeft,
                size = c.pill.size,
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawText(
                c.head,
                topLeft = Offset(c.pill.left + c.padH, c.pill.top + c.padV),
            )
            drawText(
                c.body,
                topLeft = Offset(
                    c.pill.left + c.padH,
                    c.pill.top + c.padV + c.head.size.height,
                ),
            )
        }
    }
}

/** Everything the selected-point callout needs, measured before anything is drawn. */
private class Callout(
    val pill: Rect,
    val head: TextLayoutResult,
    val body: TextLayoutResult,
    val padH: Float,
    val padV: Float,
    val point: Offset,
    val cross: Pair<Offset, Offset>,
)

/**
 * The pace delta as a chart label — short, because it shares a line with the value.
 *
 * CCRM-74 (Chart Polish) item 4: the number carries a "%", the way every other pace
 * phrase in the app now does; "points" is gone. Public only so the copy can be pinned by
 * a unit test, the same reason [SparkGeometry] is.
 */
fun paceLabel(delta: Double): String = when {
    delta > PACE_DEAD_ZONE -> "+${delta.roundToInt()}% vs pace"
    delta < -PACE_DEAD_ZONE -> "${delta.roundToInt()}% vs pace"
    else -> "on pace"
}

/** The same verdict in words, for the screen-reader summary. */
fun pacePhrase(delta: Double): String = when {
    delta > PACE_DEAD_ZONE -> "${delta.roundToInt()}% above even pace"
    delta < -PACE_DEAD_ZONE -> "${(-delta).roundToInt()}% below even pace"
    else -> "On even pace"
}
