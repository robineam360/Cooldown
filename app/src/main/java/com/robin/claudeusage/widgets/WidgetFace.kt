package com.robin.claudeusage.widgets

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.robin.claudeusage.R
import com.robin.claudeusage.alerts.Alerts
import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.ui.BarRenderer
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.PACE_DEAD_ZONE
import com.robin.claudeusage.ui.RingRenderer
import com.robin.claudeusage.ui.Rooms
import com.robin.claudeusage.ui.providerMarkRes
import java.time.Instant
import java.time.ZoneId

/**
 * The pure half of the v1.8 widget faces (CCRM-78 (Widgets Reborn)): what each face
 * *says*, decided without a `Context` so every state of the ROADMAP's S1–S14 table is
 * pinned by a unit test rather than by looking at a launcher. [WidgetFace.render] only
 * lays this out.
 */

/** The four faces (Robin, Q1). Never a combined figure. */
enum class Face { RING, NUMBER, COUNTDOWN, STRIP }

/** Which window a face shows. Stored in `w<id>.window` by [WidgetPrefs]. */
enum class FaceWindow(val lengthMs: Long, val word: String) {
    SESSION(Projection.SESSION_MS, "5h"),
    WEEKLY(Projection.WEEKLY_MS, "Weekly"),
}

/** Solid / Gradient / Transparent (Robin, Q4; three since rev C). Default Solid. */
enum class FaceBackground { SOLID, GRADIENT, TRANSPARENT }

/**
 * Every size a face is drawn at — at most three per face (R10). Sizes are the Fold 7
 * **cover** cells (90.75×84 dp, so 4×2 = 363×168 dp), which is the frame the approved
 * wireframe fits every run of text against; the inner grid (~118×100 dp a cell) is
 * unverified until the Step 7 device pass.
 */
enum class Bucket(val face: Face, val label: String, val widthDp: Float, val heightDp: Float) {
    RING_1X1(Face.RING, "1×1", 91f, 84f),
    RING_2X2(Face.RING, "2×2", 181f, 168f),
    NUMBER_2X1(Face.NUMBER, "2×1", 181f, 84f),
    NUMBER_4X1(Face.NUMBER, "4×1", 363f, 84f),
    NUMBER_4X2(Face.NUMBER, "4×2", 363f, 168f),
    COUNTDOWN_2X1(Face.COUNTDOWN, "2×1", 181f, 84f),
    COUNTDOWN_2X2(Face.COUNTDOWN, "2×2", 181f, 168f),
    STRIP_4X1(Face.STRIP, "4×1", 363f, 84f),
    STRIP_4X2(Face.STRIP, "4×2", 363f, 168f);

    /** 160 dp or more: the synthetic marker is the full-width ribbon, not the dot (R8). */
    val tall: Boolean get() = heightDp >= 160f

    /** 12 dp a side on the cover, 8 dp top and bottom on the one-row 84 dp faces. */
    val padXDp: Float get() = 12f
    val padYDp: Float get() = if (heightDp <= 84f) 8f else 12f
    val innerWidthDp: Float get() = widthDp - 2 * padXDp
    val innerHeightDp: Float get() = heightDp - 2 * padYDp

    companion object {
        fun of(face: Face): List<Bucket> = entries.filter { it.face == face }
    }
}

/** The ROADMAP's state table. A cell carries every state that applies to it at once. */
enum class StateId(val label: String) {
    S1("normal"),
    S2("above pace"),
    S3("100%"),
    S4("no 5h window"),
    S5("window not started"),
    S6("reset passed"),
    S7("sign-in broken"),
    S8("stale"),
    S9("account removed"),
    S10("unassigned / no accounts"),
    S11("Left mode"),
    S12("Free plan"),
    S13("synthetic"),
    S14("unavailable"),
}

/**
 * One account as a face needs it, resolved by the caller (the providers at Step 4, the
 * Faces gallery at Step 5) from the cache. [accentArgb] is already the account's
 * accent for the face's theme; the severity ladder is applied here.
 */
data class AccountInput(
    val key: String,
    val label: String,
    val provider: Provider,
    val accentArgb: Int,
    val data: UsageData?,
    /** Epoch ms of the last successful fetch; 0 = never. */
    val fetchedAt: Long,
    val authState: AuthState,
    /** CCBG-27 (Free Plan 403): the plan reports no usage at all (S12). */
    val planUnsupported: Boolean = false,
    /** `Projection.estimate(...).hitsLimitAtMs` per window, when non-null (Countdown 2×2). */
    val sessionRunsOutAtMs: Long? = null,
    val weeklyRunsOutAtMs: Long? = null,
)

