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
    /// First line under the chart. CCRM-97 (Pace Number): on a weekly window the band and allowance in words
    /// ("Slow down · ≈11%/day"), or "Out until Sun 9:30 pm" when spent; on the 5h window empty (its line is the
    /// projection), or "Limit reached" at 100%.
    var paceLine: String
    /// Red: a weekly band over pace (Slow down, Nearly out) or spent; the 5h at its limit or projected to run out.
    var paceLineAlarm: Bool
    /// Second line: the projection, or why there isn't one.
    var projectionLine: String?
    /// The projection alone, short, for widgets: "~ runs out 6:51 pm" / "~70% at reset"; nil when there's none.
    var projectionShort: String? = nil
    var resetsIn: String
    var resetsAt: String
    /// No reset time yet: the window hasn't started (CCBG-30 (Phantom Window)). The card keeps its bar and says
    /// "Starts when a message is sent", with no chart or pace — as Android's `ResetRow` does.
    var notStarted = false
    /// CCRM-97 (Pace Number), weekly windows: the band (nil with no verdict), and the reset's weekday for
    /// "… until Sun" / "… · resets Sun".
    var band: Pace.Band? = nil
    var resetDay: String = ""
    /// The 5h window's folded projection: "on track for ~69%" / "runs out ~3:25 pm"; nil when there is none.
    var projectionFold: String? = nil
    var isWeekly = false
    /// CCRM-103 (Hybrid Notification) §2b: the window's reset clock, its length and the moment this was built, for the
    /// tap readout (core §6.3 `markTime`). nil reset = not started.
    var resetMs: Int64? = nil
    var windowMs: Int64 = 0
    var builtAtMs: Int64 = 0

    /// When the pace mark meets the usage; nil — the tap does nothing — when spent, unclocked or unread.
    var markTime: MarkTime? {
        MarkTime.of(usedPct: pct, msUntilReset: resetMs.map { $0 - builtAtMs }, windowLengthMs: windowMs, nowMs: builtAtMs)
    }
    /// The tap readout's words: "Pace catches up Thu 5 Nov, 12:50 PM", "1h 6m behind pace", "On the pace mark".
    func readout(use24h: Bool = Display.use24h, timeZone: TimeZone = .current) -> String? {
        PaceText.markReadout(markTime, use24h: use24h, timeZone: timeZone)
    }

    /// The weekly pace line at full width: "Room to spare · ≈22%/day until Fri" (rev B); halves drop " until …".
    var paceLineFull: String {
        guard isWeekly, let band, band != .spent, !paceLine.isEmpty, !resetDay.isEmpty else { return paceLine }
        return "\(paceLine) until \(resetDay)"
    }

    var over: Bool { (pct ?? 0) < 100 && pace.map { (pct ?? 0) > $0 + 3 } ?? false }
    /// The V1 pace-mark caption under an in-app bar (spec mobile §5), in the current Usage display mode.
    var caption: String? { notStarted ? nil : PaceText.caption(mode: Display.mode, usedPct: pct, elapsedPct: pace) }

    /// Settings → Reset time: the form that leads ("Resets in 3h 0m", or "Resets at Sat 7:43 pm" with Clock time) and
    /// the other form for the second slot ("Resets at Sat 7:43 pm", or "in 3h 0m").
    var leadReset: String { Display.clockFirst && !notStarted ? resetsAt : resetsIn }
    var secondReset: String {
        guard Display.clockFirst else { return resetsAt }
        return resetsIn.replacingOccurrences(of: "Resets in ", with: "in ").replacingOccurrences(of: "Resets soon", with: "soon")
    }
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

        var paceLine: String
        var alarm = false
        var projectionLine: String?
        var projectionShort: String?
        var projectionFold: String?
        let weeklyKind = kind == HistoryStats.weeklyKind
        let band = weeklyKind ? Pace.band(usedPct: pct, elapsedPct: pace) : nil
        if weeklyKind && Pace.isSpent(pct) {
            // Core §6.1's spent line (99.5): "Out until Sun 9:30 pm", no pace, no projection.
            paceLine = "Out until \(dayClock(reset, nowMs, use24h, timeZone))"; alarm = true
            projectionLine = "Opens again when the window resets"
        } else if (pct ?? 0) >= 100 {
            paceLine = "Limit reached"; alarm = true
            projectionLine = "Opens again when the window resets"
        } else {
            if let band {
                let allowance = Pace.allowance(usedPct: pct ?? 0, msUntilReset: reset - nowMs)
                paceLine = PaceText.tight(band, allowance) ?? ""
                alarm = PaceText.isOverPace(band)
            } else {
                // The 5h session gets no Pace (spec mobile §5): its line is the projection.
                paceLine = ""
            }
            if let suppression = outlook.suppression {
                projectionLine = suppression.explanation
            } else if let estimate {
                let rate = String(format: "%.1f%%/h", estimate.ratePctPerHour)
                if let hits = estimate.hitsLimitAtMs {
                    let when = kind == HistoryStats.weeklyKind || !Fmt.sameDay(hits, nowMs, timeZone: timeZone)
                        ? dayClock(hits, nowMs, use24h, timeZone) : clock(hits, use24h, timeZone)
                    projectionLine = "At this pace: runs out ~\(when) · \(rate)"
                    projectionShort = "~ runs out \(when)"
                    projectionFold = "runs out ~\(when)"
                    if !weeklyKind { alarm = true }
                } else {
                    projectionLine = "At this pace: ~\(Int(estimate.pctAtReset.rounded()))% when the window resets · \(rate)"
                    projectionShort = "~\(Int(estimate.pctAtReset.rounded()))% at reset"
                    projectionFold = "on track for ~\(Int(estimate.pctAtReset.rounded()))%"
                }
            }
        }
        return WindowContent(
            title: title, pct: pct, pace: pace, chart: chart, paceLine: paceLine, paceLineAlarm: alarm,
            projectionLine: projectionLine, projectionShort: projectionShort,
            // Android's countdown collapses to "resets soon" inside five minutes.
            resetsIn: reset - nowMs < 5 * 60_000 ? "Resets soon" : "Resets in \(Fmt.compactDuration(ms: reset - nowMs))",
            resetsAt: "Resets at \(dayClock(reset, nowMs, use24h, timeZone))",
            band: band, resetDay: weekday(reset, timeZone), projectionFold: projectionFold, isWeekly: weeklyKind,
            resetMs: reset, windowMs: length, builtAtMs: nowMs)
    }

    static let notStartedLine = "Starts when a message is sent"

    /// CCRM-102 (Spent Week Gate): the one full-opacity line under a disabled 5h (spec mobile §5).
    /// Exactly Android's (`LeadLines.kt`): "Weekly limit reached — 5h can’t be used until Sat 4:51 pm"; with no reset
    /// clock still ahead it can't name a time, so it reads "Weekly limit reached".
    static func weekGateNote(weeklyResetMs: Int64?, nowMs: Int64, use24h: Bool = Display.use24h, timeZone: TimeZone = .current) -> String {
        guard let reset = weeklyResetMs, reset > nowMs else { return weekGateShort }
        return "Weekly limit reached — 5h can’t be used until \(dayClock(reset, nowMs, use24h, timeZone))"
    }
    static let weekGateShort = "Weekly limit reached"
    /// A widget face showing the 5h while the week is spent.
    static let weekSpentStamp = "Weekly spent"

    /// A folded card's one line (rev B): Weekly "Slow down · ≈11%/day · resets Sun" or "Out until Sun 9:30 pm";
    /// 5h "Resets in 2h 20m · on track for ~69%" or "… · runs out ~3:25 pm".
    static func compactLine(_ c: WindowContent) -> String {
        if c.notStarted { return notStartedLine }
        if c.isWeekly {
            if c.band == .spent || c.paceLine.hasPrefix("Out until") || c.paceLine.isEmpty { return c.paceLine.isEmpty ? c.leadReset : c.paceLine }
            return "\(c.paceLine) · resets \(c.resetDay)"
        }
        if (c.pct ?? 0) >= 100 { return "\(c.leadReset) · Limit reached" }
        return c.projectionFold.map { "\(c.leadReset) · \($0)" } ?? c.leadReset
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

    static func weekday(_ ms: Int64, _ tz: TimeZone) -> String {
        let f = DateFormatter()
        f.timeZone = tz
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "EEE"
        return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
    }
}

