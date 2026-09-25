package com.robin.claudeusage.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.notify.Surfaces

/**
 * The widgets' private receiver (CCRM-78 (Widgets Reborn) §On-face controls, R7),
 * `exported="false"`: Number 4×2's `[5h | Weekly]` chips and account cycler, and the one
 * app-wide transition alarm. The on-face intents come here rather than to the provider
 * because the provider is exported for the launcher, and a custom action on an exported
 * receiver could be sent by any app. It needs no activity, so the controls work with the
 * app swiped away.
 *
 * Each on-face PendingIntent's identity is its **data**, `cooldown-widget://<id>/<action>`
 * (extras never distinguish PendingIntents), and the widget id is read back from that data,
 * so a tap on one widget can never move another.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    enum class Action(val path: String, val code: Int) {
        WINDOW_5H("window-5h", 1),
        WINDOW_WEEKLY("window-weekly", 2),
        CYCLE("cycle", 3),
    }

    override fun onReceive(context: Context, intent: Intent) = FaceWidgetProvider.contained(context) {
        val data = intent.data ?: return@contained
        if (data.scheme != WidgetHost.SCHEME) return@contained
        if (data.host == ALARM_HOST) {
            // R7: redraw every placed widget from the cache and re-arm. Fetches nothing:
            // Polling.scheduleResetChecks already queues a poll after every reset.
            WidgetHost.updateAll(context, UsageCache(context))
            Surfaces.arm(context)
            return@contained
        }
        val id = data.host?.toIntOrNull() ?: return@contained
        val action = Action.entries.firstOrNull { it.path == data.lastPathSegment } ?: return@contained
        val face = WidgetHost.faceOf(context, id) ?: return@contained
        val cache = UsageCache(context)
        apply(WidgetPrefs(context), cache.registry().all().map { it.key }, id, action)
        WidgetHost.update(context, cache, AppWidgetManager.getInstance(context), id, face, FaceWidgetProvider.UNAVAILABLE.getValue(face))
        Surfaces.arm(context)
    }

    companion object {
        const val ALARM_HOST = "alarm"
        private const val ALARM_REQUEST = 7_000_001

        /** The pref write for one tap. Pure over [WidgetPrefs], so the tests drive it. */
        fun apply(prefs: WidgetPrefs, registryKeys: List<String>, id: Int, action: Action) {
            when (action) {
                Action.WINDOW_5H -> prefs.setWindow(id, FaceWindow.SESSION)
                Action.WINDOW_WEEKLY -> prefs.setWindow(id, FaceWindow.WEEKLY)
                Action.CYCLE -> nextAccount(registryKeys, prefs.read(id).accountKey)?.let { prefs.setAccount(id, it) }
            }
        }

        /**
         * The cycler's step (Robin, Q5): the next account in registry order after the one
         * shown — the first when unassigned (R5, that is the one drawn) — wrapping. Stores
         * the *key*, so reordering never repoints it. Null with no accounts.
         */
        fun nextAccount(registryKeys: List<String>, current: String?): String? {
            if (registryKeys.isEmpty()) return null
            val shown = registryKeys.indexOf(current).takeIf { it >= 0 } ?: 0
            return registryKeys[(shown + 1) % registryKeys.size]
        }

        fun intent(context: Context, id: Int, action: Action): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                WidgetHost.requestCode(id, action.code),
                Intent(context, WidgetActionReceiver::class.java).setData(WidgetHost.uri(id, action.path)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** The one transition alarm's PendingIntent (R7). */
        fun alarmIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST,
                Intent(context, WidgetActionReceiver::class.java)
                    .setData(Uri.parse("${WidgetHost.SCHEME}://$ALARM_HOST/transition")),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
