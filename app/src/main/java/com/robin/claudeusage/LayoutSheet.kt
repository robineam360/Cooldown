package com.robin.claudeusage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robin.claudeusage.data.CardId
import com.robin.claudeusage.data.CardLayout
import com.robin.claudeusage.data.Profile
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.ui.ContentMaxWidth
import com.robin.claudeusage.ui.DragHandle
import com.robin.claudeusage.ui.Palette
import com.robin.claudeusage.ui.appDark
import com.robin.claudeusage.ui.applyCardDrag
import com.robin.claudeusage.ui.cardRowCount
import com.robin.claudeusage.ui.cardRowIndex
import com.robin.claudeusage.ui.ReorderDragState
import com.robin.claudeusage.ui.reorderAccessibility
import com.robin.claudeusage.ui.reorderRowTransform
import com.robin.claudeusage.ui.rememberReorderDragState

/**
 * CCRM-72 (Main Screen Redesign) / CCRM-25 (Card Layout) / CCRM-35 (Layout Reset): the
 * ⋮ → "Main screen layout" bottom sheet — one row per card in [profile]'s
 * [CardLayout], each with a show/hide switch and a drag handle, a "Behind More"
 * divider a card can be dragged under, and a Reset. Wireframe §9
 * (`design/2026-09-17-main-screen-redesign.html`), states a–e.
 *
 * The drag mechanics (offset state, quantisation, the handle, the TalkBack
 * actions, the dragging-row visuals) are `ui/ReorderList.kt`'s, shared with
 * `SettingsScreen.kt`'s `ReorderAccountsSheet`; what's specific to this sheet is
 * [applyCardDrag] — resolving a drag across the divider to a fold/unfold plus
 * reorder — and the switch, which is a plain [CardLayout.hide]/[CardLayout.show]
 * gated on [CardLayout.canHide], independent of a card's position or fold state.
 *
 * Every change (switch, drag, or Reset) applies immediately via
 * [UsageCache.setLayout] and calls [onChanged] — there is no confirm besides
 * Reset's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutSheet(
    profile: Profile,
    cache: UsageCache,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    var layout by remember(profile) { mutableStateOf(cache.layout(profile)) }
    var showReset by remember { mutableStateOf(false) }
    val dark = appDark()
    val accent = Palette.color(Palette.accentName(cache, profile), dark)

    fun apply(next: CardLayout) {
        if (next !== layout) {
            layout = next
            cache.setLayout(profile, next)
            onChanged()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                Text(
                    "Main screen layout",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Show, hide and reorder the cards on this account's screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))

                val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
                val dragState = rememberReorderDragState()
                val liveLayout by rememberUpdatedState(layout)

                val mainIds = layout.order.filter { it !in layout.more }
                val moreIds = layout.order.filter { it in layout.more }

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(mainIds, key = { it.key }) { id ->
                        CardLayoutRow(
                            id = id,
                            layout = layout,
                            dragState = dragState,
                            rowHeightPx = rowHeightPx,
                            liveLayout = { liveLayout },
                            onApply = ::apply,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "__divider__") {
                        BehindMoreDivider(Modifier.animateItem())
                    }
                    items(moreIds, key = { it.key }) { id ->
                        CardLayoutRow(
                            id = id,
                            layout = layout,
                            dragState = dragState,
                            rowHeightPx = rowHeightPx,
                            liveLayout = { liveLayout },
                            onApply = ::apply,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { showReset = true }) {
                        Text("Reset layout", color = accent)
                    }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }

    if (showReset) {
        ResetLayoutDialog(
            accent = accent,
            onDismiss = { showReset = false },
            onConfirm = {
                apply(CardLayout.DEFAULT)
                showReset = false
            },
        )
    }
}

/**
 * One row: leading glyph, label, the show/hide switch ([CardLayout.hide]/[show],
 * gated on [CardLayout.canHide]) and the drag handle ([applyCardDrag]). Hidden
 * cards render at 0.6 alpha but keep their handle — they can still be reordered.
 */
