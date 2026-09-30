package com.robin.claudeusage.diag

import android.app.ApplicationExitInfo
import kotlin.math.abs

/**
 * CCRM-85 (Crash Capture): what the next launch does with `ApplicationExitInfo`, the
 * supplement to the Java handler for ANRs, native crashes and anything the handler
 * missed. Pure, so the rules are pinned by `ExitReasonsTest`:
 *
 * - only entries newer than the **watermark** are looked at, oldest first;
 * - the watermark starts at the install or upgrade time, so exits from before v1.9
 *   never show a card;
 * - it moves only once an entry has been processed — a failure part-way leaves the
 *   rest for the next launch;
 * - only `REASON_CRASH`, `REASON_CRASH_NATIVE` and `REASON_ANR` make a report; every
 *   other reason is logged only;
 * - a `REASON_CRASH` the Java handler already captured (same pid, close in time) is a
 *   duplicate and is dropped;
 * - a missing trace is tolerated: the report says Android kept none.
 */
object ExitReasons {

    data class Entry(val timestamp: Long, val pid: Int, val reason: Int)

    /** A report the Java handler already wrote: its time and process. */
    data class Captured(val at: Long, val pid: Int)

    sealed class Action {
        abstract val entry: Entry
        data class Report(override val entry: Entry, val kind: CrashReport.Kind) : Action()
        data class Duplicate(override val entry: Entry) : Action()
        data class LogOnly(override val entry: Entry) : Action()
    }

    /** The Java handler writes a few milliseconds before the process dies. */
    internal const val DUPLICATE_WINDOW_MS = 60_000L

    fun kindOf(reason: Int): CrashReport.Kind? = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> CrashReport.Kind.CRASH
        ApplicationExitInfo.REASON_CRASH_NATIVE -> CrashReport.Kind.NATIVE
        ApplicationExitInfo.REASON_ANR -> CrashReport.Kind.ANR
        else -> null
    }

    /** No stored watermark yet means this is the first run of a build that has one. */
    fun initialWatermark(stored: Long?, installedOrUpdatedAt: Long): Long = stored ?: installedOrUpdatedAt

    fun plan(entries: List<Entry>, watermark: Long, captured: List<Captured>): List<Action> =
        entries.filter { it.timestamp > watermark }
            .sortedBy { it.timestamp }
            .map { e ->
                val kind = kindOf(e.reason)
                when {
                    kind == null -> Action.LogOnly(e)
                    kind == CrashReport.Kind.CRASH && captured.any {
                        it.pid == e.pid && abs(it.at - e.timestamp) <= DUPLICATE_WINDOW_MS
                    } -> Action.Duplicate(e)
                    else -> Action.Report(e, kind)
                }
            }

    /**
     * Runs [process] over [actions] in order and saves the watermark after each one that
     * went through. Stops at the first that throws. Returns the watermark it ended on.
     */
    fun drain(actions: List<Action>, watermark: Long, process: (Action) -> Unit, save: (Long) -> Unit): Long {
        var mark = watermark
        for (a in actions) {
            try {
                process(a)
            } catch (_: Exception) {
                return mark
            }
            mark = a.entry.timestamp
            save(mark)
        }
        return mark
    }

    /** For the log line: `REASON_LOW_MEMORY` reads better than `3`. */
    fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_UNKNOWN -> "unknown"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit self"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_ANR -> "not responding"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization failure"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission change"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource use"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user requested"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user stopped"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency died"
        ApplicationExitInfo.REASON_OTHER -> "other"
        ApplicationExitInfo.REASON_FREEZER -> "freezer"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "package state change"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "package updated"
        else -> "reason $reason"
    }
}
