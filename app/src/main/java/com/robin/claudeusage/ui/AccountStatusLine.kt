package com.robin.claudeusage.ui

import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.SignInExpiry

/**
 * The account card's status lines (CCRM-65 (Accounts Redesign)) — pure string
 * assembly, no Compose, so it's testable without a UI harness. Layout and exact
 * placement come from design/accounts-redesign-wireframe.html rev D; this object
 * only owns the wording.
 */
object AccountStatusLine {

    private fun checkedClause(lastAttemptAt: Long, now: Long): String =
        if (lastAttemptAt == 0L) "Not checked yet" else "Checked ${Fmt.ago(lastAttemptAt, now)}"

    /** "Checking…" / "Not checked yet" / "Checked 12m ago · renews in 2h 4m" etc. */
    fun claude(
        lastAttemptAt: Long,
        tokenExpiresAt: Long,
        authState: AuthState,
        busy: Boolean,
        now: Long,
    ): String {
        if (busy) return "Checking…"
        val base = checkedClause(lastAttemptAt, now)
        val clause = when {
            authState == AuthState.REAUTH_NEEDED -> ""
            tokenExpiresAt > now -> " · renews in ${Fmt.dhm(tokenExpiresAt, now)}"
            tokenExpiresAt > 0 && tokenExpiresAt <= now -> " · renewal due at next check"
            else -> ""
        }
        return base + clause
    }

    /** "Checking…" / "Not checked yet" / "Checked 12m ago" — no renewal clause; ChatGPT reports none. */
    fun chatGpt(lastAttemptAt: Long, busy: Boolean, now: Long): String {
        if (busy) return "Checking…"
        return checkedClause(lastAttemptAt, now)
    }

    /**
     * The amber expiry-soon line, or null when there's nothing to warn about.
     * Only [SignInExpiry.Line.Estimated] and [SignInExpiry.Line.Exact] can ever
     * trigger it — see [SignInExpiry.expiresSoon].
     */
    fun expirySoon(line: SignInExpiry.Line, now: Long): String? {
        if (!SignInExpiry.expiresSoon(line, now)) return null
        val expiresAt = when (line) {
            is SignInExpiry.Line.Estimated -> line.expiresAt
            is SignInExpiry.Line.Exact -> line.expiresAt
            is SignInExpiry.Line.RenewalDead, SignInExpiry.Line.None -> return null
        }
        return "Sign-in expires in ${Fmt.dhm(expiresAt, now)} — re-sign in to keep polling going."
    }

    /** The free-plan notice: Claude reports no usage below Pro. */
    fun freePlan(plan: String?): String =
        "${(plan ?: "Free").replaceFirstChar { it.uppercase() }} plan: Claude doesn't report usage — upgrade to Pro, Max or Team to see numbers."
}
