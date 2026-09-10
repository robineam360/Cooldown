package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCRM-60 (Dual Identity), decision 4: the Claude room's warm ivory surfaces and
 * serif headline, the neutral room every other provider gets, and that "every
 * other provider" really does include one the palette doesn't special-case yet.
 */
class RoomsTest {

    @Test
    fun `Claude gets the warm ivory room with a serif headline`() {
        val room = Rooms.forProvider(Provider.CLAUDE)
        assertEquals(0xFFF5EFE8L, room.surfaceLight)
        assertEquals(0xFF1B1715L, room.surfaceDark)
        assertEquals(0xFFFCF8F4L, room.cardLight)
        assertEquals(0xFF26201CL, room.cardDark)
        assertTrue(room.serifHeadline)
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
