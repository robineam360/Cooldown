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
// The layout sheet's row list is the cards the account actually reports ([present],
// i.e. `dataCards`) split into two groups — those not behind More, then a
// non-draggable divider slot, then those behind More. [cardRowIndex]/[cardRowCount]
// number that three-part list; [applyCardDrag] resolves a drag within it to the
// [CardLayout] mutation implied, reusing [CardLayout]'s own primitives rather than
// reimplementing their invariants.
//
// [present] defaults to every card — the Claude account the wireframe §9 frames draw.
// It matters for an account that doesn't report one of them (ChatGPT has no 5-hour
// window): that card gets no row, so it must not occupy one, and it cannot be the
// card the never-blank invariant is holding open (see [CardLayout.canFold]).

/**
 * [id]'s index in the layout sheet's row list ([present] cards not behind More, the
 * divider slot, then [present] cards behind More) — -1 if [id] isn't drawn at all.
 *
 * Counted off the two groups the sheet actually draws rather than off
 * [CardLayout.order] plus one for the divider: those agree only while every folded
 * card sits at the tail of `order` and every card is present, neither of which the
 * model enforces — and numbering two rows the same would give the sheet's drag and
 * its TalkBack actions the wrong row.
 */
fun cardRowIndex(
    layout: CardLayout,
    id: CardId,
    present: Set<CardId> = CardId.entries.toSet(),
): Int {
    if (id !in present) return -1
    val main = layout.order.filter { it in present && it !in layout.more }
    val inMain = main.indexOf(id)
    if (inMain >= 0) return inMain
    val inMore = layout.order.filter { it in present && it in layout.more }.indexOf(id)
    return if (inMore < 0) -1 else main.size + 1 + inMore
}

/** How many rows the layout sheet draws for [layout]: every [present] card, plus the divider. */
fun cardRowCount(layout: CardLayout, present: Set<CardId> = CardId.entries.toSet()): Int =
    layout.order.count { it in present } + 1

/**
 * Resolves a drag that has moved [id] to row [toRow] of [layout]'s row list (see
 * [cardRowIndex]) to the [CardLayout] it implies.
 *
 * Worked on the row list itself — the dragged row is lifted out and put back at
 * [toRow], and the groups are read off where it landed relative to the divider — so
 * landing on the divider's own row keeps going the direction the drag came from, and
 * a card dropped past a card already behind More lands after it. A fold that would
 * break the never-blank invariant ([CardLayout.canFold] over [present]) is a no-op:
 * [layout] itself comes back unchanged, the drop snapping back exactly where the
 * disabled-switch state documents the same rule. Also a no-op, same instance back,
 * when [id] isn't drawn or the row doesn't move.
 *
 * Cards outside [present] have no row, so they keep their stored place: the present
 * cards are permuted among the slots present cards already occupy in
 * [CardLayout.order].
 */
fun applyCardDrag(
    layout: CardLayout,
    id: CardId,
    toRow: Int,
    present: Set<CardId> = CardId.entries.toSet(),
): CardLayout {
    val from = cardRowIndex(layout, id, present)
    if (from < 0) return layout
    val target = toRow.coerceIn(0, cardRowCount(layout, present) - 1)
    if (target == from) return layout

    // null is the "Behind More" divider — a real slot in the list, which is what makes
    // dragging *onto* it mean "cross it", not "stall on it".
    val rows: List<CardId?> = buildList {
        addAll(layout.order.filter { it in present && it !in layout.more })
        add(null)
        addAll(layout.order.filter { it in present && it in layout.more })
    }
    val lifted = rows.filterNot { it == id }
    val landed = lifted.toMutableList().apply { add(target.coerceAtMost(size), id) }
    val willBeInMore = landed.indexOf(id) > landed.indexOf(null)
    if (willBeInMore && id !in layout.more && !CardLayout.canFold(layout, id, present)) return layout

    val more = if (willBeInMore) layout.more + id else layout.more - id
    val ordered = landed.filterNotNull()
    val order = layout.order.toMutableList()
    layout.order.withIndex()
        .filter { (_, card) -> card in present }
        .forEachIndexed { i, (slot, _) -> order[slot] = ordered[i] }

    val next = CardLayout.normalize(layout.copy(order = order, more = more))
    return if (next == layout) layout else next
}
