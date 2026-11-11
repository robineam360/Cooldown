package com.robin.claudeusage.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * CCRM-113 (Sample Port): the reviewer's sample-data mode — three made-up accounts to look around, held in memory only
 * (BEHAVIOR-MOBILE §7; the iPhone app's `SampleMode.swift` is the same mode).
 *
 * **A process-wide flag, never written**, like [SyntheticSeries]: process death, a reboot or an update returns to the
 * real app. While it is on, the app's screens are handed [repo] — a [UsageRepository] over *twin* stores (an in-memory
 * cache, registry, history and session log, no credentials) — instead of the real one. That is the whole mechanism:
 *
 *  - **C1-1 real work keeps running.** Polling, reset checks and alerts build their own real repositories and never
 *    read this object; a request in flight completes into the real store. The sample repository never fetches.
 *  - **C1-2 settings split by reader.** The settings copy is the twin cache's own preferences; `notify/`, `widgets/`,
 *    `alerts/` and polling read the real file, and [com.robin.claudeusage.notify.Surfaces.refresh] refuses a sample cache.
 *  - **C1-3 no late write crosses over.** Every editor holds the repository it was opened with, so a commit that
 *    arrives late reaches only the sample copy, which is dropped on [exit]. [enter] and [exit] also re-key the whole
 *    tree, which closes every open dialog and drops its pending edit.
 *  - **C1-4 the debug series pauses.** [enter] suspends [SyntheticSeries] in memory, [exit] restores it as it was.
 */
object SampleMode {

    /** What the Settings account card says in place of the status dot. */
    const val ACCOUNT_WORD = "Sample"
    const val ENTRY_TITLE = "Try with sample data"
    const val ENTRY_DETAIL = "Three made-up accounts to look around. Nothing is saved or sent."

    /** The banner: "● Sample data" in violet, then the rest in the ink; the rest drops first when the line is tight. */
    const val BANNER_HEAD = "Sample data"
    const val BANNER_TAIL = " — made-up accounts"
    const val STATUS_LINE = "Sample data · nothing is fetched"

    /** #7C4DFF, Android's synthetic colour (the share band, the banner), and its lighter dark-mode text. */
    const val VIOLET = 0xFF7C4DFF
    const val VIOLET_ON_DARK = 0xFFB388FF

    private class Session(val repo: UsageRepository, val resumeKey: String?)

    private val _active = MutableStateFlow(false)

    /** True while the sample is showing; the app's root reads it to pick the repository. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    @Volatile private var session: Session? = null

    val isOn: Boolean get() = _active.value

    /** The sample's repository while it is on, else null. */
    fun repo(): UsageRepository? = session?.repo

    /**
     * Enters the sample. [resumeKey] is the account (or null for the All tab) the user was on, so [exit] can return there.
     * A second call while on changes nothing.
     */
    fun enter(context: Context, resumeKey: String? = null, nowMs: Long = System.currentTimeMillis()) {
        if (session != null) return
        session = Session(build(context, nowMs, UsageCache(context)), resumeKey)
        SyntheticSeries.suspendForSample()
        _active.value = true
    }

    /** Leaves the sample, dropping its stores; returns the key to resume on (null = the first screen's own choice). */
    fun exit(): String? {
        val s = session ?: return null
        session = null
        _active.value = false
        SyntheticSeries.resumeAfterSample()
        return s.resumeKey
    }

    /** What the sample's three accounts are, in tab order: key, label, provider, tag, plan (as stored). */
    class Account(val profile: Profile, val plan: String, val tier: String?)

    val accounts: List<Account> = listOf(
        Account(Profile("s0", 0, "Teams", Provider.CLAUDE, AccountTag.WORK.key), "Max", "default_claude_max_5x"),
        Account(Profile("s1", 1, "Personal", Provider.CHATGPT, AccountTag.PERSONAL.key), "plus", null),
        Account(Profile("s2", 2, "Lab", Provider.ANTIGRAVITY, AccountTag.WORK.key), "Pro", null),
    )

    private const val SESSION_MS = 5 * 3_600_000L
    private const val WEEK_MS = 7 * 24 * 3_600_000L
    private const val STEP_MS = 30 * 60_000L

    /** One account's made-up reading: how far through each window, and how much used. */
    private class Shape(
        val sessionPct: Double, val sessionElapsed: Double,
        val weeklyPct: Double, val weeklyElapsed: Double,
        /** The current 5-hour window's curve, as (elapsed, pct) — it ends on the headline figure. */
        val curve: List<Pair<Double, Double>>,
        val fableCap: Double? = null,
        val credits: Boolean = false,
    )

