// Ported from CooldownMac sources/ccooldown-core/Projection.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// One raw usage sample, as persisted for the trend chart.
public struct HistoryPoint: Sendable, Equatable {
    public let atMs: Int64
    public let sessionPct: Double?
    /// Window identity. 0 means "window not started", NOT "resets at the epoch".
    public let sessionResetAtMs: Int64
    public let weeklyPct: Double?
    public let weeklyResetAtMs: Int64

    public init(atMs: Int64, sessionPct: Double?, sessionResetAtMs: Int64, weeklyPct: Double?, weeklyResetAtMs: Int64) {
        self.atMs = atMs
        self.sessionPct = sessionPct
        self.sessionResetAtMs = sessionResetAtMs
        self.weeklyPct = weeklyPct
        self.weeklyResetAtMs = weeklyResetAtMs
    }
}

/// Linear burn-rate estimate for one window instance. See contract/BEHAVIOR-SPEC.md §3-4.
public enum Projection {

    /// Don't extrapolate from less than this much observed time...
    static let minSpanMs: Int64 = 20 * 60_000
    /// ...or less than this much observed movement.
    static let minDeltaPct: Double = 1.0

    /// How far a sample's `resets_at` may sit from the window's and still count as the
    /// same window. A quarter of the window is generous for drift while staying
    /// unambiguous: a *genuine* reset moves `resets_at` by a full window length — four
    /// times this — so the previous window can never leak into the current one.
    public static func tolerance(windowLengthMs: Int64) -> Int64 { windowLengthMs / 4 }

    public struct Estimate: Sendable, Equatable {
        public let ratePctPerHour: Double
        /// When usage reaches 100% at this pace; nil = not before the reset.
        public let hitsLimitAtMs: Int64?
        /// Projected percent at the reset moment, capped at 100.
        public let pctAtReset: Double
    }

    /// `samples` are (epochMs, percent) for the current window, in any order.
    ///
    /// Returns nil when there isn't enough signal to be honest — too few samples, too
    /// short a span, too little movement, or a fit that comes out flat or negative. "No
    /// projection" is a first-class outcome; never substitute a guess.
    ///
    /// The rate is a least-squares fit over *every* sample rather than the first-to-last
    /// slope: with a full window of history, one early burst followed by idle time would
    /// otherwise set the pace for the rest of the window. The projection is still
    /// *anchored* on the latest reading, so the forecast tail joins the point the chart
    /// actually draws.
    public static func estimate(samples: [(Int64, Double)], resetAtMs: Int64) -> Estimate? {
        guard samples.count >= 2 else { return nil }
        let sorted = samples.sorted { $0.0 < $1.0 }
        let (firstAt, firstPct) = sorted[0]
        let (lastAt, lastPct) = sorted[sorted.count - 1]
        guard lastAt - firstAt >= minSpanMs, lastPct - firstPct >= minDeltaPct else { return nil }

        let count = Double(sorted.count)
        let meanX = sorted.reduce(0.0) { $0 + Double($1.0 - firstAt) } / count
        let meanY = sorted.reduce(0.0) { $0 + $1.1 } / count
        var sxy = 0.0
        var sxx = 0.0
        for (at, pct) in sorted {
            let dx = Double(at - firstAt) - meanX
            sxy += dx * (pct - meanY)
            sxx += dx * dx
        }
        let ratePerMs = sxx > 0 ? sxy / sxx : (lastPct - firstPct) / Double(lastAt - firstAt)
        // A fit can be flat or negative even when the endpoints rose (a burst then a
        // plateau, or a decline). No honest projection to make from that.
        guard ratePerMs > 0, ratePerMs.isFinite else { return nil }

        let msToLimit = (100.0 - lastPct) / ratePerMs
        let msToReset = Double(resetAtMs - lastAt)
        let hitsLimitAtMs: Int64? =
            (msToLimit.isFinite && msToLimit >= 0 && msToLimit <= msToReset)
            ? lastAt + Int64(msToLimit)
            : nil

        return Estimate(
            ratePctPerHour: ratePerMs * 3_600_000,
            hitsLimitAtMs: hitsLimitAtMs,
            pctAtReset: min(100.0, lastPct + ratePerMs * msToReset)
        )
    }

    /// Session samples belonging to the window that resets *around* `resetAtMs`.
    public static func sessionSamples(history: [HistoryPoint], resetAtMs: Int64, windowLengthMs: Int64) -> [(Int64, Double)] {
        bind(history, resetAtMs, windowLengthMs, { $0.sessionPct }, { $0.sessionResetAtMs })
    }

    /// Weekly samples belonging to the window that resets *around* `resetAtMs`.
    public static func weeklySamples(history: [HistoryPoint], resetAtMs: Int64, windowLengthMs: Int64) -> [(Int64, Double)] {
        bind(history, resetAtMs, windowLengthMs, { $0.weeklyPct }, { $0.weeklyResetAtMs })
    }