/** What one placed widget is asked to draw. */
data class FaceInput(
    val face: Face,
    /** `w<id>.account`; null or empty is the unassigned state (R5). Unused by the Strip. */
    val accountKey: String?,
    val window: FaceWindow,
    val background: FaceBackground,
    val dark: Boolean,
    /** Every account in registry order (CCRM-71 (Account Order)). */
    val accounts: List<AccountInput>,
    /** CCRM-22 (Used or Left): the figures flip (S11). */
    val usageLeft: Boolean,
    val showOverPace: Boolean,
    /** R8's process-wide synthetic series is on (S13). */
    val synthetic: Boolean,
    /** R9: this face was pulled in a later version and draws the recovery face (S14). */
    val unavailable: Boolean,
    val nowMs: Long,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/** A face-wide message that replaces every reading. */
enum class FaceMessage {
    /** S9 — R5: never repoint silently. */
    REMOVED,
    /** S10 with an empty registry, or an account that has never signed in. */
    SIGN_IN,
    /** S12. */
    FREE,
    /** S14. */
    UNAVAILABLE,
}

/** How the Countdown's headline reads (CCRM-81 (Countdown Face)). */
enum class CountForm {
    /** A live chronometer, launcher-ticked, counting down to [Cell.resetsAt]. */
    LIVE,
    /** Weekly more than 24 h out: the absolute "Sat 9:10 PM" at the count's size. */
    ABSOLUTE,
    /** S6: "Reset 9:10 PM", past tense, no count. */
    RESET_PASSED,
    /** S5 (or no reading at all): no count and no clock. */
    NONE,
}

/** One gauge's worth of reading: the whole face for three faces, one ring on the Strip. */
data class Cell(
    val key: String,
    val name: String,
    val provider: Provider,
    val accentArgb: Int,
    /** The Room card colour this account's Solid background uses. */
    val cardArgb: Int,
    val states: Set<StateId>,
    /** The window actually shown — Weekly when the account has no 5h window (S4). */
    val window: FaceWindow,
    /** The account has both windows (Number 4×2's chips). */
    val hasBothWindows: Boolean,
    /** Null → no reading: the extent alone, never 0% (S5, S6, S12, never fetched). */
    val pct: Double?,
    val elapsed: Double?,
    /** "38%", "62%" in Left mode, "—" with no reading. */
    val figure: String,
    /** The severity-ladder colour of the figure, the ring and the bar. */
    val fillArgb: Int,
    val resetsAt: Instant?,
    /** The absolute sub-line (R4), or "Stale", "Starts when a message is sent". */
    val sub: String?,
    /** The Strip 4×2's short line under a ring: "9:10 PM", "Reset 9:10 PM", "Stale". */
    val shortReset: String?,
    val countForm: CountForm,
    /** "~ runs out 4:20 PM" (CCRM-30 (Estimate Honesty)'s "~"), or null. */
    val estimate: String?,
) {
    val dim: Boolean get() = StateId.S7 in states || StateId.S8 in states
    val brokenDot: Boolean get() = StateId.S7 in states
    val unassigned: Boolean get() = StateId.S10 in states
    val left: Boolean get() = StateId.S11 in states
    val full: Boolean get() = StateId.S3 in states
    val free: Boolean get() = StateId.S12 in states
    val weeklyTagged: Boolean get() = window == FaceWindow.WEEKLY
}

/** Everything [WidgetFace.render] needs, and nothing it has to decide. */
data class FaceState(
    val face: Face,
    val background: FaceBackground,
    val dark: Boolean,
    val showOverPace: Boolean,
    val synthetic: Boolean,
    /** Non-null → the face is this message and nothing else. */
    val message: FaceMessage?,
    /** One cell for Ring, Number and Countdown; up to four for the Strip. */
    val cells: List<Cell>,
    /** The Strip's "+N" when there are more than four accounts. */
    val overflow: Int,
    /** R4 / Q11: "as of 9:10 PM", the snapshot's absolute age, or null. */
    val asOf: String?,
    /** Every state on the face, for the gallery caption and the tests. */
    val states: Set<StateId>,
    /** The Strip's neutral card, or the one account's Room card. */
    val cardArgb: Int,
    /** The Gradient wash's hue — the account's accent, or neutral ink on the Strip. */
    val washArgb: Int,
    val nowMs: Long,
    val zone: ZoneId,
)

/** The state table: [FaceInput] → [FaceState]. Pure. */
object FaceStates {

    /** The Strip shows the first four accounts and then "+N". */
    const val STRIP_MAX = 4

    private const val NEUTRAL_CARD_DARK = 0xFF1A1A1A.toInt()
    private const val NEUTRAL_CARD_LIGHT = 0xFFFCF8F4.toInt()

    fun of(input: FaceInput): FaceState {
        val faceWide = buildSet {
            if (input.synthetic) add(StateId.S13)
            if (input.usageLeft) add(StateId.S11)
        }
        fun message(m: FaceMessage, s: StateId, card: Int = neutralCard(input.dark)) = FaceState(
            face = input.face, background = input.background, dark = input.dark,
            showOverPace = input.showOverPace, synthetic = input.synthetic, message = m,
            cells = emptyList(), overflow = 0, asOf = null, states = faceWide + s,
            cardArgb = card, washArgb = neutralWash(input.dark), nowMs = input.nowMs,
            zone = input.zone,
        )

        if (input.unavailable) return message(FaceMessage.UNAVAILABLE, StateId.S14)
        if (input.accounts.isEmpty()) return message(FaceMessage.SIGN_IN, StateId.S10)

        if (input.face == Face.STRIP) {
            val shown = input.accounts.take(STRIP_MAX)
            // Each ring on its own headline window (CCRM-82 (Accounts Strip)).
            val cells = shown.map { cell(input, it, FaceWindow.SESSION, unassigned = false) }
            val newest = shown.maxOf { it.fetchedAt }
            return FaceState(
                face = input.face, background = input.background, dark = input.dark,
                showOverPace = input.showOverPace, synthetic = input.synthetic, message = null,
                cells = cells, overflow = (input.accounts.size - STRIP_MAX).coerceAtLeast(0),
                asOf = asOf(newest, input), states = faceWide + cells.flatMap { it.states },
                cardArgb = neutralCard(input.dark), washArgb = neutralWash(input.dark),
                nowMs = input.nowMs, zone = input.zone,
            )
        }

        val key = input.accountKey?.takeIf { it.isNotEmpty() }
        val account = if (key == null) input.accounts.first()
        else input.accounts.firstOrNull { it.key == key }
            ?: return message(FaceMessage.REMOVED, StateId.S9)
        val card = card(account.provider, input.dark)
        if (account.planUnsupported) return message(FaceMessage.FREE, StateId.S12, card)
        if (account.authState == AuthState.NO_CREDENTIALS && account.data == null) {
            return message(FaceMessage.SIGN_IN, StateId.S10, card)
        }
        val c = cell(input, account, input.window, unassigned = key == null)
        return FaceState(
            face = input.face, background = input.background, dark = input.dark,
            showOverPace = input.showOverPace, synthetic = input.synthetic, message = null,
            cells = listOf(c), overflow = 0, asOf = asOf(account.fetchedAt, input),
            states = faceWide + c.states, cardArgb = card, washArgb = c.accentArgb,
            nowMs = input.nowMs, zone = input.zone,
        )
    }

    /**
     * One account on one window, classified. [asked] falls back to the other window when
     * the account does not have it — Weekly for a weekly-only account (S4), the same
     * headline rule the notification uses (CCRM-54 (ChatGPT Account) part 2).
     */
    fun cell(input: FaceInput, a: AccountInput, asked: FaceWindow, unassigned: Boolean): Cell {
        val now = input.nowMs
        val data = a.data
        val states = mutableSetOf<StateId>()
        if (input.usageLeft) states += StateId.S11
        if (input.synthetic) states += StateId.S13
        if (unassigned) states += StateId.S10

        val card = card(a.provider, input.dark)
        if (a.planUnsupported) {
            states += StateId.S12
            return Cell(
                a.key, a.label, a.provider, a.accentArgb, card, states, asked,
                hasBothWindows = false, pct = null, elapsed = null, figure = "—",
                fillArgb = a.accentArgb, resetsAt = null, sub = "No usage on this plan",
                shortReset = "Free", countForm = CountForm.NONE, estimate = null,
            )
        }

        val hasSession = data?.session != null
        val hasWeekly = data?.weekly != null
        val weeklyOnly = data != null && !hasSession && hasWeekly
        val window = shownWindow(data, asked)
        if (weeklyOnly) states += StateId.S4
        val w: UsageWindow? = if (window == FaceWindow.WEEKLY) data?.weekly else data?.session
        val resetsAt = w?.resetsAt

        if (a.authState == AuthState.REAUTH_NEEDED) states += StateId.S7
        if (a.fetchedAt > 0 && now >= a.fetchedAt + Alerts.STALE_DATA_MS) states += StateId.S8

        // S6 is decided at draw time from resetsAt and fetchedAt (R4), never from an
        // expectation that a redraw will arrive.
        val resetPassed = resetsAt != null && resetsAt.toEpochMilli() <= now &&
            a.fetchedAt < resetsAt.toEpochMilli()
        val notStarted = data != null && (w == null || resetsAt == null)

        var pct: Double? = null
        var elapsed: Double? = null
        when {
            resetPassed -> states += StateId.S6
            notStarted -> states += StateId.S5
            w?.percent != null -> {
                pct = w.percent
                elapsed = elapsed(resetsAt, window.lengthMs, now)
                when {
                    pct.toInt() >= 100 -> states += StateId.S3
                    elapsed != null && pct > elapsed + PACE_DEAD_ZONE -> states += StateId.S2
                }
            }
        }
        if (states.none { it in READING_STATES } && pct != null) states += StateId.S1

        val clock = resetsAt?.let { Fmt.widgetClock(it, now, input.zone) }
        val sub = when {
            StateId.S8 in states -> "Stale"
            resetPassed -> "Reset $clock"
            notStarted -> NOT_STARTED
            data == null -> NO_READING
            clock != null -> "Resets $clock"
            else -> null
        }
        val shortReset = when {
            StateId.S8 in states -> "Stale"
            resetPassed -> "Reset $clock"
            notStarted -> "Not started"
            // A signed-in account with no payload yet (Fable call, Step 4): the ring would
            // otherwise be the only one on the Strip with an empty line.
            data == null -> "No reading"
            else -> clock
        }
        val countForm = when {
            resetPassed -> CountForm.RESET_PASSED
            notStarted || resetsAt == null -> CountForm.NONE
            window == FaceWindow.WEEKLY && resetsAt.toEpochMilli() - now > DAY_MS -> CountForm.ABSOLUTE
            else -> CountForm.LIVE
        }
        val runsOut = if (window == FaceWindow.WEEKLY) a.weeklyRunsOutAtMs else a.sessionRunsOutAtMs
        val estimate = runsOut
            ?.takeIf { pct != null && StateId.S3 !in states && it > now }
            ?.let { "~ runs out ${Fmt.widgetClock(Instant.ofEpochMilli(it), now, input.zone)}" }

        return Cell(
            key = a.key, name = a.label, provider = a.provider, accentArgb = a.accentArgb,
            cardArgb = card, states = states, window = window,
            hasBothWindows = hasSession && hasWeekly, pct = pct, elapsed = elapsed,
            figure = pct?.let { "${Fmt.usageInt(it, input.usageLeft)}%" } ?: "—",
            fillArgb = barColor(pct, a.accentArgb, input.dark), resetsAt = resetsAt,
            sub = sub, shortReset = shortReset, countForm = countForm, estimate = estimate,
        )
    }

    /**
     * The window a face actually shows for [asked]: Weekly on a weekly-only account (S4,
     * the notification's CCRM-54 (ChatGPT Account) headline rule), 5h when Weekly was
     * asked of an account that has only 5h. [Transitions] resolves windows through this
     * too, so the alarm and the face never disagree.
     */
    fun shownWindow(data: UsageData?, asked: FaceWindow): FaceWindow {
        val hasSession = data?.session != null
        val hasWeekly = data?.weekly != null
        return when {
            data != null && !hasSession && hasWeekly -> FaceWindow.WEEKLY
            asked == FaceWindow.WEEKLY && !hasWeekly && hasSession -> FaceWindow.SESSION
            else -> asked
        }
    }

    /** The states that describe the reading itself; S1 is "none of these". */
    private val READING_STATES = setOf(StateId.S2, StateId.S3, StateId.S5, StateId.S6)

    const val NOT_STARTED = "Starts when a message is sent"

    /** No payload yet on a signed-in account — the notification's own wording. */
    const val NO_READING = "No reading yet"

    private const val DAY_MS = 24 * 60 * 60_000L

    private fun asOf(fetchedAt: Long, input: FaceInput): String? =
        fetchedAt.takeIf { it > 0 }
            ?.let { "as of ${Fmt.widgetClock(Instant.ofEpochMilli(it), input.nowMs, input.zone)}" }

    /** Elapsed percent of a window ending at [resetsAt], at [nowMs]. Null with no clock. */
    fun elapsed(resetsAt: Instant?, lengthMs: Long, nowMs: Long): Double? {
        resetsAt ?: return null
        val remaining = (resetsAt.toEpochMilli() - nowMs).toDouble()
        return ((lengthMs - remaining) / lengthMs * 100.0).coerceIn(0.0, 100.0)
    }

    /** `Palette.barColor` on ARGB ints: accent below 80%, then yellow / orange / red. */
    fun barColor(pct: Double?, accentArgb: Int, dark: Boolean): Int {
        val p = pct ?: 0.0
        return when {
            p >= 100.0 -> if (dark) 0xFFFF5252.toInt() else 0xFFC62828.toInt()
            p > 90.0 -> if (dark) 0xFFFFA726.toInt() else 0xFFF57C00.toInt()
            p > 80.0 -> if (dark) 0xFFFDD663.toInt() else 0xFFF9A825.toInt()
            else -> accentArgb
        }
    }

    fun card(provider: Provider, dark: Boolean): Int =
        Rooms.forProvider(provider).let { (if (dark) it.cardDark else it.cardLight).toInt() }

    fun neutralCard(dark: Boolean): Int = if (dark) NEUTRAL_CARD_DARK else NEUTRAL_CARD_LIGHT

    /** The Strip's neutral wash: 6% ink toward the opposite tone. */
    fun neutralWash(dark: Boolean): Int = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
}

/**
 * The drawn half (CCRM-78 (Widgets Reborn) §Where): [render] turns one [FaceState] into
 * **one single-size** `RemoteViews` for one [Bucket]. The providers (RUNBOOK.md Step 4)
 * compose their `Map<SizeF, RemoteViews>` from it and the Faces gallery (CCRM-84 (Faces
 * Gallery)) inflates the very same call, so the two cannot drift.
 *
 * Text stays real `TextView`s at true sp; only the ring and the bar are bitmaps, drawn at
 * bucket size and never scaled (R10). [bitmapBytes] is the pure estimate of what one
 * composed update carries, and the test holds it equal to what [render] actually draws.
 */
object WidgetFace {

    /** R10's budget for one composed update's bitmaps, every bucket together. */
    const val BITMAP_BUDGET_BYTES = 2L * 1024 * 1024

    private const val INK_DARK = 0xFFEDE8E4.toInt()
    private const val INK_LIGHT = 0xFF26211E.toInt()
    private const val RED_DARK = 0xFFFF6B6B.toInt()
    private const val RED_LIGHT = 0xFFC62828.toInt()

    /** R6: the figure dims to 0.5, the bar or ring to 0.45. */
    private const val DIM_FIGURE = 0.5f
    private const val DIM_GAUGE = 0.45f

    // ---- geometry: shared by render and bitmapBytes, so the estimate is exact -----------

    /** A ring's diameter and stroke in dp. */
    data class RingDp(val diameter: Float, val stroke: Float)

    /** A bar's track width and height in dp. */
    data class BarDp(val width: Float, val height: Float)

    fun ring(bucket: Bucket, cells: Int = 1): RingDp? = when (bucket) {
        Bucket.RING_1X1 -> RingDp(64f, 6f)
        Bucket.RING_2X2 -> RingDp(110f, 9f)
        // The spec's Ø56 / Ø88 are ceilings (CCRM-82 (Accounts Strip), rev D): four rings
        // share the inner width with 6 dp gaps, and the ring plus its lines must fit the
        // inner height — Ø53 at 4×1 and Ø80 at 4×2 on the cover.
        Bucket.STRIP_4X1, Bucket.STRIP_4X2 -> {
            val big = bucket == Bucket.STRIP_4X2
            val n = cells.coerceAtLeast(1)
            val byW = kotlin.math.floor((bucket.innerWidthDp - 6f * (n - 1)) / n)
            val byH = kotlin.math.floor(bucket.innerHeightDp - if (big) 33f else 15f)
            RingDp(minOf(if (big) 88f else 56f, byW, byH), if (big) 6f else 5f)
        }
        else -> null
    }

    fun bar(bucket: Bucket): BarDp? = when (bucket) {
        Bucket.NUMBER_2X1, Bucket.NUMBER_4X1 -> BarDp(bucket.innerWidthDp, 8f)
        Bucket.NUMBER_4X2 -> BarDp(bucket.innerWidthDp, 10f)
        Bucket.COUNTDOWN_2X2 -> BarDp(bucket.innerWidthDp, 6f)
        else -> null
    }

    private fun ringPx(dDp: Float, density: Float): Int = kotlin.math.ceil(dDp * density).toInt()

    /**
     * The track width in px that makes the bar's bitmap — track plus BarRenderer's tick
     * padding at each end — exactly the bucket's inner width, so it is shown 1:1.
     */
    private fun barTrackPx(b: BarDp, density: Float): Float =
        b.width * density - 2f * BarRenderer.sidePadding(b.height * density, barTick(density, true, null))

    private fun barBytes(b: BarDp, density: Float): Long {
        val h = b.height * density
        val pad = BarRenderer.sidePadding(h, barTick(density, true, null))
        val w = (barTrackPx(b, density) + 2f * pad).toInt().coerceAtLeast(1)
        return w.toLong() * BarRenderer.bitmapHeight(h) * 4L
    }

    /** The bars' pace tick (rev D): 3 dp at 90% ink, a 1 dp halo in the face colour. */
    private fun barTick(density: Float, dark: Boolean, haloArgb: Int?) =
        BarRenderer.Tick(widthPx = 3f * density, haloPx = 1f * density, inkArgb = ink(dark, 0.9f), haloArgb = haloArgb)

    /** The largest bitmap set one bucket draws, over every state and account count. */
    fun bucketBytes(bucket: Bucket, density: Float): Long {
        // (rings drawn, cells laid out): the Strip with one to four accounts, and with
        // four rings beside a "+N" cell, which shrinks them.
        val configs = if (bucket.face == Face.STRIP) {
            (1..FaceStates.STRIP_MAX).map { it to it } + (FaceStates.STRIP_MAX to FaceStates.STRIP_MAX + 1)
        } else {
            listOf(1 to 1)
        }
        val rings = configs.maxOf { (n, cells) ->
            val r = ring(bucket, cells) ?: return@maxOf 0L
            val px = ringPx(r.diameter, density).toLong()
            n * px * px * 4L
        }
        return rings + (bar(bucket)?.let { barBytes(it, density) } ?: 0L)
    }

    /**
     * R10: the bytes of every bitmap in one composed update — the whole [sizeMap], as one
     * `RemoteViews(Map<SizeF, RemoteViews>)` carries them — at [density].
     */
    fun bitmapBytes(face: Face, sizeMap: List<Bucket>, density: Float): Long {
        require(sizeMap.all { it.face == face }) { "a size map holds one face's buckets" }
        return sizeMap.sumOf { bucketBytes(it, density) }
    }

    // ---- render ---------------------------------------------------------------------

    /** One single-size face. [face] must be [bucket]'s own. */
    fun render(context: Context, face: Face, bucket: Bucket, state: FaceState): RemoteViews {
        require(bucket.face == face && state.face == face) { "$bucket is not a $face bucket" }
        val rv = RemoteViews(context.packageName, layoutFor(face, state))
        val d = context.resources.displayMetrics.density
        frame(rv, bucket, state, d)
        val message = state.message
        if (message != null) {
            rv.setViewVisibility(contentId(face), View.GONE)
            rv.setViewVisibility(R.id.w_msg, View.VISIBLE)
            rv.setTextViewText(R.id.w_msg, messageText(message, bucket, state.dark))
            rv.setTextViewTextSize(R.id.w_msg, TypedValue.COMPLEX_UNIT_SP, messageSp(message, bucket))
            rv.setTextColor(R.id.w_msg, ink(state.dark, 1f))
            rv.setContentDescription(R.id.w_root, messageText(message, bucket, state.dark).toString().replace('\n', ' '))
            return rv
        }
        when (face) {
            Face.RING -> ringFace(context, rv, bucket, state, state.cells.single())
            Face.NUMBER -> numberFace(context, rv, bucket, state, state.cells.single())
            Face.COUNTDOWN -> countdownFace(context, rv, bucket, state, state.cells.single())
            Face.STRIP -> stripFace(context, rv, bucket, state)
        }
        rv.setContentDescription(R.id.w_root, describe(state))
        return rv
    }

    /** The layout for [face]; a Transparent face gets the shadowed text variant. */
    fun layoutFor(face: Face, state: FaceState): Int {
        val shadow = when {
            state.background != FaceBackground.TRANSPARENT -> 0
            state.dark -> 1
            else -> 2
        }
        return when (face) {
            Face.RING -> intArrayOf(R.layout.widget_ring, R.layout.widget_ring_sd, R.layout.widget_ring_sl)
            Face.NUMBER -> intArrayOf(R.layout.widget_number, R.layout.widget_number_sd, R.layout.widget_number_sl)
            Face.COUNTDOWN -> intArrayOf(R.layout.widget_countdown, R.layout.widget_countdown_sd, R.layout.widget_countdown_sl)
            Face.STRIP -> intArrayOf(R.layout.widget_strip, R.layout.widget_strip_sd, R.layout.widget_strip_sl)
        }[shadow]
    }

    private fun contentId(face: Face): Int = when (face) {
        Face.RING -> R.id.ring_col
        Face.NUMBER -> R.id.num_col
        Face.COUNTDOWN -> R.id.cd_col
        Face.STRIP -> R.id.strip_col
    }

    /** Background, padding and the synthetic marker — every face's shell. */
    private fun frame(rv: RemoteViews, bucket: Bucket, state: FaceState, d: Float) {
        when (state.background) {
            FaceBackground.TRANSPARENT -> rv.setViewVisibility(R.id.w_bg, View.GONE)
            else -> rv.setInt(R.id.w_bg, "setColorFilter", state.cardArgb)
        }
        if (state.background == FaceBackground.GRADIENT) {
            rv.setViewVisibility(R.id.w_wash, View.VISIBLE)
            rv.setInt(R.id.w_wash, "setColorFilter", state.washArgb)
            val neutral = state.face == Face.STRIP || state.message != null
            val alpha = when {
                neutral -> 0.06f
                state.dark -> 0.15f
                else -> 0.16f
            }
            rv.setInt(R.id.w_wash, "setImageAlpha", (alpha * 255f).toInt())
        }
        val px = (bucket.padXDp * d).toInt()
        val py = (bucket.padYDp * d).toInt()
        rv.setViewPadding(R.id.w_pad, px, py, px, py)
        if (state.synthetic) {
            rv.setViewVisibility(if (bucket.tall) R.id.w_ribbon else R.id.w_synth_dot, View.VISIBLE)
        }
    }

    fun messageText(m: FaceMessage, bucket: Bucket, dark: Boolean = true): CharSequence {
        val ring = bucket.face == Face.RING
        val small = bucket == Bucket.RING_1X1 || bucket == Bucket.NUMBER_2X1 ||
            bucket == Bucket.COUNTDOWN_2X1
        return when (m) {
            FaceMessage.REMOVED ->
                if (ring) twoLine("Account removed", "tap to choose", dark)
                else "Account removed · tap to choose"
            FaceMessage.SIGN_IN -> if (ring) "Open Cooldown\nto sign in" else "Open Cooldown to sign in"
            FaceMessage.FREE -> if (ring) "No usage\non this plan" else "No usage on this plan"
            // R9: "Update Cooldown" on 1×1 and 2×1; the full sentence on 4×1, 2×2 and up.
            FaceMessage.UNAVAILABLE -> when {
                bucket == Bucket.RING_1X1 -> "Update\nCooldown"
                small -> "Update Cooldown"
                ring -> "Unavailable in this version\nupdate Cooldown"
                else -> "Unavailable in this version · update Cooldown"
            }
        }
    }

    private fun messageSp(m: FaceMessage, bucket: Bucket): Float = when (bucket.face) {
        Face.RING -> when {
            bucket == Bucket.RING_1X1 -> if (m == FaceMessage.UNAVAILABLE) 9.5f else 9f
            m == FaceMessage.UNAVAILABLE -> 11f
            else -> 11.5f
        }
        Face.NUMBER -> 11.5f
        else -> 11f
    }

    /** "Account removed" over a 72%-size, 75%-ink "tap to choose". */
    private fun twoLine(first: String, second: String, dark: Boolean): CharSequence =
        SpannableString("$first\n$second").apply {
            val start = first.length + 1
            setSpan(RelativeSizeSpan(0.72f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(ink(dark, 0.75f)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    // ---- Ring (CCRM-79) -------------------------------------------------------------

    private fun ringFace(context: Context, rv: RemoteViews, bucket: Bucket, s: FaceState, c: Cell) {
        val g = ring(bucket)!!
        val d = context.resources.displayMetrics.density
        rv.setImageViewBitmap(R.id.ring, ringBitmap(context, g, s, c, d))
        rv.setViewLayoutWidth(R.id.ring, g.diameter, TypedValue.COMPLEX_UNIT_DIP)
        rv.setViewLayoutHeight(R.id.ring, g.diameter, TypedValue.COMPLEX_UNIT_DIP)
        if (c.dim) dimImage(rv, R.id.ring)

        // At 100% the × replaces the figure (Q3, Q10).
        if (c.full) rv.setViewVisibility(R.id.ring_fig, View.GONE)
        else figure(rv, R.id.ring_fig, c, s.dark, if (bucket == Bucket.RING_1X1) 16f else 26f)

        if (bucket == Bucket.RING_1X1) {
            if (c.brokenDot) dot(rv, R.id.ring_corner_dot, s.dark)
            return
        }
        rv.setViewVisibility(R.id.ring_label_row, View.VISIBLE)
        mark(rv, R.id.ring_mark, c)
        rv.setTextViewText(R.id.ring_label, ringLabel(c))
        rv.setTextColor(R.id.ring_label, ink(s.dark, if (c.dim) DIM_FIGURE else 1f))
        if (c.brokenDot) dot(rv, R.id.ring_dot, s.dark)
        s.asOf?.let { stamp(rv, R.id.ring_stamp, "· $it", s.dark) }
    }

    /**
     * The Ring's one line: the account alone, tagged only when the window is not 5h, and
     * in Left mode — the bore has no other room to say so. S5 says "not started" in words:
     * an empty extent means two different things and the Ring bears no time.
     */
    fun ringLabel(c: Cell): String = buildString {
        append(c.name)
        if (c.unassigned) append(" (unassigned)")
        if (c.weeklyTagged) append(" · Weekly")
        if (c.left) append(" · left")
        if (StateId.S5 in c.states) append(" · not started")
    }

    // ---- Number (CCRM-80) -----------------------------------------------------------

    private fun numberFace(context: Context, rv: RemoteViews, bucket: Bucket, s: FaceState, c: Cell) {
        val d = context.resources.displayMetrics.density
        val big = bucket == Bucket.NUMBER_4X2
        val figSp = if (big) 44f else 32f
        val labelSp = if (big) 14f else 13f

        mark(rv, R.id.num_mark, c)
        rv.setTextViewText(R.id.num_label, numberLabel(c, bucket))
        rv.setTextViewTextSize(R.id.num_label, TypedValue.COMPLEX_UNIT_SP, labelSp)
        rv.setTextColor(R.id.num_label, ink(s.dark, if (c.dim) DIM_FIGURE else 1f))
        if (c.brokenDot) dot(rv, R.id.num_dot, s.dark)
        if (bucket == Bucket.NUMBER_2X1 && c.unassigned) {
            rv.setViewVisibility(R.id.num_pill, View.VISIBLE)
            rv.setTextColor(R.id.num_pill, s.cardArgb)
        }
        if (bucket == Bucket.NUMBER_2X1 && c.weeklyTagged) {
            rv.setViewVisibility(R.id.num_weekly2, View.VISIBLE)
            rv.setTextColor(R.id.num_weekly2, ink(s.dark, 0.75f))
        }

        figure(rv, R.id.num_fig, c, s.dark, figSp)
        // Below 4×1 the LEFT caption drops and the bare figure flips.
        val leftCap = c.left && c.pct != null && bucket != Bucket.NUMBER_2X1
        if (leftCap) {
            rv.setViewVisibility(R.id.num_leftcap, View.VISIBLE)
            rv.setTextColor(R.id.num_leftcap, ink(s.dark, 0.75f))
        }
        // CCBG-24 (Duet Label Clamp)'s measure: the label gets exactly what the measured
        // figure leaves on the row.
        val figDp = textDp(context, c.figure, figSp, bold = true) +
            (if (leftCap) textDp(context, "LEFT", 10f, bold = true) + 4f else 0f)
        val labelDp = bucket.innerWidthDp - figDp - 10f - 14f - 4f - (if (c.brokenDot) 10f else 0f)
        rv.setInt(R.id.num_label, "setMaxWidth", (labelDp.coerceAtLeast(24f) * d).toInt())
        if (c.dim) rv.setInt(R.id.num_mark, "setImageAlpha", (DIM_FIGURE * 255).toInt())

        val b = bar(bucket)!!
        barInto(context, rv, R.id.num_bar, b, s, c)

        if (bucket == Bucket.NUMBER_2X1) return
        rv.setViewVisibility(R.id.num_subrow, View.VISIBLE)
        rv.setTextViewText(R.id.num_sub, c.sub ?: "")
        rv.setTextColor(R.id.num_sub, ink(s.dark, if (c.dim) 0.5f else 0.78f))
        s.asOf?.let { stamp(rv, R.id.num_stamp, it, s.dark) }

        if (!big) return
        rv.setViewVisibility(R.id.num_ctrl, View.VISIBLE)
        if (c.hasBothWindows) {
            chip(rv, R.id.num_chip_s, R.id.num_chip_s_bg, R.id.num_chip_s_text, "5h", c.window == FaceWindow.SESSION, s, c)
            chip(rv, R.id.num_chip_w, R.id.num_chip_w_bg, R.id.num_chip_w_text, "Weekly", c.window == FaceWindow.WEEKLY, s, c)
        } else {
            // A single-window account shows a fixed tag instead of chips.
            rv.setViewVisibility(R.id.num_chip_fixed, View.VISIBLE)
            rv.setImageViewResource(R.id.num_chip_fixed_bg, R.drawable.widget_tag)
            rv.setInt(R.id.num_chip_fixed_bg, "setColorFilter", ink(s.dark, 0.08f))
            rv.setTextViewText(R.id.num_chip_fixed_text, c.window.word)
            rv.setTextColor(R.id.num_chip_fixed_text, ink(s.dark, 0.8f))
            rv.setViewPadding(R.id.num_chip_fixed_text, (8 * d).toInt(), 0, (8 * d).toInt(), 0)
        }
        // The account cycler (Robin, Q5); wired to WidgetActionReceiver at Step 4.
        rv.setInt(R.id.num_cycler_bg, "setColorFilter", ink(s.dark, 0.08f))
        mark(rv, R.id.num_cycler_mark, c)
        rv.setTextViewText(R.id.num_cycler_text, cyclerSpan(c, s.dark))
        rv.setTextColor(R.id.num_cycler_text, ink(s.dark, 1f))
    }

    /** The on-face views Step 4 wires to `WidgetActionReceiver` (CCRM-78 §On-face controls). */
    val CHIP_SESSION: Int get() = R.id.num_chip_s
    val CHIP_WEEKLY: Int get() = R.id.num_chip_w
    val CYCLER: Int get() = R.id.num_cycler

    /**
     * The Number's label. At 2×1 an unassigned or weekly-only account shows the name
     * alone — the pill or the second line says the rest (rev D); from 4×1 it is inline.
     */
    fun numberLabel(c: Cell, bucket: Bucket): String = when {
        bucket == Bucket.NUMBER_2X1 && (c.unassigned || c.weeklyTagged) -> c.name
        c.unassigned -> "${c.name} (unassigned) · ${c.window.word}"
        else -> "${c.name} · ${c.window.word}"
    }

    fun cyclerText(c: Cell): String = "${c.name} ⇄"

    /** The cycler's "⇄" at 60% ink, the name at full (rev D). */
    private fun cyclerSpan(c: Cell, dark: Boolean): CharSequence =
        SpannableString(cyclerText(c)).apply {
            setSpan(ForegroundColorSpan(ink(dark, 0.6f)), length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun chip(
        rv: RemoteViews, root: Int, bg: Int, text: Int, word: String, selected: Boolean,
        s: FaceState, c: Cell,
    ) {
        rv.setViewVisibility(root, View.VISIBLE)
        rv.setTextViewText(text, word)
        if (selected) {
            rv.setImageViewResource(bg, R.drawable.widget_chip)
            rv.setInt(bg, "setColorFilter", c.accentArgb)
            rv.setTextColor(text, if (s.dark) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt())
        } else {
            rv.setImageViewResource(bg, R.drawable.widget_chip_outline)
            rv.setInt(bg, "setColorFilter", ink(s.dark, 0.28f))
            rv.setTextColor(text, ink(s.dark, 0.85f))
        }
    }

    // ---- Countdown (CCRM-81) --------------------------------------------------------

    private fun countdownFace(context: Context, rv: RemoteViews, bucket: Bucket, s: FaceState, c: Cell) {
        val big = bucket == Bucket.COUNTDOWN_2X2
        val dim = if (c.dim) DIM_FIGURE else 1f

        rv.setTextViewText(R.id.cd_caption, countdownCaption(c))
        rv.setTextColor(R.id.cd_caption, ink(s.dark, if (c.dim) 0.5f else 0.75f))
        mark(rv, R.id.cd_mark, c)
        rv.setTextViewText(R.id.cd_label, countdownLabel(c))
        rv.setTextColor(R.id.cd_label, ink(s.dark, dim))
        if (c.brokenDot) dot(rv, R.id.cd_dot, s.dark)

        if (c.countForm == CountForm.NONE) {
            // S5, or nothing fetched yet: no count and no clock — the caption, the words
            // and the account line. No estimate either.
            rv.setViewVisibility(R.id.cd_countrow, View.GONE)
            rv.setViewVisibility(R.id.cd_msg, View.VISIBLE)
            rv.setTextViewText(
                R.id.cd_msg,
                if (StateId.S5 in c.states) FaceStates.NOT_STARTED else FaceStates.NO_READING,
            )
            rv.setTextColor(R.id.cd_msg, ink(s.dark, 1f))
            if (big) s.asOf?.let { stamp(rv, R.id.cd_stamp, it, s.dark) }
            return
        }

        val clock = Fmt.widgetClock(c.resetsAt!!, s.nowMs, s.zone)
        when (c.countForm) {
            CountForm.LIVE -> {
                // The launcher ticks it (Q6): H:MM:SS, MM:SS under an hour, a negative
                // duration past zero. No alarm and no per-minute redraw drive it.
                rv.setViewVisibility(R.id.cd_chrono, View.VISIBLE)
                val base = SystemClock.elapsedRealtime() + (c.resetsAt.toEpochMilli() - s.nowMs)
                rv.setChronometer(R.id.cd_chrono, base, null, true)
                rv.setChronometerCountDown(R.id.cd_chrono, true)
                rv.setTextViewTextSize(R.id.cd_chrono, TypedValue.COMPLEX_UNIT_SP, if (big) 28f else 24f)
                rv.setTextColor(R.id.cd_chrono, ink(s.dark, dim))
                // The absolute time is always on the face (R2), so the count is readable
                // when its form is ambiguous and true after zero.
                val at = if (big) R.id.cd_at_below else R.id.cd_at_inline
                rv.setViewVisibility(at, View.VISIBLE)
                rv.setTextViewText(at, "at $clock")
                rv.setTextColor(at, ink(s.dark, 0.78f * dim))
            }
            CountForm.ABSOLUTE, CountForm.RESET_PASSED -> {
                rv.setViewVisibility(R.id.cd_abs, View.VISIBLE)
                val passed = c.countForm == CountForm.RESET_PASSED
                rv.setTextViewText(R.id.cd_abs, if (passed) "Reset $clock" else clock)
                val sp = when {
                    passed -> if (big) 22f else 20f
                    else -> 24f
                }
                rv.setTextViewTextSize(R.id.cd_abs, TypedValue.COMPLEX_UNIT_SP, sp)
                rv.setTextColor(R.id.cd_abs, ink(s.dark, dim))
            }
            CountForm.NONE -> Unit
        }

        if (!big) return
        rv.setViewVisibility(R.id.cd_gap1, View.VISIBLE)
        rv.setViewVisibility(R.id.cd_gap2, View.VISIBLE)
        rv.setViewVisibility(R.id.cd_figrow, View.VISIBLE)
        // S6's "—" is at half ink: nothing is read, and the count above says why.
        figure(rv, R.id.cd_fig, c, s.dark, 18f, noReadingAlpha = DIM_FIGURE)
        if (c.left && c.pct != null) {
            rv.setViewVisibility(R.id.cd_leftcap, View.VISIBLE)
            rv.setTextColor(R.id.cd_leftcap, ink(s.dark, 0.8f))
        }
        c.estimate?.let {
            rv.setViewVisibility(R.id.cd_est, View.VISIBLE)
            rv.setTextViewText(R.id.cd_est, it)
            rv.setTextColor(R.id.cd_est, ink(s.dark, if (c.dim) 0.5f else 0.78f))
        }
        rv.setViewVisibility(R.id.cd_bar, View.VISIBLE)
        barInto(context, rv, R.id.cd_bar, bar(bucket)!!, s, c)
        s.asOf?.let { stamp(rv, R.id.cd_stamp, it, s.dark) }
    }

    /** "5h reset" on both sides of zero, never "Resets in"; "Stale" at S8. */
    fun countdownCaption(c: Cell): String =
        if (StateId.S8 in c.states) "Stale" else "${c.window.word} reset"

    fun countdownLabel(c: Cell): String = buildString {
        append(c.name)
        if (c.unassigned) append(" (unassigned)")
        append(" · ").append(c.window.word)
        if (c.left) append(" · left")
    }

    // ---- Strip (CCRM-82) ------------------------------------------------------------

    private val STRIP_CELL = intArrayOf(R.id.strip_cell0, R.id.strip_cell1, R.id.strip_cell2, R.id.strip_cell3)
    private val STRIP_RING = intArrayOf(R.id.strip_ring0, R.id.strip_ring1, R.id.strip_ring2, R.id.strip_ring3)
    private val STRIP_FIG = intArrayOf(R.id.strip_fig0, R.id.strip_fig1, R.id.strip_fig2, R.id.strip_fig3)
    private val STRIP_TAG = intArrayOf(R.id.strip_tag0, R.id.strip_tag1, R.id.strip_tag2, R.id.strip_tag3)
    private val STRIP_XTAG = intArrayOf(R.id.strip_xtag0, R.id.strip_xtag1, R.id.strip_xtag2, R.id.strip_xtag3)
    private val STRIP_MARK = intArrayOf(R.id.strip_mark0, R.id.strip_mark1, R.id.strip_mark2, R.id.strip_mark3)
    private val STRIP_LABEL = intArrayOf(R.id.strip_label0, R.id.strip_label1, R.id.strip_label2, R.id.strip_label3)
    private val STRIP_DOT = intArrayOf(R.id.strip_dot0, R.id.strip_dot1, R.id.strip_dot2, R.id.strip_dot3)
    private val STRIP_RESET = intArrayOf(R.id.strip_reset0, R.id.strip_reset1, R.id.strip_reset2, R.id.strip_reset3)

    private fun stripFace(context: Context, rv: RemoteViews, bucket: Bucket, s: FaceState) {
        val big = bucket == Bucket.STRIP_4X2
        val d = context.resources.displayMetrics.density
        val cellCount = s.cells.size + if (s.overflow > 0) 1 else 0
        val g = ring(bucket, cellCount)!!
        val figSp = if (big) 18f else 13f
        val labelSp = if (big) 11f else 9.5f
        val tagSp = if (big) 9f else 8f

        for (i in 0 until FaceStates.STRIP_MAX) {
            val c = s.cells.getOrNull(i)
            if (c == null) {
                rv.setViewVisibility(STRIP_CELL[i], View.GONE)
                continue
            }
            rv.setImageViewBitmap(STRIP_RING[i], ringBitmap(context, g, s, c, d))
            rv.setViewLayoutWidth(STRIP_RING[i], g.diameter, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutHeight(STRIP_RING[i], g.diameter, TypedValue.COMPLEX_UNIT_DIP)
            if (c.dim) dimImage(rv, STRIP_RING[i])
            mark(rv, STRIP_MARK[i], c)
            rv.setTextViewText(STRIP_LABEL[i], stripLabel(c))
            rv.setTextViewTextSize(STRIP_LABEL[i], TypedValue.COMPLEX_UNIT_SP, labelSp)
            rv.setTextColor(STRIP_LABEL[i], ink(s.dark, if (c.dim) DIM_FIGURE else 1f))
            if (c.brokenDot) dot(rv, STRIP_DOT[i], s.dark)

            if (c.free) {
                // S12 on one ring: the empty extent, the name, and "Free" under it.
                rv.setViewVisibility(STRIP_FIG[i], View.GONE)
                rv.setViewVisibility(STRIP_RESET[i], View.VISIBLE)
                rv.setTextViewText(STRIP_RESET[i], "Free")
                rv.setTextColor(STRIP_RESET[i], ink(s.dark, 0.7f))
                continue
            }
            if (c.full) rv.setViewVisibility(STRIP_FIG[i], View.GONE)
            else figure(rv, STRIP_FIG[i], c, s.dark, figSp)
            if (c.weeklyTagged) {
                // A weekly-only ring carries "Weekly" in the bore; at 100% it stays under
                // the × on 4×2 and drops on 4×1 (rev D).
                val tag = when {
                    !c.full -> STRIP_TAG[i]
                    big -> STRIP_XTAG[i]
                    else -> null
                }
                if (tag != null) {
                    rv.setViewVisibility(tag, View.VISIBLE)
                    rv.setTextViewTextSize(tag, TypedValue.COMPLEX_UNIT_SP, tagSp)
                    rv.setTextColor(tag, ink(s.dark, if (c.dim) 0.45f else 0.78f))
                    if (tag == STRIP_XTAG[i]) {
                        val r = RingRenderer.radius(ringPx(g.diameter, d), g.stroke * d, d)
                        val below = (r * 0.32f + 5f * d) / d
                        rv.setViewLayoutMargin(tag, RemoteViews.MARGIN_TOP, below * 2f, TypedValue.COMPLEX_UNIT_DIP)
                    }
                }
            }
            if (big) c.shortReset?.let {
                rv.setViewVisibility(STRIP_RESET[i], View.VISIBLE)
                rv.setTextViewText(STRIP_RESET[i], it)
                rv.setTextColor(STRIP_RESET[i], ink(s.dark, if (c.dim) 0.5f else 0.72f))
            }
        }
        if (s.overflow > 0) {
            rv.setViewVisibility(R.id.strip_plus, View.VISIBLE)
            rv.setTextViewText(R.id.strip_plus, "+${s.overflow}")
            rv.setTextViewTextSize(R.id.strip_plus, TypedValue.COMPLEX_UNIT_SP, figSp)
            rv.setTextColor(R.id.strip_plus, ink(s.dark, 0.7f))
        }
        if (big) s.asOf?.let { stamp(rv, R.id.strip_stamp, it, s.dark) }
    }

    fun stripLabel(c: Cell): String = if (c.left) "${c.name} · left" else c.name

    // ---- shared pieces --------------------------------------------------------------

    private fun ringBitmap(context: Context, g: RingDp, s: FaceState, c: Cell, d: Float) =
        RingRenderer.draw(
            context, ringPx(g.diameter, d), g.stroke * d, c.pct, c.elapsed, c.fillArgb, s.dark,
            s.showOverPace, spentCross = c.full, mark = RingRenderer.PaceMark.TICK,
            haloArgb = if (s.background == FaceBackground.TRANSPARENT) null else s.cardArgb,
            shadowArgb = if (s.background == FaceBackground.TRANSPARENT) shadow(s.dark) else null,
        )

    private fun barInto(context: Context, rv: RemoteViews, id: Int, b: BarDp, s: FaceState, c: Cell) {
        val d = context.resources.displayMetrics.density
        val h = b.height * d
        val bmp = BarRenderer.draw(
            barTrackPx(b, d), h, c.pct, c.elapsed, androidx.compose.ui.graphics.Color(c.accentArgb),
            s.dark, s.showOverPace,
            tick = barTick(d, s.dark, if (s.background == FaceBackground.TRANSPARENT) null else s.cardArgb),
        )
        rv.setImageViewBitmap(id, bmp)
        // Sized to the bitmap, never stretched to the cell (R10).
        rv.setViewLayoutWidth(id, bmp.width.toFloat(), TypedValue.COMPLEX_UNIT_PX)
        rv.setViewLayoutHeight(id, bmp.height.toFloat(), TypedValue.COMPLEX_UNIT_PX)
        if (c.dim) dimImage(rv, id)
    }

    /**
     * The figure in the severity colour. Dimming is folded into the colour's alpha, and
     * the gauges' into `setImageAlpha`: `View.setAlpha` is not a remotable method on every
     * API level this app supports, and one unremotable call fails the whole face.
     */
    private fun figure(
        rv: RemoteViews, id: Int, c: Cell, dark: Boolean, sp: Float, noReadingAlpha: Float = 1f,
    ) {
        rv.setViewVisibility(id, View.VISIBLE)
        rv.setTextViewText(id, c.figure)
        rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp)
        val dim = if (c.dim) DIM_FIGURE else 1f
        rv.setTextColor(id, if (c.pct == null) ink(dark, noReadingAlpha * dim) else fade(c.fillArgb, dim))
    }

    /** R6's 0.45 on a ring or bar bitmap. */
    private fun dimImage(rv: RemoteViews, id: Int) =
        rv.setInt(id, "setImageAlpha", (DIM_GAUGE * 255).toInt())

    /** [argb] with its alpha scaled by [a]. */
    private fun fade(argb: Int, a: Float): Int {
        val alpha = ((argb ushr 24) * a).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (argb and 0x00FFFFFF)
    }

    private fun mark(rv: RemoteViews, id: Int, c: Cell) {
        rv.setImageViewResource(id, providerMarkRes(c.provider))
        rv.setInt(id, "setColorFilter", c.accentArgb)
    }

    private fun dot(rv: RemoteViews, id: Int, dark: Boolean) {
        rv.setViewVisibility(id, View.VISIBLE)
        rv.setInt(id, "setColorFilter", if (dark) RED_DARK else RED_LIGHT)
    }

    /** Q11 (kept, rev D): 10 sp at 55% ink, in flow, never overlaid. */
    private fun stamp(rv: RemoteViews, id: Int, text: String, dark: Boolean) {
        rv.setViewVisibility(id, View.VISIBLE)
        rv.setTextViewText(id, text)
        rv.setTextColor(id, ink(dark, 0.55f))
    }

    private fun textDp(context: Context, text: String, sp: Float, bold: Boolean): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        return paint.measureText(text) / context.resources.displayMetrics.density
    }

    /** The face's text colour at [alpha]. */
    fun ink(dark: Boolean, alpha: Float): Int {
        val base = if (dark) INK_DARK else INK_LIGHT
        return ((alpha * 255f).toInt().coerceIn(0, 255) shl 24) or (base and 0x00FFFFFF)
    }

    /** A Transparent face's soft shadow, in the opposite tone to its ink. */
    private fun shadow(dark: Boolean): Int = if (dark) 0xA6000000.toInt() else 0xCCFFFFFF.toInt()

    /** What TalkBack reads for the whole face. */
    fun describe(s: FaceState): String = s.cells.joinToString("; ") { c ->
        buildString {
            append(c.name).append(", ").append(c.window.word).append(", ")
            append(if (c.pct == null) "no reading" else if (c.left) "${c.figure} left" else "${c.figure} used")
            c.sub?.let { append(", ").append(it) }
        }
    }
}
