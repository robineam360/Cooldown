package com.robin.claudeusage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robin.claudeusage.data.CardId
import com.robin.claudeusage.data.CardLayout
import com.robin.claudeusage.data.Pace
import androidx.compose.ui.draw.alpha
import com.robin.claudeusage.data.Headline
import com.robin.claudeusage.data.HeadlineRule
import com.robin.claudeusage.data.HeadlineSurface
import com.robin.claudeusage.data.WindowKind
import com.robin.claudeusage.ui.AppPace
import com.robin.claudeusage.ui.Grammar
import com.robin.claudeusage.ui.GrammarBar
import com.robin.claudeusage.ui.GrammarRing
import com.robin.claudeusage.ui.GrammarRingBox
import com.robin.claudeusage.ui.LocalUsageEndMark
import com.robin.claudeusage.ui.UsageEndMark
import com.robin.claudeusage.ui.PaceText
import com.robin.claudeusage.ui.RingRenderer
import com.robin.claudeusage.ui.grammarDigits
import com.robin.claudeusage.ui.grammarFigure
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import com.robin.claudeusage.data.HistoryPoint
import com.robin.claudeusage.data.ModelCap
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.SpendCredits
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.ui.BarGeometry
import com.robin.claudeusage.ui.ChartOrientation
import com.robin.claudeusage.ui.ChartSize
import com.robin.claudeusage.ui.EstimateLine
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.LocalWindowHeight
import com.robin.claudeusage.ui.LocalWindowWidth
import com.robin.claudeusage.ui.Palette
import com.robin.claudeusage.ui.ProvenanceNote
import com.robin.claudeusage.ui.UsageSparkline
import com.robin.claudeusage.ui.appDark
import com.robin.claudeusage.ui.chartHeight
import com.robin.claudeusage.ui.elapsedPercent
import com.robin.claudeusage.ui.hasTwoColumns
import com.robin.claudeusage.ui.statusLine
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * CCRM-72 (Main Screen Redesign): the main screen's cards, split out of `ProfileScreen`
 * so each one owns its own two densities — `design/2026-09-17-main-screen-redesign.html`
 * rev E, approved 2026-09-17.
 *
 * CCRM-103 (Hybrid Notification), the shape grammar (`design/2026-11-02-shape-grammar.html`
 * rev D, frozen 2026-11-02, §1 and §2): the "7-day window" and "5-hour window" cards are one
 * [AccountTile] — the 5h ring on the left, the 7-day bars stacked on the right, the band line
 * and the 5h line under it. *Compact* shows the tile alone and opens on a tap; *Comfortable*
 * (or an opened tile) adds the chart under it, with the chips 7d · <model> · 5h (CCRM-106 (7d Chip)).
 * [CreditsCard] is not a window and keeps its bar; it is identical in both densities.
 *
 * Also here: CCRM-73 (Model Cap Chart)'s series chips, CCRM-75 (Chart Height) and CCRM-77
 * (Transposed Chart) threading through to the chart, and CCRM-25 (Card Layout)'s "More"
 * disclosure.
 */

private const val SESSION_MS: Long = Projection.SESSION_MS
private const val WEEKLY_MS: Long = Projection.WEEKLY_MS

/** The gutter between the two chart columns of the wide 7-day card (wireframe §8). */
private val ColumnGutter: Dp = 16.dp

/** The tap readout shows this long, then the line returns (shape grammar rev D §2b, C2). */
internal const val TAP_READOUT_MS: Long = 3_000L

// --- pure helpers (tested in app/src/test) ---

/**
 * CCRM-73 (Model Cap Chart): which series the 7-day card charts, resolved against the
 * caps the account actually reports right now.
 *
 * Null means *All*, the default. A [stored] name that is no longer among [capNames] —
 * a plan change, or the older Sonnet/Opus payload giving way to Fable — falls back to
 * All rather than charting a series the account no longer has.
 */
fun weeklyChartSelection(stored: String?, capNames: List<String>): String? =
    stored?.takeIf { it in capNames }

/**
 * CCRM-25 (Card Layout): the cards this account has data for right now, in one pure place
 * so the top bar's ⋮ and the layout sheet can't disagree with what `ProfileScreen` draws.
 *
 * CCRM-103 (Hybrid Notification): one account tile carries both windows, so there is one
 * window card, not two. It takes the 7-day card's slot ([CardId.WEEKLY]) — and with it that
 * card's place, visibility and fold — and only an account reporting no 7-day window at all
 * keeps it under [CardId.SESSION].
 */
fun dataCards(data: UsageData?, creditsVisible: Boolean): Set<CardId> {
    data ?: return emptySet()
    val ids = LinkedHashSet<CardId>()
    if (data.weekly != null || data.modelCaps.isNotEmpty()) ids += CardId.WEEKLY
    else if (data.session != null) ids += CardId.SESSION
    if (data.credits?.isReportable == true && creditsVisible) ids += CardId.CREDITS
    return ids
}

/**
 * CCRM-96 (Weekly First): the layout as drawn. A layout reordered in the sheet keeps its
 * order; an untouched one (the stored order is still [CardLayout.DEFAULT]'s) takes the
 * Headline's default — 5h first under Headline 5-hour, Weekly first otherwise
 * (spec/mobile BEHAVIOR-MOBILE §5). Credits keep their place. Since CCRM-103 (Hybrid
 * Notification) only one of the two window ids is ever present ([dataCards]), so this
 * decides nothing inside the tile: the ring is always on its left.
 */
fun cardOrderLayout(layout: CardLayout, headline: Headline): CardLayout {
    if (layout.order != CardLayout.DEFAULT.order) return layout
    val lead = HeadlineRule.lead(headline, HeadlineSurface.CARD_ORDER, hasSession = true, sessionBinding = false)
    if (lead == WindowKind.SESSION) return layout
    val windows = listOf(CardId.WEEKLY, CardId.SESSION)
    var i = 0
    return layout.copy(order = layout.order.map { if (it in windows) windows[i++] else it })
}

/** The cards a page draws: [main] above More, [folded] under it, each in order. */
data class DrawnCards(val main: List<CardId>, val folded: List<CardId>)

/**
 * CCRM-103 (Hybrid Notification): what the page draws from a stored [layout] (already through
 * [cardOrderLayout]) and the cards the account reports ([present], from [dataCards]).
 *
 * The tile carries both windows and the layout sheet shows it as one row, "Usage" (Robin,
 * 2026-11-02), under the id the account reports ([dataCards]). It follows that row — its place,
 * its fold — and the sheet hides or shows both window ids together. A layout saved before the
 * tile (v1.9/v2.0) that hid only that card while keeping the other window's still shows it,
 * where the other stood: the tile hides only when **both** are hidden. And the sheet's "never
 * blank" rule holds whatever was stored: when nothing would be drawn while the account reports
 * cards, the tile (else the first reported card) shows.
 */
