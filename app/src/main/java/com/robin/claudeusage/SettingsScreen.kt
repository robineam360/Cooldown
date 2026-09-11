package com.robin.claudeusage

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.robin.claudeusage.ui.EstimateLine
import com.robin.claudeusage.ui.appDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.browser.customtabs.CustomTabsIntent
import com.robin.claudeusage.data.ApiClient
import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.CodexDeviceSignIn
import com.robin.claudeusage.data.OAuthSignIn
import com.robin.claudeusage.data.Profile
import com.robin.claudeusage.data.ProfileRegistry
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.ErrorKind
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.data.QuickLinks
import com.robin.claudeusage.data.SignInExpiry
import com.robin.claudeusage.data.UpdateCheck
import com.robin.claudeusage.data.UpdateGate
import com.robin.claudeusage.data.UpdateInfo
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.data.UsageRepository
import com.robin.claudeusage.diag.AppLog
import com.robin.claudeusage.notify.UpdateNotification
import com.robin.claudeusage.ui.ContentColumn
import com.robin.claudeusage.ui.ContentMaxWidth
import com.robin.claudeusage.ui.DeviceCodeCopy
import com.robin.claudeusage.ui.DeviceCodeStage
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.Motion
import com.robin.claudeusage.ui.Palette
import com.robin.claudeusage.ui.ProviderMark
import com.robin.claudeusage.ui.WideMaxWidth
import com.robin.claudeusage.ui.hasTwoColumns
import com.robin.claudeusage.work.Polling
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FEEDBACK_EMAIL = "robin@eam360.com"
private const val DEBUG_UNLOCK_TAPS = 7

// CCRM-26 (Quick Links) destinations now live in the per-provider table
// com.robin.claudeusage.data.QuickLinks (CCRM-57 (Provider Plumbing)) — the main
// screen's error notice reads the same table for its "is it them?" button.

/**
 * CCRM-61 (Settings Diet), built to `design/settings-diet-wireframe.html` section 1:
 * four swipeable tabs — Accounts, Alerts, Appearance, More — over a
 * [HorizontalPager], replacing the old thirteen-section single scroll. Copies the
 * tab/pager wiring [MainActivity]'s `ProfileTabs` already uses: [rememberPagerState],
 * a coroutine-driven `animateScrollToPage` on tab tap, the indicator following the
 * pager. Unlike that strip, the four tab labels don't carry a per-account accent
 * colour, so there's no cross-fade to guard against and the default [TabRow]
 * indicator is used as-is.
 */
