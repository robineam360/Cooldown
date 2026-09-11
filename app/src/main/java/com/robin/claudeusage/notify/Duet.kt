package com.robin.claudeusage.notify

import com.robin.claudeusage.data.UsageCache

/**
 * The pure decisions behind CCRM-62 (Duet Notification) — the always-on notification
 * carrying two accounts.
 *
 * Everything that can be decided without a `Context`, a `Canvas` or a `SharedPreferences`
 * lives here so it is testable without Robolectric (`DuetTest`). Nothing in this file
 * touches Android: the two `UsageCache.RING_*` / [Conditions.MAX_STRIPS] references are
 * `const val`s, inlined at compile time, so no Android class is loaded to read them.
 *
 * The numbers are the approved ones from `design/settings-diet-wireframe.html` section 2.
 * They are not adjustable knobs — each one falls out of the width or height arithmetic
 * drawn there, and the notes below say which.
 */
object Duet {

    /** Which of the two accounts a single-slot surface (the status-bar ring) is showing. */
    enum class Slot { FIRST, SECOND }

    /**
     * How wide a Duet half's account label may be, in dp.
     *
     * The width arithmetic: a 360 dp notification card less 16 dp of padding a side leaves
     * 328 dp, and one 16 dp gutter down the middle leaves **156 dp per half** — the tight
     * case, which is the one that has to work. A three-character figure at 30 sp bold takes
     * about 54 dp plus its 8 dp gap, leaving a 90 dp left column that also has to hold the
     * 14 dp provider mark and its 4 dp gap: hence **72 dp** for the label.
     *
     * A four-character figure ("100%") takes about 74 dp instead, so the label gives back
     * the difference and clamps at **56 dp**. Short labels never notice; a long one loses
     * about two characters exactly when the number matters most, which is the accepted
     * trade — the mark and the accent still say which account it is, and the full label is
     * one tap away in the expanded header.
     */
    fun labelClampDp(figureText: String): Int = if (figureText.length >= 4) 56 else 72

    /**
     * How many condition strips the expanded panel may draw.
     *
     * Android caps the expanded custom view; two Duet header blocks and their gaps spend
     * 136 dp of it, leaving ~120 dp for the panel. The two Weekly rows the panel exists
     * for take 78 of that as native rows (CCBG-26 (Panel Scaling)), which leaves room for
     * exactly **one** one-line strip (38 dp). So the CCRM-44 (One Surface) cap of
     * [Conditions.MAX_STRIPS] falls to 1 whenever a Second account is set; the rest fold
     * into a "+ n more" tail on that one line. (It was 2 before CCBG-26, priced against
     * strips the bitmap then drew taller than the table said, which is what made the
     * whole panel shrink.)
     */
    fun maxStrips(hasSecond: Boolean): Int = if (hasSecond) 1 else Conditions.MAX_STRIPS

    /**
     * Which account the status-bar ring shows, from the "Status-bar ring shows" setting
     * ([UsageCache.RING_FIRST] / [UsageCache.RING_SECOND] / [UsageCache.RING_HIGHER]) and
     * the two headline percentages.
     *
     * The ring is one glyph for two accounts, and since CCRM-62 it carries no weekly hub
     * dot — below 80% its hue *is* the account's own accent, which is what makes a single
     * glyph workable at all. [UsageCache.RING_HIGHER] therefore may switch during the day,
     * deliberately: the colour flipping from orange to green is itself the news that the
     * other account has taken the lead.
     *
     * Ties keep [Slot.FIRST]. The comparison is strictly "is Second ahead of First", so a
     * dead heat does not make the glyph flicker between two accounts on successive polls,
     * and First is the account the setting already treats as the default. A null on one
     * side hands the ring to the other; both null falls back to First, because with no
     * reading at all there is nothing to compare and the ring draws its no-data face
     * either way.
     */
    fun ringAccount(mode: String, firstPct: Double?, secondPct: Double?): Slot = when (mode) {
        UsageCache.RING_SECOND -> Slot.SECOND
        UsageCache.RING_HIGHER -> when {
            firstPct == null && secondPct == null -> Slot.FIRST
            firstPct == null -> Slot.SECOND
            secondPct == null -> Slot.FIRST
            secondPct > firstPct -> Slot.SECOND
            else -> Slot.FIRST
        }
        // RING_FIRST, and anything unrecognised: the default the setting ships with.
        else -> Slot.FIRST
    }
}
