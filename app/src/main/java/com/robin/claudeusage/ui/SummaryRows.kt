package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.data.SummaryRank
import com.robin.claudeusage.data.UsageWindow
import java.time.Instant

/**
 * CCRM-98 (Summary Tab): the All tab's row words (`design/2026-11-02-summary-tab.html`
 * rev B, §2 "What each list row is made of"). Pure; [SummaryRank] decides the order.
 */
object SummaryRows {

    private const val DOT = " · "

    /** From this many accounts the rows go to one line and the map numbers its dots (S2). */
    const val COMPACT_FROM = 6

    fun compact(accountCount: Int): Boolean = accountCount >= COMPACT_FROM

    /** Spent and unread rows draw at half strength (C1's Side, S2's Ops). */
    fun dimmed(group: SummaryRank.Group): Boolean =
        group == SummaryRank.Group.SPENT || group == SummaryRank.Group.NO_READING

    /**
     * Line 3's words: "Room to spare · ≈22%/day · resets Fri"; spent "Out until Thu 8:00 AM";
     * a week with a reading but no clock "Not started yet"; no reading "No reading yet".
     */
    fun words(weekly: UsageWindow?, elapsed: Double?, use24h: Boolean, now: Instant = Instant.now()): String {
        val used = weekly?.percent ?: return "No reading yet"
        val resets = weekly.resetsAt ?: return "Not started yet"
        if (Pace.isSpent(used)) return "Out until ${Fmt.dayTime(resets, use24h)}"
        val band = Pace.bandOrNull(used, elapsed) ?: return "Not started yet"
        val tight = PaceText.tight(band, Pace.allowance(used, resets.toEpochMilli() - now.toEpochMilli()))
            ?: return "Not started yet"
        return tight + DOT + "resets " + Fmt.weekday(resets)
    }

    /**
     * Line 3 of a row its binding 5h holds (CCRM-103 (Hybrid Notification), shape grammar
     * rev D S1, Product): "Held · back at 3:40 PM · resets Fri". The ring at the row's left
     * replaced line 1's "5h 93% · back 3:40 PM" companion, so the reason moved here. Without
     * a 5h reset clock, "Held"; without a weekly reset, no "resets" clause.
     */
    fun heldWords(session: UsageWindow?, weekly: UsageWindow?, use24h: Boolean): String {
        val back = session?.resetsAt?.let { DOT + "back at " + Fmt.timeOnly(it, use24h) } ?: ""
        val resets = weekly?.resetsAt?.let { DOT + "resets " + Fmt.weekday(it) } ?: ""
        return "Held$back$resets"
    }

    /**
     * The compact row's one word (S2): the band, "Held · 3:40 PM", "Out · Thu", "No
     * reading". A held row says "Held" where v2.0 said "5h" — the shape grammar never writes
     * "5h" (judge F3) — and keeps the bare time so it fits the 90 dp word column.
     */
    fun compactWord(
        group: SummaryRank.Group,
        weekly: UsageWindow?,
        elapsed: Double?,
        session: UsageWindow?,
        use24h: Boolean,
    ): String = when (group) {
        SummaryRank.Group.NO_READING -> "No reading"
        SummaryRank.Group.SPENT -> "Out · ${Fmt.weekday(weekly?.resetsAt)}"
        SummaryRank.Group.HELD ->
            session?.resetsAt?.let { "Held$DOT${Fmt.timeOnly(it, use24h)}" } ?: "Held"
        SummaryRank.Group.ELIGIBLE ->
            Pace.bandOrNull(weekly?.percent, elapsed)?.let { PaceText.bandWords(it) } ?: "Not started"
    }

    /**
     * The pace pip's dot, 0…1 across its 40 dp scale: the Pace −100…+100 mapped onto the
     * wireframe's 3…37 of 40, so a column of rows reads left = room, right = over.
     */
    fun pipFraction(pace: Double): Float = ((3.0 + 34.0 * (pace.coerceIn(-100.0, 100.0) + 100.0) / 200.0) / 40.0).toFloat()

    enum class PipTone { ROOM, ON_PACE, OVER, NEARLY_OUT }

    /** Green-grey under pace, neutral on pace, amber over, red when nearly out. */
    fun pipTone(pace: Double): PipTone = when {
        kotlin.math.abs(pace) <= 5.0 -> PipTone.ON_PACE
        pace < 0 -> PipTone.ROOM
        pace > 50 -> PipTone.NEARLY_OUT
        else -> PipTone.OVER
    }
}
