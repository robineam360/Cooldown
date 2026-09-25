package com.robin.claudeusage.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * One successful fetch, flattened for trend math.
 *
 * CCRM-73 (Model Cap Chart): [capPcts] / [capResets] carry every per-model weekly cap
 * (`Fable`, `Opus`, …) keyed by its display name, so the 7-day chart can draw a cap's
 * curve as well as the pool's. Lines written before 2026-09-17 have neither map — they
 * read back empty, and every existing reader is untouched.
 */
data class HistoryPoint(
    val at: Long,               // epoch millis of the fetch
    val sessionPct: Double?,
    val sessionResetAt: Long,   // window identity (its resets_at); 0 = not started
    val weeklyPct: Double?,
    val weeklyResetAt: Long,
    val capPcts: Map<String, Double> = emptyMap(),
    val capResets: Map<String, Long> = emptyMap(),
)

/**
 * Usage history, one JSONL file per profile in filesDir. Kept just past 7 days
 * so the weekly window always has a full curve. At 15-minute polls that's a few
 * hundred short lines, so each record rewrites the pruned file atomically
 * (temp file + rename) rather than risking a torn append.
 *
 * The line format lives in the companion as pure functions ([encode], [parsePoint])
 * so the round trip is unit-testable without a Context.
 */
class HistoryStore(context: Context) {

    private val dir: File = context.applicationContext.filesDir

    companion object {
        private const val MAX_AGE_MS = 8L * 24 * 60 * 60_000L

        /** One JSONL line for [data] fetched at [at]. Absent fields are omitted, never null. */
        fun encode(data: UsageData, at: Long): String = JSONObject().apply {
            put("t", at)
            data.session?.percent?.let { put("sp", it) }
            data.session?.resetsAt?.let { put("sr", it.toEpochMilli()) }
            data.weekly?.percent?.let { put("wp", it) }
            data.weekly?.resetsAt?.let { put("wr", it.toEpochMilli()) }
            // CCRM-73 (Model Cap Chart): per-model caps as two name-keyed maps. Only
            // caps with a percent are recorded; a cap with a percent but no reset gets a
            // percent and no reset, exactly like the pool windows above.
            val pcts = JSONObject()
            val resets = JSONObject()
            for (cap in data.modelCaps) {
                val pct = cap.window.percent ?: continue
                pcts.put(cap.modelName, pct)
                cap.window.resetsAt?.let { resets.put(cap.modelName, it.toEpochMilli()) }
            }
            if (pcts.length() > 0) put("mc", pcts)
            if (resets.length() > 0) put("mr", resets)
        }.toString()

        /** The inverse of [encode]; null for a blank, torn or timestamp-less line. */
        fun parsePoint(line: String): HistoryPoint? = try {
            val o = JSONObject(line)
            val t = o.optLong("t")
            if (t <= 0) null else HistoryPoint(
                at = t,
                sessionPct = if (o.has("sp")) o.optDouble("sp") else null,
                sessionResetAt = o.optLong("sr", 0L),
                weeklyPct = if (o.has("wp")) o.optDouble("wp") else null,
                weeklyResetAt = o.optLong("wr", 0L),
                capPcts = o.optJSONObject("mc")?.let { m ->
                    m.keys().asSequence().associateWith { m.getDouble(it) }
                } ?: emptyMap(),
                capResets = o.optJSONObject("mr")?.let { m ->
                    m.keys().asSequence().associateWith { m.getLong(it) }
                } ?: emptyMap(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun file(profile: Profile) = File(dir, "usage-history-${profile.key}.jsonl")

    fun record(profile: Profile, data: UsageData, at: Long) {
        val line = encode(data, at)
        val kept = readLines(profile).filter { timestampOf(it) > at - MAX_AGE_MS }
        writeAtomically(file(profile), kept + line)
    }

    /** What the chart and the estimates draw: the file, or R8's series while it is on. */
    fun points(profile: Profile): List<HistoryPoint> =
        if (SyntheticSeries.isOn) SyntheticSeries.points(emptyList()) else realPoints(profile)

    fun realPoints(profile: Profile): List<HistoryPoint> =
        readLines(profile).mapNotNull { parsePoint(it) }.sortedBy { it.at }

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
            // History is best-effort; a failed write only costs one data point.
        }
    }

    private fun timestampOf(line: String): Long = try {
        JSONObject(line).optLong("t")
    } catch (_: Exception) {
        0L
    }
}
