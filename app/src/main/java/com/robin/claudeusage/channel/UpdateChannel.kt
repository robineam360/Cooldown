package com.robin.claudeusage.channel

import android.content.Context
import androidx.compose.runtime.Composable
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.notify.Conditions

/**
 * CCRM-87 (Update Channel): everything the app does about its own updates, behind one
 * seam so each distribution channel supplies its own. Each flavor source set provides
 * `object Channel : UpdateChannel` in this package, and `main` calls only that.
 *
 * - `src/github/` checks GitHub Releases, as every build did up to v1.8.
 * - `src/play/` is a no-op: Play updates the app by itself, and Play policy forbids an
 *   update path outside the store. The GitHub classes are absent from that build, not
 *   hidden behind a flag, so a static scan of the Play artefact finds no endpoint.
 */
interface UpdateChannel {

    /** Tail of every usage poll, already on a worker thread. */
    fun autoCheck(context: Context, cache: UsageCache)

    /** The pinned panel's app-global update strip, or null when there is none to show. */
    fun updateCondition(context: Context, cache: UsageCache): Conditions.Condition?

    /**
     * Settings' Updates section, label and card, with the 24 dp gap above it. Emits
     * nothing on a channel without one, so the column simply ends sooner.
     */
    @Composable
    fun SettingsSection(cache: UsageCache)

    /** The action row of the main screen's invalid-response notice; nothing when absent. */
    @Composable
    fun InvalidResponseAction(onOpenSettings: () -> Unit)
}