/**
 * The stored [layout] as the tile's one "Usage" row sees it (6ee08e6 judge F1): when a layout
 * saved before the tile hid the id the account reports ([present]) but kept the other window
 * card, the reported id takes the other's shown state and fold, and the other takes the hidden
 * flag. The order is left alone (a Headline-ordered layout stays untouched). The sheet and
 * [drawnCards] both read through this, so the row's switch, fold and place are the screen's.
 */
fun tileLayout(layout: CardLayout, present: Set<CardId>): CardLayout {
    val tile = present.firstOrNull { it == CardId.SESSION || it == CardId.WEEKLY } ?: return layout
    val other = if (tile == CardId.WEEKLY) CardId.SESSION else CardId.WEEKLY
    if (tile !in layout.hidden || other in layout.hidden) return layout
    val more = layout.more - tile - other + (if (other in layout.more) setOf(tile) else emptySet())
    return layout.copy(hidden = layout.hidden - tile + other, more = more)
}

fun drawnCards(stored: CardLayout, present: Set<CardId>): DrawnCards {
    val layout = tileLayout(stored, present)
    val windows = setOf(CardId.SESSION, CardId.WEEKLY)
    val tile = present.firstOrNull { it in windows }
    val slot = tile?.takeIf { it !in layout.hidden }
        ?: layout.order.firstOrNull { it in windows && it !in layout.hidden }
    val main = mutableListOf<CardId>()
    val folded = mutableListOf<CardId>()
    for (id in layout.order) {
        val drawn = when {
            id in windows -> tile?.takeIf { id == slot }
            id in present && id !in layout.hidden -> id
            else -> null
        } ?: continue
        if (id in layout.more) folded += drawn else main += drawn
    }
    if (main.isEmpty() && folded.isEmpty() && present.isNotEmpty()) main += tile ?: present.first()
    return DrawnCards(main, folded)
}

/**
 * The open tile's first chip (rev D §2, Fable A5; as iOS): 5h when the drawn order leads with
 * the 5-hour card (Headline 5-hour via [cardOrderLayout]) and the account has a 5h; All
 * otherwise. The sheet's one "Usage" row cannot reorder the two windows against each other, so
 * the Headline (or a layout reordered before the tile) decides. The choice stays session-only.
 */
fun tileStartsOnFive(layout: CardLayout, hasSession: Boolean): Boolean =
    hasSession && layout.order.firstOrNull { it != CardId.CREDITS } == CardId.SESSION

/** The tile's ring: 56 dp under 360 dp of width, 84 dp from 700 dp, else 72 dp (rev D §2, A1/A4/A5). */
fun tileRingDp(windowWidthDp: Float): Int = when {
    windowWidthDp >= 700f -> 84
    windowWidthDp < 360f -> 56
    else -> 72
}

/** The gap between the ring and the bars: 12 dp at the narrowest width (A5), else 14 dp. */
fun tileRingGapDp(windowWidthDp: Float): Int = if (windowWidthDp < 360f) 12 else 14

/**
 * The ring's state (rev D §1, §7): no 5h on the plan → a grey ring with a cross; a spent
 * week (CCRM-102 (Spent Week Gate)) → greyed; otherwise a reading (or none yet: "—").
 */
fun tileRingState(session: UsageWindow?, weekly: UsageWindow?): GrammarRing.State = when {
    session == null -> GrammarRing.State.NOT_APPLICABLE
    Pace.isSpent(weekly?.percent) -> GrammarRing.State.GREY
    else -> GrammarRing.State.NORMAL
}

/**
 * What a screen reader hears for a ring (rev D open question "Screen readers"): the
 * visible ring never says "5h", so its label does — "5-hour window, 34 percent".
 */
fun ringDescription(
    state: GrammarRing.State,
    figure: String?,
    mode: PaceText.Mode,
    tone: PaceText.VsTone,
): String {
    if (state == GrammarRing.State.NOT_APPLICABLE) return "No 5-hour window"
    val n = figure?.takeIf { it.endsWith("%") }?.removeSuffix("%")
        ?: return "5-hour window, no reading yet"
    val tail = when {
        mode == PaceText.Mode.VS_PACE && tone == PaceText.VsTone.UNDER -> " percent under pace"
        mode == PaceText.Mode.VS_PACE && tone == PaceText.VsTone.OVER -> " percent over pace"
        mode == PaceText.Mode.VS_PACE && tone == PaceText.VsTone.ON -> " percent, on pace"
        mode == PaceText.Mode.LEFT -> " percent left"
        else -> " percent"
    }
    return "5-hour window, $n$tail"
}

/** The open ring's dotted projection (rev D §2, A3): to the projected end, or to 100% if it gets there first. */
fun tileProjectionPct(estimate: Projection.Estimate?): Double? =
    estimate?.let { if (it.hitsLimitAtMs != null) 100.0 else it.pctAtReset }

// --- shared pieces ---

@Composable
internal fun barFill(percent: Double?): Color =
    Palette.barColor(percent, MaterialTheme.colorScheme.primary, appDark())

/**
 * Claude-style bar: light tint track, solid fill, fully rounded — plus the pace
 * marks (CCRM-43 (Bar Pace Marks)): the neutral even-pace tick and, past the dead
 * zone, the red over-pace segment.
 *
 * [elapsedPercent] null → no marks at all: either there is no reset clock to derive
 * even pace from, or this is a credits row, which has no clock by definition.
 * [showOverPace] is the Settings toggle and gates only the red; the tick always
 * draws.
 *
 * The outer Box is deliberately *not* clipped and is taller than the bar: the tick
 * overhangs the bar by 0.3h top and bottom, so a clip here would shear it off. The
 * track and fill carry their own rounded clips instead.
 */