/// CCRM-94 (iOS App): when a sign-in runs out — the Details sheet's rule (Claude only: added + ~30 days; ChatGPT and
/// Gemini renew themselves). Spec mobile §3 (BEHAVIOR-MOBILE) warns within 7 days; Android's code uses 3 (SignInExpiry.kt
/// SOON_MS) — iOS follows the spec, and the mismatch is listed for the Android session (iOS parity wireframes rev B).
enum SignInExpiry {
    static let lifetimeMs: Int64 = 30 * 86_400_000
    static let soonMs: Int64 = 7 * 86_400_000

    static func expiresAtMs(provider: Provider, addedAtMs: Int64?) -> Int64? {
        guard provider == .claude, let added = addedAtMs else { return nil }
        return added + lifetimeMs
    }

    /// Time left, when the sign-in is within the warning window (and not already broken).
    static func soonLeftMs(provider: Provider, addedAtMs: Int64?, reauthNeeded: Bool, nowMs: Int64) -> Int64? {
        guard !reauthNeeded, let at = expiresAtMs(provider: provider, addedAtMs: addedAtMs) else { return nil }
        let left = at - nowMs
        return left <= soonMs ? max(0, left) : nil
    }
}

/// The condition shown above an account's cards (spec mobile §3): states, not notifications.
struct Notice: Equatable {
    enum Tone { case error, warning, quiet }
    let title: String
    let body: String
    let action: String?
    let tone: Tone
    /// A raw detail under the title ("Reply held 1 of 3 groups").
    var evidence: String? = nil
    /// When set, the action opens this page (a status page) instead of the sign-in sheet.
    var actionURL: String? = nil

