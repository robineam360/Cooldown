package com.robin.claudeusage.notify

import android.content.Context
import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.Profile
import com.robin.claudeusage.data.UpdateCheck
import com.robin.claudeusage.data.UpdateGate
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.ui.Fmt

/**
 * CCBG-12 (Status Icon Swap): the *conditions* — a sign-in that stopped working or is
 * nearing expiry, usage data that has gone stale, an update waiting — as data rather than
 * as notifications.
 *
 * The distinction that matters is lifetime. A window reset is an **event**: it happens, it
 * is read, it stops being interesting, and it still posts (`alerts/Alerts.kt`). These are
 * **states**: true continuously until something fixes them, which as notifications meant
 * one sitting in the shade for hours or, in the sign-in case, up to seven days. That is
 * precisely what makes Android swap our live status-bar meter for the launcher icon, so
 * the price of saying "your token expires next week" was a status bar that read 72% all
 * week.
 *
 * As strips they render inside the pinned notification instead — it is already posted, so
 * they cost no second notification — and they disappear on their own when the condition
 * resolves, with nothing to dismiss. Nothing else consumes this: it is derived live at
 * draw time, so a strip and the thing it describes can never disagree.
 */
object Conditions {

    /** Warn this far ahead of a known sign-in expiry — the pinned panel's own horizon. */
    private const val EXPIRY_HORIZON_MS = 7 * 86_400_000L

    /**
     * @param short one line for the collapsed row, which has room for nothing else.
     * @param detail the full sentence, shown in the expanded panel.
     * @param error true for a fault (stale data), false for a warning (expiry ahead).
     *   Drives the strip's tint and, for stale, the dimming of the numbers themselves.
     */
    data class Condition(
        val short: String,
        val title: String,
        val detail: String,
        val error: Boolean,
    )

    /** The expanded panel renders at most this many strips; the rest fold to one line. */
    const val MAX_STRIPS = 3

    /**
     * CCRM-44 (One Surface): everything the pinned panel carries for [profile], as one
     * ordered, capped stack. Every strip is derived here and now — nothing is persisted,
     * so nothing can outlive the condition it describes (CCRM-61 (Settings Diet) removed
     * the folded-event store that could).
     *
     * @param strips at most [MAX_STRIPS], ordered faults (re-auth, stale) · warnings
     *   (expiry) · update last — the update strip is the least urgent, so it is the
     *   first into the overflow.
     * @param overflow how many strips did not fit; drawn as a "+ n more" line.
     * @param stale whether the stale fault is among the strips — it alone also dims
     *   the big-number figure, doubt belonging on the number itself.
     */
    data class Panel(val strips: List<Condition>, val overflow: Int, val stale: Boolean)

    fun panelFor(context: Context, cache: UsageCache, profile: Profile): Panel {
        // The panel carries the other profiles too (revised 2026-08-18): their strips are
        // prefixed with their names, since the header only names the shown profile.
        //
        // CCRM-6 (Multi-Account) generalised this from exactly one "other" to every other
        // registered account. Ordering is unchanged and deliberate: the *shown* profile's
        // faults come first, so it can never be crowded out of its own panel, then the
        // others in registry order. MAX_STRIPS stays 3 — the panel's height is finite
        // however many accounts exist, and "+ n more" is the honest answer.
        val others = cache.registry().all().filter { it != profile }
        val staleCondition = stale(cache, profile)
        val all = listOfNotNull(reauth(cache, profile), staleCondition) +
            others.flatMap { other ->
                val label = cache.profileLabel(other)
                listOfNotNull(
                    reauth(cache, other)?.labelled(label),
                    stale(cache, other)?.labelled(label),
                )
            } + listOfNotNull(expiry(cache, profile)) +
            others.mapNotNull { other ->
                expiry(cache, other)?.labelled(cache.profileLabel(other))
            } + listOfNotNull(update(context, cache))
        return Panel(
            strips = all.take(MAX_STRIPS),
            overflow = (all.size - MAX_STRIPS).coerceAtLeast(0),
            // Only the shown profile's staleness dims the shown number.
            stale = staleCondition != null,
        )
    }

    private fun Condition.labelled(label: String): Condition =
        copy(short = "$label: $short", title = "$label: $title")

