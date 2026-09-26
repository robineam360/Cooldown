package com.robin.claudeusage.widgets

import com.robin.claudeusage.alerts.Alerts
import com.robin.claudeusage.data.UsageData

/**
 * When the one app-wide transition alarm should next fire (CCRM-78 (Widgets Reborn), R7).
 *
 * A face never depends on a redraw arriving (R4) — every state is decided at draw time —
 * so this only picks the moment a redraw would make a face *more* right: the earliest
 * future instant, over every placed widget's shown account and window, of
 *  (a) `resetsAt` — the reset-passed state S6;
 *  (b) `fetchedAt + STALE_DATA_MS` — the stale dim (R6);
 *  (c) for a Countdown on Weekly, `resetsAt − 24 h` — live ticking begins;
 *  (d) `fetchedAt + 24 h` (one ms past it, since `Fmt.widgetClock` adds the day only
 *      beyond 24 h) — the "as of" stamp gains its weekday (R2), so a face last fed
 *      yesterday never reads as today. Every placed account, whatever its bucket.
 * A Ring counts both of its account's windows (rev H's companion ring).
 * Null when nothing is placed, or nothing placed has anything left to change into.
 * `Surfaces.arm` sets or cancels the one alarm from it; Step 4 wires that.
 */
object Transitions {

    private const val DAY_MS = 24 * 60 * 60_000L

    /** One placed widget, as `WidgetPrefs` stores it. */
    data class Placed(
        val face: Face,
        /** Null or empty → unassigned: the first account in registry order (R5). */
        val accountKey: String?,
        val window: FaceWindow,
    )

    /** What the cache knows about one account. */
    data class Snapshot(val key: String, val data: UsageData?, val fetchedAt: Long)

    /**
     * @param snapshots every account in registry order (CCRM-71 (Account Order)), so an
     *   unassigned widget resolves exactly as [FaceStates] draws it.
     */
    fun nextTransitionAt(nowMs: Long, placed: List<Placed>, snapshots: List<Snapshot>): Long? {
        if (placed.isEmpty() || snapshots.isEmpty()) return null
        val candidates = mutableListOf<Long>()
        for (p in placed) {
            val shown: List<Pair<Snapshot, FaceWindow>> = when (p.face) {
                // Every account, each on its own headline window (CCRM-82 (Accounts Strip)).
                Face.STRIP -> snapshots.take(FaceStates.STRIP_MAX).map { it to FaceWindow.SESSION }
                else -> {
                    val key = p.accountKey?.takeIf { it.isNotEmpty() }
                    val s = if (key == null) snapshots.first()
                    else snapshots.firstOrNull { it.key == key }
                    // A removed account (S9) has nothing left to change into.
                    // Rev H (CCBG-44 (Widget Fill)): a Ring may draw the other window as its
                    // companion ring — on a frame this does not know — so it counts both;
                    // one spare inexact wake-up is safe under R4.
                    val windows = if (p.face == Face.RING) listOf(p.window, other(p.window)) else listOf(p.window)
                    s?.let { snap -> windows.map { snap to it } }.orEmpty()
                }
            }
            for ((s, asked) in shown) {
                if (s.fetchedAt > 0) {
                    candidates += s.fetchedAt + Alerts.STALE_DATA_MS
                    candidates += s.fetchedAt + DAY_MS + 1
                }
                val window = FaceStates.shownWindow(s.data, asked)
                val resetsAt = when (window) {
                    FaceWindow.WEEKLY -> s.data?.weekly?.resetsAt
                    FaceWindow.SESSION -> s.data?.session?.resetsAt
                }?.toEpochMilli() ?: continue
                candidates += resetsAt
                if (p.face == Face.COUNTDOWN && window == FaceWindow.WEEKLY) {
                    candidates += resetsAt - DAY_MS
                }
            }
        }
        return candidates.filter { it > nowMs }.minOrNull()
    }

    private fun other(w: FaceWindow) = if (w == FaceWindow.SESSION) FaceWindow.WEEKLY else FaceWindow.SESSION
}
