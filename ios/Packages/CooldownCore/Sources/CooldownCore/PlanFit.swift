import Foundation

/// CCRM-70 (Plan Fit), ported from Android `PlanFit.kt` + `PlanFitCopy.kt` (CCRM-94 (iOS App)).
/// Is the plan a profile is on the right size, read off the last eight *closed* weekly windows
/// on the plan currently in force. Pure math, no UI.
///
/// iOS limitation: an iOS `HistoryStats.Record` carries no plan tag and no model-cap tag, so the
/// app cannot feed `WeeklyEntry.plan` / `WeeklyEntry.modelCap` yet; tonight it passes nil, which
/// means no plan-change note and the generic "the cap" wording. Both paths are fully
/// implemented and tested from explicit inputs.
public enum PlanFit {
    /// The lookback target: the last eight closed weekly windows on the current plan.
    public static let maxWeeks = 8
    /// No reading is offered on fewer than this many recorded weeks on the current plan.
    public static let minWeeks = 4

    public enum Verdict: Sendable, Equatable {
        case runsOut, smallPart, idle, fits
    }

    public enum Band: Sendable, Equatable {
        case capped, heavy, middle, light
    }

    /// One closed weekly window with its optional plan and per-model-cap tags (nil on iOS tonight).
    public struct WeeklyEntry: Sendable, Equatable {
        public let resetAtMs: Int64
        public let peakPct: Double
        public let hitLimit: Bool
        public let plan: String?
        public let modelCap: String?

        public init(resetAtMs: Int64, peakPct: Double, hitLimit: Bool = false,
                    plan: String? = nil, modelCap: String? = nil) {
            self.resetAtMs = resetAtMs
            self.peakPct = peakPct
            self.hitLimit = hitLimit
            self.plan = plan
            self.modelCap = modelCap
        }
    }

    public struct WeekReading: Sendable, Equatable {
        public let resetAtMs: Int64
        public let peakPct: Double
        public let hitLimit: Bool
        public let modelCap: String?
        public let band: Band
    }

    public struct PlanChange: Sendable, Equatable {
        public let previousPlan: String?
        public let weeksAgo: Int
        public let msAgo: Int64
    }

    public struct Fitted: Sendable, Equatable {
        public let verdict: Verdict
        public let currentPlan: String?
        /// Oldest to newest, count == recordedWeeks.
        public let weeks: [WeekReading]
        public let recordedWeeks: Int
        public let periodTargetWeeks: Int
        public let cappedCount: Int
        public let heavyCount: Int
        public let middleCount: Int
        public let lightCount: Int
        /// 5h cap hits (closed session records with `hitLimit`) inside the period.
        public let fiveHourCapHits: Int
        public let planChange: PlanChange?
    }

    public struct NotEnoughData: Sendable, Equatable {
        public let recordedWeeks: Int
        public let currentPlan: String?
        public let planChange: PlanChange?
    }

    public enum Reading: Sendable, Equatable {
        case notEnoughData(NotEnoughData)
        case fitted(Fitted)

        public var currentPlan: String? {
            switch self {
            case .notEnoughData(let r): return r.currentPlan
            case .fitted(let r): return r.currentPlan
            }
        }
        public var recordedWeeks: Int {
            switch self {
            case .notEnoughData(let r): return r.recordedWeeks
            case .fitted(let r): return r.recordedWeeks
            }
        }
        public var planChange: PlanChange? {
            switch self {
            case .notEnoughData(let r): return r.planChange
            case .fitted(let r): return r.planChange
            }
        }
    }

    /// Convenience over iOS history records: weekly-kind records become untagged entries,
    /// session-kind records feed the 5h line; any other kind is ignored.
    public static func evaluate(records: [HistoryStats.Record], nowMs: Int64) -> Reading {
        let weekly = records.filter { $0.kind == HistoryStats.weeklyKind }.map {
            WeeklyEntry(resetAtMs: $0.resetAtMs, peakPct: $0.peakPct, hitLimit: $0.hitLimit)
        }
        let sessions = records.filter { $0.kind == HistoryStats.sessionKind }
        return evaluate(weekly: weekly, sessions: sessions, nowMs: nowMs)
    }

