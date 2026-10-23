import CooldownCore
import Foundation

/// CCRM-94 (iOS App): the wireframe's sample accounts (Teams and Product on Claude, Personal on ChatGPT), so every
/// state can be drawn in the simulator and checked against design/2026-10-23-ios-wireframes.html. Launch with
/// `-demo <scenario>`; nothing here touches the network, the Keychain or the App Group.
enum DemoScenario: String, CaseIterable {
    case standard, signin, plan, stale, max, empty

    static func fromLaunchArguments() -> DemoScenario? {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-demo"), i + 1 < args.count else { return nil }
        return DemoScenario(rawValue: args[i + 1]) ?? .standard
    }
}

@MainActor
enum DemoData {
    static func load(_ scenario: DemoScenario, into model: AppModel) {
        let now = AppModel.clock()
        model.nowMs = now
        guard scenario != .empty else { model.accounts = []; return }
        model.accounts = [
            Account(key: "teams", label: "Teams", provider: .claude, pinned: true),
            Account(key: "product", label: "Product", provider: .claude),
            Account(key: "personal", label: "Personal", provider: .chatgpt),
        ]
        model.selectedKey = "teams"

        // Teams: on even pace (46% at 46% elapsed), or at 100% in the `max` scenario.
        let teamsReset = now + (5 * 3600 - Int64(0.46 * 5 * 3600)) * 1000
        let teamsPct = scenario == .max ? 100.0 : 46
        let teamsCurve: [(Double, Double)] = scenario == .max
            ? [(0, 0), (0.08, 15), (0.16, 33), (0.24, 52), (0.32, 70), (0.40, 86), (0.45, 97)]
            : [(0, 0), (0.06, 2), (0.11, 5), (0.18, 11), (0.24, 19), (0.30, 27), (0.36, 34), (0.41, 40)]
        add(model, key: "teams", provider: .claude, plan: "Team", sessionPct: teamsPct, sessionReset: teamsReset,
            curve: teamsCurve, weeklyPct: scenario == .max ? 64 : 18, weeklyElapsed: 0.43, opus: 5, credits: true, now: now)

        // Product: above pace (62% at 40% elapsed), unless the scenario breaks it.
        let productReset = now + Int64(0.6 * 5 * 3600) * 1000
        add(model, key: "product", provider: .claude, plan: "Max 5×", sessionPct: 62, sessionReset: productReset,
            curve: [(0, 0), (0.05, 6), (0.10, 13), (0.15, 21), (0.20, 30), (0.25, 38), (0.30, 47), (0.36, 55)],
            weeklyPct: 64, weeklyElapsed: 0.43, opus: nil, credits: false, now: now,
            stale: scenario == .stale, signin: scenario == .signin, plan_: scenario == .plan)

        // Personal (ChatGPT): well below pace.
        let personalReset = now + (5 * 3600 - Int64(0.68 * 5 * 3600)) * 1000
        add(model, key: "personal", provider: .chatgpt, plan: "Plus", sessionPct: 26, sessionReset: personalReset,
            curve: [(0, 0), (0.2, 0), (0.29, 4), (0.38, 9), (0.49, 16), (0.55, 18), (0.64, 24)],
            weeklyPct: 9, weeklyElapsed: 0.43, opus: nil, credits: false, now: now)
    }

    private static func add(
        _ model: AppModel, key: String, provider: Provider, plan: String, sessionPct: Double, sessionReset: Int64,
        curve: [(Double, Double)], weeklyPct: Double, weeklyElapsed: Double, opus: Double?, credits: Bool, now: Int64,
        stale: Bool = false, signin: Bool = false, plan_: Bool = false
    ) {
        let session = HistoryStats.sessionMs, week = HistoryStats.weekMs
        let weeklyReset = now + Int64((1 - weeklyElapsed) * Double(week))
        let fetchedAt = stale ? now - 7 * 3_600_000 : (signin ? now - 95 * 60_000 : now - 3 * 60_000)
        let body: String
        switch provider {
        case .claude:
            var fields = [
                #""five_hour":{"utilization":\#(sessionPct),"resets_at":"\#(iso(sessionReset))"}"#,
                #""seven_day":{"utilization":\#(weeklyPct),"resets_at":"\#(iso(weeklyReset))"}"#,
            ]
            if let opus { fields.append(#""seven_day_opus":{"utilization":\#(opus),"resets_at":"\#(iso(weeklyReset - 60_000))"}"#) }
            if credits {
                fields.append(#""spend":{"used":{"amount_minor":12390,"currency":"USD","exponent":2},"limit":{"amount_minor":40000,"currency":"USD","exponent":2},"percent":31,"severity":"normal","enabled":true}"#)
            }
            body = "{" + fields.joined(separator: ",") + "}"
        case .chatgpt:
            body = #"{"plan_type":"plus","rate_limit":{"allowed":true,"limit_reached":false,"primary_window":{"used_percent":\#(Int(sessionPct)),"limit_window_seconds":18000,"reset_after_seconds":\#((sessionReset - now) / 1000),"reset_at":\#(sessionReset / 1000)},"secondary_window":{"used_percent":\#(Int(weeklyPct)),"limit_window_seconds":604800,"reset_after_seconds":\#((weeklyReset - now) / 1000),"reset_at":\#(weeklyReset / 1000)}},"credits":{"has_credits":true,"unlimited":false,"balance":"4.20"}}"#
        }
        var state = AccountState()
        state.lastSuccessAtMs = fetchedAt
        state.lastAttemptAtMs = now - 3 * 60_000
        state.lastKind = signin ? "authNeeded" : (plan_ ? "plan" : (stale ? "network" : "success"))
        state.reauthNeeded = signin
        state.plan = plan_ ? "Free" : plan
        let snapshot: Snapshot? = plan_ ? nil : Snapshot(
            profileKey: key, provider: provider, body: body, fetchedAtMs: fetchedAt, lastAttemptAtMs: state.lastAttemptAtMs,
            lastKind: state.lastKind, lastMessage: "", plan: plan)

        // History: the curve's samples inside the current 5-hour window, plus a gentle weekly ramp.
        let sessionStart = sessionReset - session
        var points = curve.map { x, pct in
            HistoryPoint(atMs: sessionStart + Int64(x * Double(session)), sessionPct: pct, sessionResetAtMs: sessionReset,
                         weeklyPct: weeklyPct * 0.9, weeklyResetAtMs: weeklyReset)
        }
        let weekStart = weeklyReset - week
        points += stride(from: 0.0, through: weeklyElapsed - 0.05, by: 0.05).map { x in
            HistoryPoint(atMs: weekStart + Int64(x * Double(week)), sessionPct: nil, sessionResetAtMs: 0,
                         weeklyPct: weeklyPct * x / weeklyElapsed, weeklyResetAtMs: weeklyReset)
        }
        // Closed windows for the history screen: eleven 5-hour windows this week, two of them maxed out.
        let peaks: [Double] = [4, 100, 5, 25, 66, 7, 26, 100, 95, 4]
        let records = peaks.enumerated().map { i, peak in
            HistoryStats.Record(kind: HistoryStats.sessionKind, resetAtMs: sessionStart - Int64(i) * 9 * 3_600_000,
                                peakPct: peak, hitLimit: peak >= 99.5)
        } + [HistoryStats.Record(kind: HistoryStats.weeklyKind, resetAtMs: weekStart, peakPct: 71, hitLimit: false)]
        model.set(snapshot: snapshot, state: state, history: points.sorted { $0.atMs < $1.atMs }, records: records, for: key)
    }

    private static func iso(_ ms: Int64) -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
    }
}
