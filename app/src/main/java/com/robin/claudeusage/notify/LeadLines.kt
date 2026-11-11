package com.robin.claudeusage.notify

import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.PaceText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CCRM-96 (Weekly First) and CCRM-97 (Pace Number): the notification's text lines once
 * Weekly leads (`design/2026-11-02-weekly-first-pace.html` §2–3, the caption decision in
 * `-pace-line-label.html`); CCRM-104 (Pace Line Parity) moved them to core §6.3's printed
 * form (`design/2026-11-06-mobile-pace-line-wireframe.html` §5, §8). Strings only;
 * [PinnedNotification] styles and places them.
 */
internal object LeadLines {

    private const val DOT = " · "

    /**
     * CCRM-103 (Hybrid Notification), shape grammar rev D §3: the header's label names the
     * Weekly whatever leads — the shapes never swap, the ring is the 5h.
     */
    fun headerTitle(accountLabel: String): String = "$accountLabel${DOT}Weekly"

    /** Rev D §3 Q6: the sub line while the Weekly has no reading or no reset clock yet. */
    const val WEEKLY_NOT_STARTED = "Weekly not started"

    /** Rev D §3 Q7: nothing fetched yet (single and Duet alike). */
    const val NO_READING = "No reading yet"

    /**
     * The ring's spoken label — there is no "5h" text anywhere in the grammar, so the
     * accessibility label carries the window: "5-hour window, 34 percent".
     */
    fun ringDescription(figure: String): String =
        "5-hour window, " + if (figure == "—") "no reading" else figure.replace("%", " percent")

    /** CCRM-105 (Duet Stack): the spoken label of a collapsed Duet ring slot whose plan has no 5h. */
    const val NO_FIVE_HOUR = "No 5-hour window on this plan"

    /**
     * CCRM-105 (Duet Stack): the tail of "Out · Thu" — the reset's weekday, or its bare clock when
     * it falls later today (core §6.3: a bare clock always means today).
     */
    fun dayOrClock(
        reset: Instant,
        nowMs: Long,
        use24h: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val pattern = when {
            !PaceText.resetsToday(reset, nowMs, zone) -> "EEE"
            use24h -> "HH:mm"
            else -> "h:mm a"
        }
        return DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(reset)
    }

    /** Rev D §3 N2: the 5h's time left under the expanded ring, "in 2h 20m"; null without a clock. */
    fun ringTimeLeft(session: UsageWindow?): String? = session?.resetsAt?.let { Fmt.relIn(it) }

    /** "Out until Sun 9:30 PM · in 3d 4h" — a spent week, no band, no allowance. */
    fun outUntil(weekly: UsageWindow, use24h: Boolean): String =
        "Out until ${Fmt.dayTime(weekly.resetsAt, use24h)}$DOT${Fmt.relIn(weekly.resetsAt)}"

    /** CCRM-114 (Narrow Duet), F2: the spent line with its second clause dropped whole — "Out until Sun 9:30 PM". */
    fun outUntilShort(weekly: UsageWindow, use24h: Boolean): String = "Out until ${Fmt.dayTime(weekly.resetsAt, use24h)}"

    /**
     * CCRM-104 (Pace Line Parity), wireframe D3/D10: the Weekly's pace line — "21h 50m ahead ·
     * Sun 9:30 PM" — replacing the band words and "≈11%/day". Null when there is no readout
     * (no reading, no reset clock, a spent week: the caller's own line applies). The form is
     * chosen for a column [availDp] wide at [sp] and [fontScale] ([TextFit], C1-4).
     */
    fun weeklyPace(
        weekly: UsageWindow?,
        now: Instant,
        use24h: Boolean,
        availDp: Float,
        sp: Float,
        fontScale: Float,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): TextFit.Pick<PaceText.PaceLine>? {
        val resets = weekly?.resetsAt ?: return null
        val mt = Pace.markTime(
            weekly.percent, resets.toEpochMilli() - now.toEpochMilli(), Projection.WEEKLY_MS, now.toEpochMilli(),
        )
        val forms = PaceText.paceLineForms(mt, resets, now.toEpochMilli(), use24h, zone, locale)
        return TextFit.paceLine(forms, availDp, sp, fontScale)
    }

