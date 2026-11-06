package com.robin.claudeusage

import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robin.claudeusage.data.AccountRead
import com.robin.claudeusage.data.HistoryPoint
import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.data.Profile
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.SummaryRank
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsageRepository
import com.robin.claudeusage.ui.AppPace
import com.robin.claudeusage.ui.Grammar
import com.robin.claudeusage.ui.GrammarRing
import com.robin.claudeusage.ui.GrammarRingBox
import com.robin.claudeusage.ui.grammarDigits
import com.robin.claudeusage.ui.grammarFigure
import com.robin.claudeusage.ui.PaceMap
import com.robin.claudeusage.ui.PaceMapEntry
import com.robin.claudeusage.ui.PaceMapGeometry
import com.robin.claudeusage.ui.PaceText
import com.robin.claudeusage.ui.Palette
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.ProviderMark
import com.robin.claudeusage.ui.SummaryRows
import com.robin.claudeusage.ui.appDark
import com.robin.claudeusage.ui.elapsedPercent
import com.robin.claudeusage.ui.hasTwoColumns
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CCRM-98 (Summary Tab): the All tab — every account's week on one page, most room first,
 * then the Pace map (`design/2026-11-02-summary-tab.html` rev B: C1/C2 on the cover, S1–S3,
 * W1 two columns on the Fold's inner screen). Tapping a row opens that account's tab.
 */
@Composable
internal fun SummaryScreen(
    repo: UsageRepository,
    profiles: List<Profile>,
    mode: PaceText.Mode,
    use24h: Boolean,
    showOverPace: Boolean,
    tick: Int,
    onOpen: (Profile) -> Unit,
) {
    val cache = remember { repo.cacheSettings() }
    val dark = appDark()
    // History files are read only when a fetch lands, not on every 5 s tick (Step 5 judge F4);
    // the tick still re-runs the time maths.
    val fetchedAts = profiles.map { repo.snapshot(it).fetchedAt }
    val histories = remember(profiles, fetchedAts) { profiles.associateWith { repo.history().points(it) } }
    // Appearance › Summary order (Robin, 2026-11-02): keyed so a change re-sorts at once.
    val order = cache.summaryOrder()
    val rows = remember(profiles, tick, histories, order) { summaryRows(repo, profiles, histories) }
    val compact = SummaryRows.compact(profiles.size)
    val mapEntries = remember(rows, dark) {
        rows.mapIndexedNotNull { i, r -> r.mapEntry(i + 1, Palette.color(Palette.accentName(cache, r.profile), dark)) }
    }
    val list: @Composable () -> Unit = {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                CardHeader("Weekly", order.header)
                Spacer(Modifier.height(6.dp))
                rows.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider()
                    val accent = Palette.color(Palette.accentName(cache, r.profile), dark)
                    if (compact) {
                        CompactSummaryRow(r, i + 1, accent, mode, use24h, showOverPace) { onOpen(r.profile) }
                    } else {
                        SummaryRow(
                            r, accent, r.name,
                            // API-CONTRACT §11's name, as Settings shows it.
                            repo.plan(r.profile)?.let { Fmt.planLabel(r.profile.provider, it, repo.tier(r.profile)) },
                            mode, use24h, showOverPace,
                        ) {
                            onOpen(r.profile)
                        }
                    }
                }
            }
        }
    }
    if (hasTwoColumns()) {
        // W1: the list on the left, a square map on the right.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { list() }
            BoxWithConstraints(Modifier.weight(1f)) {
                MapCard(mapEntries, maxWidth - 32.dp)
            }
        }
    } else {
        list()
        Spacer(Modifier.height(12.dp))
        MapCard(mapEntries, 230.dp)
    }
    Spacer(Modifier.height(12.dp))
    SummaryStatus(repo, profiles, tick)
}

/** One account as the All tab reads it — the ranked entry plus what its row draws. */
internal data class SummaryRowModel(
    val profile: Profile,
    val name: String,
    val data: UsageData?,
    val elapsed: Double?,
    val group: SummaryRank.Group,
    val samples: List<Pair<Long, Double>>,
) {
    val weekly get() = data?.weekly
    val used get() = data?.weekly?.percent

    fun mapEntry(number: Int, color: Color): PaceMapEntry? {
        val u = used ?: return null
        val gone = elapsed ?: 0.0
        val reset = weekly?.resetsAt?.toEpochMilli()
        val trail = if (reset == null) listOf(gone to u)
        else PaceMapGeometry.trail(samples, reset, Projection.WEEKLY_MS, gone, u)
        return PaceMapEntry(name, color, gone, u, Pace.isSpent(u), trail, number)
    }
}

