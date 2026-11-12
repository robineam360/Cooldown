package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.data.Projection
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * CCRM-109 (Quiet Accounts) and CCRM-111 (Map Layers), core §6.3 and the mobile foot
 * line (wireframe `design/2026-11-10-v20-feedback-wireframes.html` rev B §5 and §7). Pure, so the fixtures
 * (`printed.json` → `quiet`, `mapLabels.figures`, `callout`, `projectionLayer`; `spec/mobile/fixtures/map-foot`)
 * test every rule without drawing. iOS's `PaceMapRules` is the same rules in Swift.
 */
object PaceMapRules {

    // ---- CCRM-109 (Quiet Accounts)

    /** The row's printed Weekly used figure: a window row truncates, so 0.99 prints "0%" (the never-overstate rule). */
    fun rowPrints(usedPct: Double?): String? = usedPct?.let { "${it.toInt()}%" }

    /** Whether the row prints "0%" Weekly used: the account leaves the map (the list still shows it). */
    fun isQuiet(usedPct: Double?): Boolean = usedPct != null && usedPct.toInt() < 1

    /** On the map: a reading whose row prints 1% or more, with a reset clock (the x). */
    fun isPlotted(usedPct: Double?, clocked: Boolean): Boolean = usedPct != null && clocked && !isQuiet(usedPct)

    data class Account(val usedPct: Double?, val clocked: Boolean)

    /** Every account at 0%: the message in the behind wedge. */
    data class Empty(val title: String, val detail: String) {
        companion object {
            val MESSAGE = Empty("Nothing to plot yet", "every account is at 0% this week")
        }
    }

    /** The card's foot line: [left] "each account's own week", [count] "2 at 0% · 1 no reading" (null when nothing is left off). */
    data class Foot(val left: String, val count: String?, val empty: Empty?)

    /**
     * The foot line (mobile): readings that print 0% are counted "N at 0%" (a not-started week included); accounts
     * with no reading — and the rare reading of 1% or more with no reset clock, which has no x — "N no reading"; each
     * part left out at zero. The message shows when every account is at 0% (rev B draws no other empty map: with
     * some accounts unread the frame shows the count alone).
     */
    fun foot(accounts: List<Account>): Foot {
        val atZero = accounts.count { isQuiet(it.usedPct) }
        val noReading = accounts.count { it.usedPct == null || (!isQuiet(it.usedPct) && !it.clocked) }
        val plotted = accounts.count { isPlotted(it.usedPct, it.clocked) }
        val parts = listOfNotNull(
            if (atZero > 0) "$atZero at 0%" else null,
            if (noReading > 0) "$noReading no reading" else null,
        )
        return Foot(
            left = PaceText.MAP_FOOT_NOTE,
            count = parts.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            empty = if (plotted == 0 && atZero > 0 && noReading == 0) Empty.MESSAGE else null,
        )
    }

    // ---- CCRM-111 (Map Layers): the figure after the name

    /**
     * The figure the Figures layer prints after a name: "52%", "48% left", "−13%" / "+19%" / "0%" (Vs pace, signed,
     * falling back to Used with no reset clock). Used and Left truncate like the row; spent prints "100%", or "0%" in
     * Left (as drawn, no "left"; Robin, 2026-11-11). Null with no reading.
     */
    fun figure(mode: PaceText.Mode, usedPct: Double?, elapsedPct: Double?): String? {
        val used = usedPct ?: return null
        if (Pace.isSpent(used)) return if (mode == PaceText.Mode.LEFT) "0%" else "100%"
        if (mode == PaceText.Mode.VS_PACE) Pace.vsPace(used, elapsedPct)?.let { return PaceText.signed(it) }
        if (mode == PaceText.Mode.LEFT) return "${Fmt.usageInt(used, true)}% left"
        return "${used.toInt()}%"
    }

    /** "Teams 52%", the name and its figure as one label (a name alone with no reading). */
    fun label(name: String, mode: PaceText.Mode, usedPct: Double?, elapsedPct: Double?): String? =
        figure(mode, usedPct, elapsedPct)?.let { "$name $it" }

    /** Which colour the figure takes: the severity colour for Used and Left (and the Vs pace fallback), green under, red over, ink on the mark. */
    fun figureTone(mode: PaceText.Mode, usedPct: Double?, elapsedPct: Double?): PaceText.VsTone =
        if (mode != PaceText.Mode.VS_PACE || usedPct == null || Pace.isSpent(usedPct)) PaceText.VsTone.USAGE
        else PaceText.vsPaceTone(usedPct, elapsedPct)

    /** The figure as TalkBack reads it: "minus 13 percent", "plus 19 percent", "48 percent left". */
    fun spokenFigure(figure: String): String =
        figure.replace(PaceText.MINUS, "minus ").replace("+", "plus ").replace("%", " percent")

    /** What TalkBack reads for a dot: its name, figure, pace line and forecast, joined by commas. */
    fun spoken(name: String, figure: String?, line: String?, forecast: String?): String =
        listOfNotNull(name, figure?.let(::spokenFigure), line, forecast).joinToString(", ")

    // ---- CCRM-111: the callout's third line and the projection layer

    /** Whether the account gets a forecast at all: an estimate, not spent, with a reset clock. */
    private fun forecastable(estimate: Projection.Estimate?, usedPct: Double?, resetMs: Long?): Projection.Estimate? =
        if (estimate == null || resetMs == null || Pace.isSpent(usedPct)) null else estimate