    /// One notice at a time (iOS parity wireframes rev B §3): plan refusal › sign-in needed › the last fetch's error ›
    /// stale › sign-in expiring. A fetch error absorbs staleness: its evidence line names the last reading.
    static func forAccount(label: String, state: AccountState, snapshot: Snapshot?, nowMs: Int64,
                           provider: Provider = .claude, addedAtMs: Int64? = nil,
                           use24h: Bool = Display.use24h, timeZone: TimeZone = .current) -> Notice? {
        if state.lastKind == "plan" {
            let plan = state.plan ?? "Free"
            return Notice(title: "Usage isn’t available on the \(plan) plan",
                          body: "\(provider.displayName) only reports usage windows for paid plans. This account stays signed in and will show numbers if the plan changes.",
                          action: nil, tone: .quiet)
        }
        if state.reauthNeeded || state.lastKind == "authNeeded" {
            return Notice(title: "\(label): Sign-in stopped working",
                          body: "The saved sign-in failed and couldn’t renew. The numbers below are the last ones that worked.",
                          action: "Sign in again", tone: .error)
        }
        let stale = snapshot.map { nowMs - $0.fetchedAtMs > staleMs } ?? false
        let lastReading = snapshot.map { " · last reading \(CardContent.dayClock($0.fetchedAtMs, nowMs, use24h, timeZone))" } ?? ""
        let vendor = provider.serviceName
        func detail(_ evidence: String) -> String? {
            let text = evidence + (stale ? lastReading : "")
            return text.isEmpty ? nil : text
        }
        switch state.lastKind {
        case "invalidResponse" where provider == .antigravity:
            // CCRM-55 (H): Google answered without the Gemini groups. Never 0% or 100%: the last good cards stay.
            return Notice(title: "Google changed something — usage unavailable",
                          body: "Cooldown won’t guess. An app update may be needed.", action: nil, tone: .warning,
                          evidence: state.lastMessage.hasPrefix("Reply held") ? state.lastMessage : nil)
        case "rateLimited", "backedOff":
            var evidence = state.lastMessage
            if state.backoffUntilMs > nowMs {
                evidence += (evidence.isEmpty ? "" : " · ") + "next try ~\(CardContent.clock(state.backoffUntilMs, use24h, timeZone))"
            }
            return Notice(title: "Rate limited — backing off, retries on its own.", body: "", action: nil, tone: .warning,
                          evidence: detail(evidence))
        case "network":
            return Notice(title: "Couldn’t reach \(vendor) — check your connection, or see if it’s them.", body: "",
                          action: "Check \(vendor) status", tone: .warning, evidence: detail(state.lastMessage),
                          actionURL: provider.statusPageURL)
        case "server":
            return Notice(title: "\(vendor)’s server errored — usually theirs, usually brief.", body: "",
                          action: "Check \(vendor) status", tone: .warning, evidence: detail(state.lastMessage),
                          actionURL: provider.statusPageURL)
        case "invalidResponse":
            return Notice(title: "The server answered in a shape this app doesn’t know — an app update may be needed.", body: "",
                          action: nil, tone: .warning, evidence: detail(state.lastMessage))
        case "error":
            return Notice(title: "Something unexpected went wrong in the app.", body: "", action: nil, tone: .error,
                          evidence: detail(state.lastMessage))
        default: break
        }
        if stale, let snapshot {
            let when = CardContent.dayClock(snapshot.fetchedAtMs, nowMs, use24h, timeZone)
            return Notice(title: "Stale — nothing since \(when)",
                          body: "Cooldown hasn’t had a reading for over 6 hours. Pull down to try again.",
                          action: nil, tone: .warning)
        }
        if let left = SignInExpiry.soonLeftMs(provider: provider, addedAtMs: addedAtMs, reauthNeeded: state.reauthNeeded, nowMs: nowMs) {
            return Notice(title: "Sign-in expires in \(Fmt.compactDuration(ms: left))",
                          body: "Sign in again to keep polling going.", action: "Sign in again", tone: .quiet)
        }
        return nil
    }

