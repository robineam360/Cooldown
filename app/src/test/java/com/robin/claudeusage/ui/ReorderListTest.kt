package com.robin.claudeusage.ui

import com.robin.claudeusage.data.CardId
import com.robin.claudeusage.data.CardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCRM-72 (Main Screen Redesign): [ReorderDragState.dragBy]'s quantised-shift
 * contract, and [applyCardDrag]'s resolution of a layout-sheet drag to a
 * [CardLayout] mutation — modelled on the `move` block of `CardLayoutTest`/
 * `ProfileRegistryTest`. `DEFAULT` is `[SESSION, WEEKLY, CREDITS]`, all shown,
 * none behind More, so its row list is `[SESSION, WEEKLY, CREDITS, divider]` —
 * rows 0..3.
 */
class ReorderListTest {

    private val default get() = CardLayout.DEFAULT

    // --- cardRowIndex / cardRowCount ---

    @Test
    fun `row index of a main-group card is its order index`() {
        assertEquals(0, cardRowIndex(default, CardId.SESSION))
        assertEquals(1, cardRowIndex(default, CardId.WEEKLY))
        assertEquals(2, cardRowIndex(default, CardId.CREDITS))
    }

    @Test
    fun `row index of a more-group card is shifted past the divider`() {
        val folded = CardLayout.toMore(default, CardId.CREDITS)
        // order unchanged [S, W, C]; C is now behind the divider at row 3.
        assertEquals(3, cardRowIndex(folded, CardId.CREDITS))
    }

    @Test
    fun `row index follows the groups the sheet draws, not the raw order`() {
        // A folded card ahead of a shown one in `order` — not something a drag can
        // leave behind, but nothing in the model forbids it, and the sheet draws
        // [5-hour, Usage credits, divider, 7-day] whatever `order` says.
        val odd = CardLayout(
            order = listOf(CardId.SESSION, CardId.WEEKLY, CardId.CREDITS),
            hidden = emptySet(),
            more = setOf(CardId.WEEKLY),
        )
        assertEquals(0, cardRowIndex(odd, CardId.SESSION))
        assertEquals(1, cardRowIndex(odd, CardId.CREDITS))
        assertEquals(3, cardRowIndex(odd, CardId.WEEKLY))
    }

    @Test
    fun `row index is -1 for a card not in the layout`() {
        val trimmed = default.copy(order = listOf(CardId.SESSION, CardId.WEEKLY))
        assertEquals(-1, cardRowIndex(trimmed, CardId.CREDITS))
    }

    @Test
    fun `row count is every card plus one divider row`() {
        assertEquals(4, cardRowCount(default))
    }

    // --- present: the cards the account actually reports (ChatGPT has no 5-hour window) ---

    /** A ChatGPT-shaped account: a 7-day window and credits, no 5-hour window. */
    private val chatgpt = setOf(CardId.WEEKLY, CardId.CREDITS)

    @Test
    fun `a card the account doesn't report takes no row`() {
        assertEquals(-1, cardRowIndex(default, CardId.SESSION, chatgpt))
        assertEquals(0, cardRowIndex(default, CardId.WEEKLY, chatgpt))
        assertEquals(1, cardRowIndex(default, CardId.CREDITS, chatgpt))
        assertEquals(3, cardRowCount(default, chatgpt))
    }

    @Test
    fun `an absent card can't be the one holding the screen open`() {
        // CCBG territory: with SESSION counted, hiding both real cards was allowed and
        // left the main screen as nothing but the status line.
        val creditsHidden = CardLayout.hide(default, CardId.CREDITS)
        assertFalse(CardLayout.canHide(creditsHidden, CardId.WEEKLY, chatgpt))
        assertFalse(CardLayout.canFold(creditsHidden, CardId.WEEKLY, chatgpt))
        // The same layout on a Claude account, where the 5-hour card is real, is fine.
        assertTrue(CardLayout.canHide(creditsHidden, CardId.WEEKLY))
        assertTrue(CardLayout.canFold(creditsHidden, CardId.WEEKLY))
    }

    @Test
    fun `folding the last present card is a no-op, same instance back`() {
        val creditsFolded = CardLayout.toMore(default, CardId.CREDITS)
        // Rows for ChatGPT: [7-day, divider, Usage credits] — dragging the 7-day card
        // onto the divider would leave nothing above it.
        assertSame(creditsFolded, applyCardDrag(creditsFolded, CardId.WEEKLY, 1, chatgpt))
    }

    @Test
    fun `a present card folds past a card the account doesn't report`() {
        val folded = applyCardDrag(default, CardId.CREDITS, 2, chatgpt)
        assertEquals(setOf(CardId.CREDITS), folded.more)
        // SESSION isn't drawn, so it keeps its stored slot rather than being shuffled.
        assertEquals(CardId.SESSION, folded.order.first())
        // Rows are now [7-day, divider, Usage credits].
        assertEquals(0, cardRowIndex(folded, CardId.WEEKLY, chatgpt))
        assertEquals(2, cardRowIndex(folded, CardId.CREDITS, chatgpt))
    }

    @Test
    fun `reordering two present cards leaves an absent one where it was`() {
        val moved = applyCardDrag(default, CardId.CREDITS, 0, chatgpt)
        assertEquals(listOf(CardId.SESSION, CardId.CREDITS, CardId.WEEKLY), moved.order)
    }

