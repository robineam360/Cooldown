package com.robin.claudeusage.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Long-term log of closed usage windows, one JSONL file per profile. Unlike
 * [HistoryStore] (raw samples, ~8 days), this keeps one compact line per window
 * that finished — enough to draw the per-session and per-week history bars going
 * back a year. A line is written when a window rolls over (see Alerts.checkReset).
 */
class SessionLog(context: Context) {

    /**
     * kind is "session" (5-hour) or "weekly" (7-day). resetAt is the window identity.
     *
     * [plan] and [modelCap] are forward-only tags added for CCRM-70 (Plan Fit) — cheap to
     * write now, impossible to backfill later, so they go in regardless of whether any UI
     * reads them yet:
     * - [plan] is the plan label (and tier, if the caller chooses to fold it in, e.g.
     *   "Max 5x") in force when this window closed, so a plan change is visible in the
     *   data rather than guessed.
     * - [modelCap] is the name of a per-model weekly cap that hit 100 during this window
     *   (e.g. "Opus"), or null if none did. Without it a Max user whose pool reads 40%
     *   while Opus is capped weekly would look under-tiered.
     *
     * Both are null on every record written before this field existed, and on any record
     * a caller writes without knowing them — [parse] treats a missing tag as absent, never
     * as an error. [PlanFit] is the reader that turns an absent [plan] into "the plan first
     * observed after the update" rather than a break in the series.
     */
    data class Record(
        val kind: String,
        val resetAt: Long,
        val peakPct: Double,
        val hitLimit: Boolean,
        val plan: String? = null,
        val modelCap: String? = null,
    )

    private val dir: File = context.applicationContext.filesDir

    companion object {
        const val SESSION = "session"
        const val WEEKLY = "weekly"
        private const val MAX_AGE_MS = 366L * 24 * 60 * 60_000L
    }

    private fun file(profile: Profile) = File(dir, "usage-sessions-${profile.key}.jsonl")

    /**
     * Records one closed window. No-ops if this exact window was already logged.
     *
     * [plan] and [modelCap] are the CCRM-70 (Plan Fit) tags — both optional, both omitted
     * from the line entirely when null so old and new records read identically to a caller
     * that only looks at `k`/`r`/`p`/`h`.
     */
    fun record(
        profile: Profile,
        kind: String,
        resetAt: Long,
        peakPct: Double,
        hitLimit: Boolean,
        plan: String? = null,
        modelCap: String? = null,
    ) {
        if (records(profile).any { it.kind == kind && it.resetAt == resetAt }) return
        val line = JSONObject().apply {
            put("k", kind)
            put("r", resetAt)
            put("p", peakPct)
            put("h", hitLimit)
            if (!plan.isNullOrEmpty()) put("pl", plan)
            if (!modelCap.isNullOrEmpty()) put("mc", modelCap)
        }.toString()
        val now = System.currentTimeMillis()
        val kept = readLines(profile).filter { resetAtOf(it) > now - MAX_AGE_MS }
        writeAtomically(file(profile), kept + line)
    }

    fun records(profile: Profile): List<Record> =
        readLines(profile).mapNotNull { parse(it) }.sortedBy { it.resetAt }

    fun clear(profile: Profile) {
        file(profile).delete()
    }

    private fun readLines(profile: Profile): List<String> = try {
        val f = file(profile)
        if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun writeAtomically(target: File, lines: List<String>) {
        try {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(lines.joinToString("\n") + "\n")
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        } catch (_: Exception) {
            // Best-effort; a failed write only costs one session record.
        }
    }

    private fun resetAtOf(line: String): Long = try {
        JSONObject(line).optLong("r")
    } catch (_: Exception) {
        0L
    }

    private fun parse(line: String): Record? = try {
        val o = JSONObject(line)
        val kind = o.optString("k")
        val resetAt = o.optLong("r")
        if (kind.isEmpty() || resetAt <= 0) null
        else Record(
            kind, resetAt, o.optDouble("p", 0.0), o.optBoolean("h", false),
            plan = o.optString("pl", "").ifEmpty { null },
            modelCap = o.optString("mc", "").ifEmpty { null },
        )
    } catch (_: Exception) {
        null
    }
}
