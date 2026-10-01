package com.robin.claudeusage

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robin.claudeusage.data.CredentialStoreOpener
import com.robin.claudeusage.data.HeldChanges
import com.robin.claudeusage.data.UsageRepository
import com.robin.claudeusage.diag.CrashCopy
import com.robin.claudeusage.ui.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * CCBG-50 (Degraded Store Notice): every word the approved wireframe (rev B, 2026-10-01)
 * puts on screen while the credential store runs in memory, and after. Pure.
 */
internal object StoreCopy {

    data class Notice(val title: String, val body: String, val evidence: String, val canRetry: Boolean)

    /** [clock] formats a moment as the app's 12 h/24 h time. Null while the store is healthy. */
    fun notice(s: CredentialStoreOpener.State, clock: (Long) -> String): Notice? {
        if (!s.degraded) return null
        val held = "Held in memory since ${clock(s.heldSince)}"
        val failure = if (s.simulated) "Simulated" else s.errorName ?: "Unknown"
        return when {
            s.held && s.resetFailed -> Notice(
                HELD_TITLE,
                "Secure storage isn't working, so what you just changed is held in memory only. " +
                    "It's undone when Cooldown restarts.",
                held, false,
            )
            s.held -> Notice(
                HELD_TITLE,
                "Secure storage wasn't responding, so what you just changed is held in memory only. " +
                    "It's undone when Cooldown restarts, and the sign-ins saved before come back.",
                held, false,
            )
            s.resetFailed -> Notice(
                "Cooldown couldn't set up secure storage",
                "Android's secure storage isn't working, so sign-ins can't be saved. A sign-in made " +
                    "now lasts only until the app restarts, which tries again.",
                "Reset failed · $failure", false,
            )
            else -> Notice(
                "Cooldown can't open your saved sign-ins",
                "Android's secure storage isn't responding. Anything saved is still on this phone, " +
                    "untouched, and comes back once storage answers. Cooldown keeps checking. A " +
                    "sign-in or sign-out made now lasts only until the app restarts.",
                "$failure · checked ${clock(s.checkedAt)}", s.canRetry,
            )
        }
    }

    private const val HELD_TITLE = "Changes made now last only until Cooldown restarts"

    data class Card(val title: String, val detail: String)

    /** [whenText] is [CrashCopy.whenText] of the card's time; [label] names an account key. */
    fun card(card: HeldChanges.Card, whenText: String, label: (String) -> String): Card {
        if (card is HeldChanges.Card.Reset) return Card(
            "Your saved sign-ins couldn't be read",
            "Android's secure storage could no longer unlock them, so Cooldown cleared them to " +
                "start fresh. Sign in again in Settings.",
        )
        val items = (card as HeldChanges.Card.Changes).items
        // "from on 28 Sep" reads wrong; "from 28 Sep" does not.
        val from = whenText.removePrefix("on ")
        if (items.size == 1) {
            val l = label(items[0].profileKey)
            return when (items[0].kind) {
                HeldChanges.Kind.SIGN_IN_LOST -> Card(
                    "Your $l sign-in from $from wasn't kept",
                    "Secure storage wasn't responding then, so it lasted only until the app closed. " +
                        "Sign in again in Settings if $l needs it.",
                )
                HeldChanges.Kind.BACK_ON_OLDER -> Card(
                    "$l is back on the sign-in saved before",
                    "Your newer $l sign-in from $from wasn't kept, because secure storage wasn't " +
                        "responding then. Re-sign in in Settings if you need it.",
                )
                HeldChanges.Kind.SIGNED_IN_AGAIN -> Card(
                    "$l is signed in again",
                    "You signed out of $l $whenText while secure storage wasn't responding, so the " +
                        "sign-out wasn't kept. Sign out again in Settings if you meant to.",
                )
            }
        }
        // Sign-outs first, as the wireframe reads: "you signed out of Work and signed in to Personal".
        val phrases = items.sortedBy { it.kind != HeldChanges.Kind.SIGNED_IN_AGAIN }.map {
            val l = label(it.profileKey)
            if (it.kind == HeldChanges.Kind.SIGNED_IN_AGAIN) "signed out of $l" else "signed in to $l"
        }
        val list = if (phrases.size == 2) phrases.joinToString(" and ")
        else phrases.dropLast(1).joinToString(", ") + " and " + phrases.last()
        return Card(
            "${items.size} sign-in changes from $from weren't kept",
            "Secure storage wasn't responding, so they lasted only until the app closed: you $list. " +
                "Check Settings → Accounts.",
        )
    }

    fun clearedMessage(label: String, degraded: Boolean): String =
        if (degraded) "$label signed out — until Cooldown restarts." else "$label signed out."

    const val CANT_REMOVE_BODY_TAIL =
        "'s saved sign-in can't be deleted with the rest of it. Try again once your saved sign-ins are back."

