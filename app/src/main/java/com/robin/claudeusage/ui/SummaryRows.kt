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
     * Line 3's words: the pace line, "1d 7h behind · resets Fri 9:00 AM" (CCRM-108 (Reset Named)); spent "Out until Thu 8:00 AM";
     * a week with a reading but no clock "Not started yet"; no reading "No reading yet".
     */
    fun words(weekly: UsageWindow?, elapsed: Double?, use24h: Boolean, now: Instant = Instant.now()): String {
        val used = weekly?.percent ?: return "No reading yet"
        val resets = weekly.resetsAt ?: return "Not started yet"
        if (Pace.isSpent(used)) return "Out until ${Fmt.dayTime(resets, use24h)}"
        Pace.bandOrNull(used, elapsed) ?: return "Not started yet"
        return paceLine(weekly, use24h, now)?.text ?: "Not started yet"
    }

    /** CCRM-104 (Pace Line Parity): line 3's pace line, with its drop-order forms; null when the row has none. */
    fun paceLine(weekly: UsageWindow?, use24h: Boolean, now: Instant = Instant.now()): AppPace.Line? =
        AppPace.weeklyPaceLine(weekly, use24h, now)

    /**
     * Line 3 of a row its binding 5h holds (CCRM-103 (Hybrid Notification), shape grammar
     * rev D S1, Product): "Held · back at 3:40 PM · resets Fri". The ring at the row's left
     * replaced line 1's "5h 93% · back 3:40 PM" companion, so the reason moved here. Without
     * a 5h reset clock, "Held"; without a weekly reset, no "resets" clause.
     */
    /** CCBG-60 (Summary Line Clip): line 3 without its " · resets <day>" clause, for a row too narrow for it. */
    fun withoutReset(line: String): String = line.substringBefore(DOT + "resets ")

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
        now: Instant = Instant.now(),
    ): String = when (group) {
        SummaryRank.Group.NO_READING -> "No reading"
        SummaryRank.Group.SPENT -> "Out · ${Fmt.weekday(weekly?.resetsAt)}"
        SummaryRank.Group.HELD ->
            session?.resetsAt?.let { "Held$DOT${Fmt.timeOnly(it, use24h)}" } ?: "Held"
        SummaryRank.Group.ELIGIBLE ->
            // CCRM-104: the tinted span alone ("13h 59m ahead", "On the mark"); [compactTone] colours it.
            paceLine(weekly, use24h, now)?.let { it.emphasis ?: "On the mark" }
                ?: Pace.bandOrNull(weekly?.percent, elapsed)?.let { PaceText.bandWords(it) } ?: "Not started"
    }

    /** The tone of [compactWord]'s span; null when the word is not a pace span. */
    fun compactTone(group: SummaryRank.Group, weekly: UsageWindow?, use24h: Boolean, now: Instant = Instant.now()): PaceText.VsTone? =
        if (group == SummaryRank.Group.ELIGIBLE) paceLine(weekly, use24h, now)?.tone else null
}
