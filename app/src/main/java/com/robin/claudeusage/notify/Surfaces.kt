package com.robin.claudeusage.notify

import android.app.AlarmManager
import android.content.Context
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.widgets.FaceWidgetProvider
import com.robin.claudeusage.widgets.Transitions
import com.robin.claudeusage.widgets.WidgetActionReceiver
import com.robin.claudeusage.widgets.WidgetHost

/**
 * The one refresh seam (CCRM-78 (Widgets Reborn), rule R7): everything the phone draws
 * from the cache outside the app — the always-on notification and the home-screen
 * widgets — is redrawn together through [refresh]. Every poll (`Alerts.evaluate`) and
 * every Settings change that affects a surface ends here, so the widgets need no Settings
 * rows and no redraw worker of their own: that is the answer to the third of CCRM-61
 * (Settings Diet)'s reasons for removing the old ones.
 */
object Surfaces {

    /** Redraws the notification and every placed widget from [cache], then re-arms. */
    fun refresh(context: Context, cache: UsageCache) {
        PinnedNotification.update(context, cache)
        // Off the caller's thread: most Settings changes land here from a click handler,
        // and every placed widget's whole size map is drawn. One thread, so two refreshes
        // never interleave their updates.
        val app = context.applicationContext
        widgetThread.execute {
            FaceWidgetProvider.contained(app) { WidgetHost.updateAll(app, UsageCache(app)) }
            arm(app)
        }
    }

    private val widgetThread = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "cooldown-widgets").apply { isDaemon = true }
    }

    /**
     * Sets the one app-wide transition alarm at `Transitions.nextTransitionAt` over
     * **every** placed widget of all four faces, or cancels it when that is null (R7).
     * `setAndAllowWhileIdle` on RTC, not RTC_WAKEUP: a widget is only looked at with the
     * screen on, and a non-wakeup alarm fires on wake. No exact-alarm permission, and
     * nothing is claimed about delivery — R4 makes a late redraw merely late.
     */
    fun arm(context: Context) = FaceWidgetProvider.contained(context) {
        val at = Transitions.nextTransitionAt(
            System.currentTimeMillis(), WidgetHost.placed(context), WidgetHost.snapshots(UsageCache(context)),
        )
        val am = context.getSystemService(AlarmManager::class.java) ?: return@contained
        val pi = WidgetActionReceiver.alarmIntent(context)
        if (at == null) am.cancel(pi) else am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
    }
}
