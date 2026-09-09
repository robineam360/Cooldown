package com.robin.claudeusage.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.work.Polling

/**
 * The pinned notification's one broadcast: [PinnedNotification.ACTION_REFRESH], the
 * user's "Refresh" action — it fetches, on the pinned account.
 *
 * It had a second, CCBG-18 (Strip Lifetime Stamp)'s expiry alarm, which redrew the panel
 * when a persisted strip's lifetime was up. CCRM-61 (Settings Diet) removed the persisted
 * strips: every strip the panel draws is now derived at draw time, so there is nothing
 * left to retire on a timer.
 */
class PinnedRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PinnedNotification.ACTION_REFRESH) {
            val profile = UsageCache(context).pinnedProfile()
            Polling.refreshOnce(context, manual = true, profile = profile)
        }
    }
}
