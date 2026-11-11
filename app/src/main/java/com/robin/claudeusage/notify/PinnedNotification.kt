package com.robin.claudeusage.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.robin.claudeusage.MainActivity
import com.robin.claudeusage.R
import com.robin.claudeusage.data.AccountRead
import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.HeadlineSurface
import com.robin.claudeusage.data.StatusGlyph
import com.robin.claudeusage.data.StatusIconChoice
import com.robin.claudeusage.data.StatusIconRule
import com.robin.claudeusage.data.HistoryStore
import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.data.Profile
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.data.SyntheticSeries
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.data.WindowKind
import com.robin.claudeusage.ui.BarGeometry
import com.robin.claudeusage.ui.BarRenderer
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.Grammar
import com.robin.claudeusage.ui.GrammarRing
import com.robin.claudeusage.ui.PaceText
import com.robin.claudeusage.ui.Palette
import com.robin.claudeusage.ui.UsageEndMark
import com.robin.claudeusage.ui.UsageIcon
import com.robin.claudeusage.ui.elapsedPercent
import com.robin.claudeusage.ui.providerMarkRes
import java.time.Instant
import kotlin.math.ceil

/**
 * The optional always-on notification: an account's Weekly usage as the largest
 * number the collapsed row can hold, its 5h as the shape grammar's ring at the left
 * (CCRM-103 (Hybrid Notification), `design/2026-11-02-shape-grammar.html` rev D §3),
 * plus a panel (expanded). It's silent and ongoing, and re-renders on every poll so
 * the percentage and countdown stay live.
 *
 * Since CCRM-67 (Pin Service) the drawing here is only half the story: [update] no
 * longer just posts a notification, it also keeps [PinnedService] running for as
 * long as the pin is on. `setOngoing(true)` from a plain, non-foreground-service
 * notification stopped being honoured by the platform at our targetSdk — the row
 * became swipe-dismissible and fell into the shade's low-priority pile — so the
 * FGS is what actually keeps this pinned and near the top, not the flag on the
 * builder (which stays set anyway, for the pre-FGS-enforcement OEM skins). The
 * drawing logic below is unchanged; only who calls it and when the result gets
 * posted is new. See [PinnedService] for the poll loop this unlocks.
 *
 * Since CCRM-62 (Duet Notification) it can carry **two** accounts. Which layout it
 * renders is decided by one setting and nothing else: with no Second account
 * ([UsageCache.pinnedSecondProfile] null, "None") it is the single layout; with
 * one, it is the Duet — two halves collapsed,
 * two header blocks expanded, each half its own tap target. An install that never
 * sets Second never sees any of it.
 *
 * The colored surfaces (the numbers, the panel bars) follow the theme and the
 * warning ladder — and since CCRM-49 (Glyph Legibility) the status-bar icon does
 * too, drawn with the very same [Palette.barColor] value so the glyph and the
 * number can never disagree. On a Duet that colour carries a second meaning: below
 * 80% it is the shown account's own accent, so the hue says *whose* number the one
 * glyph is showing.
 */
object PinnedNotification {

    // v2: LOW (not MIN) so the status-bar icon actually shows. Channel importance
    // is locked after creation, so the level change needs a fresh channel id.
    // Not private any more (CCRM-67 (Pin Service)): [PinnedService] posts its own
    // throwaway placeholder on this channel for the one race where it has to call
    // startForeground before it has a real notification to show.
    const val CHANNEL = "pinned_usage_v2"

    /**
     * The nominal width every bitmap in this notification is drawn at. RemoteViews
     * never learns the real content width, so both the panel and the collapsed bar
     * assume the same figure and let `fitXY` take up the small remaining difference.
     */
    private const val PANEL_WIDTH_DP = 340f

    // A Duet half's bar is drawn at its own nominal width ([Duet.barDp]) rather than at
    // [PANEL_WIDTH_DP] for the reason in [drawBarBitmap]'s note — a bar squeezed 2×
    // horizontally keeps its tick's *position* but flattens its shape and skews the
    // fill→red boundary off vertical.

    // Not private any more (CCRM-67 (Pin Service)): [PinnedService] posts and
    // cancels under this same id so the notification it's bound to via
    // startForeground survives our own nm.notify() re-renders.
    const val NOTIF_ID = 9100
    const val ACTION_REFRESH = "com.robin.claudeusage.PINNED_REFRESH"

    /** The official Claude Android app — the optional tap target (CCRM-2). */
    val CLAUDE_PACKAGE = Provider.CLAUDE.appPackage

    /** Resolves Claude's launcher intent, or null when it isn't installed. */
    fun claudeLaunchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(CLAUDE_PACKAGE)