/** Reads every account, ranks them by [SummaryRank], and returns them in display order. */
private fun summaryRows(
    repo: UsageRepository,
    profiles: List<Profile>,
    histories: Map<Profile, List<HistoryPoint>>,
): List<SummaryRowModel> {
    val now = System.currentTimeMillis()
    val cache = repo.cacheSettings()
    val read = profiles.associate { p ->
        val data = repo.snapshot(p).data
        val history: List<HistoryPoint> = histories[p].orEmpty()
        val elapsed = elapsedPercent(data?.weekly, Projection.WEEKLY_MS)
        val binding = AccountRead.sessionBinding(data, AccountRead.sessionHitsLimitAtMs(data, history))
        val entry = SummaryRank.Entry(
            key = p.key,
            usedPct = data?.weekly?.percent,
            elapsedPct = elapsed,
            msUntilReset = data?.weekly?.resetsAt?.toEpochMilli()?.minus(now),
            sessionBinding = binding,
            sessionMsUntilReset = data?.session?.resetsAt?.toEpochMilli()?.minus(now),
        )
        val samples = data?.weekly?.resetsAt?.let {
            Projection.weeklySamples(history, it.toEpochMilli(), Projection.WEEKLY_MS)
        } ?: emptyList()
        p.key to Triple(p, entry, Pair(data, Pair(elapsed, samples)))
    }
    return SummaryRank.ordered(read.values.map { it.second }, cache.summaryOrder()).map { ranked ->
        val (p, _, rest) = read.getValue(ranked.entry.key)
        SummaryRowModel(p, cache.profileLabel(p), rest.first, rest.second.first, ranked.group, rest.second.second)
    }
}

@Composable
private fun CardHeader(title: String, sub: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.weight(1f))
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MapCard(entries: List<PaceMapEntry>, plotHeight: androidx.compose.ui.unit.Dp) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CardHeader("Pace map", "Weekly, where each account stands")
            Spacer(Modifier.height(8.dp))
            PaceMap(entries, modifier = Modifier.fillMaxWidth().height(plotHeight))
        }
    }
}

/**
 * The full row (C1), with CCRM-103 (Hybrid Notification)'s ring (shape grammar rev D S1,
 * `srow()`): a 48 dp 5h ring on the left spanning the row — greyed with a cross on a plan
 * with no 5h, greyed on a spent week — then mark · name · plan pill · figure; the Weekly
 * bar with its neutral pace tick; the V1 caption; the words (no pace pip since 2026-11-02 —
 * Robin: the bar above already shows where the usage stands against the mark). Tapping the
 * figure swaps line 3 for the pace-mark readout for 3 s (P8). Spent and unread rows draw
 * line 1, the bar and the words at half strength.
 */