    // iOS's DemoData numbers, so both stores' review notes describe the same three accounts.
    private val shapes = mapOf(
        "s0" to Shape(
            46.0, 0.46, 18.0, 0.43,
            // Reads 46% at 46% elapsed and eases off: the 5-hour window projects to finish under its limit, so Teams is not held.
            listOf(0.0 to 0.0, 0.06 to 9.0, 0.11 to 16.0, 0.18 to 24.0, 0.24 to 29.0, 0.30 to 34.0, 0.36 to 39.0, 0.41 to 43.0),
            fableCap = 5.0, credits = true,
        ),
        "s1" to Shape(
            26.0, 0.68, 9.0, 0.43,
            listOf(0.0 to 0.0, 0.2 to 0.0, 0.29 to 4.0, 0.38 to 9.0, 0.49 to 16.0, 0.55 to 18.0, 0.64 to 24.0),
        ),
        "s2" to Shape(
            62.0, 0.40, 64.0, 0.43,
            listOf(0.0 to 0.0, 0.05 to 6.0, 0.10 to 13.0, 0.15 to 21.0, 0.20 to 30.0, 0.25 to 38.0, 0.30 to 47.0, 0.36 to 55.0),
        ),
    )

    /** The sample's repository, built over fresh in-memory twins and filled for [nowMs]. Public for the tests. */
    internal fun build(context: Context, nowMs: Long, real: UsageCache? = null): UsageRepository {
        val registry = ProfileRegistry.inMemory(accounts.map { it.profile })
        // "Settings changed in the sample go to an in-memory copy seeded from the user's own."
        val prefs = MemoryPrefs().also { real?.copyAppearanceTo(it) }
        val cache = UsageCache.sample(context, prefs, registry)
        val history = HistoryStore.inMemory()
        val log = SessionLog.inMemory()
        val repo = UsageRepository.sample(context, cache, registry, history, log)
        for (a in accounts) fill(a, shapes.getValue(a.profile.key), nowMs, cache, history, log)
        return repo
    }

    private fun fill(a: Account, s: Shape, now: Long, cache: UsageCache, history: HistoryStore, log: SessionLog) {
        val profile = a.profile
        val sessionReset = now + ((1 - s.sessionElapsed) * SESSION_MS).toLong()
        val weeklyReset = now + ((1 - s.weeklyElapsed) * WEEK_MS).toLong()
        cache.saveSuccess(profile, rawJson(profile.provider, s, sessionReset, weeklyReset, now), now - 3 * 60_000L)
        cache.setTokenMeta(profile, 0L, a.plan, a.tier)
        cache.setEmail(profile, null)

        // The 7-day trend: every 30 minutes back to the week's start, each in its own 5-hour window. The current window
        // follows [Shape.curve]; earlier ones peak at a modest, varied height so the week reads as ordinary days.
        val weekStart = weeklyReset - WEEK_MS
        val lines = ArrayList<String>()
        var t = weekStart + STEP_MS
        while (t < now) {
            lines += HistoryStore.encode(pointAt(profile, s, t, now, sessionReset, weeklyReset), t)
            t += STEP_MS
        }
        lines += HistoryStore.encode(pointAt(profile, s, now, now, sessionReset, weeklyReset), now)
        history.seedMemory(profile, lines)

        // Closed windows for the History screen: twelve 5-hour windows and four weeks, peaks varied, one that hit its limit.
        val seed = profile.slot + 1
        for (i in 1..12) {
            val peak = 18.0 + ((i * 37 * seed) % 61)
            log.record(profile, SessionLog.SESSION, sessionReset - i * SESSION_MS, peak, hitLimit = i == 5 && seed == 3, plan = a.plan)
        }
        for (i in 1..4) {
            val peak = 30.0 + ((i * 29 * seed) % 55)
            log.record(profile, SessionLog.WEEKLY, weeklyReset - i * WEEK_MS, peak, hitLimit = false, plan = a.plan)
        }
    }

