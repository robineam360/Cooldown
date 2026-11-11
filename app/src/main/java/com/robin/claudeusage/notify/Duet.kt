package com.robin.claudeusage.notify

import com.robin.claudeusage.data.UsageCache
import kotlin.math.max
import kotlin.math.min

/**
 * The pure decisions behind CCRM-62 (Duet Notification) — the always-on notification
 * carrying two accounts — in the stacked layout of CCRM-105 (Duet Stack).
 *
 * Everything that can be decided without a `Context`, a `Canvas` or a `SharedPreferences`
 * lives here so it is testable without Robolectric (`DuetTest`). Nothing in this file
 * touches Android: the `UsageCache.RING_*` / [Conditions.MAX_STRIPS] references are
 * `const val`s, inlined at compile time, and [TextFit] is plain Kotlin.
 *
 * The numbers are the approved ones from `design/2026-11-10-v20-feedback-wireframes.html` §1
 * (rev B). They are not adjustable knobs — each one falls out of the width or height
 * arithmetic drawn there, and the notes below say which.
 */
object Duet {

    /** Which of the two accounts a single-slot surface (the status-bar ring) is showing. */
    enum class Slot { FIRST, SECOND }

    // --- the collapsed width budget (wireframe §1) -------------------------------------

    /** The two 5h rings at the left, First then Second, 6 dp apart. */
    const val RING_DP = 36f
    const val RING_GAP_DP = 6f
    /** From the second ring to the rows, and from the rows to the figure slot. */
    const val COLUMN_GAP_DP = 8f
    /** The figure's fixed slot at each row's right: "+19%" is 2.25 em × 18 = 40.5 dp. */
    const val FIGURE_SLOT_DP = 42f

    /** 36 + 6 + 36 + 8 + 8 + 42: everything but the bar. Rings and the figure slot never shrink. */
    const val CHROME_DP = RING_DP + RING_GAP_DP + RING_DP + COLUMN_GAP_DP + COLUMN_GAP_DP + FIGURE_SLOT_DP

    /** The bar's minimum, as the halves' was: the layout floor is 136 + 40 = 176 dp. */
    const val BAR_MIN_DP = 40f

    // The name line, 15 dp tall over the bar: mark 14, 4, name 12 sp, state word 11 sp at the right.
    const val MARK_DP = 14f
    const val MARK_GAP_DP = 4f
    /** The 6 dp condition dot and its 5 dp margin, after the name. */
    const val DOT_DP = 11f
    /** The state word's start padding. */
    const val WORD_GAP_DP = 6f
    /** The name ellipsizes down to this, and only then does the state word drop its tail. */
    const val NAME_FLOOR_DP = 24f

    const val NAME_SP = 12f
    const val WORD_SP = 11f
    const val FIGURE_SP = 18f

    /**
     * Wireframe §1 (Robin, 2026-11-10: "cap the collapsed text"): a fixed 42 dp slot and a 52 dp
     * height cannot follow the font scale, so the collapsed Duet's text is capped in dp — as the
     * ring's bore figure already is. A stated exception to foundations §2 ("true sp") for this one
     * view; the expanded view scales as before.
     */
    const val FIGURE_CAP_DP = 18f
    const val NAME_CAP_DP = 13.5f
    const val WORD_CAP_DP = 12f

    /** [sp] at [fontScale], in dp, never above [capDp]. */
    fun cappedDp(sp: Float, fontScale: Float, capDp: Float): Float = min(sp * fontScale, capDp)

    /**
     * The collapsed content width the layout is drawn for. A RemoteViews never learns it, so it
     * is a nominal per screen class, keyed on the smallest width so a phone turned sideways stays
     * a phone. From 600 dp up, the Fold 7 inner shade's ≈352 dp (wireframe §1's table). Below
     * that, the screen less [PHONE_SHADE_CHROME_DP]: the shade's margins, the card's padding,
     * the app icon's column and the expand button. Measured 2026-11-11 (Step A2): the AOSP shade
     * gave 280 dp at 420 dp and 220 at 360; the Fold 7 cover's One UI shade gave 272 at 411 (the
     * v1.8 pass). Only the bar bitmap's nominal width and the state word's tail depend on it.
     */
    fun contentDp(smallestScreenWidthDp: Int): Float = when {
        smallestScreenWidthDp >= 600 -> 352f
        else -> (smallestScreenWidthDp.toFloat() - PHONE_SHADE_CHROME_DP).coerceAtLeast(CHROME_DP + BAR_MIN_DP)
    }