@Composable
fun SettingsScreen(
    repo: UsageRepository,
    /** The resolved 24-hour flag, for rendering examples — the mode is [timeFormat]. */
    use24h: Boolean,
    /** CCRM-29 (Display Mode): "system" / "light" / "dark", hoisted to the App root. */
    themeMode: String,
    onThemeMode: (String) -> Unit,
    /** CCRM-29 (Display Mode): "system" / "12" / "24", hoisted likewise. */
    timeFormat: String,
    onTimeFormat: (String) -> Unit,
    /** CCRM-22 (Used or Left) — hoisted like use24h so the main screen recomposes. */
    usageLeft: Boolean,
    onUsageLeft: (Boolean) -> Unit,
    /** CCRM-23 (Reset Display) — same hoisting for which reset form leads. */
    resetClock: Boolean,
    onResetClock: (Boolean) -> Unit,
    /**
     * CCRM-43 (Bar Pace Marks): one toggle since CCRM-61 (Settings Diet) merged the
     * in-app and notification switches. Hoisted so flipping it recomposes the usage
     * screen's bars behind this one.
     */
    showOverPace: Boolean,
    onShowOverPace: (Boolean) -> Unit,
    themeName: String,
    onTheme: (String) -> Unit,
    debugUnlocked: Boolean,
    onDebugUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cacheSettings = repo.cacheSettings()
    // Bumped when an account is added, renamed or removed, so the list and every label
    // elsewhere on this screen refresh. CCRM-6 (Multi-Account) made the account list itself
    // mutable, so this now gates the list too, not only the labels.
    var namesTick by remember { mutableIntStateOf(0) }
    val profiles = remember(namesTick) { repo.profiles() }
    val labels = remember(namesTick) { profiles.associateWith { cacheSettings.profileLabel(it) } }
    // Which account's ⋮ sheet or dialog is open, and which kind.
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var removing by remember { mutableStateOf<Profile?>(null) }
    val accountScope = rememberCoroutineScope()

    var showAddSheet by remember { mutableStateOf(false) }
    // CCRM-56 (Provider Identity): the account the sheet just minted, so its
    // card starts sign-in itself the moment it mounts.
    var autoStartProfileKey by remember { mutableStateOf<String?>(null) }

    // --- Accounts tab ---
    val accountsTab: @Composable () -> Unit = {
        SectionLabel("Accounts")
        if (hasTwoColumns()) {
            // At the inner screen's width the cards alternate left/right (1st left,
            // 2nd right, …) rather than splitting into two independent lists — with
            // only two or three accounts a subject split would leave one column empty.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    for ((index, profile) in profiles.withIndex()) {
                        if (index % 2 != 0) continue
                        key(profile.key) {
                            TokenCard(
                                repo, profile, use24h,
                                label = labels.getValue(profile),
                                canRemove = profiles.size > 1,
                                onRename = { renaming = profile },
                                onRemove = { removing = profile },
                                autoStartSignIn = profile.key == autoStartProfileKey,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    for ((index, profile) in profiles.withIndex()) {
                        if (index % 2 != 1) continue
                        key(profile.key) {
                            TokenCard(
                                repo, profile, use24h,
                                label = labels.getValue(profile),
                                canRemove = profiles.size > 1,
                                onRename = { renaming = profile },
                                onRemove = { removing = profile },
                                autoStartSignIn = profile.key == autoStartProfileKey,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        } else {
            for (profile in profiles) {
                // Keyed on the account, not on its position: removing a card from the
                // middle would otherwise shift every card below it onto the state of
                // its neighbour — a signed-in card showing the next account's sign-in
                // step.
                key(profile.key) {
                    TokenCard(
                        repo, profile, use24h,
                        label = labels.getValue(profile),
                        canRemove = profiles.size > 1,
                        onRename = { renaming = profile },
                        onRemove = { removing = profile },
                        autoStartSignIn = profile.key == autoStartProfileKey,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        // CCRM-6 (Multi-Account): the card appears immediately with a positional default
        // label, in its familiar not-signed-in state — no name-first dialog, because the
        // next tap the user wants is "Sign in on this phone". Renaming is a later thought.
        // CCRM-56 (Provider Identity), decision 5: the sheet picks the provider now too.
        OutlinedButton(
            onClick = { showAddSheet = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("+ Add account") }
        if (showAddSheet) {
            AddAccountSheet(
                onDismiss = { showAddSheet = false },
                onPick = { provider ->
                    val newProfile = repo.addProfile(provider = provider)
                    namesTick++
                    Shortcuts.publish(context)
                    autoStartProfileKey = newProfile.key
                    showAddSheet = false
                },
            )
        }
        // The old "Profile names" section lived here. Names are registry-owned now and each
        // card renames itself through ⋮ → Rename, so a second editor for the same field
        // would only raise the question of which one is authoritative.
    }

    // --- Alerts tab ---
    val alwaysOnCard: @Composable () -> Unit = {
        SectionLabel("Always-on notification")
        SectionCard {
            var pinned by remember { mutableStateOf(cacheSettings.pinnedEnabled()) }
            var first by remember { mutableStateOf(cacheSettings.pinnedProfile()) }
            var second by remember { mutableStateOf(cacheSettings.pinnedSecondProfile()) }
            var tapTarget by remember { mutableStateOf(cacheSettings.pinnedTapTarget()) }
            var ringShows by remember { mutableStateOf(cacheSettings.statusRingShows()) }
            fun refreshPinned() {
                com.robin.claudeusage.notify.PinnedNotification.update(context, cacheSettings)
            }
            ToggleRow(
                title = "Always-on usage notification",
                subtitle = "Silent and ongoing, with a status-bar ring. Nothing else " +
                    "posts on its own except the reset pings below.",
                checked = pinned,
            ) {
                pinned = it
                cacheSettings.setPinnedEnabled(it)
                refreshPinned()
            }
            if (pinned) {
                RowDivider()
                Text("Show accounts", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                // FlowRow, not Row: labels do not fit one line at any phone width, and
                // a plain Row would clip the last chip out of reach entirely. Lists
                // every registered account, signed in or not — this is Settings,
                // where accounts live, so the setting stays visible while an account
                // is signed out.
                val pinnedPickerDark = appDark()
                Text(
                    "First",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in profiles) {
                        FilterChip(
                            selected = first == p,
                            onClick = {
                                first = p
                                cacheSettings.setPinnedProfile(p)
                                // CCRM-62 (Duet Notification): the same account can't
                                // be both halves — if First just became what Second
                                // already was, Second collapses back to None.
                                if (second == p) {
                                    second = null
                                    cacheSettings.setPinnedSecondProfile(null)
                                }
                                refreshPinned()
                            },
                            label = { Text(labels.getValue(p)) },
                            leadingIcon = {
                                ProviderMark(
                                    p.provider,
                                    size = 14.dp,
                                    tint = Palette.color(Palette.accentName(cacheSettings, p), pinnedPickerDark),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Second · optional",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = second == null,
                        onClick = {
                            second = null
                            cacheSettings.setPinnedSecondProfile(null)
                            refreshPinned()
                        },
                        label = { Text("None") },
                    )
                    for (p in profiles) {
                        FilterChip(
                            selected = second == p,
                            // Picking the same account as First is refused outright —
                            // a Duet of one account said twice would say nothing.
                            enabled = p != first,
                            onClick = {
                                second = p
                                cacheSettings.setPinnedSecondProfile(p)
                                refreshPinned()
                            },
                            label = { Text(labels.getValue(p)) },
                            leadingIcon = {
                                ProviderMark(
                                    p.provider,
                                    size = 14.dp,
                                    tint = Palette.color(Palette.accentName(cacheSettings, p), pinnedPickerDark),
                                )
                            },
                        )
                    }
                }
                RowDivider()
                Text("Tapping a number opens", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                val firstProviderApp = first.provider
                val providerAppInstalled = remember(firstProviderApp) {
                    com.robin.claudeusage.notify.PinnedNotification.providerLaunchIntent(context, firstProviderApp) != null
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The stored value "claude" from pre-multi-provider installs is
                    // read as "provider" (PinnedNotification.tapIntent).
                    for ((value, text) in listOf("app" to "Cooldown", "provider" to "That service's app")) {
                        FilterChip(
                            selected = tapTarget == value || (value == "provider" && tapTarget == "claude"),
                            onClick = {
                                tapTarget = value
                                cacheSettings.setPinnedTapTarget(value)
                                refreshPinned()
                            },
                            label = { Text(text) },
                        )
                    }
                }
                if ((tapTarget == "provider" || tapTarget == "claude") && !providerAppInstalled) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The ${firstProviderApp.displayName} app isn't installed — taps " +
                            "open Cooldown instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RowDivider()
                Text("Status-bar ring shows", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                if (second == null) {
                    Text(
                        "One account, so there is nothing to choose",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilterChip(
                        selected = true,
                        enabled = false,
                        onClick = {},
                        label = { Text("First") },
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    // FlowRow: three chips wrap rather than clip, and nothing inside a
                    // page may scroll horizontally against the pager.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((value, text) in listOf(
                            UsageCache.RING_FIRST to "First",
                            UsageCache.RING_SECOND to "Second",
                            UsageCache.RING_HIGHER to "Whichever is higher",
                        )) {
                            FilterChip(
                                selected = ringShows == value,
                                onClick = {
                                    ringShows = value
                                    cacheSettings.setStatusRingShows(value)
                                    refreshPinned()
                                },
                                label = { Text(text) },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "A clean ring for the 5h window, in that account's own colour, " +
                            "so the colour tells you which account you are looking at.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RowDivider()
            // Always shown, not gated on the toggle above: two channels are left —
            // pinned_usage_v2 and reset_alerts — and this is still the only way to
            // silence or hide either at the OS level.
            LinkRow("System notification settings", subtitle = "The two channels left") {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        }
    }

    val resetPingsCard: @Composable () -> Unit = {
        SectionLabel("Reset pings")
        SectionCard {
            Text(
                "The one notification that still posts on its own. \"If busy\" pings " +
                    "only when that window had reached 80% before it reset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // Per account since CCRM-61 (Settings Diet): the reset ping is the only
            // standalone notification left, so this *is* the "which accounts may
            // interrupt me" control — there is no separate per-profile alerts toggle
            // above it any more to mean that.
            for ((index, profile) in profiles.withIndex()) {
                if (index > 0) RowDivider()
                Text(labels.getValue(profile), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                ResetModeRow("5h reset", profile, "Session", cacheSettings)
                Spacer(Modifier.height(8.dp))
                ResetModeRow("Weekly reset", profile, "Weekly", cacheSettings)
            }
        }
    }

    // --- Appearance tab ---
    val themeTimeUsageRows: @Composable () -> Unit = {
        // CCRM-29 (Display Mode): a forced theme drives the Material scheme, the
        // chart's per-mode opacities and the status-bar icons together (via
        // LocalAppDark). The notification keeps following the system — its
        // backdrop is the shade's, not ours.
        Text(
            "Theme",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, text) in listOf(
                "system" to "System", "light" to "Light", "dark" to "Dark",
            )) {
                FilterChip(
                    selected = themeMode == value,
                    onClick = {
                        onThemeMode(value)
                        cacheSettings.setThemeMode(value)
                    },
                    label = { Text(text) },
                )
            }
        }
        RowDivider()
        // CCRM-29 (Display Mode): grown from the old 24-hour switch. An install
        // that ever touched that switch keeps its explicit choice (see
        // UsageCache.timeFormat); only fresh installs land on System.
        Text(
            "Time format",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (use24h) "Times shown like Thu 23:45" else "Times shown like Thu 11:45 PM",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, text) in listOf(
                "system" to "System", "12" to "12-hour", "24" to "24-hour",
            )) {
                FilterChip(
                    selected = timeFormat == value,
                    onClick = {
                        onTimeFormat(value)
                        cacheSettings.setTimeFormat(value)
                        com.robin.claudeusage.notify.PinnedNotification.update(context, cacheSettings)
                    },
                    label = { Text(text) },
                )
            }
        }
        RowDivider()
        // CCRM-22 (Used or Left): one global token; every numeric readout follows
        // it (rev B). Bars, ring fills and the warning colours always show the
        // spend, so a red bar can't sit beside "8% left" and read as backwards.
        Text(
            "Usage display",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "How percentages read on every surface. Bars and colours always show " +
                "what's spent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, text) in listOf("used" to "Used", "left" to "Left")) {
                FilterChip(
                    selected = if (value == "left") usageLeft else !usageLeft,
                    onClick = {
                        onUsageLeft(value == "left")
                        cacheSettings.setUsageDisplay(value)
                        // Re-post so the change lands without waiting for a poll.
                        com.robin.claudeusage.notify.PinnedNotification
                            .update(context, cacheSettings)
                    },
                    label = { Text(text) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (usageLeft) "Shows \"53% left\" — what remains of each window."
            else "Shows \"47% used\" — what each window has consumed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val resetPaceColorRows: @Composable () -> Unit = {
        // CCRM-23 (Reset Display), Option A: the token decides which reset form
        // *leads*; surfaces with a second slot keep the other form there. Grown
        // from the old tile-only countdown/clock choice (CCRM-11), which this
        // replaced.
        Text(
            "Reset time",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Which form leads wherever a reset is shown. Where there's room, " +
                "the other form keeps the second slot.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, text) in listOf("countdown" to "Countdown", "clock" to "Clock time")) {
                FilterChip(
                    selected = if (value == "clock") resetClock else !resetClock,
                    onClick = {
                        onResetClock(value == "clock")
                        cacheSettings.setResetDisplay(value)
                        // Re-post so the change lands without waiting for a poll.
                        com.robin.claudeusage.notify.PinnedNotification
                            .update(context, cacheSettings)
                    },
                    label = { Text(text) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (resetClock)
                "Leads with \"resets 4:12 PM\". A clock time can't go stale on " +
                    "surfaces that refresh every 15 minutes."
            else
                "Leads with \"resets in 2h 14m\", collapsing to \"resets soon\" " +
                    "inside five minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RowDivider()
        // CCRM-61 (Settings Diet): the old three-way split (in-app / notification /
        // widgets) collapses to this one toggle — the widgets it also used to gate
        // are gone, and the two remaining surfaces are both read up close, so one
        // switch covers both. It gates only the red past the mark; the neutral
        // even-pace tick always draws (CCRM-43, Bar Pace Marks).
        ToggleRow(
            title = "Show red past the pace mark",
            subtitle = "Colours the bar past the even-pace tick, in the app and in the " +
                "notification. Off keeps the tick without the colour.",
            checked = showOverPace,
        ) {
            onShowOverPace(it)
            cacheSettings.setShowOverPace(it)
            // Re-post so the change lands without waiting for the next refresh.
            com.robin.claudeusage.notify.PinnedNotification.update(context, cacheSettings)
        }
        RowDivider()
        Text("Theme color", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        ThemeColorPicker(themeName) {
            onTheme(it)
            repo.cacheSettings().setThemeColorName(it)
            // CCBG-14 (Stale Notification Theme): the pinned notification's gauge
            // and status-bar glyph wear the theme too — redraw now, not next poll.
            com.robin.claudeusage.notify.PinnedNotification.update(context, repo.cacheSettings())
        }
    }

    // --- More tab ---
    // CCRM-61 (Settings Diet): the whole Usage credits section is conditional —
    // it renders only once some registered account's cached snapshot actually
    // reports a credit budget, so an all-Claude-plan install never sees a header
    // for a feature that applies to nobody signed in.
    val showCredits = remember(namesTick) {
        profiles.any { cacheSettings.snapshot(it).data?.credits?.isReportable == true }
    }
    val pollingUpdatesCredits: @Composable () -> Unit = {
        SectionLabel("Polling")
        PollingSection(repo)
        Spacer(Modifier.height(24.dp))
        if (showCredits) {
            SectionLabel("Usage credits")
            SectionCard {
                Text(
                    "Pay-as-you-go credits that cover you once a plan window runs out. " +
                        "The section only appears for accounts that actually have a " +
                        "credit budget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                for (profile in profiles) {
                    RowDivider()
                    var visible by remember(profile) {
                        mutableStateOf(cacheSettings.creditsVisible(profile))
                    }
                    ToggleRow(
                        title = "Show for ${labels.getValue(profile)}",
                        subtitle = "Credits card on this profile's screen",
                        checked = visible,
                    ) {
                        visible = it
                        cacheSettings.setCreditsVisible(profile, it)
                        com.robin.claudeusage.notify.PinnedNotification.update(context, cacheSettings)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        SectionLabel("Updates")
        UpdatesCard(cacheSettings)
    }

    val diagnosticsAboutDebug: @Composable () -> Unit = {
        // CCRM-34 (Diagnostics Log): visible without the debug unlock — for a
        // sideload-only app with an email feedback channel, "share your log" is
        // the diagnosis path, so it can't hide behind a 7-tap ritual.
        SectionLabel("Diagnostics")
        AppLogCard(cacheSettings)
        Spacer(Modifier.height(24.dp))

        SectionLabel("About")
        AboutCard(debugUnlocked, onDebugUnlock)

        if (debugUnlocked) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Debug")
            TrendDiagnostics(repo, use24h)
            Spacer(Modifier.height(10.dp))
            DebugSection(repo) { namesTick++; Shortcuts.publish(context) }
        }
    }

    // --- tabs + pager ---
    // Plain `remember`, not `rememberSaveable`: Settings always reopens on the
    // Accounts tab, including the Settings → Guide → back trip — that trip leaves
    // this composable's branch of MainActivity's screen `when` entirely, so its
    // remembered state (this pager included) is gone by the time it's rebuilt.
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val tabScope = rememberCoroutineScope()
    // Read inside the click, never captured at composition — see ProfileTabs'
    // own motionContext for why.
    val motionContext = LocalContext.current
    val tabTitles = listOf("Accounts", "Alerts", "Appearance", "More")

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pagerState.currentPage) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        tabScope.launch {
                            if (Motion.reduced(Motion.scale(motionContext))) {
                                pagerState.scrollToPage(index)
                            } else {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    },
                ) {
                    // 10 dp a side rather than Material's default 16 dp, so
                    // "Appearance" fits in the ~102 dp four tabs get at a 410 dp
                    // width (decision 4 of the approved wireframe). If a larger
                    // font scale still reads tight on some device, the fallback is
                    // ScrollableTabRow rather than shrinking this further.
                    Text(
                        title,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ContentColumn(maxWidth = if (hasTwoColumns()) WideMaxWidth else ContentMaxWidth) {
                Spacer(Modifier.height(8.dp))
                when (page) {
                    0 -> accountsTab()
                    1 -> if (hasTwoColumns()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Column(Modifier.weight(1f)) { alwaysOnCard() }
                            Column(Modifier.weight(1f)) { resetPingsCard() }
                        }
                    } else {
                        alwaysOnCard()
                        Spacer(Modifier.height(24.dp))
                        resetPingsCard()
                    }
                    2 -> {
                        SectionLabel("Appearance")
                        if (hasTwoColumns()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                Column(Modifier.weight(1f)) { SectionCard { themeTimeUsageRows() } }
                                Column(Modifier.weight(1f)) { SectionCard { resetPaceColorRows() } }
                            }
                        } else {
                            SectionCard {
                                themeTimeUsageRows()
                                RowDivider()
                                resetPaceColorRows()
                            }
                        }
                    }
                    else -> if (hasTwoColumns()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Column(Modifier.weight(1f)) { pollingUpdatesCredits() }
                            Column(Modifier.weight(1f)) { diagnosticsAboutDebug() }
                        }
                    } else {
                        pollingUpdatesCredits()
                        Spacer(Modifier.height(24.dp))
                        diagnosticsAboutDebug()
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // CCRM-6 (Multi-Account): both live here rather than inside TokenCard so they survive
    // the card recomposing under them, and so the remove path can bump namesTick once.
    renaming?.let { profile ->
        RenameAccountDialog(
            current = labels[profile] ?: cacheSettings.profileLabel(profile),
            fallback = repo.registry().defaultLabelFor(profile.key),
            onDismiss = { renaming = null },
            onSave = { name ->
                repo.renameProfile(profile, name)
                renaming = null
                namesTick++
                // CCRM-33 (App Shortcuts): shortcut labels follow renames.
                Shortcuts.publish(context)
                com.robin.claudeusage.notify.PinnedNotification.update(context, cacheSettings)
            },
        )
    }
    removing?.let { profile ->
        RemoveAccountDialog(
            label = labels[profile] ?: cacheSettings.profileLabel(profile),
            replacement = repo.profiles().firstOrNull { it != profile }
                ?.let { cacheSettings.profileLabel(it) } ?: "",
            onDismiss = { removing = null },
            onConfirm = {
                accountScope.launch {
                    repo.removeProfile(profile)
                    removing = null
                    namesTick++
                    com.robin.claudeusage.notify.PinnedNotification.update(context, cacheSettings)
                }
            },
        )
    }
}

/**
 * CCRM-6 (Multi-Account): rename lives behind the card's ⋮ rather than in a standing text
 * field, so a card at rest looks exactly as it did before three accounts were possible.
 */
@Composable
private fun RenameAccountDialog(
    current: String,
    fallback: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename account") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ProfileRegistry.MAX_LABEL) },
                    label = { Text("Name") },
                    singleLine = true,
                    supportingText = { Text("${name.length}/${ProfileRegistry.MAX_LABEL}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Used everywhere — tabs, notifications and shortcuts. " +
                        "Clear the field to go back to \"$fallback\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The one destructive confirmation in the app, so it names what goes rather than asking
 * "are you sure" — including the year-long session log, which is the part that is genuinely
 * unrecoverable, and the fact that **Clear** is the non-destructive alternative (CCBG-1
 * (History Retention) is why the two are different actions at all).
 */
@Composable
private fun RemoveAccountDialog(
    label: String,
    replacement: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove \"$label\"?") },
        text = {
            Column {
                Text(
                    "This deletes, permanently and only for this account:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                for (line in listOf(
                    "the saved sign-in",
                    "its cached usage and settings — alerts, credits, reset pings",
                    "8 days of trend history",
                    "a year of session and weekly history",
                )) {
                    Text("•  $line", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Its launcher shortcut disappears." +
                        if (replacement.isNotEmpty()) {
                            " The pinned notification switches to $replacement, if it was " +
                                "showing this account."
                        } else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "To keep the history and just sign out, use Clear instead.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove account", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class BrowserChoice(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?,
)

/**
 * Launcher icons are adaptive drawables, not bitmaps, so render into one by hand
 * (core-ktx's toBitmap() isn't a declared dependency). Null on any failure — the
 * picker row then keeps its leading space so labels stay aligned.
 */
private fun Drawable.asIconBitmap(): ImageBitmap? = try {
    val w = intrinsicWidth.coerceAtLeast(1)
    val h = intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, w, h)
    draw(Canvas(bmp))
    bmp.asImageBitmap()
} catch (_: Exception) {
    null
}

/**
 * Installed browsers, for the sign-in "open with" picker. Uses QUERY_ALL_PACKAGES
 * (fine for a sideload app) so OEM skins that under-report a scoped <queries> still
 * list every browser. Signing a given account in the browser where that account is
 * logged in is the whole point — e.g. Work in Samsung Internet, Personal in Brave.
 */
private fun installedBrowsers(context: android.content.Context): List<BrowserChoice> {
    val pm = context.packageManager
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    return pm.queryIntentActivities(probe, android.content.pm.PackageManager.MATCH_ALL)
        .mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            val icon = try {
                ri.loadIcon(pm)?.asIconBitmap()
            } catch (_: Exception) {
                null
            }
            BrowserChoice(ri.loadLabel(pm).toString(), pkg, icon)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/**
 * Whether a URL is one the app will hand to a browser: plain web links only.
 * Every launch site uses a compile-time https constant today; this is the guard
 * that keeps a future refactor from sending an `intent://` or `javascript:`
 * payload through the same path.
 */
internal fun allowedLinkUrl(url: String): Boolean {
    val scheme = url.trim().substringBefore(':', missingDelimiterValue = "")
    return scheme.equals("https", ignoreCase = true) || scheme.equals("http", ignoreCase = true)
}

/**
 * Opens a URL in a specific browser (full external app, not an in-app tab).
 * The single launch path for sign-in and the CCRM-26 (Quick Links) buttons —
 * shared with MainActivity so the [allowedLinkUrl] check guards every launch
 * through this path. (The release-notes dialog and the mailto feedback intent
 * build their own intents and are guarded at their own call sites.)
 */
internal fun openInBrowser(context: android.content.Context, url: String, pkg: String?) {
    if (!allowedLinkUrl(url)) return
    val uri = Uri.parse(url)
    if (pkg != null) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .setPackage(pkg)
            )
            return
        } catch (_: Exception) {
            // Fall through to a generic open.
        }
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: Exception) {
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        } catch (_: Exception) {
            // Nothing on the device can open a web link.
        }
    }
}

@Composable
private fun TokenCard(
    repo: UsageRepository,
    profile: Profile,
    use24h: Boolean,
    label: String,
    canRemove: Boolean,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    /** CCRM-56 (Provider Identity): the Add-account sheet starts sign-in at once. */
    autoStartSignIn: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var stateKey by remember { mutableIntStateOf(0) }

    // Sign-in completion state. awaitingCode survives config changes; if the
    // process was killed during the browser trip, a persisted pending sign-in
    // for this profile re-opens the completion step on its own.
    var awaitingCode by remember { mutableStateOf(repo.hasPendingSignIn(profile)) }
    var codeInput by remember { mutableStateOf("") }
    var authUrl by remember { mutableStateOf<String?>(null) }

    val hasToken = remember(stateKey) { repo.hasCredentials(profile) }
    val snapshot = remember(stateKey) { repo.snapshot(profile) }
    val addedAt = remember(stateKey) { repo.tokenAddedAt(profile) }
    val tail = remember(stateKey) { repo.tokenTail(profile) }
    val plan = remember(stateKey) { repo.plan(profile) }
    val tier = remember(stateKey) { repo.tier(profile) }
    val tokenExpiresAt = remember(stateKey) { repo.tokenExpiresAt(profile) }
    val refreshExpiresAt = remember(stateKey) { repo.refreshExpiresAt(profile) }
    val refreshEstimated = remember(stateKey) { repo.refreshExpiryEstimated(profile) }
    val lastRenewedAt = remember(stateKey) { repo.lastRenewedAt(profile) }
    val backoffUntil = remember(stateKey) { repo.cacheSettings().backoffUntil(profile) }
    val firstRefreshFailAt = remember(stateKey) { repo.cacheSettings().firstRefreshFailAt(profile) }

    val openWithPicker = rememberBrowserOpener(label, profile.provider)

    fun beginSignIn() {
        message = null
        codeInput = ""
        awaitingCode = true
        val url = repo.startSignIn(profile)
        authUrl = url
        openWithPicker(url)
    }

    fun finishSignIn() {
        scope.launch {
            busy = true
            message = null
            val result = repo.completeSignIn(profile, codeInput)
            if (result.message == "OK") {
                Polling.schedulePeriodic(context, repo.cacheSettings().pollIntervalMinutes())
                message = "$label signed in — usage fetched, polling started."
                awaitingCode = false
                codeInput = ""
            } else {
                message = result.message
            }
            busy = false
            stateKey++
        }
    }

    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // CCRM-56 (Provider Identity), decision 4: the mark, not a dot,
                // identifies the service — 20dp before the label on account cards.
                val cardDark = appDark()
                val cardAccent = remember(stateKey, cardDark) {
                    Palette.color(Palette.accentName(repo.cacheSettings(), profile), cardDark)
                }
                ProviderMark(profile.provider, size = 20.dp, tint = cardAccent)
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                if (hasToken) StatusChip(snapshot.authState)
                if (hasToken && plan != null) {
                    Spacer(Modifier.width(6.dp))
                    // CCRM-57 (Provider Plumbing): tier is Anthropic's `default_5x`
                    // grammar. No multiplier is invented for OpenAI or Google, so a
                    // non-Claude account passes null and renders the bare plan.
                    // CCRM-64 (Claude Plan Tag): the multiplier is only meaningful on
                    // Max ("Max 5x"); a Team premium seat also reports a `_5x` tier,
                    // and "Team Premium 5x" would read as a third plan.
                    PlanChip(
                        plan,
                        tier.takeIf { profile.provider == Provider.CLAUDE && plan.startsWith("Max") },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (hasToken && tail != null) {
                    Text(
                        "…$tail",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (busy) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                // CCRM-6 (Multi-Account): rename and remove live here, deliberately away
                // from "Clear" in the button row below. Clear signs out and keeps the
                // history; Remove destroys it. Side by side, that is a fat-finger disaster.
                var menuOpen by remember { mutableStateOf(false) }
                var showAccentPicker by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Account options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename…") },
                            onClick = { menuOpen = false; onRename() },
                        )
                        // CCRM-56 (Provider Identity), decision 1: the per-account override.
                        DropdownMenuItem(
                            text = { Text("Accent colour…") },
                            onClick = { menuOpen = false; showAccentPicker = true },
                        )
                        // Hidden on the last remaining card: the registry always keeps one
                        // account, which is what makes the zero-configured fallback work.
                        if (canRemove) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Remove account",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = { menuOpen = false; onRemove() },
                            )
                        }
                    }
                }
                if (showAccentPicker) {
                    AccentColorDialog(
                        profile = profile,
                        cache = repo.cacheSettings(),
                        onDismiss = { showAccentPicker = false },
                        onPicked = { showAccentPicker = false; stateKey++ },
                    )
                }
            }

            // The sign-in completion step takes over the card while active, for
            // either a brand-new sign-in or a re-sign-in of an existing account.
            if (awaitingCode) {
                SignInCompletion(
                    busy = busy,
                    codeInput = codeInput,
                    onCodeChange = { codeInput = it },
                    onPaste = { clipboard.getText()?.text?.let { codeInput = it.trim() } },
                    onFinish = { finishSignIn() },
                    onReopen = { authUrl?.let { openWithPicker(it) } ?: beginSignIn() },
                    onCancel = {
                        repo.cancelSignIn()
                        awaitingCode = false
                        codeInput = ""
                        message = null
                    },
                )
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                return@Column
            }

            // CCRM-54 (ChatGPT Account) part 2: everything below the shared header —
            // the label, chip, ⋮ menu — is the provider's own. See ChatGptAccountBody
            // for what a non-Claude account deliberately does not have.
            if (profile.provider != Provider.CLAUDE) {
                ChatGptAccountBody(
                    repo, profile, label, use24h,
                    autoStart = autoStartSignIn,
                ) { stateKey++ }
                return@Column
            }

            // CCRM-56 (Provider Identity): the Add-account sheet's Claude row lands
            // here and starts sign-in immediately, matching ChatGPT's autoStart.
            LaunchedEffect(Unit) {
                if (autoStartSignIn && !hasToken && !awaitingCode) beginSignIn()
            }

            if (!hasToken) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Not signed in",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(enabled = !busy, onClick = { beginSignIn() }) {
                    Text("Sign in on this phone")
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Opens Claude's sign-in in your browser — no computer needed. " +
                        "Needs a paid plan: Claude reports no usage for Free accounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Last checked: ${Fmt.dayTimeWithAgo(snapshot.lastAttemptAt, use24h)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                message = null
                                val result = repo.refreshNow(profile, manual = true)
                                message = if (result.message == "OK") "Token checked — working." else result.message
                                busy = false
                                stateKey++
                            }
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Check token now",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                val now = System.currentTimeMillis()
                // CCBG-27 (Free Plan 403): the sign-in works, the plan does not — said
                // on the card, under a plan chip that now reads "Free".
                if (snapshot.lastStatusKind == ErrorKind.PLAN.key) {
                    Text(
                        "Claude doesn't report usage for the ${plan ?: "Free"} plan — " +
                            "upgrade to Pro, Max or Team to see numbers here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (tokenExpiresAt > 0) {
                    Text(
                        if (tokenExpiresAt > now) "Auto-renews in ${Fmt.dhm(tokenExpiresAt)}"
                        else "Renewal due at the next check",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (lastRenewedAt > 0) {
                    Text(
                        "Last auto-renewed: ${Fmt.dayTimeWithAgo(lastRenewedAt, use24h)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // CCRM-16: once renewal is dead the ~30-day estimate must stop
                // rendering as a date — the fail-streak start is the best fix on
                // when it died, falling back to the failure's own timestamp.
                val deadAt = when {
                    firstRefreshFailAt > 0 -> firstRefreshFailAt
                    snapshot.lastAttemptAt > 0 -> snapshot.lastAttemptAt
                    else -> now
                }
                when (
                    val expiry = SignInExpiry.line(
                        snapshot.authState, refreshEstimated, refreshExpiresAt, addedAt, deadAt, now,
                    )
                ) {
                    is SignInExpiry.Line.RenewalDead -> {
                        val estDays = OAuthSignIn.ESTIMATED_FAMILY_MS / SignInExpiry.DAY_MS
                        val days = expiry.daysObserved
                        Text(
                            when {
                                days == null ->
                                    "Renewal has stopped working — re-sign in below."
                                expiry.earlierThanEstimate && days == 0L ->
                                    "Renewal stopped working within a day of sign-in — " +
                                        "earlier than the ~$estDays-day estimate. Re-sign in below."
                                expiry.earlierThanEstimate ->
                                    "Renewal stopped working $days day${if (days == 1L) "" else "s"} after sign-in — " +
                                        "earlier than the ~$estDays-day estimate. Re-sign in below."
                                else ->
                                    "Renewal stopped working ~$days days after sign-in — " +
                                        "the sign-in likely reached its age limit. Re-sign in below."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    // CCRM-30 (Estimate Honesty): the ~30-day figure is inferred and
                    // has never been observed, so it carries the marker; the Exact
                    // line below is server-reported and stays plain — marking a
                    // measurement would hedge it.
                    is SignInExpiry.Line.Estimated -> EstimateLine(
                        text = "Sign-in expires around ${Fmt.dateTime(expiry.expiresAt, use24h)} · ~${Fmt.dhm(expiry.expiresAt)} left",
                        provenance = "Anthropic doesn't report the real expiry — this is " +
                            "a flat ~30-day estimate from sign-in, corrected if renewal " +
                            "stops working earlier.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is SignInExpiry.Line.Exact -> Text(
                        "Sign-in valid until ${Fmt.dateTime(expiry.expiresAt, use24h)} · ${Fmt.dhm(expiry.expiresAt)} to go",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SignInExpiry.Line.None -> {}
                }
                Text(
                    "Added: ${if (addedAt > 0) Fmt.date(addedAt) else "before v0.7"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (backoffUntil > now) {
                    Text(
                        "Rate-limited — next try in ${Fmt.dhm(backoffUntil)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(enabled = !busy, onClick = { beginSignIn() }) { Text("Re-sign in") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            repo.clearCredentials(profile)
                            message = "$label signed out."
                            stateKey++
                        },
                    ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                }
                RowDivider()
                QuickLinksRow(
                    provider = profile.provider,
                    onOpenDefault = { openInBrowser(context, it, null) },
                    onOpenWithPicker = { openWithPicker(it) },
                )
            }

            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * "Open this URL in a browser, asking which one when there's more than one" — so a
 * Work and a Personal account can route through different browsers, which is the
 * whole reason the choice exists. Always a real external browser, never an in-app
 * tab. Returns the opener and hosts the dialog itself, so every caller gets the
 * same behaviour by asking for it once.
 *
 * Shared by both sign-ins and by the CCRM-26 (Quick Links) account link: *which
 * browser holds this profile's session* is the same question on all three.
 */
@Composable
private fun rememberBrowserOpener(label: String, provider: Provider): (String) -> Unit {
    val context = LocalContext.current
    var pendingUrl by remember { mutableStateOf<String?>(null) }

    pendingUrl?.let { url ->
        val browsers = remember { installedBrowsers(context) }
        AlertDialog(
            onDismissRequest = { pendingUrl = null },
            title = { Text("Open with") },
            text = {
                Column {
                    Text(
                        "Pick the browser where you're signed in to the $label " +
                            "${provider.displayName} account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    for (b in browsers) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    pendingUrl = null
                                    openInBrowser(context, url, b.packageName)
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (b.icon != null) {
                                Image(b.icon, contentDescription = null, Modifier.size(32.dp))
                            } else {
                                Spacer(Modifier.size(32.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(b.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingUrl = null }) { Text("Cancel") }
            },
        )
    }

    return { url ->
        val browsers = installedBrowsers(context)
        if (browsers.size >= 2) pendingUrl = url
        else openInBrowser(context, url, browsers.firstOrNull()?.packageName)
    }
}

/**
 * A ChatGPT account's card body (CCRM-54 (ChatGPT Account) part 2), built to
 * `design/provider-identity-wireframe.html` section 4. It shares the card header
 * above it — mark, label, [StatusChip], [PlanChip] — and replaces everything else,
 * because a non-Claude account has none of that machinery: no authorize URL, no
 * pasted `code#state`, no QR or desktop-JSON backup, and no ~30-day family estimate
 * to draw an "expires around" line from (CCRM-16 (Sign-in Expiry Accuracy)'s rule
 * that no estimate beats a wrong one — `refreshExpiresAt` stays 0 here).
 *
 * Sign-in opens the [DeviceCodeSheet]; the poll runs while the sheet is open and
 * resumes from [UsageRepository.pendingDeviceSignIn] after process death — fifteen
 * minutes does not justify WorkManager.
 */
@Composable
private fun ChatGptAccountBody(
    repo: UsageRepository,
    profile: Profile,
    label: String,
    use24h: Boolean,
    /** CCRM-56 (Provider Identity): the Add-account sheet starts sign-in at once. */
    autoStart: Boolean = false,
    onStateChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openWithPicker = rememberBrowserOpener(label, profile.provider)
    var tick by remember { mutableIntStateOf(0) }
    val hasToken = remember(tick) { repo.hasCredentials(profile) }
    val snapshot = remember(tick) { repo.snapshot(profile) }
    val addedAt = remember(tick) { repo.tokenAddedAt(profile) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // A flow interrupted by process death reopens the sheet where it left off.
    var started by remember { mutableStateOf(repo.pendingDeviceSignIn(profile)) }
    var stage by remember {
        mutableStateOf(if (started != null) DeviceCodeStage.WAITING else null)
    }
    var detail by remember { mutableStateOf<String?>(null) }

    fun bump() {
        tick++
        onStateChanged()
    }

    fun beginDeviceSignIn() {
        scope.launch {
            message = null
            detail = null
            stage = DeviceCodeStage.STARTING
            stage = try {
                started = repo.startDeviceSignIn(profile)
                DeviceCodeStage.WAITING
            } catch (_: CodexDeviceSignIn.Unavailable) {
                // The one failure with its own remedy, and the only 404 that means the
                // flow is off rather than "keep polling" — see CodexDeviceSignIn.
                started = null
                DeviceCodeStage.UNAVAILABLE
            } catch (e: Exception) {
                started = null
                detail = e.message ?: e.javaClass.simpleName
                DeviceCodeStage.FAILED
            }
        }
    }

    // CCRM-56 (Provider Identity): the Add-account sheet's ChatGPT row lands here
    // and starts sign-in immediately, matching Claude's beginSignIn() on its own
    // freshly minted card.
    LaunchedEffect(Unit) {
        if (autoStart && !hasToken && started == null) beginDeviceSignIn()
    }

    // The poll. Keyed on the flow's own id so a "Get a new code" restarts it and a
    // recomposition does not; it stops by clearing `started`.
    LaunchedEffect(started?.deviceAuthId) {
        val flow = started ?: return@LaunchedEffect
        while (true) {
            delay(flow.intervalSec * 1000L)
            val poll = try {
                repo.pollDeviceSignIn(flow)
            } catch (_: Exception) {
                // A dropped connection mid-wait is not a dead flow: the 15-minute cap
                // bounds it, so keep polling rather than throwing the code away.
                continue
            }
            when (poll) {
                CodexDeviceSignIn.Poll.Pending -> Unit
                CodexDeviceSignIn.Poll.Expired -> {
                    repo.cancelDeviceSignIn()
                    started = null
                    stage = DeviceCodeStage.EXPIRED
                    break
                }
                is CodexDeviceSignIn.Poll.Denied -> {
                    repo.cancelDeviceSignIn()
                    started = null
                    detail = "HTTP ${poll.status}"
                    stage = DeviceCodeStage.DENIED
                    break
                }
                is CodexDeviceSignIn.Poll.Granted -> {
                    busy = true
                    val result = repo.completeDeviceSignIn(profile, poll)
                    busy = false
                    started = null
                    if (result.message == "OK") {
                        Polling.schedulePeriodic(context, repo.cacheSettings().pollIntervalMinutes())
                        // Done: the sheet closes and the account's own tab takes over.
                        stage = null
                        message = "$label signed in — usage fetched, polling started."
                    } else {
                        detail = result.message
                        stage = DeviceCodeStage.FAILED
                    }
                    bump()
                    break
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    if (!hasToken) {
        Text(
            "Not signed in",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(enabled = !busy, onClick = { beginDeviceSignIn() }) {
            Text("Sign in with a code")
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Shows a short code to type at auth.openai.com — on this phone or any " +
                "other device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            "Last checked: ${Fmt.dayTimeWithAgo(snapshot.lastAttemptAt, use24h)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Added: ${if (addedAt > 0) Fmt.date(addedAt) else "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // No "expires around" line, deliberately (CCRM-57 (Provider Plumbing)): the
        // ~30-day family estimate is Anthropic's, and OpenAI's token family has no
        // fixed life we know of. `refreshExpiresAt` stays 0, which is what every
        // expiry surface — this line, the panel strip, the alert — is gated on.
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { beginDeviceSignIn() }) {
                Text("Sign in with a code")
            }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        val result = repo.refreshNow(profile, manual = true)
                        message = if (result.message == "OK") "Checked — working." else result.message
                        busy = false
                        bump()
                    }
                },
            ) { Text("Refresh") }
            TextButton(
                enabled = !busy,
                onClick = {
                    repo.clearCredentials(profile)
                    message = "$label signed out."
                    bump()
                },
            ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
        }
        RowDivider()
        QuickLinksRow(
            provider = profile.provider,
            onOpenDefault = { openInBrowser(context, it, null) },
            onOpenWithPicker = openWithPicker,
        )
        // No Backup method, paste or QR: those move a *desktop* Claude Code token onto
        // the phone. OpenAI's refresh tokens rotate, and redeeming an imported one
        // invalidates the whole family (`refresh_token_reused`) — the phone mints its
        // own, and there is deliberately no other way in.
    }

    message?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodySmall)
    }

    stage?.let { current ->
        DeviceCodeSheet(
            stage = current,
            started = started,
            detail = detail,
            busy = busy,
            onOpenPage = openWithPicker,
            onRetry = { beginDeviceSignIn() },
            onDismiss = {
                repo.cancelDeviceSignIn()
                started = null
                stage = null
            },
        )
    }
}

/**
 * The device-code sheet (CCRM-54 (ChatGPT Account) part 2), built to
 * `design/provider-identity-wireframe.html` section 5. One bottom sheet, five
 * states, all of them the same three parts: the sentence, the code (when there is
 * one), and the buttons. Copy and the state table live in [DeviceCodeCopy] so both
 * are pinned by a test rather than only by eye.
 *
 * The *done* state is the absence of this sheet — the caller drops it the moment the
 * token lands, and the account's own tab takes over behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceCodeSheet(
    stage: DeviceCodeStage,
    started: CodexDeviceSignIn.Started?,
    detail: String?,
    busy: Boolean,
    /** The same browser picker Claude's sign-in uses — see [rememberBrowserOpener]. */
    onOpenPage: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val showsCode = DeviceCodeCopy.showsCode(stage) && started != null

    // The code's own clock, ticking in seconds because the user is watching it —
    // Fmt.dhm would sit on "14m" for a minute at a time (noted in CCRM-54 part 1).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showsCode, started?.deviceAuthId) {
        while (showsCode) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Scrollable because the 30 sp code plus the buttons can outgrow a short
        // sheet at large font scales, and a clipped "Copy code" is a dead end.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(DeviceCodeCopy.TITLE, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                DeviceCodeCopy.body(stage, detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (showsCode) {
                val flow = started
                Spacer(Modifier.height(16.dp))
                Text(
                    DeviceCodeCopy.INSTRUCTION_PREFIX,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        flow.verifyUrl.removePrefix("https://"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    DeviceCodeCopy.INSTRUCTION_SUFFIX,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                SelectionContainer {
                    Text(
                        flow.userCode,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 30.sp,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Code expires in ${Fmt.mmss(flow.expiresAtMs, now)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        DeviceCodeCopy.WAITING_NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenPage(flow.verifyUrl) }) {
                        Text("Open in browser")
                    }
                    OutlinedButton(onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(flow.userCode))
                    }) { Text("Copy code") }
                }
            }

            if (stage == DeviceCodeStage.STARTING || busy) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }

            DeviceCodeCopy.primaryLabel(stage)?.let { primary ->
                Spacer(Modifier.height(16.dp))
                Button(enabled = !busy, onClick = onRetry) { Text(primary) }
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss) {
                Text(if (showsCode) "Cancel" else "Close")
            }
        }
    }
}

/** Inline "paste the code from the sign-in page" step shown after the browser trip. */
@Composable
private fun SignInCompletion(
    busy: Boolean,
    codeInput: String,
    onCodeChange: (String) -> Unit,
    onPaste: () -> Unit,
    onFinish: () -> Unit,
    onReopen: () -> Unit,
    onCancel: () -> Unit,
) {
    Spacer(Modifier.height(10.dp))
    Text("Finish signing in", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Text(
        "Sign in on the page that opened, then copy the code it shows and paste it here.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = codeInput,
        onValueChange = onCodeChange,
        label = { Text("Paste the sign-in code") },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            TextButton(onClick = onPaste, enabled = !busy) { Text("Paste") }
        },
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(enabled = !busy && codeInput.isNotBlank(), onClick = onFinish) {
            Text("Finish sign-in")
        }
        Spacer(Modifier.width(8.dp))
        TextButton(enabled = !busy, onClick = onReopen) { Text("Reopen page") }
        Spacer(Modifier.weight(1f))
        TextButton(enabled = !busy, onClick = onCancel) { Text("Cancel") }
    }
}

/**
 * CCRM-26 (Quick Links), per provider (CCRM-57 (Provider Plumbing)): the two
 * escapes below an account card's divider — quick escapes, not account actions.
 *
 * The status page goes to the default browser (it is account-independent); the
 * dashboard reuses the sign-in picker, because which browser holds *this
 * profile's* session is the same question either way. A FlowRow rather than a Row
 * so the second label drops to its own line at large font scales instead of
 * wrapping mid-label.
 */
@Composable
private fun QuickLinksRow(
    provider: Provider,
    onOpenDefault: (String) -> Unit,
    onOpenWithPicker: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (link in QuickLinks.forProvider(provider)) {
            TextButton(onClick = {
                if (link.usePicker) onOpenWithPicker(link.url) else onOpenDefault(link.url)
            }) { Text(link.label) }
        }
    }
}

@Composable
private fun PlanChip(plan: String, tier: String?) {
    val color = MaterialTheme.colorScheme.primary
    // "Max 20x" when the tier parses, bare "Max" otherwise (CCRM-38). A tier
    // with no plan renders no chip at all — the caller's gate is on the plan.
    val multiplier = Fmt.tierMultiplier(tier)
    Text(
        plan.replaceFirstChar { it.uppercase() } + (multiplier?.let { " $it" } ?: ""),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun StatusChip(authState: AuthState) {
    val dark = appDark()
    val (label, color) = when (authState) {
        AuthState.REAUTH_NEEDED -> "Needs re-auth" to MaterialTheme.colorScheme.error
        else -> "Active" to if (dark) Color(0xFF81C995) else Color(0xFF188038)
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun PollingSection(repo: UsageRepository) {
    val context = LocalContext.current
    val presets = listOf(5L, 15L, 30L, 60L)
    var interval by remember { mutableLongStateOf(repo.cacheSettings().pollIntervalMinutes()) }
    SectionCard {
        Text("Check usage every", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            presets.forEachIndexed { index, minutes ->
                SegmentedButton(
                    selected = interval == minutes,
                    onClick = {
                        interval = minutes
                        repo.cacheSettings().setPollIntervalMinutes(minutes)
                        Polling.schedulePeriodic(context, minutes)
                        if (minutes < 15) Polling.chainNext(context, minutes)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = presets.size),
                ) { Text("${minutes}m") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (interval < 15) "Short intervals use chained jobs; Android may delay them to save battery."
            else "Applies to all configured profiles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val lastAttempt = repo.profiles().maxOfOrNull { repo.snapshot(it).lastAttemptAt } ?: 0L
        if (lastAttempt > 0) {
            val next = lastAttempt + interval * 60_000
            Text(
                "Next automatic check: " +
                    if (next > System.currentTimeMillis()) "in ~${Fmt.dhm(next)}" else "due now",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * CCRM-56 (Provider Identity), decision 1: the global picker, plus a leading
 * "Per provider" tri-colour swatch — where an install that never touched this
 * picker lands, and a Claude-only account then renders pixel-identical to before
 * this existed. [providerOnlySwatch] draws it; ordinary swatches are otherwise
 * unchanged, minus the two provider-only [Palette] entries (see
 * [Palette.selectableOptions]).
 */
@Composable
private fun ThemeColorPicker(themeName: String, onTheme: (String) -> Unit) {
    val context = LocalContext.current
    val dark = appDark()
    val allOptions = listOf(Palette.PER_PROVIDER, Palette.DYNAMIC) + Palette.selectableOptions.map { it.name }
    for (rowNames in allOptions.chunked(6)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (name in rowNames) {
                val selected = name == themeName
                // The amber/yellow dots need a dark check for contrast.
                val checkTint = if (name == "Amber") Color(0xFF412402) else Color.White
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .then(
                            when (name) {
                                Palette.PER_PROVIDER -> Modifier.background(providerOnlySwatch(dark))
                                Palette.DYNAMIC -> Modifier.background(
                                    if (dark) dynamicDarkColorScheme(context).primary
                                    else dynamicLightColorScheme(context).primary
                                )
                                else -> Modifier.background(Palette.color(name, dark))
                            }
                        )
                        .then(
                            if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        )
                        .clickable { onTheme(name) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "$name selected",
                            tint = checkTint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
    Text(
        "Selected: $themeName" + if (themeName == Palette.DYNAMIC) " (follows your wallpaper)" else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The "Per provider" dot: a sweep of the tracked providers' own colours. Two-way as of
 * 2026-09-08 — CCRM-55 (Antigravity Account) dropped Google, so Antigravity's blue no
 * longer belongs in this sweep.
 */
private fun providerOnlySwatch(dark: Boolean): Brush = Brush.sweepGradient(
    listOf(
        Palette.color(Provider.CLAUDE.themeName, dark),
        Palette.color(Provider.CHATGPT.themeName, dark),
        Palette.color(Provider.CLAUDE.themeName, dark),
    )
)

/**
 * CCRM-56 (Provider Identity), decision 5: "+ Add account" opens this sheet
 * instead of minting a Claude profile directly. Only Claude and ChatGPT are
 * listed — CCRM-55 (Antigravity Account) was dropped 2026-09-08 (Google never
 * exposes real usage outside a live Antigravity session, to anyone), so
 * Antigravity gets no row here, same as Cursor, Copilot or any other
 * out-of-scope provider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(onDismiss: () -> Unit, onPick: (Provider) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Add account", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            AddAccountRow(
                Provider.CLAUDE,
                "Signs in through your browser, then paste the code back — as before.",
                onClick = { onPick(Provider.CLAUDE) },
            )
            Spacer(Modifier.height(14.dp))
            AddAccountRow(
                Provider.CHATGPT,
                "Shows a short code to type at auth.openai.com — on this phone or any other device.",
                onClick = { onPick(Provider.CHATGPT) },
            )
        }
    }
}

@Composable
private fun AddAccountRow(provider: Provider, subtitle: String, onClick: () -> Unit) {
    val dark = appDark()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ProviderMark(provider, size = 28.dp, tint = Palette.color(provider.themeName, dark))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * CCRM-56 (Provider Identity), decision 1: the ⋮ → "Accent colour…" sheet. Same
 * grid as [ThemeColorPicker] minus "Per provider"/"Material You" — Material You
 * is global-only — plus a leading "Provider colour (default)" swatch, drawn in
 * the provider's own colour, that clears the override.
 */
@Composable
private fun AccentColorDialog(
    profile: Profile,
    cache: UsageCache,
    onDismiss: () -> Unit,
    onPicked: () -> Unit,
) {
    val dark = appDark()
    val current = cache.accountAccent(profile)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Accent colour · ${profile.provider.displayName}") },
        text = {
            Column {
                val allNames = listOf<String?>(null) + Palette.selectableOptions.map { it.name }
                for (rowNames in allNames.chunked(6)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        for (name in rowNames) {
                            val selected = name == current
                            val dotColor = name?.let { Palette.color(it, dark) }
                                ?: Palette.color(profile.provider.themeName, dark)
                            val checkTint = if (name == "Amber") Color(0xFF412402) else Color.White
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .then(
                                        if (selected) {
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else Modifier
                                    )
                                    .clickable {
                                        cache.setAccountAccent(profile, name)
                                        onPicked()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = (name ?: "Provider colour (default)") + " selected",
                                        tint = checkTint,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    "Selected: " + (current ?: "Provider colour (default)"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * The UPDATES section (CCRM-28): the auto-check toggle, the manual check button
 * (moved here from the About card), and the outcome line the background check
 * shares with it. Failures only ever surface here — never as a notification.
 */
@Composable
private fun UpdatesCard(cache: UsageCache) {
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

@Composable
private fun AboutCard(debugUnlocked: Boolean, onDebugUnlock: () -> Unit) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    // Fallback message when no email app is configured to take the feedback intent.
    var emailFallback by remember { mutableStateOf<String?>(null) }
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }
    Card {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text("Cooldown", style = MaterialTheme.typography.titleMedium)
            Text(
                "Version $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        if (!debugUnlocked && ++taps >= DEBUG_UNLOCK_TAPS) onDebugUnlock()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            if (debugUnlocked) {
                Text(
                    "Debug tools unlocked until the app is closed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("Made by Robin Richard Rajan", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Built with Claude Code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            // Only button left here — "Check for updates" moved to the UPDATES
            // section above (CCRM-28), so a single Row fits every width class.
            OutlinedButton(onClick = {
                val email = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$FEEDBACK_EMAIL")
                    putExtra(Intent.EXTRA_SUBJECT, "Cooldown feedback (v$versionName)")
                }
                try {
                    context.startActivity(
                        Intent.createChooser(email, "Share feedback")
                    )
                } catch (_: Exception) {
                    // No email app configured — surface the address instead.
                    emailFallback = "Email me at $FEEDBACK_EMAIL"
                }
            }) { Text("Share feedback") }
            Spacer(Modifier.height(12.dp))
            Text(
                "Unofficial. Not affiliated with, endorsed by, or supported by Anthropic or OpenAI. " +
                    "\"Claude\" is a trademark of Anthropic, PBC. \"ChatGPT\" is a trademark of OpenAI.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }

    emailFallback?.let { message ->
        AlertDialog(
            onDismissRequest = { emailFallback = null },
            confirmButton = {
                TextButton(onClick = { emailFallback = null }) { Text("OK") }
            },
            text = { Text(message) },
        )
    }
}

private sealed interface UpdateUi {
    /** A successful check with version details. */
    data class Ok(val info: UpdateInfo) : UpdateUi
    /** A plain message (error, or fallback when no email app is present). */
    data class Message(val text: String) : UpdateUi
}

/**
 * Why the trend chart is or isn't showing. `Projection` binds history points to a
 * window by exact `resets_at` equality, so if the server's `resets_at` drifts
 * mid-window every earlier point orphans and the chart goes quiet. The distinct
 * counts below are the test for that: more than one value for a live window means
 * drift, not missing data.
 */
@Composable
private fun TrendDiagnostics(repo: UsageRepository, use24h: Boolean) {
    SectionCard {
        Text("Trend samples", style = MaterialTheme.typography.bodyLarge)
        for (profile in repo.profiles()) {
            val points = remember(profile) { repo.history().points(profile) }
            val data = repo.snapshot(profile).data
            Spacer(Modifier.height(8.dp))
            Text(
                repo.cacheSettings().profileLabel(profile),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            if (points.isEmpty()) {
                Text(
                    "no history points recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                continue
            }
            val lines = buildList {
                add("history points: ${points.size} · oldest ${Fmt.ago(points.first().at)}")
                val session = data?.session?.resetsAt?.toEpochMilli()
                val weekly = data?.weekly?.resetsAt?.toEpochMilli()
                if (session != null) {
                    val bound = Projection.sessionSamples(points, session, Projection.SESSION_MS).size
                    val distinct = points.map { it.sessionResetAt }.filter { it > 0 }.distinct().size
                    add("5-hour resets_at ${Fmt.dayTime(java.time.Instant.ofEpochMilli(session), use24h)}")
                    add("  bound to it: $bound · distinct in history: $distinct")
                } else add("5-hour: no resets_at in the payload")
                if (weekly != null) {
                    val bound = Projection.weeklySamples(points, weekly, Projection.WEEKLY_MS).size
                    val distinct = points.map { it.weeklyResetAt }.filter { it > 0 }.distinct().size
                    add("7-day resets_at ${Fmt.dayTime(java.time.Instant.ofEpochMilli(weekly), use24h)}")
                    add("  bound to it: $bound · distinct in history: $distinct")
                } else add("7-day: no resets_at in the payload")
            }
            for (line in lines) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "A chart needs 2+ bound samples, 20 min of span, and 1% of movement. " +
                "\"distinct in history\" above 1 for a live window means resets_at moved " +
                "and older points no longer match.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DebugSection(repo: UsageRepository, onAccountsChanged: () -> Unit) {
    var showDebug by remember { mutableStateOf(false) }
    var accountsTick by remember { mutableIntStateOf(0) }
    val profiles = remember(accountsTick) { repo.profiles() }
    var debugProfile by remember { mutableStateOf(profiles.first()) }
    SectionCard {
        // CCRM-54 (ChatGPT Account) part 1: the only way to make a ChatGPT account
        // until CCRM-56 (Provider Identity) builds the real "+ Add account" sheet with
        // its three rows. Debug-gated precisely because it is not the approved design.
        OutlinedButton(onClick = {
            repo.addProfile(provider = Provider.CHATGPT)
            accountsTick++
            onAccountsChanged()
        }) { Text("+ Add ChatGPT account") }
        Spacer(Modifier.height(4.dp))
        Text(
            "Scaffolding for the payload capture. The real Add-account sheet lands in " +
                "CCRM-56; sign in from the new card above with \"Sign in with a code\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        // CCRM-16: the ~30-day family estimate has never been verified against a
        // real expiry — this age readout is how we learn the true number the first
        // time a family dies of old age rather than revocation.
        val signInAges = remember {
            val now = System.currentTimeMillis()
            profiles.joinToString(" · ") { p ->
                val label = repo.cacheSettings().profileLabel(p)
                val added = repo.tokenAddedAt(p)
                when {
                    !repo.hasCredentials(p) || added <= 0 -> "$label —"
                    repo.refreshExpiryEstimated(p) ->
                        "$label ${(now - added) / SignInExpiry.DAY_MS}d " +
                            "(est. ~${OAuthSignIn.ESTIMATED_FAMILY_MS / SignInExpiry.DAY_MS}d)"
                    else -> "$label ${(now - added) / SignInExpiry.DAY_MS}d (exact)"
                }
            }
        }
        Text(
            "Sign-in age: $signInAges",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showDebug = !showDebug }) {
                Text(if (showDebug) "Hide raw response" else "Show last raw response")
            }
            Spacer(Modifier.width(8.dp))
            if (showDebug) {
                // Cycles every account rather than flip-flopping two, or a third account
                // would be unreachable from the dev tools (CCRM-6 (Multi-Account)).
                OutlinedButton(onClick = {
                    val i = profiles.indexOf(debugProfile)
                    debugProfile = profiles[(i + 1).mod(profiles.size)]
                }) { Text(repo.cacheSettings().profileLabel(debugProfile)) }
            }
        }
        if (showDebug) {
            Spacer(Modifier.height(8.dp))
            // Redacted for the same reason the capture button's output is: this box
            // exists to be copied out, and a ChatGPT payload names the account holder
            // (CCRM-54 (ChatGPT Account)). Keys and structure survive, which is all a
            // shape check needs.
            val raw = repo.snapshot(debugProfile).rawJson?.let { AppLog.redactPayload(it) }
                ?: "(nothing cached yet for ${repo.cacheSettings().profileLabel(debugProfile)})"
            SelectionContainer {
                Text(
                    raw,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(12.dp),
                )
            }
            // Key names (never values) of the last sign-in's token response —
            // settles whether `rate_limit_tier` is in ours (CCRM-38 verify-first).
            Spacer(Modifier.height(8.dp))
            Text(
                "Sign-in token keys: " +
                    (repo.signInTokenKeys(debugProfile) ?: "(no native sign-in recorded yet)"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    EndpointProbe(repo)
}

/**
 * Paths worth trying first, in order. The first is a control: it is the endpoint we
 * already read successfully, so a non-200 there means the probe itself is broken and
 * nothing below it can be trusted. `bootstrap` is included because the org uuid the
 * balance path needs appears nowhere in our own payload.
 */
private val PROBE_PRESETS = listOf(
    "/api/oauth/usage",
    "/api/bootstrap",
    "/api/organizations",
    "/api/account",
)

/**
 * Endpoint probe (CCBG-6). GETs a path on an allowlisted host with a profile's token and
 * shows status + body.
 *
 * It exists because the credit **balance** the Claude app displays is not in the payload
 * we read: its APK fetches `organizations/{uuid}/usage` on `api.claude.ai`, while we read
 * `/api/oauth/usage` on `api.anthropic.com`, where `spend.balance` is permanently null.
 * The open question is whether our subscription OAuth token authenticates there at all.
 *
 * Deliberately GET-only, host-allowlisted, and non-caching — see `ApiClient.probe` and
 * `UsageRepository.probeEndpoint`. Request headers are never rendered, because the output
 * is meant to be copied out and the bearer token must not ride along with it.
 */
@Composable
private fun EndpointProbe(repo: UsageRepository) {
    val scope = rememberCoroutineScope()
    val profiles = remember { repo.profiles() }
    var profile by remember { mutableStateOf(profiles.first()) }
    var host by remember { mutableStateOf(ApiClient.ProbeHost.CLAUDE_AI) }
    var path by remember { mutableStateOf(PROBE_PRESETS.first()) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    SectionCard {
        Text("Endpoint probe", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "GET only, on an allowlisted host, with this profile's token. Nothing is " +
                "parsed or cached. Skim the body for an org id or email before sharing it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                profile = profiles[(profiles.indexOf(profile) + 1).mod(profiles.size)]
            }) { Text(repo.cacheSettings().profileLabel(profile)) }
            Spacer(Modifier.width(8.dp))
            // Cycles the whole allowlist: with CCRM-54 (ChatGPT Account) it has three
            // entries, and a flip-flop would leave the third unreachable.
            OutlinedButton(onClick = {
                val hosts = ApiClient.ProbeHost.entries
                host = hosts[(hosts.indexOf(host) + 1).mod(hosts.size)]
            }) { Text(host.origin.removePrefix("https://")) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("Path") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        FlowRow {
            PROBE_PRESETS.forEach { preset ->
                FilterChip(
                    selected = path == preset,
                    onClick = { path = preset },
                    label = { Text(preset, style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !running,
            onClick = {
                scope.launch {
                    running = true
                    result = "Probing ${host.origin}$path …"
                    val resp = repo.probeEndpoint(profile, host, path)
                    result = when (resp) {
                        null -> "No credentials for ${profile.label}"
                        else -> "GET ${host.origin}$path\nHTTP ${resp.code}\n\n${resp.body}"
                    }
                    running = false
                }
            },
        ) { Text(if (running) "Probing…" else "Probe") }
        result?.let { text ->
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun NoteCard(text: String, positive: Boolean) {
    val dark = appDark()
    val tint = if (positive) {
        if (dark) Color(0xFF81C995) else Color(0xFF188038)
    } else {
        if (dark) Color(0xFFFDD663) else Color(0xFF9A6700)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            modifier = Modifier.padding(12.dp),
        )
    }
}

// --- shared bits ---

@Composable
private fun ResetModeRow(label: String, profile: Profile, window: String, cache: UsageCache) {
    val context = LocalContext.current
    var mode by remember(profile) { mutableStateOf(cache.resetPingMode(profile, window)) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        val options = listOf(
            UsageCache.RESET_OFF to "Off",
            UsageCache.RESET_SMART to "If busy",
            UsageCache.RESET_ALWAYS to "Always",
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, text) ->
                SegmentedButton(
                    selected = mode == value,
                    onClick = {
                        mode = value
                        cache.setResetPingMode(profile, window, value)
                        com.robin.claudeusage.notify.PinnedNotification.update(context, cache)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(text) }
            }
        }
    }
}

/**
 * The app log (CCRM-34 (Diagnostics Log)), grown from the window-ping trace:
 * polls, reset pings and token renewals, levelled and categorised.
 * Shows the tail in-app; Share hands the recent lines to any mail/chat app,
 * which is the whole point — "paste your log" is the only realistic way to
 * diagnose someone else's phone. Never contains tokens, headers, or the
 * code_verifier (hard rule in `AppLog`).
 */
@Composable
private fun AppLogCard(cache: UsageCache) {
    val context = LocalContext.current
    var tail by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var level by remember { mutableStateOf(cache.logLevel()) }

    LaunchedEffect(refreshTick) {
        tail = withContext(Dispatchers.IO) {
            try {
                val f = AppLog.file(context)
                if (f.exists()) f.readLines().takeLast(12) else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    SectionCard {
        Text("App log", style = MaterialTheme.typography.bodyLarge)
        Text(
            "What the app does in the background — polls, alerts, sign-in renewals. " +
                "Share it when reporting a problem; it never contains " +
                "tokens. Pull the full file with:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "adb pull ${AppLog.file(context).absolutePath}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, text) in listOf("info" to "Info", "debug" to "Debug")) {
                FilterChip(
                    selected = level == value,
                    onClick = {
                        level = value
                        cache.setLogLevel(value)
                    },
                    label = { Text(text) },
                )
            }
        }
        Text(
            if (level == "debug")
                "Debug adds routine successes — turn it on only while chasing something."
            else "Info records failures, alerts and decisions; routine successes stay out.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        if (tail.isEmpty()) {
            Text(
                "Nothing logged yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            for (line in tail) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refreshTick++ }) { Text("Reload") }
            OutlinedButton(onClick = {
                // Recent lines as plain text — no FileProvider, nothing the
                // receiving app has to know how to open.
                val body = try {
                    AppLog.file(context).readLines().takeLast(400).joinToString("\n")
                } catch (_: Exception) {
                    ""
                }
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Cooldown app log")
                    .putExtra(Intent.EXTRA_TEXT, body.ifEmpty { "(log is empty)" })
                context.startActivity(Intent.createChooser(send, "Share app log"))
            }) { Text("Share") }
            OutlinedButton(onClick = {
                AppLog.clear(context)
                refreshTick++
            }) { Text("Clear") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card { Column(Modifier.fillMaxWidth().padding(16.dp), content = content) }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val dim = if (enabled) 1f else 0.38f
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
