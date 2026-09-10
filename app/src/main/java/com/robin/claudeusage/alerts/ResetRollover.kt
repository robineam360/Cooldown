package com.robin.claudeusage.alerts

import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.UsageCache

/**
 * The pure decision behind the reset ping: given what the poll saw and what was stored
 * last time, has a window rolled over, and what follows from that.
 *
 * Everything decidable without a `Context`, a `SharedPreferences` or a `NotificationManager`
 * lives here so it is testable without Robolectric (`ResetRolloverTest`) — the pattern
 * `notify/Duet.kt` already uses. Nothing here touches Android: [Projection] is plain Kotlin
 * and the two [UsageCache] references are `const val`s, inlined at compile time.
 *
 * CCBG-25 (Idle Reset Silence) is the reason this is its own file. The rollover test used
 * to sit behind `window?.resetsAt ?: return`, so the headline case — hit the limit, stop,
 * wait for the window to come back — never pinged at all: an idle Claude account reports
 * **no session window** once its 5 hours are up, so there was no key to compare and the
 * reset was only noticed on the first poll *after* the user had already started the next
 * window. The idle arm below is that case, and it is the one that clears the stored key so
 * the ping fires exactly once.
 */
object ResetRollover {

    /** A closed window to append to `SessionLog`. */
    data class LogEntry(val resetAt: Long, val peakPct: Double, val hitLimit: Boolean)

    /** A ping to post on the `reset_alerts` channel. */
    data class Ping(val title: String, val body: String)

    /**
     * What the caller must do after this poll. [storeWindowKey] and [storePeak] are always
     * the values to write back — including "the same as before", so the caller never has to
     * reason about which arm it took. A `storeWindowKey` of 0 means *clear* it.
     */
    data class Outcome(
        val log: LogEntry? = null,
        val ping: Ping? = null,
        val storeWindowKey: Long,
        val storePeak: Double,
    )

    /** A window is treated as having been run to the limit at this percentage. */
    private const val HIT_LIMIT_PCT = 99.5

    /**
     * @param windowLabel the tight-surface name — "5h" or "Weekly", never "5-hour window".
     * @param windowKey the live window's `resets_at` in epoch millis; **null when the
     *   payload carries no window at all**, which is what an idle account reports once its
     *   session window has expired.
     * @param pct the live window's percentage; null reads as 0.
     * @param lastSeenKey the previously stored window identity; 0 = nothing recorded yet.
     * @param storedPeak the highest percentage seen while the previous window was open.
     * @param mode the account's ping mode for this window kind
     *   ([UsageCache.RESET_OFF] / [UsageCache.RESET_SMART] / [UsageCache.RESET_ALWAYS]).
     * @param nowMs the poll's wall clock.
     * @param nextResetPhrase how to name the *next* reset ("in 4h 12m"), for the live-window
     *   arm only. There is no next reset to name when the account is idle, so the idle arm
     *   ignores it and its body ends at the percentage.
     */
    fun decide(
        windowLabel: String,
        windowKey: Long?,
        pct: Double?,
        lastSeenKey: Long,
        storedPeak: Double,
        mode: String,
        windowLengthMs: Long,
        nowMs: Long,
        nextResetPhrase: String?,
    ): Outcome {
        val observed = pct ?: 0.0

        if (windowKey == null) {
            // CCBG-25 (Idle Reset Silence): no window in the payload. That is a rollover
            // exactly when we had a window whose reset instant has already passed — the
            // account simply hasn't started the next one. A fresh install (lastSeenKey 0)
            // and a window that hasn't reached its reset yet both stay silent, and neither
            // touches the stored key or peak.
            if (lastSeenKey == 0L || lastSeenKey >= nowMs) {
                return Outcome(storeWindowKey = lastSeenKey, storePeak = storedPeak)
            }
            return Outcome(
                log = LogEntry(lastSeenKey, storedPeak, storedPeak >= HIT_LIMIT_PCT),
                ping = if (wantsPing(mode, storedPeak)) {
                    // No "Next reset …" clause: the next window starts when a message is
                    // sent, so there is no instant to name and a guess would be a lie.
                    Ping("$windowLabel reset", "Usage is back at 0%.")
                } else null,
                // Cleared, so the ping fires once and the next real window starts clean.
                storeWindowKey = 0L,
                storePeak = 0.0,
            )
        }

        // Proximity, not equality (CCBG-4 (Alert Dedup)). Exact comparison also made this
        // fire spuriously when a poll landed within ~1s of the boundary and drift pushed
        // lastSeen just into the past.
        val rolledOver = lastSeenKey != 0L &&
            !Projection.sameWindow(lastSeenKey, windowKey, windowLengthMs) &&
            lastSeenKey < nowMs

        if (!rolledOver) {
            return Outcome(
                storeWindowKey = windowKey,
                storePeak = maxOf(storedPeak, observed),
            )
        }
        return Outcome(
            log = LogEntry(lastSeenKey, storedPeak, storedPeak >= HIT_LIMIT_PCT),
            ping = if (wantsPing(mode, storedPeak)) {
                Ping(
                    "$windowLabel reset",
                    "Usage is back at ${observed.toInt()}%. Next reset ${nextResetPhrase ?: "unknown"}.",
                )
            } else null,
            storeWindowKey = windowKey,
            storePeak = observed,
        )
    }

    /**
     * Smart mode only pings when the finished window had actually been running hot — a
     * reset nobody was waiting for is just noise.
     */
    private fun wantsPing(mode: String, peak: Double): Boolean =
        mode == UsageCache.RESET_ALWAYS ||
            (mode == UsageCache.RESET_SMART && peak >= UsageCache.SMART_RESET_MIN_PCT)
}
