package com.robin.claudeusage.channel

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.robin.claudeusage.RowDivider
import com.robin.claudeusage.SectionCard
import com.robin.claudeusage.ToggleRow
import com.robin.claudeusage.data.UpdateCheck
import com.robin.claudeusage.data.UpdateGate
import com.robin.claudeusage.data.UpdateInfo
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.notify.UpdateNotification
import com.robin.claudeusage.ui.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// CCRM-87 (Update Channel): Settings' Updates card, moved verbatim from SettingsScreen.kt
// into the github source set so the Play build carries none of it.

/**
 * The UPDATES section (CCRM-28): the auto-check toggle, the manual check button
 * (moved here from the About card), and the outcome line the background check
 * shares with it. Failures only ever surface here — never as a notification.
 */
@Composable
internal fun UpdatesCard(cache: UsageCache) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(cache.autoCheckUpdates()) }
    var checking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateUi?>(null) }
    // Bumped when a manual check finishes so the outcome line re-reads the cache.
    var outcomeTick by remember { mutableIntStateOf(0) }
    val versionName = remember { UpdateNotification.installedVersion(context) }

    SectionCard {
        ToggleRow(
            title = "Check automatically",
            subtitle = "Checks GitHub for a newer release every 6 hours, riding the " +
                "usage poll. A new version notifies once; a failed check never notifies.",
            checked = auto,
        ) {
            auto = it
            cache.setAutoCheckUpdates(it)
        }
        RowDivider()
        OutlinedButton(
            enabled = !checking,
            onClick = {
                checking = true
                scope.launch {
                    // Manual checks ignore the toggle and the skip record; a success
                    // still refreshes the shared last-checked line below.
                    updateResult = try {
                        val info = withContext(Dispatchers.IO) {
                            UpdateCheck.fetchLatest(versionName)
                        }
                        cache.recordUpdateCheckSuccess(
                            System.currentTimeMillis(),
                            UpdateGate.successOutcome(info.latestVersion, info.updateAvailable),
                            info.latestVersion,
                        )
                        UpdateUi.Ok(info)
                    } catch (_: Exception) {
                        cache.recordUpdateCheckFailure(System.currentTimeMillis(), "couldn't reach GitHub")
                        UpdateUi.Message(
                            "Couldn't check for updates. Check your connection and try again."
                        )
                    }
                    outcomeTick++
                    checking = false
                }
            },
        ) {
            if (checking) {
                CircularProgressIndicator(
                    Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("Checking…")
            } else {
                Text("Check for updates")
            }
        }
        Spacer(Modifier.height(8.dp))
        val lastOkAt = remember(outcomeTick) { cache.lastUpdateCheckAt() }
        val outcome = remember(outcomeTick) { cache.lastUpdateCheckOutcome() }
        val failAt = remember(outcomeTick) { cache.lastUpdateFailAt() }
        val failReason = remember(outcomeTick) { cache.lastUpdateFailReason() }
        val dismissed = remember(outcomeTick) { cache.dismissedUpdateVersion() }
        when {
            failAt > lastOkAt -> {
                Text(
                    "Last check failed ${Fmt.ago(failAt)} — " +
                        "${failReason ?: "couldn't reach GitHub"}. Retries with the next poll.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (lastOkAt > 0 && outcome != null) {
                    Text(
                        "Last successful check ${Fmt.ago(lastOkAt)} — $outcome",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            lastOkAt > 0 && outcome != null -> Text(
                "Last checked ${Fmt.ago(lastOkAt)} — ${UpdateGate.outcomeLine(outcome, dismissed)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(
                "Not checked yet — the first check rides the next poll.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    when (val r = updateResult) {
        is UpdateUi.Message -> AlertDialog(
            onDismissRequest = { updateResult = null },
            confirmButton = {
                TextButton(onClick = { updateResult = null }) { Text("OK") }
            },
            text = { Text(r.text) },
        )
        is UpdateUi.Ok -> {
            val info = r.info
            AlertDialog(
                onDismissRequest = { updateResult = null },
                title = {
                    Text(
                        if (info.updateAvailable) "Update available"
                        else "You're up to date"
                    )
                },
                text = {
                    Column {
                        if (info.updateAvailable) {
                            Text("v${info.latestVersion} is available (you have v${info.currentVersion}).")
                            if (info.notes.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    info.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (UpdateGate.isSkipped(info.latestVersion, cache.dismissedUpdateVersion())) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "You skipped this version, so it isn't notifying.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text("You're running the latest version (v${info.currentVersion}).")
                        }
                    }
                },
                confirmButton = {
                    if (info.updateAvailable && info.releaseUrl.isNotBlank()) {
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(UpdateGate.safeReleaseUrl(info.releaseUrl)),
                                )
                            )
                            updateResult = null
                        }) { Text("Open GitHub") }
                    } else {
                        TextButton(onClick = { updateResult = null }) { Text("OK") }
                    }
                },
                dismissButton = {
                    if (info.updateAvailable) {
                        TextButton(onClick = { updateResult = null }) { Text("Later") }
                    }
                },
            )
        }
        null -> {}
    }
}


private sealed interface UpdateUi {
    /** A successful check with version details. */
    data class Ok(val info: UpdateInfo) : UpdateUi
    /** A plain message (error, or fallback when no email app is present). */
    data class Message(val text: String) : UpdateUi
}