    // --- applyCardDrag: within-group reorder ---

    @Test
    fun `dragging a main card earlier reorders within main`() {
        val result = applyCardDrag(default, CardId.CREDITS, 0)
        assertEquals(listOf(CardId.CREDITS, CardId.SESSION, CardId.WEEKLY), result.order)
        assertTrue(result.more.isEmpty())
    }

    @Test
    fun `dragging a main card later reorders within main`() {
        val result = applyCardDrag(default, CardId.SESSION, 1)
        assertEquals(listOf(CardId.WEEKLY, CardId.SESSION, CardId.CREDITS), result.order)
    }

    @Test
    fun `dragging to the row it's already at is a no-op, same instance back`() {
        val result = applyCardDrag(default, CardId.WEEKLY, 1)
        assertTrue(result === default)
    }

    @Test
    fun `a card not in the layout is a no-op, same instance back`() {
        val trimmed = default.copy(order = listOf(CardId.SESSION, CardId.WEEKLY))
        val result = applyCardDrag(trimmed, CardId.CREDITS, 0)
        assertTrue(result === trimmed)
    }

    // --- applyCardDrag: crossing the divider ---

    @Test
    fun `dragging the last main card onto the divider folds it behind More`() {
        // default rows: [S(0), W(1), C(2), divider(3)]. Drag C to the divider.
        val result = applyCardDrag(default, CardId.CREDITS, 3)
        assertTrue(CardId.CREDITS in result.more)
        assertEquals(listOf(CardId.SESSION, CardId.WEEKLY, CardId.CREDITS), result.order)
    }

    @Test
    fun `dragging a card past the divider into a non-empty More lands after what's already there`() {
        val folded = CardLayout.toMore(default, CardId.CREDITS) // order [S,W,C], more {C}
        // rows: [S(0), W(1), divider(2), C(3)]. Drag WEEKLY down onto CREDITS's row.
        val result = applyCardDrag(folded, CardId.WEEKLY, 3)
        assertTrue(CardId.WEEKLY in result.more)
        assertTrue(CardId.CREDITS in result.more)
        assertEquals(listOf(CardId.SESSION, CardId.CREDITS, CardId.WEEKLY), result.order)
    }

    @Test
    fun `dragging a more-group card onto the divider unfolds it`() {
        val folded = CardLayout.toMore(default, CardId.CREDITS)
        // rows now [S(0), W(1), divider(2), C(3)]. Drag C up onto the divider.
        val result = applyCardDrag(folded, CardId.CREDITS, 2)
        assertFalse(CardId.CREDITS in result.more)
    }

    @Test
    fun `folding the last shown card is a no-op, same instance back`() {
        val onlySessionShown = CardLayout(
            order = CardId.entries,
            hidden = emptySet(),
            more = setOf(CardId.WEEKLY, CardId.CREDITS),
        )
        // rows: [S(0), divider(1), W(2), C(3)]. Dragging SESSION onto the divider
        // would fold the only card left above More.
        val result = applyCardDrag(onlySessionShown, CardId.SESSION, 1)
        assertTrue(result === onlySessionShown)
    }

    @Test
    fun `a hidden card can still be reordered within its group`() {
        val hidden = CardLayout.hide(default, CardId.WEEKLY)
        val result = applyCardDrag(hidden, CardId.WEEKLY, 0)
        assertEquals(listOf(CardId.WEEKLY, CardId.SESSION, CardId.CREDITS), result.order)
        assertTrue(CardId.WEEKLY in result.hidden)
    }

    @Test
    fun `dragging clamps a row past the end of the list to the last row`() {
        // 99 clamps to row 3 (the divider, default's last row) — same as dragging
        // SESSION straight onto it: folds behind More, landing at the end.
        val result = applyCardDrag(default, CardId.SESSION, 99)
        assertTrue(CardId.SESSION in result.more)
        assertEquals(listOf(CardId.WEEKLY, CardId.CREDITS, CardId.SESSION), result.order)
    }

    // --- ReorderDragState.dragBy ---

    @Test
    fun `dragBy does nothing before a full row is crossed`() {
        val state = ReorderDragState()
        state.start("row")
        var calls = 0
        state.dragBy(10f, rowHeightPx = 56f) { calls++; true }
        assertEquals(0, calls)
        assertEquals(10f, state.offsetFor("row"))
    }

    @Test
    fun `dragBy applies one shift per row crossed and compensates the offset`() {
        val state = ReorderDragState()
        state.start("row")
        var appliedShift = 0
        state.dragBy(60f, rowHeightPx = 56f) { shift -> appliedShift = shift; true }
        assertEquals(1, appliedShift)
        // 60px of drag, one row (56px) consumed: ~4px of residual offset remains.
        assertEquals(4f, state.offsetFor("row"), 0.01f)
    }

    @Test
    fun `dragBy keeps accumulating when the shift is refused`() {
        val state = ReorderDragState()
        state.start("row")
        state.dragBy(60f, rowHeightPx = 56f) { false }
        // Refused: no compensation, the full accumulated offset is still there.
        assertEquals(60f, state.offsetFor("row"), 0.01f)
    }

    @Test
    fun `offsetFor is zero for a row that isn't being dragged`() {
        val state = ReorderDragState()
        state.start("row")
        state.dragBy(60f, rowHeightPx = 56f) { true }
        assertEquals(0f, state.offsetFor("other"))
    }
}
