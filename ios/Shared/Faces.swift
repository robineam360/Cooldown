import AppIntents
import CooldownCore
import Foundation

/// CCRM-94 (iOS App): what a widget, a lock-screen widget, the Control Center control and the Live Activity show
/// for one account — read from the App Group the app writes (widgets never fetch: two processes renewing one token
/// family could break it). Wireframe rev B §4–§7.
struct Face: Equatable, Sendable {
    enum Status: Equatable, Sendable {
        case fresh
        /// Older than 30 minutes: the face says "as of".
        case aging
        /// Older than 6 hours (spec mobile §3): dimmed numbers, orange "as of".
        case stale
        case signIn
        case plan
        case noData
    }

    let account: Account
    let pct: Double?
    let pace: Double?
    let reset: Date?
    /// When usage reaches 100% at this pace, if before the reset.
    let runOut: Date?
    let weeklyPct: Double?
    let weeklyPace: Double?
    let weeklyReset: Date?
    let fetchedAt: Date?
    let status: Status
    /// The app's own card lines and charts for each window (CardContent), so the large widgets say what the app says.
    /// nil when the window is absent (a weekly-only plan has no 5h).
    var session: WindowContent? = nil
    var weekly: WindowContent? = nil

    /// A plan with no 5h window at all (OpenAI's weekly-only plans): Weekly leads.
    var weeklyOnly: Bool { pct == nil && weeklyPct != nil && status != .noData && status != .signIn }
    /// Set on `leading` for a weekly-only plan: the main fields carry the Weekly window, and there is no second one.
    var leadsWeekly = false
    /// The main window's name on tight surfaces: "5h", or "Weekly" where Weekly leads.
    var windowLabel: String { leadsWeekly ? "Weekly" : "5h" }

    /// The face as the 5h-led surfaces (Number, Countdown, Strip, lock screen, Control Center, Live Activity) should
    /// draw it: unchanged, or — for a plan with no 5h window — with Weekly moved into the main slot, so nothing says
    /// "no 5h" (Robin, 2026-10-24).
    var leading: Face {
        guard weeklyOnly else { return self }
        var f = Face(account: account, pct: weeklyPct, pace: weeklyPace, reset: weeklyReset, runOut: nil,
                     weeklyPct: nil, weeklyPace: nil, weeklyReset: nil, fetchedAt: fetchedAt, status: status)
        f.session = weekly
        f.leadsWeekly = true
        return f
    }

    /// The main reset, written for its window: "5:50 pm" for 5h, "Thu 9:16 pm" for Weekly.
    func resetClock(_ date: Date) -> String {
        leadsWeekly ? CardContent.dayClock(date.ms, Date.now.ms, Display.use24h, .current) : shortClock(date)
    }
    var isDimmed: Bool { status == .stale || status == .signIn }
    var over: Bool { (pct ?? 0) < 100 && pace.map { (pct ?? 0) > $0 + 3 } ?? false }
    var atLimit: Bool { (pct ?? 0) >= 100 }

    static let agingMs: Int64 = 30 * 60_000

    static func build(account: Account, snapshot: Snapshot?, state: AccountState, history: [HistoryPoint], nowMs: Int64) -> Face {
        let usage = snapshot?.usage(nowMs: nowMs)
        let session = usage?.session, weekly = usage?.weekly
        // A window whose reset has passed reads 0% until the next fetch: its old figure belongs to the closed window.
        let sessionReset = session?.resetsAtMs.flatMap { $0 > nowMs ? $0 : nil }
        let weeklyReset = weekly?.resetsAtMs.flatMap { $0 > nowMs ? $0 : nil }
        let pct = session.map { sessionReset == nil ? 0 : ($0.percent ?? 0) }
        let pace = sessionReset.flatMap { Fmt.elapsedPercent(resetsAtMs: $0, windowLengthMs: HistoryStats.sessionMs, nowMs: nowMs) }
        var runOut: Date?
        if let reset = sessionReset, let pct, pct < 100 {
            let samples = Projection.sessionSamples(history: history, resetAtMs: reset, windowLengthMs: HistoryStats.sessionMs)
            if let hits = Projection.estimate(samples: samples, resetAtMs: reset)?.hitsLimitAtMs, (pace.map { pct > $0 + 3 } ?? false) {
                runOut = Date(ms: hits)
            }
        }
        let status: Status
        if state.lastKind == "plan" { status = .plan }
        else if state.reauthNeeded || state.lastKind == "authNeeded" { status = .signIn }
        else if let snapshot {
            let age = nowMs - snapshot.fetchedAtMs
            status = age > Notice.staleMs ? .stale : age > agingMs ? .aging : .fresh
        } else { status = .noData }
        var face = Face(
            account: account, pct: pct, pace: pace, reset: sessionReset.map(Date.init(ms:)), runOut: runOut,
            weeklyPct: weekly.map { weeklyReset == nil ? 0 : ($0.percent ?? 0) },
            weeklyPace: weeklyReset.flatMap { Fmt.elapsedPercent(resetsAtMs: $0, windowLengthMs: HistoryStats.weekMs, nowMs: nowMs) },
            weeklyReset: weeklyReset.map(Date.init(ms:)), fetchedAt: snapshot.map { Date(ms: $0.fetchedAtMs) }, status: status)
        face.attachContent(sessionWindow: session.map { sessionReset == nil ? UsageWindow(percent: 0, resetsAtMs: nil, serverSeverity: nil) : $0 },
                           weeklyWindow: weekly.map { weeklyReset == nil ? UsageWindow(percent: 0, resetsAtMs: nil, serverSeverity: nil) : $0 },
                           history: history, nowMs: nowMs)
        return face
    }

