package com.robin.claudeusage

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
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
import com.robin.claudeusage.ui.PaceMap
import com.robin.claudeusage.ui.PaceMapEntry
import com.robin.claudeusage.ui.PaceMapGeometry
import com.robin.claudeusage.ui.PaceText
import com.robin.claudeusage.ui.Palette
import com.robin.claudeusage.ui.ProviderMark
import com.robin.claudeusage.ui.SummaryRows
import com.robin.claudeusage.ui.appDark
import com.robin.claudeusage.ui.elapsedPercent
import com.robin.claudeusage.ui.hasTwoColumns
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
    val rows = remember(profiles, tick) { summaryRows(repo, profiles) }
    val compact = SummaryRows.compact(profiles.size)
    val mapEntries = remember(rows, dark) {
        rows.mapIndexedNotNull { i, r -> r.mapEntry(i + 1, Palette.color(Palette.accentName(cache, r.profile), dark)) }
    }
    val list: @Composable () -> Unit = {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                CardHeader("Weekly", "most room first")
                Spacer(Modifier.height(6.dp))
                rows.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider()
                    val accent = Palette.color(Palette.accentName(cache, r.profile), dark)
                    if (compact) {
                        CompactSummaryRow(r, i + 1, accent, mode, use24h, showOverPace) { onOpen(r.profile) }
                    } else {
                        SummaryRow(r, accent, r.name, repo.plan(r.profile), mode, use24h, showOverPace) {
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
                MapCard(mapEntries, compact, maxWidth - 32.dp)
            }
        }
    } else {
        list()
        Spacer(Modifier.height(12.dp))
        MapCard(mapEntries, compact, 230.dp)
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
private fun summaryRows(repo: UsageRepository, profiles: List<Profile>): List<SummaryRowModel> {
    val now = System.currentTimeMillis()
    val cache = repo.cacheSettings()
    val read = profiles.associate { p ->
        val data = repo.snapshot(p).data
        val history: List<HistoryPoint> = repo.history().points(p)
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
    return SummaryRank.rank(read.values.map { it.second }).map { ranked ->
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
private fun MapCard(entries: List<PaceMapEntry>, numbered: Boolean, plotHeight: androidx.compose.ui.unit.Dp) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CardHeader("Pace map", "Weekly, where each account stands")
            Spacer(Modifier.height(8.dp))
            PaceMap(entries, numbered, Modifier.fillMaxWidth().height(plotHeight))
        }
    }
}

/**
 * The full row (C1): mark · name · plan pill · 5h companion · figure; the Weekly bar with
 * its neutral pace tick; the V1 caption; the pace pip and the words. Spent and unread
 * rows draw at half strength.
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
    val companion = SummaryRows.companion(r.group, r.data?.session, r.data?.session != null, mode, use24h)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 9.dp, bottom = 10.dp),
    ) {
        Row(Modifier.alpha(dim), verticalAlignment = Alignment.CenterVertically) {
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
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp),
                )
            }
            companion?.let {
                val loud = r.group == SummaryRank.Group.HELD
                Spacer(Modifier.width(7.dp))
                Text(
                    it,
                    fontSize = 12.sp,
                    maxLines = 1,
                    fontWeight = if (loud) FontWeight.Bold else FontWeight.Normal,
                    color = if (loud) Palette.barColor(r.data?.session?.percent, accent, dark) else muted,
                )
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(7.dp))
            if (used == null) {
                Text("—", fontSize = 17.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = muted)
            } else {
                // The Summary figure carries no "vs pace" word (vs-pace wireframe C2);
                // Left keeps its word, as everywhere.
                val (figure, word) = AppPace.figure(used, r.elapsed, mode)
                Text(
                    if (word == "left") "$figure left" else figure,
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = Palette.barColor(used, accent, dark),
                )
            }
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
        PaceCaption(mode, used, r.elapsed)
        Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            val pace = if (used != null && r.elapsed != null && !Pace.isSpent(used)) Pace.pace(used, r.elapsed) else null
            pace?.let {
                PacePip(it)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                SummaryRows.words(r.weekly, r.elapsed, use24h),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(dim),
            )
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
    val wordColor = when (r.group) {
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
        Text(
            word,
            fontSize = 12.5.sp,
            color = wordColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(90.dp),
        )
        val (figure, _) = AppPace.figure(used, r.elapsed, mode)
        Text(
            figure,
            fontSize = 15.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            color = if (used == null) muted else Palette.barColor(used, accent, dark),
            maxLines = 1,
            modifier = Modifier.width(40.dp),
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

/**
 * The pace pip (rev B §2, line 3): a 40 dp scale with a centre mark, its dot at the
 * account's Pace — left = room, right = over. In-app only.
 */
@Composable
private fun PacePip(pace: Double) {
    val ink = MaterialTheme.colorScheme.onSurface
    val card = MaterialTheme.colorScheme.surfaceContainer
    val tone = when (SummaryRows.pipTone(pace)) {
        SummaryRows.PipTone.ROOM -> Color(0xFF8FBF9A)
        SummaryRows.PipTone.ON_PACE -> Color(0xFFBDB8B3)
        SummaryRows.PipTone.OVER -> Color(0xFFF2B45A)
        SummaryRows.PipTone.NEARLY_OUT -> Color(0xFFFF6B5B)
    }
    Canvas(Modifier.size(width = 40.dp, height = 12.dp)) {
        val u = size.width / 40f
        drawLine(ink.copy(alpha = 0.35f), Offset(3 * u, 6 * u), Offset(37 * u, 6 * u), 1.5f * u)
        drawLine(ink.copy(alpha = 0.6f), Offset(20 * u, 1.5f * u), Offset(20 * u, 10.5f * u), 1.5f * u)
        val c = Offset(size.width * SummaryRows.pipFraction(pace), 6 * u)
        drawCircle(tone, 4 * u, c)
        drawCircle(card, 4 * u, c, style = Stroke(1.5f * u))
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