    /**
     * Resolves any provider's launcher intent, or null when it isn't installed
     * (CCRM-56 (Provider Identity) — the "app"/"provider" tap target generalises
     * from Claude-only to whichever provider owns the pinned profile).
     */
    fun providerLaunchIntent(context: Context, provider: Provider): Intent? =
        context.packageManager.getLaunchIntentForPackage(provider.appPackage)

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // LOW is fully silent (no sound, no vibration, no heads-up) but, unlike
        // MIN, keeps the status-bar icon. It's a status readout, not an alert.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Pinned usage", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The always-on usage notification"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    /**
     * Renders or removes the notification to match current settings + data, and
     * — since CCRM-67 (Pin Service) — keeps [PinnedService] alive for exactly as
     * long as the pin is on. This is the entry point every call site outside this
     * package still uses; its signature and its "on means shown, off means gone"
     * contract are unchanged. What's new is *how* it stays shown: disabled stops
     * the service and cancels the notification (in that order — cancelling first
     * would let the FGS contract's own notification linger a frame); enabled
     * makes sure the service is running, then posts through `nm.notify` under the
     * same [NOTIF_ID] the service's own `startForeground` used, which is what
     * keeps the two from fighting over the notification's identity.
     */
    fun update(context: Context, cache: UsageCache) {
        val nm = NotificationManagerCompat.from(context)
        if (!cache.pinnedEnabled()) {
            PinnedService.stop(context)
            nm.cancel(NOTIF_ID)
            return
        }
        PinnedService.ensureRunning(context)
        val notification = buildNotification(context, cache) ?: return
        try {
            nm.notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — nothing to show.
        }
    }

    /**
     * Draws the notification for the current settings + data — every pixel [update]
     * used to draw, extracted so [PinnedService] can call it too. Returns null only
     * when the pin is off; [PinnedService]'s own start path (a cold boot restart is
     * the case that matters — it never goes through [update]) needs that same
     * null-means-off answer to decide whether to actually run or just satisfy the
     * foreground-service contract and stop itself.
     */
    fun buildNotification(context: Context, cache: UsageCache): Notification? {
        if (!cache.pinnedEnabled()) return null
        ensureChannel(context)

        val dark = isNightMode(context)
        val use24h = cache.use24hTime()
        // CCRM-22 (Used or Left), rev B: every numeric readout flips — the big
        // digits and the panel rows. The bars and the status-icon fill keep
        // drawing the spend.
        val left = cache.usageLeft()
        // CCRM-100 (Vs Pace Display): the token's third value, the distance from the pace mark.
        val mode = cache.usageMode()
        // CCRM-23 (Reset Display), Option A: the chosen form leads. The expanded
        // line keeps both, chosen first.
        val resetClock = cache.resetClock()
        // CCRM-43 (Bar Pace Marks): one toggle since CCRM-61 (Settings Diet), shared
        // with the in-app bars. The even-pace tick draws regardless; only the colour
        // past it is optional.
        val showOverPace = cache.showOverPace()

        // CCRM-62 (Duet Notification): First is the account this notification has
        // always carried. Second is optional and null means "None" — the whole of the
        // Duet hangs off that one value, so an install that never sets it keeps today's
        // notification unchanged.
        val first = cache.pinnedProfile()
        val second = cache.pinnedSecondProfile()

        val builder =
            if (second == null) {
                singleNotification(context, cache, first, dark, left, mode, use24h, resetClock, showOverPace)
            } else {
                duetNotification(
                    context, cache, first, second, dark, left, mode, use24h, resetClock, showOverPace,
                )
            }

        return builder.build()
    }

    /** Everything both layouts set on the builder: silence, ongoing, and Refresh. */
    private fun baseBuilder(
        context: Context,
        smallIcon: IconCompat,
        title: String,
        text: CharSequence,
        contentIntent: PendingIntent,
        /** Drives the accent the shade uses for the app name. */
        accent: Color,
    ): NotificationCompat.Builder {
        val refresh = PendingIntent.getBroadcast(
            context, NOTIF_ID,
            Intent(context, PinnedRefreshReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(accent.toArgb())
            // One button for both accounts on a Duet: it already refreshes every
            // account, so there is nothing to split.
            .addAction(0, "Refresh", refresh)
    }

    // --- one account (CCRM-103 (Hybrid Notification): H4, the 5h ring at the left) ----

    private fun singleNotification(
        context: Context,
        cache: UsageCache,
        profile: Profile,
        dark: Boolean,
        left: Boolean,
        mode: PaceText.Mode,
        use24h: Boolean,
        resetClock: Boolean,
        showOverPace: Boolean,
    ): NotificationCompat.Builder {
        val h = half(context, cache, profile, dark, mode, use24h, resetClock, duet = false)
        // CCRM-103 (Hybrid Notification), rev D §1e: Appearance › Usage end mark, on the bars and the ring.
        val endMark = cache.usageEndMark()

        // CCRM-49 (Glyph Legibility): the status bar keeps colour, so the glyph wears
        // the same severity colour as the gauge below it — one source of truth, and the
        // two can never disagree. CCRM-51 (Rails Gauge): the marks are neutral ink
        // ("time has no severity"), so no pace hue is passed. CCRM-62 (Duet
        // Notification) took the weekly window off the glyph altogether — it was a flag
        // dot in the needle's hub, and the hollow it sat in is what the ring needed back
        // for legibility.
        // CCRM-103 (Hybrid Notification): the glyph is the account's own (rev D §4), so its
        // input is its own window, not the figure's.
        val smallIcon = drawStatusIcon(
            context, h.gaugeGlyph, h.gaugePct, left, h.gaugeElapsed, h.gaugeFill.toArgb(), dark, showOverPace,
        )

        // CCRM-44 (One Surface): this panel carries every *condition* for every account
        // — the CCBG-12 (Status Icon Swap) sign-in and stale-data strips, plus the update
        // strip — silent by the user's explicit choice, readable on demand. All of them
        // are derived live at draw time (CCRM-61 (Settings Diet) retired the persisted
        // event strips), so nothing here can outlive what it describes.
        //
        // The collapsed row gives its one line to the highest-priority strip (the
        // reset time stays in the expanded header, per the approved CCRM-44
        // wireframe); quiet, it keeps the reset line as always. The dot marks the
        // line as a strip, in the strip's own hue.
        val panelState = Conditions.panelFor(context, cache, profile)
        val collapsedText = collapsedLine(panelState, h.collapsedSub.text, h.accent, dark, synthetic = false)
        // A strip takes the line at the normal size; the pace line's step-down is its own.
        val collapsedSp = if (panelState.strips.isEmpty()) h.collapsedSub.sp else SUB_SP
        // Expanded has the room the collapsed row doesn't: the panel below carries every
        // condition in full, so the header needs no marker at all.
        val expandedText = h.sub.text

        val builder = baseBuilder(
            context, smallIcon, h.title, collapsedLine(panelState, h.collapsedSub.text, h.accent, dark),
            tapIntent(context, cache, profile, slot = 0), h.fill,
        )

        // Not gated on data any more: a condition is worth showing even before the first
        // successful fetch, which is exactly when a sign-in problem is most likely.
        val panel = PanelSpec(
            // CCRM-103 (Hybrid Notification), rev D §3 N2: no companion row any more — the
            // 5h is the ring with its time left (the grammar has no 5h bars) and the Weekly is
            // the header whatever leads. Model caps stay unless the account hides them (CCRM-99).
            bars = if (cache.showModelLimits(profile)) capRows(h.data, null, h.accent) else emptyList(),
            theme = h.accent, dark = dark, mode = mode, showOverPace = showOverPace,
            conditions = panelState.strips, overflow = panelState.overflow,
            endMark = endMark,
        )

        // Custom views: the largest number the collapsed row can hold. Collapsed has no
        // height for the synthetic band (rev E §9c), so it shows the violet dot instead.
        builder.setCustomContentView(
            bigNumberView(
                context, R.layout.notif_big_number, h, collapsedText, collapsedSp,
                dark, showOverPace, endMark, null, panelState.stale, expanded = false,
            ).apply { showSyntheticDot(R.id.synth_dot) }
        )
        builder.setCustomBigContentView(synthetic(context,
            bigNumberView(
                context, R.layout.notif_big_number_expanded, h, expandedText, h.sub.sp,
                dark, showOverPace, endMark, panel, panelState.stale, expanded = true,
            )
        ))
        builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
        return builder
    }

    // --- two accounts: the Duet (CCRM-62) ------------------------------------------

    private fun duetNotification(
        context: Context,
        cache: UsageCache,
        firstProfile: Profile,
        secondProfile: Profile,
        dark: Boolean,
        left: Boolean,
        mode: PaceText.Mode,
        use24h: Boolean,
        resetClock: Boolean,
        showOverPace: Boolean,
    ): NotificationCompat.Builder {
        val first = half(context, cache, firstProfile, dark, mode, use24h, resetClock, duet = true)
        val second = half(context, cache, secondProfile, dark, mode, use24h, resetClock, duet = true)
        val panelState = Conditions.panelFor(context, cache, firstProfile, secondProfile)
        val endMark = cache.usageEndMark()

        // One glyph, two accounts: "Status-bar ring shows" picks which. Its colour is
        // now the account identity as much as the severity, which is the whole reason a
        // single glyph works here — and why "Whichever is higher" is allowed to switch
        // during the day, the colour change being the tell.
        val ring = when (Duet.ringAccount(cache.statusRingShows(), first.gaugePct, second.gaugePct)) {
            Duet.Slot.FIRST -> first
            Duet.Slot.SECOND -> second
        }
        // That account's own Status-bar icon draws it (rev D §4b).
        val smallIcon = drawStatusIcon(
            context, ring.gaugeGlyph, ring.gaugePct, left, ring.gaugeElapsed, ring.gaugeFill.toArgb(), dark, showOverPace,
        )

        // The plain strings still matter: the lock screen, TalkBack, and any skin that
        // ignores the custom view read these and nothing else. Both accounts, both
        // numbers, in the order the halves are drawn.
        val title = "${first.label} ${first.pctText} · ${second.label} ${second.pctText}"
        val collapsedText = collapsedLine(panelState, first.collapsedSub.text, first.accent, dark)

        val firstTap = tapIntent(context, cache, firstProfile, slot = 0)
        val secondTap = tapIntent(context, cache, secondProfile, slot = 1)
        // The shade's app-name accent follows the *ring's* account, so the glyph in the
        // status bar and the chrome under it are one colour rather than two.
        val builder = baseBuilder(
            context, smallIcon, title, collapsedText,
            // Taps that miss a half — the row's edges, the expanded panel — belong to
            // First, which is also the account the ring defaults to.
            contentIntent = firstTap, accent = ring.fill,
        )

        // CCRM-103 (Hybrid Notification), rev D §3 D4: the companion rows are gone — each 5h
        // is its header's ring with its time left, each Weekly its header — so the panel
        // carries only the condition strip. Still no model caps on a Duet (CCBG-26 (Panel
        // Scaling)).
        val panel = PanelSpec(
            bars = emptyList(),
            theme = first.accent, dark = dark, mode = mode, showOverPace = showOverPace,
            endMark = endMark,
            // CCRM-15 (Above-Pace Verification), rev E §9c: the synthetic band spends
            // ~19 dp of the cap the one strip was priced against, so under synthetic the
            // expanded Duet draws no strip. The collapsed halves keep their condition dots.
            conditions = if (SyntheticSeries.isOn) emptyList() else panelState.strips,
            overflow = if (SyntheticSeries.isOn) 0 else panelState.overflow,
            compact = true,
        )

        val update = Conditions.hasUpdate(context, cache)
        builder.setCustomContentView(
            duetView(
                context, R.layout.notif_duet, first, second, dark, showOverPace, endMark,
                update, panel = null, expanded = false, firstTap = firstTap, secondTap = secondTap,
            )
        )
        builder.setCustomBigContentView(synthetic(context,
            duetView(
                context, R.layout.notif_duet_expanded, first, second, dark, showOverPace, endMark,
                update, panel = panel, expanded = true, firstTap = firstTap, secondTap = secondTap,
            )
        ))
        builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
        return builder
    }

    /**
     * Where a tap goes (CCRM-2). "provider" jumps straight into that account's own
     * provider app; anything else — including "provider" when that app isn't installed —
     * opens our own breakdown on that account. (CCRM-56 (Provider Identity): the stored
     * value "claude" from pre-multi-provider installs reads as "provider".) The setting
     * is one choice applied per half, each half resolving it against its own provider.
     *
     * [slot] is 0 for First, 1 for Second, and it is in the request code because
     * **`PendingIntent` identity ignores the extras**: two `getActivity` intents that
     * differ only in their `"profile"` extra and share a request code are the *same*
     * PendingIntent, so `FLAG_UPDATE_CURRENT` would rewrite the first half's extra with
     * the second's and both halves would open the same account. `NOTIF_ID` and
     * `NOTIF_ID + 1` were the old single-layout codes and `NOTIF_ID` is still the refresh
     * broadcast's, so the per-half codes start clear of them at `NOTIF_ID + 10`.
     */
    private fun tapIntent(
        context: Context,
        cache: UsageCache,
        profile: Profile,
        slot: Int,
    ): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val target = cache.pinnedTapTarget()
        if (target == "provider" || target == "claude") {
            val launch = providerLaunchIntent(context, profile.provider)
            if (launch != null) {
                // Launched from a notification, so it needs its own task.
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return PendingIntent.getActivity(context, NOTIF_ID + 20 + slot, launch, flags)
            }
        }
        return PendingIntent.getActivity(
            context, NOTIF_ID + 10 + slot,
            Intent(context, MainActivity::class.java).putExtra("profile", profile.key),
            flags,
        )
    }

    // --- one account's headline reading -------------------------------------------

    /**
     * Everything either layout needs about one account, resolved once.
     *
     * [Half] is deliberately the same shape for both layouts: the single row fills one
     * of them and the Duet fills two, so the headline promotion, the severity colour and
     * the reset line cannot drift between the two views. What differs is [title] and
     * [sub], which are built in the wording the calling layout uses — see [half].
     */
    private data class Half(
        val profile: Profile,
        val label: String,
        /** The account's resolved accent (CCRM-56), *before* the severity ladder. */
        val accent: Color,
        val data: UsageData?,
        /**
         * CCRM-103 (Hybrid Notification), rev D §3 Q2: the 5h's words lead the sub line —
         * CCRM-96 (Weekly First)'s Headline picked the 5h (5-hour, or Auto while it binds).
         * The shapes never swap: the figure and the bar stay the Weekly, the ring the 5h.
         */
        val takeover: Boolean,
        /** The Weekly's used percent — the figure and the bar, whatever leads. */
        val pct: Double?,
        val pctText: String,
        /** [Palette.barColor] of [pct] over [accent] — the bar's and the shade accent's hue. */
        val fill: Color,
        /** The figure's colour: [fill], or in Vs pace green / red / ink (rev D §7b). */
        val figureColor: Color,
        val elapsed: Double?,
        /** "Personal · Weekly", on the single row and a Duet alike (CCBG-45 (Single Panel Wording)). */
        val title: String,
        /**
         * The expanded header's label: [title], or the name alone when the derived column cannot hold "· Weekly" too
         * (CCRM-114 (Narrow Duet), N1-Q7: "· Weekly" gives before the name). Single and Duet.
         */
        val headerTitle: String,
        /** The single collapsed row's label, by the same rule at that row's column and 13 sp. */
        val collapsedTitle: String,
        /**
         * The expanded header's first line (single and Duet): the pace line in the form the
         * column holds (CCRM-104 (Pace Line Parity); CCBG-61 (Duet Line Clip)'s tight form is
         * its second drop-order step), at its size.
         */
        val sub: Sub,
        /** The single expanded header's second line — the pace-mark caption (D4) — or null. */
        val sub2: Sub?,
        /** The single collapsed row's line: the pace line, or the 5h's words in a takeover. */
        val collapsedSub: Sub,
        /** The 5h ring, or null for a plan with no 5h (B1: no ring at all). */
        val ring: Ring?,
        /** "LEFT", "VS PACE" or null, under the figure. */
        val caption: String?,
        /** The status-bar gauge's window — Weekly behaves as Auto there. */
        val gaugePct: Double?,
        /** CCRM-103: what the status bar draws for this account; [gaugePct] is that glyph's window. */
        val gaugeGlyph: StatusGlyph,
        val gaugeElapsed: Double?,
        val gaugeFill: Color,
        /** core §6.2: the 5h is the limit about to stop this account. */
        val binding: Boolean,
        val stale: Boolean,
        /** A sign-in that stopped working or a stale reading — the condition dot's cue. */
        val fault: Boolean,
        /**
         * CCRM-105 (Duet Stack), wireframe §1: the collapsed row's state word ([Duet.word]) and
         * its two forms, full and with the tail dropped; null for a row with nothing to say.
         */
        val word: Duet.Word?,
        val wordFull: String?,
        val wordShort: String?,
        /** The 5h's ladder colour, which "Held · …" takes; null without a 5h reading. */
        val heldColor: Color?,
        /** Sign in, no reading, or a Weekly not started: "—", a bare track, no tick. */
        val dash: Boolean,
        /** A Free plan with no usage: the one fault with no word, so it keeps the 6 dp dot. */
        val planFault: Boolean,
    )

    /**
     * CCRM-103 (Hybrid Notification), shape grammar rev D §3: one account's 5h ring, drawn
     * by [GrammarRing.paint] with its figure a real TextView in the bore.
     */
    private data class Ring(
        val pct: Double?,
        val elapsed: Double?,
        /** "34%", "66%" (Left), "19%" (Vs pace) or "—". */
        val figure: String,
        /** The figure's colour; null = the ink, or the severity colour above 90 ([GrammarRing.loud]). */
        val figureColor: Color?,
        /** [GrammarRing.State.GREY] while the week is spent (CCRM-102 (Spent Week Gate)). */
        val state: GrammarRing.State,
        /** "in 2h 20m" under the expanded ring, or null. */
        val timeLeft: String?,
        /** "5-hour window, 34 percent". */
        val description: String,
    )

    private fun half(
        context: Context,
        cache: UsageCache,
        profile: Profile,
        dark: Boolean,
        mode: PaceText.Mode,
        use24h: Boolean,
        resetClock: Boolean,
        /** True for the two-account layout: a stricter no-reading. */
        duet: Boolean,
    ): Half {
        val label = cache.profileLabel(profile)
        val accent = Palette.color(Palette.accentName(cache, profile), dark)
        val snapshot = cache.snapshot(profile)
        val data = snapshot.data
        // CCRM-96 (Weekly First): the Headline decides, per account. Weekly leads by
        // default; Auto (and the status-bar gauge) switch to 5h while it binds. An account
        // with no session window at all — ChatGPT Plus/Pro since OpenAI lifted the 5-hour
        // limit, Claude Ultra since 2026-10-30 — always leads with Weekly.
        val points = if (data?.session != null) HistoryStore(context).points(profile) else null
        val hitsAt = AccountRead.sessionHitsLimitAtMs(data, points)
        val binding = AccountRead.sessionBinding(data, hitsAt)
        val setting = cache.headline(profile)
        val headlineWeekly =
            AccountRead.lead(setting, HeadlineSurface.NOTIFICATION, data, binding) == WindowKind.WEEKLY
        // CCRM-103 (Hybrid Notification), shape grammar rev D §4: the Headline no longer picks
        // the status-bar window. The ring is always the 5h; the account's Status-bar icon (or a
        // plan with no 5h) makes it the Weekly bar; a full window is the red cross, a spent week
        // greys the ring. No reading yet keeps the account's choice.
        val iconChoice = cache.statusIcon(profile)
        val iconHasSession = data == null || data.session != null
        val glyph = StatusIconRule.glyph(
            iconChoice, iconHasSession, sessionPct = data?.session?.percent, weeklyPct = data?.weekly?.percent,
        )
        // The window the glyph stands for — the Weekly for the bar (and its cross), else the
        // 5h — is also what a Duet's "Whichever is higher" compares (BEHAVIOR-MOBILE §5).
        val gaugeWeekly = !iconHasSession || iconChoice == StatusIconChoice.WEEKLY_BAR
        // CCRM-103 (Hybrid Notification), rev D §3: the shapes never swap. The figure, the bar
        // and the label are the Weekly whatever leads; the Headline only decides whose words
        // the sub line carries (Q2, Auto takeover). An account with no 5h always leads Weekly.
        val takeover = !headlineWeekly
        // CCRM-62 (Duet Notification): on a Duet half a broken sign-in reads as no
        // reading at all — the placeholder figure and an empty track with no tick — so
        // the 6 dp dot and the panel strip are what say why, and the *other* half stays
        // crisp. The single layout is untouched by this: with one account there is
        // nothing to contrast against, and it keeps showing what was last cached.
        val broken = duet && snapshot.authState == AuthState.REAUTH_NEEDED
        val weekly = data?.weekly
        val session = data?.session
        val pct = if (broken) null else weekly?.percent
        val elapsed = if (broken) null else elapsedPercent(weekly, Projection.WEEKLY_MS)
        val weeklyElapsed = elapsedPercent(weekly, Projection.WEEKLY_MS)
        val weeklySpent = Pace.isSpent(weekly?.percent)
        // Rev D §3 Q6/Q7: "No reading yet" with nothing fetched (single and Duet alike now),
        // "Weekly not started" without a Weekly clock; a takeover keeps today's line for a
        // 5h with no clock.
        val fallback = when {
            broken || data == null -> LeadLines.NO_READING
            takeover && session?.resetsAt == null -> "Not started yet"
            !takeover && (weekly?.percent == null || weekly.resetsAt == null) -> LeadLines.WEEKLY_NOT_STARTED
            else -> null
        }
        // CCRM-104 (Pace Line Parity), wireframe D3/D10: Weekly leads with the pace line, "21h 50m
        // ahead · Sun 9:30 PM" (its "<duration> ahead|behind" span tinted), in the form the
        // column's width holds at this font scale; a spent week says "Out until …"; a takeover:
        // the 5h's reset line, "Back at …" while it binds (its projection line is unchanged).
        val ink = Grammar.ink(dark)
        val fontScale = context.resources.configuration.fontScale
        val nowAt = Instant.now()
        val fixedLine: String? = fallback ?: if (takeover) {
            LeadLines.sessionLine(session, binding, resetClock, use24h) ?: ""
        } else if (weeklySpent) {
            LeadLines.outUntil(weekly!!, use24h)
        } else null
        val fill = Palette.barColor(pct, accent, dark)
        val figureColor =
            if (mode == PaceText.Mode.VS_PACE && pct != null) {
                Grammar.vsColor(PaceText.vsPaceTone(pct, elapsed), dark, onInk = ink, usage = fill)
            } else fill
        // B1: a plan with no 5h has no ring at all. No reading yet keeps one (the plan is
        // unknown, as the status-bar icon assumes).
        val ring = if (data != null && session == null) null else {
            val rPct = if (broken) null else session?.percent
            val rElapsed = if (broken) null else elapsedPercent(session, Projection.SESSION_MS)
            val grey = !broken && weeklySpent
            val rFigure = LeadLines.ringFigure(rPct, rElapsed, mode) // unsigned in the ring (D1)
            val tone = if (mode == PaceText.Mode.VS_PACE && rPct != null) PaceText.vsPaceTone(rPct, rElapsed) else null
            Ring(
                pct = rPct,
                elapsed = rElapsed,
                figure = rFigure,
                // Vs pace first (rev D §7b), then the binding 5h's ladder colour (Q1/Q2: "the
                // figure in that colour"), else the ink, loud above 90.
                figureColor = when {
                    rPct == null || grey -> null
                    tone != null && tone != PaceText.VsTone.USAGE ->
                        Grammar.vsColor(tone, dark, onInk = ink, usage = ink)
                    binding -> Palette.barColor(rPct, accent, dark)
                    else -> null
                },
                state = if (grey) GrammarRing.State.GREY else GrammarRing.State.NORMAL,
                timeLeft = if (broken || grey) null else LeadLines.ringTimeLeft(session),
                description = LeadLines.ringDescription(rFigure),
            )
        }
        // CCBG-80 (Fixed Pace Columns), CCRM-114 (Narrow Duet) F2: each line is fitted to its column as derived from
        // the screen's smallest width, the ring (and its time line) and the figure beside it, sizes in dp from the
        // system — never wider than the measured Fold 7 constants until Step 3 measures again.
        val sw = context.resources.configuration.smallestScreenWidthDp
        val metrics = context.resources.displayMetrics
        val spToDp = TextFit.SpToDp { sp -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics) / metrics.density }
        val pctText = LeadLines.figure(pct, elapsed, mode)
        val noReading = !broken && data == null
        val notStarted = !broken && data != null && (weekly?.percent == null || weekly.resetsAt == null)
        val dash = broken || noReading || notStarted
        // A Duet header prints "—" for a row with no figure; the single layout prints its own figure.
        val headerFigure = if (duet && dash) "—" else pctText
        val headerColumn = TextFit.expandedColumnDp(
            sw, hasRing = ring != null, ringTime = ring?.timeLeft, figure = headerFigure,
            figureSp = if (duet) TextFit.DUET_EXPANDED_FIGURE_SP else TextFit.SINGLE_EXPANDED_FIGURE_SP, spToDp = spToDp,
        )
        val headerFit = TextFit.fitDp(headerColumn, if (duet) TextFit.DUET_EXPANDED_COLUMN_DP else TextFit.EXPANDED_COLUMN_DP)
        val collapsedColumn = TextFit.collapsedColumnDp(sw, hasRing = ring != null, figure = pctText, spToDp = spToDp)
        val collapsedFit = TextFit.fitDp(collapsedColumn, TextFit.COLLAPSED_COLUMN_DP)
        fun subFor(availDp: Float): Sub {
            if (fixedLine != null) {
                // F2: the spent line has no drop order of its own; it drops its second clause whole.
                if (weeklySpent && fallback == null && !takeover &&
                    TextFit.widthDp(TextFit.em(fixedLine), SUB_SP, fontScale) > availDp
                ) return Sub(LeadLines.outUntilShort(weekly!!, use24h), SUB_SP)
                return Sub(fixedLine, SUB_SP)
            }
            val pick = LeadLines.weeklyPace(weekly, nowAt, use24h, availDp, SUB_SP, fontScale)
                ?: return Sub("", SUB_SP)
            return Sub(paceSpan(pick.form, dark, ink), pick.sp)
        }
        val headerSub = subFor(headerFit)
        // Line 2: the pace-mark caption, permanently (D4, C1-3); a takeover keeps the 5h projection.
        val sub2: Sub? = when {
            duet || fallback != null -> null
            takeover -> LeadLines.sessionSecondLine(hitsAt, use24h)?.let { Sub(it, SUB_SP) }
            weeklySpent -> null
            else -> LeadLines.weeklyCaption(
                weekly, weeklyElapsed, mode, headerFit, SUB_SP, fontScale,
            )?.let { Sub(it.form, it.sp) }
        }
        // Rev D §3 N1: the collapsed line is the pace line only — the ring replaced the "· 5h 34%"
        // companion, and the pace line carries the reset, so a plan with no 5h needs no "resets Sun".
        val collapsedSub = subFor(collapsedFit)
        val title = LeadLines.headerTitle(label)
        // N1-Q7: "· Weekly" gives before the name, both layouts.
        val headerTitle =
            if (TextFit.titleKeepsWindow(
                    title, headerColumn, spToDp.dp(TextFit.EXPANDED_TITLE_SP),
                    if (duet) TextFit.duetLabelMaxDp(ring != null) else Float.MAX_VALUE,
                )
            ) title else label
        val collapsedTitle =
            if (TextFit.titleKeepsWindow(title, collapsedColumn, spToDp.dp(TextFit.COLLAPSED_TITLE_SP))) title else label
        val gaugeWindow = if (gaugeWeekly) weekly else data?.session
        val gaugePct = if (broken) null else gaugeWindow?.percent
        // CCRM-105 (Duet Stack), wireframe §1: the collapsed row's state word, by precedence.
        val stale = Conditions.isStale(cache, profile)
        val word = Duet.word(
            signIn = snapshot.authState == AuthState.REAUTH_NEEDED,
            noReading = noReading,
            stale = stale,
            weekSpent = weeklySpent,
            held = binding,
            notStarted = notStarted,
        )
        val wordClock = when (word) {
            // Core §6.3: a bare clock always means today; a 5h that lets go after midnight names its day.
            Duet.Word.HELD -> session?.resetsAt?.let { PaceText.dayClock(it, nowAt.toEpochMilli(), use24h) }
            Duet.Word.OUT -> weekly?.resetsAt?.let { LeadLines.dayOrClock(it, nowAt.toEpochMilli(), use24h) }
            else -> null
        }
        val wordForms = word?.let { Duet.wordText(it, wordClock) }
        return Half(
            profile = profile,
            label = label,
            accent = accent,
            data = data,
            takeover = takeover,
            pct = pct,
            pctText = pctText,
            fill = fill,
            figureColor = figureColor,
            elapsed = elapsed,
            title = title,
            headerTitle = headerTitle,
            collapsedTitle = collapsedTitle,
            sub = headerSub,
            sub2 = sub2,
            collapsedSub = collapsedSub,
            ring = ring,
            caption = LeadLines.figureCaption(pct, mode),
            gaugePct = gaugePct,
            gaugeGlyph = glyph,
            gaugeElapsed = if (broken) null else elapsedPercent(
                gaugeWindow, if (gaugeWeekly) Projection.WEEKLY_MS else Projection.SESSION_MS,
            ),
            gaugeFill = Palette.barColor(gaugePct, accent, dark),
            binding = binding,
            stale = stale,
            fault = Conditions.hasFault(cache, profile),
            word = word,
            wordFull = wordForms?.first,
            wordShort = wordForms?.second,
            heldColor = session?.percent?.let { Palette.barColor(it, accent, dark) },
            dash = dash,
            planFault = Conditions.hasPlanFault(cache, profile),
        )
    }

    // --- view filling -------------------------------------------------------------

    /**
     * Fills one of the big-number layouts. Text colours come from the layout's
     * theme attributes so they follow the notification shade; only the percentage
     * takes the usage colour, which is the whole point of the style.
     */
    /**
     * R8 (CCRM-15 (Above-Pace Verification)): [inner] under the full-width
     * "SYNTHETIC DATA" band while the synthetic series is on, else [inner] itself.
     * Expanded views only: a collapsed row has no height for it (rev E §9c) and takes
     * [showSyntheticDot] instead.
     */
    private fun synthetic(context: Context, inner: RemoteViews): RemoteViews =
        if (!SyntheticSeries.isOn) inner
        else RemoteViews(context.packageName, R.layout.notif_synthetic_wrap).apply {
            addView(R.id.synthetic_body, inner)
        }

    /**
     * The collapsed line: the top strip behind its dot, else [sub]. With [synthetic]
     * (the default, for the builder's content text), rev E §9c: surfaces that never
     * draw the custom views (Wear, skins that ignore RemoteViews) show only the content
     * title and text, so while the synthetic series is on the text says so. The ● of a
     * strip line stays leading. The custom row passes false: its violet dot says it.
     */
    private fun collapsedLine(
        panelState: Conditions.Panel,
        sub: CharSequence,
        accent: Color,
        dark: Boolean,
        synthetic: Boolean = true,
    ): CharSequence {
        val prefix = if (synthetic && SyntheticSeries.isOn) "Synthetic data · " else ""
        return panelState.strips.firstOrNull()
            ?.let { withConditionDot(prefix + it.short, conditionHue(it, accent, dark)) }
            // TextUtils.concat keeps the companion's spans (CCRM-96 (Weekly First)).
            ?: if (prefix.isEmpty()) sub else android.text.TextUtils.concat(prefix, sub)
    }

    /** Rev E §9c: the collapsed row's violet marker, gone in the layout unless synthetic is on. */
    private fun RemoteViews.showSyntheticDot(id: Int) {
        if (SyntheticSeries.isOn) setViewVisibility(id, android.view.View.VISIBLE)
    }

    private fun bigNumberView(
        context: Context,
        layout: Int,
        h: Half,
        sub: CharSequence,
        subSp: Float,
        dark: Boolean,
        showOverPace: Boolean,
        endMark: UsageEndMark,
        panel: PanelSpec?,
        stale: Boolean,
        /** The expanded layout: `sub2` and the ring's time line exist there. */
        expanded: Boolean,
    ): RemoteViews = RemoteViews(context.packageName, layout).apply {
        setImageViewResource(R.id.provider_mark, providerMarkRes(h.profile.provider))
        setInt(R.id.provider_mark, "setColorFilter", h.accent.toArgb())
        // CCRM-103 (Hybrid Notification), rev D §1b/§7b: the "%" at 58%, Vs pace coloured.
        setTextViewText(R.id.pct, Grammar.smallPercent(h.pctText))
        setTextColor(R.id.pct, h.figureColor.toArgb())
        // CCRM-22 / CCRM-100: "LEFT", or "UNDER PACE" / "OVER PACE" / "ON PACE", under the number.
        setViewVisibility(
            R.id.pct_caption,
            if (h.caption != null) android.view.View.VISIBLE else android.view.View.GONE,
        )
        if (h.caption != null) setTextViewText(R.id.pct_caption, h.caption)
        setTextViewText(R.id.title, if (expanded) h.headerTitle else h.collapsedTitle)
        setTextViewText(R.id.sub, sub)
        setTextViewTextSize(R.id.sub, TypedValue.COMPLEX_UNIT_SP, subSp)
        // CCRM-96 (Weekly First), CCRM-104 (Pace Line Parity) D4: the expanded header's second
        // line, the pace-mark caption (or the 5h projection under a takeover).
        if (expanded && h.sub2 != null) {
            setTextViewText(R.id.sub2, h.sub2.text)
            setTextViewTextSize(R.id.sub2, TypedValue.COMPLEX_UNIT_SP, h.sub2.sp)
            setViewVisibility(R.id.sub2, android.view.View.VISIBLE)
        }
        setImageViewBitmap(
            R.id.bar, drawBarBitmap(context, h.pct, h.elapsed, h.accent, dark, showOverPace, endMark),
        )
        // Rev D §3 N1/N2: the 44 dp 5h ring on the left; the time left under it, expanded.
        if (h.ring != null) {
            fillRing(
                context, RingIds(R.id.ring_box, R.id.ring_img, R.id.ring_fig, if (expanded) R.id.ring_time else null),
                h.ring, HEADER_RING_DP, h.accent, dark, showOverPace, endMark, stale,
            )
        }
        if (panel != null) fillPanel(context, panel)
        // CCBG-12 (Status Icon Swap): a stale reading drawn as crisply as a live one is the
        // actual hazard — the old separate alert said "stale" somewhere else in the shade
        // while the number here still looked authoritative. Fading the number and its bar
        // puts the doubt on the figure itself. The strip below names the cause.
        if (stale) {
            setFloat(R.id.pct, "setAlpha", 0.5f)
            setFloat(R.id.bar, "setAlpha", 0.45f)
        }
    }

    /** CCRM-104 (Pace Line Parity): the sub lines are 12 sp; [TextFit.STEP_DOWN_SP] is the last-resort step. */
    private const val SUB_SP = 12f

    /** A sub line and the size to set it at (12 sp, or 10 when even the floor form would not fit). */
    private class Sub(val text: CharSequence, val sp: Float)

    /**
     * The pace line with its "<duration> behind|ahead" span in the tone colour and semi-bold
     * (wireframe §8's +3% allowance); a line on the mark, or one with no span, has none.
     */
    private fun paceSpan(line: PaceText.PaceLine, dark: Boolean, ink: Color): CharSequence {
        val tinted = line.tinted ?: return line.text
        if (!line.text.startsWith(tinted)) return line.text
        return SpannableString(line.text).apply {
            val color = Grammar.vsColor(line.tone, dark, onInk = ink, usage = ink).toArgb()
            setSpan(ForegroundColorSpan(color), 0, tinted.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, tinted.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** Rev D §3: the header ring, single and Duet expanded (the figure 0.29 × 44 ≈ 12.8 sp before fitting). */
    private const val HEADER_RING_DP = 44f

    /** The view ids of one ring: its box (shown or gone), bitmap, figure and optional time line. */
    private class RingIds(val box: Int, val img: Int, val fig: Int, val time: Int?)

    /**
     * Fills one ring's views: the bitmap from [GrammarRing.paint] at [sizeDp], the figure
     * a TextView fitted to the bore ([GrammarRing.figureDp], [GrammarRing.fitFigure], sized
     * in dp so font scale cannot push it into the band), and the time line under it.
     */
    private fun RemoteViews.fillRing(
        context: Context,
        ids: RingIds,
        r: Ring,
        sizeDp: Float,
        accent: Color,
        dark: Boolean,
        showOverPace: Boolean,
        endMark: UsageEndMark,
        stale: Boolean,
    ) {
        setViewVisibility(ids.box, android.view.View.VISIBLE)
        setContentDescription(ids.box, r.description)
        setImageViewBitmap(ids.img, drawRingBitmap(context, sizeDp, r, accent, dark, showOverPace, endMark))
        setTextViewText(ids.fig, Grammar.smallPercent(r.figure))
        setTextViewTextSize(ids.fig, TypedValue.COMPLEX_UNIT_DIP, ringFigureDp(context, sizeDp, r.figure))
        setTextColor(ids.fig, ringFigureColor(r, accent, dark).toArgb())
        if (ids.time != null) {
            setViewVisibility(ids.time, if (r.timeLeft == null) android.view.View.GONE else android.view.View.VISIBLE)
            if (r.timeLeft != null) {
                setTextViewText(ids.time, r.timeLeft)
                // CCRM-114 (Narrow Duet), N1-Q6: sized in dp like the bore figure above it — at font 2.0 an sp line
                // was cut in half by its 13 dp box and took 48 dp of the pace line's column.
                setTextViewTextSize(ids.time, TypedValue.COMPLEX_UNIT_DIP, TextFit.TIME_LINE_DP)
            }
        }
        if (stale) {
            setFloat(ids.img, "setAlpha", 0.45f)
            setFloat(ids.fig, "setAlpha", 0.5f)
        }
    }

    /** The ring figure's colour, as `GrammarRingBox` draws it: dimmed grey / no reading, else its own, loud, or ink. */
    private fun ringFigureColor(r: Ring, accent: Color, dark: Boolean): Color {
        val ink = Grammar.ink(dark)
        return when {
            r.state == GrammarRing.State.GREY -> ink.copy(alpha = 0.45f)
            r.pct == null -> ink.copy(alpha = 0.5f)
            r.figureColor != null -> r.figureColor
            GrammarRing.loud(r.pct) -> Palette.barColor(r.pct, accent, dark)
            else -> ink
        }
    }

    /** [GrammarRing.figureDp] fitted to the bore by the figure's measured width (tabular, small "%"). */
    private fun ringFigureDp(context: Context, sizeDp: Float, figure: String): Float {
        val density = context.resources.displayMetrics.density
        val base = GrammarRing.figureDp(sizeDp, figure)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = base * density
            fontFeatureSettings = Grammar.FIGURE_FEATURES
            letterSpacing = -0.02f
        }
        val small = figure.length >= 2 && figure.last() == '%' && figure[figure.length - 2].isDigit()
        val widthPx =
            if (small) paint.measureText(figure.dropLast(1)) + Grammar.PCT_SCALE * paint.measureText("%")
            else paint.measureText(figure)
        return GrammarRing.fitFigure(sizeDp, base, widthPx / density)
    }

    /** The ring bitmap at [sizeDp]: transparent, so the end mark's gap is cleared to the shade. */
    private fun drawRingBitmap(
        context: Context,
        sizeDp: Float,
        r: Ring,
        accent: Color,
        dark: Boolean,
        showOverPace: Boolean,
        endMark: UsageEndMark,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = ceil(sizeDp * density).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also {
            GrammarRing.paint(
                Canvas(it), 0f, 0f, px.toFloat(), density, r.pct, r.elapsed, accent, dark,
                state = r.state, endMark = endMark, showOverPace = showOverPace, surface = null,
            )
        }
    }

    /** The view ids one Duet account owns. Both Duet layouts use the same set; [sub], [caption] and [ringTime] are expanded only, [word] and [line] collapsed only. */
    private class HalfIds(
        /** The collapsed row, or the expanded header: the account's tap target. */
        val root: Int,
        val mark: Int,
        val label: Int,
        val dot: Int,
        val bar: Int,
        val pct: Int,
        val sub: Int,
        val caption: Int,
        val ringBox: Int,
        val ring: Int,
        val ringFig: Int,
        val ringTime: Int,
        val word: Int,
        /** Collapsed only: the mark, name and dot line, padded at its end by the word's width. */
        val line: Int,
    )

    private val FIRST_IDS = HalfIds(
        root = R.id.duet_half_first, mark = R.id.duet_mark_first,
        label = R.id.duet_label_first, dot = R.id.duet_dot_first,
        bar = R.id.duet_bar_first, pct = R.id.duet_pct_first,
        sub = R.id.duet_sub_first, caption = R.id.duet_caption_first,
        ringBox = R.id.duet_ring_box_first, ring = R.id.duet_ring_first, ringFig = R.id.duet_ring_fig_first,
        ringTime = R.id.duet_ring_time_first, word = R.id.duet_word_first, line = R.id.duet_line_first,
    )

    private val SECOND_IDS = HalfIds(
        root = R.id.duet_half_second, mark = R.id.duet_mark_second,
        label = R.id.duet_label_second, dot = R.id.duet_dot_second,
        bar = R.id.duet_bar_second, pct = R.id.duet_pct_second,
        sub = R.id.duet_sub_second, caption = R.id.duet_caption_second,
        ringBox = R.id.duet_ring_box_second, ring = R.id.duet_ring_second, ringFig = R.id.duet_ring_fig_second,
        ringTime = R.id.duet_ring_time_second, word = R.id.duet_word_second, line = R.id.duet_line_second,
    )

    /**
     * Fills either Duet layout: [expanded] false is the collapsed stack (CCRM-105 (Duet Stack):
     * two rings, two rows), true the two header blocks (window name, pace line, "LEFT" caption)
     * plus the panel. One function for both, so the two views cannot disagree about an
     * account's number, colour, dot or tap target.
     */
    private fun duetView(
        context: Context,
        layout: Int,
        first: Half,
        second: Half,
        dark: Boolean,
        showOverPace: Boolean,
        endMark: UsageEndMark,
        /** App-global (CCRM-44), so it dots First only. */
        updateAvailable: Boolean,
        panel: PanelSpec?,
        expanded: Boolean,
        firstTap: PendingIntent,
        secondTap: PendingIntent,
    ): RemoteViews = RemoteViews(context.packageName, layout).apply {
        // The dot is a pointer, not a message: red for this account's own fault, the
        // accent for the app-global update strip. Either way it means "there is a strip
        // for this in the expanded panel". Collapsed, a fault with a state word (sign-in,
        // stale) says so in words, so the dot stays only for the Free-plan fault (§1).
        fun dotHue(h: Half, update: Boolean): Int? = when {
            (if (expanded) h.fault else h.planFault) -> conditionHue(error = true, theme = h.accent, dark = dark)
            update -> conditionHue(error = false, theme = h.accent, dark = dark)
            else -> null
        }
        val firstDot = dotHue(first, updateAvailable)
        val secondDot = dotHue(second, update = false)
        if (expanded) {
            fillHalf(context, FIRST_IDS, first, dark, showOverPace, endMark, firstTap, firstDot)
            fillHalf(context, SECOND_IDS, second, dark, showOverPace, endMark, secondTap, secondDot)
        } else {
            val fontScale = context.resources.configuration.fontScale
            val contentDp = Duet.contentDp(context.resources.configuration.smallestScreenWidthDp)
            val barDp = Duet.barDp(contentDp)
            // Rev E §9c: app-global like the update dot, so First only.
            val synth = SyntheticSeries.isOn
            fillRow(context, FIRST_IDS, first, dark, showOverPace, endMark, firstTap, firstDot, synth, contentDp, barDp, fontScale)
            showSyntheticDot(R.id.duet_synth_dot)
            fillRow(context, SECOND_IDS, second, dark, showOverPace, endMark, secondTap, secondDot, false, contentDp, barDp, fontScale)
        }
        if (panel != null) fillPanel(context, panel)
    }

    /** The 6 dp condition dot after the label, or none. */
    private fun RemoteViews.fillDot(ids: HalfIds, dotHue: Int?) {
        setViewVisibility(ids.dot, if (dotHue == null) android.view.View.GONE else android.view.View.VISIBLE)
        if (dotHue != null) setInt(ids.dot, "setColorFilter", dotHue)
    }

    /** One expanded header: as shipped (rev D §3 D4), the figure at 24 sp (CCRM-105, from the layout). */
    private fun RemoteViews.fillHalf(
        context: Context,
        ids: HalfIds,
        h: Half,
        dark: Boolean,
        showOverPace: Boolean,
        endMark: UsageEndMark,
        tap: PendingIntent,
        dotHue: Int?,
    ) {
        // Per-view PendingIntents: the one thing a custom RemoteViews layout can do that
        // the styles CCRM-61 (Settings Diet) removed could not, and the reason each account
        // can be its own tap target rather than sharing one content intent.
        setOnClickPendingIntent(ids.root, tap)
        setImageViewResource(ids.mark, providerMarkRes(h.profile.provider))
        setInt(ids.mark, "setColorFilter", h.accent.toArgb())
        setTextViewText(ids.label, h.headerTitle)
        // CCRM-103 (Hybrid Notification), rev D §3 D4: every header with a 5h takes the 44 dp
        // ring and its time left; none for no 5h (B1).
        if (h.ring != null) {
            fillRing(
                context, RingIds(ids.ringBox, ids.ring, ids.ringFig, ids.ringTime),
                h.ring, HEADER_RING_DP, h.accent, dark, showOverPace, endMark, h.stale,
            )
            // The layout's 200 dp label bound assumed the full 328 dp row; the ring and
            // its 14 dp gap take 58 of it, so the dot after the label stays on screen.
            setInt(ids.label, "setMaxWidth", dp(context, TextFit.duetLabelMaxDp(hasRing = true)).toInt())
        }
        // Sign in, no reading, a Weekly not started: "—" and a bare track (notification.md §1.4;
        // CCRM-105 keeps the two views agreeing on it).
        setTextViewText(ids.pct, Grammar.smallPercent(if (h.dash) "—" else h.pctText))
        setTextColor(ids.pct, (if (h.dash) Grammar.ink(dark).copy(alpha = 0.5f) else h.figureColor).toArgb())
        setImageViewBitmap(
            ids.bar,
            drawBarBitmap(
                context, if (h.dash) null else h.pct, if (h.dash) null else h.elapsed,
                h.accent, dark, showOverPace, endMark, widthDp = PANEL_WIDTH_DP,
            ),
        )
        // CCRM-104 (Pace Line Parity): the pace line in the form the header's column holds.
        setTextViewText(ids.sub, h.sub.text)
        setTextViewTextSize(ids.sub, TypedValue.COMPLEX_UNIT_SP, h.sub.sp)
        setViewVisibility(ids.caption, if (h.caption != null) android.view.View.VISIBLE else android.view.View.GONE)
        if (h.caption != null) setTextViewText(ids.caption, h.caption)
        fillDot(ids, dotHue)
        // Per-account staleness: the doubt belongs to one figure, not the whole notification.
        if (h.stale) {
            setFloat(ids.pct, "setAlpha", 0.5f)
            setFloat(ids.bar, "setAlpha", 0.45f)
        }
    }

    /**
     * CCRM-105 (Duet Stack), wireframe §1: one account's collapsed share — its 36 dp 5h ring at
     * the left (unsigned; track and cross for a plan with no 5h) and its row (mark, name, state
     * word, Weekly bar, the signed figure in the 42 dp slot). Ring and row both open the account.
     * The text is sized in dp, capped (wireframe §1, Robin 2026-11-10).
     */
    private fun RemoteViews.fillRow(
        context: Context,
        ids: HalfIds,
        h: Half,
        dark: Boolean,
        showOverPace: Boolean,
        endMark: UsageEndMark,
        tap: PendingIntent,
        dotHue: Int?,
        synthDot: Boolean,
        contentDp: Float,
        barDp: Float,
        fontScale: Float,
    ) {
        setOnClickPendingIntent(ids.root, tap)
        setOnClickPendingIntent(ids.ringBox, tap)
        setImageViewResource(ids.mark, providerMarkRes(h.profile.provider))
        setInt(ids.mark, "setColorFilter", h.accent.toArgb())
        setTextViewText(ids.label, h.label)
        setTextViewTextSize(ids.label, TypedValue.COMPLEX_UNIT_DIP, Duet.cappedDp(Duet.NAME_SP, fontScale, Duet.NAME_CAP_DP))

        // The ring: the 5h as everywhere (rev D §3); a plan with no 5h keeps its slot as the
        // track and a cross, so both rings stay put and left ring = top row.
        val ring = h.ring ?: Ring(
            pct = null, elapsed = null, figure = "", figureColor = null,
            state = GrammarRing.State.NOT_APPLICABLE, timeLeft = null, description = LeadLines.NO_FIVE_HOUR,
        )
        fillRing(
            context, RingIds(ids.ringBox, ids.ring, ids.ringFig, null),
            ring, Duet.RING_DP, h.accent, dark, showOverPace, endMark, h.stale,
        )

        // The state word, by precedence, through the narrow drop order (Duet.rowFit, CCRM-114 (Narrow Duet)): its
        // tail dropped once the name is at its floor; under 256 dp of content a row with no figure gives the word the
        // figure's slot; a bare word that would leave the name under 32 dp goes ("Stale" leaving its dot).
        val dots = (if (dotHue != null) 1 else 0) + (if (synthDot) 1 else 0)
        val fit = Duet.rowFit(
            contentDp, barDp, dots, h.label, h.word, h.wordFull, h.wordShort, h.dash, fontScale,
            dotShown = dotHue != null,
        )
        if (fit.word == null) {
            setViewVisibility(ids.word, android.view.View.GONE)
            setViewPadding(ids.line, 0, 0, 0, 0)
        } else {
            setViewVisibility(ids.word, android.view.View.VISIBLE)
            setTextViewText(ids.word, fit.word)
            setViewPadding(ids.line, 0, 0, dp(context, Duet.wordPadDp(fit.word, fontScale)).toInt(), 0)
            setTextViewTextSize(ids.word, TypedValue.COMPLEX_UNIT_DIP, Duet.cappedDp(Duet.WORD_SP, fontScale, Duet.WORD_CAP_DP))
            val color: Color? = when (h.word) {
                Duet.Word.SIGN_IN, Duet.Word.STALE -> Color(conditionHue(error = true, theme = h.accent, dark = dark))
                Duet.Word.OUT -> Palette.barColor(100.0, h.accent, dark)
                Duet.Word.HELD -> h.heldColor
                else -> null // the layout's info colour
            }
            if (color != null) setTextColor(ids.word, color.toArgb())
        }
        // TalkBack hears the full word whichever rung printed: on the word ("Held · 3:40 PM" while "Held" prints), or,
        // with no word printed, on the name ("Design, No reading").
        setContentDescription(ids.word, h.wordFull)
        setContentDescription(ids.label, if (fit.word == null && h.wordFull != null) "${h.label}, ${h.wordFull}" else null)

        // The Weekly bar and figure. Sign in / no reading / not started: "—", a bare track, no
        // tick; a spent week: the full red bar, no tick (wireframe §1).
        val spent = Pace.isSpent(h.pct)
        setImageViewBitmap(
            ids.bar,
            drawBarBitmap(
                context, if (h.dash) null else h.pct, if (h.dash || spent) null else h.elapsed,
                h.accent, dark, showOverPace, endMark, widthDp = barDp,
            ),
        )
        setTextViewText(ids.pct, Grammar.smallPercent(if (h.dash) "—" else h.pctText))
        setTextViewTextSize(ids.pct, TypedValue.COMPLEX_UNIT_DIP, Duet.cappedDp(Duet.FIGURE_SP, fontScale, Duet.FIGURE_CAP_DP))
        setTextColor(ids.pct, (if (h.dash) Grammar.ink(dark).copy(alpha = 0.5f) else h.figureColor).toArgb())
        // Variant C: the "—" gives its slot to the word — the figure view goes, so the name line runs on over the gap
        // and the slot to the row's edge, and the bar's end is padded by the same 50 dp so it keeps its length.
        setViewVisibility(ids.pct, if (fit.inSlot) android.view.View.GONE else android.view.View.VISIBLE)
        setViewPadding(ids.bar, 0, 0, if (fit.inSlot) dp(context, Duet.SLOT_RUN_DP).toInt() else 0, 0)
        // A "Stale" that went leaves v2.0's 6 dp fault-red dot after the name.
        fillDot(ids, if (fit.staleDot) conditionHue(error = true, theme = h.accent, dark = dark) else dotHue)
        // Stale: figure 50%, bar 45% (the ring's own dimming is in fillRing).
        if (h.stale) {
            setFloat(ids.pct, "setAlpha", 0.5f)
            setFloat(ids.bar, "setAlpha", 0.45f)
        }
    }

    // --- drawing ---

    /**
     * A headline bar with its pace mark: the collapsed row's, or a Duet header's.
     *
     * Rendered at a nominal width rather than the old `h × 80`. The ImageView is still
     * `fitXY` — RemoteViews can't tell us the real content width — but at roughly 1:1 the
     * residual stretch is a few percent instead of the 2.4× horizontal squeeze the old
     * aspect produced, which flattened the tick to about 0.19 h and skewed the fill→red
     * boundary off the vertical (CCRM-43 (Bar Pace Marks) wireframe rev B, D4). The
     * tick's *position* was always exact; this is about its shape. [widthDp] is what
     * keeps that true on a Duet half, which is 156 dp wide and not 340.
     */
    private fun drawBarBitmap(
        context: Context,
        pct: Double?,
        elapsed: Double?,
        theme: Color,
        dark: Boolean,
        showOverPace: Boolean,
        /** CCRM-103 (Hybrid Notification), rev D §1e: Appearance › Usage end mark. */
        endMark: UsageEndMark,
        widthDp: Float = PANEL_WIDTH_DP,
    ): Bitmap = BarRenderer.draw(
        widthPx = dp(context, widthDp),
        heightPx = dp(context, 8f),
        percent = pct,
        elapsedPercent = elapsed,
        accent = theme,
        dark = dark,
        showOverPace = showOverPace,
        endMark = endMark,
        // The bitmap is transparent: a null surface clears the mark's gap to the shade.
        surfaceArgb = null,
        density = context.resources.displayMetrics.density,
    )

    private fun isNightMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun dp(context: Context, value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics
        )

    /**
     * The status-bar icon: the ring gauge drawn by [UsageIcon], for one window — the
     * headline 5h one — and one account. There is no weekly reading on it since CCRM-62
     * (Duet Notification) dropped the hub's flag dot; [fillArgb] now carries the account's
     * identity as well as the severity, so on a Duet the hue is what says whose number
     * this is.
     */
    private fun drawStatusIcon(
        context: Context,
        glyph: StatusGlyph,
        pct: Double?,
        left: Boolean,
        sessionElapsed: Double?,
        fillArgb: Int?,
        dark: Boolean,
        showOverPace: Boolean,
    ): IconCompat = IconCompat.createWithBitmap(
        UsageIcon.drawGlyph(context, glyph, pct, left, sessionElapsed, fillArgb, dark, showOverPace)
    )

    /**
     * The collapsed row's condition marker: a coloured dot ahead of the reset line.
     *
     * A span rather than a second view, because the collapsed layout has no slot to spare and
     * `setContentText` has to carry the same string the custom view's `sub` line shows (a
     * fallback for surfaces that don't render the custom view at all).
     *
     * U+25CF BLACK CIRCLE at full text size, deliberately with no size span. The
     * glyph is drawn centred about 0.3 em above the baseline, so it lines up with the text
     * beside it on its own; scaling it to 60% to look "dot-sized" moved that centre to
     * 0.18 em and visibly dropped it below the line — and made it small enough to miss.
     * Unscaled it is roughly 7 dp across, matching the panel strip's own 6 dp dot.
     */
    private fun withConditionDot(text: String, hue: Int): CharSequence =
        SpannableString("●  $text").apply {
            setSpan(ForegroundColorSpan(hue), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    /**
     * A fault gets a fixed red — it must not be themed away by an accent that happens to be
     * green. A warning takes the user's own accent, which is what the rest of the panel is
     * already drawn in.
     */
    private fun conditionHue(
        condition: Conditions.Condition,
        theme: Color,
        dark: Boolean,
    ): Int = conditionHue(condition.error, theme, dark)

    /** [conditionHue] by kind, for the Duet's dot — which has no [Conditions.Condition]. */
    private fun conditionHue(error: Boolean, theme: Color, dark: Boolean): Int = when {
        error && dark -> AndroidColor.parseColor("#E0705A")
        error -> AndroidColor.parseColor("#B3402A")
        else -> theme.toArgb()
    }

    /**
     * One bar row of the expanded panel — since CCRM-103 (Hybrid Notification) a model cap
     * only: the 5h is the header's ring (the grammar has no 5h bars) and the Weekly is the
     * header itself, so the companion rows of CCRM-96 (Weekly First) are gone.
     *
     * [accent] is per row, not per panel, so a row always draws in its account's colour.
     */
    private data class PanelBar(
        val label: String,
        val window: UsageWindow?,
        val sub: String,
        val accent: Color,
        /** What the row's pace mark measures against: 7 days. */
        val lengthMs: Long = Projection.WEEKLY_MS,
    )

    /** One account's per-model cap rows, prefixed with its label on a Duet. */
    private fun capRows(data: UsageData?, label: String?, accent: Color): List<PanelBar> =
        data?.modelCaps?.map {
            PanelBar(
                label = if (label == null) it.modelName else "$label · ${it.modelName}",
                window = it.window,
                sub = "",
                accent = accent,
            )
        } ?: emptyList()

    /**
     * What the expanded panel shows: the bar rows (weekly windows, per-model caps) and
     * the condition strips, plus the settings that decide how they draw. Resolved once
     * per render and handed to [fillPanel], which fills whichever panel layout the
     * expanded view carries.
     *
     * Each account's headline window is deliberately absent: it's the figure plus the
     * title in the header above, and repeating it here would be duplicate info. There's
     * no profile header either, for the same reason — the title already names it, and on
     * a Duet every row carries its own account prefix.
     *
     * [compact] is the Duet (CCRM-62 (Duet Notification)): `notif_panel_duet.xml`, where
     * a row puts label, figure and reset on **one** line above the bar, and a strip is one
     * line of its [Conditions.Condition.short] text with any overflow folded into a
     * "+ n more" tail. The single layout keeps its full rows and two-line strips in
     * `notif_panel_single.xml`.
     */
    private class PanelSpec(
        val bars: List<PanelBar>,
        /** The condition strips' warning hue; each bar row carries its own accent. */
        val theme: Color,
        val dark: Boolean,
        /** CCRM-22 (Used or Left), CCRM-100 (Vs Pace Display) — the row readouts; the bars draw the spend. */
        val mode: PaceText.Mode,
        val showOverPace: Boolean,
        val conditions: List<Conditions.Condition>,
        val overflow: Int = 0,
        val compact: Boolean = false,
        /** CCRM-103 (Hybrid Notification), rev D §1e: Appearance › Usage end mark. */
        val endMark: UsageEndMark = UsageEndMark.DEFAULT,
    )

    private class StripIds(val slot: Int, val bg: Int, val dot: Int, val title: Int, val detail: Int, val more: Int)
    private class RowIds(val slot: Int, val label: Int, val sub: Int, val value: Int, val bar: Int, val below: Int)

    private val STRIP_IDS = listOf(
        StripIds(R.id.panel_strip_1, R.id.panel_strip_bg_1, R.id.panel_strip_dot_1, R.id.panel_strip_title_1, R.id.panel_strip_detail_1, R.id.panel_strip_more_1),
        StripIds(R.id.panel_strip_2, R.id.panel_strip_bg_2, R.id.panel_strip_dot_2, R.id.panel_strip_title_2, R.id.panel_strip_detail_2, R.id.panel_strip_more_2),
        StripIds(R.id.panel_strip_3, R.id.panel_strip_bg_3, R.id.panel_strip_dot_3, R.id.panel_strip_title_3, R.id.panel_strip_detail_3, R.id.panel_strip_more_3),
    )
    private val ROW_IDS = listOf(
        RowIds(R.id.panel_row_1, R.id.panel_row_label_1, R.id.panel_row_sub_1, R.id.panel_row_value_1, R.id.panel_row_bar_1, R.id.panel_row_below_1),
        RowIds(R.id.panel_row_2, R.id.panel_row_label_2, R.id.panel_row_sub_2, R.id.panel_row_value_2, R.id.panel_row_bar_2, R.id.panel_row_below_2),
        RowIds(R.id.panel_row_3, R.id.panel_row_label_3, R.id.panel_row_sub_3, R.id.panel_row_value_3, R.id.panel_row_bar_3, R.id.panel_row_below_3),
        RowIds(R.id.panel_row_4, R.id.panel_row_label_4, R.id.panel_row_sub_4, R.id.panel_row_value_4, R.id.panel_row_bar_4, R.id.panel_row_below_4),
    )

    /** The strip tint: 13% of its hue, what the old bitmap painted. */
    private const val STRIP_TINT_ALPHA = 0x21

    /**
     * Fills the expanded panel — the `notif_panel_*.xml` slots — from a [PanelSpec].
     *
     * CCBG-26 (Panel Scaling): this used to be a bitmap. RemoteViews never learns the
     * card's width or the height Android leaves under the header blocks, so a bitmap
     * taller than that room was scaled down uniformly by its `ImageView` — strips and
     * Weekly rows shrinking together to about 60% — while the same rows drew at full
     * width whenever no strip was up. Native views cannot be scaled: a `TextView` is as
     * tall as its sp and as wide as the card, and the bars are `fitXY` bitmaps exactly as
     * the header bars are. What no longer fits is *cut*, by the rules below, never shrunk.
     *
     * Rows on the Duet: only what the caller passes (the two Weekly rows — model caps
     * leave the Duet panel, they never fit unshrunk). Rows on the single layout: the
     * CCRM-44 (One Surface) budget of four minus one per strip, weekly first, as before.
     * Strips: on the Duet one, as [Conditions.Condition.short], with the overflow as a
     * "+ n more" tail on that line; on the single layout up to [Conditions.MAX_STRIPS] with
     * title and detail, and the overflow as its own line. Both come pre-capped from
     * [Conditions.panelFor]; the slot count here is the ceiling, not the rule.
     */
    private fun RemoteViews.fillPanel(context: Context, s: PanelSpec) {
        val gone = android.view.View.GONE
        val visible = android.view.View.VISIBLE

        val strips = s.conditions.take(STRIP_IDS.size)
        STRIP_IDS.forEachIndexed { i, ids ->
            val c = strips.getOrNull(i)
            setViewVisibility(ids.slot, if (c == null) gone else visible)
            if (c == null) return@forEachIndexed
            val hue = conditionHue(c, s.theme, s.dark)
            setInt(ids.bg, "setColorFilter", hue)
            setInt(ids.bg, "setImageAlpha", STRIP_TINT_ALPHA)
            setInt(ids.dot, "setColorFilter", hue)
            setTextViewText(ids.title, if (s.compact) c.short else c.title)
            setTextViewText(ids.detail, c.detail)
            // The Duet folds the overflow into its one strip's tail rather than spending a
            // line on it — a separate line is what would push the second Weekly row out.
            val tail = if (s.compact && i == 0 && s.overflow > 0) "+${s.overflow} more" else null
            setViewVisibility(ids.more, if (tail == null) gone else visible)
            if (tail != null) setTextViewText(ids.more, tail)
        }

        // CCRM-44 (One Surface): the "+ n more" line when strips overflowed the cap.
        val moreLine = !s.compact && s.overflow > 0
        setViewVisibility(R.id.panel_more, if (moreLine) visible else gone)
        if (moreLine) {
            setTextViewText(R.id.panel_more, "+ ${s.overflow} more — open the app for the rest")
        }

        // Conditions displace model caps, never a weekly window — it is the only bar the
        // expanded panel exists to carry, so it holds its place however many strips
        // appear. The Duet's caller already passes only its Weekly rows.
        val budget = if (s.compact) ROW_IDS.size else (4 - strips.size).coerceAtLeast(1)
        val shown = s.bars.take(budget)
        ROW_IDS.forEachIndexed { i, ids ->
            val bar = shown.getOrNull(i)
            setViewVisibility(ids.slot, if (bar == null) gone else visible)
            if (bar == null) return@forEachIndexed
            val pct = bar.window?.percent
            val elapsed = elapsedPercent(bar.window, bar.lengthMs)
            setTextViewText(ids.label, bar.label)
            setTextViewText(ids.value, Grammar.smallPercent(LeadLines.figure(pct, elapsed, s.mode)))
            // Rev D §7b: an unsigned Vs pace figure says its side by colour — green under the
            // mark, red over. On the mark, or fallen back to usage, it keeps the row's ink.
            if (s.mode == PaceText.Mode.VS_PACE && pct != null) {
                when (PaceText.vsPaceTone(pct, elapsed)) {
                    PaceText.VsTone.UNDER -> setTextColor(ids.value, Grammar.vsUnder(s.dark).toArgb())
                    PaceText.VsTone.OVER -> setTextColor(ids.value, Grammar.vsOver(s.dark).toArgb())
                    else -> Unit
                }
            }
            // The layout decides which of the two sub slots is visible (inline on the
            // Duet, under the bar on the single layout); both get the text, and an empty
            // sub — a model cap — hides whichever is in use.
            setTextViewText(ids.sub, bar.sub)
            setTextViewText(ids.below, bar.sub)
            if (bar.sub.isEmpty()) {
                setViewVisibility(ids.sub, gone)
                setViewVisibility(ids.below, gone)
            }
            // Each row measures its pace against its own window (7 days for a model cap).
            setImageViewBitmap(
                ids.bar,
                drawBarBitmap(context, pct, elapsed, bar.accent, s.dark, s.showOverPace, s.endMark),
            )
        }

        setViewVisibility(
            R.id.panel_root,
            if (shown.isEmpty() && strips.isEmpty() && !moreLine) gone else visible,
        )
    }
}