    /// Cards dim under a failure or warning, never under a quiet note (rev B §3).
    var dimsCards: Bool { tone != .quiet }

    /// spec mobile §3: stale after 6 hours without a success.
    static let staleMs: Int64 = 6 * 3_600_000
}

/// CCRM-94 (iOS App): the account page's status line (iOS parity wireframes rev B §2) and its condition rows.
enum AccountStatus {
    /// "Checking…", "Not checked yet", "Checked 12m ago", plus (Claude and Gemini, never while sign-in is broken)
    /// " · renews in 6h 4m" or " · renewal due at next check". `tokenExpiresAtMs` is the Keychain access token's expiry;
    /// nil or 0 (unreadable) adds nothing. ChatGPT never shows the clause.
    static func line(provider: Provider, state: AccountState, tokenExpiresAtMs: Int64?, busy: Bool, nowMs: Int64) -> String {
        if busy { return "Checking…" }
        guard state.lastAttemptAtMs > 0 else { return "Not checked yet" }
        var text = "Checked \(Fmt.relativeAgo(epochMs: state.lastAttemptAtMs, nowMs: nowMs))"
        if provider != .chatgpt, !state.reauthNeeded, let at = tokenExpiresAtMs, at > 0 {
            text += at > nowMs ? " · renews in \(Fmt.compactDuration(ms: at - nowMs))" : " · renewal due at next check"
        }
        return text
    }

    struct Condition: Equatable {
        enum Tone { case red, amber }
        let text: String
        let tone: Tone
    }

    /// The condition rows under the status line, in Android's order: free plan, sign-in stopped, expiring, rate-limited.
    static func conditions(provider: Provider, state: AccountState, addedAtMs: Int64?, nowMs: Int64) -> [Condition] {
        var rows: [Condition] = []
        if state.lastKind == "plan" {
            rows.append(Condition(text: provider == .claude
                ? "\(state.plan ?? "Free") plan: Claude doesn’t report usage — upgrade to Pro, Max or Team to see numbers."
                : "\(provider.displayName) only reports usage windows for paid plans.", tone: .red))
        }
        if state.reauthNeeded || state.lastKind == "authNeeded" {
            rows.append(Condition(text: "Sign-in stopped working — \(provider == .chatgpt ? "sign in with a code" : "sign in again") below.", tone: .red))
        }
        if let left = SignInExpiry.soonLeftMs(provider: provider, addedAtMs: addedAtMs, reauthNeeded: state.reauthNeeded, nowMs: nowMs) {
            rows.append(Condition(text: "Sign-in expires in \(Fmt.compactDuration(ms: left)) — sign in again to keep polling going.", tone: .amber))
        }
        if state.backoffUntilMs > nowMs {
            rows.append(Condition(text: "Rate-limited — next try in \(Fmt.compactDuration(ms: state.backoffUntilMs - nowMs))", tone: .red))
        }
        return rows
    }

    /// The Settings list row's word: broken sign-in, then an expiry warning, else signed in.
    static func listWord(provider: Provider, state: AccountState, addedAtMs: Int64?, nowMs: Int64) -> (text: String, tone: Condition.Tone?) {
        if state.reauthNeeded { return ("Sign in again", .amber) }
        if let left = SignInExpiry.soonLeftMs(provider: provider, addedAtMs: addedAtMs, reauthNeeded: false, nowMs: nowMs) {
            return ("Expires in \(Fmt.compactDuration(ms: left))", .amber)
        }
        return ("Signed in", nil)
    }
}
