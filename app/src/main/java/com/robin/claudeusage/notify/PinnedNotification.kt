package com.robin.claudeusage.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.robin.claudeusage.MainActivity
import com.robin.claudeusage.R
import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.Profile
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.ui.BarGeometry
import com.robin.claudeusage.ui.BarRenderer
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.Palette
import com.robin.claudeusage.ui.UsageIcon
import com.robin.claudeusage.ui.elapsedPercent
import com.robin.claudeusage.ui.providerMarkRes

/**
 * The optional always-on notification: an account's 5-hour usage as the largest
 * number the collapsed row can hold, plus a full bar panel (expanded). It's silent
 * and ongoing, and re-renders on every poll so the percentage and countdown stay
 * live.
 *
 * Since CCRM-62 (Duet Notification) it can carry **two** accounts. Which layout it
 * renders is decided by one setting and nothing else: with no Second account
 * ([UsageCache.pinnedSecondProfile] null, "None") it is today's single layout, down
 * to its "5-hour window" wording; with one, it is the Duet — two halves collapsed,
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
    private const val CHANNEL = "pinned_usage_v2"

    /**
     * The nominal width every bitmap in this notification is drawn at. RemoteViews
     * never learns the real content width, so both the panel and the collapsed bar
     * assume the same figure and let `fitXY` take up the small remaining difference.
     */
    private const val PANEL_WIDTH_DP = 340f

    /**
     * The nominal width of one Duet half's bar (CCRM-62 (Duet Notification)): a
     * 360 dp card, less 16 dp of system padding a side, less the 16 dp gutter, split
     * two ways. Drawn at its own width rather than at [PANEL_WIDTH_DP] for the reason
     * in [drawBarBitmap]'s note — a bar squeezed 2× horizontally keeps its tick's
     * *position* but flattens its shape and skews the fill→red boundary off vertical.
     */
    private const val HALF_WIDTH_DP = 156f

    private const val NOTIF_ID = 9100
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

    /** Renders or removes the notification to match current settings + data. */
    fun update(context: Context, cache: UsageCache) {
        val nm = NotificationManagerCompat.from(context)
        if (!cache.pinnedEnabled()) {
            nm.cancel(NOTIF_ID)
            return
        }
        ensureChannel(context)

        val dark = isNightMode(context)
        val use24h = cache.use24hTime()
        // CCRM-22 (Used or Left), rev B: every numeric readout flips — the big
        // digits and the panel rows. The bars and the status-icon fill keep
        // drawing the spend.
        val left = cache.usageLeft()
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
                singleNotification(context, cache, first, dark, left, use24h, resetClock, showOverPace)
            } else {
                duetNotification(
                    context, cache, first, second, dark, left, use24h, resetClock, showOverPace,
                )
            }

        try {
            nm.notify(NOTIF_ID, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — nothing to show.
        }
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

    // --- one account (today's layout, unchanged) ------------------------------------

    private fun singleNotification(
        context: Context,
        cache: UsageCache,
        profile: Profile,
        dark: Boolean,
        left: Boolean,
        use24h: Boolean,
        resetClock: Boolean,
        showOverPace: Boolean,
    ): NotificationCompat.Builder {
        val h = half(cache, profile, dark, left, use24h, resetClock, duet = false)

        // CCRM-49 (Glyph Legibility): the status bar keeps colour, so the glyph wears
        // the same severity colour as the gauge below it — one source of truth, and the
        // two can never disagree. CCRM-51 (Rails Gauge): the marks are neutral ink
        // ("time has no severity"), so no pace hue is passed. CCRM-62 (Duet
        // Notification) took the weekly window off the glyph altogether — it was a flag
        // dot in the needle's hub, and the hollow it sat in is what the ring needed back
        // for legibility.
        val smallIcon =
            drawStatusIcon(context, h.pct, left, h.elapsed, h.fill.toArgb(), dark, showOverPace)

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
        val collapsedText = panelState.strips.firstOrNull()
            ?.let { withConditionDot(it.short, conditionHue(it, h.accent, dark)) }
            ?: h.sub
        // Expanded has the room the collapsed row doesn't: the panel below carries every
        // condition in full, so the header needs no marker at all.
        val expandedText = h.sub

        val builder = baseBuilder(
            context, smallIcon, h.title, collapsedText,
            tapIntent(context, cache, profile, slot = 0), h.fill,
        )

        // Not gated on data any more: a condition is worth showing even before the first
        // successful fetch, which is exactly when a sign-in problem is most likely.
        val panel = drawPanel(
            context,
            // CCRM-54 (ChatGPT Account) part 2: once the 7-day window has been
            // promoted to the headline, the panel's 7-day bar would print the same
            // number twice. Model caps still belong here.
            bars = buildList {
                if (!h.headlineWeekly) {
                    weeklyRow(h.data, null, h.accent, resetClock, use24h)?.let { add(it) }
                }
                addAll(capRows(h.data, null, h.accent))
            },
            theme = h.accent, dark = dark, usageLeft = left, showOverPace = showOverPace,
            conditions = panelState.strips, overflow = panelState.overflow,
        )

        // Custom views: the largest number the collapsed row can hold.
        builder.setCustomContentView(
            bigNumberView(
                context, R.layout.notif_big_number, h.pctText, h.title, collapsedText,
                h.pct, h.elapsed, h.fill, h.accent, dark, showOverPace, null, panelState.stale,
                profile.provider,
                leftCaption = left && h.pct != null,
            )
        )
        builder.setCustomBigContentView(
            bigNumberView(
                context, R.layout.notif_big_number_expanded,
                h.pctText, h.title, expandedText,
                h.pct, h.elapsed, h.fill, h.accent, dark, showOverPace, panel, panelState.stale,
                profile.provider,
                leftCaption = left && h.pct != null,
            )
        )
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
        use24h: Boolean,
        resetClock: Boolean,
        showOverPace: Boolean,
    ): NotificationCompat.Builder {
        val first = half(cache, firstProfile, dark, left, use24h, resetClock, duet = true)
        val second = half(cache, secondProfile, dark, left, use24h, resetClock, duet = true)
        val panelState = Conditions.panelFor(context, cache, firstProfile, secondProfile)

        // One glyph, two accounts: "Status-bar ring shows" picks which. Its colour is
        // now the account identity as much as the severity, which is the whole reason a
        // single glyph works here — and why "Whichever is higher" is allowed to switch
        // during the day, the colour change being the tell.
        val ring = when (Duet.ringAccount(cache.statusRingShows(), first.pct, second.pct)) {
            Duet.Slot.FIRST -> first
            Duet.Slot.SECOND -> second
        }
        val smallIcon = drawStatusIcon(
            context, ring.pct, left, ring.elapsed, ring.fill.toArgb(), dark, showOverPace,
        )

        // The plain strings still matter: the lock screen, TalkBack, and any skin that
        // ignores the custom view read these and nothing else. Both accounts, both
        // numbers, in the order the halves are drawn.
        val title = "${first.label} ${first.pctText} · ${second.label} ${second.pctText}"
        val collapsedText = panelState.strips.firstOrNull()
            ?.let { withConditionDot(it.short, conditionHue(it, first.accent, dark)) }
            ?: first.sub

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

        // Panel order: each account's Weekly row (skipped for an account whose headline
        // *is* the weekly window — the CCRM-54 (ChatGPT Account) promotion, now per
        // account, because printing the same number twice is what the rule exists to
        // stop), then the model caps. The 4-minus-strips budget is unchanged, so in
        // practice a Duet panel with one strip shows the two Weekly rows and nothing
        // else.
        val panel = drawPanel(
            context,
            bars = buildList {
                if (!first.headlineWeekly) {
                    weeklyRow(first.data, first.label, first.accent, resetClock, use24h)
                        ?.let { add(it) }
                }
                if (!second.headlineWeekly) {
                    weeklyRow(second.data, second.label, second.accent, resetClock, use24h)
                        ?.let { add(it) }
                }
                addAll(capRows(first.data, first.label, first.accent))
                addAll(capRows(second.data, second.label, second.accent))
            },
            theme = first.accent, dark = dark, usageLeft = left, showOverPace = showOverPace,
            conditions = panelState.strips, overflow = panelState.overflow,
            compact = true,
        )

        val update = Conditions.hasUpdate(context, cache)
        builder.setCustomContentView(
            duetView(
                context, R.layout.notif_duet, first, second, dark, left, showOverPace,
                update, panel = null, expanded = false, firstTap = firstTap, secondTap = secondTap,
            )
        )
        builder.setCustomBigContentView(
            duetView(
                context, R.layout.notif_duet_expanded, first, second, dark, left, showOverPace,
                update, panel = panel, expanded = true, firstTap = firstTap, secondTap = secondTap,
            )
        )
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
        /** CCRM-54 (ChatGPT Account): the weekly window is this account's headline. */
        val headlineWeekly: Boolean,
        val pct: Double?,
        val pctText: String,
        /** [Palette.barColor] of [pct] over [accent] — the figure's and the ring's hue. */
        val fill: Color,
        val elapsed: Double?,
        /** "Personal · 5h" on a Duet, "Personal · 5-hour window" on the single row. */
        val title: String,
        val sub: String,
        val stale: Boolean,
        /** A sign-in that stopped working or a stale reading — the condition dot's cue. */
        val fault: Boolean,
    )

    private fun half(
        cache: UsageCache,
        profile: Profile,
        dark: Boolean,
        usageLeft: Boolean,
        use24h: Boolean,
        resetClock: Boolean,
        /** True for the two-account layout: shorter window names, stricter no-reading. */
        duet: Boolean,
    ): Half {
        val label = cache.profileLabel(profile)
        val accent = Palette.color(Palette.accentName(cache, profile), dark)
        val snapshot = cache.snapshot(profile)
        val data = snapshot.data
        // CCRM-54 (ChatGPT Account) part 2: the headline is the 5-hour window when
        // there is one, else the 7-day. An account that has no session window at all
        // — a ChatGPT Plus or Pro account since OpenAI lifted the 5-hour limit — would
        // otherwise pin an em dash forever next to a perfectly healthy weekly figure.
        val headlineWeekly = data != null && data.session == null && data.weekly != null
        val window = if (headlineWeekly) data?.weekly else data?.session
        // CCRM-62 (Duet Notification): on a Duet half a broken sign-in reads as no
        // reading at all — the placeholder figure and an empty track with no tick — so
        // the 6 dp dot and the panel strip are what say why, and the *other* half stays
        // crisp. The single layout is untouched by this: with one account there is
        // nothing to contrast against, and it keeps showing what was last cached.
        val broken = duet && snapshot.authState == AuthState.REAUTH_NEEDED
        val pct = if (broken) null else window?.percent
        val elapsed =
            if (broken) null
            else elapsedPercent(
                window, if (headlineWeekly) Projection.WEEKLY_MS else Projection.SESSION_MS,
            )
        // "5h" and "Weekly" on every Duet surface, never "5-hour window" / "7-day":
        // two accounts doubled every label in the expanded view and the account prefix
        // had to come from somewhere. The single row keeps the long names — it has the
        // width for them, and nothing about it changed.
        val windowName = when {
            !duet -> if (headlineWeekly) "7-day window" else "5-hour window"
            else -> if (headlineWeekly) "Weekly" else "5h"
        }
        val resetsAt = window?.resetsAt
        return Half(
            profile = profile,
            label = label,
            accent = accent,
            data = data,
            headlineWeekly = headlineWeekly,
            pct = pct,
            pctText = if (pct == null) "—" else "${Fmt.usageInt(pct, usageLeft)}%",
            fill = Palette.barColor(pct, accent, dark),
            elapsed = elapsed,
            title = "$label · $windowName",
            // Collapsed, the single row is the headline window and nothing else:
            // percentage, bar, and when it resets. A Duet has no room for the reset
            // line at all, so this is its expanded header's sub.
            sub = when {
                duet && (broken || data == null) -> "No reading yet"
                resetsAt == null -> "Not started yet"
                resetClock -> "Resets at ${Fmt.timeOnly(resetsAt, use24h)} · " +
                    Fmt.relIn(resetsAt)
                else -> "Resets ${Fmt.relIn(resetsAt)} · " +
                    Fmt.timeOnly(resetsAt, use24h)
            },
            stale = Conditions.isStale(cache, profile),
            fault = Conditions.hasFault(cache, profile),
        )
    }

    // --- view filling -------------------------------------------------------------

    /**
     * Fills one of the big-number layouts. Text colours come from the layout's
     * theme attributes so they follow the notification shade; only the percentage
     * takes the usage colour, which is the whole point of the style.
     */
    private fun bigNumberView(
        context: Context,
        layout: Int,
        pctText: String,
        title: String,
        sub: CharSequence,
        pct: Double?,
        elapsed: Double?,
        fill: Color,
        theme: Color,
        dark: Boolean,
        showOverPace: Boolean,
        panel: Bitmap?,
        stale: Boolean,
        provider: Provider,
        /** CCRM-22: the small "LEFT" caption under the number in Left mode. */
        leftCaption: Boolean = false,
    ): RemoteViews = RemoteViews(context.packageName, layout).apply {
        setImageViewResource(R.id.provider_mark, providerMarkRes(provider))
        setInt(R.id.provider_mark, "setColorFilter", theme.toArgb())
        setTextViewText(R.id.pct, pctText)
        setTextColor(R.id.pct, fill.toArgb())
        setViewVisibility(
            R.id.pct_caption,
            if (leftCaption) android.view.View.VISIBLE else android.view.View.GONE,
        )
        setTextViewText(R.id.title, title)
        setTextViewText(R.id.sub, sub)
        setImageViewBitmap(
            R.id.bar, drawBarBitmap(context, pct, elapsed, theme, dark, showOverPace),
        )
        if (panel != null) setImageViewBitmap(R.id.panel, panel)
        // CCBG-12 (Status Icon Swap): a stale reading drawn as crisply as a live one is the
        // actual hazard — the old separate alert said "stale" somewhere else in the shade
        // while the number here still looked authoritative. Fading the number and its bar
        // puts the doubt on the figure itself. The strip below names the cause.
        if (stale) {
            setFloat(R.id.pct, "setAlpha", 0.5f)
            setFloat(R.id.bar, "setAlpha", 0.45f)
        }
    }

    /** The view ids one Duet half owns. Both Duet layouts use the same set. */
    private class HalfIds(
        val root: Int,
        val mark: Int,
        val label: Int,
        val dot: Int,
        val bar: Int,
        val pct: Int,
        val sub: Int,
        val caption: Int,
    )

    private val FIRST_IDS = HalfIds(
        root = R.id.duet_half_first, mark = R.id.duet_mark_first,
        label = R.id.duet_label_first, dot = R.id.duet_dot_first,
        bar = R.id.duet_bar_first, pct = R.id.duet_pct_first,
        sub = R.id.duet_sub_first, caption = R.id.duet_caption_first,
    )

    private val SECOND_IDS = HalfIds(
        root = R.id.duet_half_second, mark = R.id.duet_mark_second,
        label = R.id.duet_label_second, dot = R.id.duet_dot_second,
        bar = R.id.duet_bar_second, pct = R.id.duet_pct_second,
        sub = R.id.duet_sub_second, caption = R.id.duet_caption_second,
    )

    /**
     * Fills either Duet layout: [expanded] false is the collapsed row (label, bar,
     * figure), true the two header blocks (window name, reset sub, "LEFT" caption) plus
     * the panel bitmap. One function for both, so the two views cannot disagree about a
     * half's number, colour, dot or tap target.
     */
    private fun duetView(
        context: Context,
        layout: Int,
        first: Half,
        second: Half,
        dark: Boolean,
        usageLeft: Boolean,
        showOverPace: Boolean,
        /** App-global (CCRM-44), so it dots the First half only. */
        updateAvailable: Boolean,
        panel: Bitmap?,
        expanded: Boolean,
        firstTap: PendingIntent,
        secondTap: PendingIntent,
    ): RemoteViews = RemoteViews(context.packageName, layout).apply {
        // The dot is a pointer, not a message: red for this account's own fault, the
        // accent for the app-global update strip. Either way it means "there is a strip
        // for this in the expanded panel".
        fillHalf(
            context, FIRST_IDS, first, dark, usageLeft, showOverPace, expanded, firstTap,
            dotHue = when {
                first.fault -> conditionHue(error = true, theme = first.accent, dark = dark)
                updateAvailable -> conditionHue(error = false, theme = first.accent, dark = dark)
                else -> null
            },
        )
        fillHalf(
            context, SECOND_IDS, second, dark, usageLeft, showOverPace, expanded, secondTap,
            dotHue = if (second.fault) {
                conditionHue(error = true, theme = second.accent, dark = dark)
            } else {
                null
            },
        )
        if (panel != null) setImageViewBitmap(R.id.duet_panel, panel)
    }

    private fun RemoteViews.fillHalf(
        context: Context,
        ids: HalfIds,
        h: Half,
        dark: Boolean,
        usageLeft: Boolean,
        showOverPace: Boolean,
        expanded: Boolean,
        tap: PendingIntent,
        dotHue: Int?,
    ) {
        // Per-view PendingIntents: the one thing a custom RemoteViews layout can do that
        // the styles CCRM-61 (Settings Diet) removed could not, and the reason each half
        // can be its own tap target rather than sharing one content intent.
        setOnClickPendingIntent(ids.root, tap)
        setImageViewResource(ids.mark, providerMarkRes(h.profile.provider))
        setInt(ids.mark, "setColorFilter", h.accent.toArgb())
        setTextViewText(ids.label, if (expanded) h.title else h.label)
        if (!expanded) {
            // The collapsed clamp, which depends on how wide this half's own figure is.
            // The expanded header's bound is in the layout: it has the full card width.
            setInt(
                ids.label, "setMaxWidth",
                dp(context, Duet.labelClampDp(h.pctText).toFloat()).toInt(),
            )
        }
        setTextViewText(ids.pct, h.pctText)
        setTextColor(ids.pct, h.fill.toArgb())
        setImageViewBitmap(
            ids.bar,
            drawBarBitmap(
                context, h.pct, h.elapsed, h.accent, dark, showOverPace,
                widthDp = if (expanded) PANEL_WIDTH_DP else HALF_WIDTH_DP,
            ),
        )
        if (expanded) {
            setTextViewText(ids.sub, h.sub)
            setViewVisibility(
                ids.caption,
                if (usageLeft && h.pct != null) android.view.View.VISIBLE
                else android.view.View.GONE,
            )
        }
        setViewVisibility(
            ids.dot,
            if (dotHue == null) android.view.View.GONE else android.view.View.VISIBLE,
        )
        if (dotHue != null) setInt(ids.dot, "setColorFilter", dotHue)
        // Per-half staleness is new with the Duet: today the whole notification dims
        // because there is only one account on it. Here the doubt belongs to one figure.
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
        widthDp: Float = PANEL_WIDTH_DP,
    ): Bitmap = BarRenderer.draw(
        widthPx = dp(context, widthDp),
        heightPx = dp(context, 8f),
        percent = pct,
        elapsedPercent = elapsed,
        accent = theme,
        dark = dark,
        showOverPace = showOverPace,
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
        pct: Double?,
        left: Boolean,
        sessionElapsed: Double?,
        fillArgb: Int?,
        dark: Boolean,
        showOverPace: Boolean,
    ): IconCompat = IconCompat.createWithBitmap(
        UsageIcon.draw(context, pct, left, sessionElapsed, fillArgb, dark, showOverPace)
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
     * Greedy word wrap for the condition detail. Bounded at three lines because the strip's
     * height is what displaces a model-cap bar — an unbounded sentence could push every bar
     * out of the panel. The last line is ellipsised rather than dropped silently; the full
     * text is one tap away in the app.
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (maxWidth <= 0f) return listOf(text)
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth) {
                line = candidate
            } else {
                if (line.isNotEmpty()) lines.add(line)
                line = word
                if (lines.size == 3) break
            }
        }
        if (lines.size < 3 && line.isNotEmpty()) lines.add(line)
        return if (lines.size < 3) lines else lines.take(2) + listOf(
            android.text.TextUtils.ellipsize(
                lines[2], android.text.TextPaint(paint), maxWidth,
                android.text.TextUtils.TruncateAt.END,
            ).toString()
        )
    }

    /**
     * One bar row of the expanded panel.
     *
     * [accent] is per row, not per panel: a Duet's two Weekly rows belong to different
     * accounts and each draws in its own colour, which is what lets the panel be read
     * without a legend.
     */
    private data class PanelBar(
        val label: String,
        val window: UsageWindow?,
        val sub: String,
        val accent: Color,
    )

    /**
     * The weekly row for one account, or null when there is no data at all.
     *
     * [label] null is the single layout, which has one account and therefore no need to
     * say whose window this is; non-null prefixes it, because with two accounts in the
     * header a bare window name no longer says.
     */
    private fun weeklyRow(
        data: UsageData?,
        label: String?,
        accent: Color,
        resetClock: Boolean,
        use24h: Boolean,
    ): PanelBar? {
        if (data == null) return null
        return PanelBar(
            label = if (label == null) "7-day" else "$label · Weekly",
            window = data.weekly,
            // CCRM-23 (Reset Display): both forms, chosen first. The panel
            // has the room, and a 7-day clock needs its weekday to be honest.
            sub = data.weekly?.resetsAt?.let {
                if (resetClock) "Resets ${Fmt.dayTime(it, use24h)} · ${Fmt.relIn(it)}"
                else "Resets ${Fmt.relIn(it)} · ${Fmt.dayTime(it, use24h)}"
            } ?: "",
            accent = accent,
        )
    }

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
     * The expanded panel: the weekly windows and any per-model caps, each drawn the
     * same way the header draws the headline window — name on the left, bold
     * percentage on the right, full-width bar underneath, reset time below it.
     *
     * Each account's headline window is deliberately absent: it's the figure plus the
     * title in the header above, and repeating it here would be duplicate info. There's
     * no profile header either, for the same reason — the title already names it, and on
     * a Duet every row carries its own account prefix.
     *
     * [compact] is the Duet's row (CCRM-62 (Duet Notification)): label, figure and reset
     * on **one** line above the bar instead of three stacked pieces. Two header blocks
     * leave the panel only about 120 dp, and at the full row height that holds one row —
     * so two accounts' Weekly rows would not both fit, which is the whole reason the row
     * was recut. The full row is untouched for the single layout.
     */
    private fun drawPanel(
        context: Context,
        bars: List<PanelBar>,
        /** The condition strips' warning hue; each bar row carries its own accent. */
        theme: Color,
        dark: Boolean,
        /** CCRM-22 (Used or Left) — flips the row readouts; the bars draw the spend. */
        usageLeft: Boolean,
        showOverPace: Boolean,
        conditions: List<Conditions.Condition>,
        overflow: Int = 0,
        compact: Boolean = false,
    ): Bitmap? {
        val width = dp(context, PANEL_WIDTH_DP).toInt()
        val left = dp(context, 2f)
        val right = width - dp(context, 2f)
        val barThick = dp(context, if (compact) 13f else 10f)
        val labelH = dp(context, 19f)
        val subH = dp(context, 15f)
        val rowGap = dp(context, 14f)
        // The compact row's own three pieces. 16 dp is what a 13.5 dp line needs for its
        // descenders — "Weekly" has one, and it is in every Duet row label.
        val compactLineH = dp(context, 16f)
        val compactGap = dp(context, 8f)

        val onSurface = if (dark) AndroidColor.parseColor("#ECECEC") else AndroidColor.parseColor("#1F1F1F")
        val muted = if (dark) AndroidColor.parseColor("#9E9E9E") else AndroidColor.parseColor("#6B6B6B")

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurface; textSize = dp(context, 13.5f)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted; textSize = dp(context, 12f)
        }
        // Compact puts the reset text on the label's own line, so it takes the label's
        // size and keeps only the muted colour to stay subordinate to it.
        val compactSubPaint = Paint(subPaint).apply { textSize = dp(context, 13.5f) }

        // CCBG-12 (Status Icon Swap): condition strips borrow the bar rows' own two type
        // sizes rather than bringing a third — labelPaint for the title, subPaint for the
        // detail — so the panel reads as one thing. Separation comes from a tint, not a
        // rule: a rule made the strip look heavier than the bars it introduces.
        val condPadH = dp(context, 9f)
        val condPadV = dp(context, 7f)
        val condRadius = dp(context, 8f)
        val condDot = dp(context, 6f)
        val condTextLeft = left + condPadH + condDot + dp(context, 7f)
        val condTextWidth = right - condPadH - condTextLeft
        val condTitlePaint = Paint(labelPaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val wrapped = conditions.map { wrapText(it.detail, subPaint, condTextWidth) }
        val condHeights = wrapped.map { condPadV * 2f + labelH + it.size * subH }

        // Conditions displace model caps, never a weekly window — it is the only bar the
        // expanded panel exists to carry, so it holds its place however many strips
        // appear. On a Duet the two weekly rows take two of the four before any cap is
        // drawn, which is why a Duet panel with one strip shows them and nothing else.
        val barBudget = (4 - conditions.size).coerceAtLeast(1)
        val shown = bars.take(barBudget)
        if (shown.isEmpty() && conditions.isEmpty() && overflow == 0) return null

        // Every row grows by the tick's overhang above and below, so a mark at the
        // top or bottom edge of the bar can't collide with the label or be cut off.
        // The 2 dp side inset is already wider than half a tick (1.55 dp at this
        // thickness), so a tick at 0% or 100% stays inside the bitmap horizontally.
        val over = BarGeometry.tickOverhang(barThick)

        var height = condHeights.sum() + condHeights.size * dp(context, 12f)
        // CCRM-44 (One Surface): the "+ n more" line when strips overflowed the cap.
        if (overflow > 0) height += subH + dp(context, 12f)
        for (b in shown) {
            if (compact) {
                height += compactLineH + over + barThick + over + compactGap
            } else {
                height += labelH + dp(context, 7f) + 2f * over + barThick
                if (b.sub.isNotEmpty()) height += subH
                height += rowGap
            }
        }

        val bmp = Bitmap.createBitmap(width, height.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Bold and larger, mirroring the collapsed row's headline percentage. Compact
        // drops to the label's size, because it shares the label's line.
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurface
            textSize = dp(context, if (compact) 13.5f else 16f)
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var y = 0f
        conditions.forEachIndexed { index, condition ->
            val hue = conditionHue(condition, theme, dark)
            val stripHeight = condHeights[index]
            val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (hue and 0x00FFFFFF) or (0x21 shl 24) // 13% alpha
            }
            c.drawRoundRect(
                RectF(left, y, right, y + stripHeight), condRadius, condRadius, tint,
            )
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hue }
            c.drawCircle(
                left + condPadH + condDot / 2f,
                y + condPadV + labelH / 2f,
                condDot / 2f,
                dotPaint,
            )
            c.drawText(
                condition.title, condTextLeft, y + condPadV + labelH - dp(context, 5f),
                condTitlePaint,
            )
            var lineY = y + condPadV + labelH
            for (line in wrapped[index]) {
                c.drawText(line, condTextLeft, lineY + subH - dp(context, 4f), subPaint)
                lineY += subH
            }
            y += stripHeight + dp(context, 12f)
        }

        if (overflow > 0) {
            c.drawText(
                "+ $overflow more — open the app for the rest",
                left + condPadH, y + subH - dp(context, 4f), subPaint,
            )
            y += subH + dp(context, 12f)
        }

        for (bar in shown) {
            val figure = bar.window?.percent?.let { "${Fmt.usageInt(it, usageLeft)}%" } ?: "—"
            if (compact) {
                val baseline = y + compactLineH - dp(context, 4f)
                c.drawText(bar.label, left, baseline, labelPaint)
                c.drawText(figure, right, baseline, valuePaint)
                // The reset text sits right after the label and gives way first: it is
                // the piece the header above already implies, and the only one on this
                // line that can be shortened without losing a number.
                val subX = left + labelPaint.measureText(bar.label) + dp(context, 6f)
                val subRoom = right - valuePaint.measureText(figure) - dp(context, 8f) - subX
                if (bar.sub.isNotEmpty() && subRoom > dp(context, 24f)) {
                    c.drawText(
                        android.text.TextUtils.ellipsize(
                            bar.sub, android.text.TextPaint(compactSubPaint), subRoom,
                            android.text.TextUtils.TruncateAt.END,
                        ).toString(),
                        subX, baseline, compactSubPaint,
                    )
                }
                y += compactLineH + over
            } else {
                val baseline = y + labelH - dp(context, 4f)
                c.drawText(bar.label, left, baseline, labelPaint)
                c.drawText(figure, right, baseline, valuePaint)
                y += labelH + dp(context, 7f) + over
            }

            val pct = bar.window?.percent
            // Every row in this panel is a 7-day surface — the weekly windows and the
            // per-model caps alike — so they all measure their pace against 7 days.
            val elapsed = elapsedPercent(bar.window, Projection.WEEKLY_MS)
            val fill = Palette.barColor(pct, bar.accent, dark)
            val radius = barThick / 2f
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill.copy(alpha = 0.25f).toArgb() }
            c.drawRoundRect(RectF(left, y, right, y + barThick), radius, radius, track)

            var fillEnd: Float? = null
            if (pct != null) {
                val end = left + (right - left) * BarGeometry.fillFraction(pct)
                if (end > left) {
                    fillEnd = end.coerceAtLeast(left + barThick).coerceAtMost(right)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill.toArgb() }
                    c.drawRoundRect(RectF(left, y, fillEnd, y + barThick), radius, radius, paint)
                }
            }

            // The red rides inside a clip of the fill's own rounded rect: straight
            // vertical boundary where the colours meet, and the red covers the fill's
            // rounded tip rather than stopping short of it.
            val segment = BarGeometry.redSegment(pct, elapsed, showOverPace)
            if (segment != null && fillEnd != null) {
                val segLeft = left + (right - left) * segment.first
                if (fillEnd > segLeft) {
                    val clip = Path().apply {
                        addRoundRect(
                            RectF(left, y, fillEnd, y + barThick), radius, radius, Path.Direction.CW,
                        )
                    }
                    c.save()
                    c.clipPath(clip)
                    c.drawRect(
                        RectF(segLeft, y, fillEnd, y + barThick),
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Palette.barColor(100.0, bar.accent, dark).toArgb()
                        },
                    )
                    c.restore()
                }
            }

            if (BarGeometry.showTick(pct, elapsed)) {
                val tickW = BarGeometry.tickWidth(barThick)
                val cx = left + (right - left) * BarGeometry.tickFraction(elapsed!!)
                val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (if (dark) Color(0xFFF2F2F4) else Color(0xFF1D1D1F))
                        .copy(alpha = if (dark) 0.60f else 0.48f).toArgb()
                }
                c.drawRoundRect(
                    RectF(cx - tickW / 2f, y - over, cx + tickW / 2f, y + barThick + over),
                    tickW / 2f, tickW / 2f, tickPaint,
                )
            }
            y += barThick + over

            if (compact) {
                y += compactGap
            } else {
                if (bar.sub.isNotEmpty()) {
                    c.drawText(bar.sub, left, y + subH - dp(context, 3f), subPaint)
                    y += subH
                }
                y += rowGap
            }
        }
        return bmp
    }
}