    /**
     * The callout's third line: "~74% at reset" (rounded half up), or "100% at Wed 6:10 PM" when it runs out before
     * the reset (the weekday dropped later the same local day). Null — no third line — with no estimate, spent, or
     * no reset clock.
     */
    fun forecast(
        estimate: Projection.Estimate?,
        usedPct: Double?,
        resetMs: Long?,
        nowMs: Long,
        use24h: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String? {
        val e = forecastable(estimate, usedPct, resetMs) ?: return null
        val hit = e.hitsLimitAtMs
        if (hit != null && hit < resetMs!!) return "100% at " + PaceText.dayClock(Instant.ofEpochMilli(hit), nowMs, use24h, zone, locale)
        return "~${PaceText.halfUp(e.pctAtReset)}% at reset"
    }

    /** Whether the forecast line (and the ring) is the red "runs out" one. */
    fun runsOut(estimate: Projection.Estimate?, usedPct: Double?, resetMs: Long?): Boolean {
        val e = forecastable(estimate, usedPct, resetMs) ?: return false
        val hit = e.hitsLimitAtMs ?: return false
        return hit < resetMs!!
    }

    /** Where the tail ends, in the map's own units (share of the week gone, used %); [runsOut] puts the ring on the 100% line, red. */
    data class ProjectionEnd(val weekGonePct: Double, val usedPct: Double, val runsOut: Boolean)

    /**
     * The Projection layer's tail end: a hollow ring on the right edge at the projected %, or on the 100% line at the
     * week-gone % where it runs out. Null — no tail, no ring — with no estimate, spent, or no reset clock.
     */
    fun projection(estimate: Projection.Estimate?, usedPct: Double?, resetMs: Long?, windowLengthMs: Long): ProjectionEnd? {
        val e = forecastable(estimate, usedPct, resetMs) ?: return null
        val reset = resetMs!!
        val hit = e.hitsLimitAtMs
        if (hit != null && hit < reset) {
            val start = reset - windowLengthMs
            return ProjectionEnd((hit - start).toDouble() / windowLengthMs * 100.0, 100.0, true)
        }
        return ProjectionEnd(100.0, min(100.0, e.pctAtReset), false)
    }

    // ---- CCRM-111: the trail's last day, the pin and the hit test

    /**
     * The trail's last 24 h (a seventh of the week): the points from `gone − 100/7` on, starting where the line
     * crosses it. Empty when no point is that recent.
     */
    fun lastDay(pts: List<Pair<Double, Double>>, gone: Double): List<Pair<Double, Double>> {
        val from = gone - 100.0 / 7
        val k = pts.indexOfFirst { it.first >= from }
        if (k < 0) return emptyList()
        if (k == 0) return pts
        val a = pts[k - 1]
        val b = pts[k]
        val t = if (b.first == a.first) 0.0 else (from - a.first) / (b.first - a.first)
        return listOf(from to a.second + t * (b.second - a.second)) + pts.subList(k, pts.size)
    }

    /**
     * Whether an account's trail is drawn: [trail] ends on the live point, so it needs at least two samples behind it
     * (iOS: `samples.count > 1`) — an account with no history, or one reading, has no made-up line (iOS judge F1).
     */
    fun hasTrail(trail: List<Pair<Double, Double>>): Boolean = trail.size > 2

    /** A tap's effect: [pinned] afterwards and the account to [open], if any. */
    data class Tap(val pinned: String?, val open: String?)

    /**
     * A tap on the empty plot clears the pin; on the pinned account it opens that account (the pin stays); on
     * another account the pin moves there.
     */
    fun tap(pinned: String?, hit: String?): Tap = when {
        hit == null -> Tap(null, null)
        pinned == hit -> Tap(pinned, hit)
        else -> Tap(hit, null)
    }

    /** The pin is dropped when its account leaves the map (quiet, filtered, signed out). */
    fun keepPin(pinned: String?, keys: List<String>): String? = pinned?.takeIf { it in keys }

    /** The 48 dp tap target around a dot (wireframe §7). */
    const val TARGET_DP = 48f

    /** A dot's centre in px and the box of its drawn name, if any. */
    data class Target(val key: String, val x: Float, val y: Float, val name: MapLabels.Rect?)

    /**
     * A tap: the nearest dot whose 48 dp target, or whose name (its box widened to 48 dp high), holds the point;
     * null = the empty plot. [d] is px per dp.
     */
    fun hit(targets: List<Target>, atX: Float, atY: Float, d: Float): String? {
        val half = TARGET_DP * d / 2
        val candidates = targets.filter { t ->
            if (abs(atX - t.x) <= half && abs(atY - t.y) <= half) return@filter true
            val b = t.name ?: return@filter false
            val grow = max(0.0, (TARGET_DP * d - b.h) / 2)
            atX >= b.x && atX <= b.right && atY >= b.y - grow && atY <= b.bottom + grow
        }
        return candidates.minByOrNull { hypot((it.x - atX).toDouble(), (it.y - atY).toDouble()) }?.key
    }

    /**
     * Where the callout's top-left goes: above-right of its dot, flipped left at the right edge and below at the
     * top, clamped to the plot ([left], [top], [right], [bottom] in px). [d] is px per dp.
     */
    fun calloutOrigin(
        dotX: Float, dotY: Float, w: Float, h: Float,
        left: Float, top: Float, right: Float, bottom: Float, d: Float,
    ): Pair<Float, Float> {
        var x = dotX + 10 * d
        var y = dotY - h - 8 * d
        if (x + w > right + 6 * d) x = dotX - 10 * d - w
        if (y < top - 6 * d) y = dotY + 10 * d
        x = min(max(x, left), max(left, right - w))
        y = min(max(y, top - 6 * d), max(top - 6 * d, bottom - h))
        return x to y
    }
}
