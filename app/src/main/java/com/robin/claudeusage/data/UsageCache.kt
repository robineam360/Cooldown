package com.robin.claudeusage.data

import android.content.Context
import android.content.SharedPreferences
import com.robin.claudeusage.data.source.Sources
import com.robin.claudeusage.ui.Palette

enum class AuthState { NO_CREDENTIALS, OK, REAUTH_NEEDED }

data class Snapshot(
    val rawJson: String?,
    val fetchedAt: Long,        // epoch millis of last successful fetch, 0 = never
    val lastStatus: String,     // human-readable outcome of the last attempt
    val lastAttemptAt: Long,
    val authState: AuthState,
    /** CCRM-27 (Error Taxonomy): the typed kind behind [lastStatus]. */
    val lastStatusKind: String = ErrorKind.INTERNAL.key,
    /**
     * Whose parser reads [rawJson] back (CCRM-54 (ChatGPT Account)). The cached body is
     * re-parsed on *read*, not kept as an object, so the provider has to travel with the
     * snapshot — CCRM-53 (Provider Model) routed the fetch-time parse through the seam
     * and left this one hardcoded to Claude, which made a signed-in ChatGPT account fetch
     * successfully and then render "No data yet" on every surface at once. Found on the
     * phone 2026-09-06, minutes after the first real sign-in.
     */
    val provider: Provider = Provider.CLAUDE,
) {
    // Lazy, not get(): the UI reads this several times per composition and
    // on a 5-second tick — one parse per snapshot is plenty.
    val data: UsageData? by lazy {
        rawJson?.let {
            // A provider with no source yet (Antigravity, CCRM-55) must read as "no
            // data", never as a crash on a lazy the whole UI touches.
            try {
                Sources.of(provider).parseUsage(it)
            } catch (_: NotImplementedError) {
                null
            }
        }
    }
}

/**
 * Plain (non-secret) cache. Per-profile state (payload, status, backoff, window peaks,
 * reset-ping mode) is keyed with the profile prefix; app-wide settings are unprefixed.
 * Personal keys carry no prefix so v0.5 data migrates transparently.
 */
class UsageCache(context: Context) {

    companion object {
        const val RESET_OFF = "off"
        const val RESET_SMART = "smart"
        const val RESET_ALWAYS = "always"

        /** In smart mode a reset ping fires only if the window had reached this. */
        const val SMART_RESET_MIN_PCT = 80.0

        /**
         * Every fixed per-profile entry name, for [clearProfile]'s legacy path. Kept next to
         * the getters that write them: adding a `k(profile, "…")` key without adding it here
         * leaves residue behind on removal. The runtime-built families (`peak…`, `seen…Key`,
         * and the retired `pace…` / `modelAlert.…`) are handled separately in [clearProfile].
         */
        private val LEGACY_PROFILE_KEYS = listOf(
            "rawJson", "fetchedAt", "lastStatus", "lastStatusKind", "lastAttemptAt",
            "authState", "plan", "tier", "signInTokenKeys", "nativeSignIn",
            "refreshExpiresAt", "refreshExpiryEstimated", "lastRenewedAt",
            "firstRefreshFailAt", "backoffUntil", "consecutive429",
            "creditsVisible", "customLabel", "accent",
            "resetPingSession", "resetPingWeekly",
            // retired in v1.6 (CCRM-61 (Settings Diet)); still swept so residue leaves
            // with the account. They are plain strings, not references to live
            // accessors — an install upgraded from v1.5 still has them on disk, and
            // Android Auto Backup carries them to a new device.
            "reauthNotified", "staleNotified", "foldedEvents",
            "profileAlertsEnabled",
            "sessionAlertKey", "sessionAlertThreshold",
            "weeklyAlertKey", "weeklyAlertThreshold",
            "pingEnabled", "pingFirstMinute", "pingCutoffMinute", "pingRenewals",
            "pingDay", "pingWindowsStarted", "pingRetryIndex", "pingLastSentAt",
            "pingLastAttemptAt", "pingLastResult", "pingLastFailed", "pingRevision",
            "pingPendingBefore", "pingVerifyAttempt",
        )
    }

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("usage_cache", Context.MODE_PRIVATE)