    /**
     * Wireframe D4/C1-3: the expanded single header's line 2 under a Weekly lead, permanently
     * the pace-mark caption — "39% pace mark · 13 over", Vs pace "52% used · 39% pace mark",
     * Left "61% pace mark · 13 over" — or its distance clause alone when the column is too
     * narrow ("13 over"). Null when spent, unclocked or unread.
     */
    fun weeklyCaption(
        weekly: UsageWindow?,
        elapsed: Double?,
        mode: PaceText.Mode,
        availDp: Float,
        sp: Float,
        fontScale: Float,
    ): TextFit.Pick<String>? {
        if (weekly?.resetsAt == null) return null
        return TextFit.caption(PaceText.captionForms(mode, weekly.percent, elapsed), availDp, sp, fontScale)
    }

    /**
     * The 5h line when 5h leads: today's reset line, or, while it binds, "Back at 3:40 PM ·
     * in 50m" (the window is what is stopping you, so the line says when it lets go).
     */
    fun sessionLine(session: UsageWindow?, binding: Boolean, resetClock: Boolean, use24h: Boolean): String? {
        val resets = session?.resetsAt ?: return null
        return when {
            binding -> "Back at ${Fmt.timeOnly(resets, use24h)}$DOT${Fmt.relIn(resets)}"
            resetClock -> "Resets at ${Fmt.timeOnly(resets, use24h)}$DOT${Fmt.relIn(resets)}"
            else -> "Resets ${Fmt.relIn(resets)}$DOT${Fmt.timeOnly(resets, use24h)}"
        }
    }

    /**
     * CCRM-102 (Spent Week Gate): the note a disabled 5h carries while the week is spent —
     * "Weekly limit reached — 5h can't be used until Sat 4:51 PM".
     */
    fun spentWeekNote(weekly: UsageWindow, use24h: Boolean): String =
        "Weekly limit reached — 5h can’t be used until ${Fmt.dayTime(weekly.resetsAt, use24h)}"

    /** The expanded header's second line under a 5h lead: "Projected to hit 100% at 3:25 PM". */
    fun sessionSecondLine(hitsLimitAtMs: Long?, use24h: Boolean): String? =
        hitsLimitAtMs?.let { "Projected to hit 100% at ${Fmt.timeOnly(Instant.ofEpochMilli(it), use24h)}" }

    /**
     * A figure in the chosen Usage display: Used, Left, or — CCRM-104 (Pace Line Parity), D1 —
     * the signed distance from the pace mark ("+19%", "−12%", "0%"); "—" with no reading.
     * The 5h ring's figure stays unsigned: [ringFigure].
     */
    fun figure(pct: Double?, elapsed: Double?, mode: PaceText.Mode): String = when {
        pct == null -> "—"
        mode == PaceText.Mode.VS_PACE -> PaceText.vsPaceSigned(pct, elapsed)
        else -> "${Fmt.usageInt(pct, mode == PaceText.Mode.LEFT)}%"
    }

    /** The ring's figure: Vs pace is unsigned ("19%"), its tone carries the side (wireframe §1). */
    fun ringFigure(pct: Double?, elapsed: Double?, mode: PaceText.Mode): String = when {
        pct == null -> "—"
        mode == PaceText.Mode.VS_PACE -> PaceText.vsPaceFigure(pct, elapsed)
        else -> "${Fmt.usageInt(pct, mode == PaceText.Mode.LEFT)}%"
    }

    /**
     * The small caption under a figure: "LEFT" in Left mode. Vs pace has none since CCRM-104
     * (D2): the figure's sign and colour are the cue.
     */
    fun figureCaption(pct: Double?, mode: PaceText.Mode): String? =
        if (pct != null && mode == PaceText.Mode.LEFT) "LEFT" else null
}
