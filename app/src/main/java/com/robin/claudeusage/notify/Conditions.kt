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

    /**
     * The expanded panel renders at most this many strips; the rest fold to one line.
     * The Duet's own cap is lower — see [Duet.maxStrips].
     */
    const val MAX_STRIPS = 3

    /**
     * CCRM-44 (One Surface): everything the pinned panel carries for [profile], as one
     * ordered, capped stack. Every strip is derived here and now — nothing is persisted,
     * so nothing can outlive the condition it describes (CCRM-61 (Settings Diet) removed
     * the folded-event store that could).
     *
     * @param strips at most [MAX_STRIPS] (or [Duet.maxStrips] with a Second account),
     *   ordered faults (re-auth, stale) · warnings (expiry) · update last — the update
     *   strip is the least urgent, so it is the first into the overflow.
     * @param overflow how many strips did not fit; drawn as a "+ n more" line.
     * @param stale whether the stale fault is among the strips — it alone also dims
     *   the big-number figure, doubt belonging on the number itself.
     */
    data class Panel(val strips: List<Condition>, val overflow: Int, val stale: Boolean)

    /**
     * @param second CCRM-62 (Duet Notification): the Second account when the notification
     *   is carrying two, else null for today's single layout. It is the one switch for
     *   Duet mode, and both differences fall out of it rather than out of two flags that
     *   could be set inconsistently: **every** strip gains its account prefix (with two
     *   accounts in the header a bare "Sign-in stopped working" no longer says whose), and
     *   the cap drops to [Duet.maxStrips]`(true)` because two header blocks leave the
     *   panel about 120 dp and three strips cost 132.
     */
    fun panelFor(
        context: Context,
        cache: UsageCache,
        profile: Profile,
        second: Profile? = null,
    ): Panel {
        // The panel carries the accounts the header does *not* show too (revised
        // 2026-08-18): their strips are prefixed with their names, since a bare strip
        // would otherwise read as the shown account's.
        //
        // CCRM-6 (Multi-Account) generalised this from exactly one "other" to every other
        // registered account. Ordering is unchanged and deliberate: the *shown* profiles'
        // faults come first — First, then Second, then everyone else in registry order —
        // so a shown account can never be crowded out of its own panel. The cap is finite
        // however many accounts exist, and "+ n more" is the honest answer.
        val shown = listOfNotNull(profile, second)
        val ordered = shown + cache.registry().all().filter { it !in shown }
        // One evaluation per account per condition: these read the clock, so calling
        // them twice could in principle disagree with itself across a poll boundary.
        val faults = ordered.associateWith { reauth(cache, it) to stale(cache, it) }
        // In Duet mode every strip is prefixed, including the shown accounts'.
        fun Condition?.prefixed(owner: Profile): Condition? = when {
            this == null -> null
            second == null && owner == profile -> this
            else -> labelled(cache.profileLabel(owner))
        }
        val all = ordered.flatMap { owner ->
            val (reauth, stale) = faults.getValue(owner)
            listOfNotNull(reauth.prefixed(owner), stale.prefixed(owner))
        } + ordered.mapNotNull { owner ->
            expiry(cache, owner).prefixed(owner)
        } + listOfNotNull(update(context, cache))
        val cap = Duet.maxStrips(hasSecond = second != null)
        return Panel(
            strips = all.take(cap),
            overflow = (all.size - cap).coerceAtLeast(0),
            // Only the shown profile's staleness dims the shown number. A Duet dims per
            // half instead, from [isStale] — the whole notification going grey because
            // one of two accounts is stale is what CCBG-12 (Status Icon Swap) was
            // fighting, in reverse.
            stale = faults.getValue(profile).second != null,
        )
    }

    /**
     * Whether [profile]'s reading is stale — the per-half dimming on a Duet collapsed row
     * (CCRM-62 (Duet Notification)), where [Panel.stale] would dim both halves at once.
     */
    fun isStale(cache: UsageCache, profile: Profile): Boolean = stale(cache, profile) != null

    /**
     * Whether [profile] has a fault worth a condition dot on its Duet half: a sign-in that
     * stopped working, or a stale reading. The dot is a pointer, not a message — it says
     * "there is a strip for this account in the expanded panel" — so it deliberately keys
     * on the red conditions only. The expiry warning is not one: it is a week of notice,
     * and it does not belong in 6 dp on a row with no room to explain it.
     */
    fun hasFault(cache: UsageCache, profile: Profile): Boolean =
        reauth(cache, profile) != null || stale(cache, profile) != null

    /**
     * Whether an update strip is showing. App-global, not per account, so on a Duet it
     * marks the First half only — the dot has to hang somewhere, and First is the half
     * that also owns the notification's content intent.
     */
    fun hasUpdate(context: Context, cache: UsageCache): Boolean = update(context, cache) != null

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