@Composable
private fun CardLayoutRow(
    id: CardId,
    layout: CardLayout,
    dragState: ReorderDragState,
    rowHeightPx: Float,
    liveLayout: () -> CardLayout,
    onApply: (CardLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hidden = id in layout.hidden
    val checked = !hidden
    val switchEnabled = if (checked) CardLayout.canHide(layout, id) else true
    val isDragging = dragState.isDragging(id)
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val row = cardRowIndex(layout, id)

    Column(modifier = modifier.reorderRowTransform(isDragging, dragState.offsetFor(id))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (hidden) 0.6f else 1f)
                .height(56.dp)
                .padding(horizontal = 16.dp)
                .reorderAccessibility(
                    canMoveUp = row > 0,
                    canMoveDown = row < cardRowCount(layout) - 1,
                    onMoveUp = { onApply(applyCardDrag(layout, id, row - 1)) },
                    onMoveDown = { onApply(applyCardDrag(layout, id, row + 1)) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardGlyph(id, tint)
            Spacer(Modifier.width(14.dp))
            Text(
                cardLabel(id),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                enabled = switchEnabled,
                onCheckedChange = { isChecked ->
                    onApply(if (isChecked) CardLayout.show(layout, id) else CardLayout.hide(layout, id))
                },
            )
            DragHandle(
                dragKey = id,
                tint = tint,
                onDragStart = { dragState.start(id) },
                onDragBy = { deltaY ->
                    dragState.dragBy(deltaY, rowHeightPx) { shift ->
                        val current = liveLayout()
                        val from = cardRowIndex(current, id)
                        val next = if (from < 0) current else applyCardDrag(current, id, from + shift)
                        if (next !== current) {
                            onApply(next)
                            true
                        } else {
                            false
                        }
                    }
                },
                onDragEnd = { dragState.end() },
            )
        }
        if (!switchEnabled) {
            Text(
                "At least one card must stay visible above More.",
                fontSize = 11.5.sp,
                color = Palette.warn(appDark()),
                modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 6.dp),
            )
        }
    }
}

/** "5-hour window" / "7-day window" / "Usage credits". */
private fun cardLabel(id: CardId): String = when (id) {
    CardId.SESSION -> "5-hour window"
    CardId.WEEKLY -> "7-day window"
    CardId.CREDITS -> "Usage credits"
}

/**
 * The row's leading glyph: the calendar mark for the two windows, and a small
 * hand-drawn coin for credits — `material-icons-core` (this app's only icon
 * dependency) has neither a currency nor a credits icon, the same gap
 * `DragHandleIcon` already works around.
 */
@Composable
private fun CardGlyph(id: CardId, tint: Color) {
    when (id) {
        CardId.SESSION, CardId.WEEKLY ->
            Icon(Icons.Filled.DateRange, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        CardId.CREDITS -> CreditsGlyphIcon(tint = tint)
    }
}

@Composable
private fun CreditsGlyphIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        drawCircle(
            color = tint,
            radius = size.minDimension / 2f - stroke,
            style = Stroke(width = stroke),
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.32f, size.height / 2f),
            end = Offset(size.width * 0.68f, size.height / 2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** The non-draggable "BEHIND MORE" row a card can be dragged under or above. */
@Composable
private fun BehindMoreDivider(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashedLine(color, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            "BEHIND MORE",
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.85f),
        )
        Spacer(Modifier.width(8.dp))
        DashedLine(color, Modifier.weight(1f))
    }
}

@Composable
private fun DashedLine(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(1.dp)) {
        drawLine(
            color = color.copy(alpha = 0.35f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
        )
    }
}

/**
 * Reset confirm — the `RemoveAccountDialog` shape, but the accent-coloured
 * confirm rather than error red: a layout reset is reversible where removing an
 * account is not.
 */
@Composable
private fun ResetLayoutDialog(
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset layout?") },
        text = {
            Text(
                "Shows every card again, in the default order (5-hour, 7-day, Usage " +
                    "credits), for this account only. Doesn't touch its history or settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Reset layout", color = accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