    /** What a phone's shade takes around a collapsed custom view (see [contentDp]). */
    const val PHONE_SHADE_CHROME_DP = 140f

    /**
     * The bar's length at [contentDp]: whatever the chrome leaves, never under [BAR_MIN_DP].
     * Both rows' bars share it, so they share one left edge and one length. It is also the
     * nominal width the bar bitmap is drawn at, so `fitXY` stretches it by a few percent at most.
     */
    fun barDp(contentDp: Float): Float = max(BAR_MIN_DP, contentDp - CHROME_DP)

    // --- the state word ------------------------------------------------------------------

    /**
     * The word a row carries at its right, in precedence order (wireframe §1): the first that
     * applies wins; the other row is unaffected.
     */
    enum class Word { SIGN_IN, NO_READING, STALE, OUT, HELD, NOT_STARTED }

    fun word(
        signIn: Boolean,
        noReading: Boolean,
        stale: Boolean,
        weekSpent: Boolean,
        held: Boolean,
        notStarted: Boolean,
    ): Word? = when {
        signIn -> Word.SIGN_IN
        noReading -> Word.NO_READING
        stale -> Word.STALE
        weekSpent -> Word.OUT
        held -> Word.HELD
        notStarted -> Word.NOT_STARTED
        else -> null
    }

    /**
     * A row's word in full and with its tail dropped: "Held · 3:40 PM" / "Held", "Out · Thu" /
     * "Out"; the others have no tail. [clock] is the 5h's reset for [Word.HELD] and the Weekly's
     * reset (its weekday, or the bare clock later today) for [Word.OUT]; null leaves the word bare.
     */
    fun wordText(word: Word, clock: String?): Pair<String, String> {
        val head = when (word) {
            Word.SIGN_IN -> "Sign in"
            Word.NO_READING -> "No reading"
            Word.STALE -> "Stale"
            Word.OUT -> "Out"
            Word.HELD -> "Held"
            Word.NOT_STARTED -> "Not started"
        }
        val tailed = (word == Word.OUT || word == Word.HELD) && clock != null
        return (if (tailed) "$head · $clock" else head) to head
    }

    /** The state word's width with its 6 dp gap, in dp; 0 for none. */
    fun wordDp(word: String?, fontScale: Float): Float =
        if (word == null) 0f
        else WORD_GAP_DP + TextFit.widthDp(TextFit.em(word), 1f, cappedDp(WORD_SP, fontScale, WORD_CAP_DP))

    /**
     * How far the name line's end is padded so the name ellipsizes before the word: the word's
     * estimated width plus 4%, since [TextFit]'s estimate ran about 2% narrow on the Fold 7.
     */
    fun wordPadDp(word: String?, fontScale: Float): Float = if (word == null) 0f else wordDp(word, fontScale) * 1.04f

    /**
     * The name's room on its line: the bar's length less the mark, its gap, the dots and the word
     * as the line is padded for it ([wordPadDp]), so the decision and the layout agree.
     */
    fun nameRoomDp(barDp: Float, dots: Int, word: String?, fontScale: Float): Float =
        barDp - MARK_DP - MARK_GAP_DP - dots * DOT_DP - wordPadDp(word, fontScale)

    /**
     * The narrow drop order (wireframe §1, Astra C1-4): the bar absorbs first (the layout's
     * weight), then the name ellipsizes to its 24 dp floor, and only then does the state word
     * drop its tail. True keeps [full]; the layout ellipsizes the name against what is left.
     */
    fun keepsTail(barDp: Float, dots: Int, name: String, full: String, fontScale: Float): Boolean {
        val nameDp = TextFit.widthDp(TextFit.em(name, bold = true), 1f, cappedDp(NAME_SP, fontScale, NAME_CAP_DP))
        return nameRoomDp(barDp, dots, full, fontScale) >= min(NAME_FLOOR_DP, nameDp)
    }

    /**
     * How many condition strips the expanded panel may draw.
     *
     * Android caps the expanded custom view; two Duet header blocks and their gaps spend
     * 136 dp of it, leaving ~120 dp for the panel. The two Weekly rows the panel exists
     * for take 78 of that as native rows (CCBG-26 (Panel Scaling)), which leaves room for
     * exactly **one** one-line strip (38 dp). So the CCRM-44 (One Surface) cap of
     * [Conditions.MAX_STRIPS] falls to 1 whenever a Second account is set; the rest fold
     * into a "+ n more" tail on that one line.
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
     * side hands the ring to the other; both null falls back to First.
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
