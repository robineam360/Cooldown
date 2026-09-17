package com.robin.claudeusage

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robin.claudeusage.data.CardId
import com.robin.claudeusage.data.HistoryPoint
import com.robin.claudeusage.data.ModelCap
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.SpendCredits
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.ui.BarGeometry
import com.robin.claudeusage.ui.ChartOrientation
import com.robin.claudeusage.ui.ChartSize
import com.robin.claudeusage.ui.CompactLine
import com.robin.claudeusage.ui.EstimateLine
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.LocalWindowHeight
import com.robin.claudeusage.ui.LocalWidthClass
import com.robin.claudeusage.ui.PACE_DEAD_ZONE
import com.robin.claudeusage.ui.Palette
import com.robin.claudeusage.ui.ProvenanceNote
import com.robin.claudeusage.ui.UsageSparkline
import com.robin.claudeusage.ui.appDark
import com.robin.claudeusage.ui.chartHeight
import com.robin.claudeusage.ui.compactLine
import com.robin.claudeusage.ui.elapsedPercent
import com.robin.claudeusage.ui.pacePhrase
import com.robin.claudeusage.ui.statusLine
import com.robin.claudeusage.ui.twoPane
import java.time.Instant
import java.util.Locale

/**
 * CCRM-72 (Main Screen Redesign): the main screen's cards, split out of `ProfileScreen`
 * so each one owns its own two densities — `design/2026-09-17-main-screen-redesign.html`
 * rev E, approved 2026-09-17.
 *
 * *Comfortable* is a strict no-op on the cards (wireframe §1): every card draws exactly
 * what v1.6 drew. *Compact* folds [SessionCard] and [WeeklyCard] to a title row, their
 * bar(s) and one line, with a chevron in the title row and the whole card as the tap
 * target; [CreditsCard] has no chart, so it has nothing to fold and is identical in both.
 *
 * Also here: CCRM-73 (Model Cap Chart)'s All/cap toggle inside the 7-day card, CCRM-75
 * (Chart Height) and CCRM-77 (Transposed Chart) threading through to the chart, and
 * CCRM-25 (Card Layout)'s "More" disclosure.
 */

private const val SESSION_MS: Long = Projection.SESSION_MS
private const val WEEKLY_MS: Long = Projection.WEEKLY_MS

/** The gutter between the two chart columns of the wide 7-day card (wireframe §8). */
private val ColumnGutter: Dp = 16.dp

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
 * CCRM-25 (Card Layout): the cards this account has data for right now — the same three
 * existence tests `ProfileScreen` renders by, in one pure place so the top bar's ⋮ can
 * count them without duplicating the rules (wireframe §9: the entry point appears only
 * at two or more).
 */
