import CooldownCore
import Foundation

/// CCRM-94 (iOS App): the wireframe's sample accounts (Teams on Claude, Personal on ChatGPT, Lab on Gemini since CCRM-113
/// (Sample Port) — Product, a second Claude, gave way so both stores' review notes describe the same three accounts), so
/// every state can be drawn in the simulator and checked against design/2026-10-23-ios-wireframes.html. Launch with
/// `-demo <scenario>`; nothing here touches the network, the Keychain or the App Group.
enum DemoScenario: String, CaseIterable {
    case standard, signin, plan, stale, max, empty
    /// CCRM-109 (Quiet Accounts) and CCRM-111 (Map Layers): the Pace map's states on the Summary — the wireframe's six
    /// (`map`), ten for a dense map (`dense`), every account at 0% (`quiet`), two hidden and one unread (`hidden`).
    case map, dense, quiet, hidden
    /// CCRM-110 (Account Tags): the standard accounts with only Personal tagged, so the filter's Work segment is disabled
    /// (`tagsdisabled`); every standard account untagged, so the Summary shows no control (`untagged`).
    case tagsdisabled, untagged
    /// CCRM-112 (Summary Snapshot): no account has a reading, so the snapshot says "Nothing to share yet".
    case noreading

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
        if let set = mapSet(scenario) {
            model.accounts = set.map { Account(key: $0.key, label: $0.label, provider: $0.provider, tag: $0.tag?.rawValue) }
            model.selectedKey = AppModel.summaryKey
            for a in set {
                add(model, key: a.key, provider: a.provider, plan: a.provider == .chatgpt ? "Plus" : "Max", sessionPct: 20,
                    sessionReset: now + 3 * 3_600_000, curve: [(0, 0), (0.2, 8), (0.4, 20)], weeklyPct: a.weekly ?? 0,
                    weeklyElapsed: a.elapsed, opus: nil, credits: false, now: now, plan_: a.weekly == nil, sessionWeekly: 1)
            }
            return
        }
        model.accounts = [
            // CCRM-110 (Account Tags): Teams is Work and Personal is Personal, so the All · Personal · Work control shows;
            // Lab is untagged (rev B §9).
            Account(key: "teams", label: "Teams", provider: .claude, pinned: true, tag: AccountTag.work.rawValue),
            Account(key: "personal", label: "Personal", provider: .chatgpt, tag: AccountTag.personal.rawValue),
            Account(key: "lab", label: "Lab", provider: .antigravity),
        ]
        model.selectedKey = "teams"
        if scenario == .untagged { for i in model.accounts.indices { model.accounts[i].tag = nil } }
        if scenario == .tagsdisabled { model.accounts[0].tag = nil }
        // The signed-out Lab carries a Work tag, so Settings shows its warning word beside both chips.
        if scenario == .signin { model.accounts[2].tag = AccountTag.work.rawValue }

        // Teams: on even pace (46% at 46% elapsed), or at 100% in the `max` scenario.
        let teamsReset = now + (5 * 3600 - Int64(0.46 * 5 * 3600)) * 1000
        let teamsPct = scenario == .max ? 100.0 : 46
        let teamsCurve: [(Double, Double)] = scenario == .max
            ? [(0, 0), (0.08, 15), (0.16, 33), (0.24, 52), (0.32, 70), (0.40, 86), (0.45, 97)]
            : [(0, 0), (0.06, 2), (0.11, 5), (0.18, 11), (0.24, 19), (0.30, 27), (0.36, 34), (0.41, 40)]
        add(model, key: "teams", provider: .claude, plan: "Team", sessionPct: teamsPct, sessionReset: teamsReset,
            curve: teamsCurve, weeklyPct: scenario == .max ? 64 : 18, weeklyElapsed: 0.43, opus: 5, credits: true, now: now,
            plan_: scenario == .noreading)

        // Lab (Gemini): above pace (62% at 40% elapsed), unless the scenario breaks it.
        let labReset = now + Int64(0.6 * 5 * 3600) * 1000
        add(model, key: "lab", provider: .antigravity, plan: "Pro", sessionPct: 62, sessionReset: labReset,
            curve: [(0, 0), (0.05, 6), (0.10, 13), (0.15, 21), (0.20, 30), (0.25, 38), (0.30, 47), (0.36, 55)],
            weeklyPct: 64, weeklyElapsed: 0.43, opus: nil, credits: false, now: now,
            stale: scenario == .stale, signin: scenario == .signin, plan_: scenario == .plan || scenario == .noreading)