@Composable
internal fun UsageBarLine(
    percent: Double?,
    fillColor: Color,
    height: Dp = 12.dp,
    elapsedPercent: Double? = null,
    showOverPace: Boolean = true,
    /**
     * CCRM-97 (Pace Number), V1 (`design/2026-11-02-pace-line-label.html`): under pace, the
     * room from the fill to the mark drawn as a dashed outline. In-app bars only.
     */
    dashedRoom: Boolean = false,
    /**
     * CCRM-103 (Hybrid Notification), rev D §1e: how the fill ends at the usage. Round is
     * the pre-v2.0 capsule; Line and Pace colour end the fill square with a short line.
     */
    endMark: UsageEndMark = LocalUsageEndMark.current,
    /** The surface the bar sits on: the end mark's 1 dp gap is drawn in it. */
    surface: Color = CardDefaults.cardColors().containerColor,
) {
    val fraction = ((percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val dark = appDark()
    val endColor = if (percent != null && percent > 0.0) {
        Grammar.endMarkColor(endMark, percent, elapsedPercent, fillColor, dark)
    } else null
    val fillShape = if (endMark == UsageEndMark.ROUND) RoundedCornerShape(height / 2)
    else RoundedCornerShape(topStart = height / 2, bottomStart = height / 2)
    val overhang = height * 0.3f
    val segment = BarGeometry.redSegment(percent, elapsedPercent, showOverPace)
    val tick = BarGeometry.showTick(percent, elapsedPercent)
    val tickColor = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (appDark()) 0.60f else 0.48f,
    )
    val redColor = Palette.barColor(100.0, fillColor, appDark())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height + overhang * 2),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(fillColor.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(fillShape)
                    .background(fillColor),
            ) {
                // The red rides *inside* the fill's clip, which is what makes the
                // boundary between the two colours a straight vertical edge and lets
                // the red cover the fill's rounded tip (wireframe rev B). Offsetting
                // by the segment's start keeps it beginning exactly on the pace line.
                if (segment != null) {
                    val (start, end) = segment
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (end > 0f) 1f - start / end else 0f)
                            .fillMaxHeight()
                            .align(Alignment.CenterEnd)
                            .background(redColor),
                    )
                }
            }
        }
        // `tick` already means elapsedPercent is non-null; saying so again here is what
        // lets the Canvas read it without a `!!`.
        if (endColor != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas {
                    GrammarBar.paintEndMark(
                        it.nativeCanvas, size.width * fraction, overhang.toPx(),
                        overhang.toPx() + height.toPx(), density, endColor, surface,
                    )
                }
            }
        }
        if (tick && elapsedPercent != null) {
            val roomColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            val room = dashedRoom && percent != null && percent < elapsedPercent
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (room) {
                    val stroke = 1.3.dp.toPx()
                    val x0 = size.width * fraction
                    val x1 = size.width * BarGeometry.tickFraction(elapsedPercent)
                    val top = overhang.toPx() + 1.dp.toPx() + stroke / 2f
                    val bottom = overhang.toPx() + height.toPx() - 1.dp.toPx() - stroke / 2f
                    if (x1 - x0 > stroke) {
                        drawPath(
                            Path().apply {
                                moveTo(x0, top); lineTo(x1, top); lineTo(x1, bottom); lineTo(x0, bottom)
                            },
                            color = roomColor,
                            style = Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                            ),
                        )
                    }
                }
                val w = BarGeometry.tickWidth(height.toPx())
                val cx = size.width * BarGeometry.tickFraction(elapsedPercent)
                drawRoundRect(
                    color = tickColor,
                    topLeft = Offset(cx - w / 2f, 0f),
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(w / 2f),
                )
            }
        }
    }
}