    /** The registry is the owner of labels and of the account list; see [profileLabel]. */
    private val registry: ProfileRegistry by lazy { ProfileRegistry(appContext) }

    fun registry(): ProfileRegistry = registry

    // The legacy exception, restated as a key comparison now that Profile is a value type
    // (CCRM-6 (Multi-Account)): v0.5 stored the single account's entries unprefixed, and
    // that key is a storage-format constant, so this test can never be dropped.
    private fun k(profile: Profile, name: String): String =
        if (profile.key == Profile.LEGACY_KEY) name else "${profile.key}.$name"

    fun snapshot(profile: Profile): Snapshot {
        val lastStatus =
            prefs.getString(k(profile, "lastStatus"), "Never fetched") ?: "Never fetched"
        return Snapshot(
            rawJson = prefs.getString(k(profile, "rawJson"), null),
            fetchedAt = prefs.getLong(k(profile, "fetchedAt"), 0L),
            lastStatus = lastStatus,
            lastAttemptAt = prefs.getLong(k(profile, "lastAttemptAt"), 0L),
            authState = AuthState.valueOf(
                prefs.getString(k(profile, "authState"), AuthState.NO_CREDENTIALS.name)
                    ?: AuthState.NO_CREDENTIALS.name
            ),
            // Pre-CCRM-27 installs have a status but no kind: guess from the
            // string once; the next failure writes the real kind.
            lastStatusKind = prefs.getString(k(profile, "lastStatusKind"), null)
                ?: ErrorKind.fromStatus(lastStatus).key,
            provider = profile.provider,
        )
    }

    fun saveSuccess(profile: Profile, rawJson: String, now: Long) {
        prefs.edit()
            .putString(k(profile, "rawJson"), rawJson)
            .putLong(k(profile, "fetchedAt"), now)
            .putString(k(profile, "lastStatus"), "OK")
            .putLong(k(profile, "lastAttemptAt"), now)
            .putString(k(profile, "authState"), AuthState.OK.name)
            .putInt(k(profile, "consecutive429"), 0)
            .putLong(k(profile, "backoffUntil"), 0L)
            .apply()
    }

    fun saveFailure(
        profile: Profile,
        status: String,
        now: Long,
        authState: AuthState? = null,
        kind: ErrorKind = ErrorKind.INTERNAL,
    ) {
        val e = prefs.edit()
            .putString(k(profile, "lastStatus"), status)
            .putString(k(profile, "lastStatusKind"), kind.key)
            .putLong(k(profile, "lastAttemptAt"), now)
        if (authState != null) e.putString(k(profile, "authState"), authState.name)
        e.apply()
    }

    fun setAuthState(profile: Profile, state: AuthState) {
        prefs.edit().putString(k(profile, "authState"), state.name).apply()
    }

    // --- 429 backoff: 5 min * 2^n, capped at 60 min ---

    fun backoffUntil(profile: Profile): Long = prefs.getLong(k(profile, "backoffUntil"), 0L)

    fun bumpBackoff(profile: Profile, now: Long): Long {
        val n = prefs.getInt(k(profile, "consecutive429"), 0)
        val delayMs = (5L * 60_000L shl n.coerceAtMost(4)).coerceAtMost(60L * 60_000L)
        val until = now + delayMs
        prefs.edit()
            .putInt(k(profile, "consecutive429"), n + 1)
            .putLong(k(profile, "backoffUntil"), until)
            .apply()
        return until
    }

    // --- app-wide settings ---

    fun pollIntervalMinutes(): Long = prefs.getLong("pollIntervalMin", 15L)

    fun setPollIntervalMinutes(min: Long) {
        prefs.edit().putLong("pollIntervalMin", min.coerceAtLeast(5L)).apply()
    }

    /**
     * Display name for a profile — the key stays, only the label is editable.
     *
     * Still the read path ~40 sites use, but it resolves through the registry **by key**
     * rather than returning the [Profile]'s own field, so a rename is visible to a
     * `Profile` captured earlier in a composition or an intent extra.
     * Falls back to the captured label if the account has since been removed.
     */
    fun profileLabel(profile: Profile): String =
        registry.byKey(profile.key)?.label ?: profile.label