    /// Builds the card content for both windows; a window whose reset has passed arrives as "not started".
    mutating func attachContent(sessionWindow: UsageWindow?, weeklyWindow: UsageWindow?, history: [HistoryPoint], nowMs: Int64) {
        session = CardContent.window(title: "5h", window: sessionWindow, kind: HistoryStats.sessionKind, history: history, nowMs: nowMs, use24h: Display.use24h)
        weekly = CardContent.window(title: "Weekly", window: weeklyWindow, kind: HistoryStats.weeklyKind, history: history, nowMs: nowMs, use24h: Display.use24h)
    }
}

/// Reads every account's face from the App Group (or from a given storage, for tests and previews).
enum Faces {
    static func load(storage: Storage = Shared.storage, nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> [Face] {
        let snapshots = SnapshotStore(storage: storage), states = AccountStateStore(storage: storage)
        let history = HistoryStore(storage: storage)
        return AccountsStore(storage: storage).load().map { account in
            Face.build(account: account, snapshot: snapshots.load(account.key), state: states.load(account.key),
                       history: history.points(Profile(key: account.key, label: account.label)), nowMs: nowMs)
        }
    }
}

extension Date {
    init(ms: Int64) { self.init(timeIntervalSince1970: Double(ms) / 1000) }
    var ms: Int64 { Int64(timeIntervalSince1970 * 1000) }
}

/// Lower-case "9:50 pm", as on Android.
func shortClock(_ date: Date) -> String {
    CardContent.clock(date.ms, Display.use24h, .current)
}

// MARK: - Preferences shared by the app and the extension

enum Prefs {
    static var defaults: UserDefaults { UserDefaults(suiteName: Shared.appGroup) ?? .standard }

    /// Reset alerts, per account and window (spec mobile §2): "off", "smart" (If busy) or "always".
    /// Android's defaults: 5h If busy, Weekly Always.
    static func resetMode(_ key: String, weekly: Bool) -> String {
        defaults.string(forKey: "ping.\(key).\(weekly ? "weekly" : "session")") ?? (weekly ? "always" : "smart")
    }
    static func setResetMode(_ key: String, weekly: Bool, _ mode: String) {
        defaults.set(mode, forKey: "ping.\(key).\(weekly ? "weekly" : "session")")
    }

    /// The Live Activity switch, and which accounts it shows (default: the first two in your order).
    static var liveActivityOn: Bool {
        get { defaults.bool(forKey: "liveActivity.on") }
        set { defaults.set(newValue, forKey: "liveActivity.on") }
    }
    static var liveActivityKeys: [String]? {
        get { defaults.stringArray(forKey: "liveActivity.keys") }
        set { defaults.set(newValue, forKey: "liveActivity.keys") }
    }
}

// MARK: - Widget configuration: which account, which window

struct AccountEntity: AppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Account"
    static let defaultQuery = AccountQuery()
    let id: String
    let label: String
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(label)") }
}

struct AccountQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [AccountEntity] {
        try await suggestedEntities().filter { identifiers.contains($0.id) }
    }
    func suggestedEntities() async throws -> [AccountEntity] {
        AccountsStore(storage: Shared.storage).load().map { AccountEntity(id: $0.key, label: $0.label) }
    }
    func defaultResult() async -> AccountEntity? { try? await suggestedEntities().first }
}

enum WindowChoice: String, AppEnum {
    case fiveHour, weekly
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Window"
    static let caseDisplayRepresentations: [WindowChoice: DisplayRepresentation] = [.fiveHour: "5h", .weekly: "Weekly"]
}

struct AccountWidgetIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Account"
    @Parameter(title: "Account") var account: AccountEntity?
    init() {}
}

struct AccountWindowWidgetIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Account and window"
    @Parameter(title: "Account") var account: AccountEntity?
    @Parameter(title: "Window", default: .fiveHour) var window: WindowChoice
    init() {}
}

/// Opens the app on an account — the Control Center control's action.
struct OpenAccountIntent: AppIntent {
    static let title: LocalizedStringResource = "Open Cooldown"
    static let openAppWhenRun = true
    @Parameter(title: "Account") var account: AccountEntity?
    init() {}
    init(account: AccountEntity?) { self.account = account }
    func perform() async throws -> some IntentResult { .result() }
}

/// `cooldown://account/<key>` — widgets and the Live Activity open the app on their account.
enum DeepLink {
    static func account(_ key: String) -> URL { URL(string: "cooldown://account/\(key)")! }
    static func key(from url: URL) -> String? {
        guard url.scheme == "cooldown", url.host == "account" else { return nil }
        return url.pathComponents.dropFirst().first
    }
}