    /// - Parameters:
    ///   - weekly: closed weekly windows (any order).
    ///   - sessions: the same profile's session-kind records, for the 5h line; empty for a
    ///     provider with no 5-hour window. Non-session kinds are ignored.
    ///   - nowMs: injectable clock.
    public static func evaluate(weekly: [WeeklyEntry],
                                sessions: [HistoryStats.Record] = [],
                                nowMs: Int64) -> Reading {
        if weekly.isEmpty {
            return .notEnoughData(NotEnoughData(recordedWeeks: 0, currentPlan: nil, planChange: nil))
        }

        // Untagged records are treated as belonging to the plan first observed once tagging
        // started, never as an unknown plan of their own.
        let ascendingAll = weekly.sorted { $0.resetAtMs < $1.resetAtMs }
        let firstTaggedPlan = ascendingAll.first { !($0.plan ?? "").isEmpty }?.plan
        func effectivePlan(_ r: WeeklyEntry) -> String? { orNil(r.plan) ?? firstTaggedPlan }

        let descending = weekly.sorted { $0.resetAtMs > $1.resetAtMs }
        let currentPlan = effectivePlan(descending[0])

        var periodDesc: [WeeklyEntry] = []
        var planChange: PlanChange?
        for r in descending {
            if effectivePlan(r) != currentPlan {
                let oldestIncluded = periodDesc[periodDesc.count - 1]
                let changeStartMs = oldestIncluded.resetAtMs - HistoryStats.weekMs
                let msAgo = max(nowMs - changeStartMs, 0)
                planChange = PlanChange(previousPlan: effectivePlan(r),
                                        weeksAgo: Int(msAgo / HistoryStats.weekMs),
                                        msAgo: msAgo)
                break
            }
            periodDesc.append(r)
            if periodDesc.count == maxWeeks { break }
        }

        let weeksAsc = periodDesc.sorted { $0.resetAtMs < $1.resetAtMs }
        let n = weeksAsc.count

        if n < minWeeks {
            return .notEnoughData(NotEnoughData(recordedWeeks: n, currentPlan: currentPlan, planChange: planChange))
        }

        let readings = weeksAsc.map { r -> WeekReading in
            let cap = orNil(r.modelCap)
            return WeekReading(resetAtMs: r.resetAtMs, peakPct: r.peakPct, hitLimit: r.hitLimit,
                               modelCap: cap, band: bandOf(r.peakPct, r.hitLimit, cap))
        }
        let capped = readings.filter { $0.band == .capped }.count
        let heavy = readings.filter { $0.band == .heavy }.count
        let middle = readings.filter { $0.band == .middle }.count
        let light = readings.filter { $0.band == .light }.count

        let verdict: Verdict
        if capped >= ceilDiv(n, 3) || capped + heavy >= ceilDiv(2 * n, 3) {
            verdict = .runsOut
        } else if readings.allSatisfy({ $0.peakPct <= 0.0 && $0.modelCap == nil }) {
            // Checked ahead of smallPart on purpose (as on Android): idle is a subset of it.
            verdict = .idle
        } else if light >= ceilDiv(3 * n, 4) && capped == 0 {
            verdict = .smallPart
        } else {
            verdict = .fits
        }

        let periodStartMs = weeksAsc[0].resetAtMs - HistoryStats.weekMs
        let fiveHourCapHits = sessions.filter {
            $0.kind == HistoryStats.sessionKind && $0.hitLimit
                && $0.resetAtMs >= periodStartMs && $0.resetAtMs <= nowMs
        }.count

        return .fitted(Fitted(verdict: verdict, currentPlan: currentPlan, weeks: readings,
                              recordedWeeks: n, periodTargetWeeks: maxWeeks,
                              cappedCount: capped, heavyCount: heavy, middleCount: middle,
                              lightCount: light, fiveHourCapHits: fiveHourCapHits,
                              planChange: planChange))
    }

    private static func bandOf(_ peak: Double, _ hit: Bool, _ cap: String?) -> Band {
        if hit || peak >= 100.0 || !(cap ?? "").isEmpty { return .capped }
        if peak >= 75.0 { return .heavy }
        if peak >= 25.0 { return .middle }
        return .light
    }

    fileprivate static func ceilDiv(_ a: Int, _ b: Int) -> Int { (a + b - 1) / b }

    private static func orNil(_ s: String?) -> String? {
        guard let s, !s.isEmpty else { return nil }
        return s
    }
}

/// CCRM-70 (Plan Fit): the wording for the tonal block above the weekly bars. Byte-identical to
/// Android `PlanFitCopy.kt`. Pure string assembly over a `PlanFit.Reading`.
public enum PlanFitCopy {
    /// The block's up-to-four lines; `fiveHourLine` is nil when the fourth line doesn't render.
    public struct Lines: Sendable, Equatable {
        public let header: String
        public let reading: String
        public let evidence: String
        public let fiveHourLine: String?
    }

    /// - Parameters:
    ///   - fallbackPlan: the already-known live plan label, used only for the brand-new-account
    ///     case (zero closed weeks); every other nil-plan case leaves the header without a plan.
    ///   - compact: true on the narrow layout: the header drops its period token and an ungapped
    ///     evidence line says "of the last N weeks".
    public static func build(_ reading: PlanFit.Reading, fallbackPlan: String? = nil,
                             compact: Bool = false) -> Lines {
        let header = header(reading, planToken(reading, fallbackPlan), compact)
        switch reading {
        case .notEnoughData(let r):
            return Lines(header: header,
                         reading: notEnoughDataReading(r),
                         evidence: r.planChange != nil
                            ? "The reading restarts after four weeks on the new plan."
                            : "The reading starts after four weeks.",
                         fiveHourLine: nil)
        case .fitted(let r):
            return Lines(header: header,
                         reading: fittedReading(r.verdict),
                         evidence: evidence(r, compact),
                         fiveHourLine: fiveHourLine(r.fiveHourCapHits))
        }
    }

