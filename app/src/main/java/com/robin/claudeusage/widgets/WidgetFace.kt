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

    /** This bucket drawn at its own design size — rev D's frame. */
    val frame: Frame get() = Frame(this, widthDp, heightDp)
    val tall: Boolean get() = frame.tall
    val innerWidthDp: Float get() = frame.innerWidthDp

    companion object {
        fun of(face: Face): List<Bucket> = entries.filter { it.face == face }
    }
}

/**
 * The frame one face is drawn into (wireframe rev F, CCBG-38 (Cover Buckets)): the
 * [bucket] picks the layout, the frame — the size the launcher reports — sets the geometry.
 * One UI's cells differ from the Pixel's and from grid to grid, so nothing here assumes a
 * cell; at a bucket's own design size ([Bucket.frame]) every number is rev D's.
 */
data class Frame(
    val bucket: Bucket,
    val widthDp: Float,
    val heightDp: Float,
    /** R8's ribbon over a tall face: it comes off the inner height only (rev H). */
    val ribbonDp: Float = 0f,
) {
    /** 160 dp or more: the synthetic marker is the full-width ribbon, not the dot (R8). */
    val tall: Boolean get() = heightDp >= 160f

    /**
     * 14 dp once both sides reach 200 dp; 8 dp under 90 dp — top and bottom on a short row,
     * a side on a narrow column (so One UI's 84 dp 1×1 keeps Ø64); else 12.
     */
    val padXDp: Float get() = when {
        minOf(widthDp, heightDp) >= 200f -> 14f
        widthDp < 90f -> 8f
        else -> 12f
    }
    val padYDp: Float get() = when {
        minOf(widthDp, heightDp) >= 200f -> 14f
        // Rev H: any row under 100 dp (a 90 dp launcher row too) keeps the short row's 8 dp.
        heightDp < 100f -> 8f
        else -> 12f
    }
    val innerWidthDp: Float get() = widthDp - 2 * padXDp
    val innerHeightDp: Float get() = heightDp - 2 * padYDp - ribbonDp

    /**
     * Rev H (CCBG-44 (Widget Fill)): the tier inside the bucket — short (under 140 dp tall,
     * narrow or wide at 240 dp), tall (T, aspect under 1.35) or wide (W). The bucket still
     * picks the layout file (R9's keys and R10's three buckets are untouched); the tier
     * picks how the face fills the frame.
     */
    val tier: Tier get() = when {
        heightDp < 140f -> if (widthDp < 240f) Tier.S_NARROW else Tier.S_WIDE
        widthDp / heightDp < 1.35f -> Tier.T
        else -> Tier.W
    }

    /** Rev H's roomy type (label 15, sub 13, caption 12) once the inner height reaches 200 dp. */
    val roomy: Boolean get() = innerHeightDp >= 200f

    companion object {
        /** The layout a [face] takes in a [widthDp] × [heightDp] frame: rev F's class thresholds. */
        fun bucketFor(face: Face, widthDp: Float, heightDp: Float): Bucket = when (face) {
            Face.RING -> if (widthDp >= 140f && heightDp >= 140f) Bucket.RING_2X2 else Bucket.RING_1X1
            Face.NUMBER -> when {
                widthDp < 240f -> Bucket.NUMBER_2X1
                heightDp >= 150f -> Bucket.NUMBER_4X2
                else -> Bucket.NUMBER_4X1
            }
            Face.COUNTDOWN ->
                if (widthDp >= 140f && heightDp >= 150f) Bucket.COUNTDOWN_2X2 else Bucket.COUNTDOWN_2X1
            Face.STRIP -> if (heightDp >= 150f) Bucket.STRIP_4X2 else Bucket.STRIP_4X1
        }

        /** [face] drawn at a reported [widthDp] × [heightDp]. */
        fun at(face: Face, widthDp: Float, heightDp: Float) =
            Frame(bucketFor(face, widthDp, heightDp), widthDp, heightDp)
    }
}