    fun setProfileLabel(profile: Profile, label: String) {
        registry.rename(profile.key, label)
    }

    /**
     * Forgets every per-profile entry for [profile] — CCRM-6 (Multi-Account) account
     * removal — the cache step of [UsageRepository.removeProfile]'s load-bearing ordering.
     *
     * Two paths, because of the legacy exception in [k]. A prefixed profile can be
     * prefix-scanned, which is exhaustive by construction. The legacy `personal` profile's
     * entries share the bare namespace with every app-wide setting in this file — `snapshot`
     * lives at `"rawJson"`, the app's theme at `"themeMode"` — so a prefix scan there would
     * take the whole app's settings with it. Its names are enumerated instead, including the
     * families whose names are built at runtime (per-window and per-model). The
     * `modelAlert.` scan is bounded to the `Key`/`Threshold` suffixes so it can never reach
     * an app-wide setting that is one plural away from a per-profile key — the retired
     * `sessionAlertThresholds` was exactly that, and the bound is kept rather than
     * loosened now that it is gone.
     */
    fun clearProfile(profile: Profile) {
        val e = prefs.edit()
        if (profile.key == Profile.LEGACY_KEY) {
            for (name in LEGACY_PROFILE_KEYS) e.remove(name)
            for (window in listOf("Session", "Weekly")) {
                e.remove("peak$window")
                e.remove("seen${window}Key")
                // retired in v1.6 (CCRM-61 (Settings Diet)); still swept so residue
                // leaves with the account
                e.remove("pace${window}Key")
                e.remove("pace${window}Mask")
            }
            // retired in v1.6 (CCRM-61 (Settings Diet)); still swept so residue leaves
            // with the account
            for (name in prefs.all.keys) {
                if (name.startsWith("modelAlert.") &&
                    (name.endsWith("Key") || name.endsWith("Threshold"))
                ) e.remove(name)
            }
        } else {
            val prefix = "${profile.key}."
            for (name in prefs.all.keys) if (name.startsWith(prefix)) e.remove(name)
        }
        e.apply()
    }

    /**
     * Reset-ping behaviour, per account and per window kind ("Session"/"Weekly"): off,
     * smart, or always.
     *
     * Per account since CCRM-61 (Settings Diet) — the reset ping is the one standalone
     * notification left, so "which accounts may ping me" is now the *only* thing this
     * setting can mean, and the retired per-profile alerts toggle isn't there to say it.
     *
     * Two migration fallbacks, in order: the app-wide `resetMode$window` this grew out of
     * (so an upgrading install keeps its choice on every account), then the pre-granularity
     * `resetAlertsEnabled` master switch, honoured only when it was explicitly turned off.
     */
    fun resetPingMode(profile: Profile, window: String): String =
        prefs.getString(k(profile, "resetPing$window"), null)
            ?: prefs.getString("resetMode$window", null)
            ?: when {
                !prefs.getBoolean("resetAlertsEnabled", true) -> RESET_OFF
                window == "Session" -> RESET_SMART
                else -> RESET_ALWAYS
            }

    fun setResetPingMode(profile: Profile, window: String, mode: String) {
        prefs.edit().putString(k(profile, "resetPing$window"), mode).apply()
    }

    /**
     * One-shot guard for `Alerts.retireOldChannels` — the six notification channels
     * CCRM-61 (Settings Diet) left without a poster are deleted once per install, not on
     * every poll.
     */
    fun oldChannelsRetired(): Boolean = prefs.getBoolean("oldChannelsRetired", false)

    fun setOldChannelsRetired() {
        prefs.edit().putBoolean("oldChannelsRetired", true).apply()
    }

    // --- highest percent seen in the current window instance (drives smart reset pings) ---

    fun windowPeak(profile: Profile, window: String): Double =
        prefs.getFloat(k(profile, "peak$window"), 0f).toDouble()

    fun setWindowPeak(profile: Profile, window: String, pct: Double) {
        prefs.edit().putFloat(k(profile, "peak$window"), pct.toFloat()).apply()
    }

    // --- pinned (ongoing) usage notification ---

