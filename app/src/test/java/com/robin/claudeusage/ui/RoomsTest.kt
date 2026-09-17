package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCRM-60 (Dual Identity), decision 4: the Claude room's warm ivory surfaces and
 * serif headline, the neutral room every other provider gets, and that "every
 * other provider" really does include one the palette doesn't special-case yet.
 *
 * CCRM-76 (Black Room) took the dark half of that decision back — Claude's dark
 * surfaces are now ChatGPT's black — so these also pin what still separates the two
 * rooms, which is the light half and the headline face.
 */
class RoomsTest {

    @Test
    fun `Claude gets the warm ivory room with a serif headline`() {
        val room = Rooms.forProvider(Provider.CLAUDE)
        assertEquals(0xFFF5EFE8L, room.surfaceLight)
        assertEquals(0xFFFCF8F4L, room.cardLight)
        assertTrue(room.serifHeadline)
    }

    @Test
    fun `Claude's dark room is black, the same as ChatGPT's`() {
        // CCRM-76 (Black Room): the warm #1B1715 / #26201C left the orange accent and
        // the amber pace line with nothing to stand out against.
        val room = Rooms.forProvider(Provider.CLAUDE)
        assertEquals(0xFF0D0D0DL, room.surfaceDark)
        assertEquals(0xFF1A1A1AL, room.cardDark)
        assertEquals(Rooms.forProvider(Provider.CHATGPT).surfaceDark, room.surfaceDark)
        assertEquals(Rooms.forProvider(Provider.CHATGPT).cardDark, room.cardDark)
    }

    @Test
    fun `the two rooms still differ in the light theme`() {
        // The room is still a room: CCRM-76 stops it tinting the *dark* theme only, and
        // a change that flattened the light half too would be the decision it isn't.
        val claude = Rooms.forProvider(Provider.CLAUDE)
        val neutral = Rooms.forProvider(Provider.CHATGPT)
        assertNotEquals(claude.surfaceLight, neutral.surfaceLight)
        assertNotEquals(claude.cardLight, neutral.cardLight)
        assertNotEquals(claude.serifHeadline, neutral.serifHeadline)
    }

    @Test
    fun `ChatGPT gets the neutral room with the default sans`() {
        val room = Rooms.forProvider(Provider.CHATGPT)
        assertEquals(0xFFF7F7F8L, room.surfaceLight)
        assertEquals(0xFF0D0D0DL, room.surfaceDark)
        assertEquals(0xFFFFFFFFL, room.cardLight)
        assertEquals(0xFF1A1A1AL, room.cardDark)
        assertFalse(room.serifHeadline)
    }

    @Test
    fun `an other provider falls back to the neutral room`() {
        // Antigravity (Gemini) has no room of its own — the wireframe's decision
        // covers "any other provider" under the neutral room rather than one per
        // provider, so it lands on exactly ChatGPT's values.
        assertEquals(Rooms.forProvider(Provider.CHATGPT), Rooms.forProvider(Provider.ANTIGRAVITY))
    }
}
