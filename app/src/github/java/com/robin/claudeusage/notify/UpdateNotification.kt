package com.robin.claudeusage.notify

import android.content.Context
import com.robin.claudeusage.data.UpdateCheck
import com.robin.claudeusage.data.UpdateGate
import com.robin.claudeusage.data.UsageCache

/**
 * The automatic update check (CCRM-28): fetches GitHub's latest release when the
 * poll-riding gate says one is due and records the outcome. All decisions are in
 * [UpdateGate] (pure, tested); this is the I/O. Never downloads or installs anything.
 *
 * It posts nothing of its own since CCRM-61 (Settings Diet) retired the once-per-version
 * notification. Two surfaces read what it writes: the update strip in the pinned
 * notification's panel (`Conditions.update`, off `latestKnownVersion`), which persists
 * for as long as the installed version lags rather than firing once, and the Updates
 * card in Settings.
 */
object UpdateNotification {

    /**
     * Tail-runs on every poll (already on a worker thread). A failed fetch records
     * the failure for the settings card and nothing else — no notification, and
     * lastUpdateCheckAt stays put so the next poll retries.
     */
    fun autoCheck(context: Context, cache: UsageCache) {
        val now = System.currentTimeMillis()
        if (!UpdateGate.shouldCheckNow(cache.autoCheckUpdates(), now, cache.lastUpdateCheckAt())) return
        val info = try {
            UpdateCheck.fetchLatest(installedVersion(context))
        } catch (_: Exception) {
            cache.recordUpdateCheckFailure(now, "couldn't reach GitHub")
            return
        }
        cache.recordUpdateCheckSuccess(
            System.currentTimeMillis(),
            UpdateGate.successOutcome(info.latestVersion, info.updateAvailable),
            info.latestVersion,
        )
    }

    fun installedVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }
}