    fun pinnedEnabled(): Boolean = prefs.getBoolean("pinnedEnabled", false)

    fun setPinnedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pinnedEnabled", enabled).apply()
    }

    fun pinnedProfile(): Profile = registry.resolve(prefs.getString("pinnedProfile", null))

    fun setPinnedProfile(profile: Profile) {
        prefs.edit().putString("pinnedProfile", profile.key).apply()
    }

    /**
     * CCRM-23 (Reset Display): which reset form *leads* on every surface —
     * "countdown" ("resets in 2h 14m", the default) or "clock" ("resets 4:12 PM").
     * Grown from the tile-only `tileSubtitle` pref (CCRM-11), whose stored value is
     * migrated by the read-time fallback below; the tile keeps the behaviour and
     * stops owning the preference. Option A of the approved wireframe: surfaces
     * with a second slot keep the other form there — the countdown reads better,
     * the clock can't go stale, and the token only decides which one leads.
     */
    fun resetDisplay(): String =
        prefs.getString("resetDisplay", null)
            ?: prefs.getString("tileSubtitle", "countdown")
            ?: "countdown"

    fun setResetDisplay(mode: String) {
        prefs.edit().putString("resetDisplay", mode).apply()
    }

    /** The flag render sites actually branch on. */
    fun resetClock(): Boolean = resetDisplay() == "clock"

    /**
     * Where tapping the notification body goes: "app" (this app's breakdown, the
     * default) or "claude" (the Claude app, falling back to us if it isn't there).
     */
    fun pinnedTapTarget(): String = prefs.getString("pinnedTapTarget", "app") ?: "app"

    fun setPinnedTapTarget(target: String) {
        prefs.edit().putString("pinnedTapTarget", target).apply()
    }

    // --- usage credits (CCRM-1) ---

    /**
     * Whether the pay-as-you-go credits section shows for this profile. Per-profile
     * because the two accounts can be on very different billing setups — credits may
     * be meaningful on one and noise on the other.
     */
    fun creditsVisible(profile: Profile): Boolean =
        prefs.getBoolean(k(profile, "creditsVisible"), true)

    fun setCreditsVisible(profile: Profile, visible: Boolean) {
        prefs.edit().putBoolean(k(profile, "creditsVisible"), visible).apply()
    }

    // --- CCRM-43 (Bar Pace Marks): the red over-pace segment, per surface ---
    //
    // Two keys rather than one, by decision of 2026-08-13: the surfaces are read at
    // very different distances (a long look at the usage screen, a notification you
    // can't dismiss), so the appetite for red differs per surface. All default ON —
    // the behaviour approved and shipped — and each gates *only* the segment. The
    // neutral even-pace tick always draws, and the 80/90/100 severity ladder is
    // untouched: this is about pace, not severity.

    fun paceOverInApp(): Boolean = prefs.getBoolean("paceOverInApp", true)

    fun setPaceOverInApp(enabled: Boolean) {
        prefs.edit().putBoolean("paceOverInApp", enabled).apply()
    }

    fun paceOverOnNotification(): Boolean = prefs.getBoolean("paceOverOnNotification", true)

    fun setPaceOverOnNotification(enabled: Boolean) {
        prefs.edit().putBoolean("paceOverOnNotification", enabled).apply()
    }

    // --- CCRM-29 (Display Mode) ---

    /**
     * "system" (default) / "light" / "dark". In-app screens only — the notification
     * follows the system, since its backdrop isn't ours.
     */
    fun themeMode(): String = prefs.getString("themeMode", "system") ?: "system"

    fun setThemeMode(mode: String) {
        prefs.edit().putString("themeMode", mode).apply()
    }

    /**
     * "system" / "12" / "24". Migration: an install that ever touched the old
     * `use24hTime` boolean keeps that explicit choice; only installs without the
     * old key get "system" — nobody's clock format flips on upgrade.
     */
    fun timeFormat(): String =
        prefs.getString("timeFormat", null)
            ?: if (prefs.contains("use24hTime")) {
                if (prefs.getBoolean("use24hTime", false)) "24" else "12"
            } else "system"

    fun setTimeFormat(mode: String) {
        prefs.edit().putString("timeFormat", mode).apply()
    }

    /** The resolved boolean every render site still reads, unchanged in shape. */
    fun use24hTime(): Boolean = when (timeFormat()) {
        "12" -> false
        "24" -> true
        else -> android.text.format.DateFormat.is24HourFormat(appContext)
    }

    // CCRM-22 (Used or Left): one app-wide display token. Every numeric usage
    // readout follows it (rev B — nothing is exempt); fills, pace ticks and the
    // warning-colour ladder never do, so a red bar can't sit beside "8% left"
    // and read as backwards.
    fun usageDisplay(): String = prefs.getString("usageDisplay", "used") ?: "used"

    fun setUsageDisplay(mode: String) {
        prefs.edit().putString("usageDisplay", mode).apply()
    }

    /** The flag render sites actually branch on. */
    fun usageLeft(): Boolean = usageDisplay() == "left"

    // CCRM-34 (Diagnostics Log): the app log's minimum level. "info" default;
    // "debug" only while someone is chasing something — a user-facing setting,
    // deliberately (OpenQuota's call, and the right one).
    fun logLevel(): String = prefs.getString("logLevel", "info") ?: "info"

    fun setLogLevel(level: String) {
        prefs.edit().putString("logLevel", level).apply()
    }

    /**
     * The global theme choice (CCRM-56 (Provider Identity), decision 1). Migration
     * without a flag: an install that never touched the picker has no "themeColor"
     * key, so it reads as [Palette.PER_PROVIDER] (a Claude account then renders
     * Claude Orange, pixel-identical to before this existed); once a key is
     * written — even to "Claude Orange" — that explicit choice stays the global
     * override for every account.
     */
    fun themeColorName(): String = prefs.getString("themeColor", Palette.PER_PROVIDER) ?: Palette.PER_PROVIDER

    fun setThemeColorName(name: String) {
        prefs.edit().putString("themeColor", name).apply()
    }

    /** Per-account accent override (CCRM-56 (Provider Identity), decision 1). */
    fun accountAccent(profile: Profile): String? = prefs.getString(k(profile, "accent"), null)

    fun setAccountAccent(profile: Profile, name: String?) {
        prefs.edit().apply {
            if (name == null) remove(k(profile, "accent")) else putString(k(profile, "accent"), name)
        }.apply()
    }

    // --- automatic update checks (CCRM-28): app-global, deliberately not per-profile ---

    fun autoCheckUpdates(): Boolean = prefs.getBoolean("autoCheckUpdates", true)

    fun setAutoCheckUpdates(enabled: Boolean) {
        prefs.edit().putBoolean("autoCheckUpdates", enabled).apply()
    }

    /** Last **successful** check, epoch ms; 0 = never. A failure never advances it. */
    fun lastUpdateCheckAt(): Long = prefs.getLong("lastUpdateCheckAt", 0L)

    /** The settings line's outcome half, e.g. "up to date (v0.14)" / "v0.15 available". */
    fun lastUpdateCheckOutcome(): String? = prefs.getString("lastUpdateCheckOutcome", null)

    fun recordUpdateCheckSuccess(at: Long, outcome: String, latestVersion: String) {
        prefs.edit()
            .putLong("lastUpdateCheckAt", at)
            .putString("lastUpdateCheckOutcome", outcome)
            .putString("latestKnownVersion", latestVersion)
            .remove("lastUpdateFailAt")
            .remove("lastUpdateFailReason")
            .apply()
    }

    /**
     * The newest release version any check has seen, for the CCRM-44 (One Surface)
     * update strip — which persists while this is ahead of the installed version,
     * instead of the once-per-version notification.
     */
    fun latestKnownVersion(): String? = prefs.getString("latestKnownVersion", null)

    fun lastUpdateFailAt(): Long = prefs.getLong("lastUpdateFailAt", 0L)

    fun lastUpdateFailReason(): String? = prefs.getString("lastUpdateFailReason", null)

    /** Deliberately leaves lastUpdateCheckAt alone, so the next poll retries. */
    fun recordUpdateCheckFailure(at: Long, reason: String) {
        prefs.edit()
            .putLong("lastUpdateFailAt", at)
            .putString("lastUpdateFailReason", reason)
            .apply()
    }

    /** "Skip this version": silences exactly this version; a newer one still notifies. */
    fun dismissedUpdateVersion(): String? = prefs.getString("dismissedUpdateVersion", null)

    fun setDismissedUpdateVersion(version: String) {
        prefs.edit().putString("dismissedUpdateVersion", version).apply()
    }

    // --- token health metadata (informational fields from the pasted JSON) ---

    /** Stored at paste time; cleared when a renewal rotates the refresh token. */
    fun refreshExpiresAt(profile: Profile): Long = prefs.getLong(k(profile, "refreshExpiresAt"), 0L)

    fun plan(profile: Profile): String? = prefs.getString(k(profile, "plan"), null)

    /** Raw rate-limit tier, e.g. "default_5x" — parsed at render time (CCRM-38). */
    fun tier(profile: Profile): String? = prefs.getString(k(profile, "tier"), null)

    fun setTokenMeta(profile: Profile, refreshExpiresAt: Long, plan: String?, tier: String?) {
        prefs.edit()
            .putLong(k(profile, "refreshExpiresAt"), refreshExpiresAt)
            .putString(k(profile, "plan"), plan)
            .putString(k(profile, "tier"), tier)
            .apply()
    }

    /**
     * Key names (never values) of the last sign-in's token response — a
     * debug-only instrument so whether `rate_limit_tier` actually appears in
     * *our* token response gets settled by the next real sign-in (CCRM-38).
     */
    fun signInTokenKeys(profile: Profile): String? =
        prefs.getString(k(profile, "signInTokenKeys"), null)

    fun setSignInTokenKeys(profile: Profile, keys: String?) {
        prefs.edit().putString(k(profile, "signInTokenKeys"), keys).apply()
    }

    fun clearRefreshExpiry(profile: Profile) {
        prefs.edit().putLong(k(profile, "refreshExpiresAt"), 0L).apply()
    }

    /**
     * True when the stored refresh-expiry is our own ~30-day estimate from a
     * native phone sign-in (the token response omits the real date), not an
     * exact value read from a pasted desktop token. Drives "expires around …".
     */
    fun refreshExpiryEstimated(profile: Profile): Boolean =
        prefs.getBoolean(k(profile, "refreshExpiryEstimated"), false)

    fun setRefreshExpiryEstimated(profile: Profile, estimated: Boolean) {
        prefs.edit().putBoolean(k(profile, "refreshExpiryEstimated"), estimated).apply()
    }

    /**
     * True when this profile was authenticated by native sign-in on the phone.
     * Its family rotates as normal healthy renewal, so — unlike a shared desktop
     * copy — a rotated refresh token must NOT clear the estimated expiry.
     */
    fun nativeSignIn(profile: Profile): Boolean =
        prefs.getBoolean(k(profile, "nativeSignIn"), false)

    fun setNativeSignIn(profile: Profile, native: Boolean) {
        prefs.edit().putBoolean(k(profile, "nativeSignIn"), native).apply()
    }

    fun lastRenewedAt(profile: Profile): Long = prefs.getLong(k(profile, "lastRenewedAt"), 0L)

    fun setLastRenewedAt(profile: Profile, at: Long) {
        prefs.edit().putLong(k(profile, "lastRenewedAt"), at).apply()
    }

    /** First moment the current streak of failed renewals started; 0 = no streak. */
    fun firstRefreshFailAt(profile: Profile): Long = prefs.getLong(k(profile, "firstRefreshFailAt"), 0L)

    fun setFirstRefreshFailAt(profile: Profile, at: Long) {
        prefs.edit().putLong(k(profile, "firstRefreshFailAt"), at).apply()
    }

    // --- reset detection: last seen window identity (its resets_at) per window kind ---

    fun lastSeenWindowKey(profile: Profile, window: String): Long =
        prefs.getLong(k(profile, "seen${window}Key"), 0L)

    fun setLastSeenWindowKey(profile: Profile, window: String, key: Long) {
        prefs.edit().putLong(k(profile, "seen${window}Key"), key).apply()
    }

}