    public static func header(_ reading: PlanFit.Reading, fallbackPlan: String? = nil,
                              compact: Bool = false) -> String {
        header(reading, planToken(reading, fallbackPlan), compact)
    }

    public static func readingLine(_ reading: PlanFit.Reading) -> String {
        switch reading {
        case .notEnoughData(let r): return notEnoughDataReading(r)
        case .fitted(let r): return fittedReading(r.verdict)
        }
    }

    public static func evidenceLine(_ reading: PlanFit.Reading, compact: Bool = false) -> String {
        build(reading, compact: compact).evidence
    }

    private static func planToken(_ reading: PlanFit.Reading, _ fallbackPlan: String?) -> String? {
        if let p = reading.currentPlan { return p }
        if case .notEnoughData(let r) = reading, r.recordedWeeks == 0 { return fallbackPlan }
        return nil
    }

    private static func header(_ reading: PlanFit.Reading, _ planToken: String?, _ compact: Bool) -> String {
        var parts = ["PLAN FIT"]
        if let planToken { parts.append(planToken.uppercased()) }
        if !compact, let p = periodToken(reading) { parts.append(p) }
        return parts.joined(separator: " · ")
    }

    private static func periodToken(_ reading: PlanFit.Reading) -> String? {
        switch reading {
        case .fitted(let r):
            return "LAST \(r.periodTargetWeeks) \(weeksWord(r.periodTargetWeeks).uppercased())"
        case .notEnoughData(let r):
            if r.recordedWeeks == 0 { return nil }
            return "\(r.recordedWeeks) \(weeksWord(r.recordedWeeks).uppercased())"
        }
    }

    private static func notEnoughDataReading(_ r: PlanFit.NotEnoughData) -> String {
        if let change = r.planChange {
            let ago = "\(change.weeksAgo) \(weeksWord(change.weeksAgo)) ago"
            if let prev = change.previousPlan { return "Changed from \(prev) \(ago)." }
            return "Changed plans \(ago)."
        }
        if r.recordedWeeks == 0 { return "First week in progress." }
        return "\(r.recordedWeeks) of \(PlanFit.minWeeks) \(weeksWord(PlanFit.minWeeks)) recorded."
    }

    private static func fittedReading(_ v: PlanFit.Verdict) -> String {
        switch v {
        case .runsOut: return "This plan runs out on you."
        case .smallPart: return "You use a small part of this plan."
        case .idle: return "No use recorded on this account."
        case .fits: return "This plan fits how you use it."
        }
    }

    private static func evidence(_ r: PlanFit.Fitted, _ compact: Bool) -> String {
        let n = r.recordedWeeks
        let target = r.periodTargetWeeks
        let gaps = n < target
        switch r.verdict {
        case .runsOut:
            if r.cappedCount >= PlanFit.ceilDiv(n, 3) {
                let capWord = sharedModelCap(r.weeks).map { "the \($0) cap" } ?? "the cap"
                return "Hit \(capWord) in \(r.cappedCount) of \(weeksDenominator(n, target, gaps, compact))"
            } else {
                let near = r.cappedCount + r.heavyCount
                return "Capped or above 75% in \(near) of \(weeksDenominator(n, target, gaps, compact))"
            }
        case .smallPart:
            return "Under 25% in \(r.lightCount) of \(weeksDenominator(n, target, gaps, compact)) · never capped"
        case .idle:
            let word = gaps ? "recorded idle \(weeksWord(n))" : "idle \(weeksWord(n))"
            return "\(n) \(word)."
        case .fits:
            return "Between 25% and 75% most weeks · \(cappedPhrase(r.cappedCount))"
        }
    }

    private static func weeksDenominator(_ n: Int, _ target: Int, _ gaps: Bool, _ compact: Bool) -> String {
        if gaps { return "\(n) recorded \(weeksWord(n))" }
        if compact { return "the last \(target) \(weeksWord(target))" }
        return "\(target) \(weeksWord(target))"
    }

    private static func cappedPhrase(_ count: Int) -> String {
        switch count {
        case 0: return "never capped"
        case 1: return "capped once"
        default: return "capped \(count) times"
        }
    }

    private static func fiveHourLine(_ count: Int) -> String? {
        if count <= 0 { return nil }
        return "5h cap hit \(count) \(count == 1 ? "time" : "times")"
    }

    private static func sharedModelCap(_ weeks: [PlanFit.WeekReading]) -> String? {
        let capped = weeks.filter { $0.band == .capped }
        guard let first = capped.first, let cap = first.modelCap else { return nil }
        return capped.allSatisfy { $0.modelCap == cap } ? cap : nil
    }

    private static func weeksWord(_ n: Int) -> String { n == 1 ? "week" : "weeks" }
}
