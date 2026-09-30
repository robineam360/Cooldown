package com.robin.claudeusage

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robin.claudeusage.diag.CrashCopy
import com.robin.claudeusage.diag.CrashNowCanary
import com.robin.claudeusage.diag.CrashStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * CCRM-85 (Crash Capture), wireframe rev B §1: the next-launch card. Emits nothing while
 * no report is waiting. The caller places it — above the account tabs, or at compact
 * height at the top of the open page's column.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CrashCard(use24h: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val changes by CrashStore.changes.collectAsState()
    var unseen by remember { mutableStateOf<List<CrashStore.Report>>(emptyList()) }
    LaunchedEffect(changes) {
        unseen = withContext(Dispatchers.IO) {
            CrashStore.ingestOnce(context)
            CrashStore.unseen(context)
        }
    }
    val newest = unseen.firstOrNull() ?: return
    val whenText = CrashCopy.whenText(newest.at, System.currentTimeMillis(), ZoneId.systemDefault(), use24h)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Grey, not red: it's past news, not an ongoing failure. Grows with the font.
                val icon = (20f * LocalDensity.current.fontScale).dp
                Box(
                    Modifier
                        .padding(top = 1.dp)
                        .size(icon)
                        .border(2.dp, muted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("!", color = muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        CrashCopy.title(newest.kind, whenText),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        CrashCopy.detail(unseen.size, newest.hasTrace),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
            }
            // Wraps below each other at a large font rather than clipping.
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .offset(x = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                TextButton(onClick = { CrashStore.dismiss(context, unseen.map { it.name }) }) {
                    Text("Not now")
                }
                TextButton(onClick = { share(context, unseen) }) {
                    Text("Share report")
                }
            }
        }
    }
}

/** The crash reporter must never crash the app: a failed share just does nothing. */
private fun share(context: android.content.Context, reports: List<CrashStore.Report>) {
    CrashStore.share(context, reports)
}

/**
 * Wireframe rev B §3: every report still on file, dismissed or not, behind the 7-tap
 * unlock under "App log". Hidden (with its gap) while there are none.
 */
@Composable
internal fun CrashReportsCard(use24h: Boolean) {
    val context = LocalContext.current
    val changes by CrashStore.changes.collectAsState()
    var all by remember { mutableStateOf<List<CrashStore.Report>>(emptyList()) }
    LaunchedEffect(changes) {
        all = withContext(Dispatchers.IO) {
            CrashStore.ingestOnce(context)
            CrashStore.reports(context)
        }
    }
    val newest = all.firstOrNull() ?: return
    Spacer(Modifier.height(10.dp))
    SectionCard {
        Text("Crash reports", style = MaterialTheme.typography.bodyLarge)
        Text(
            CrashCopy.diagnostics(
                all.size,
                CrashCopy.whenText(newest.at, System.currentTimeMillis(), ZoneId.systemDefault(), use24h),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { share(context, all) }) {
                Text("Share")
            }
            OutlinedButton(onClick = { CrashStore.deleteAll(context) }) { Text("Delete") }
        }
    }
}

/** Wireframe rev B §3: the last Debug card, for the Step 7 device check. */
@Composable
internal fun CrashNowCard() {
    var confirm by remember { mutableStateOf(false) }
    val error = MaterialTheme.colorScheme.error
    SectionCard {
        Text(
            "Crash test",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Throws an uncaught exception on the main thread, so the crash card and report can be checked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { confirm = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
            border = BorderStroke(1.dp, error),
        ) { Text("Crash now") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Crash Cooldown now?") },
            text = {
                Text("The app closes at once. Reopen it and the crash card should appear. Nothing leaves the phone.")
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    // Posted, so it is thrown from the main looper itself rather than from
                    // inside Compose's click dispatch.
                    Handler(Looper.getMainLooper()).post { throw CrashNowCanary() }
                }) { Text("Crash", color = error) }
            },
        )
    }
}
