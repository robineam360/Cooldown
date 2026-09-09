package com.robin.claudeusage.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.robin.claudeusage.MainActivity
import com.robin.claudeusage.R
import com.robin.claudeusage.data.Profile
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.SessionLog
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.diag.AppLog
import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.ui.Fmt
import java.time.Instant

/**
 * The reset ping — the one standalone notification left after CCRM-61 (Settings Diet).
 *
 * "Your window reset" is a *moment*: it happens, it is read, it stops being interesting,
 * and nothing else on the phone would say it. Every other alert this file used to post —
 * thresholds, pace, re-auth, expiry, stale data, update available — was either a **state**
 * better drawn as a live strip in the pinned notification's panel (`notify/Conditions.kt`)
 * or dropped outright.
 *
 * Pings are per account and per window ("5h" / "Weekly"), each with its own Off / If busy /
 * Always mode ([UsageCache.resetPingMode]), and they post even while the always-on
 * notification is on — decided 2026-09-09: the point of a ping is to reach someone who is
 * *not* looking at the shade.
 *
 * [evaluate] also carries the window bookkeeping in [checkReset] (whose `SessionLog` write
 * is the History screen's data source) and the pinned notification's per-poll re-render.
 */
object Alerts {

    private const val CHANNEL_RESET = "reset_alerts"

    /** Channels whose posters CCRM-61 (Settings Diet) removed — see [retireOldChannels]. */
    private val RETIRED_CHANNELS = listOf(
        "usage_alerts", "auth_alerts", "health_alerts",
        "ping_alerts", "pace_alerts", "update_alerts",
    )

    /** Unchanged from the pre-CCRM-61 ids, so nothing churns in the shade on upgrade. */
    private const val RESET_SESSION_KIND = 4
    private const val RESET_WEEKLY_KIND = 5

    /**
     * The widest kind in use. Bounds the per-slot ID stride and the range [cancelAllFor]
     * sweeps, so both follow the kinds automatically. Dropping it from 31 to 5 strands no
     * orphan: [retireOldChannels] deletes the retired kinds' channels, and Android
     * cancels a deleted channel's notifications with it.
     */
    const val MAX_KIND = RESET_WEEKLY_KIND