    fun debugLine(s: CredentialStoreOpener.State, clock: (Long) -> String): String = when {
        s.simulated && s.held -> "Simulated failure · a change is held in memory"
        s.simulated -> "Simulated failure · running in memory"
        s.resetFailed -> "Reset failed · ${s.errorName ?: "Unknown"}"
        s.degraded -> "Can't open · ${s.errorName ?: "Unknown"} · checked ${clock(s.checkedAt)}"
        else -> "Open · healthy"
    }
}

private fun clockText(use24h: Boolean): (Long) -> String =
    { at -> Fmt.timeOnly(Instant.ofEpochMilli(at), use24h) }

/**
 * Wireframe rev B §1: today's ErrorNotice (the warning tint at 11%, 12 dp radius, 12/10 dp
 * padding) with one plain-language body line. Emits nothing while the store is healthy;
 * not dismissable while it is not. The caller places it.
 */
@Composable
internal fun DegradedStoreNotice(use24h: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by CredentialStoreOpener.state.collectAsState()
    val n = StoreCopy.notice(state, clockText(use24h)) ?: return
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    val tint = barFill(95.0)
    Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = 0.11f), modifier = modifier) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = if (n.canRetry) 4.dp else 10.dp)) {
            Text(n.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tint)
            Spacer(Modifier.height(2.dp))
            Text(n.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                n.evidence,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = tint.copy(alpha = 0.8f),
            )
            if (n.canRetry) {
                TextButton(
                    enabled = !checking,
                    modifier = Modifier.offset(x = (-12).dp),
                    onClick = {
                        scope.launch {
                            checking = true
                            withContext(Dispatchers.IO) { CredentialStoreOpener.retryNow(context) }
                            checking = false
                        }
                    },
                ) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = tint)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Try again", color = tint)
                }
            }
        }
    }
}

/**
 * Wireframe rev B §2: the one-time card on the next working launch — the crash card's
 * look with an "i" for the "!". A reset card (call 7) comes first, then the dropped
 * changes. Emits nothing when there is neither.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoreChangeCard(
    repo: UsageRepository,
    use24h: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val changes by HeldChanges.changes.collectAsState()
    val store by CredentialStoreOpener.state.collectAsState()
    var card by remember { mutableStateOf<HeldChanges.Card?>(null) }
    LaunchedEffect(changes, store.healthy) {
        card = withContext(Dispatchers.IO) {
            // A record from an earlier session settles the moment the store opens.
            repo.settleHeldChanges()
            HeldChanges.card(context, repo.profiles().map { it.key }.toSet())
        }
    }
    val c = card ?: return
    val cache = repo.cacheSettings()
    val byKey = repo.profiles().associateBy { it.key }
    val at = when (c) { is HeldChanges.Card.Reset -> c.at; is HeldChanges.Card.Changes -> c.at }
    val copy = StoreCopy.card(
        c,
        CrashCopy.whenText(at, System.currentTimeMillis(), ZoneId.systemDefault(), use24h),
    ) { key -> byKey[key]?.let { cache.profileLabel(it) } ?: key }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                val icon = (20f * LocalDensity.current.fontScale).dp
                Box(
                    Modifier.padding(top = 1.dp).size(icon).border(2.dp, muted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("i", color = muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(copy.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(copy.detail, style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 6.dp).offset(x = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                TextButton(onClick = { HeldChanges.dismiss(context, c) }) { Text("OK") }
                TextButton(onClick = {
                    HeldChanges.dismiss(context, c)
                    onOpenSettings()
                }) { Text("Open Settings") }
            }
        }
    }
}

/** Wireframe rev B §3: Remove account while degraded. One button; nothing is deleted. */
@Composable
internal fun CantRemoveDialog(label: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Can't remove \"$label\" right now") },
        text = {
            Text(
                "Secure storage isn't responding, so $label${StoreCopy.CANT_REMOVE_BODY_TAIL}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/**
 * Wireframe rev B §4: the Debug switch, just above Crash test. Memory only — it never
 * touches the Keystore or the file, and is forgotten when the process ends.
 */
@Composable
internal fun SimulateDegradedCard(use24h: Boolean) {
    val context = LocalContext.current
    val state by CredentialStoreOpener.state.collectAsState()
    val scope = rememberCoroutineScope()
    val realFailure = state.degraded && !state.simulated
    val note = when {
        state.simulated && state.held -> "Stays on until the app restarts, because a change is held in memory."
        realFailure -> "The real store has failed, so there is nothing to simulate."
        else -> null
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SectionCard {
        Text("Credential store", style = MaterialTheme.typography.labelMedium, color = muted)
        Text(StoreCopy.debugLine(state, clockText(use24h)), style = MaterialTheme.typography.bodySmall, color = muted)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Simulate degraded store", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "In memory only. Never touches the Keystore or the saved file, and is forgotten when the app closes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = state.simulated,
                enabled = !realFailure && !(state.simulated && state.held),
                // Off re-opens the real store, which can block for the retry's 200 ms.
                onCheckedChange = { on ->
                    scope.launch { withContext(Dispatchers.IO) { CredentialStoreOpener.simulate(context, on) } }
                },
            )
        }
        note?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}
