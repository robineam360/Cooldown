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
     * How wide a Duet half's account label may be, in dp, given how wide this half's
     * figure really is.
     *
     * The width arithmetic: a 360 dp notification card less 16 dp of padding a side leaves
     * 328 dp, and one 16 dp gutter down the middle leaves **156 dp per half** — the tight
     * case, which is the one that has to work. The half also holds the 14 dp provider
     * mark and its 4 dp gap, and the figure's 8 dp start margin; the condition dot, when
     * it shows, takes 6 dp plus its 5 dp margin, and so does the synthetic dot beside it
     * (CCRM-15 (Above-Pace Verification)) — [dots] counts them. The label gets whatever
     * is left.
     *
     * CCBG-24 (Duet Label Clamp): this used to be a two-step table (72 dp, or 56 dp at four
     * characters), priced against a ~54 dp three-character figure. Samsung's font draws
     * "98%" at 30 sp bold wider than that, so the label ellipsized to "ChatG…" beside it.
     * The caller now measures the figure ([figureWidthDp], `Paint.measureText` in the
     * device font) and the label takes the true remainder — "ChatGPT 98%" reads whole, and
     * a genuinely long label still ellipsizes. Never below [MIN_LABEL_DP], so a huge font
     * scale leaves a stub of label rather than none.
     */
    fun labelClampDp(figureWidthDp: Float, dots: Int = 0): Int {
        val left = HALF_DP - MARK_DP - MARK_GAP_DP - FIGURE_GAP_DP - figureWidthDp -
            dots * DOT_DP
        return kotlin.math.floor(left).toInt().coerceAtLeast(MIN_LABEL_DP)
    }

    private const val HALF_DP = 156f
    private const val MARK_DP = 14f
    private const val MARK_GAP_DP = 4f
    private const val FIGURE_GAP_DP = 8f
    private const val DOT_DP = 11f
    const val MIN_LABEL_DP = 24

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