    /**
     * Public because the stale strip in `notify/Conditions.kt` uses the same threshold —
     * one constant, so nothing that means "stale" can disagree about what it is.
     */
    const val STALE_DATA_MS = 6 * 60 * 60_000L

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESET, "Window resets", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "A usage window has reset — fresh again" }
        )
    }

    /**
     * Deletes the six channels CCRM-61 (Settings Diet) left without a poster, once per
     * install — an upgraded install would otherwise keep offering to configure alerts that
     * no longer exist. Never touches `reset_alerts` or the always-on `pinned_usage_v2`:
     * deleting a live channel takes the user's own importance and sound choices with it.
     */
    fun retireOldChannels(context: Context, cache: UsageCache) {
        if (cache.oldChannelsRetired()) return
        val nm = context.getSystemService(NotificationManager::class.java)
        for (channel in RETIRED_CHANNELS) nm.deleteNotificationChannel(channel)
        cache.setOldChannelsRetired()
    }

    /** Called after every poll; works off the latest cached data and dedupes itself. */
    fun evaluate(context: Context, cache: UsageCache) {
        retireOldChannels(context, cache)
        ensureChannels(context)
        for (profile in cache.registry().all()) {
            val data = cache.snapshot(profile).data ?: continue
            checkReset(context, cache, profile, "Session", "5h", data.session, Projection.SESSION_MS)
            checkReset(context, cache, profile, "Weekly", "Weekly", data.weekly, Projection.WEEKLY_MS)
        }
        // The always-on notification rides the same cadence so it stays live. This is its
        // only per-poll re-render, and it must stay last: it draws from the state above.
        com.robin.claudeusage.notify.PinnedNotification.update(context, cache)
    }

    /**
     * Reset detection for one account's window. Runs whatever the ping mode is — it also
     * tracks window identity and peak, and records the closed window in [SessionLog].
     */
    private fun checkReset(
        context: Context,
        cache: UsageCache,
        profile: Profile,
        windowName: String,
        windowLabel: String,
        window: UsageWindow?,
        windowLengthMs: Long,
    ) {
        val key = window?.resetsAt?.toEpochMilli() ?: return
        val pct = window.percent ?: 0.0
        val lastSeen = cache.lastSeenWindowKey(profile, windowName)
        // Proximity, not equality (CCBG-4 (Alert Dedup)). Exact comparison also made this
        // fire spuriously when a poll landed within ~1s of the boundary and drift pushed
        // lastSeen just into the past.
        if (lastSeen != 0L && !Projection.sameWindow(lastSeen, key, windowLengthMs) &&
            Instant.ofEpochMilli(lastSeen).isBefore(Instant.now())
        ) {
            // The window rolled over. Log the window that just closed to the
            // long-term session log (its identity is lastSeen, its peak is what
            // we accumulated while it was open) for the history bars.
            val peak = cache.windowPeak(profile, windowName)
            SessionLog(context).record(
                profile,
                if (windowName == "Session") SessionLog.SESSION else SessionLog.WEEKLY,
                lastSeen, peak, peak >= 99.5,
            )
            // Smart mode only pings when the finished window had actually been
            // running hot — a reset nobody was waiting for is just noise.
            val mode = cache.resetPingMode(profile, windowName)
            val wanted = mode == UsageCache.RESET_ALWAYS ||
                (mode == UsageCache.RESET_SMART && peak >= UsageCache.SMART_RESET_MIN_PCT)
            if (wanted) {
                notify(
                    context, cache, profile,
                    notifId(
                        profile,
                        if (windowName == "Session") RESET_SESSION_KIND else RESET_WEEKLY_KIND,
                    ),
                    // Tight-surface wording: "5h" / "Weekly", never "5-hour window".
                    "$windowLabel reset",
                    "Usage is back at ${pct.toInt()}%. Next reset ${Fmt.relIn(window.resetsAt)}.",
                    timeoutMs = resetTimeout(window.resetsAt),
                )
            }
            cache.setWindowPeak(profile, windowName, pct)
        } else {
            cache.setWindowPeak(profile, windowName, maxOf(cache.windowPeak(profile, windowName), pct))
        }
        cache.setLastSeenWindowKey(profile, windowName, key)
    }

    /**
     * A ping lives until the fresh window resets in turn — after which "your window reset"
     * is about a window two generations old. This was the "auto" arm of the retired "keep
     * alerts for" setting; with the preference gone it is simply the behaviour. An hour is
     * the floor, since expiring immediately would eat the ping before it could be read.
     */
    private fun resetTimeout(windowResetsAt: Instant?): Long {
        val left = windowResetsAt?.toEpochMilli()?.minus(System.currentTimeMillis())
        return if (left == null || left <= 0) 60 * 60_000L else left
    }

    /**
     * CCRM-6 (Multi-Account): the ID scheme is `kind + slot * 100`. Kinds top out at
     * [MAX_KIND], so the ×100 stride can't collide however many accounts exist — and
     * slots 0/1 reproduce the pre-CCRM-6 IDs exactly (`kind` and `kind + 100`), so nothing
     * churns on upgrade. It replaces a `+100 only for WORK` test under which every third
     * account silently overwrote Personal's notifications.
     */
    fun notifId(profile: Profile, kind: Int): Int = kind + profile.slot * 100

    /**
     * Dismisses every notification this profile could have posted — its whole ID range.
     * Used by account removal (CCRM-6 phase 4), which has to happen while the slot is still
     * known or an orphan sits in the shade forever.
     */
    fun cancelAllFor(context: Context, profile: Profile) {
        val nm = NotificationManagerCompat.from(context)
        for (kind in 1..MAX_KIND) nm.cancel(notifId(profile, kind))
    }

    private fun notify(
        context: Context,
        cache: UsageCache,
        profile: Profile,
        id: Int,
        title: String,
        text: String,
        timeoutMs: Long,
    ) {
        // Request code = notification id (already unique per profile+kind), so
        // a Work alert's intent isn't recycled with the Personal extra.
        val openApp = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).putExtra("profile", profile.key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Prefixed here, where nothing else names the account.
        val prefixed = "${cache.profileLabel(profile)}: $title"
        val notification = NotificationCompat.Builder(context, CHANNEL_RESET)
            .setSmallIcon(R.drawable.ic_stat_bars)
            .setContentTitle(prefixed)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setTimeoutAfter(timeoutMs)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            // CCRM-34 (Diagnostics Log): titles carry no secrets — percentages and
            // window names only.
            AppLog.log(
                context, AppLog.Level.INFO, "alerts", profile,
                "posted [$CHANNEL_RESET] $prefixed",
            )
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip.
            AppLog.log(
                context, AppLog.Level.WARN, "alerts", profile,
                "post blocked — notifications permission missing",
            )
        }
    }
}
