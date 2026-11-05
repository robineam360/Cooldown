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
    /// CCRM-96 (Weekly First): the account's Headline, core §6.2's `sessionBinding`, and whether the plan has a 5h
    /// window at all — set by `build`, kept through `leading` and `lead`.
    var headline: Headline = .default
    var sessionBinding = false
    var hasSessionWindow = false
    /// CCRM-103 (Hybrid Notification) rev D §6 iBars/iStrip: the account's first model limit (Fable, Argon …) as the
    /// app's tile shows it — nil when the plan reports none or Model limits is off for the account (CCRM-99 (Model
    /// Limits Toggle)). Not a window: it stays put through `leading` and `lead`.
    var modelLimit: ModelLimit? = nil

    /// One model limit's reading: its name ("Claude " dropped, as the app's tile), used % and the week gone.
    struct ModelLimit: Equatable, Sendable {
        let name: String
        let pct: Double?
        let pace: Double?
    }

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
        f.carry(self)
        return f
    }

    /// CCRM-96 (Weekly First): the face with `kind` in the main slot. Leading with Weekly moves the 5h window into
    /// the second slot (the `companion…` names below), so every face draws "main, then companion" either way.
    func lead(_ kind: WindowKind) -> Face {
        if weeklyOnly || leadsWeekly { return leading }
        guard kind == .weekly, weeklyPct != nil else { return self }
        var f = Face(account: account, pct: weeklyPct, pace: weeklyPace, reset: weeklyReset, runOut: nil,
                     weeklyPct: pct, weeklyPace: pace, weeklyReset: reset, fetchedAt: fetchedAt, status: status)
        f.session = weekly
        f.weekly = session
        f.leadsWeekly = true
        f.carry(self)
        return f
    }

    /// The face as `surface` leads it, from the account's Headline (spec mobile §5).
    func lead(for surface: HeadlineSurface) -> Face {
        lead(HeadlineRule.lead(setting: headline, surface: surface, hasSession: hasSessionWindow, sessionBinding: sessionBinding))
    }

    private mutating func carry(_ from: Face) {
        headline = from.headline
        sessionBinding = from.sessionBinding
        hasSessionWindow = from.hasSessionWindow
        modelLimit = from.modelLimit
    }

    // The second slot: Weekly under a 5h lead, 5h under a Weekly lead (nil on a weekly-only plan).
    var companionPct: Double? { weeklyPct }
    var companionPace: Double? { weeklyPace }
    var companionReset: Date? { weeklyReset }
    var companionContent: WindowContent? { weekly }
    var companionLabel: String { leadsWeekly ? "5h" : "Weekly" }
    /// The 5h companion while it binds: the bar colour's step and bold (spec mobile §5).
    var companionLoud: Bool { leadsWeekly && sessionBinding && companionPct != nil }
    /// A Weekly lead that is spent: the 5h companion is dropped (rev B: spent drops the 5h row).
    var leadSpent: Bool { leadsWeekly && Pace.isSpent(pct) }
    var showsCompanion: Bool { companionPct != nil && !leadSpent }
    /// CCRM-102 (Spent Week Gate): the main slot is the 5h while the week is spent — drawn dimmed, "Weekly spent".
    var weekGated: Bool { !leadsWeekly && pct != nil && Pace.isSpent(weeklyPct) }

    // MARK: CCRM-103 (Hybrid Notification) — the shape grammar: the 5h a ring on the left, the Weekly a bar on the right

    /// The 5h window's reading, wherever the face carries it.
    struct FiveHour: Equatable {
        let pct: Double?
        let pace: Double?
        let reset: Date?
        var content: WindowContent? = nil
    }

    /// The face the grammar draws, whatever the Headline: Weekly in the main slot (the bars and the text), the 5h as
    /// the companion (the ring). A plan with no 5h is Weekly-led with no companion.
    var grammar: Face { lead(.weekly) }

    /// The 5h: the companion of a Weekly-led face, the main slot otherwise; nil on a plan with no 5h (B1).
    var fiveHour: FiveHour? {
        if leadsWeekly {
            guard companionPct != nil else { return nil }
            return FiveHour(pct: companionPct, pace: companionPace, reset: companionReset, content: companionContent)
        }
        return FiveHour(pct: pct, pace: pace, reset: reset, content: session)
    }

    /// CCRM-102 (Spent Week Gate): the 5h ring greys while the week is spent, on a Weekly-led face or a 5h-led one.
    var fiveGreyed: Bool { leadsWeekly ? leadSpent && companionPct != nil : weekGated }

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
        // Core §6.2: past 90%, or projected to hit the limit before its reset (whatever the pace).
        var sessionHits = false
        if let reset = sessionReset, let pct, pct < 100 {
            let samples = Projection.sessionSamples(history: history, resetAtMs: reset, windowLengthMs: HistoryStats.sessionMs)
            sessionHits = Projection.estimate(samples: samples, resetAtMs: reset)?.hitsLimitAtMs != nil
        }
        let weeklyNow = weekly.map { weeklyReset == nil ? 0 : ($0.percent ?? 0) }
        var face = Face(
            account: account, pct: pct, pace: pace, reset: sessionReset.map(Date.init(ms:)), runOut: runOut,
            weeklyPct: weeklyNow,
            weeklyPace: weeklyReset.flatMap { Fmt.elapsedPercent(resetsAtMs: $0, windowLengthMs: HistoryStats.weekMs, nowMs: nowMs) },
            weeklyReset: weeklyReset.map(Date.init(ms:)), fetchedAt: snapshot.map { Date(ms: $0.fetchedAtMs) }, status: status)
        face.attachContent(sessionWindow: session.map { sessionReset == nil ? UsageWindow(percent: 0, resetsAtMs: nil, serverSeverity: nil) : $0 },
                           weeklyWindow: weekly.map { weeklyReset == nil ? UsageWindow(percent: 0, resetsAtMs: nil, serverSeverity: nil) : $0 },
                           history: history, nowMs: nowMs)
        face.headline = Prefs.headline(account.key)
        face.hasSessionWindow = session != nil
        face.sessionBinding = Pace.sessionBinding(sessionPct: pct, weeklyPct: weeklyNow, sessionHitsLimitBeforeReset: sessionHits)
        face.modelLimit = limit(usage?.modelCaps.first, show: Prefs.showModelLimits(account.key), nowMs: nowMs)
        return face
    }

    /// The face's model limit from the account's first cap; a cap whose reset has passed reads 0%, as the windows do.
    static func limit(_ cap: ModelCap?, show: Bool, nowMs: Int64) -> ModelLimit? {
        guard show, let cap else { return nil }
        let reset = cap.window.resetsAtMs.flatMap { $0 > nowMs ? $0 : nil }
        let passed = cap.window.resetsAtMs != nil && reset == nil
        return ModelLimit(name: cap.modelName.replacingOccurrences(of: "Claude ", with: ""),
                          pct: passed ? 0 : cap.window.percent,
                          pace: reset.flatMap { Fmt.elapsedPercent(resetsAtMs: $0, windowLengthMs: HistoryStats.weekMs, nowMs: nowMs) })
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

/// CCRM-94 (iOS App) parity wireframes rev B §7, owner's call 2026-11-05: the reviewer sample data saves nothing at all,
/// display settings included. On entry the user's current settings are copied into two throwaway suites and every read and
/// write goes there (`Prefs.defaults`, `Prefs.standard`); on Exit the suites are deleted and the real values were never touched.
enum SampleStore {
    nonisolated(unsafe) static var active = false
    static let groupName = "cooldown.sample.group"
    static let standardName = "cooldown.sample.standard"
    nonisolated(unsafe) static let group: UserDefaults = UserDefaults(suiteName: groupName) ?? UserDefaults()
    nonisolated(unsafe) static let standard: UserDefaults = UserDefaults(suiteName: standardName) ?? UserDefaults()

    /// Copies the real settings (their persistent domains, by name) into the two suites; no redirect yet.
    static func seed(realGroup: UserDefaults, groupDomain: String, realStandard: UserDefaults, standardDomain: String) {
        wipe()
        group.setPersistentDomain(realGroup.persistentDomain(forName: groupDomain) ?? [:], forName: groupName)
        standard.setPersistentDomain(realStandard.persistentDomain(forName: standardDomain) ?? [:], forName: standardName)
    }

    static func begin() {
        seed(realGroup: UserDefaults(suiteName: Shared.appGroup) ?? .standard, groupDomain: Shared.appGroup,
             realStandard: .standard, standardDomain: Bundle.main.bundleIdentifier ?? "")
        active = true
    }

    static func end() {
        active = false
        wipe()
    }

    /// Deletes both suites. Also run at launch: a kill during the sample leaves them on disk (the real settings were
    /// never touched, `active` lives only in memory).
    static func wipe() {
        UserDefaults.standard.removePersistentDomain(forName: groupName)
        UserDefaults.standard.removePersistentDomain(forName: standardName)
    }
}

enum Prefs {
    /// The App Group defaults the widgets share — or, while the reviewer sample data is showing, a throwaway copy
    /// (`SampleStore`), so nothing done in the sample is saved.
    static var defaults: UserDefaults { pick(active: SampleStore.active, real: UserDefaults(suiteName: Shared.appGroup) ?? .standard, sample: SampleStore.group) }
    /// The app-only defaults (theme, density, chart size, card layout), redirected the same way.
    static var standard: UserDefaults { pick(active: SampleStore.active, real: .standard, sample: SampleStore.standard) }
    static func pick(active: Bool, real: UserDefaults, sample: UserDefaults) -> UserDefaults { active ? sample : real }

    /// Reset alerts, per account and window (spec mobile §2): "off", "smart" (If busy) or "always".
    /// Android's defaults: 5h If busy, Weekly Always.
    static func resetMode(_ key: String, weekly: Bool) -> String {
        defaults.string(forKey: "ping.\(key).\(weekly ? "weekly" : "session")") ?? (weekly ? "always" : "smart")
    }
    static func setResetMode(_ key: String, weekly: Bool, _ mode: String) {
        defaults.set(mode, forKey: "ping.\(key).\(weekly ? "weekly" : "session")")
    }

    // MARK: v2.0 — every key is new, so v1.9 ignores them after a downgrade (RUNBOOK-v2.0 Step 3).

    /// CCRM-96 (Weekly First): per account, "weekly" (default) · "fiveHour" · "auto".
    static func headline(_ key: String) -> Headline { Headline(key: defaults.string(forKey: "headline.\(key)")) }
    static func setHeadline(_ key: String, _ value: Headline) { defaults.set(value.rawValue, forKey: "headline.\(key)") }

    /// CCRM-99 (Model Limits Toggle): per account, default on.
    static func showModelLimits(_ key: String) -> Bool { defaults.object(forKey: "modelLimits.\(key)") as? Bool ?? true }
    static func setShowModelLimits(_ key: String, _ on: Bool) { defaults.set(on, forKey: "modelLimits.\(key)") }

    /// CCRM-98 (Summary Tab): Appearance › Open on the summary, default on.
    static var openOnSummary: Bool {
        get { defaults.object(forKey: "openOnSummary") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "openOnSummary") }
    }
    /// Appearance › Summary order (Robin, 2026-11-02), default Your order; v1.9 never reads the key.
    static var summaryOrder: SummaryOrder {
        get { SummaryOrder(key: defaults.string(forKey: "summaryOrder")) }
        set { defaults.set(newValue.rawValue, forKey: "summaryOrder") }
    }
    /// The last account looked at, for launch with Open on the summary off (or one account).
    static var lastAccountKey: String? {
        get { defaults.string(forKey: "lastAccount") }
        set { defaults.set(newValue, forKey: "lastAccount") }
    }
    /// CCRM-96 (Weekly First): the one-time notice — nil until the first v2.0 launch settles it, then
    /// `WeeklyFirstNotice.pending` or `.done`.
    static var weeklyFirstNotice: String? {
        get { defaults.string(forKey: "weeklyFirstNotice") }
        set { defaults.set(newValue, forKey: "weeklyFirstNotice") }
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
    /// CCRM-96 (Weekly First): a new Number starts on its account's Headline (`HeadlineWindowOptions`, Auto → Weekly);
    /// the value is then stored with the widget. Placed widgets keep what they stored; nil (nothing stored) reads as
    /// 5h, today's default, so an already placed widget can never change window by itself.
    @Parameter(title: "Window", optionsProvider: HeadlineWindowOptions()) var window: WindowChoice?
    init() {}
}

/// Seeds a new widget's window from the default account's Headline (spec mobile §5, "a new widget's window choice").
struct HeadlineWindowOptions: DynamicOptionsProvider {
    func results() async throws -> [WindowChoice] { [.weekly, .fiveHour] }
    func defaultResult() async -> WindowChoice? {
        guard let account = AccountsStore(storage: Shared.storage).load().first else { return .weekly }
        let kind = HeadlineRule.lead(setting: Prefs.headline(account.key), surface: .newWidget,
                                     hasSession: true, sessionBinding: false)
        return kind == .session ? .fiveHour : .weekly
    }
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
