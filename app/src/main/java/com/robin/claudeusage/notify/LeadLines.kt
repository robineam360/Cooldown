package com.robin.claudeusage.notify

import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.PaceText
import java.time.Instant

/**
 * CCRM-96 (Weekly First) and CCRM-97 (Pace Number): the notification's text lines once
 * Weekly leads (`design/2026-11-02-weekly-first-pace.html` §2–3, the caption decision in
 * `-pace-line-label.html`). Strings only; [PinnedNotification] styles and places them.
 */
internal object LeadLines {

    private const val DOT = " · "

    /** The 5h companion's own text, "5h 34%" — its loud styling is the caller's. */
    fun companion(session: UsageWindow?, left: Boolean): String? {
        val pct = session?.percent ?: return null
        return "5h ${Fmt.usageInt(pct, left)}%"
    }

    /** "Out until Sun 9:30 PM · in 3d 4h" — a spent week, no band, no allowance. */
    fun outUntil(weekly: UsageWindow, use24h: Boolean): String =
        "Out until ${Fmt.dayTime(weekly.resetsAt, use24h)}$DOT${Fmt.relIn(weekly.resetsAt)}"

    /**
     * "Slow down · ≈11%/day", or null when there is no verdict (no reading, not started).
     * [toLast] adds the expanded header's " to last" after a daily allowance.
     */
    fun paceWords(weekly: UsageWindow?, elapsed: Double?, now: Instant = Instant.now(), toLast: Boolean = false): String? {
        val used = weekly?.percent ?: return null
        val resets = weekly.resetsAt ?: return null
        val band = Pace.bandOrNull(used, elapsed) ?: return null
        val words = PaceText.bandWords(band) ?: return null
        val allowance = Pace.allowance(used, resets.toEpochMilli() - now.toEpochMilli())
            ?: return words
        val tail = if (toLast && allowance is Pace.Allowance.PerDay) " to last" else ""
        return words + DOT + PaceText.allowance(allowance) + tail
    }

    /**
     * Weekly leads: the single collapsed row's line and a Duet header's sub, without the
     * companion (the caller appends it, styled). Null = the caller's existing "Not started"
     * / "No reading" line applies.
     */
    fun weeklyLine(weekly: UsageWindow?, elapsed: Double?, use24h: Boolean): String? {
        if (weekly?.percent == null || weekly.resetsAt == null) return null
        if (Pace.isSpent(weekly.percent)) return outUntil(weekly, use24h)
        return paceWords(weekly, elapsed)
    }

    /** "resets Sun" — the companion slot on an account with no 5h, and a Duet header's tail. */
    fun resetsDay(weekly: UsageWindow?): String? = weekly?.resetsAt?.let { "resets ${Fmt.weekday(it)}" }

    /**
     * The expanded single header's second line under a Weekly lead: the distance from the
     * pace mark and the reset day, "13 over the pace mark (39%) · resets Sun"; in Vs pace
     * mode the usage, "52% used · resets Sun".
     */
    fun weeklySecondLine(weekly: UsageWindow?, elapsed: Double?, mode: PaceText.Mode): String? {
        val day = resetsDay(weekly) ?: return null
        val distance = PaceText.notificationDistance(mode, weekly?.percent, elapsed) ?: return null
        return distance + DOT + day
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

    /** The expanded header's second line under a 5h lead: "Projected to hit 100% at 3:25 PM". */
    fun sessionSecondLine(hitsLimitAtMs: Long?, use24h: Boolean): String? =
        hitsLimitAtMs?.let { "Projected to hit 100% at ${Fmt.timeOnly(Instant.ofEpochMilli(it), use24h)}" }

    /** A panel's Weekly row sub under a 5h lead: "Slow down · ≈11%/day · Sun". */
    fun weeklyRowSub(weekly: UsageWindow?, elapsed: Double?, use24h: Boolean): String? {
        if (weekly?.percent == null || weekly.resetsAt == null) return null
        if (Pace.isSpent(weekly.percent)) return outUntil(weekly, use24h)
        val words = paceWords(weekly, elapsed) ?: return null
        return words + DOT + Fmt.weekday(weekly.resetsAt)
    }

    /**
     * A figure in the chosen Usage display: Used, Left, or the distance from the pace
     * mark (CCRM-100 (Vs Pace Display)) — "—" with no reading.
     */
    fun figure(pct: Double?, elapsed: Double?, mode: PaceText.Mode): String = when {
        pct == null -> "—"
        mode == PaceText.Mode.VS_PACE -> PaceText.vsPaceFigure(pct, elapsed)
        else -> "${Fmt.usageInt(pct, mode == PaceText.Mode.LEFT)}%"
    }

    /** The small caption under a figure: "LEFT", "VS PACE", or none. */
    fun figureCaption(pct: Double?, elapsed: Double?, mode: PaceText.Mode): String? = when {
        pct == null -> null
        mode == PaceText.Mode.LEFT -> "LEFT"
        mode == PaceText.Mode.VS_PACE && Pace.vsPace(pct, elapsed) != null -> "VS PACE"
        else -> null
    }
}
