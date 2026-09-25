package com.robin.claudeusage.notify

import android.content.Context
import com.robin.claudeusage.data.UsageCache

/**
 * The one refresh seam (CCRM-78 (Widgets Reborn), rule R7): everything the phone draws
 * from the cache outside the app — the always-on notification and, from v1.8, the
 * home-screen widgets — is redrawn together through [refresh]. Every poll
 * (`Alerts.evaluate`) and every Settings change that affects a surface ends here, so the
 * widgets need no Settings rows and no redraw worker of their own: that is the answer to
 * the third of CCRM-61 (Settings Diet)'s reasons for removing the old ones.
 *
 * Until RUNBOOK.md Step 4 builds the providers, the widget half and [arm] are no-ops, so
 * [refresh] behaves exactly like the `PinnedNotification.update` call it replaced.
 */
object Surfaces {

    /** Redraws the notification and every placed widget from [cache], then re-arms. */
    fun refresh(context: Context, cache: UsageCache) {
        PinnedNotification.update(context, cache)
        redrawWidgets(context, cache)
        arm(context)
    }

    /**
     * Sets the one app-wide transition alarm at `widgets/Transitions.nextTransitionAt`
     * over every placed widget, or cancels it when that is null (R7). A no-op until
     * Step 4: nothing is placed.
     */
    @Suppress("UNUSED_PARAMETER")
    fun arm(context: Context) = Unit

    /** Every placed widget, from the cache. A no-op until Step 4. */
    @Suppress("UNUSED_PARAMETER")
    private fun redrawWidgets(context: Context, cache: UsageCache) = Unit
}
