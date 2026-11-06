package com.robin.claudeusage.data

/**
 * CCRM-103 (Hybrid Notification): the per-account Status-bar icon choice in the account's
 * ⋮ › Headline… sheet (shape grammar rev D §4b, Android only). The 5h ring by default; an
 * account may take the Weekly bar instead. A plan with no 5h always shows the Weekly bar.
 */
enum class StatusIconChoice(val key: String) {
    RING("ring"),
    WEEKLY_BAR("weeklyBar");

    companion object {
        val DEFAULT = RING
        fun fromKey(key: String?): StatusIconChoice = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** What the status-bar slot draws for the account it belongs to. */
enum class StatusGlyph {
    /** The 5h ring: arc, needle, the red slice past it. */
    RING,
    /** The 5h ring's arc at 40% ink, no needle: the week is spent, so the 5h cannot be used. */
    GREYED_RING,
    /** A red cross alone: out right now. */
    CROSS,
    /** The Weekly as a small bar with its pace tick (N3). */
    WEEKLY_BAR,
}

/**
 * Which [StatusGlyph] the status bar shows (rev D §4, C6–C8). The Headline no longer
 * picks the status-bar window: the ring is always the 5h.
 *
 * - The 5h ring chosen: a spent week greys it, and that outranks the cross (C7: the 5h
 *   cannot be used anyway); a full 5h is the cross until it resets.
 * - The Weekly bar (chosen, or a plan with no 5h): a full Weekly, or a full 5h on a plan
 *   that has one, is the cross (C8: the cross means "out right now" whichever icon).
 *
 * "Full" is the ring's own cross test, a truncated 100 (spec notification §4); "spent" is
 * core §6.1's 99.5. Cases: `spec/mobile/fixtures/status-icon/cases.json`.
 */
object StatusIconRule {

    fun glyph(
        choice: StatusIconChoice,
        hasSession: Boolean,
        sessionPct: Double?,
        weeklyPct: Double?,
    ): StatusGlyph {
        if (hasSession && choice == StatusIconChoice.RING) {
            return when {
                Pace.isSpent(weeklyPct) -> StatusGlyph.GREYED_RING
                full(sessionPct) -> StatusGlyph.CROSS
                else -> StatusGlyph.RING
            }
        }
        if (full(weeklyPct) || (hasSession && full(sessionPct))) return StatusGlyph.CROSS
        return StatusGlyph.WEEKLY_BAR
    }

    private fun full(pct: Double?): Boolean = pct != null && pct.toInt() >= 100
}
