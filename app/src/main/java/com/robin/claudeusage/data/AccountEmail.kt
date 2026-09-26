package com.robin.claudeusage.data

/**
 * CCBG-34 (Account Display Name), wireframe `design/2026-09-26-account-email-wireframe.html`
 * (approved 2026-09-26): which login an account is. Pure, so `AccountEmailTest` pins it.
 * Shown only in Settings → Accounts, ⋮ → Details and the Rename dialog — never on the
 * notification, the widgets, the share card or in the log.
 */
object AccountEmail {

    /**
     * The card's two runs for a middle ellipsis (Q1): the local part may shrink, the
     * `@domain` never does — the domain is what tells two logins apart. An address with no
     * "@" is one run.
     */
    fun split(email: String): Pair<String, String> {
        val at = email.lastIndexOf('@')
        return if (at <= 0) email to "" else email.substring(0, at) to email.substring(at)
    }

    /** Q3: no line when the account is already named by its email. */
    fun shownUnder(label: String, email: String?): Boolean =
        !email.isNullOrBlank() && !email.trim().equals(label.trim(), ignoreCase = true)

    /**
     * Q4 as amended: the local part ("robin"), unless another account's email shares it —
     * then the domain's first label ("eam360", "gmail"), which is what differs. Null when
     * there is no email, or the suggestion is already the name. Clipped to [maxLen].
     */
    fun suggestion(email: String?, others: List<String>, current: String, maxLen: Int): String? {
        if (email.isNullOrBlank() || !email.contains('@')) return null
        val (local, domain) = split(email.trim())
        val shared = others.any { o -> o.contains('@') && split(o.trim()).first.equals(local, ignoreCase = true) }
        val pick = if (shared) domain.removePrefix("@").substringBefore('.') else local
        val out = pick.take(maxLen)
        return out.takeIf { it.isNotBlank() && !it.equals(current.trim(), ignoreCase = true) }
    }
}
