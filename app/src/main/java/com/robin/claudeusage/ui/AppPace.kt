package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.UsageWindow
import java.time.Instant

/**
 * RUNBOOK-v2.0 Step 5: the app cards' words once Weekly leads — CCRM-96 (Weekly First),
 * CCRM-97 (Pace Number), CCRM-100 (Vs Pace Display). Pure, like [compactLine]: the card
 * owns the colours, this owns the words and which part of them is emphasised.
 * Wireframes: `design/2026-11-02-weekly-first-pace.html` §5 (A1/A2), `-pace-line-label.html`
 * (E1–E3), `-vs-pace-display.html` §3.
 */
object AppPace {

    private const val DOT = " · "

    /**
     * One line, with [emphasis] the part of [text] drawn bold in the orange step (today's
     * over-pace emphasis); null when nothing is emphasised. Always a substring of [text].
     */
    data class Line(val text: String, val emphasis: String? = null)

    /** Slow down and Nearly out are the over-pace bands; spent is drawn the same way. */
    fun overPace(band: Pace.Band): Boolean =
        band == Pace.Band.SLOW_DOWN || band == Pace.Band.NEARLY_OUT || band == Pace.Band.SPENT

    private fun msLeft(resets: Instant, now: Instant) = resets.toEpochMilli() - now.toEpochMilli()

    /**
     * The open Weekly card's first line under the chart: "Slow down · ≈11%/day until Sun";
     * under a day, "Use before reset · 40% left, 6h" (wireframe E1, no day); spent, "Out
     * until Sun 9:30 PM". Null with no reading or no reset clock. A cap's window words the
     * same way (the chips switch the pace lines with the chart, as today).
     */
    fun weeklyOpen(window: UsageWindow?, elapsed: Double?, use24h: Boolean, now: Instant = Instant.now()): Line? {
        val used = window?.percent ?: return null
        val resets = window.resetsAt ?: return null
        val band = Pace.bandOrNull(used, elapsed) ?: return null
        if (band == Pace.Band.SPENT) {
            val text = "Out until ${Fmt.dayTime(resets, use24h)}"
            return Line(text, text)
        }
        val words = PaceText.bandWords(band) ?: return null
        val allowance = Pace.allowance(used, msLeft(resets, now))
        val text = when (allowance) {
            null -> words
            is Pace.Allowance.PerDay -> words + DOT + PaceText.allowance(allowance) + " until " + Fmt.weekday(resets)
            is Pace.Allowance.Hours -> words + DOT + PaceText.allowance(allowance)
        }
        return Line(text, text.takeIf { overPace(band) })
    }

    /**
     * The folded Weekly card's one line: "Slow down · ≈11%/day · resets Sun", the tight
     * form bold when over pace and the reset day muted; spent "Out until Sun 9:30 PM"; no
     * reset clock, today's "Starts when a message is sent".
     */
    fun weeklyFolded(window: UsageWindow?, elapsed: Double?, use24h: Boolean, now: Instant = Instant.now()): Line {
        val resets = window?.resetsAt ?: return Line("Starts when a message is sent")
        val used = window.percent ?: return Line("Resets ${Fmt.relIn(resets)}")
        val band = Pace.bandOrNull(used, elapsed) ?: return Line("Resets ${Fmt.relIn(resets)}")
        if (band == Pace.Band.SPENT) {
            val text = "Out until ${Fmt.dayTime(resets, use24h)}"
            return Line(text, text)
        }
        val tight = PaceText.tight(band, Pace.allowance(used, msLeft(resets, now)))
            ?: return Line("Resets ${Fmt.relIn(resets)}")
        return Line(tight + DOT + "resets " + Fmt.weekday(resets), tight.takeIf { overPace(band) })
    }

    /**
     * The folded 5h card's line — no Pace on the session (decision 2), core §3's projection
     * instead: "Resets in 2h 20m · on track for ~69%". A projection that reaches 100%
     * before the reset reads "on track for 100% at 3:25 PM" and is emphasised. No
     * projection yet, or already at the limit: the reset clause alone.
     */
    fun sessionFolded(window: UsageWindow?, estimate: Projection.Estimate?, use24h: Boolean): Line {
        val resets = window?.resetsAt ?: return Line("Starts when a message is sent")
        val reset = "Resets ${Fmt.relIn(resets)}"
        if (estimate == null || (window.percent ?: 0.0) >= 100.0) return Line(reset)
        val hits = estimate.hitsLimitAtMs
        return if (hits != null) {
            val clause = "on track for 100% at ${Fmt.timeOnly(Instant.ofEpochMilli(hits), use24h)}"
            Line(reset + DOT + clause, clause)
        } else {
            Line(reset + DOT + "on track for ~${estimate.pctAtReset.toInt()}%")
        }
    }

    /**
     * CCRM-103 (Hybrid Notification): the account tile's 5h line (shape grammar rev D §1),
     * [sessionFolded]'s words after a ring glyph that stands for "5h": "resets in 2h 20m ·
     * on track for ~69%". Null — no line — before the window has a reading and a clock.
     */
    fun fiveHourLine(window: UsageWindow?, estimate: Projection.Estimate?, use24h: Boolean): Line? {
        if (window?.resetsAt == null || window.percent == null) return null
        val line = sessionFolded(window, estimate, use24h)
        return line.copy(text = line.text.replaceFirstChar { it.lowercaseChar() })
    }

    /**
     * CCRM-102 (Spent Week Gate): the one full-opacity note under a greyed 5h card,
     * "Weekly limit reached — 5h can't be used until Sun 9:30 PM".
     */
    fun spentWeekNote(weekly: UsageWindow, use24h: Boolean): String =
        "Weekly limit reached — 5h can’t be used until ${Fmt.dayTime(weekly.resetsAt, use24h)}"

    /**
     * A card or row figure in the Usage display mode, and the small word after it:
     * Used "52%"; Left "48%" + "left"; Vs pace "10%" and no word — its colour
     * ([PaceText.vsPaceTone]) says the side since the shape grammar (rev D §7b) — falling
     * back to the Used figure when spent, unclocked or unread.
     */
    fun figure(pct: Double?, elapsed: Double?, mode: PaceText.Mode): Pair<String, String?> = when {
        pct == null -> "—" to null
        mode == PaceText.Mode.LEFT -> "${Fmt.usageInt(pct, true)}%" to "left"
        mode == PaceText.Mode.VS_PACE && Pace.vsPace(pct, elapsed) != null ->
            PaceText.vsPaceFigure(pct, elapsed) to null
        else -> "${pct.toInt()}%" to null
    }
}