    /// Binds by **proximity, never equality**. The server slides `resets_at` forward on
    /// nearly every poll — one live 7-day window produced 561 distinct values across 672
    /// samples — so exact matching bound 1-2 samples out of hundreds and silently killed
    /// the trend chart (CCBG-2).
    private static func bind(
        _ history: [HistoryPoint],
        _ resetAtMs: Int64,
        _ windowLengthMs: Int64,
        _ pct: (HistoryPoint) -> Double?,
        _ reset: (HistoryPoint) -> Int64
    ) -> [(Int64, Double)] {
        let tol = tolerance(windowLengthMs: windowLengthMs)
        return history.compactMap { point -> (Int64, Double)? in
            let resetsAt = reset(point)
            guard let value = pct(point), resetsAt > 0, abs(resetsAt - resetAtMs) <= tol else { return nil }
            return (point.atMs, value)
        }
        .sorted { $0.0 < $1.0 }
    }
}

// MARK: - Pace severity (CCM-17)

extension Projection {

    /// "Cutting it close": projected to land at or above this percent at the reset.
    public static let paceCloseAtReset: Double = 85.0

    /// No verdict under this much usage — extrapolating from noise late in a
    /// barely-touched window says nothing worth printing.
    public static let paceMinUsed: Double = 5.0

    /// Young-window suppression: no verdict until this much of the period has elapsed.
    /// A 5-hour window at 2% after four minutes projects to 150% and means nothing.
    /// Floors at a minute so a short window still gets one.
    public static func paceMinElapsedMs(windowLengthMs: Int64) -> Int64 {
        max(windowLengthMs / 100, 60_000)
    }

    /// Where this window is *heading*, named. Ordered — CCM-19 [Alerts] compares by
    /// severity, and hysteresis needs an ordering it can trust.
    public enum PaceSeverity: Int, Sendable, Equatable, Comparable {
        case untracked = 0, healthy, close, runningOut, spent
        public static func < (a: Self, b: Self) -> Bool { a.rawValue < b.rawValue }
    }

    /// *Why* there is no verdict. Three distinct reasons, and they need three distinct
    /// sentences: a reader shown "too early" four hours into a window would rightly call
    /// it a bug.
    public enum PaceSuppression: Sendable, Equatable {
        /// The window has barely opened.
        case youngWindow
        /// Elapsed enough, but too little has been used to extrapolate from.
        case tooLittleUsage
        /// `estimate` refused to project — too few samples, too short a span, too little
        /// movement, or a flat/negative fit (BEHAVIOR-SPEC §3).
        case noProjection

        /// The one place this is phrased. The chart's note and the pace line read from
        /// here, so the two surfaces cannot word the same silence differently.
        public var explanation: String {
            switch self {
            case .youngWindow: "Too early in this window to project a pace"
            case .tooLittleUsage: "Not enough usage yet to project a pace"
            case .noProjection: "Not enough readings yet to project a pace"
            }
        }
    }

    /// A severity and, when there isn't one, the reason. Invariant: `suppression` is
    /// non-nil exactly when `severity == .untracked`.
    public struct PaceOutlook: Sendable, Equatable {
        public let severity: PaceSeverity
        public let suppression: PaceSuppression?

        init(_ severity: PaceSeverity, _ suppression: PaceSuppression? = nil) {
            self.severity = severity
            self.suppression = suppression
        }
    }

    /// The ladder, from the same inputs the chart draws — which is why the chart, the
    /// wording and (later) the alerts agree for free.
    ///
    /// Order matters, and it matches Android's `Projection.paceSeverity` (CCRM-21) rung
    /// for rung so the two products can't disagree about what "cutting it close" means:
    ///
    /// - **The two floors come first, ahead of even `.spent`.** Right after a reset the
    ///   server can still report the *old* window's percent for a poll or two, and a
    ///   young window reading 100% is far more likely to be that carryover than a real
    ///   exhausted window. "Too early to say" is the honest answer there.
    /// - **A nil estimate can still be `.spent`, never `.close` or `.runningOut`.** No
    ///   projection, no verdict about the future — but "you have nothing left" needs no
    ///   projection to assert.
    ///
    /// One deliberate difference from Android: with no estimate and usage below the spent
    /// line they return `HEALTHY`, we return `.untracked(.noProjection)`. Theirs feeds
    /// alerts, where both answers mean "say nothing"; ours feeds copy, where the two are
    /// not the same sentence. Nothing an alert would do differs.
    public static func outlook(
        usedPct: Double, elapsedMs: Int64, windowLengthMs: Int64, estimate: Estimate?
    ) -> PaceOutlook {
        if elapsedMs < paceMinElapsedMs(windowLengthMs: windowLengthMs) {
            return PaceOutlook(.untracked, .youngWindow)
        }
        if usedPct < paceMinUsed { return PaceOutlook(.untracked, .tooLittleUsage) }
        // Same 99.5 the history bars call "exhausted" — one number, one source.
        if usedPct >= HistoryStats.hitLimitThreshold { return PaceOutlook(.spent) }
        guard let estimate else { return PaceOutlook(.untracked, .noProjection) }
        if estimate.hitsLimitAtMs != nil { return PaceOutlook(.runningOut) }
        if estimate.pctAtReset >= paceCloseAtReset { return PaceOutlook(.close) }
        return PaceOutlook(.healthy)
    }

    /// Elapsed milliseconds for a window that resets at `resetsAtMs`, clamped at zero —
    /// `resets_at` drifts forward on nearly every poll (CCBG-2), so a freshly-opened
    /// window can compute a negative elapsed and must not read as "old enough".
    public static func elapsedMs(resetsAtMs: Int64, windowLengthMs: Int64, nowMs: Int64) -> Int64 {
        max(0, nowMs - (resetsAtMs - windowLengthMs))
    }
}
