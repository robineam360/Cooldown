package com.robin.claudeusage

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import android.text.format.DateFormat
import com.robin.claudeusage.alerts.Alerts
import com.robin.claudeusage.data.ErrorKind
import com.robin.claudeusage.data.Profile
import com.robin.claudeusage.data.ProfileRegistry
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.data.QuickLinks
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.data.UsageRepository
import com.robin.claudeusage.ui.ChartColumnMaxWidth
import com.robin.claudeusage.ui.ContentColumn
import com.robin.claudeusage.ui.ContentMaxWidth
import com.robin.claudeusage.ui.Fmt
import com.robin.claudeusage.ui.LocalWidthClass
import com.robin.claudeusage.ui.Palette
import com.robin.claudeusage.ui.ProviderMark
import com.robin.claudeusage.ui.ProviderTabLabel
import com.robin.claudeusage.ui.ProvideWidthClass
import com.robin.claudeusage.ui.Rooms
import com.robin.claudeusage.ui.WideMaxWidth
import com.robin.claudeusage.ui.Motion
import com.robin.claudeusage.ui.LocalAppDark
import com.robin.claudeusage.ui.resolve24h
import com.robin.claudeusage.ui.resolveDark
import com.robin.claudeusage.ui.twoPane
import com.robin.claudeusage.work.Polling
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.robin.claudeusage.data.CardId
import com.robin.claudeusage.ui.ChartOrientation
import com.robin.claudeusage.ui.ChartSize
import com.robin.claudeusage.ui.Density

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retires the notification channels CCRM-61 (Settings Diet) left without a
        // poster, so an upgraded install stops offering to configure alerts that no
        // longer exist. One-shot, guarded inside.
        Alerts.retireOldChannels(this, UsageCache(this))
        val startProfile =
            ProfileRegistry(this).resolve(intent?.getStringExtra("profile"))
        // CCRM-33 (App Shortcuts): the "Refresh now" shortcut opens the app with
        // this extra — a manual poll of every signed-in account, the same path
        // the pinned notification's Refresh action takes.
        if (intent?.getBooleanExtra("refresh", false) == true) {
            Polling.refreshOnce(this, manual = true)
        }
        // Republish so shortcut labels track renamed profiles even when the
        // rename happened on another device restore or a cleared cache.
        Shortcuts.publish(this)
        // Measured at the root so every screen sees the same window width, and so
        // it re-measures on a fold/unfold without the activity being torn down.
        setContent { ProvideWidthClass { App(startProfile) } }
    }
}

private enum class Screen { MAIN, SETTINGS, HISTORY }

/**
 * CCRM-6 (Multi-Account): profile tab strips stay a fixed [TabRow] up to this many accounts
 * and become a [ScrollableTabRow] beyond it. Shared by the main screen and the History
 * screen so the two strips can never disagree. See the wireframe's state 5d–5f for the
 * measurement this comes from.
 */