@Composable
internal fun ResetRow(window: UsageWindow?, use24h: Boolean, resetClock: Boolean) {
    // A window with no reset time hasn't started yet (0% and idle).
    if (window?.resetsAt == null) {
        Text(
            "Starts when a message is sent",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // CCRM-23 (Reset Display), Option A: the chosen form leads, the other keeps
    // the second slot — the token decides order here, never presence.
    val countdown = "Resets ${Fmt.relIn(window.resetsAt)}"
    val clock = "Resets at ${Fmt.dayTime(window.resetsAt, use24h)}"
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            if (resetClock) clock else countdown,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (resetClock) countdown else clock,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A figure's colour in the grammar (rev D §2, §7b): the usage's severity colour (the
 * account colour, its step above 80); in Vs pace green under the mark, red over it, the
 * ink on it — the severity colour again when it fell back to the usage figure.
 */
@Composable
internal fun grammarFigureColor(
    percent: Double?,
    elapsed: Double?,
    mode: PaceText.Mode,
    accent: Color = MaterialTheme.colorScheme.primary,
): Color {
    if (percent == null) return MaterialTheme.colorScheme.onSurfaceVariant
    val dark = appDark()
    val usage = Palette.barColor(percent, accent, dark)
    if (mode != PaceText.Mode.VS_PACE) return usage
    return Grammar.vsColor(PaceText.vsPaceTone(percent, elapsed), dark, MaterialTheme.colorScheme.onSurface, usage)
}

/**
 * The tap readout in place of a line (rev D §2b form (b), `.swap`): onSurface SemiBold,
 * underlined while it shows. CCBG-57 (Readout Underline Clip): the font's own underline, placed
 * below the baseline by the text engine, so it never crosses the glyphs in a fixed-height line
 * (a hand-drawn line at the box's bottom did, once the 17 dp caption squeezed the text).
 */
@Composable
internal fun SwapText(text: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        textDecoration = TextDecoration.Underline,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * CCRM-97 (Pace Number), V1 (`design/2026-11-02-pace-line-label.html`): one 11.5 sp line
 * under an in-app bar, starting 5 dp right of the pace mark, or ending 5 dp left of it
 * when there is no room ([PaceText.captionRightOfMark]). Its leading value is drawn in
 * the strong ink: the mark ("60% pace mark · 10 under") or, in Vs pace, the usage ("52%
 * used · mark 39%"). Nothing when [PaceText.caption] has nothing to say (spent, unclocked,
 * unread). Costs 22 dp.
 */
@Composable
internal fun PaceCaption(mode: PaceText.Mode, percent: Double?, elapsed: Double?, underline: Boolean = false) {
    val forms = PaceText.captionForms(mode, percent, elapsed)
    if (forms.isEmpty() || elapsed == null) return
    val onSurface = MaterialTheme.colorScheme.onSurface
    fun annotate(text: String): AnnotatedString = buildAnnotatedString {
        val strongPart = if (mode == PaceText.Mode.VS_PACE) text.substringBefore(" · ") else text.substringBefore(" ")
        // CCRM-104 (Pace Line Parity) D5: a press underlines the caption for 3 s (the font's own underline).
        withStyle(SpanStyle(textDecoration = if (underline) TextDecoration.Underline else null)) {
            withStyle(SpanStyle(color = onSurface, fontWeight = FontWeight.SemiBold)) {
                append(strongPart)
            }
            append(text.removePrefix(strongPart))
        }
    }
    val style = MaterialTheme.typography.bodySmall.copy(
        fontSize = 11.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val measurer = rememberTextMeasurer()
    val gap = with(LocalDensity.current) { 5.dp.toPx() }
    // CCBG-93 (Summary Caption Clip): the line is 17 dp at font 1.0 and grows with the text, so large text is never
    // cut through; a caption wider than the bar takes its short form ("52% used", "13 over"), as the notification does.
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(top = 5.dp)
            .heightIn(min = 17.dp),
    ) {
        val width = constraints.maxWidth.toFloat()
        val text = PaceText.fitText(forms) { measurer.measure(annotate(it), style).size.width <= width } ?: forms.first()
        val annotated = annotate(text)
        val textWidth = measurer.measure(annotated, style).size.width.toFloat()
        val mark = width * BarGeometry.tickFraction(elapsed)
        val right = PaceText.captionRightOfMark(width, elapsed, textWidth, gap)
        val x = (if (right) mark + gap else mark - gap - textWidth).coerceAtLeast(0f)
        Text(
            annotated,
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), 0) },
        )
    }
}

@Composable
private fun emphasised(line: AppPace.Line): AnnotatedString {
    val warn = barFill(95.0)
    val e = line.emphasis
    val at = e?.let { line.text.indexOf(it) } ?: -1
    if (e == null || at < 0) return AnnotatedString(line.text)
    return buildAnnotatedString {
        append(line.text.substring(0, at))
        withStyle(SpanStyle(color = warn, fontWeight = FontWeight.Bold)) { append(e) }
        append(line.text.substring(at + e.length))
    }
}

/**
 * CCRM-104 (Pace Line Parity) D3, D10: a pace line, its "<duration> behind|ahead" span in the tone
 * (green under, red over, ink on the mark) and semibold, the rest in [base]. The form is chosen
 * at run time on the scaled text width ([PaceText.fit]: full, no weekday, duration+word); if even
 * the floor does not fit it steps down to 10 sp, then ellipsizes (Astra C1-4).
 */
@Composable
internal fun PaceLineText(
    line: AppPace.Line,
    fontSize: TextUnit,
    base: Color,
    weight: FontWeight,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val dark = appDark()
    val ink = MaterialTheme.colorScheme.onSurface
    val tint = Grammar.vsColor(line.tone ?: PaceText.VsTone.ON, dark, ink, ink)
    val measurer = rememberTextMeasurer()
    fun styled(f: PaceText.PaceLine): AnnotatedString {
        val span = f.tinted ?: f.text.takeIf { f.tone == PaceText.VsTone.ON && f.tinted == null }?.substringBefore(" · ")
        return buildAnnotatedString {
            if (span != null && f.text.startsWith(span)) {
                withStyle(SpanStyle(color = tint, fontWeight = FontWeight.SemiBold)) { append(span) }
                append(f.text.removePrefix(span))
            } else append(f.text)
        }
    }
    // CCBG-92 (Summary Line Measure): measure with the style the Text below draws with — the theme's body
    // style (its 0.5 sp letter spacing included), not a bare TextStyle, or every form measures narrow and the
    // line is cut mid-clock instead of dropping to its next form.
    val drawn = LocalTextStyle.current
    BoxWithConstraints(modifier) {
        val avail = constraints.maxWidth
        fun width(f: PaceText.PaceLine, sp: TextUnit): Int =
            measurer.measure(
                styled(f),
                drawn.merge(TextStyle(fontSize = sp, fontWeight = weight, lineHeight = lineHeight)),
                softWrap = false,
                maxLines = 1,
            ).size.width
        var size = fontSize
        var chosen = PaceText.fit(line.forms) { t -> line.forms.first { it.text == t }.let { width(it, size) <= avail } }
            ?: line.forms.first()
        if (width(chosen, size) > avail) {
            size = 10.sp
            chosen = PaceText.fit(line.forms) { t -> line.forms.first { it.text == t }.let { width(it, size) <= avail } }
                ?: line.forms.first()
        }
        Text(
            styled(chosen),
            fontSize = size,
            lineHeight = lineHeight,
            fontWeight = weight,
            color = base,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A Weekly line: the pace line when it has one, else (spent, no clock) today's [emphasised] words. */
@Composable
private fun WeeklyLineText(line: AppPace.Line, fontSize: TextUnit, lineHeight: TextUnit, weight: FontWeight, base: Color) {
    if (line.forms.isNotEmpty()) PaceLineText(line, fontSize, base, weight, lineHeight = lineHeight)
    else Text(emphasised(line), fontSize = fontSize, lineHeight = lineHeight, fontWeight = weight, color = base)
}

/**
 * The burn-rate view for one window: a sparkline of this window instance's
 * fetches (dashed tail = extrapolation) and a plain-words projection line.
 *
 * When there isn't enough signal to project honestly, it says so rather than
 * rendering nothing — a silently missing chart is indistinguishable from a broken
 * one, which is exactly how it read before.
 *
 * CCRM-75 (Chart Height) and CCRM-77 (Transposed Chart) reach the chart through
 * [chartSize] and [orientation]; the pace readout and estimate line below it are drawn
 * at every size, because at Small they are the only place the numbers are left.
 */
@Composable
internal fun TrendBlock(
    window: UsageWindow,
    samples: List<Pair<Long, Double>>,
    windowLengthMs: Long,
    use24h: Boolean,
    usageLeft: Boolean,
    chartSize: ChartSize,
    chartOrientation: ChartOrientation,
    /**
     * CCRM-97 (Pace Number): the Weekly chart's band · allowance line, drawn under the
     * chart where "40% below even pace" was; null under the 5h chart (decision 2: no Pace
     * on the session).
     */
    paceLine: AppPace.Line? = null,
    /** CCRM-102 (Spent Week Gate): a greyed 5h draws no projection, on the chart or below it. */
    gated: Boolean = false,
    /**
     * CCRM-103 (Hybrid Notification), rev D §2b C5: the ring's tap readout, shown for
     * [TAP_READOUT_MS] in place of the projection line under the 5h chart.
     */
    readout: String? = null,
) {
    val resetMs = window.resetsAt?.toEpochMilli() ?: return
    if (samples.size < 2) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Not enough history in this window yet to chart a pace",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PaceWordsLine(paceLine)
        if (readout != null) {
            Spacer(Modifier.height(2.dp))
            SwapText(readout, MaterialTheme.typography.bodySmall.fontSize)
        }
        return
    }
    val est = Projection.estimate(samples, resetMs)
    val atLimit = (window.percent ?: 0.0) >= 100.0

    Spacer(Modifier.height(10.dp))
    // Measured here rather than derived from the window width: the chart sits inside a
    // card inside a capped column, so only this box knows what it actually got.
    val windowHeight = LocalWindowHeight.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        UsageSparkline(
            samples = samples,
            windowStartMs = resetMs - windowLengthMs,
            windowEndMs = resetMs,
            // Read here, in composition, exactly as `elapsedPercent` below reads
            // `Instant.now()` for the bar's pace mark: ProfileScreen's 5-second tick
            // recomposes this block, so both fractions are taken from the same clock at
            // the same moment and the divider walks with the mark instead of waiting for
            // the next poll (CCRM-74 (Chart Polish) item 1).
            nowMs = System.currentTimeMillis(),
            projectedEnd = est?.takeIf { !gated }?.let { e ->
                if (e.hitsLimitAtMs != null) e.hitsLimitAtMs to 100.0 else resetMs to e.pctAtReset
            },
            color = barFill(window.percent),
            use24h = use24h,
            usageLeft = usageLeft,
            linesOnly = chartSize.linesOnly,
            orientation = chartOrientation,
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight(maxWidth, windowHeight, chartSize)),
        )
    }
    PaceWordsLine(paceLine)
    if (readout != null) {
        Spacer(Modifier.height(2.dp))
        SwapText(readout, MaterialTheme.typography.bodySmall.fontSize)
        return
    }
    if (est == null && !atLimit && !gated) {
        Spacer(Modifier.height(2.dp))
        Text(
            "Usage hasn't moved enough yet to project a pace",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (est != null && !atLimit && !gated) {
        Spacer(Modifier.height(2.dp))
        val hits = est.hitsLimitAtMs
        val rate = " · ${String.format(Locale.US, "%.1f", est.ratePctPerHour)}%/h"
        // CCRM-30 (Estimate Honesty): the projection is inferred, so it carries
        // the marker and a tap-to-reveal provenance line.
        EstimateLine(
            text = (if (hits != null)
                "At this pace: 100% at ${Fmt.dayTime(Instant.ofEpochMilli(hits), use24h)} — " +
                    "${Fmt.span(resetMs - hits)} before the reset"
            else
                "At this pace: ~${est.pctAtReset.toInt()}% when the window resets") + rate,
            provenance = "Projected from this window's samples — a least-squares fit " +
                "anchored on the latest reading. It shifts as new polls land.",
            color = if (hits != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The open Weekly chart's first line under the chart: the pace line ("21h 50m ahead · Sun
 * 9:30 PM", CCRM-104 (Pace Line Parity)); a spent window's "Out until …" bold in the warning colour.
 */
@Composable
private fun PaceWordsLine(line: AppPace.Line?) {
    line ?: return
    Spacer(Modifier.height(6.dp))
    if (line.forms.isNotEmpty()) {
        PaceLineText(
            line, MaterialTheme.typography.bodySmall.fontSize, MaterialTheme.colorScheme.onSurfaceVariant,
            FontWeight.Normal,
        )
        return
    }
    val over = line.emphasis != null
    Text(
        line.text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (over) FontWeight.Bold else FontWeight.Normal,
        color = if (over) barFill(95.0) else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --- the cards ---

/**
 * A card body, with the Compact tap target when there is one. [onToggle] null is
 * Comfortable — and the credits card, which never folds.
 */
@Composable
private fun CardShell(onToggle: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    // The *whole* card is the tap target in Compact (wireframe §3, decision 4), not a
    // 24dp glyph.
    Card(modifier = if (onToggle == null) Modifier else Modifier.clickable { onToggle() }) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Which figure's line is showing its tap readout: the ring, the Weekly, or a model limit by name. */
private const val TAP_RING = "ring"
private const val TAP_WEEKLY = "weekly"
private fun tapCap(name: String) = "cap:$name"

/**
 * CCRM-103 (Hybrid Notification), the account tile (shape grammar rev D §1, §2, §2b, §7):
 * one card for both windows. The 5h ring on the left ([GrammarRingBox], [tileRingDp]),
 * the 7-day bars stacked on the right — "Weekly · All models" (just "Weekly" with no model
 * limit shown) with its figure at the ring's number size, then each shown model limit —
 * each with its dashed room and V1 caption. Closed (Compact): under the tile the band line
 * and the 5h line, led by a 12 dp ring glyph. Open (Comfortable, or a tapped Compact
 * tile): the chips 7d · <model> · 5h and the chosen series' chart and lines; the 5h chip
 * also draws the ring's dotted projection. Tapping a figure swaps its own line for
 * [TAP_READOUT_MS] to the pace-mark readout; tapping the ring also picks the 5h chip on an
 * open tile (C5).
 *
 * A plan with no 5h keeps the ring slot, greyed with a cross; a spent week greys the ring
 * and its 5h line becomes CCRM-102 (Spent Week Gate)'s note.
 */
@Composable
internal fun AccountTile(
    data: UsageData,
    history: List<HistoryPoint>,
    compact: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    selectedCap: String?,
    onSelectCap: (String?) -> Unit,
    use24h: Boolean,
    mode: PaceText.Mode,
    resetClock: Boolean,
    showOverPace: Boolean,
    chartSize: ChartSize,
    chartOrientation: ChartOrientation,
    showModelLimits: Boolean = true,
    /** The chip the tile starts on: 5h when the drawn order leads with it ([tileStartsOnFive]). */
    startOnFive: Boolean = false,
) {
    val open = !compact || expanded
    val caps = if (showModelLimits) data.modelCaps else emptyList()
    val session = data.session
    val gate = data.weekly?.takeIf { Pace.isSpent(it.percent) }
    val dark = appDark()
    val accent = MaterialTheme.colorScheme.primary
    val widthDp = LocalWindowWidth.current.value
    val ringDp = tileRingDp(widthDp)
    val gapDp = tileRingGapDp(widthDp)

    // Session-only: the 5h chip is not stored with the series (CCRM-73's pref keeps a cap name).
    // It starts on 5h when the drawn order leads with the 5h (Fable A5, as iOS).
    var fiveChip by remember(startOnFive) { mutableStateOf(startOnFive) }
    val showFive = open && fiveChip && session != null
    // CCRM-104 (Pace Line Parity) D5: the figure pressed in the last [TAP_READOUT_MS], by key.
    var pressed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pressed) {
        if (pressed != null) {
            delay(TAP_READOUT_MS)
            pressed = null
        }
    }

    val sessionElapsed = elapsedPercent(session, SESSION_MS)
    val sessionSamples = session?.resetsAt?.let {
        Projection.sessionSamples(history, it.toEpochMilli(), SESSION_MS)
    } ?: emptyList()
    val sessionEstimate = session?.resetsAt?.let { Projection.estimate(sessionSamples, it.toEpochMilli()) }

    val ringState = tileRingState(session, data.weekly)
    val ringFigure = if (session == null) null else AppPace.figure(session.percent, sessionElapsed, mode).first
    val ringTone = PaceText.vsPaceTone(session?.percent, sessionElapsed)
    val ringFigureColor = if (mode == PaceText.Mode.VS_PACE && ringTone != PaceText.VsTone.USAGE &&
        ringState == GrammarRing.State.NORMAL
    ) Grammar.vsColor(ringTone, dark, Grammar.ink(dark), Grammar.ink(dark)) else null
    // A greyed 5h can't be used, so its figure has nothing to read out; the 5h window's caption
    // ("40% pace mark · 12 over") takes the 5h line's place for 3 s.
    val ringCaption = if (ringState == GrammarRing.State.NORMAL) PaceText.caption(mode, session?.percent, sessionElapsed) else null
    val ringTappable = session != null && (open || ringCaption != null)

    CardShell(onToggle.takeIf { compact }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GrammarRingBox(
                size = ringDp.dp,
                pct = session?.percent,
                elapsed = sessionElapsed,
                accent = accent,
                figure = ringFigure,
                description = ringDescription(ringState, ringFigure, mode, ringTone),
                modifier = Modifier.clickable(
                    enabled = ringTappable,
                    onClickLabel = ringCaption ?: "Show the 5-hour chart",
                ) {
                    // C5 "both on one tap": open, it also picks the 5h chip, and the caption
                    // swaps the 5h chart's projection line; folded, it swaps the 5h line.
                    if (open) fiveChip = true
                    if (ringCaption != null) pressed = TAP_RING
                },
                state = ringState,
                figureColor = ringFigureColor,
                projectionPct = if (showFive && gate == null) tileProjectionPct(sessionEstimate) else null,
                showOverPace = showOverPace,
            )
            Spacer(Modifier.width(gapDp.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TileBarRow(
                    label = if (caps.isNotEmpty()) "Weekly · All models" else "Weekly",
                    window = data.weekly,
                    lead = true,
                    ringDp = ringDp,
                    mode = mode,
                    showOverPace = showOverPace,
                    underlined = pressed == TAP_WEEKLY,
                    onPress = { pressed = TAP_WEEKLY },
                )
                for (cap in caps) {
                    TileBarRow(
                        label = cap.modelName,
                        window = cap.window,
                        lead = false,
                        ringDp = ringDp,
                        mode = mode,
                        showOverPace = showOverPace,
                        underlined = pressed == tapCap(cap.modelName),
                        onPress = { pressed = tapCap(cap.modelName) },
                    )
                }
            }
        }

        if (!open) {
            // The band line, then the 5h line led by the ring glyph (rev D §1, T1–T8).
            Spacer(Modifier.height(10.dp))
            WeeklyLineText(
                AppPace.weeklyFolded(data.weekly, elapsedPercent(data.weekly, WEEKLY_MS), use24h),
                13.5.sp, 18.sp, FontWeight.SemiBold, MaterialTheme.colorScheme.onSurface,
            )
            if (session != null && gate != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    AppPace.spentWeekNote(gate, use24h),
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (session != null) {
                val line = AppPace.fiveHourLine(session, sessionEstimate, use24h)
                val swapped = ringCaption.takeIf { pressed == TAP_RING }
                if (line != null || swapped != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 3.dp)) {
                            FiveHourGlyph(session.percent, sessionElapsed, showOverPace)
                        }
                        Spacer(Modifier.width(4.dp))
                        if (swapped != null) {
                            SwapText(swapped, 13.5.sp)
                        } else if (line != null) {
                            Text(
                                emphasised(line),
                                fontSize = 13.5.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            return@CardShell
        }

        // Open: the chips, then the chosen series' chart and lines (rev D §2, A2/A3).
        Spacer(Modifier.height(12.dp))
        if (caps.isNotEmpty() || session != null) {
            TileChips(
                caps = caps,
                selectedCap = selectedCap,
                fiveSelected = showFive,
                hasFive = session != null,
                onSelectCap = { name ->
                    fiveChip = false
                    onSelectCap(name)
                },
                onFive = { fiveChip = true },
            )
            Spacer(Modifier.height(10.dp))
        }
        if (showFive && session != null) {
            Column(Modifier.alpha(if (gate != null) 0.45f else 1f)) {
                TrendBlock(
                    window = session,
                    samples = sessionSamples,
                    windowLengthMs = SESSION_MS,
                    use24h = use24h,
                    usageLeft = mode == PaceText.Mode.LEFT,
                    chartSize = chartSize,
                    chartOrientation = chartOrientation,
                    gated = gate != null,
                    readout = ringCaption.takeIf { pressed == TAP_RING },
                )
                Spacer(Modifier.height(2.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                ResetRow(session, use24h, resetClock)
            }
            gate?.let { weekly ->
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Text(
                    AppPace.spentWeekNote(weekly, use24h),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
            return@CardShell
        }
        // The card splits its *own* content, so the threshold is the one Adaptive keeps for
        // exactly that (700dp, [hasTwoColumns]) rather than the 600dp two-pane breakpoint.
        if (caps.isNotEmpty() && hasTwoColumns()) {
            WeeklyColumns(
                data, history, caps, selectedCap, use24h, mode, resetClock, chartSize, chartOrientation,
            )
            return@CardShell
        }
        val cap = caps.firstOrNull { it.modelName == selectedCap }
        val window = cap?.window ?: data.weekly
        window?.let { w ->
            TrendBlock(
                window = w,
                samples = weeklySamplesFor(history, cap, w),
                windowLengthMs = WEEKLY_MS,
                use24h = use24h,
                usageLeft = mode == PaceText.Mode.LEFT,
                chartSize = chartSize,
                chartOrientation = chartOrientation,
                paceLine = AppPace.weeklyOpen(w, elapsedPercent(w, WEEKLY_MS), use24h),
            )
        }
        Spacer(Modifier.height(2.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        ResetRow(window, use24h, resetClock)
    }
}

/**
 * One 7-day bar on the tile (rev D `tile()` rowH): the label and the figure on one 20 dp
 * row, the bar, and the V1 caption — or, for [TAP_READOUT_MS] after the figure is tapped,
 * the [readout] in the caption's place. The lead row ("Weekly · All models") has a 13.5 sp
 * label, its figure at the ring's number size (0.25 of the ring) and a 10 dp bar; a model
 * limit a 12.5 sp label, a 14 sp figure and a 7 dp bar.
 */
@Composable
private fun TileBarRow(
    label: String,
    window: UsageWindow?,
    lead: Boolean,
    ringDp: Int,
    mode: PaceText.Mode,
    showOverPace: Boolean,
    underlined: Boolean,
    onPress: () -> Unit,
) {
    val pct = window?.percent
    val elapsed = elapsedPercent(window, WEEKLY_MS)
    // The tile's figures carry no word: Left "48%", Vs pace "+13%" signed in its colour (CCRM-104 D1).
    val figure = AppPace.figureSigned(pct, elapsed, mode).first
    // D5: a press on the figure underlines the caption under its bar; TalkBack reads it as the action.
    val caption = PaceText.caption(mode, pct, elapsed)
    val figureSp = if (lead) (0.25f * ringDp).roundToInt().sp else 14.sp
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // CCBG-58 (Tile Title Clip), CCBG-59 (Model Figure Alignment): the label takes all
            // the width the figure leaves, so every row's figure sits flush right; a lead
            // label that still does not fit drops " · All models" rather than ellipsizing.
            // Keyed on the width, so a fold or unfold (the activity handles the size change
            // itself) measures again and the full label comes back where it fits.
            BoxWithConstraints(Modifier.weight(1f)) {
                var short by remember(label, maxWidth) { mutableStateOf(false) }
                Text(
                    if (short) label.substringBefore(" · ") else label,
                    fontSize = if (lead) 13.5.sp else 12.5.sp,
                    color = if (lead) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (lead && !short && it.hasVisualOverflow && " · " in label) short = true },
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                grammarFigure(figure),
                style = TextStyle(fontSize = figureSp, fontWeight = FontWeight.Bold).grammarDigits(),
                color = grammarFigureColor(pct, elapsed, mode),
                maxLines = 1,
                modifier = Modifier.clickable(enabled = caption != null, onClickLabel = caption) { onPress() },
            )
        }
        Spacer(Modifier.height(5.dp))
        UsageBarLine(
            percent = pct,
            fillColor = barFill(pct),
            height = if (lead) 10.dp else 7.dp,
            elapsedPercent = elapsed,
            showOverPace = showOverPace,
            dashedRoom = true,
        )
        PaceCaption(mode, pct, elapsed, underline = underlined)
    }
}

/**
 * The 5h line's lead (rev D §1, "The 5h line"): a 12 dp ring glyph in place of the word
 * "5h" — the status-bar ring's geometry ([RingRenderer.PaceMark.NEEDLE]), in the severity
 * colour, with its red slice and, at 100%, its cross.
 */
@Composable
private fun FiveHourGlyph(pct: Double?, elapsed: Double?, showOverPace: Boolean) {
    val context = LocalContext.current
    val dark = appDark()
    val px = with(LocalDensity.current) { 12.dp.roundToPx() }
    val fill = barFill(pct).toArgb()
    val bitmap = remember(px, pct, elapsed, fill, dark, showOverPace) {
        RingRenderer.draw(
            context, px, RingRenderer.ICON_BAND_W * px / 24f, pct, elapsed, fill, dark,
            showOverPace, spentCross = Pace.isSpent(pct),
        ).asImageBitmap()
    }
    Image(bitmap, contentDescription = null, modifier = Modifier.size(12.dp))
}

/**
 * The inner screen's open tile with model limits (wireframe §8 of the main-screen page):
 * two charts side by side, All on the left and one cap on the right, each with its own
 * chart, pace line, estimate line and reset row. The tile's chips pick the right column's
 * cap; All (null) resolves to the first cap rather than leaving the right column blank.
 * The bars are the tile's, above, so the columns no longer repeat them.
 */
@Composable
private fun WeeklyColumns(
    data: UsageData,
    history: List<HistoryPoint>,
    caps: List<ModelCap>,
    selectedCap: String?,
    use24h: Boolean,
    mode: PaceText.Mode,
    resetClock: Boolean,
    chartSize: ChartSize,
    chartOrientation: ChartOrientation,
) {
    val cap = caps.firstOrNull { it.modelName == selectedCap } ?: caps.first()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ColumnGutter),
    ) {
        Column(Modifier.weight(1f)) {
            data.weekly?.let { w ->
                TrendBlock(
                    window = w,
                    samples = weeklySamplesFor(history, null, w),
                    windowLengthMs = WEEKLY_MS,
                    use24h = use24h,
                    usageLeft = mode == PaceText.Mode.LEFT,
                    chartSize = chartSize,
                    chartOrientation = chartOrientation,
                    paceLine = AppPace.weeklyOpen(w, elapsedPercent(w, WEEKLY_MS), use24h),
                )
            }
            Spacer(Modifier.height(2.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            ResetRow(data.weekly, use24h, resetClock)
        }
        Column(Modifier.weight(1f)) {
            TrendBlock(
                window = cap.window,
                samples = weeklySamplesFor(history, cap, cap.window),
                windowLengthMs = WEEKLY_MS,
                use24h = use24h,
                usageLeft = mode == PaceText.Mode.LEFT,
                chartSize = chartSize,
                chartOrientation = chartOrientation,
                paceLine = AppPace.weeklyOpen(cap.window, elapsedPercent(cap.window, WEEKLY_MS), use24h),
            )
            Spacer(Modifier.height(2.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            ResetRow(cap.window, use24h, resetClock)
        }
    }
}

/** The pool's samples, or one cap's — bound to the same window the bar above reads. */
private fun weeklySamplesFor(
    history: List<HistoryPoint>,
    cap: ModelCap?,
    window: UsageWindow,
): List<Pair<Long, Double>> {
    val resetMs = window.resetsAt?.toEpochMilli() ?: return emptyList()
    return if (cap == null) {
        Projection.weeklySamples(history, resetMs, WEEKLY_MS)
    } else {
        Projection.capSamples(history, cap.modelName, resetMs, WEEKLY_MS)
    }
}

/**
 * The open tile's chips (rev D §2; CCRM-106 (7d Chip)): "7d · Fable · 5h". The 7d chip is the
 * stored "no cap" selection (null), unchanged; only its label moved from "All". "5h" stays a word here — a chip
 * needs a name — and only on an account with a 5h; tapping the ring picks it too.
 */
/** CCRM-106 (7d Chip): the 7-day series' chip, and what TalkBack reads for it. Pinned by `MainCardsTest`. */
internal const val SEVEN_DAY_CHIP = "7d"
internal const val SEVEN_DAY_CHIP_SPOKEN = "7-day, all models"

@Composable
private fun TileChips(
    caps: List<ModelCap>,
    selectedCap: String?,
    fiveSelected: Boolean,
    hasFive: Boolean,
    onSelectCap: (String?) -> Unit,
    onFive: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !fiveSelected && selectedCap == null,
            onClick = { onSelectCap(null) },
            label = { Text(SEVEN_DAY_CHIP) },
            // CCRM-106 (7d Chip): the label moved from "All"; TalkBack still says what it holds.
            modifier = Modifier.semantics { contentDescription = SEVEN_DAY_CHIP_SPOKEN },
        )
        for (cap in caps) {
            FilterChip(
                selected = !fiveSelected && selectedCap == cap.modelName,
                onClick = { onSelectCap(cap.modelName) },
                label = { Text(cap.modelName) },
            )
        }
        if (hasFive) {
            FilterChip(
                selected = fiveSelected,
                onClick = onFive,
                label = { Text("5h") },
            )
        }
    }
}

/**
 * Pay-as-you-go credits — identical in both densities (wireframe decision 8): it never
 * had a chart, so Compact has nothing to fold, and it takes no chevron and no tap.
 *
 * [serifHeadline] is CCRM-60 (Dual Identity)'s Claude serif: CCRM-74 (Chart Polish)
 * item 3 gives every card **one** row style, so this card's headline percentage is
 * `bodyMedium` bold rather than a `titleMedium` a size larger (wireframe §6).
 */
@Composable
internal fun CreditsCard(credits: SpendCredits, usageLeft: Boolean, serifHeadline: Boolean = false) {
    val pct = credits.percent
    // The binding constraint, not the monthly remainder: identical while the
    // server reports no balance, but the day it does, "left" must mean the
    // smaller of the two ceilings (CCBG-6).
    val remaining = credits.bindingRemainingMinor
    CardShell(null) {
        var creditsProv by remember { mutableStateOf(false) }
        // Name on the left, the headline percentage on the right, bar underneath.
        // With no cap there is no percentage and no bar — just what has been spent.
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Usage credits", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    credits.limitMinor != null ->
                        "${Fmt.money(credits.usedMinor, credits.exponent, credits.currency)} / " +
                            Fmt.money(credits.limitMinor, credits.exponent, credits.currency)
                    // CCRM-54 (ChatGPT Account) part 2: OpenAI reports a
                    // pot, not a meter — nothing has been "spent" from it
                    // and there is no cap to spend against, so the balance
                    // is the whole story. "$0.00 spent" would say nothing.
                    credits.usedMinor == 0L && credits.balanceMinor != null ->
                        "${Fmt.money(credits.balanceMinor, credits.exponent, credits.currency)} balance"
                    else ->
                        "${Fmt.money(credits.usedMinor, credits.exponent, credits.currency)} spent"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // CCRM-30 (Estimate Honesty): the percentage is computed
            // locally, so it carries the marker — with a note that says
            // it's *finer* than the server's figure, not a hedge.
            Text(
                // The rounded display percent, not the exact one — credits
                // round where windows truncate (CCRM-3, deliberate).
                if (pct != null) buildAnnotatedString {
                    append(Fmt.usageShort(credits.percentDisplay?.toDouble(), usageLeft))
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.7f),
                        )
                    ) { append(" ⓘ") }
                } else AnnotatedString("No cap"),
                // One row style everywhere (CCRM-74 (Chart Polish) item 3, §6).
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = if (serifHeadline) FontFamily.Serif else FontFamily.Default,
                modifier = Modifier.clickable(enabled = pct != null) {
                    creditsProv = !creditsProv
                },
            )
        }
        if (pct != null) {
            Spacer(Modifier.height(8.dp))
            // No elapsed, so no pace mark: credits are money, and money
            // has no clock. Spending them faster than the month isn't a
            // thing to be behind or ahead of.
            UsageBarLine(pct, barFill(pct))
        }
        if (creditsProv) {
            Spacer(Modifier.height(6.dp))
            ProvenanceNote(
                "Computed from the exact amounts — finer than the " +
                    "server's rounded figure, not an estimate.",
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                remaining == null ->
                    "No monthly spend limit — credits cover you when you hit your plan limits"
                // The balance branch above already prints the amount; saying
                // "$12.40 left" under "$12.40 balance" is the same fact twice.
                credits.usedMinor == 0L && credits.limitMinor == null &&
                    credits.balanceMinor != null ->
                    "Covers you when you hit your plan limits"
                remaining > 0L ->
                    "${Fmt.money(remaining, credits.exponent, credits.currency)} left · " +
                        if (credits.limitMinor != null) {
                            "covers you when you hit your plan limits"
                        } else {
                            // Only reachable once the server reports a balance
                            // for an uncapped account — the balance is then the
                            // one ceiling that exists.
                            "no monthly spend limit"
                        }
                else -> "All credits spent — nothing left to cover plan overruns"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (remaining == null || remaining > 0L) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * CCRM-25 (Card Layout), wireframe §7e/§7f: the disclosure at the foot of the list that
 * the folded cards live under. Session-only state and collapsed by default — folding a
 * card is the persistent decision; peeking at it is not.
 */
@Composable
internal fun MoreDisclosure(open: Boolean, count: Int, onToggle: () -> Unit) {
    Text(
        "More ${if (open) "▴" else "▾"} · $count ${if (count == 1) "card" else "cards"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 6.dp),
    )
}

/**
 * CCRM-72 (Main Screen Redesign), wireframe §2: one row under the cards in place of the
 * Refresh button and the two timestamp lines. The whole row is the refresh affordance —
 * tapping it runs the same manual poll — and the glyph becomes a spinner while that poll
 * is in flight.
 */
@Composable
internal fun StatusLine(
    fetchedAt: Long,
    lastAttemptAt: Long,
    lastOk: Boolean,
    refreshing: Boolean,
    now: Long,
    /** CCRM-113 (Sample Port): "Sample data · nothing is fetched", no refresh glyph, and the row is not a button. */
    sample: Boolean = false,
    onRefresh: () -> Unit,
) {
    if (sample) {
        Text(
            com.robin.claudeusage.data.SampleMode.STATUS_LINE,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !refreshing) { onRefresh() }
            .padding(vertical = 6.dp),
    ) {
        Text(
            statusLine(fetchedAt, lastAttemptAt, lastOk, now),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Refresh now",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
