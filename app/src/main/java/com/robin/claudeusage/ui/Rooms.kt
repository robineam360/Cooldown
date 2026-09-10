package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Provider

/**
 * CCRM-60 (Dual Identity): "two rooms in one house" (design/dual-identity-wireframe.html
 * section 3, decision 4). The selected tab's account already drives the accent
 * (CCRM-56 (Provider Identity) decision 1); this carries that one step further —
 * the surface tint, the card tint one step warmer/lighter than the surface, and
 * the headline typeface — while the cards themselves stay exactly what they are
 * today.
 *
 * Pure and Android-free on purpose, colours as `0xFFRRGGBB` [Long]s rather than
 * `androidx.compose.ui.graphics.Color`, so this unit-tests without Compose or
 * Robolectric. `MainActivity` turns a room's colours into `Color` at the call site.
 */
object Rooms {

    data class Room(
        val surfaceLight: Long,
        val surfaceDark: Long,
        val cardLight: Long,
        val cardDark: Long,
        val serifHeadline: Boolean,
    )

    private val CLAUDE_ROOM = Room(
        surfaceLight = 0xFFF5EFE8,
        surfaceDark = 0xFF1B1715,
        cardLight = 0xFFFCF8F4,
        cardDark = 0xFF26201C,
        serifHeadline = true,
    )

    /**
     * ChatGPT's room — and every other provider's, Antigravity (Gemini) included:
     * the wireframe's decision covers "any other provider" under the neutral room
     * rather than drawing one up per provider, so a fourth service (should one ever
     * qualify under CCRM-53 (Provider Model)) lands here without a design gap.
     */
    private val NEUTRAL_ROOM = Room(
        surfaceLight = 0xFFF7F7F8,
        surfaceDark = 0xFF0D0D0D,
        cardLight = 0xFFFFFFFF,
        cardDark = 0xFF1A1A1A,
        serifHeadline = false,
    )

    fun forProvider(provider: Provider): Room = when (provider) {
        Provider.CLAUDE -> CLAUDE_ROOM
        else -> NEUTRAL_ROOM
    }
}
