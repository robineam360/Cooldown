package com.robin.claudeusage.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.notify.Surfaces

/**
 * R7's system triggers for the widgets: a boot, an app update, a clock change and a zone
 * change each redraw every placed widget and re-arm the one transition alarm. A clock
 * change moves every chronometer base and a zone change every absolute time, so a re-arm
 * alone would leave the faces wrong.
 *
 * Exported because all four must reach a manifest receiver: `BOOT_COMPLETED` is a
 * protected broadcast, `MY_PACKAGE_REPLACED` is addressed to this package, and the last two
 * are on the implicit-broadcast exemption list — no other app can send any of them.
 * Deliberately **not** behind `PinnedBootReceiver`'s `pinnedEnabled()` early return: the
 * widgets do not need the pin. Not `LOCKED_BOOT_COMPLETED` either — the prefs and cache are
 * credential-encrypted.
 */
class WidgetSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = FaceWidgetProvider.contained(context) {
        if (intent.action !in ACTIONS) return@contained
        WidgetHost.updateAll(context, UsageCache(context))
        Surfaces.arm(context)
    }

    companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, // "android.intent.action.TIME_SET"
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
