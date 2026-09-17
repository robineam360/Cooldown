package com.robin.claudeusage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCRM-25 (Card Layout) / CCRM-35 (Layout Reset), built under CCRM-72 (Main Screen
 * Redesign): the layout sheet's rules, exercised against [CardLayout.Companion]'s pure
 * functions — modelled on the `move` block of `ProfileRegistryTest`.
 */
class CardLayoutTest {

    private val default get() = CardLayout.DEFAULT

    // --- move ---

    @Test
    fun `move up brings a later card earlier`() {
        val result = CardLayout.move(default, CardId.CREDITS, 0)
        assertEquals(listOf(CardId.CREDITS, CardId.SESSION, CardId.WEEKLY), result.order)
    }

    @Test
    fun `move down sends an earlier card later`() {
        val result = CardLayout.move(default, CardId.SESSION, 1)
        assertEquals(listOf(CardId.WEEKLY, CardId.SESSION, CardId.CREDITS), result.order)
    }

    @Test
    fun `move to index 0 goes to the very front`() {
        val result = CardLayout.move(default, CardId.WEEKLY, 0)
        assertEquals(listOf(CardId.WEEKLY, CardId.SESSION, CardId.CREDITS), result.order)
    }

    @Test
    fun `move to the last index goes to the very end`() {
        val result = CardLayout.move(default, CardId.SESSION, default.order.lastIndex)
        assertEquals(listOf(CardId.WEEKLY, CardId.CREDITS, CardId.SESSION), result.order)
    }

    @Test
    fun `move clamps a negative index to the front`() {
        val result = CardLayout.move(default, CardId.CREDITS, -5)
        assertEquals(listOf(CardId.CREDITS, CardId.SESSION, CardId.WEEKLY), result.order)
    }

    @Test
    fun `move clamps an index past the end to the last slot`() {
        val result = CardLayout.move(default, CardId.SESSION, 99)
        assertEquals(listOf(CardId.WEEKLY, CardId.CREDITS, CardId.SESSION), result.order)
    }

    @Test
    fun `move ignores a card not in order, same instance back`() {
        val trimmed = default.copy(order = listOf(CardId.SESSION, CardId.WEEKLY))
        val result = CardLayout.move(trimmed, CardId.CREDITS, 0)
        assertTrue(result === trimmed)
    }

    @Test
    fun `move to the index it's already at is a no-op, same instance back`() {
        val result = CardLayout.move(default, CardId.WEEKLY, 1)
        assertTrue(result === default)
    }

    // --- hide / show ---

    @Test
    fun `hide adds the card to hidden`() {
        val result = CardLayout.hide(default, CardId.CREDITS)
        assertTrue(CardId.CREDITS in result.hidden)
    }

    @Test
    fun `hide is a no-op, same instance back, when already hidden`() {
        val hidden = CardLayout.hide(default, CardId.CREDITS)
        val result = CardLayout.hide(hidden, CardId.CREDITS)
        assertTrue(result === hidden)
    }

    @Test
    fun `show removes the card from hidden`() {
        val hidden = CardLayout.hide(default, CardId.CREDITS)
        val result = CardLayout.show(hidden, CardId.CREDITS)
        assertFalse(CardId.CREDITS in result.hidden)
    }

    @Test
    fun `show is a no-op, same instance back, when not hidden`() {
        val result = CardLayout.show(default, CardId.CREDITS)
        assertTrue(result === default)
    }

    @Test
    fun `hiding a card also drops it from more`() {
        val inMore = CardLayout.toMore(default, CardId.CREDITS)
        val result = CardLayout.hide(inMore, CardId.CREDITS)
        assertTrue(CardId.CREDITS in result.hidden)
        assertFalse(CardId.CREDITS in result.more)
    }

    // --- toMore / toMain ---

    @Test
    fun `toMore folds the card behind More`() {
        val result = CardLayout.toMore(default, CardId.CREDITS)
        assertTrue(CardId.CREDITS in result.more)
    }

    @Test
    fun `toMore is a no-op, same instance back, when already behind More`() {
        val folded = CardLayout.toMore(default, CardId.CREDITS)
        val result = CardLayout.toMore(folded, CardId.CREDITS)
        assertTrue(result === folded)
    }

    @Test
    fun `toMain unfolds the card from behind More`() {
        val folded = CardLayout.toMore(default, CardId.CREDITS)
        val result = CardLayout.toMain(folded, CardId.CREDITS)
        assertFalse(CardId.CREDITS in result.more)
    }

    @Test
    fun `toMain is a no-op, same instance back, when not behind More`() {
        val result = CardLayout.toMain(default, CardId.CREDITS)
        assertTrue(result === default)
    }

    // --- normalize ---

    @Test
    fun `normalize drops an id not in CardId entries`() {
        val decoded = CardLayout.decode("""{"o":["session","weekly","bogus"],"h":[],"m":[]}""")
        assertEquals(listOf(CardId.SESSION, CardId.WEEKLY, CardId.CREDITS), decoded.order)
    }