/** Rev H's tiers (CCBG-44 (Widget Fill)): see [Frame.tier]. */
enum class Tier { S_NARROW, S_WIDE, T, W }

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
    /** R8: [data] is [com.robin.claudeusage.data.SyntheticSeries]' numbers (the snapshot's own flag). */
    val synthetic: Boolean = false,
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
    /**
     * Rev H (Q1): a Ring's companion — the same account's other window, when it has both.
     * Drawn only where the frame has room for it ([WidgetFace.ringLayout]).
     */
    val companion: Cell? = null,
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
        val other = if (c.window == FaceWindow.SESSION) FaceWindow.WEEKLY else FaceWindow.SESSION
        val companion = if (input.face == Face.RING && c.hasBothWindows) {
            cell(input, account, other, unassigned = key == null)
        } else null
        return FaceState(
            face = input.face, background = input.background, dark = input.dark,
            showOverPace = input.showOverPace, synthetic = input.synthetic, message = null,
            cells = listOf(c), overflow = 0, asOf = asOf(account.fetchedAt, input),
            states = faceWide + c.states, cardArgb = card, washArgb = c.accentArgb,
            nowMs = input.nowMs, zone = input.zone, companion = companion,
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

    /** Rev H (Q4): an unassigned face's stamp slot — the tap opens its config (CCBG-43 (Widget Settings Hidden)). */
    const val TAP_TO_CHOOSE = "tap to choose account"

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

    /**
     * R10's budget for one composed update's bitmaps, every frame together: 4 MB since rev H
     * (CCBG-44 (Widget Fill)), for the Strip's larger rings on a Fold's two frames. The host
     * caps an update at 1.5 × the screen's pixels × 4 B (≈16 MB on the Fold 7's cover); bitmaps
     * cross Binder as ashmem blobs; [WidgetHost.withinBudget] drops the largest frames past it
     * and SafeUpdate falls back to one size if the host refuses anyway.
     */
    const val BITMAP_BUDGET_BYTES = 4L * 1024 * 1024

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

    fun ring(bucket: Bucket, cells: Int = 1): RingDp? = ring(bucket.frame, cells)

    /** The main ring of a Ring face, or one Strip ring for [cells] cells, at [frame]. */
    fun ring(frame: Frame, cells: Int = 1): RingDp? = when (frame.bucket.face) {
        Face.RING -> ringLayout(frame, companion = false).main
        Face.STRIP -> stripRing(frame, cells)
        else -> null
    }

    /**
     * How a Ring face fills [frame] (rev H, CCBG-44 (Widget Fill)): the ring takes what its
     * lines leave, up to a cap; [lines] is how many of name · reset · stamp are shown;
     * [horizontal] puts them beside the ring (the wide tiers); [companionRing] is the other
     * window's ring (Q1), drawn only when [companion] — the account has both — and the frame
     * has the room.
     */
    data class RingLayout(
        val main: RingDp,
        val horizontal: Boolean,
        val lines: Int,
        val companionRing: RingDp?,
        /** The lines' start margin beside the ring, when [horizontal]. */
        val gapDp: Float = 0f,
    )

    private const val RING_CAP = 160f

    /** R8's synthetic ribbon on a tall face (the layouts' 12 dp). */
    private const val RIBBON_DP = 12f

    /**
     * The frame a face is laid out in: R8's ribbon takes 12 dp off a tall face's inner
     * height, its padding and tier stay the frame's. [WidgetHost] wires controls from the
     * same frame, so a control is never wired on a view the draw hid. [frameBytes] takes the
     * larger of both, so it bounds a ribboned draw.
     */
    fun drawFrame(frame: Frame, state: FaceState): Frame =
        if (state.synthetic && frame.tall) frame.copy(ribbonDp = RIBBON_DP) else frame
    private const val COMPANION = 100f

    private fun mainRing(dia: Float, small: Boolean) = if (small) {
        RingDp(dia, maxOf(5f, kotlin.math.round(6f * dia / 64f)))
    } else {
        RingDp(dia, kotlin.math.round(9f * dia / 110f).coerceIn(7f, 13f))
    }

    private val COMPANION_RING = RingDp(COMPANION, 6f)

    /**
     * The height the Ring's lines take under it: each line at 1.34× its size (the text's own
     * font padding), the 6 dp above them and 2 dp between. Fable's 56 dp at rev D's type.
     */
    private fun ringReserve(lines: Int, roomy: Boolean): Float {
        val name = if (roomy) 15f else 13f
        val reset = if (roomy) 13f else 12f
        return 1f + kotlin.math.ceil(when (lines) {
            3 -> 6f + 1.34f * (name + reset + 10f) + 4f
            2 -> 6f + 1.34f * (name + reset) + 2f
            1 -> 6f + 1.34f * name
            else -> 0f
        })
    }

    fun ringLayout(frame: Frame, companion: Boolean): RingLayout {
        val w = frame.innerWidthDp
        val h = frame.innerHeightDp
        if (frame.bucket == Bucket.RING_1X1) {
            // 1×1 and 2×1 are the ring alone; a short frame wide enough for a 90 dp column
            // beside it (3×1 and up) adds the lines there.
            val dia = kotlin.math.floor(minOf(92f, w, h))
            val side = w - dia - 12f >= 90f
            return RingLayout(mainRing(dia, small = true), side, if (side) 3 else 0, null, 12f)
        }
        if (frame.tier == Tier.W) {
            var dia = kotlin.math.floor(minOf(RING_CAP, h))
            if (w - dia - 16f < 90f) dia = kotlin.math.floor(w - 106f)
            if (dia >= 64f) {
                val comp = companion && w - dia - 162f >= COMPANION
                return RingLayout(mainRing(dia, small = false), true, 3, if (comp) COMPANION_RING else null, 16f)
            }
        }
        // T: the lines centred under the ring, dropped from the stamp up until the ring is
        // at least Ø100 (or only the name is left).
        var lines = 3
        var dia: Float
        while (true) {
            dia = kotlin.math.floor(minOf(RING_CAP, w, h - ringReserve(lines, frame.roomy)))
            if (dia >= 100f || lines == 1) break
            lines--
        }
        val spare = h - dia - ringReserve(lines, frame.roomy)
        val comp = companion && lines == 3 && spare >= COMPANION + 8f && w >= COMPANION
        return RingLayout(mainRing(dia, small = false), false, lines, if (comp) COMPANION_RING else null)
    }

    /** The Ring's bore figure, scaled with the ring from rev D's 16 / 26 sp. */
    fun ringFigureSp(frame: Frame): Float {
        val g = ring(frame)!!
        return if (frame.bucket == Bucket.RING_1X1) kotlin.math.round(16f * g.diameter / 64f)
        else kotlin.math.round(26f * g.diameter / 110f)
    }

    /**
     * One Strip ring. 4×1 keeps rev G (Ø53 at rev D's 84 dp, Ø64 over a 12 sp name at 100 dp
     * and up — CCBG-41 (Cover Strip Type)). 4×2 fills (rev H): four rings share the inner
     * width with 8 dp gaps, the ring plus name, reset and stamp (62 dp) the inner height, up
     * to Ø120.
     */
    private fun stripRing(frame: Frame, cells: Int): RingDp {
        val big = frame.bucket == Bucket.STRIP_4X2
        val n = cells.coerceAtLeast(1)
        if (big) {
            val byW = kotlin.math.floor((frame.innerWidthDp - 8f * (n - 1)) / n)
            val byH = kotlin.math.floor(frame.innerHeightDp - 62f)
            val dia = minOf(120f, byW, byH)
            return RingDp(dia, kotlin.math.round(6f * dia / 88f).coerceIn(5f, 9f))
        }
        val roomy = stripRoomy(frame)
        val byW = kotlin.math.floor((frame.innerWidthDp - 6f * (n - 1)) / n)
        val byH = kotlin.math.floor(frame.innerHeightDp - if (roomy) 19f else 15f)
        return RingDp(minOf(if (roomy) 64f else 56f, byW, byH), 5f)
    }

    /**
     * Rev H: a Strip cell's width — its share of the row, capped at the ring plus 40 dp
     * (4×2) or 56 dp (4×1), CCBG-10's pitch rule, so a wide row is a centred group rather
     * than rings spread to the third-points. A "+N" cell keeps 30 dp of its own.
     */
    fun stripPitch(frame: Frame, rings: Int, overflow: Boolean): Float {
        val g = stripRing(frame, rings + if (overflow) 1 else 0)
        val share = stripShare(frame, rings, overflow)
        val cap = g.diameter + when {
            frame.bucket == Bucket.STRIP_4X2 -> 40f
            // The seat's call on a rev H gap: Fable's "reset joins the name at Ø+70" could
            // never fire under an Ø+56 cap, so a 4×1 whose share has that room widens its
            // cap to fit "Personal · 9:20 PM".
            stripInlineReset(frame, rings, overflow) -> 96f
            else -> 56f
        }
        return minOf(share, cap)
    }

    private fun stripShare(frame: Frame, rings: Int, overflow: Boolean): Float =
        kotlin.math.floor((frame.innerWidthDp - if (overflow) 30f else 0f) / rings.coerceAtLeast(1))

    /** Rev H: a 4×1 cell with room for it puts the reset beside the name. */
    fun stripInlineReset(frame: Frame, rings: Int, overflow: Boolean): Boolean =
        frame.bucket == Bucket.STRIP_4X1 &&
            stripShare(frame, rings, overflow) >= stripRing(frame, rings + if (overflow) 1 else 0).diameter + 70f

    // ---- Number (rev H) ---------------------------------------------------------------

    /**
     * Rev H: the Number 2×2 — the 2×1 layout with 205 dp inside (One UI's two rows, 237 dp):
     * label, figure, bar, reset, stamp, chips and cycler need it. A shorter 2×2 keeps the
     * one-row or stacked 2×1.
     */
    fun numberTall(frame: Frame): Boolean = frame.bucket == Bucket.NUMBER_2X1 && frame.innerHeightDp >= 205f

    /** The 5h|Weekly chips and the cycler: the 4×2 and, since rev H, the 2×2. */
    fun numberControls(frame: Frame): Boolean = frame.bucket == Bucket.NUMBER_4X2 || numberTall(frame)

    /** The cycler's view: on its own row on the 2×2, in the chip row on the 4×2. */
    fun cycler(frame: Frame): Int = if (numberTall(frame)) R.id.num_cycler2 else R.id.num_cycler

    /**
     * The Number's figure (rev H): the hero takes the height the rows leave, within the
     * width a "100%" needs (2.4 em). Rev D's frames keep 32 / 44 sp.
     */
    fun numberFigSp(frame: Frame, pill: Boolean = false): Float {
        val w = frame.innerWidthDp
        val h = frame.innerHeightDp
        val sp = when (frame.bucket) {
            Bucket.NUMBER_2X1 -> when {
                // Its rows take 134 dp; the figure's line is 1.17× its size.
                numberTall(frame) -> minOf(w / 2.4f, 72f, (h - 134f) / 1.17f).coerceAtLeast(32f)
                // With the pill a stacked 2×1 has 13 dp less: 30 sp (the Fold 7 device check
                // showed One UI's font clipping the pill at Fable's 36).
                numberStacked(frame) -> if (pill) 30f else minOf(w / 2.4f, h - 29f).coerceIn(32f, 44f)
                else -> 32f
            }
            // The figure's line is about 1.1–1.17× its size; the bar and sub-line take 36 dp.
            Bucket.NUMBER_4X1 -> ((h - 36f) / 1.1f).coerceIn(32f, 44f)
            Bucket.NUMBER_4X2 -> {
                // The controls, bar and sub-line take 73 dp, a stacked label 20 more.
                val stacked = numberStacked(frame)
                val byW = if (stacked) w / 2.4f else (w - 94f) / 2.4f
                val byH = (h - 73f - if (stacked) 20f else 0f) / 1.17f
                minOf(byW, byH).coerceIn(if (stacked) 32f else 44f, if (h >= 300f) 96f else 72f)
            }
            else -> 32f
        }
        return kotlin.math.floor(sp)
    }

    // ---- Countdown (rev H) ------------------------------------------------------------

    /**
     * The count's size (rev H): within the width "−0:00:00" needs (4.5 em) and the height the
     * other rows leave. Rev D's frames keep 24 / 28 sp.
     */
    fun countSp(frame: Frame): Float {
        val w = frame.innerWidthDp
        val h = frame.innerHeightDp
        // A 1-row face's caption, account line and air take 37 dp; "at 9:10 PM" on its own
        // line 16 more. The count's line is 1.17× its size (no font padding).
        val sp = when {
            frame.bucket == Bucket.COUNTDOWN_2X2 -> minOf(w / 4.5f, h - 118f).coerceIn(28f, 80f)
            frame.tier == Tier.S_WIDE -> minOf(44f, (w - 66f) / 4.5f, (h - 37f) / 1.17f).coerceAtLeast(20f)
            else -> {
                val inline = minOf(w / 4.5f, (h - 37f) / 1.17f)
                val one = if (frame.heightDp < 100f || countFitsInline(w, inline)) inline
                else minOf(w / 4.5f, (h - 53f) / 1.17f)
                one.coerceIn(20f, 44f)
            }
        }
        return kotlin.math.floor(sp)
    }

    /** "0:00:00" is about 3.9 em of bold digits; "at 9:10 PM" about 62 dp, 6 dp after it. */
    private fun countFitsInline(innerW: Float, sp: Float): Boolean = 3.9f * sp + 6f + 62f <= innerW

    /** Rev H: a wide 1-row Countdown's side column (figure over a bar), or null. */
    fun countdownSideDp(frame: Frame): Float? {
        // The column (figure over bar, ~39 dp) needs One UI's 108 dp row; an 84 dp row can't.
        if (frame.bucket != Bucket.COUNTDOWN_2X1 || frame.tier != Tier.S_WIDE || frame.innerHeightDp < 80f) return null
        val room = frame.innerWidthDp - 4.5f * countSp(frame) - 82f
        return if (room >= 95f) kotlin.math.floor(minOf(160f, room)) else null
    }

    fun bar(bucket: Bucket): BarDp? = bar(bucket.frame)

    fun bar(frame: Frame): BarDp? = when (frame.bucket) {
        Bucket.NUMBER_2X1 -> BarDp(frame.innerWidthDp, if (numberTall(frame)) 12f else if (numberFigSp(frame) >= 40f) 10f else 8f)
        Bucket.NUMBER_4X1 -> BarDp(frame.innerWidthDp, if (numberFigSp(frame) >= 40f) 10f else 8f)
        Bucket.NUMBER_4X2 -> BarDp(frame.innerWidthDp, if (numberFigSp(frame) >= 64f) 14f else 12f)
        Bucket.COUNTDOWN_2X2 -> BarDp(frame.innerWidthDp, if (countSp(frame) >= 60f) 10f else 8f)
        Bucket.COUNTDOWN_2X1 -> countdownSideDp(frame)?.let { BarDp(it, 8f) }
        else -> null
    }

    /**
     * Rev F: a 2×1 Number on a frame narrower than rev D's (inner under 150 dp) cannot
     * hold the label beside the 32 sp figure; where the frame has the height (100 dp or
     * more — One UI's 108 dp row) the label takes its own line above the figure. Rev H: the
     * 2×2 always stacks, and so does a 4×2 under 260 dp inner with 180 dp of height (the 3×2).
     */
    fun numberStacked(frame: Frame): Boolean = when (frame.bucket) {
        Bucket.NUMBER_2X1 -> (frame.innerWidthDp < 150f && frame.heightDp >= 100f) || numberTall(frame)
        Bucket.NUMBER_4X2 -> frame.innerWidthDp < 260f && frame.innerHeightDp >= 180f
        else -> false
    }

    /**
     * Rev F.1: the same narrow 2×1 without the height (a five-column launcher) takes rev
     * D's compact label — the name alone — rather than an ellipsis.
     */
    fun numberCompact(frame: Frame): Boolean =
        frame.bucket == Bucket.NUMBER_2X1 && frame.innerWidthDp < 150f && frame.heightDp < 100f

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
    fun bucketBytes(bucket: Bucket, density: Float): Long = frameBytes(bucket.frame, density)

    /**
     * The largest bitmap set one frame draws, over every state and account count — with and
     * without R8's ribbon, since a ribboned tall Ring can drop a line and grow (rev H).
     */
    fun frameBytes(frame: Frame, density: Float): Long =
        if (frame.tall) maxOf(frameBytesAt(frame, density), frameBytesAt(frame.copy(ribbonDp = RIBBON_DP), density))
        else frameBytesAt(frame, density)

    private fun frameBytesAt(frame: Frame, density: Float): Long {
        val bucket = frame.bucket
        // (rings drawn, cells laid out): the Strip with one to four accounts, and with
        // four rings beside a "+N" cell, which shrinks them.
        val configs = if (bucket.face == Face.STRIP) {
            (1..FaceStates.STRIP_MAX).map { it to it } + (FaceStates.STRIP_MAX to FaceStates.STRIP_MAX + 1)
        } else {
            listOf(1 to 1)
        }
        val rings = configs.maxOf { (n, cells) ->
            val r = ring(frame, cells) ?: return@maxOf 0L
            val px = ringPx(r.diameter, density).toLong()
            n * px * px * 4L
        }
        // Rev H: the worst case of a Ring carries its companion wherever the frame has room.
        val companion = if (bucket.face == Face.RING) {
            ringLayout(frame, companion = true).companionRing
                ?.let { ringPx(it.diameter, density).toLong().let { px -> px * px * 4L } } ?: 0L
        } else 0L
        return rings + companion + (bar(frame)?.let { barBytes(it, density) } ?: 0L)
    }

    /**
     * R10: the bytes of every bitmap in one composed update — the whole [sizeMap], as one
     * `RemoteViews(Map<SizeF, RemoteViews>)` carries them — at [density].
     */
    fun bitmapBytes(face: Face, sizeMap: List<Bucket>, density: Float): Long {
        require(sizeMap.all { it.face == face }) { "a size map holds one face's buckets" }
        return sizeMap.sumOf { bucketBytes(it, density) }
    }

    /** [bitmapBytes] for a size map keyed by reported frames (rev F). */
    fun frameBytes(frames: List<Frame>, density: Float): Long = frames.sumOf { frameBytes(it, density) }

    // ---- render ---------------------------------------------------------------------

    /** One single-size face at [bucket]'s own design size. [face] must be [bucket]'s own. */
    fun render(context: Context, face: Face, bucket: Bucket, state: FaceState): RemoteViews =
        render(context, face, bucket.frame, state)

    /** One single-size face drawn at [frame] (rev F). [face] must be the frame bucket's own. */
    fun render(context: Context, face: Face, frame: Frame, state: FaceState): RemoteViews {
        val bucket = frame.bucket
        require(bucket.face == face && state.face == face) { "$bucket is not a $face bucket" }
        val rv = RemoteViews(context.packageName, layoutFor(face, state))
        val d = context.resources.displayMetrics.density
        shell(rv, frame, state, d)
        val geo = drawFrame(frame, state)
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
            Face.RING -> ringFace(context, rv, geo, state, state.cells.single())
            Face.NUMBER -> numberFace(context, rv, geo, state, state.cells.single())
            Face.COUNTDOWN -> countdownFace(context, rv, geo, state, state.cells.single())
            Face.STRIP -> stripFace(context, rv, geo, state)
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
        Face.RING -> R.id.ring_content
        Face.NUMBER -> R.id.num_col
        Face.COUNTDOWN -> R.id.cd_col
        Face.STRIP -> R.id.strip_col
    }

    /** Background, padding and the synthetic marker — every face's shell. */
    private fun shell(rv: RemoteViews, frame: Frame, state: FaceState, d: Float) {
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
        val px = (frame.padXDp * d).toInt()
        val py = (frame.padYDp * d).toInt()
        rv.setViewPadding(R.id.w_pad, px, py, px, py)
        if (state.synthetic) {
            rv.setViewVisibility(if (frame.tall) R.id.w_ribbon else R.id.w_synth_dot, View.VISIBLE)
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

    /** One of the Ring's two arrangements (rev H): the column, or the row beside the ring. */
    private class RingIds(
        val group: Int, val ring: Int, val fig: Int, val lines: Int, val labelRow: Int,
        val mark: Int, val label: Int, val dot: Int, val reset: Int, val stamp: Int,
        val compBox: Int, val comp: Int, val compFig: Int, val compTag: Int,
    )

    private val RING_V = RingIds(
        R.id.ring_col, R.id.ring, R.id.ring_fig, R.id.ring_lines, R.id.ring_label_row,
        R.id.ring_mark, R.id.ring_label, R.id.ring_dot, R.id.ring_reset, R.id.ring_stamp,
        R.id.ring_comp_box, R.id.ring_comp, R.id.ring_comp_fig, R.id.ring_comp_tag,
    )
    private val RING_H = RingIds(
        R.id.ring_row, R.id.ring_h, R.id.ring_fig_h, R.id.ring_lines_h, R.id.ring_label_row_h,
        R.id.ring_mark_h, R.id.ring_label_h, R.id.ring_dot_h, R.id.ring_reset_h, R.id.ring_stamp_h,
        R.id.ring_comp_box_h, R.id.ring_comp_h, R.id.ring_comp_fig_h, R.id.ring_comp_tag_h,
    )

    private fun ringFace(context: Context, rv: RemoteViews, frame: Frame, s: FaceState, c: Cell) {
        val companion = s.companion
        val l = ringLayout(frame, companion = companion != null)
        val g = l.main
        val ids = if (l.horizontal) RING_H else RING_V
        if (l.horizontal) {
            rv.setViewVisibility(R.id.ring_col, View.GONE)
            rv.setViewVisibility(R.id.ring_row, View.VISIBLE)
        }
        val d = context.resources.displayMetrics.density
        rv.setImageViewBitmap(ids.ring, ringBitmap(context, g, s, c, d))
        rv.setViewLayoutWidth(ids.ring, g.diameter, TypedValue.COMPLEX_UNIT_DIP)
        rv.setViewLayoutHeight(ids.ring, g.diameter, TypedValue.COMPLEX_UNIT_DIP)
        if (c.dim) dimImage(rv, ids.ring)

        // At 100% the × replaces the figure (Q3, Q10).
        if (c.full) rv.setViewVisibility(ids.fig, View.GONE)
        else figure(rv, ids.fig, c, s.dark, ringFigureSp(frame))

        if (l.lines == 0) {
            if (c.brokenDot) dot(rv, R.id.ring_corner_dot, s.dark)
            return
        }
        val nameSp = if (frame.roomy) 15f else 13f
        val resetSp = if (frame.roomy) 13f else 12f
        rv.setViewVisibility(ids.lines, View.VISIBLE)
        if (l.horizontal) rv.setViewLayoutMargin(ids.lines, RemoteViews.MARGIN_START, l.gapDp, TypedValue.COMPLEX_UNIT_DIP)
        // The width the lines may take: the whole inner width under the ring, or what the
        // ring (and a companion) leave beside it.
        val lineDp = if (!l.horizontal) frame.innerWidthDp
        else frame.innerWidthDp - g.diameter - l.gapDp - (l.companionRing?.let { it.diameter + 8f } ?: 0f)

        rv.setViewVisibility(ids.labelRow, View.VISIBLE)
        mark(rv, ids.mark, c)
        val label = ringLabel(c, withReset = l.lines >= 2 && c.sub == FaceStates.NOT_STARTED)
        rv.setTextViewText(ids.label, label)
        rv.setTextViewTextSize(ids.label, TypedValue.COMPLEX_UNIT_SP, nameSp)
        rv.setTextColor(ids.label, ink(s.dark, if (c.dim) DIM_FIGURE else 1f))
        rv.setInt(ids.label, "setMaxWidth", ((lineDp - 16f - if (c.brokenDot) 10f else 0f).coerceAtLeast(24f) * d).toInt())
        if (c.brokenDot) dot(rv, ids.dot, s.dark)

        if (l.lines >= 2) c.sub?.let {
            rv.setViewVisibility(ids.reset, View.VISIBLE)
            rv.setTextViewText(ids.reset, it)
            rv.setTextViewTextSize(ids.reset, TypedValue.COMPLEX_UNIT_SP, resetSp)
            rv.setTextColor(ids.reset, ink(s.dark, if (c.dim) 0.5f else 0.78f))
            rv.setInt(ids.reset, "setMaxWidth", (lineDp * d).toInt())
        }
        if (l.lines >= 3) {
            // Rev H (Q4): an unassigned face says how to choose, in the stamp's slot.
            val text = if (c.unassigned) FaceStates.TAP_TO_CHOOSE else s.asOf
            text?.let {
                stamp(rv, ids.stamp, it, s.dark)
                rv.setInt(ids.stamp, "setMaxWidth", (lineDp * d).toInt())
            }
        }

        val cg = l.companionRing ?: return
        val k = companion ?: return
        rv.setViewVisibility(ids.compBox, View.VISIBLE)
        rv.setImageViewBitmap(ids.comp, ringBitmap(context, cg, s, k, d))
        rv.setViewLayoutWidth(ids.comp, cg.diameter, TypedValue.COMPLEX_UNIT_DIP)
        rv.setViewLayoutHeight(ids.comp, cg.diameter, TypedValue.COMPLEX_UNIT_DIP)
        if (k.dim) dimImage(rv, ids.comp)
        if (k.full) rv.setViewVisibility(ids.compFig, View.GONE)
        else figure(rv, ids.compFig, k, s.dark, kotlin.math.round(22f * cg.diameter / COMPANION))
        rv.setTextViewText(ids.compTag, k.window.word)
        rv.setTextColor(ids.compTag, ink(s.dark, if (k.dim) 0.45f else 0.78f))
    }

    /**
     * The Ring's name line: the account, tagged only when the window is not 5h, and in
     * Left mode. S5 says "not started" in words when no reset line under it can (a 2×2
     * that dropped its lines); rev H's reset line says "Starts when a message is sent".
     * Rev H (Q4): no "(unassigned)" — the stamp slot says "tap to choose account".
     */
    fun ringLabel(c: Cell, withReset: Boolean = false): String = buildString {
        append(c.name)
        if (c.weeklyTagged) append(" · Weekly")
        if (c.left) append(" · left")
        if (StateId.S5 in c.states && !withReset) append(" · not started")
    }

    // ---- Number (CCRM-80) -----------------------------------------------------------

    private fun numberFace(context: Context, rv: RemoteViews, frame: Frame, s: FaceState, c: Cell) {
        val bucket = frame.bucket
        val d = context.resources.displayMetrics.density
        val big = bucket == Bucket.NUMBER_4X2
        val tall = numberTall(frame)
        val stacked = numberStacked(frame)
        // Rev D's pill marks an unassigned 2×1; rev H's 2×2 says it in the stamp slot (Q4).
        // …and the 110 dp minimum 2×1 has no height for it: the tap still opens the config.
        val pill = bucket == Bucket.NUMBER_2X1 && c.unassigned && !tall && !numberCompact(frame)
        val figSp = numberFigSp(frame, pill)
        val labelSp = if (big || tall) (if (frame.roomy) 15f else 14f) else 13f

        mark(rv, R.id.num_mark, c)
        rv.setTextViewText(R.id.num_label, if (numberCompact(frame)) c.name else numberLabel(c, frame))
        rv.setTextViewTextSize(R.id.num_label, TypedValue.COMPLEX_UNIT_SP, labelSp)
        rv.setTextColor(R.id.num_label, ink(s.dark, if (c.dim) DIM_FIGURE else 1f))
        if (c.brokenDot) dot(rv, R.id.num_dot, s.dark)
        if (pill) {
            rv.setViewVisibility(R.id.num_pill, View.VISIBLE)
            rv.setTextColor(R.id.num_pill, s.cardArgb)
        }
        if (bucket == Bucket.NUMBER_2X1 && c.weeklyTagged && !tall) {
            rv.setViewVisibility(R.id.num_weekly2, View.VISIBLE)
            rv.setTextColor(R.id.num_weekly2, ink(s.dark, 0.75f))
        }

        figure(rv, R.id.num_fig, c, s.dark, figSp)
        // Below 4×1 the LEFT caption drops and the bare figure flips; a stacked figure has
        // no row beside it for the caption either.
        val leftCap = c.left && c.pct != null && bucket != Bucket.NUMBER_2X1 && !stacked
        if (leftCap) {
            rv.setViewVisibility(R.id.num_leftcap, View.VISIBLE)
            rv.setTextColor(R.id.num_leftcap, ink(s.dark, 0.75f))
        }
        // CCBG-24 (Duet Label Clamp)'s measure: the label gets exactly what the measured
        // figure leaves on the row.
        val figDp = textDp(context, c.figure, figSp, bold = true) +
            (if (leftCap) textDp(context, "LEFT", 10f, bold = true) + 4f else 0f)
        val labelDp = frame.innerWidthDp - figDp - 10f - 14f - 4f - (if (c.brokenDot) 10f else 0f)
        rv.setInt(R.id.num_label, "setMaxWidth", (labelDp.coerceAtLeast(24f) * d).toInt())
        if (c.dim) rv.setInt(R.id.num_mark, "setImageAlpha", (DIM_FIGURE * 255).toInt())
        if (stacked) stackNumberLabel(rv, frame, s, c, d, figSp, pill)

        val b = bar(frame)!!
        barInto(context, rv, R.id.num_bar, b, s, c)

        if (bucket == Bucket.NUMBER_2X1 && !tall) return
        rv.setViewVisibility(R.id.num_subrow, View.VISIBLE)
        rv.setTextViewText(R.id.num_sub, c.sub ?: "")
        if (frame.roomy) rv.setTextViewTextSize(R.id.num_sub, TypedValue.COMPLEX_UNIT_SP, 13f)
        rv.setTextColor(R.id.num_sub, ink(s.dark, if (c.dim) 0.5f else 0.78f))
        // Rev H (Q4): an unassigned face says how to choose, in the stamp's slot — on the
        // 2×2 on a line of its own.
        (if (c.unassigned) FaceStates.TAP_TO_CHOOSE else s.asOf)?.let {
            stamp(rv, if (tall) R.id.num_stamp_below else R.id.num_stamp, it, s.dark)
        }

        if (!numberControls(frame)) return
        rv.setViewVisibility(R.id.num_ctrl, View.VISIBLE)
        if (big) {
            // Rev H: a 32 dp control row with 26 dp chips on the 4×2.
            rv.setViewLayoutHeight(R.id.num_ctrl_row, 32f, TypedValue.COMPLEX_UNIT_DIP)
            for (id in intArrayOf(R.id.num_chip_s, R.id.num_chip_w, R.id.num_chip_fixed, R.id.num_cycler)) {
                rv.setViewLayoutHeight(id, 26f, TypedValue.COMPLEX_UNIT_DIP)
            }
        }
        if (c.hasBothWindows) {
            chip(rv, R.id.num_chip_s, R.id.num_chip_s_bg, R.id.num_chip_s_text, "5h", c.window == FaceWindow.SESSION, s, c)
            chip(rv, R.id.num_chip_w, R.id.num_chip_w_bg, R.id.num_chip_w_text, "Weekly", c.window == FaceWindow.WEEKLY, s, c)
        } else {
            // A single-window account shows a fixed tag instead of chips.
            rv.setViewVisibility(R.id.num_chip_fixed, View.VISIBLE)
            rv.setImageViewResource(R.id.num_chip_fixed_bg, R.drawable.widget_tag)
            tint(rv, R.id.num_chip_fixed_bg, ink(s.dark, 0.08f))
            rv.setTextViewText(R.id.num_chip_fixed_text, c.window.word)
            rv.setTextColor(R.id.num_chip_fixed_text, ink(s.dark, 0.8f))
            rv.setViewPadding(R.id.num_chip_fixed_text, (8 * d).toInt(), 0, (8 * d).toInt(), 0)
        }
        // The account cycler (Robin, Q5): in the chip row on the 4×2, its own row on the 2×2.
        if (tall) {
            rv.setViewVisibility(R.id.num_cycler, View.GONE)
            rv.setViewVisibility(R.id.num_cycler2, View.VISIBLE)
            cyclerInto(rv, R.id.num_cycler2_bg, R.id.num_cycler2_mark, R.id.num_cycler2_text, s, c)
        } else {
            cyclerInto(rv, R.id.num_cycler_bg, R.id.num_cycler_mark, R.id.num_cycler_text, s, c)
        }
    }

    private fun cyclerInto(rv: RemoteViews, bg: Int, mark: Int, text: Int, s: FaceState, c: Cell) {
        tint(rv, bg, ink(s.dark, 0.08f))
        mark(rv, mark, c)
        rv.setTextViewText(text, cyclerSpan(c, s.dark))
        rv.setTextColor(text, ink(s.dark, 1f))
    }

    /** The on-face views Step 4 wires to `WidgetActionReceiver` (CCRM-78 §On-face controls). */
    val CHIP_SESSION: Int get() = R.id.num_chip_s
    val CHIP_WEEKLY: Int get() = R.id.num_chip_w

    /**
     * The Number's label. At 2×1 an unassigned or weekly-only account shows the name
     * alone — the pill or the second line says the rest (rev D); from 4×1, and on rev H's
     * 2×2, it is inline, and never says "(unassigned)" (Q4: the stamp slot does).
     */
    fun numberLabel(c: Cell, bucket: Bucket): String = numberLabel(c, bucket.frame)

    fun numberLabel(c: Cell, frame: Frame): String = when {
        frame.bucket == Bucket.NUMBER_2X1 && !numberTall(frame) && (c.unassigned || c.weeklyTagged) -> c.name
        else -> "${c.name} · ${c.window.word}"
    }

    /**
     * Rev F's stacked 2×1: the label takes the row alone — the whole inner width, with the
     * window inline ("ChatGPT · Weekly"), the pill still marking an unassigned widget — and
     * the figure moves under it.
     */
    private fun stackNumberLabel(
        rv: RemoteViews, frame: Frame, s: FaceState, c: Cell, d: Float, figSp: Float, pill: Boolean,
    ) {
        // A stacked 4×2 has no row for the LEFT caption, so the label says it (rev D's 2×1
        // rule — the bare figure flips — stays for the 2×1 and 2×2).
        val left = if (c.left && frame.bucket == Bucket.NUMBER_4X2) " · left" else ""
        rv.setTextViewText(R.id.num_label, if (pill) c.name else "${c.name} · ${c.window.word}$left")
        rv.setViewVisibility(R.id.num_weekly2, View.GONE)
        val labelDp = frame.innerWidthDp - 14f - 4f - (if (c.brokenDot) 10f else 0f)
        rv.setInt(R.id.num_label, "setMaxWidth", (labelDp * d).toInt())
        rv.setViewVisibility(R.id.num_fig, View.GONE)
        figure(rv, R.id.num_fig_below, c, s.dark, figSp)
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
            tint(rv, bg, c.accentArgb)
            rv.setTextColor(text, if (s.dark) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt())
        } else {
            rv.setImageViewResource(bg, R.drawable.widget_chip_outline)
            tint(rv, bg, ink(s.dark, 0.28f))
            rv.setTextColor(text, ink(s.dark, 0.85f))
        }
    }

    // ---- Countdown (CCRM-81) --------------------------------------------------------

    private fun countdownFace(context: Context, rv: RemoteViews, frame: Frame, s: FaceState, c: Cell) {
        val bucket = frame.bucket
        val big = bucket == Bucket.COUNTDOWN_2X2
        val dim = if (c.dim) DIM_FIGURE else 1f
        val count = countSp(frame)

        // Rev H (Q4): an unassigned 2×1 says how to choose in its caption; the 2×2 in its stamp slot.
        rv.setTextViewText(R.id.cd_caption, if (c.unassigned && !big) "${countdownCaption(c)} · tap to choose" else countdownCaption(c))
        if (frame.roomy) rv.setTextViewTextSize(R.id.cd_caption, TypedValue.COMPLEX_UNIT_SP, 12f)
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
            // The 110 dp minimum frame says it short, as the Strip does, rather than wrapping
            // over the account line.
            val narrow = frame.innerWidthDp < 120f
            rv.setTextViewText(
                R.id.cd_msg,
                when {
                    StateId.S5 in c.states -> if (narrow) "Not started" else FaceStates.NOT_STARTED
                    else -> if (narrow) "No reading" else FaceStates.NO_READING
                },
            )
            rv.setTextColor(R.id.cd_msg, ink(s.dark, 1f))
            if (big) stampText(s, c)?.let { cdStamp(context, rv, frame, s, c, it) }
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
                rv.setTextViewTextSize(R.id.cd_chrono, TypedValue.COMPLEX_UNIT_SP, count)
                rv.setTextColor(R.id.cd_chrono, ink(s.dark, dim))
                // The absolute time is always on the face (R2), so the count is readable
                // when its form is ambiguous and true after zero.
                // Rev F: on a narrow frame with the height, "at 9:10 PM" drops under the
                // count rather than ellipsizing beside it.
                val below = big || countdownStacked(context, frame, s, "at $clock")
                val at = if (below) R.id.cd_at_below else R.id.cd_at_inline
                rv.setViewVisibility(at, View.VISIBLE)
                rv.setTextViewText(at, "at $clock")
                if (below && !big) {
                    rv.setTextViewTextSize(at, TypedValue.COMPLEX_UNIT_SP, 11f)
                    rv.setViewLayoutMargin(at, RemoteViews.MARGIN_TOP, 2f, TypedValue.COMPLEX_UNIT_DIP)
                }
                if (big && frame.roomy) rv.setTextViewTextSize(at, TypedValue.COMPLEX_UNIT_SP, 14f)
                if (!big && frame.tier == Tier.S_WIDE) rv.setTextViewTextSize(at, TypedValue.COMPLEX_UNIT_SP, 12f)
                rv.setTextColor(at, ink(s.dark, 0.78f * dim))
            }
            CountForm.ABSOLUTE, CountForm.RESET_PASSED -> {
                rv.setViewVisibility(R.id.cd_abs, View.VISIBLE)
                val passed = c.countForm == CountForm.RESET_PASSED
                rv.setTextViewText(R.id.cd_abs, if (passed) "Reset $clock" else clock)
                val text = if (passed) "Reset $clock" else clock
                // Rev F: steps down 2 sp at a time until it fits the frame, never under 18.
                // Rev H: from the count's own size (the past tense at 80 % of it).
                var sp = if (passed) kotlin.math.floor(count * 0.8f).coerceAtLeast(18f) else count
                while (sp > 18f && textDp(context, text, sp, bold = true) > frame.innerWidthDp) sp -= 2f
                rv.setTextViewTextSize(R.id.cd_abs, TypedValue.COMPLEX_UNIT_SP, sp)
                rv.setTextColor(R.id.cd_abs, ink(s.dark, dim))
            }
            CountForm.NONE -> Unit
        }

        if (!big) {
            countdownSide(context, rv, frame, s, c)
            return
        }
        rv.setViewVisibility(R.id.cd_gap1, View.VISIBLE)
        rv.setViewVisibility(R.id.cd_gap2, View.VISIBLE)
        rv.setViewVisibility(R.id.cd_figrow, View.VISIBLE)
        // S6's "—" is at half ink: nothing is read, and the count above says why.
        // Rev H: 32 sp on a frame with the room, rev D's 18 on its own 168 dp frame.
        val figSp = cdFigureSp(frame)
        val leftSp = if (figSp >= 28f) 10f else 8.5f
        val estSp = if (frame.roomy) 12f else 11f
        figure(rv, R.id.cd_fig, c, s.dark, figSp, noReadingAlpha = DIM_FIGURE)
        if (c.left && c.pct != null) {
            rv.setViewVisibility(R.id.cd_leftcap, View.VISIBLE)
            rv.setTextViewTextSize(R.id.cd_leftcap, TypedValue.COMPLEX_UNIT_SP, leftSp)
            rv.setTextColor(R.id.cd_leftcap, ink(s.dark, 0.8f))
        }
        c.estimate?.let {
            // Rev F.1: on a narrow tall frame the estimate takes its own line under the figure.
            val figDp = textDp(context, c.figure, figSp, bold = true) +
                (if (c.left && c.pct != null) textDp(context, "LEFT", leftSp, bold = true) + 4f else 0f)
            val below = figDp + 6f + textDp(context, it, estSp, bold = false) > frame.innerWidthDp && frame.heightDp >= 150f
            val id = if (below) R.id.cd_est_below else R.id.cd_est
            rv.setViewVisibility(id, View.VISIBLE)
            rv.setTextViewText(id, it)
            rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, estSp)
            rv.setTextColor(id, ink(s.dark, if (c.dim) 0.5f else 0.78f))
        }
        rv.setViewVisibility(R.id.cd_bar, View.VISIBLE)
        barInto(context, rv, R.id.cd_bar, bar(frame)!!, s, c)
        stampText(s, c)?.let { cdStamp(context, rv, frame, s, c, it) }
    }

    /** The Countdown 2×2's figure: rev D's 18 sp until the frame has 180 dp inside, then 32. */
    fun cdFigureSp(frame: Frame): Float = if (frame.innerHeightDp >= 180f) 32f else 18f

    /** Rev H (Q4): the stamp slot — "tap to choose account" on an unassigned face. */
    private fun stampText(s: FaceState, c: Cell): String? = if (c.unassigned) FaceStates.TAP_TO_CHOOSE else s.asOf

    /** Rev H: a wide 1-row Countdown's figure over a bar at the row's end (5×1 and up). */
    private fun countdownSide(context: Context, rv: RemoteViews, frame: Frame, s: FaceState, c: Cell) {
        val b = bar(frame) ?: return
        rv.setViewVisibility(R.id.cd_side_gap, View.VISIBLE)
        rv.setViewVisibility(R.id.cd_side, View.VISIBLE)
        figure(rv, R.id.cd_side_fig, c, s.dark, 24f, noReadingAlpha = DIM_FIGURE)
        if (c.left && c.pct != null) {
            rv.setViewVisibility(R.id.cd_side_left, View.VISIBLE)
            rv.setTextColor(R.id.cd_side_left, ink(s.dark, 0.8f))
        }
        barInto(context, rv, R.id.cd_side_bar, b, s, c)
    }

    /**
     * Rev F: a 2×1 count whose "at 9:10 PM" does not fit beside it, on a frame with the
     * height for a second line (100 dp or more), puts it underneath instead.
     */
    private fun countdownStacked(context: Context, frame: Frame, s: FaceState, at: String): Boolean {
        if (frame.bucket != Bucket.COUNTDOWN_2X1 || frame.heightDp < 100f || frame.tier == Tier.S_WIDE) return false
        // Rev H: the same estimate [countSp] sized the count by, so the two never disagree.
        return !countFitsInline(frame.innerWidthDp, countSp(frame))
    }

    /** Rev F.1: the Countdown's stamp, left off when it and the account line do not fit. */
    private fun cdStamp(context: Context, rv: RemoteViews, frame: Frame, s: FaceState, c: Cell, text: String) {
        val need = textDp(context, countdownLabel(c), 11f, bold = false) + 15f + (if (c.brokenDot) 10f else 0f) +
            6f + textDp(context, text, 10f, bold = false)
        if (need <= frame.innerWidthDp) stamp(rv, R.id.cd_stamp, text, s.dark)
    }

    /** "5h reset" on both sides of zero, never "Resets in"; "Stale" at S8. */
    fun countdownCaption(c: Cell): String =
        if (StateId.S8 in c.states) "Stale" else "${c.window.word} reset"

    fun countdownLabel(c: Cell): String = buildString {
        append(c.name)
        append(" · ").append(c.window.word)
        if (c.left) append(" · left")
    }

    // ---- Strip (CCRM-82) ------------------------------------------------------------

    private val STRIP_CELL = intArrayOf(R.id.strip_cell0, R.id.strip_cell1, R.id.strip_cell2, R.id.strip_cell3)
    private val STRIP_RING = intArrayOf(R.id.strip_ring0, R.id.strip_ring1, R.id.strip_ring2, R.id.strip_ring3)
    private val STRIP_FIG = intArrayOf(R.id.strip_fig0, R.id.strip_fig1, R.id.strip_fig2, R.id.strip_fig3)
    private val STRIP_TAG = intArrayOf(R.id.strip_tag0, R.id.strip_tag1, R.id.strip_tag2, R.id.strip_tag3)
    private val STRIP_XTAG = intArrayOf(R.id.strip_xtag0, R.id.strip_xtag1, R.id.strip_xtag2, R.id.strip_xtag3)
    private val STRIP_FREE = intArrayOf(R.id.strip_free0, R.id.strip_free1, R.id.strip_free2, R.id.strip_free3)
    private val STRIP_MARK = intArrayOf(R.id.strip_mark0, R.id.strip_mark1, R.id.strip_mark2, R.id.strip_mark3)
    private val STRIP_LABEL = intArrayOf(R.id.strip_label0, R.id.strip_label1, R.id.strip_label2, R.id.strip_label3)
    private val STRIP_DOT = intArrayOf(R.id.strip_dot0, R.id.strip_dot1, R.id.strip_dot2, R.id.strip_dot3)
    private val STRIP_RESET = intArrayOf(R.id.strip_reset0, R.id.strip_reset1, R.id.strip_reset2, R.id.strip_reset3)

    private fun stripFace(context: Context, rv: RemoteViews, frame: Frame, s: FaceState) {
        val big = frame.bucket == Bucket.STRIP_4X2
        val d = context.resources.displayMetrics.density
        val overflow = s.overflow > 0
        val cellCount = s.cells.size + if (overflow) 1 else 0
        val g = ring(frame, cellCount)!!
        val roomy = stripRoomy(frame)
        // Rev H: the 4×2's bore figure scales with its ring from rev D's 18 sp at Ø88.
        val figSp = if (big) kotlin.math.round(18f * g.diameter / 88f) else if (roomy) 16f else 13f
        val labelSp = if (big && g.diameter >= 100f) 13f else stripLabelSp(frame, cellCount)
        val resetSp = if (big && g.diameter >= 100f) 11f else 10f
        val tagSp = if (big && g.diameter >= 110f) 10f else if (big || roomy) 9f else 8f
        val pitch = stripPitch(frame, s.cells.size, overflow)
        val inline = stripInlineReset(frame, s.cells.size, overflow)

        for (i in 0 until FaceStates.STRIP_MAX) {
            val c = s.cells.getOrNull(i)
            if (c == null) {
                rv.setViewVisibility(STRIP_CELL[i], View.GONE)
                continue
            }
            rv.setViewLayoutWidth(STRIP_CELL[i], pitch, TypedValue.COMPLEX_UNIT_DIP)
            rv.setImageViewBitmap(STRIP_RING[i], ringBitmap(context, g, s, c, d))
            rv.setViewLayoutWidth(STRIP_RING[i], g.diameter, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutHeight(STRIP_RING[i], g.diameter, TypedValue.COMPLEX_UNIT_DIP)
            if (c.dim) dimImage(rv, STRIP_RING[i])
            mark(rv, STRIP_MARK[i], c)
            val reset = c.shortReset?.takeIf { inline && !c.free }
            rv.setTextViewText(STRIP_LABEL[i], if (reset != null) inlineReset(stripLabel(c), reset, labelSp, s.dark) else stripLabel(c))
            rv.setTextViewTextSize(STRIP_LABEL[i], TypedValue.COMPLEX_UNIT_SP, labelSp)
            rv.setTextColor(STRIP_LABEL[i], ink(s.dark, if (c.dim) DIM_FIGURE else 1f))
            rv.setInt(STRIP_LABEL[i], "setMaxWidth", ((pitch - 13f - if (c.brokenDot) 8f else 0f).coerceAtLeast(20f) * d).toInt())
            if (c.brokenDot) dot(rv, STRIP_DOT[i], s.dark)

            if (c.free) {
                // S12 on one ring: the empty extent, the name, and "Free" — under the name
                // on 4×2; in the empty bore on 4×1, where a second line has no room left
                // (Fable call at Step 4, measured: 0 dp under Ø53 + the name).
                // The bore slot is regular weight: a label, not a figure.
                rv.setViewVisibility(STRIP_FIG[i], View.GONE)
                val slot = if (big) STRIP_RESET[i] else STRIP_FREE[i]
                rv.setViewVisibility(slot, View.VISIBLE)
                rv.setTextViewText(slot, "Free")
                if (roomy && !big) rv.setTextViewTextSize(slot, TypedValue.COMPLEX_UNIT_SP, 15f)
                if (big) rv.setTextViewTextSize(slot, TypedValue.COMPLEX_UNIT_SP, resetSp)
                rv.setTextColor(slot, ink(s.dark, 0.7f))
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
                rv.setTextViewTextSize(STRIP_RESET[i], TypedValue.COMPLEX_UNIT_SP, resetSp)
                rv.setTextColor(STRIP_RESET[i], ink(s.dark, if (c.dim) 0.5f else 0.72f))
            }
        }
        if (overflow) {
            rv.setViewVisibility(R.id.strip_plus, View.VISIBLE)
            rv.setTextViewText(R.id.strip_plus, "+${s.overflow}")
            rv.setTextViewTextSize(R.id.strip_plus, TypedValue.COMPLEX_UNIT_SP, figSp)
            rv.setTextColor(R.id.strip_plus, ink(s.dark, 0.7f))
        }
        if (big) s.asOf?.let { stamp(rv, R.id.strip_stamp, it, s.dark) }
    }

    /** "Personal · 9:20 PM": the time at 10 sp and 72 % ink after the name (rev H, 4×1). */
    private fun inlineReset(name: String, reset: String, labelSp: Float, dark: Boolean): CharSequence =
        SpannableString("$name · $reset").apply {
            val start = name.length
            setSpan(RelativeSizeSpan(10f / labelSp), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(ink(dark, 0.72f)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    fun stripLabel(c: Cell): String = if (c.left) "${c.name} · left" else c.name

    /**
     * A 4×1 frame at least 100 dp tall — One UI's cover gives 108 and then draws it at 71 %,
     * so rev D's 9.5 sp name read as 6.8 sp. Rev G (CCBG-41 (Cover Strip Type)) spends the
     * spare height on Ø64, a 16 sp figure and a 12 sp name; rev D's 84 dp frame is untouched.
     */
    fun stripRoomy(frame: Frame): Boolean = frame.bucket == Bucket.STRIP_4X1 && frame.heightDp >= 100f

    /** 11 sp on 4×2, 10 sp once a cell is under 80 dp wide (rev F); on 4×1 12 sp when roomy (rev G), else 9.5. */
    fun stripLabelSp(frame: Frame, cells: Int = FaceStates.STRIP_MAX): Float {
        if (frame.bucket != Bucket.STRIP_4X2) return if (stripRoomy(frame)) 12f else 9.5f
        val n = cells.coerceAtLeast(1)
        return if (kotlin.math.floor((frame.innerWidthDp - 6f * (n - 1)) / n) < 80f) 10f else 11f
    }

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

    /**
     * A white shape drawable in [argb]. A colour filter keeps the drawable's own (opaque)
     * alpha, so a translucent [argb] would still paint solid: the colour goes in opaque
     * and its alpha through `setImageAlpha` (found on the emulator at Step 4).
     */
    private fun tint(rv: RemoteViews, id: Int, argb: Int) {
        rv.setInt(id, "setColorFilter", argb or 0xFF000000.toInt())
        rv.setInt(id, "setImageAlpha", argb ushr 24)
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
