import CooldownCore
import Foundation
import SwiftUI

/// CCRM-94 (iOS App): what each card says, built from a reading, the history samples and the clock — pure, so
/// the wording is tested apart from the views. Copy follows the Android cards and the approved wireframe rev B.
struct WindowContent: Equatable {
    var title: String
    var pct: Double?
    /// Even-pace position in percent (elapsed share of the window), for the tick.
    var pace: Double?
    var chart: TrendChart.Model?
    /// First line under the chart: "On even pace" / "22% below even pace" / "22% above even pace" / "Limit reached".
    var paceLine: String
    var paceLineAlarm: Bool
    /// Second line: the projection, or why there isn't one.
    var projectionLine: String?
    var resetsIn: String
    var resetsAt: String
    /// No reset time yet: the window hasn't started (CCBG-30 (Phantom Window)). The card keeps its bar and says
    /// "Starts when a message is sent", with no chart or pace — as Android's `ResetRow` does.
    var notStarted = false

    var over: Bool { (pct ?? 0) < 100 && pace.map { (pct ?? 0) > $0 + 3 } ?? false }
}

enum CardContent {

    static func window(
        title: String, window: UsageWindow?, kind: String, history: [HistoryPoint], nowMs: Int64,
        use24h: Bool = false, timeZone: TimeZone = .current
    ) -> WindowContent? {
        guard let window else { return nil }
        guard let reset = window.resetsAtMs else {
            return WindowContent(title: title, pct: window.percent, pace: nil, chart: nil, paceLine: "", paceLineAlarm: false,
                                 projectionLine: nil, resetsIn: Self.notStartedLine, resetsAt: "", notStarted: true)
        }
        let length = HistoryStats.windowMs(kind: kind)
        let start = reset - length
        let pct = window.percent
        let pace = Fmt.elapsedPercent(resetsAtMs: reset, windowLengthMs: length, nowMs: nowMs)
        let samples = kind == HistoryStats.weeklyKind
            ? Projection.weeklySamples(history: history, resetAtMs: reset, windowLengthMs: length)
            : Projection.sessionSamples(history: history, resetAtMs: reset, windowLengthMs: length)
        let estimate = Projection.estimate(samples: samples, resetAtMs: reset)
        let outlook = Projection.outlook(
            usedPct: pct ?? 0, elapsedMs: Projection.elapsedMs(resetsAtMs: reset, windowLengthMs: length, nowMs: nowMs),
            windowLengthMs: length, estimate: estimate)

        // The chart: every sample in this window, then the current reading at "now".
        var points = samples.sorted { $0.0 < $1.0 }
            .filter { $0.0 >= start && $0.0 <= nowMs }
            .map { CGPoint(x: Double($0.0 - start) / Double(length), y: $0.1) }
        let nowX = min(max(Double(nowMs - start) / Double(length), 0), 1)
        if let pct { points.append(CGPoint(x: nowX, y: pct)) }
        var projection: Double?
        if let estimate, let pct, pct < 100 {
            // The chart's end agrees with the sentence: the estimate's own percent at the reset, or — when it
            // runs out first — a value past 100 so the line stops where it crosses.
            projection = estimate.hitsLimitAtMs == nil
                ? estimate.pctAtReset
                : max(100.5, pct + estimate.ratePctPerHour * Double(max(reset - nowMs, 0)) / 3_600_000)
        }
        let chart = TrendChart.Model(
            samples: points, nowX: nowX, projection: projection,
            over: (pct ?? 0) < 100 && pace.map { (pct ?? 0) > $0 + 3 } ?? false,
            startLabel: axisLabel(start, kind: kind, use24h: use24h, timeZone: timeZone),
            endLabel: axisLabel(reset, kind: kind, use24h: use24h, timeZone: timeZone))

        let paceLine: String
        var alarm = false
        var projectionLine: String?
        if (pct ?? 0) >= 100 {
            paceLine = "Limit reached"; alarm = true
            projectionLine = "Opens again when the window resets"
        } else {
            let delta = (pct ?? 0) - (pace ?? 0)
            if abs(delta) <= 3 { paceLine = "On even pace" }
            else if delta < 0 { paceLine = "\(Int((-delta).rounded()))% below even pace" }
            else { paceLine = "\(Int(delta.rounded()))% above even pace"; alarm = true }
            if let suppression = outlook.suppression {
                projectionLine = suppression.explanation
            } else if let estimate {
                let rate = String(format: "%.1f%%/h", estimate.ratePctPerHour)
                if let hits = estimate.hitsLimitAtMs {
                    let when = kind == HistoryStats.weeklyKind || !Fmt.sameDay(hits, nowMs, timeZone: timeZone)
                        ? dayClock(hits, nowMs, use24h, timeZone) : clock(hits, use24h, timeZone)
                    projectionLine = "At this pace: runs out ~\(when) · \(rate)"
                } else {
                    projectionLine = "At this pace: ~\(Int(estimate.pctAtReset.rounded()))% when the window resets · \(rate)"
                }
            }
        }
        return WindowContent(
            title: title, pct: pct, pace: pace, chart: chart, paceLine: paceLine, paceLineAlarm: alarm,
            projectionLine: projectionLine,
            resetsIn: "Resets in \(Fmt.compactDuration(ms: reset - nowMs))",
            resetsAt: "Resets at \(dayClock(reset, nowMs, use24h, timeZone))")
    }

