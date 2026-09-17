package com.robin.claudeusage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.robin.claudeusage.data.CardId
import com.robin.claudeusage.data.CardLayout
import kotlin.math.roundToInt

/**
 * The drag-to-reorder mechanics shared by every drag list in the app — CCRM-71
 * (Account Order)'s `ReorderAccountsSheet`/`ReorderRow` first, CCRM-72 (Main Screen
 * Redesign)'s `LayoutSheet` second — extracted rather than copied a second time.
 * Deliberately not a third-party "reorderable list" dependency (there isn't one in
 * this project, and CCRM-71 says not to add one): a plain
 * [androidx.compose.ui.input.pointer.pointerInput]/[detectDragGestures] on a 48dp
 * handle, converting the accumulated vertical drag into a row-height-quantised
 * shift, applied live on every boundary crossing rather than only on release.
 *
 * What's shared: the drag/offset state ([ReorderDragState]), the quantisation math
 * ([ReorderDragState.dragBy]), the handle's gesture box and glyph ([DragHandle],
 * [DragHandleIcon]), the dragging row's visual treatment
 * ([Modifier.reorderRowTransform]) and its TalkBack custom actions
 * ([Modifier.reorderAccessibility]). What's *not* shared, because it's specific to
 * what's being reordered: the row's own content, and what a boundary crossing
 * means (`ProfileRegistry.move` for accounts; fold/unfold plus reorder for the
 * layout sheet's cards, below). `animateItem()` on siblings also stays with each
 * caller — it's a `LazyItemScope` extension, applied through the `modifier`
 * parameter every row here already threads through, the same way `ReorderRow`
 * always has.
 */
class ReorderDragState {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var dragOffsetPx by mutableFloatStateOf(0f)
        private set

    fun isDragging(key: Any): Boolean = draggingKey == key

    /** The live pixel offset for [key]'s row — zero for every row not being dragged. */
    fun offsetFor(key: Any): Float = if (draggingKey == key) dragOffsetPx else 0f

    fun start(key: Any) {
        draggingKey = key
        dragOffsetPx = 0f
    }

    fun end() {
        draggingKey = null
        dragOffsetPx = 0f
    }

    /**
     * Accumulates [deltaY] and, once it crosses a full [rowHeightPx] boundary, asks
     * [onShift] to apply that many rows of movement — positive is downward, same
     * sign as the drag. The accumulator is only relieved by that many rows when
     * [onShift] reports `true`; when it reports `false` (the shift was blocked —
     * clamped at a list edge, or refused by an invariant) the offset keeps
     * building, so escaping the block takes proportionally more drag distance
     * instead of needing to be re-dragged from zero. This is exactly the
     * compensation `ReorderAccountsSheet`'s inline drag math did before extraction.
     */
    fun dragBy(deltaY: Float, rowHeightPx: Float, onShift: (rows: Int) -> Boolean) {
        dragOffsetPx += deltaY
        val shift = (dragOffsetPx / rowHeightPx).roundToInt()
        if (shift != 0 && onShift(shift)) {
            dragOffsetPx -= shift * rowHeightPx
        }
    }
}

@Composable
fun rememberReorderDragState(): ReorderDragState = remember { ReorderDragState() }

/**
 * The dragging row's visual treatment — raised above its siblings, nudged by the
 * live offset, and given a faint card behind it — matching `ReorderRow`'s
 * unchanged look. A no-op [Modifier] while [isDragging] is false.
 */
@Composable
fun Modifier.reorderRowTransform(isDragging: Boolean, dragOffsetPx: Float): Modifier {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    return this
        .zIndex(if (isDragging) 1f else 0f)
        .then(
            if (isDragging) {
                Modifier
                    .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                    .scale(1.02f)
                    .shadow(2.dp, RoundedCornerShape(12.dp))
                    .background(surface, RoundedCornerShape(12.dp))
            } else {
                Modifier
            }
        )
}

/**
 * The row's TalkBack story: one merged stop with `Move up`/`Move down` custom
 * actions in place of the drag gesture, which a screen reader can't perform.
 * Correct for a genuinely one-dimensional list (the account-order sheet, the
 * layout sheet) rather than a 2D grid.
 */
fun Modifier.reorderAccessibility(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
): Modifier = this.semantics(mergeDescendants = true) {
    customActions = listOfNotNull(
        if (canMoveUp) CustomAccessibilityAction("Move up") { onMoveUp(); true } else null,
        if (canMoveDown) CustomAccessibilityAction("Move down") { onMoveDown(); true } else null,
    )
}