        // Personal (ChatGPT): well below pace.
        let personalReset = now + (5 * 3600 - Int64(0.68 * 5 * 3600)) * 1000
        add(model, key: "personal", provider: .chatgpt, plan: "Plus", sessionPct: 26, sessionReset: personalReset,
            curve: [(0, 0), (0.2, 0), (0.29, 4), (0.38, 9), (0.49, 16), (0.55, 18), (0.64, 24)],
            weeklyPct: 9, weeklyElapsed: 0.43, opus: nil, credits: false, now: now, plan_: scenario == .noreading)
    }

    private struct MapAccount {
        let key: String, label: String, provider: Provider
        /// nil = no reading.
        let weekly: Double?
        let elapsed: Double
        /// CCRM-110: the wireframe's tags (rev B §6); `quiet` and `hidden` leave every account untagged.
        var tag: AccountTag? = nil
        var untagged: MapAccount { MapAccount(key: key, label: label, provider: provider, weekly: weekly, elapsed: elapsed) }
    }

    /// The wireframe's Pace map accounts (rev B §5 and §7), by scenario; nil for the other scenarios.
    private static func mapSet(_ scenario: DemoScenario) -> [MapAccount]? {
        let six = [
            MapAccount(key: "teams", label: "Teams", provider: .claude, weekly: 52, elapsed: 0.39, tag: .work),
            MapAccount(key: "personal", label: "Personal", provider: .chatgpt, weekly: 50, elapsed: 0.68, tag: .personal),
            MapAccount(key: "chatgpt", label: "ChatGPT", provider: .chatgpt, weekly: 6, elapsed: 0.30, tag: .personal),
            MapAccount(key: "gemini", label: "Gemini", provider: .antigravity, weekly: 91, elapsed: 0.83, tag: .work),
            MapAccount(key: "lab", label: "Lab", provider: .claude, weekly: 61, elapsed: 0.61),
            MapAccount(key: "atlas", label: "Atlas", provider: .claude, weekly: 100, elapsed: 0.70, tag: .work),
        ]
        switch scenario {
        case .map: return six
        case .dense:
            return six + [
                MapAccount(key: "ops", label: "Ops", provider: .claude, weekly: 33, elapsed: 0.36, tag: .work),
                MapAccount(key: "research", label: "Research", provider: .chatgpt, weekly: 28, elapsed: 0.33, tag: .personal),
                MapAccount(key: "client", label: "Client", provider: .antigravity, weekly: 74, elapsed: 0.90, tag: .work),
                MapAccount(key: "work", label: "Work", provider: .claude, weekly: 30, elapsed: 0.44, tag: .work),
            ]
        case .quiet:
            return [MapAccount(key: "notes", label: "Notes", provider: .chatgpt, weekly: 0.4, elapsed: 0.22),
                    MapAccount(key: "sandbox", label: "Sandbox", provider: .antigravity, weekly: 0, elapsed: 0.09),
                    MapAccount(key: "spare", label: "Spare", provider: .claude, weekly: 0.2, elapsed: 0.40)]
        case .hidden:
            return [six[0].untagged, six[1].untagged, six[4],
                    MapAccount(key: "notes", label: "Notes", provider: .chatgpt, weekly: 0.4, elapsed: 0.22),
                    MapAccount(key: "sandbox", label: "Sandbox", provider: .antigravity, weekly: 0, elapsed: 0.09),
                    MapAccount(key: "product", label: "Product", provider: .claude, weekly: nil, elapsed: 0.43)]
        default: return nil
        }
    }

    private static func add(
        _ model: AppModel, key: String, provider: Provider, plan: String, sessionPct: Double, sessionReset: Int64,
        curve: [(Double, Double)], weeklyPct: Double, weeklyElapsed: Double, opus: Double?, credits: Bool, now: Int64,
        stale: Bool = false, signin: Bool = false, plan_: Bool = false, sessionWeekly: Double = 0.9
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
        case .antigravity:
            func bucket(_ id: String, _ used: Double, _ reset: Int64) -> String {
                #"{"bucketId":"\#(id)","remainingFraction":\#(1 - used / 100),"resetTime":"\#(iso(reset))"}"#
            }
            body = #"{"groups":[{"displayName":"Gemini Models","buckets":[\#(bucket("gemini-5h", sessionPct, sessionReset)),\#(bucket("gemini-weekly", weeklyPct, weeklyReset))]}]}"#
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
                         weeklyPct: weeklyPct * sessionWeekly, weeklyResetAtMs: weeklyReset)
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