    private fun pointAt(profile: Profile, s: Shape, t: Long, now: Long, sessionReset: Long, weeklyReset: Long): UsageData {
        val back = if (t > sessionReset - SESSION_MS) 0L else (sessionReset - SESSION_MS - t) / SESSION_MS + 1
        val thisReset = sessionReset - back * SESSION_MS
        val x = ((t - (thisReset - SESSION_MS)).toDouble() / SESSION_MS).coerceIn(0.0, 1.0)
        val sessionPct = if (back == 0L) interpolate(s.curve + (s.sessionElapsed to s.sessionPct), x)
        else 38.0 * x.pow(1.4) * (0.55 + 0.45 * ((back * 7 + profile.slot) % 5) / 4.0) + 2.0
        val wx = ((t - (weeklyReset - WEEK_MS)).toDouble() / WEEK_MS).coerceIn(0.0, 1.0)
        val weeklyPct = s.weeklyPct * (wx / s.weeklyElapsed).coerceIn(0.0, 1.0).pow(1.15)
        val weeklyWindow = UsageWindow(weeklyPct, Instant.ofEpochMilli(weeklyReset), null)
        return UsageData(
            session = UsageWindow(min(sessionPct, 100.0), Instant.ofEpochMilli(thisReset), null),
            weekly = weeklyWindow,
            modelCaps = s.fableCap?.let { listOf(ModelCap("Fable", UsageWindow(it * (wx / s.weeklyElapsed).coerceIn(0.0, 1.0), Instant.ofEpochMilli(weeklyReset - 60_000L), null))) }
                .orEmpty(),
        )
    }

    /** Piecewise-linear value of [points] (sorted by elapsed) at [x]; flat after the last point. */
    private fun interpolate(points: List<Pair<Double, Double>>, x: Double): Double {
        val sorted = points.sortedBy { it.first }
        if (x <= sorted.first().first) return sorted.first().second
        for (i in 1 until sorted.size) {
            val (x1, y1) = sorted[i]
            if (x <= x1) {
                val (x0, y0) = sorted[i - 1]
                return if (x1 == x0) y1 else y0 + (y1 - y0) * (x - x0) / (x1 - x0)
            }
        }
        return sorted.last().second
    }

    private fun iso(ms: Long): String = Instant.ofEpochMilli(max(ms, 0L)).toString()

    /** The provider's own payload shape, parsed back through the real parser so the sample can never drift from it. */
    private fun rawJson(provider: Provider, s: Shape, sessionReset: Long, weeklyReset: Long, now: Long): String = when (provider) {
        Provider.CLAUDE -> buildString {
            append("{\"limits\":[")
            append("{\"kind\":\"session\",\"percent\":${s.sessionPct},\"resets_at\":\"${iso(sessionReset)}\"},")
            append("{\"kind\":\"weekly_all\",\"percent\":${s.weeklyPct},\"resets_at\":\"${iso(weeklyReset)}\"}")
            if (s.fableCap != null) {
                append(",{\"kind\":\"weekly_scoped\",\"percent\":${s.fableCap},\"resets_at\":\"${iso(weeklyReset - 60_000L)}\",")
                append("\"scope\":{\"model\":{\"display_name\":\"Fable\"}}}")
            }
            append("]")
            if (s.credits) {
                append(",\"spend\":{\"used\":{\"amount_minor\":12390,\"currency\":\"USD\",\"exponent\":2},")
                append("\"limit\":{\"amount_minor\":40000,\"currency\":\"USD\",\"exponent\":2},\"percent\":31,\"severity\":\"normal\"}")
            }
            append("}")
        }
        Provider.ANTIGRAVITY -> {
            fun bucket(id: String, used: Double, reset: Long) =
                "{\"bucketId\":\"$id\",\"remainingFraction\":${1 - used / 100},\"resetTime\":\"${iso(reset)}\"}"
            "{\"groups\":[{\"displayName\":\"Gemini Models\",\"buckets\":[" +
                "${bucket("gemini-5h", s.sessionPct, sessionReset)},${bucket("gemini-weekly", s.weeklyPct, weeklyReset)}]}]}"
        }
        Provider.CHATGPT ->
            "{\"plan_type\":\"plus\",\"rate_limit\":{\"allowed\":true,\"limit_reached\":false," +
                "\"primary_window\":{\"used_percent\":${s.sessionPct.toInt()},\"limit_window_seconds\":18000," +
                "\"reset_after_seconds\":${(sessionReset - now) / 1000},\"reset_at\":${sessionReset / 1000}}," +
                "\"secondary_window\":{\"used_percent\":${s.weeklyPct.toInt()},\"limit_window_seconds\":604800," +
                "\"reset_after_seconds\":${(weeklyReset - now) / 1000},\"reset_at\":${weeklyReset / 1000}}}}"
    }
}
