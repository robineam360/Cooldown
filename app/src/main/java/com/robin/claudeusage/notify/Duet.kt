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
     * 328 dp, and one 16 dp gutter down the middle leaves **156 dp per half**. The label's
     * line holds the 14 dp provider mark and its 4 dp gap; the condition dot, when it
     * shows, takes 6 dp plus its 5 dp margin, and so does the synthetic dot beside it
     * (CCRM-15 (Above-Pace Verification)) — [dots] counts them. The label gets the rest.
     *
     * CCBG-24 (Duet Label Clamp) measured the figure and gave the label what it left; the
     * Fold 7's unfolded shade then left one letter (CCBG-39 (Inner Duet Squeeze)), since a
     * notification is one layout for every width and its halves run from ~101 to 156 dp.
     * Wireframe rev G puts the label on its own line across the half, so the figure no
     * longer takes from it: this is the ceiling at 156 dp, and on a narrower half the
     * layout's weight ellipsizes the label against the width it really has. Never below
     * [MIN_LABEL_DP].
     */
    fun labelClampDp(dots: Int = 0, ring: HalfRing = HalfRing.NONE): Int =
        (HALF_DP - ringTakesDp(ring) - MARK_DP - MARK_GAP_DP - dots * DOT_DP).toInt().coerceAtLeast(MIN_LABEL_DP)

    private const val HALF_DP = 156f
    private const val MARK_DP = 14f
    private const val MARK_GAP_DP = 4f
    private const val DOT_DP = 11f
    const val MIN_LABEL_DP = 24

    /**
     * CCRM-103 (Hybrid Notification), shape grammar rev D §3 (D1–D3): the 5h ring a collapsed
     * half carries. [FULL] is the 36 dp ring with its figure, at the half's left, 8 dp from the
     * column; [BARE] is the 14 dp ring with no digits at the end of the label line, for a half
     * under [RING_MIN_HALF_DP] (36 + 8 + a 40 dp bar + 8 + a 57 dp figure = 149); [NONE] is an
     * account with no 5h (B1: today's half exactly).
     */
    enum class HalfRing { NONE, FULL, BARE }

    const val RING_DP = 36f
    const val RING_GAP_DP = 8f
    const val BARE_RING_DP = 14f
    const val BARE_RING_GAP_DP = 4f
    const val RING_MIN_HALF_DP = 150f

    /** The ring a half of [halfDp] carries; [hasFiveHour] false is B1's no ring at all. */
    fun halfRing(hasFiveHour: Boolean, halfDp: Float): HalfRing = when {
        !hasFiveHour -> HalfRing.NONE
        halfDp >= RING_MIN_HALF_DP -> HalfRing.FULL
        else -> HalfRing.BARE
    }

    /** The width a half's ring takes from its label line, gap included. */
    fun ringTakesDp(ring: HalfRing): Float = when (ring) {
        HalfRing.NONE -> 0f
        HalfRing.FULL -> RING_DP + RING_GAP_DP
        HalfRing.BARE -> BARE_RING_DP + BARE_RING_GAP_DP
    }

    /** The collapsed figure: 28 sp beside the 36 dp ring (D1), today's 30 sp otherwise. */
    fun figureSp(ring: HalfRing): Float = if (ring == HalfRing.FULL) 28f else 30f

    /**
     * The nominal width a collapsed half's bar is drawn at: about 47 dp beside the 36 dp ring
     * (156 − 36 − 8 − 8 − a 57 dp figure, D1), else the half's own width as before.
     */
    fun barDp(ring: HalfRing): Float = if (ring == HalfRing.FULL) RING_BAR_DP else HALF_DP

    private const val RING_BAR_DP = 47f

    /**
     * The width the layout is drawn for. RemoteViews never learns a half's real width (it
     * runs from about 101 dp on the Fold 7's inner shade to 156 on a phone), so the ring
     * choice is made against this nominal half — see [halfRing].
     */
    const val NOMINAL_HALF_DP = HALF_DP

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