    @Test
    fun `normalize appends a missing id at the end of order`() {
        val partial = CardLayout(order = listOf(CardId.WEEKLY, CardId.SESSION), hidden = emptySet(), more = emptySet())
        val result = CardLayout.normalize(partial)
        assertEquals(listOf(CardId.WEEKLY, CardId.SESSION, CardId.CREDITS), result.order)
    }

    @Test
    fun `normalize resolves a card that's both hidden and behind More by dropping it from More`() {
        val overlapping = CardLayout(
            order = CardId.entries,
            hidden = setOf(CardId.CREDITS),
            more = setOf(CardId.CREDITS, CardId.WEEKLY),
        )
        val result = CardLayout.normalize(overlapping)
        assertTrue(CardId.CREDITS in result.hidden)
        assertFalse(CardId.CREDITS in result.more)
        assertTrue(CardId.WEEKLY in result.more)
    }

    @Test
    fun `normalize restores the first card when the invariant is violated`() {
        val allHidden = CardLayout(order = CardId.entries, hidden = CardId.entries.toSet(), more = emptySet())
        val result = CardLayout.normalize(allHidden)
        assertFalse(CardId.SESSION in result.hidden)
        assertTrue(CardId.WEEKLY in result.hidden)
        assertTrue(CardId.CREDITS in result.hidden)
    }

    @Test
    fun `normalize restores the first card when everything is hidden or folded`() {
        val mixed = CardLayout(
            order = CardId.entries,
            hidden = setOf(CardId.SESSION),
            more = setOf(CardId.WEEKLY, CardId.CREDITS),
        )
        val result = CardLayout.normalize(mixed)
        // SESSION is first in order, so it's the one rescued.
        assertFalse(CardId.SESSION in result.hidden)
        assertFalse(CardId.SESSION in result.more)
        assertTrue(result.order.any { it !in result.hidden && it !in result.more })
    }

    @Test
    fun `DEFAULT normalizes to itself, same instance`() {
        val result = CardLayout.normalize(default)
        assertTrue(result === default)
    }

    @Test
    fun `normalize is a no-op, same instance back, on an already-valid layout`() {
        val valid = CardLayout.toMore(default, CardId.CREDITS)
        val result = CardLayout.normalize(valid)
        assertTrue(result === valid)
    }

    // --- canHide / canFold ---

    @Test
    fun `canHide is false for the last card shown above More`() {
        val onlySessionShown = CardLayout(
            order = CardId.entries,
            hidden = setOf(CardId.WEEKLY),
            more = setOf(CardId.CREDITS),
        )
        assertFalse(CardLayout.canHide(onlySessionShown, CardId.SESSION))
    }

    @Test
    fun `canHide is true when another card would remain shown`() {
        assertTrue(CardLayout.canHide(default, CardId.CREDITS))
    }

    @Test
    fun `canFold is false for the last card shown above More`() {
        val onlySessionShown = CardLayout(
            order = CardId.entries,
            hidden = setOf(CardId.WEEKLY),
            more = setOf(CardId.CREDITS),
        )
        assertFalse(CardLayout.canFold(onlySessionShown, CardId.SESSION))
    }

    @Test
    fun `canFold is true when another card would remain shown`() {
        assertTrue(CardLayout.canFold(default, CardId.CREDITS))
    }

    // --- encode / decode ---

    @Test
    fun `encode writes the order, hidden and more arrays`() {
        // Key order isn't guaranteed by the org.json implementation on the test
        // classpath, so this checks structure through decode rather than a literal
        // string; the literal shape is documented on CardLayout.Companion.encode.
        val layout = CardLayout.toMore(default, CardId.CREDITS)
        val json = CardLayout.encode(layout)
        val obj = org.json.JSONObject(json)
        assertEquals(listOf("session", "weekly", "credits"), (0 until obj.getJSONArray("o").length()).map { obj.getJSONArray("o").getString(it) })
        assertEquals(0, obj.getJSONArray("h").length())
        assertEquals(listOf("credits"), (0 until obj.getJSONArray("m").length()).map { obj.getJSONArray("m").getString(it) })
    }

    @Test
    fun `encode then decode round-trips order, hidden and more`() {
        var layout = CardLayout.move(default, CardId.CREDITS, 0)
        layout = CardLayout.hide(layout, CardId.WEEKLY)
        val back = CardLayout.decode(CardLayout.encode(layout))
        assertEquals(layout, back)
    }

    @Test
    fun `decode of null, blank or garbage falls back to DEFAULT`() {
        assertEquals(CardLayout.DEFAULT, CardLayout.decode(null))
        assertEquals(CardLayout.DEFAULT, CardLayout.decode(""))
        assertEquals(CardLayout.DEFAULT, CardLayout.decode("   "))
        assertEquals(CardLayout.DEFAULT, CardLayout.decode("not json"))
    }

    @Test
    fun `CardId fromKey resolves known keys and rejects unknown or null`() {
        assertEquals(CardId.SESSION, CardId.fromKey("session"))
        assertEquals(CardId.WEEKLY, CardId.fromKey("weekly"))
        assertEquals(CardId.CREDITS, CardId.fromKey("credits"))
        assertEquals(null, CardId.fromKey("bogus"))
        assertEquals(null, CardId.fromKey(null))
    }
}
