package com.robin.claudeusage.ui

/**
 * CCRM-72 (Main Screen Redesign), wireframe §2: the one status line that replaces the
 * main screen's Refresh button and its "Last success / Last attempt" pair.
 *
 * Pure string assembly in the [AccountStatusLine] idiom — no Compose, so the wording is
 * testable without a UI harness. The row that draws it owns the refresh glyph, the
 * spinner and the tap target; this only owns the words.
 *
 * The grammar is the Accounts screen's, reused rather than reinvented: `Checked <ago>`
 * off the last **success**, plus a second clause — `· Tried <ago>` — only when the last
 * **attempt** failed *and* landed after that success. A healthy account therefore never
 * shows two timestamps for one fact, which is exactly what the two lines it replaces did.
 */

/**
 * "Checked just now" / "Checked 14m ago · Tried 2m ago" / "Not checked yet".
 *
 * [fetchedAt] is the last *successful* fetch, [lastAttemptAt] the last attempt of any
 * outcome, and [lastOk] whether that attempt succeeded. An account that has never
 * succeeded reads "Not checked yet" on its own — the failure itself is already spelled
 * out by the `ErrorNotice` directly beneath, so a second clause would only repeat it.
 */
fun statusLine(fetchedAt: Long, lastAttemptAt: Long, lastOk: Boolean, now: Long): String {
    if (fetchedAt <= 0L) return "Not checked yet"
    val checked = "Checked ${Fmt.ago(fetchedAt, now)}"
    return if (!lastOk && lastAttemptAt > fetchedAt) {
        "$checked · Tried ${Fmt.ago(lastAttemptAt, now)}"
    } else {
        checked
    }
}
