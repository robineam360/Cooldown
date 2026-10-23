import Foundation

/// CCRM-94 (iOS App): what the fetch repository remembers per account between fetches — the mobile
/// counterpart of Android's `UsageCache` bookkeeping. Codable, one JSON file per account in the App
/// Group, so the app and a widget extension read the same truth.
public struct AccountState: Sendable, Equatable, Codable {
    public var lastSuccessAtMs: Int64 = 0
    public var lastAttemptAtMs: Int64 = 0
    /// The last attempt's outcome, as a short kind ("success", "rateLimited", "authNeeded", "plan", …).
    public var lastKind: String = ""
    /// What to show for a failed attempt; the last success's numbers stay in the snapshot.
    public var lastMessage: String = ""
    public var consecutive429: Int = 0
    public var backoffUntilMs: Int64 = 0
    /// When renewals started failing transiently; after `RenewalPolicy.stuckMs` it becomes "sign in again".
    public var firstRenewalFailAtMs: Int64 = 0
    public var reauthNeeded: Bool = false
    public var plan: String?
    public var tier: String?
    public var email: String?
    public var planCheckedAtMs: Int64 = 0
    // Reset detection (spec core §8): the window each reading belonged to, and its peak so far.
    public var sessionKeyMs: Int64 = 0
    public var sessionPeak: Double = 0
    public var weeklyKeyMs: Int64 = 0
    public var weeklyPeak: Double = 0

    public init() {}
}

/// Loads and saves `AccountState` files atomically.
public struct AccountStateStore: Sendable {
    let storage: Storage
    public init(storage: Storage) { self.storage = storage }

    func file(_ key: String) -> URL { storage.file("account-state-\(key).json") }

    public func load(_ key: String) -> AccountState {
        guard let data = try? Data(contentsOf: file(key)),
              let state = try? JSONDecoder().decode(AccountState.self, from: data)
        else { return AccountState() }
        return state
    }

    public func save(_ state: AccountState, _ key: String) {
        guard let data = try? JSONEncoder().encode(state), let text = String(data: data, encoding: .utf8) else { return }
        storage.writeAtomically(text, to: file(key))
    }

    public func clear(_ key: String) { try? FileManager.default.removeItem(at: file(key)) }
}

/// A window that closed between two readings, as reset detection saw it (spec core §8).
public struct ClosedWindow: Sendable, Equatable {
    public let kind: String          // "session" or "weekly", as `SessionLog` stores it
    public let resetAtMs: Int64      // the closed window's identity — its `resets_at`
    public let peakPct: Double
    public var hitLimit: Bool { peakPct >= 99.5 }
}

/// Spec core §8 reset detection: the stored window key is non-zero, differs from the current one (beyond
/// drift — `WindowReset.sameWindow`), and lies in the past → the stored window closed at its peak; re-seed
/// the peak from the current reading. Otherwise the peak accumulates. Runs on every successful reading.
public enum ResetDetection {
    public static func observe(
        _ state: inout AccountState, data: UsageData, nowMs: Int64
    ) -> [ClosedWindow] {
        var closed: [ClosedWindow] = []
        step(&state.sessionKeyMs, &state.sessionPeak, window: data.session, kind: "session",
             lengthMs: HistoryStats.sessionMs, nowMs: nowMs, closed: &closed)
        step(&state.weeklyKeyMs, &state.weeklyPeak, window: data.weekly, kind: "weekly",
             lengthMs: HistoryStats.weekMs, nowMs: nowMs, closed: &closed)
        return closed
    }

    private static func step(
        _ key: inout Int64, _ peak: inout Double, window: UsageWindow?, kind: String,
        lengthMs: Int64, nowMs: Int64, closed: inout [ClosedWindow]
    ) {
        guard let window, let current = window.resetsAtMs, current > 0 else { return }
        let pct = window.percent ?? 0
        if key != 0, !WindowReset.sameWindow(key, current, windowLengthMs: lengthMs), key <= nowMs {
            closed.append(ClosedWindow(kind: kind, resetAtMs: key, peakPct: peak))
            key = current
            peak = pct
        } else if key == 0 || !WindowReset.sameWindow(key, current, windowLengthMs: lengthMs) {
            // First reading, or a key that moved without the old one being past (a clock oddity):
            // start tracking the current window without inventing a closed one.
            key = current
            peak = pct
        } else {
            peak = max(peak, pct)
        }
    }
}

/// CCRM-94 (iOS App): the last good reading per account, in the App Group, for the widgets, the
/// Live Activity and the Control Center control to draw without fetching. The body is the raw
/// response (ChatGPT's redacted first, spec core §9.6), re-parsed by the reader.
public struct Snapshot: Sendable, Equatable, Codable {
    public let profileKey: String
    public let provider: Provider
    public let body: String
    public let fetchedAtMs: Int64
    public var lastAttemptAtMs: Int64
    public var lastKind: String
    public var lastMessage: String
    public var plan: String?

    /// The numbers, parsed again from the stored body. `nowMs` only matters for ChatGPT's
    /// not-started session rule.
    public func usage(nowMs: Int64) -> UsageData? {
        switch provider {
        case .claude: UsageParser.parse(body)
        case .chatgpt: ChatGPTUsageParser.parse(body, nowMs: nowMs)
        }
    }
}

public struct SnapshotStore: Sendable {
    let storage: Storage
    public init(storage: Storage) { self.storage = storage }

    func file(_ key: String) -> URL { storage.file("snapshot-\(key).json") }

    public func load(_ key: String) -> Snapshot? {
        guard let data = try? Data(contentsOf: file(key)) else { return nil }
        return try? JSONDecoder().decode(Snapshot.self, from: data)
    }

    public func save(_ snapshot: Snapshot) {
        guard let data = try? JSONEncoder().encode(snapshot), let text = String(data: data, encoding: .utf8) else { return }
        storage.writeAtomically(text, to: file(snapshot.profileKey))
    }

    public func clear(_ key: String) { try? FileManager.default.removeItem(at: file(key)) }
}
