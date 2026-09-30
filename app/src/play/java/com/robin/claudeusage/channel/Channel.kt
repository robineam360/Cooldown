package com.robin.claudeusage.channel

import android.content.Context
import androidx.compose.runtime.Composable
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.notify.Conditions

/**
 * CCRM-87 (Update Channel), the Play build: no update path of its own. Play delivers
 * updates, so there is no check, no Settings section and no notice action.
 *
 * It does clean up after the github build. An install that moved from the GitHub APK to
 * Play (same package, same key) inherits the update prefs, including a
 * `latestKnownVersion` that would otherwise mean nothing here; the first poll drops them.
 * The strip never shows either way, because [updateCondition] is always null.
 */
object Channel : UpdateChannel {

    override fun autoCheck(context: Context, cache: UsageCache) {
        cache.clearUpdateState()
    }

    override fun updateCondition(context: Context, cache: UsageCache): Conditions.Condition? = null

    @Composable
    override fun SettingsSection(cache: UsageCache) {}

    @Composable
    override fun InvalidResponseAction(onOpenSettings: () -> Unit) {}
}
