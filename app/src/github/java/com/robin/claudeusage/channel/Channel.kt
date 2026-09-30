package com.robin.claudeusage.channel

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robin.claudeusage.SectionLabel
import com.robin.claudeusage.data.UpdateCheck
import com.robin.claudeusage.data.UpdateGate
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.notify.Conditions
import com.robin.claudeusage.notify.UpdateNotification

/**
 * CCRM-87 (Update Channel), the GitHub build: the update path every build had up to
 * v1.8 — the poll-riding release check (CCRM-28), the pinned panel's update strip
 * (CCRM-44 (One Surface)), the Settings Updates card, and the notice's action.
 */
object Channel : UpdateChannel {

    override fun autoCheck(context: Context, cache: UsageCache) {
        UpdateNotification.autoCheck(context, cache)
    }

    /**
     * App-global, so it shows whichever profile the panel carries. Persisting while the
     * installed version lags is what resolved CCBG-12 (Status Icon Swap)'s timeout
     * tension: the standalone notice it replaced posted once per version, ever, so it
     * could never be given an expiry — a strip that is simply present while the version
     * is behind needs no such ceremony. It is the only update surface in the shade now
     * (CCRM-61 (Settings Diet)). Respects "skip this version".
     */
    override fun updateCondition(context: Context, cache: UsageCache): Conditions.Condition? {
        val latest = cache.latestKnownVersion() ?: return null
        val installed = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: return null
        } catch (_: Exception) {
            return null
        }
        val normalized = UpdateCheck.normalize(latest)
        if (UpdateCheck.compare(normalized, UpdateCheck.normalize(installed)) <= 0) return null
        if (UpdateGate.isSkipped(latest, cache.dismissedUpdateVersion())) return null
        return Conditions.Condition(
            short = "Update available — v$normalized",
            title = "Update available — v$normalized",
            detail = "You have v$installed. Tap to open the app; nothing installs by itself.",
            error = false,
        )
    }

    @Composable
    override fun SettingsSection(cache: UsageCache) {
        Spacer(Modifier.height(24.dp))
        SectionLabel("Updates")
        UpdatesCard(cache)
    }

    @Composable
    override fun InvalidResponseAction(onOpenSettings: () -> Unit) {
        TextButton(onClick = onOpenSettings) { Text("Check for updates") }
    }
}