@Composable
private fun SummaryRow(
    r: SummaryRowModel,
    accent: Color,
    name: String,
    plan: String?,
    mode: PaceText.Mode,
    use24h: Boolean,
    showOverPace: Boolean,
    onClick: () -> Unit,
) {
    val dark = appDark()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dim = if (SummaryRows.dimmed(r.group)) 0.5f else 1f
    val used = r.used
    val session = r.data?.session
    val sessionElapsed = elapsedPercent(session, Projection.SESSION_MS)
    // An unread account has no data to say it lacks a 5h: its ring is the bare track and "—".
    val ringState = if (r.data == null) GrammarRing.State.NORMAL else tileRingState(session, r.weekly)
    val ringFigure = if (ringState == GrammarRing.State.NOT_APPLICABLE) null
    else AppPace.figure(session?.percent, sessionElapsed, mode).first
    val ringTone = PaceText.vsPaceTone(session?.percent, sessionElapsed)
    val ringFigureColor = if (mode == PaceText.Mode.VS_PACE && ringTone != PaceText.VsTone.USAGE &&
        ringState == GrammarRing.State.NORMAL
    ) Grammar.vsColor(ringTone, dark, Grammar.ink(dark), Grammar.ink(dark)) else null
    // CCRM-104 (Pace Line Parity) D5: a press on the figure underlines its caption for 3 s.
    var pressed by remember(r.profile) { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(TAP_READOUT_MS)
            pressed = false
        }
    }
    val tapLabel = PaceText.caption(mode, r.used, r.elapsed)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 9.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GrammarRingBox(
            size = 48.dp,
            pct = session?.percent,
            elapsed = sessionElapsed,
            accent = accent,
            figure = ringFigure,
            description = ringDescription(ringState, ringFigure, mode, ringTone),
            state = ringState,
            figureColor = ringFigureColor,
            showOverPace = showOverPace,
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.alpha(dim), verticalAlignment = Alignment.CenterVertically) {
                // CCBG-60 (Summary Line Clip): mark, name and pill share every dp the figure
                // leaves; the name gives way first, and the pill stays beside it.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    ProviderMark(r.profile.provider, size = 18.dp, tint = accent)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        name,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    plan?.let {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            it,
                            fontSize = 11.sp,
                            color = muted,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.width(7.dp))
                // The figure in the grammar's type (rev D §1c), Vs pace signed in its colour
                // (CCRM-104 D1); Left keeps its word, as today. "—" unread.
                val (figure, word) = AppPace.figureSigned(used, r.elapsed, mode)
                Text(
                    buildAnnotatedString {
                        append(grammarFigure(figure))
                        if (word == "left") append(" left")
                    },
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold).grammarDigits(),
                    maxLines = 1,
                    color = grammarFigureColor(used, r.elapsed, mode, accent),
                    // TalkBack reads the caption as the figure's click action (the tap target below
                    // carries no semantics of its own).
                    modifier = Modifier.semantics {
                        if (tapLabel != null) onClick(label = tapLabel) { pressed = true; true }
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.alpha(dim)) {
                UsageBarLine(
                    percent = used,
                    fillColor = if (used == null) muted else Palette.barColor(used, accent, dark),
                    height = 10.dp,
                    elapsedPercent = r.elapsed.takeIf { used != null },
                    showOverPace = showOverPace,
                    dashedRoom = true,
                )
            }
            PaceCaption(mode, used, r.elapsed, underline = pressed)
            Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                val full = if (r.group == SummaryRank.Group.HELD) SummaryRows.heldWords(session, r.weekly, use24h)
                else SummaryRows.words(r.weekly, r.elapsed, use24h)
                val pace = if (r.group == SummaryRank.Group.HELD) null else SummaryRows.paceLine(r.weekly, use24h)
                if (pace != null && pace.forms.isNotEmpty()) {
                    // CCRM-104 D3: the pace line, its forms measured on the row's real width.
                    PaceLineText(
                        pace, 13.sp, MaterialTheme.colorScheme.onSurface, FontWeight.Normal,
                        modifier = Modifier.weight(1f).alpha(dim),
                    )
                } else {
                    // CCBG-60 (Summary Line Clip): the held line's "resets <day>" goes first under
                    // the measured width; keyed on the width, so a fold or unfold measures again.
                    BoxWithConstraints(Modifier.weight(1f)) {
                        var short by remember(full, maxWidth) { mutableStateOf(false) }
                        Text(
                            if (short) SummaryRows.withoutReset(full) else full,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (!short && it.hasVisualOverflow && SummaryRows.withoutReset(full) != full) short = true },
                            modifier = Modifier.alpha(dim),
                        )
                    }
                }
            }
        }
        // CCBG-56 (Summary Readout Tap): the figure's own target, 80 × 48 dp over the figure
        // and the bar's end, consumes the tap (CCRM-104 D5: the caption underlines; nothing when
        // there is no caption) so the row's tap — open the account — never takes it. A plain gesture, no
        // ripple and no node of its own for TalkBack (the figure carries the action). Only
        // while there is a readout (Fable F7): a spent, unread or unclocked row's figure opens
        // the account like the rest of the row, rather than being a dead zone.
        val currentTap by rememberUpdatedState(tapLabel)
        if (tapLabel != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 80.dp, height = 48.dp)
                    .pointerInput(Unit) { detectTapGestures { if (currentTap != null) pressed = true } },
            )
        }
        }
    }
}