    /**
     * Re-auth as a condition (CCRM-44). It is the textbook state — continuously true
     * until the user re-signs in — and was one of CCBG-12 (Status Icon Swap)'s two
     * deliberate residuals.
     *
     * Unconditional since CCRM-61 (Settings Diet): the panel is the only surface that
     * says a sign-in has stopped working, and a toggle that hides the one report of a
     * broken account is not a preference worth keeping.
     */
    private fun reauth(cache: UsageCache, profile: Profile): Condition? {
        if (cache.snapshot(profile).authState != AuthState.REAUTH_NEEDED) return null
        return Condition(
            short = "Sign-in stopped working",
            title = "Sign-in stopped working",
            detail = "The saved sign-in failed and couldn't renew. Open the app and re-sign in.",
            error = true,
        )
    }

    /**
     * Update-available as a condition (CCRM-44), app-global so it shows whichever
     * profile the panel carries. Persisting while the installed version lags is what
     * resolved CCBG-12's timeout tension: the standalone notice it replaced posted once
     * per version, ever, so it could never be given an expiry — a strip that is simply
     * present while the version is behind needs no such ceremony. It is the only
     * update surface in the shade now (CCRM-61 (Settings Diet)). Respects "skip this
     * version".
     */
    private fun update(context: Context, cache: UsageCache): Condition? {
        val latest = cache.latestKnownVersion() ?: return null
        val installed = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: return null
        } catch (_: Exception) {
            return null
        }
        val normalized = UpdateCheck.normalize(latest)
        if (UpdateCheck.compare(normalized, UpdateCheck.normalize(installed)) <= 0) return null
        if (UpdateGate.isSkipped(latest, cache.dismissedUpdateVersion())) return null
        return Condition(
            short = "Update available — v$normalized",
            title = "Update available — v$normalized",
            detail = "You have v$installed. Tap to open the app; nothing installs by itself.",
            error = false,
        )
    }

    /**
     * Data is stale when polls are running but none has succeeded for hours. Reuses
     * `Alerts.STALE_DATA_MS`, the one constant left that means "stale", so this strip and
     * anything that grows beside it can never disagree about what it is.
     */
    private fun stale(cache: UsageCache, profile: Profile): Condition? {
        val snapshot = cache.snapshot(profile)
        val fresh = snapshot.fetchedAt <= 0 ||
            System.currentTimeMillis() - snapshot.fetchedAt <= com.robin.claudeusage.alerts.Alerts.STALE_DATA_MS ||
            snapshot.lastStatus == "OK"
        if (fresh) return null
        val use24h = cache.use24hTime()
        return Condition(
            short = "Stale — nothing since ${Fmt.timeOnly(
                java.time.Instant.ofEpochMilli(snapshot.fetchedAt), use24h,
            )}",
            title = "Usage data is stale",
            // CCRM-27 (Error Taxonomy): the kind's short label, not the raw status.
            detail = "Nothing fetched since ${Fmt.dayTimeWithAgo(snapshot.fetchedAt, use24h)}. " +
                "Last error: ${com.robin.claudeusage.data.ErrorKind.fromKey(snapshot.lastStatusKind).short(profile.provider)}",
            error = true,
        )
    }

    /**
     * The expiry date is only known from the pasted JSON and a rotation clears it, so this
     * is best-effort — no expiry known means no condition, not a false all-clear.
     *
     * Unlike the alert it replaces, there are no 7/3/1-day steps. Steps existed to avoid
     * re-notifying; a strip that is simply present while the condition holds needs no such
     * ceremony, and it means the warning cannot be dismissed into invisibility while it is
     * still true.
     */
    private fun expiry(cache: UsageCache, profile: Profile): Condition? {
        val expiry = cache.refreshExpiresAt(profile)
        if (expiry <= 0) return null
        val msLeft = expiry - System.currentTimeMillis()
        // Already dead is not a warning — the re-auth path owns that, and it still posts.
        if (msLeft <= 0 || msLeft > EXPIRY_HORIZON_MS) return null
        val use24h = cache.use24hTime()
        return Condition(
            short = "Sign-in expires in ${Fmt.dhm(expiry)}",
            title = "Sign-in expires in ${Fmt.dhm(expiry)}",
            detail = "Valid until ${Fmt.dateTime(expiry, use24h)}. " +
                "Paste a fresh token when convenient.",
            error = false,
        )
    }
}
