package com.robin.claudeusage.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import kotlin.math.pow

/**
 * Rule R8 (CCRM-15 (Above-Pace Verification)): a synthetic usage series, so the states
 * real accounts rarely reach — above pace, at 100%, no reading — can be looked at on the
 * phone on demand instead of waiting for them.
 *
 * **Process-wide and in memory only.** The mode lives in this object and nowhere else: it
 * is never written to a store, so process death resets it to [Mode.OFF] and the next
 * redraw of every surface is real data — the safe direction. It is applied at the two read
 * seams every surface draws from, [UsageCache.snapshot] and [HistoryStore.points], so the
 * chart, the bars, the rings, the notification and the widgets all render one series. The
 * paths that *act* on usage — reset pings and alerts (`Alerts.evaluate`), the reset-check
 * scheduler (`Polling`) and the fetch itself — read [UsageCache.realSnapshot] instead, so a
 * synthetic 100% never posts a real alert or schedules a real poll.
 *
 * Only the usage numbers are synthetic. Sign-in state stays real, so an account that is
 * signed out still reads as signed out.
 *
 * The series is anchored at the moment the mode was switched on: window identities
 * (`resetsAt`) and the history curve stay fixed while it is on, so the chart's window
 * binding holds and nothing churns on the 5-second tick.
 */
object SyntheticSeries {

    enum class Mode(val label: String) {
        OFF("Off"),
        ABOVE_PACE("Above pace"),
        AT_100("At 100%"),
        NO_DATA("No data"),
    }

    private val _mode = MutableStateFlow(Mode.OFF)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    @Volatile
    private var anchorMs = 0L

    val isOn: Boolean get() = _mode.value != Mode.OFF

    fun set(mode: Mode, nowMs: Long = System.currentTimeMillis()) {
        anchorMs = nowMs
        _mode.value = mode
    }

    private const val SESSION_MS = 5 * 3_600_000L
    private const val WEEKLY_MS = 7 * 24 * 3_600_000L
    private const val SAMPLE_MS = 15 * 60_000L

    /**
     * One window's shape: [pct] now, [elapsed] of the window gone (0..1). The wireframe's
     * numbers (rev D §9c): above pace reads 62% at 45% elapsed and 31% at 22%.
     */
    private class Shape(val pct: Double, val elapsed: Double)

    private fun shapes(mode: Mode): Pair<Shape, Shape>? = when (mode) {
        Mode.ABOVE_PACE -> Shape(62.0, 0.45) to Shape(31.0, 0.22)
        Mode.AT_100 -> Shape(100.0, 0.60) to Shape(100.0, 0.80)
        Mode.OFF, Mode.NO_DATA -> null
    }

    private fun sessionResetAt(s: Shape) = anchorMs + ((1 - s.elapsed) * SESSION_MS).toLong()
    private fun weeklyResetAt(w: Shape) = anchorMs + ((1 - w.elapsed) * WEEKLY_MS).toLong()

    /** [real] unchanged while off; otherwise the same snapshot carrying the series. */
    fun apply(real: Snapshot): Snapshot {
        val mode = _mode.value
        if (mode == Mode.OFF) return real
        val (s, w) = shapes(mode) ?: return real.copy(
            rawJson = null, fetchedAt = 0L, lastStatus = "Never fetched",
            lastStatusKind = ErrorKind.INTERNAL.key,
            synthetic = true, syntheticData = null,
        )
        val data = UsageData(
            session = UsageWindow(s.pct, Instant.ofEpochMilli(sessionResetAt(s)), null),
            weekly = UsageWindow(w.pct, Instant.ofEpochMilli(weeklyResetAt(w)), null),
            modelCaps = emptyList(),
        )
        return real.copy(
            fetchedAt = anchorMs, lastStatus = "OK", lastAttemptAt = anchorMs,
            lastStatusKind = ErrorKind.INTERNAL.key,
            synthetic = true, syntheticData = data,
        )
    }

    /**
     * [real] unchanged while off; otherwise the series' own 15-minute samples over the
     * current weekly window, up to the anchor. The curve is `pct · x^1.5` of each window's
     * progress, so it starts under the even-pace diagonal and crosses it — the amber
     * overshoot fill has a crossing point to start from. Earlier 5-hour windows inside the
     * week peak at 40%, so the week's history reads as ordinary days.
     */
    fun points(real: List<HistoryPoint>): List<HistoryPoint> {
        val mode = _mode.value
        if (mode == Mode.OFF) return real
        val (s, w) = shapes(mode) ?: return emptyList()
        val sReset = sessionResetAt(s)
        val wReset = weeklyResetAt(w)
        val wStart = wReset - WEEKLY_MS
        val out = ArrayList<HistoryPoint>()
        var t = wStart + SAMPLE_MS
        while (t <= anchorMs) {
            // Which 5-hour window t sits in, counting back from the current one.
            val back = if (t > sReset - SESSION_MS) 0L else (sReset - SESSION_MS - t) / SESSION_MS + 1
            val thisReset = sReset - back * SESSION_MS
            val sx = ((t - (thisReset - SESSION_MS)).toDouble() / SESSION_MS).coerceIn(0.0, 1.0)
            val sPct = if (back == 0L) curve(s.pct, sx / s.elapsed) else curve(40.0, sx)
            val wx = (t - wStart).toDouble() / WEEKLY_MS
            out += HistoryPoint(
                at = t,
                sessionPct = sPct, sessionResetAt = thisReset,
                weeklyPct = curve(w.pct, wx / w.elapsed), weeklyResetAt = wReset,
            )
            t += SAMPLE_MS
        }
        return out
    }

    private fun curve(end: Double, progress: Double): Double =
        (end * progress.coerceIn(0.0, 1.0).pow(1.5) * 10).toLong() / 10.0
}