    static let notStartedLine = "Starts when a message is sent"

    /// A folded card's one line: "Resets in 1h 57m · 22% below pace" (Android's Compact card).
    static func compactLine(_ c: WindowContent) -> String {
        if c.notStarted { return notStartedLine }
        let pace = c.paceLine.replacingOccurrences(of: "even pace", with: "pace")
        return "\(c.resetsIn) · \(pace == "On pace" ? "on pace" : pace)"
    }

    /// "$123.90 / $400.00" with its percent, or "$4.20 balance"; nil when there's nothing to report.
    static func credits(_ credits: SpendCredits?) -> (text: String, pct: Int?)? {
        guard let credits, credits.isReportable else { return nil }
        if credits.hasLimit, let limit = credits.limitMinor {
            let used = Fmt.money(minorUnits: credits.usedMinor, exponent: credits.exponent, currencyCode: credits.currency)
            let cap = Fmt.money(minorUnits: limit, exponent: credits.exponent, currencyCode: credits.currency)
            return ("\(used) / \(cap)", credits.percentDisplay)
        }
        if let balance = credits.balanceMinor {
            return ("\(Fmt.money(minorUnits: balance, exponent: credits.exponent, currencyCode: credits.currency)) balance", nil)
        }
        return nil
    }

    // MARK: - Times, written the Android way: "9:50 pm", "Thu 9:50 pm", "13 Oct"

    static func clock(_ ms: Int64, _ use24h: Bool, _ tz: TimeZone) -> String {
        let s = Fmt.clock(epochMs: ms, use24h: use24h, timeZone: tz)
        return use24h ? s : s.replacingOccurrences(of: "AM", with: "am").replacingOccurrences(of: "PM", with: "pm")
    }

    /// Android's card always names the day: "Resets at Thu 9:50 pm", today included.
    static func dayClock(_ ms: Int64, _ nowMs: Int64, _ use24h: Bool, _ tz: TimeZone) -> String {
        "\(weekday(ms, tz)) \(clock(ms, use24h, tz))"
    }

    static func axisLabel(_ ms: Int64, kind: String, use24h: Bool, timeZone: TimeZone) -> String {
        if kind == HistoryStats.weeklyKind {
            let f = DateFormatter()
            f.timeZone = timeZone
            f.locale = Locale(identifier: "en_GB")
            f.dateFormat = "d MMM"
            return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
        }
        return clock(ms, use24h, timeZone)
    }

    private static func weekday(_ ms: Int64, _ tz: TimeZone) -> String {
        let f = DateFormatter()
        f.timeZone = tz
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "EEE"
        return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
    }
}

/// The condition shown above an account's cards (spec mobile §3): states, not notifications.
struct Notice: Equatable {
    enum Tone { case error, warning, quiet }
    let title: String
    let body: String
    let action: String?
    let tone: Tone

    static func forAccount(label: String, state: AccountState, snapshot: Snapshot?, nowMs: Int64) -> Notice? {
        if state.lastKind == "plan" {
            let plan = state.plan ?? "Free"
            return Notice(title: "Usage isn’t available on the \(plan) plan",
                          body: "Anthropic only reports usage windows for paid plans. This account stays signed in and will show numbers if the plan changes.",
                          action: nil, tone: .quiet)
        }
        if state.reauthNeeded || state.lastKind == "authNeeded" {
            return Notice(title: "\(label): Sign-in stopped working",
                          body: "The saved sign-in failed and couldn’t renew. The numbers below are the last ones that worked.",
                          action: "Sign in again", tone: .error)
        }
        if let snapshot, nowMs - snapshot.fetchedAtMs > staleMs {
            let when = CardContent.dayClock(snapshot.fetchedAtMs, nowMs, false, .current)
            return Notice(title: "Stale — nothing since \(when)",
                          body: "Cooldown hasn’t had a reading for over 6 hours. Pull down to try again.",
                          action: nil, tone: .warning)
        }
        return nil
    }

    /// spec mobile §3: stale after 6 hours without a success.
    static let staleMs: Int64 = 6 * 3_600_000
}