/**
 * The 48dp drag-handle target itself: a [androidx.compose.ui.input.pointer.pointerInput]
 * scoped to this box alone (so a swipe anywhere else on the row falls through to
 * the sheet's own swipe-to-dismiss), wrapping [DragHandleIcon]. [dragKey] is the
 * pointerInput key — a stable per-row identity (a profile key, a [CardId]) — and,
 * because the gesture is deliberately never restarted while that key holds, a
 * caller whose [onDragBy] reads live external state (the current list, the current
 * [CardLayout]) needs its own [androidx.compose.runtime.rememberUpdatedState]
 * around that state, the same way `ReorderAccountsSheet` already reads
 * `liveProfiles`.
 */
@Composable
fun DragHandle(
    dragKey: Any,
    tint: Color,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .pointerInput(dragKey) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragBy(dragAmount.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        DragHandleIcon(tint = tint)
    }
}

/** The drag glyph — three short horizontal bars, drawn rather than pulled from an
 * icon set, since `material-icons-core` (this app's only icon dependency) doesn't
 * carry one. */
@Composable
fun DragHandleIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        val left = size.width * 0.2f
        val right = size.width * 0.8f
        for (fraction in listOf(0.3125f, 0.5f, 0.6875f)) {
            val y = size.height * fraction
            drawLine(
                color = tint,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

// --- CCRM-72 (Main Screen Redesign): the layout sheet's drag-to-model resolution ---
//
// The layout sheet's row list is `CardLayout.order` split into two groups — cards
// not behind More, then a non-draggable divider slot, then cards behind More —
// which is also exactly how `CardLayout.order` is maintained in practice (nothing
// else interleaves it). [cardRowIndex]/[cardRowCount] number that three-part list;
// [applyCardDrag] resolves a drag within it to the [CardLayout] mutation implied,
// reusing [CardLayout]'s own primitives rather than reimplementing their
// invariants.

/**
 * [id]'s index in the layout sheet's row list (cards not behind More, the divider
 * slot, then cards behind More) — -1 if [id] isn't in [layout] at all.
 */
fun cardRowIndex(layout: CardLayout, id: CardId): Int {
    val absolute = layout.order.indexOf(id)
    if (absolute < 0) return -1
    return if (id in layout.more) absolute + 1 else absolute
}

/** How many rows the layout sheet draws for [layout]: every card, plus the divider. */
fun cardRowCount(layout: CardLayout): Int = layout.order.size + 1

/**
 * Resolves a drag that has moved [id] to row [toRow] of [layout]'s row list (see
 * [cardRowIndex]) to the [CardLayout] it implies. Landing on the divider's own row
 * keeps going the direction the drag came from, so a card dragged straight at the
 * divider from either side crosses it rather than stalling. Landing on the same
 * side [id] started on only reorders ([CardLayout.move]); crossing sides folds or
 * unfolds first ([CardLayout.toMore]/[CardLayout.toMain]) so the group changes
 * before the order within it does. A fold that would break the never-blank
 * invariant ([CardLayout.canFold] false) is a no-op — [layout] itself comes back
 * unchanged, the drop snapping back exactly where the disabled-switch state
 * documents the same rule. Also a no-op, same instance back, when [id] isn't in
 * [layout] or the row doesn't move.
 */
fun applyCardDrag(layout: CardLayout, id: CardId, toRow: Int): CardLayout {
    val from = cardRowIndex(layout, id)
    if (from < 0) return layout
    val mainCount = layout.order.count { it !in layout.more }
    val target = toRow.coerceIn(0, cardRowCount(layout) - 1)
    if (target == from) return layout

    val wasInMore = id in layout.more
    val willBeInMore = when {
        target > mainCount -> true
        target < mainCount -> false
        else -> target >= from // hit the divider itself: keep the drag's own direction
    }
    if (willBeInMore && !wasInMore && !CardLayout.canFold(layout, id)) return layout

    var next = layout
    if (willBeInMore && !wasInMore) next = CardLayout.toMore(next, id)
    else if (!willBeInMore && wasInMore) next = CardLayout.toMain(next, id)

    // toMore/toMain never touch `order`, so it's still in the original numbering
    // `target` was computed against — just skip the one divider slot.
    val absoluteIndex = target - (if (willBeInMore) 1 else 0)
    return CardLayout.move(next, id, absoluteIndex)
}
