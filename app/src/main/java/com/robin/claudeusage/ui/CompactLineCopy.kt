package com.robin.claudeusage.ui

import kotlin.math.roundToInt

/**
 * CCRM-72 (Main Screen Redesign), wireframe §3/§4/§7: the single line a *Compact* card
 * shows in place of its folded chart — "Resets in 2h 41m · 32% below pace".
 *
 * Pure, like [AccountStatusLine] and [statusLine]: the card owns the colours and the
 * tap target, this owns the words and which half of them is bold.
 *
 * Two clauses, both borrowed rather than invented: the reset clause is the countdown
 * `ResetRow` already draws, and the pace clause runs the same delta and the same
 * [PACE_DEAD_ZONE] the chart's own pace readout uses (`pacePhrase`), so a card can
 * never say "below pace" folded and "above even pace" open. The wireframe writes the
 * below-pace case short — "32% below pace" — and the above-pace case long — "12% above
 * even pace" — which is what [compactLine] reproduces: the long form is the one drawn
 * bold, and carries the word that earns its length there.
 */
data class CompactLine(
    /** The whole line, as plain text — also what a screen reader reads. */
    val text: String,
    /**
     * The pace clause when it is the above-pace one, which the card draws bold in
     * `barFill(95.0)`; null whenever nothing on the line is emphasised.
     */
    val boldClause: String?,
)

/**
 * The folded card's one line.
 *
 * [resetCountdown] is `Fmt.relIn(window.resetsAt)` — "in 2h 41m" — or null when the
 * window has no reset time at all, which is the idle case `ResetRow` already words as
 * "Starts when a message is sent" (CCBG-30 (Phantom Window)). [delta] is the window's percent minus its
 * even-pace percent, or null when there is no clock to measure pace against.
 */
fun compactLine(resetCountdown: String?, delta: Double?): CompactLine {
    if (resetCountdown == null) return CompactLine("Starts when a message is sent", null)
    val reset = "Resets $resetCountdown"
    val pace = compactPaceClause(delta) ?: return CompactLine(reset, null)
    val above = delta != null && delta > PACE_DEAD_ZONE
    return CompactLine("$reset · $pace", if (above) pace else null)
}

/** "12% above pace" / "32% below pace" / "on pace"; null with no pace to read. */
fun compactPaceClause(delta: Double?): String? = when {
    delta == null -> null
    delta > PACE_DEAD_ZONE -> "${delta.roundToInt()}% above pace"
    delta < -PACE_DEAD_ZONE -> "${(-delta).roundToInt()}% below pace"
    else -> "on pace"
}