/** S2, from six accounts: rank · name · bar · word · figure on one line. */
@Composable
private fun CompactSummaryRow(
    r: SummaryRowModel,
    rank: Int,
    accent: Color,
    mode: PaceText.Mode,
    use24h: Boolean,
    showOverPace: Boolean,
    onClick: () -> Unit,
) {
    val dark = appDark()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val used = r.used
    val word = SummaryRows.compactWord(r.group, r.weekly, r.elapsed, r.data?.session, use24h)
    val spanTone = SummaryRows.compactTone(r.group, r.weekly, use24h)
    val onInk = MaterialTheme.colorScheme.onSurface
    // CCRM-104 D5: no caption on a one-line row, so a press swaps the word slot for it for 3 s.
    val caption = PaceText.caption(mode, used, r.elapsed)
    var pressed by remember(r.profile) { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(TAP_READOUT_MS)
            pressed = false
        }
    }
    val wordColor = if (spanTone != null) Grammar.vsColor(spanTone, dark, onInk, onInk) else when (r.group) {
        SummaryRank.Group.HELD -> Palette.barColor(r.data?.session?.percent, accent, dark)
        SummaryRank.Group.ELIGIBLE -> MaterialTheme.colorScheme.onSurface
        else -> muted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp)
            .alpha(if (SummaryRows.dimmed(r.group)) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        RankBadge(rank, accent)
        Text(
            r.name,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp),
        )
        Box(Modifier.weight(1f)) {
            UsageBarLine(
                percent = used,
                fillColor = if (used == null) muted else Palette.barColor(used, accent, dark),
                height = 8.dp,
                elapsedPercent = r.elapsed.takeIf { used != null },
                showOverPace = showOverPace,
            )
        }
        val forms = PaceText.captionForms(mode, used, r.elapsed)
        if (pressed && caption != null) {
            val measurer = rememberTextMeasurer()
            val slot = with(LocalDensity.current) { 90.dp.roundToPx() }
            val shown = PaceText.fitText(forms) {
                measurer.measure(it, TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)).size.width <= slot
            } ?: caption
            SwapText(shown, 12.5.sp, Modifier.width(90.dp))
        } else {
            // Judge F2: the duration and its word are never cut (core §6.3) — step to 10 sp before any ellipsis.
            val wordMeasurer = rememberTextMeasurer()
            val wordSlot = with(LocalDensity.current) { 90.dp.roundToPx() }
            val wordWeight = if (spanTone != null) FontWeight.SemiBold else null
            val wordSize = if (spanTone != null &&
                wordMeasurer.measure(word, TextStyle(fontSize = 12.5.sp, fontWeight = wordWeight)).size.width > wordSlot
            ) 10.sp else 12.5.sp
            Text(
                word,
                fontSize = wordSize,
                fontWeight = wordWeight,
                color = wordColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(90.dp),
            )
        }
        val (figure, _) = AppPace.figureSigned(used, r.elapsed, mode)
        Text(
            grammarFigure(figure),
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold).grammarDigits(),
            textAlign = TextAlign.End,
            color = grammarFigureColor(used, r.elapsed, mode, accent),
            maxLines = 1,
            modifier = Modifier
                .width(40.dp)
                .clickable(enabled = caption != null, onClickLabel = caption) { pressed = true },
        )
    }
}

@Composable
private fun RankBadge(rank: Int, accent: Color) {
    Box(
        Modifier
            .size(17.dp)
            .clip(CircleShape)
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Text("$rank", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF121212))
    }
}

/** "Checked 1m ago ⟳" under the map: the stalest account's check; tapping refreshes all. */
@Composable
private fun SummaryStatus(repo: UsageRepository, profiles: List<Profile>, tick: Int) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val snaps = remember(profiles, tick, refreshing) { profiles.map { repo.snapshot(it) } }
    StatusLine(
        fetchedAt = snaps.minOfOrNull { it.fetchedAt } ?: 0L,
        lastAttemptAt = snaps.maxOfOrNull { it.lastAttemptAt } ?: 0L,
        lastOk = snaps.all { it.lastStatus == "OK" },
        refreshing = refreshing,
        now = remember(tick) { System.currentTimeMillis() },
    ) {
        scope.launch {
            refreshing = true
            for (p in profiles) repo.refreshNow(p, manual = true)
            refreshing = false
        }
    }
}
