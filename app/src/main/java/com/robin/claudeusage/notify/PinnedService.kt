package com.robin.claudeusage.notify

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.data.UsageRepository
import com.robin.claudeusage.diag.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * CCRM-67 (Pin Service): the foreground service the always-on notification now
 * lives inside, so `setOngoing(true)` means something again. Two things it buys
 * that a plain [android.app.NotificationManager] post never could at our
 * targetSdk:
 *
 * 1. The shade ranks a foreground-service notification in its top section, not
 *    the silent pile IMPORTANCE_LOW would otherwise earn it, and the row stops
 *    being swipe-dismissible — Android only honours "ongoing" from a real FGS.
 * 2. It owns its own poll cadence while it's alive (see [pollLoop]), so the
 *    pinned figure keeps refreshing on OEM skins that defer or kill WorkManager
 *    under Doze. [com.robin.claudeusage.work.Polling]'s periodic chain is left
 *    running regardless — it's the backstop for alerts and reset pings when the
 *    pin is off, and for this service if it ever gets killed anyway.
 *
 * The FGS type is `specialUse` (declared in the manifest, with the required
 * `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`): none of the typed categories (location,
 * media playback, health, …) describe "a usage-window readout the user asked to
 * keep pinned", and `specialUse` exists precisely for a case like this one.
 *
 * Lifetime is driven entirely by [PinnedNotification.update]: [ensureRunning]
 * starts this service whenever the pin is on and it isn't already, [stop] tears
 * it down the moment the pin is switched off. There is deliberately no bound
 * client — nothing needs to call into a running instance, only to know it exists,
 * which [running] answers without a binder round-trip.
 */
class PinnedService : Service() {

    private lateinit var scope: CoroutineScope
    private var pollJob: Job? = null

    companion object {
        /**
         * True while an instance is alive. A plain flag rather than
         * `ActivityManager.getRunningServices` (deprecated, and unreliable for an
         * app's own service since Android O) — this process is the only place
         * that can answer "is my own service up" anyway.
         */
        @Volatile private var running = false

        /** Starts the service if it isn't already running. Safe to call on every poll tick. */
        fun ensureRunning(context: Context) {
            if (running) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, PinnedService::class.java))
            } catch (e: Exception) {
                // Falling back to a plain notification (still posted by the caller,
                // PinnedNotification.update) beats crashing the process that asked
                // for the pin — see the same catch in onStartCommand for why this
                // can legitimately happen even though we're the foreground app.
                AppLog.log(
                    context, AppLog.Level.INFO, "pin-service", null,
                    "start refused (${e.javaClass.simpleName}) — falling back to a plain notification",
                )
            }
        }

        /** Stops the service, if running. A no-op — not an error — when it isn't. */
        fun stop(context: Context) {
            context.stopService(Intent(context, PinnedService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Every start — the first one, a reboot restart, a redundant [ensureRunning]
     * call — must call `startForeground` before returning, promptly enough to beat
     * the ANR window, or the platform throws. That holds even on the path where
     * the pin turns out to already be off by the time this runs (a real race: the
     * user can flip the toggle between [ensureRunning] posting the start intent
     * and this executing) — there is no notification to show, so we start with a
     * throwaway one and stop immediately, which satisfies the contract without
     * lying to the user for longer than a frame.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cache = UsageCache(applicationContext)
        val notification = PinnedNotification.buildNotification(applicationContext, cache)

        if (notification == null) {
            startForegroundCompat(offNotification())
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForegroundCompat(notification)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (API 31+) or a SecurityException
            // from a missing/withdrawn permission: the platform refused to let this
            // start as a foreground service. PinnedNotification.update already posts
            // the plain notification independently of this service, so the pin still
            // shows — just without the FGS shade ranking and poll loop this class
            // exists to add. Crashing the app over a degraded, not missing, feature
            // would be the wrong trade.
            AppLog.log(
                applicationContext, AppLog.Level.INFO, "pin-service", null,
                "startForeground refused (${e.javaClass.simpleName}) — plain notification only",
            )
            stopSelf()
            return START_NOT_STICKY
        }

        startPollLoop(cache)
        return START_STICKY
    }

    /**
     * `specialUse` is an API 34 type, and this app's minSdk is 31. On 31–33 the
     * manifest's `android:foregroundServiceType="specialUse"` is a value the
     * platform doesn't know, so it parses to a declared-type mask of *zero* — and
     * `startForeground` throws `IllegalArgumentException` when handed a type the
     * manifest didn't declare. Passing 0 there is not a downgrade: those releases
     * predate typed-FGS enforcement entirely, so an untyped foreground service is
     * exactly what they expect, and the shade ranking and process lifetime this
     * class exists for are unaffected.
     */
    private fun startForegroundCompat(notification: Notification) {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        ServiceCompat.startForeground(this, PinnedNotification.NOTIF_ID, notification, type)
    }

    /**
     * A bare, silent placeholder for the one path that has no real notification to
     * show yet — the pin-just-turned-off race in [onStartCommand]. Never reaches
     * the shade for longer than it takes `stopSelf` to run; its content doesn't
     * matter, only that `startForeground` got *something* Notification-shaped.
     */
    private fun offNotification(): Notification {
        PinnedNotification.ensureChannel(applicationContext)
        return NotificationCompat.Builder(applicationContext, PinnedNotification.CHANNEL)
            .setSmallIcon(com.robin.claudeusage.R.drawable.ic_stat_bars)
            .setContentTitle("Cooldown")
            .setOngoing(false)
            .build()
    }

    /**
     * Owns the poll cadence while the pin is up, at whatever interval the user has
     * chosen ([UsageCache.pollIntervalMinutes]) — re-read every lap, so a change in
     * Settings takes effect on the next tick rather than needing a restart. The
     * fetch itself goes through [UsageRepository.refreshAll], the same path
     * WorkManager's [com.robin.claudeusage.work.UsagePollWorker] uses, and its
     * result reaches this notification exactly the way it always has: through
     * [com.robin.claudeusage.alerts.Alerts.evaluate], which every fetch path ends
     * in and which re-renders the pin last. This loop's only job is deciding
     * *when* to fetch, never how to draw — one less place that can disagree with
     * [PinnedNotification] about what the notification should say.
     *
     * A stale `pinnedEnabled` read stops the loop and the service rather than
     * pushing on: belt-and-braces alongside [PinnedNotification.update]'s own
     * [stop] call, for the case where the setting changed without going through it.
     */
    private fun startPollLoop(cache: UsageCache) {
        if (pollJob?.isActive == true) return
        val repo = UsageRepository(applicationContext)
        pollJob = scope.launch {
            while (isActive) {
                val intervalMin = cache.pollIntervalMinutes().coerceAtLeast(1L)
                delay(intervalMin * 60_000L)
                if (!isActive) break
                if (!cache.pinnedEnabled()) {
                    stopSelf()
                    break
                }
                repo.refreshAll(manual = false)
            }
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        running = false
        super.onDestroy()
    }
}
