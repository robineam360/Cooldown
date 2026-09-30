package com.robin.claudeusage.diag

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * CCRM-85 (Crash Capture): the words on the next-launch card and the Diagnostics card,
 * exactly as the approved wireframe (rev B, 2026-09-30) draws them. Pure.
 */
object CrashCopy {

    /** "today at 9:41", "yesterday at 21:07", otherwise "on 28 Sep". */
    fun whenText(at: Long, now: Long, zone: ZoneId, use24h: Boolean): String {
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val clock = DateTimeFormatter.ofPattern(if (use24h) "H:mm" else "h:mm a", Locale.US)
            .format(Instant.ofEpochMilli(at).atZone(zone))
        return when (ChronoUnit.DAYS.between(day, today)) {
            0L -> "today at $clock"
            1L -> "yesterday at $clock"
            else -> "on " + DateTimeFormatter.ofPattern("d MMM", Locale.US).format(day)
        }
    }

    /** The newest report names the card. */
    fun title(newestKind: CrashReport.Kind, whenText: String): String =
        if (newestKind == CrashReport.Kind.ANR) "Cooldown stopped responding $whenText"
        else "Cooldown crashed $whenText"

    fun detail(count: Int, newestHasTrace: Boolean): String = when {
        count >= 3 -> "$count reports on this phone. Share sends all $count, newest first."
        count == 2 -> "2 reports on this phone. Share sends both, newest first."
        newestHasTrace -> "A report is saved on this phone. It goes nowhere unless you share it."
        else -> "Android kept no trace, so the report has only the time, the reason and the app version."
    }

    /** The Diagnostics card's line. [newestWhen] is [whenText] of the newest report. */
    fun diagnostics(count: Int, newestWhen: String): String =
        "$count on this phone · newest $newestWhen · each is kept for 30 days. " +
            "A report never contains tokens, emails or exception messages."

    fun shareSubject(versionName: String): String = "Cooldown crash report (v$versionName)"
}