internal const val FIXED_TAB_LIMIT = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(startProfile: Profile) {
    val context = LocalContext.current
    val repo = remember { UsageRepository(context) }
    val cache = remember { repo.cacheSettings() }
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    // Deliberately not persisted: the debug easter egg re-locks on every launch.
    var debugUnlocked by remember { mutableStateOf(false) }
    var themeName by remember { mutableStateOf(cache.themeColorName()) }
    // CCRM-56 (Provider Identity): the account whose tab is showing on Main or
    // History — MaterialTheme.primary follows it there, so a ChatGPT tab tints
    // the whole shell green even when the global theme is "Per provider".
    var selectedProfile by remember { mutableStateOf(startProfile) }
    // CCRM-29 (Display Mode): the two three-way modes, hoisted so the Settings
    // chips recompose the whole app; the booleans every render site reads are
    // derived below.
    var themeMode by remember { mutableStateOf(cache.themeMode()) }
    var timeFormat by remember { mutableStateOf(cache.timeFormat()) }
    val use24h = resolve24h(timeFormat, DateFormat.is24HourFormat(context))
    // CCRM-22 (Used or Left): hoisted like use24h so flipping the chips in
    // Settings recomposes every readout behind them.
    var usageLeft by remember { mutableStateOf(cache.usageLeft()) }
    // CCRM-23 (Reset Display): same hoisting for which reset form leads.
    var resetClock by remember { mutableStateOf(cache.resetClock()) }
    // Hoisted like use24h so flipping the toggle in Settings recomposes the bars
    // behind it — CCRM-43 (Bar Pace Marks)'s one toggle since CCRM-61 (Settings
    // Diet), shared with the pinned notification's own red.
    var showOverPace by remember { mutableStateOf(cache.showOverPace()) }
    // CCRM-72 (Main Screen Redesign) / CCRM-75 (Chart Height) / CCRM-77 (Transposed
    // Chart): the three global Appearance chips, hoisted exactly like showOverPace so
    // flipping one in Settings redraws every card behind it.
    var density by remember { mutableStateOf(cache.density()) }
    var chartSize by remember { mutableStateOf(cache.chartSize()) }
    var chartOrientation by remember { mutableStateOf(cache.chartOrientation()) }
    // CCRM-25 (Card Layout): bumped by the layout sheet so the main screen re-reads the
    // account's card order the moment it changes, without waiting for a poll.
    var layoutTick by remember { mutableIntStateOf(0) }
    var layoutMenuOpen by remember { mutableStateOf(false) }
    var showLayoutSheet by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    // Ticks every few seconds so "updated Xm ago" and background results stay fresh.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            tick++
        }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        if (repo.configuredProfiles().isNotEmpty()) {
            Polling.schedulePeriodic(context, cache.pollIntervalMinutes())
            val stalest = repo.configuredProfiles()
                .minOfOrNull { repo.snapshot(it).fetchedAt } ?: 0L
            if (System.currentTimeMillis() - stalest > 180_000) {
                Polling.refreshOnce(context, manual = false)
            }
        }
    }

    fun goBack() {
        screen = Screen.MAIN
    }

    // System back walks the screen stack instead of exiting the app.
    BackHandler(enabled = screen != Screen.MAIN) { goBack() }

    // CCRM-25 (Card Layout), wireframe §9: the cards the selected account actually
    // reports — the ⋮'s own gate, and the set the layout sheet arranges (and holds its
    // never-blank invariant) over. Read here rather than in the two places that need it
    // so they can't disagree about what the account has.
    val presentCards = remember(selectedProfile, tick) {
        dataCards(repo.snapshot(selectedProfile).data, cache.creditsVisible(selectedProfile))
    }

    val dark = resolveDark(themeMode, isSystemInDarkTheme())
    // A forced theme diverges from the system theme that the manifest's
    // windowLightStatusBar attribute follows (CCBG-13), so the icons are set
    // programmatically from the resolved mode.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? android.app.Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !dark
            }
        }
    }
    // CCRM-56 (Provider Identity), decision 1: Main and History follow the
    // selected tab's own resolved accent; Settings (and the guide) show the
    // global choice, with "Per provider" resolving to the plain default there —
    // there is no single account to resolve it against on that screen.
    val effectiveAccent = when (screen) {
        Screen.MAIN, Screen.HISTORY -> Palette.accentName(cache, selectedProfile)
        else -> themeName.takeIf { it != Palette.PER_PROVIDER } ?: Palette.DEFAULT
    }
    val baseScheme = when {
        effectiveAccent == Palette.DYNAMIC && dark -> dynamicDarkColorScheme(context)
        effectiveAccent == Palette.DYNAMIC -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Palette.color(effectiveAccent, true),
            onPrimary = Color(0xFF1F1F1F),
        )
        else -> lightColorScheme(
            primary = Palette.color(effectiveAccent, false),
            onPrimary = Color.White,
        )
    }
    // CCRM-60 (Dual Identity), decision 4: "two rooms" — Main and History, the same
    // two screens that already follow the selected tab's accent (CCRM-56 (Provider
    // Identity) decision 1), also take that account's surface tint and card tint.
    // Settings and the Guide keep Material's defaults, so this is scoped to those
    // two screens only. Applied as a `copy()` on top of whichever branch above ran
    // — the DYNAMIC branch's own scheme is otherwise untouched — so every branch
    // gets the room's tokens the same way instead of threading them through three
    // different constructor calls.
    val scheme = if (screen == Screen.MAIN || screen == Screen.HISTORY) {
        val room = Rooms.forProvider(selectedProfile.provider)
        val roomSurface = Color(if (dark) room.surfaceDark else room.surfaceLight)
        val roomCard = Color(if (dark) room.cardDark else room.cardLight)
        baseScheme.copy(
            surface = roomSurface,
            background = roomSurface,
            surfaceContainer = roomCard,
            surfaceContainerLow = roomCard,
            surfaceContainerHigh = roomCard,
            surfaceContainerHighest = roomCard,
            surfaceVariant = roomCard,
        )
    } else {
        baseScheme
    }

    CompositionLocalProvider(LocalAppDark provides dark) {
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            // CCRM-60 (Dual Identity), decision 3: the main screen only —
                            // Settings, History and the Guide keep a plain text title.
                            if (screen == Screen.MAIN) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ProviderMark(
                                        Provider.CLAUDE,
                                        size = 20.dp,
                                        tint = Palette.color(Provider.CLAUDE.themeName, dark),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    ProviderMark(
                                        Provider.CHATGPT,
                                        size = 20.dp,
                                        tint = Palette.color(Provider.CHATGPT.themeName, dark),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Cooldown")
                                }
                            } else {
                                Text(
                                    when (screen) {
                                        Screen.SETTINGS -> "Settings"
                                        Screen.HISTORY -> "Usage history"
                                        Screen.MAIN -> "Cooldown"
                                    }
                                )
                            }
                        },
                        navigationIcon = {
                            if (screen != Screen.MAIN) {
                                IconButton(onClick = { goBack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            if (screen == Screen.MAIN) {
                                IconButton(onClick = { screen = Screen.HISTORY }) {
                                    Icon(Icons.Filled.DateRange, contentDescription = "Usage history")
                                }
                                IconButton(onClick = { screen = Screen.SETTINGS }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                }
                                // CCRM-25 (Card Layout), wireframe §9: the entry point
                                // shows only once the selected account has two or more
                                // cards to arrange — with one, the never-blank invariant
                                // leaves nothing to hide or fold.
                                if (presentCards.size >= 2) {
                                    Box {
                                        IconButton(onClick = { layoutMenuOpen = true }) {
                                            Icon(
                                                Icons.Filled.MoreVert,
                                                contentDescription = "More options",
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = layoutMenuOpen,
                                            onDismissRequest = { layoutMenuOpen = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Main screen layout") },
                                                onClick = {
                                                    layoutMenuOpen = false
                                                    showLayoutSheet = true
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                },
            ) { innerPadding ->
                // History lays its own content out in columns when there's room, so
                // it gets the wider cap — but only when it'll actually use it, or the
                // cap would just stretch one column. The guide is prose and stays at
                // one readable measure at any size. Settings computes its own per-tab
                // width below — a HorizontalPager can't live inside this column at
                // all, let alone a capped one (see the Screen.SETTINGS branch).
                val contentWidth = when (screen) {
                    Screen.HISTORY -> if (LocalWidthClass.current.twoPane) WideMaxWidth else ContentMaxWidth
                    else -> ContentMaxWidth
                }
                when (screen) {
                    Screen.MAIN ->
                        ProfileTabs(
                            repo, use24h, usageLeft, resetClock, showOverPace,
                            density, chartSize, chartOrientation, layoutTick, tick,
                            startProfile, { screen = Screen.SETTINGS },
                            Modifier.padding(innerPadding),
                            onProfileChange = { selectedProfile = it },
                        )
                    // CCRM-61 (Settings Diet): its own branch, not the shared
                    // ContentColumn below — a HorizontalPager needs a bounded height,
                    // which a `verticalScroll` column can't give it. SettingsScreen
                    // fills the size it's handed and lays out its own tab bar and
                    // per-page columns instead.
                    Screen.SETTINGS ->
                        SettingsScreen(
                            repo = repo,
                            use24h = use24h,
                            themeMode = themeMode,
                            onThemeMode = { themeMode = it },
                            timeFormat = timeFormat,
                            onTimeFormat = { timeFormat = it },
                            usageLeft = usageLeft,
                            onUsageLeft = { usageLeft = it },
                            resetClock = resetClock,
                            onResetClock = { resetClock = it },
                            showOverPace = showOverPace,
                            onShowOverPace = { showOverPace = it },
                            density = density,
                            onDensity = { density = it },
                            chartSize = chartSize,
                            onChartSize = { chartSize = it },
                            chartOrientation = chartOrientation,
                            onChartOrientation = { chartOrientation = it },
                            themeName = themeName,
                            onTheme = { themeName = it },
                            debugUnlocked = debugUnlocked,
                            onDebugUnlock = { debugUnlocked = true },
                            modifier = Modifier.padding(innerPadding),
                        )
                    else -> ContentColumn(
                        modifier = Modifier.padding(innerPadding),
                        maxWidth = contentWidth,
                    ) {
                        Spacer(Modifier.height(8.dp))
                        HistoryScreen(repo, tick, onProfileChange = { selectedProfile = it })
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            // CCRM-25 (Card Layout) + CCRM-35 (Layout Reset): the sheet the top bar's ⋮
            // opens, for the account whose tab is showing.
            if (showLayoutSheet) {
                LayoutSheet(
                    profile = selectedProfile,
                    cache = cache,
                    present = presentCards,
                    onChanged = { layoutTick++ },
                    onDismiss = { showLayoutSheet = false },
                )
            }
        }
    }
    }
}

@Composable
private fun ProfileTabs(
    repo: UsageRepository,
    use24h: Boolean,
    usageLeft: Boolean,
    resetClock: Boolean,
    showOverPace: Boolean,
    density: Density,
    chartSize: ChartSize,
    chartOrientation: ChartOrientation,
    layoutTick: Int,
    tick: Int,
    startProfile: Profile,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
    onProfileChange: (Profile) -> Unit,
) {
    // CCRM-6 (Multi-Account): only accounts with a token get a tab. Someone with one
    // account sees no strip at all and gains its ~48 dp; a new account appears the moment
    // its token lands. Signing in stays in Settings, so a tab is never the route to it.
    // At zero we fall back to the registry's first account, which shows its own empty state.
    val profiles = repo.configuredProfiles().ifEmpty { listOf(repo.registry().first()) }
    // CCRM-71 (Account Order): the selected tab is keyed on the account's stable
    // `profile.key`, never on its position — a HorizontalPager only understands page
    // *indices*, so the index the key currently resolves to is recomputed on every
    // render and the pager is nudged onto it, rather than trusting whatever index it
    // was last sitting on. Without this, reordering accounts in the sheet would leave
    // the pager on the same raw index and silently swap which account is on screen.
    var selectedKey by rememberSaveable { mutableStateOf(startProfile.key) }
    // Falls back to the first configured profile — the same fallback `profiles` itself
    // already falls back to (`registry().first()`) when nothing else resolves — when the
    // selected key has been removed or hasn't been assigned yet.
    val selectedIndex = profiles.indexOfFirst { it.key == selectedKey }.let { if (it >= 0) it else 0 }
    // One profile at a time at every width, tabs and swipe included. A wide window used
    // to split into two side-by-side profile panes, on the theory that both accounts at
    // once was the point — but each pane then drew a chart no wider than the one on the
    // cover screen, so unfolding cost a gesture and bought nothing. The question you
    // open this app with is how much is left on the account you're about to spend, and
    // both-at-once already has a better home in the always-on notification, CCRM-62
    // (Duet Notification).
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { profiles.size },
    )
    // Keeps the pager's raw page index honest whenever the *key's* resolved index moves
    // out from under it — a reorder, an account signing out, or the list shrinking —
    // without waiting for a swipe. A no-op whenever the pager is already there, which is
    // the case for every tap/swipe-driven change (those update selectedKey below, which
    // recomputes selectedIndex right back to pagerState.currentPage).
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }
    // CCRM-56 (Provider Identity): reports the visible tab's account up so the app
    // shell can theme from it — a swipe counts the same as a tab tap. Also the other
    // direction of the key/index sync above: this is what turns a user-driven page
    // change into the new selectedKey.
    LaunchedEffect(pagerState.currentPage, profiles) {
        profiles.getOrNull(pagerState.currentPage.coerceIn(0, profiles.lastIndex))?.let {
            selectedKey = it.key
            onProfileChange(it)
        }
    }
    val scope = rememberCoroutineScope()
    // Read inside the click, never captured at composition: the user can flip the
    // system animation setting while the app is open, and the very next tab press
    // must honour the new answer.
    val motionContext = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        // One tab is no choice, so the strip disappears entirely rather than showing a lone
        // tab with nothing to switch to.
        if (profiles.size > 1) {
            // Clamped at the call site as well as in the effect above: the effect runs
            // after this composition, so for one frame after an account disappears the
            // pager's page can still be past the end — and TabRow's indicator indexes
            // straight into its tab positions with whatever it is handed.
            val selected = pagerState.currentPage.coerceIn(0, profiles.lastIndex)
            // The strip's own text/indicator colour stays neutral, never the swiped-to
            // profile's accent — MaterialTheme.colorScheme.primary is that accent, and
            // it flips mid-drag (CCRM-56 (Provider Identity)'s onProfileChange effect
            // above tracks pagerState.currentPage), so leaving Tab/TabRow at their
            // defaults meant every label's hue crossfaded during a swipe, not just the
            // selected one. Only the mark icon keeps its own per-profile tint.
            val tabContentColor = MaterialTheme.colorScheme.onSurface
            val tabs: @Composable () -> Unit = {
                profiles.forEachIndexed { index, profile ->
                    Tab(
                        selected = selected == index,
                        onClick = {
                            scope.launch {
                                // The zero-duration limit of the same page turn — identical
                                // landing, no travel — per CCRM-32 (Reduce Motion).
                                if (Motion.reduced(Motion.scale(motionContext))) {
                                    pagerState.scrollToPage(index)
                                } else {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        },
                        text = { ProviderTabLabel(repo.cacheSettings(), profile) },
                        selectedContentColor = tabContentColor,
                        unselectedContentColor = tabContentColor.copy(alpha = 0.6f),
                    )
                }
            }
            // CCRM-6 (Multi-Account): fixed up to three, scrollable at four or more, at
            // every width. Three tabs get ~133 dp on a 400 dp screen and a 16-character
            // label needs ~110, so it fits; four would get 82 dp on the cover screen and
            // every label — including the selected one — would truncate. One rule at both
            // widths deliberately, so the strip doesn't change shape when the phone unfolds.
            // The labels stay neutral to avoid the mid-swipe crossfade (see
            // tabContentColor above); the indicator now follows the accent by
            // decision, CCRM-60 (Dual Identity) — one of the two rooms' tells,
            // alongside the surface tint and the serif headline.
            val tabIndicator: @Composable (List<TabPosition>) -> Unit = { tabPositions ->
                TabRowDefaults.run {
                    SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selected]),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (profiles.size <= FIXED_TAB_LIMIT) {
                TabRow(
                    selectedTabIndex = selected,
                    contentColor = tabContentColor,
                    indicator = tabIndicator,
                ) { tabs() }
            } else {
                ScrollableTabRow(
                    selectedTabIndex = selected,
                    edgePadding = 0.dp,
                    contentColor = tabContentColor,
                    indicator = tabIndicator,
                ) { tabs() }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            ContentColumn(maxWidth = ChartColumnMaxWidth) {
                Spacer(Modifier.height(16.dp))
                ProfileScreen(
                    repo, profiles[page], use24h, usageLeft, resetClock, showOverPace,
                    density, chartSize, chartOrientation, layoutTick, tick, onOpenSettings,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    repo: UsageRepository,
    profile: Profile,
    use24h: Boolean,
    usageLeft: Boolean,
    resetClock: Boolean,
    showOverPace: Boolean,
    /** CCRM-72 (Main Screen Redesign): Comfortable / Compact, hoisted like [usageLeft]. */
    density: Density,
    /** CCRM-75 (Chart Height) and CCRM-77 (Transposed Chart), hoisted the same way. */
    chartSize: ChartSize,
    chartOrientation: ChartOrientation,
    /** Bumped by the layout sheet (CCRM-25 (Card Layout)) so the order re-reads. */
    layoutTick: Int,
    tick: Int,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cache = remember { repo.cacheSettings() }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var snapshot by remember(profile) { mutableStateOf(repo.snapshot(profile)) }
    LaunchedEffect(tick, profile) { snapshot = repo.snapshot(profile) }
    val data = snapshot.data
    // Re-read the history file only when a new fetch lands, not on every tick.
    val history = remember(profile, snapshot.fetchedAt) { repo.history().points(profile) }
    // CCRM-60 (Dual Identity), decision 4: the Claude room's headline percentages
    // set in the system serif; every other room's stay the default sans. Nothing
    // else on the card changes.
    val serifHeadline = Rooms.forProvider(profile.provider).serifHeadline
    val compact = density == Density.COMPACT

    if (!repo.hasCredentials(profile)) {
        val label = cache.profileLabel(profile)
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("No $label account yet", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                // CCRM-57 (Provider Plumbing): the two sign-ins are different flows
                // with different buttons, so this can't name one of them for both.
                Text(
                    if (profile.provider == Provider.CLAUDE) {
                        "Open Settings and tap \"Sign in on this phone\" for the " +
                            "$label account. It opens Claude's sign-in in your " +
                            "browser — no computer needed."
                    } else {
                        "Open Settings and tap \"Sign in with a code\" for the " +
                            "$label account. It shows a short code to type at " +
                            "auth.openai.com — on this phone or any other device."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    if (data == null) {
        // CCBG-27 (Free Plan 403): a plan that reports no usage has nothing to refresh
        // for; the notice below the fold says why, so no "try Refresh" here.
        if (snapshot.lastStatusKind != ErrorKind.PLAN.key) {
            Text("No data yet — try Refresh now.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }
    } else {
        // Pay-as-you-go credits. Shown once there is either a cap or real spend to
        // report — an account with neither has no credit budget, and "$0.00 of $0.00"
        // tells nobody anything. The *limit* is not the existence test: switching the
        // monthly cap off leaves real spend with no ceiling (CCBG-9).
        val creditsVisible = cache.creditsVisible(profile)
        val credits = data.credits?.takeIf { it.isReportable && creditsVisible }
        // CCRM-56 (Provider Identity), decision 6: a window the account does not
        // have is not shown at all — no placeholder card, no dash — on any
        // surface, Claude included. ChatGPT's Plus/Pro accounts lack a 5-hour
        // window since OpenAI lifted it 2026-07-12; the rule applies uniformly
        // rather than special-casing the provider.
        val present = dataCards(data, creditsVisible)

        // CCRM-25 (Card Layout): order, visibility and the fold, per account. Cards with
        // no data drop out here exactly as they always did — the layout only ever
        // arranges what the account actually reports.
        val layout = remember(profile, layoutTick) { cache.layout(profile) }
        val shown = layout.order.filter { it in present && it !in layout.hidden }
        val mainCards = shown.filter { it !in layout.more }
        val foldedCards = shown.filter { it in layout.more }
        // Session-only, collapsed by default (wireframe §7e): folding a card is the
        // persistent decision, peeking under the disclosure is not.
        var moreOpen by remember(profile) { mutableStateOf(false) }

        // CCRM-72 (Main Screen Redesign): the open/folded state of each Compact card,
        // remembered per card per account. Held as an immutable Set and replaced whole
        // on every toggle — a MutableSet in a MutableState would let a mutation land
        // without a recomposition (lint's MutableCollectionMutableState).
        var expandedCards by remember(profile) {
            mutableStateOf(CardId.entries.filter { cache.expanded(profile, it) }.toSet())
        }
        // CCRM-25 (Card Layout), wireframe §7f: a card behind More always renders
        // collapsed to begin with — the disclosure is a peek, not a second screen — so
        // its open state is session-only and deliberately never reads or writes the
        // persisted per-card flag above.
        var foldedOpen by remember(profile) { mutableStateOf(emptySet<CardId>()) }
        val isOpen: (CardId, Boolean) -> Boolean = { id, folded ->
            if (folded) id in foldedOpen else id in expandedCards
        }
        val toggle: (CardId, Boolean) -> Unit = { id, folded ->
            if (folded) {
                foldedOpen = if (id in foldedOpen) foldedOpen - id else foldedOpen + id
            } else {
                val open = id !in expandedCards
                expandedCards = if (open) expandedCards + id else expandedCards - id
                cache.setExpanded(profile, id, open)
            }
        }

        // CCRM-73 (Model Cap Chart): which series the 7-day chart draws, per account and
        // persisted — collapsing or expanding the card never changes it.
        var storedCap by remember(profile) { mutableStateOf(cache.weeklyChart(profile)) }
        val selectedCap = weeklyChartSelection(storedCap, data.modelCaps.map { it.modelName })

        val cardBody: @Composable (CardId, Boolean) -> Unit = { id, folded ->
            // A card under "More" draws in its Compact form whatever the density —
            // the disclosure is a peek, not a second full screen (wireframe §7f).
            val cardCompact = compact || folded
            when (id) {
                CardId.SESSION -> data.session?.let { w ->
                    SessionCard(
                        window = w,
                        history = history,
                        compact = cardCompact,
                        expanded = isOpen(id, folded),
                        onToggle = { toggle(id, folded) },
                        use24h = use24h,
                        usageLeft = usageLeft,
                        resetClock = resetClock,
                        showOverPace = showOverPace,
                        serifHeadline = serifHeadline,
                        chartSize = chartSize,
                        chartOrientation = chartOrientation,
                    )
                }
                CardId.WEEKLY -> WeeklyCard(
                    data = data,
                    history = history,
                    compact = cardCompact,
                    expanded = isOpen(id, folded),
                    onToggle = { toggle(id, folded) },
                    selectedCap = selectedCap,
                    onSelectCap = { name ->
                        storedCap = name
                        cache.setWeeklyChart(profile, name)
                    },
                    use24h = use24h,
                    usageLeft = usageLeft,
                    resetClock = resetClock,
                    showOverPace = showOverPace,
                    serifHeadline = serifHeadline,
                    chartSize = chartSize,
                    chartOrientation = chartOrientation,
                )
                CardId.CREDITS -> credits?.let { CreditsCard(it, usageLeft, serifHeadline) }
            }
        }

        mainCards.forEachIndexed { index, id ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            cardBody(id, false)
        }
        if (foldedCards.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MoreDisclosure(moreOpen, foldedCards.size) { moreOpen = !moreOpen }
            if (moreOpen) {
                foldedCards.forEach { id ->
                    Spacer(Modifier.height(8.dp))
                    cardBody(id, true)
                }
            }
        }

        // CCRM-56 (Provider Identity), decision 6: with no window and no credits,
        // one honest line rather than three empty cards.
        if (present.isEmpty()) {
            Text(
                "This account reports no usage windows right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    // CCRM-72 (Main Screen Redesign), wireframe §2: one status line in place of the
    // Refresh button and the "Last success / Last attempt" pair.
    StatusLine(
        fetchedAt = snapshot.fetchedAt,
        lastAttemptAt = snapshot.lastAttemptAt,
        lastOk = snapshot.lastStatus == "OK",
        refreshing = refreshing,
        // Recomputed every tick so "Checked 14m ago" ages without a new fetch.
        now = remember(tick) { System.currentTimeMillis() },
    ) {
        scope.launch {
            refreshing = true
            message = null
            val result = repo.refreshNow(profile, manual = true)
            message = if (result.message == "OK") null else result.message
            refreshing = false
            snapshot = repo.snapshot(profile)
        }
    }
    message?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
    if (snapshot.lastStatus != "OK") {
        Spacer(Modifier.height(6.dp))
        ErrorNotice(
            snapshot = snapshot,
            provider = profile.provider,
            backoffUntil = cache.backoffUntil(profile),
            use24h = use24h,
            onOpenSettings = onOpenSettings,
        )
    }
}

/**
 * CCRM-27 (Error Taxonomy): the typed failure as a tinted notice row — copy that
 * names the fix up top, the raw status as the small evidence line, and the one
 * action that actually helps for this kind. Renders *beside* the retained
 * last-good cards above, never in place of them.
 */
@Composable
private fun ErrorNotice(
    snapshot: com.robin.claudeusage.data.Snapshot,
    /** CCRM-57 (Provider Plumbing): the copy and the status link both key on it. */
    provider: com.robin.claudeusage.data.Provider,
    backoffUntil: Long,
    use24h: Boolean,
    onOpenSettings: () -> Unit,
) {
    val kind = ErrorKind.fromKey(snapshot.lastStatusKind)
    val context = LocalContext.current
    val tint = if (kind.severe) MaterialTheme.colorScheme.error else barFill(95.0)
    val detail = buildString {
        append(snapshot.lastStatus)
        // The retry moment comes from the backoff clock the app already keeps —
        // printed, not invented (CCRM-26 (Quick Links)'s honesty rule).
        if (kind == ErrorKind.RATE_LIMITED && backoffUntil > System.currentTimeMillis()) {
            append(" · next try ~${Fmt.timeOnly(Instant.ofEpochMilli(backoffUntil), use24h)}")
        }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.11f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                kind.title(provider),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = tint.copy(alpha = 0.8f),
            )
            when (kind) {
                // CCRM-26 (Quick Links): the "is it me or is it them" escape stays,
                // now scoped to the kinds where it answers the question.
                ErrorKind.NETWORK, ErrorKind.SERVER ->
                    TextButton(onClick = {
                        openInBrowser(context, QuickLinks.statusUrl(provider), null)
                    }) {
                        Text(QuickLinks.statusLabel(provider))
                    }
                ErrorKind.AUTH ->
                    TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                ErrorKind.INVALID_RESPONSE ->
                    TextButton(onClick = onOpenSettings) { Text("Check for updates") }
                // CCBG-27 (Free Plan 403): the only fix is a plan change, so that is the
                // one link offered.
                ErrorKind.PLAN ->
                    TextButton(onClick = {
                        openInBrowser(context, QuickLinks.plansUrl(provider), null)
                    }) {
                        Text(QuickLinks.plansLabel(provider))
                    }
                else -> {}
            }
        }
    }
}