fun dataCards(data: UsageData?, creditsVisible: Boolean): Set<CardId> {
    data ?: return emptySet()
    val ids = LinkedHashSet<CardId>()
    if (data.session != null) ids += CardId.SESSION
    if (data.weekly != null || data.modelCaps.isNotEmpty()) ids += CardId.WEEKLY
    if (data.credits?.isReportable == true && creditsVisible) ids += CardId.CREDITS
    return ids
}

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
) {
    val fraction = ((percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
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
                    .clip(RoundedCornerShape(height / 2))
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
        if (tick) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = BarGeometry.tickWidth(height.toPx())
                val cx = size.width * BarGeometry.tickFraction(elapsedPercent!!)
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
 * The label/percentage row every window row draws — CCRM-74 (Chart Polish) item 3: the
 * 5-hour card's headline is now literally this row too, at the 7-day row's `bodyMedium`
 * size rather than a size larger, so both cards share one row style.
 *
 * [trailing] is the Compact chevron, when there is one; it sits after the percentage so
 * it has the same home whether the card is open or folded (wireframe §3, decision 2).
 */
@Composable
private fun SubRow(
    label: String,
    percent: Double?,
    usageLeft: Boolean,
    serifHeadline: Boolean,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            // CCRM-74 (Chart Polish) item 2: "14%", not "14% used" — the word survives
            // only under the Left setting, where it carries the flipped meaning.
            Fmt.usageShort(percent, usageLeft),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (serifHeadline) FontFamily.Serif else FontFamily.Default,
        )
        trailing?.let {
            Spacer(Modifier.width(6.dp))
            it()
        }
    }
}

/**
 * One 7-day row: All, or a per-model cap. Takes the whole [window] rather than a bare
 * percent because the pace mark needs its reset time — and every row under the 7-day
 * card measures against 7 days, model caps included (they are "· 7-day" surfaces).
 */
@Composable
internal fun SubBar(
    label: String,
    window: UsageWindow?,
    usageLeft: Boolean,
    showOverPace: Boolean = true,
    // CCRM-60 (Dual Identity), decision 4: the Claude room's serif headline,
    // threaded down from ProfileScreen — every "· 7-day" row shares the style.
    serifHeadline: Boolean = false,
) {
    val percent = window?.percent
    SubRow(label, percent, usageLeft, serifHeadline)
    Spacer(Modifier.height(4.dp))
    UsageBarLine(
        percent = percent,
        fillColor = barFill(percent),
        elapsedPercent = elapsedPercent(window, WEEKLY_MS),
        showOverPace = showOverPace,
    )
    Spacer(Modifier.height(10.dp))
}

/** ▾ folded / ▴ open — the Compact chevron, in the title row of both foldable cards. */
@Composable
private fun CardChevron(expanded: Boolean) {
    Icon(
        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
        contentDescription = if (expanded) "Collapse" else "Expand",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

/**
 * The folded card's one line — "Resets in 2h 41m · 32% below pace" (wireframe §3/§4).
 * Above pace, the pace clause alone goes bold in the warning colour; the reset clause
 * stays muted, so the emphasis lands on the fact that changed.
 */
@Composable
private fun CompactStatusLine(line: CompactLine) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val warn = barFill(95.0)
    Text(
        if (line.boldClause == null) {
            AnnotatedString(line.text)
        } else {
            buildAnnotatedString {
                append(line.text.removeSuffix(line.boldClause))
                withStyle(SpanStyle(color = warn, fontWeight = FontWeight.Bold)) {
                    append(line.boldClause)
                }
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
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
) {
    val resetMs = window.resetsAt?.toEpochMilli() ?: return
    if (samples.size < 2) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Not enough history in this window yet to chart a pace",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            projectedEnd = est?.let { e ->
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
    // The pace readout: what the retired "Days elapsed" bar used to say, as a
    // number rather than a row. A ±3 point dead zone around the line stops it
    // flapping between above and below — with its colour — on every poll.
    elapsedPercent(window, windowLengthMs)?.let { elapsed ->
        val delta = (window.percent ?: 0.0) - elapsed
        val above = delta > PACE_DEAD_ZONE
        Spacer(Modifier.height(6.dp))
        Text(
            // CCRM-74 (Chart Polish) item 4: "%" not "points", shared with the chart's
            // own short label so the two can never disagree.
            pacePhrase(delta),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (above) FontWeight.Bold else FontWeight.Normal,
            color = if (above) barFill(95.0) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (est == null && !atLimit) {
        Spacer(Modifier.height(2.dp))
        Text(
            "Usage hasn't moved enough yet to project a pace",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (est != null && !atLimit) {
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

// --- the cards ---

/**
 * A card body, with the Compact tap target when there is one. [onToggle] null is
 * Comfortable — and the credits card, which never folds.
 */
@Composable
private fun CardShell(onToggle: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    // The *whole* card is the tap target in Compact (wireframe §3, decision 4), not a
    // 24dp glyph — the hit area is the full 107dp row.
    Card(modifier = if (onToggle == null) Modifier else Modifier.clickable { onToggle() }) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/**
 * The 5-hour card. Collapsed (Compact only): title row with the chevron, the bar, one
 * line. Expanded, and every Comfortable render: identical to v1.6 — chart, pace readout,
 * estimate line, reset row.
 */
@Composable
internal fun SessionCard(
    window: UsageWindow,
    history: List<HistoryPoint>,
    compact: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    use24h: Boolean,
    usageLeft: Boolean,
    resetClock: Boolean,
    showOverPace: Boolean,
    serifHeadline: Boolean,
    chartSize: ChartSize,
    chartOrientation: ChartOrientation,
) {
    val open = !compact || expanded
    CardShell(onToggle.takeIf { compact }) {
        SubRow(
            "5-hour window", window.percent, usageLeft, serifHeadline,
            trailing = if (compact) ({ CardChevron(expanded) }) else null,
        )
        Spacer(Modifier.height(8.dp))
        UsageBarLine(
            percent = window.percent,
            fillColor = barFill(window.percent),
            elapsedPercent = elapsedPercent(window, SESSION_MS),
            showOverPace = showOverPace,
        )
        if (open) {
            TrendBlock(
                window = window,
                samples = window.resetsAt?.let {
                    Projection.sessionSamples(history, it.toEpochMilli(), SESSION_MS)
                } ?: emptyList(),
                windowLengthMs = SESSION_MS,
                use24h = use24h,
                usageLeft = usageLeft,
                chartSize = chartSize,
                chartOrientation = chartOrientation,
            )
            Spacer(Modifier.height(8.dp))
            ResetRow(window, use24h, resetClock)
        } else {
            Spacer(Modifier.height(8.dp))
            CompactStatusLine(compactLineFor(window, SESSION_MS))
        }
    }
}

/** The folded line for [window], reading its own clock — pure copy, live numbers. */
private fun compactLineFor(window: UsageWindow?, windowLengthMs: Long): CompactLine {
    val resets = window?.resetsAt ?: return compactLine(null, null)
    val elapsed = elapsedPercent(window, windowLengthMs)
    return compactLine(Fmt.relIn(resets), elapsed?.let { (window.percent ?: 0.0) - it })
}

/**
 * The 7-day card. Its `SubBar`s draw in **both** densities (the roadmap's own rule), so
 * collapsed it is the title row, every bar, and one line for the All window; expanded it
 * adds CCRM-73 (Model Cap Chart)'s toggle chips, the chart for the selected series, its
 * pace readout, its estimate line and its reset row.
 *
 * On a two-pane width with at least one cap, the open card runs two chart columns instead
 * — All on the left, the cap on the right (wireframe §8), which is the one place
 * Comfortable deliberately differs from v1.6.
 */
@Composable
internal fun WeeklyCard(
    data: UsageData,
    history: List<HistoryPoint>,
    compact: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    selectedCap: String?,
    onSelectCap: (String?) -> Unit,
    use24h: Boolean,
    usageLeft: Boolean,
    resetClock: Boolean,
    showOverPace: Boolean,
    serifHeadline: Boolean,
    chartSize: ChartSize,
    chartOrientation: ChartOrientation,
) {
    val open = !compact || expanded
    val caps = data.modelCaps
    val twoColumns = open && caps.isNotEmpty() && LocalWidthClass.current.twoPane
    CardShell(onToggle.takeIf { compact }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("7-day window", style = MaterialTheme.typography.titleSmall)
            if (compact) {
                Spacer(Modifier.width(4.dp))
                CardChevron(expanded)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (twoColumns) {
            WeeklyColumns(
                data, history, caps, selectedCap, onSelectCap, use24h, usageLeft,
                resetClock, showOverPace, serifHeadline, chartSize, chartOrientation,
            )
            return@CardShell
        }
        // CCRM-73 (Model Cap Chart): the bars never change with the toggle — All plus
        // one row per cap, whichever series is charted below them.
        SubBar("All", data.weekly, usageLeft, showOverPace, serifHeadline)
        for (cap in caps) {
            SubBar(cap.modelName, cap.window, usageLeft, showOverPace, serifHeadline)
        }
        if (!open) {
            Spacer(Modifier.height(8.dp))
            CompactStatusLine(compactLineFor(data.weekly, WEEKLY_MS))
            return@CardShell
        }
        if (caps.isNotEmpty()) {
            CapChips(
                caps = caps,
                selected = selectedCap,
                includeAll = true,
                onSelect = onSelectCap,
            )
            Spacer(Modifier.height(10.dp))
        }
        val cap = caps.firstOrNull { it.modelName == selectedCap }
        val window = cap?.window ?: data.weekly
        window?.let { w ->
            TrendBlock(
                window = w,
                samples = weeklySamplesFor(history, cap, w),
                windowLengthMs = WEEKLY_MS,
                use24h = use24h,
                usageLeft = usageLeft,
                chartSize = chartSize,
                chartOrientation = chartOrientation,
            )
        }
        Spacer(Modifier.height(2.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        ResetRow(window, use24h, resetClock)
    }
}

/**
 * The inner screen's 7-day card (wireframe §8): two full stacks side by side, All on the
 * left and one cap on the right, each with its own row, bar, chart, pace line, estimate
 * line and reset row. With two caps the right column carries the chip pair that picks
 * which; with one there is no toggle at all, since both series are already on screen.
 */
@Composable
private fun WeeklyColumns(
    data: UsageData,
    history: List<HistoryPoint>,
    caps: List<ModelCap>,
    selectedCap: String?,
    onSelectCap: (String?) -> Unit,
    use24h: Boolean,
    usageLeft: Boolean,
    resetClock: Boolean,
    showOverPace: Boolean,
    serifHeadline: Boolean,
    chartSize: ChartSize,
    chartOrientation: ChartOrientation,
) {
    // Null (All) has no column of its own here, so it resolves to the first cap rather
    // than leaving the right column blank.
    val cap = caps.firstOrNull { it.modelName == selectedCap } ?: caps.first()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ColumnGutter),
    ) {
        Column(Modifier.weight(1f)) {
            SubBar("All", data.weekly, usageLeft, showOverPace, serifHeadline)
            data.weekly?.let { w ->
                TrendBlock(
                    window = w,
                    samples = weeklySamplesFor(history, null, w),
                    windowLengthMs = WEEKLY_MS,
                    use24h = use24h,
                    usageLeft = usageLeft,
                    chartSize = chartSize,
                    chartOrientation = chartOrientation,
                )
            }
            Spacer(Modifier.height(2.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            ResetRow(data.weekly, use24h, resetClock)
        }
        Column(Modifier.weight(1f)) {
            SubBar(cap.modelName, cap.window, usageLeft, showOverPace, serifHeadline)
            if (caps.size > 1) {
                CapChips(
                    caps = caps,
                    selected = cap.modelName,
                    includeAll = false,
                    onSelect = onSelectCap,
                )
                Spacer(Modifier.height(10.dp))
            }
            TrendBlock(
                window = cap.window,
                samples = weeklySamplesFor(history, cap, cap.window),
                windowLengthMs = WEEKLY_MS,
                use24h = use24h,
                usageLeft = usageLeft,
                chartSize = chartSize,
                chartOrientation = chartOrientation,
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

/** "All · Fable", or just the cap names in the wide card's right column. */
@Composable
private fun CapChips(
    caps: List<ModelCap>,
    selected: String?,
    includeAll: Boolean,
    onSelect: (String?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (includeAll) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
            )
        }
        for (cap in caps) {
            FilterChip(
                selected = selected == cap.modelName,
                onClick = { onSelect(cap.modelName) },
                label = { Text(cap.modelName) },
            )
        }
    }
}

/**
 * Pay-as-you-go credits — identical in both densities (wireframe decision 8): it never
 * had a chart, so Compact has nothing to fold, and it takes no chevron and no tap.
 */
@Composable
internal fun CreditsCard(credits: SpendCredits, usageLeft: Boolean) {
    val pct = credits.percent
    // The binding constraint, not the monthly remainder: identical while the
    // server reports no balance, but the day it does, "left" must mean the
    // smaller of the two ceilings (CCBG-6).
    val remaining = credits.bindingRemainingMinor
    CardShell(null) {
        var creditsProv by remember { mutableStateOf(false) }
        // Same shape as the 5-hour card: name on the left, the headline
        // percentage on the right, bar underneath. With no cap there is no
        // percentage and no bar — just what has been spent.
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
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
    onRefresh: () -> Unit,
) {
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
