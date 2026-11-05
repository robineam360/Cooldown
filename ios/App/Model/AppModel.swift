import CooldownCore
import Foundation
import Observation

/// The app's state: accounts, the latest readings the fetch repository wrote, and the two sign-in flows.
@MainActor @Observable
final class AppModel {
    var accounts: [Account] = []
    /// The account on screen, or `AppModel.summaryKey` for the All tab (CCRM-98 (Summary Tab)).
    var selectedKey: String? {
        didSet { if let key = selectedKey, key != Self.summaryKey, !isDemo { Prefs.lastAccountKey = key } }
    }
    static let summaryKey = "__all__"
    var showsSummary: Bool { selectedKey == Self.summaryKey }
    /// The account the toolbar's Layout and Share act on: the one on screen, else the first.
    var currentAccountKey: String? {
        if let key = selectedKey, key != Self.summaryKey, account(key) != nil { return key }
        return accounts.first?.key
    }
    /// CCRM-96 (Weekly First): the one-time "Weekly now leads" notice is showing.
    var noticeOwed = false
    var nowMs: Int64 = AppModel.clock()
    private(set) var snapshots: [String: Snapshot] = [:]
    private(set) var states: [String: AccountState] = [:]
    private(set) var histories: [String: [HistoryPoint]] = [:]
    private(set) var records: [String: [HistoryStats.Record]] = [:]
    /// Each account's card layout (the layout sheet), kept on this device.
    private(set) var layouts: [String: CardLayout] = [:]
    var isRefreshing = false

    let storage: Storage
    let repo: UsageRepository?
    let api: APIClient
    private let accountsStore: AccountsStore
    let isDemo: Bool
    /// The reviewer sample data (parity rev B §7): the built-in demo entered from the app, as opposed to the screenshot demo.
    var isSample = false

    init(demo: DemoScenario? = DemoScenario.fromLaunchArguments()) {
        // The User-Agent's version, and CCRM-55's client secret from the uncommitted xcconfig (never logged).
        AppInfo.configure(version: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String,
                          build: Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String, commit: nil)
        Antigravity.configure(clientSecret: Bundle.main.object(forInfoDictionaryKey: "AntigravityClientSecret") as? String)
        api = APIClient()
        if let demo {
            isDemo = true
            storage = Storage(directory: FileManager.default.temporaryDirectory.appending(path: "cooldown-demo"))
            repo = nil
            accountsStore = AccountsStore(storage: storage)
            DemoData.load(demo, into: self)
        } else {
            isDemo = false
            storage = Shared.storage
            repo = UsageRepository(api: api, credentials: CredentialStore.shared(accessGroup: Shared.keychainGroup), storage: storage)
            accountsStore = AccountsStore(storage: storage)
            accounts = accountsStore.load()
            // CCRM-96 (Weekly First): settled once, at the first v2.0 launch — owed only to installs that already
            // had an account (an upgrade from v1.9); a new install never sees it.
            if Prefs.weeklyFirstNotice == nil {
                Prefs.weeklyFirstNotice = WeeklyFirstNotice.settle(hadAccountsBefore: !accounts.isEmpty)
            }
            noticeOwed = Prefs.weeklyFirstNotice == WeeklyFirstNotice.pending && !accounts.isEmpty
            Display.repairMode()
            selectedKey = Self.launchKey(accounts: accounts, openOnSummary: Prefs.openOnSummary, last: Prefs.lastAccountKey)
            reload()
        }
    }

    static func clock() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    /// CCRM-98 (Summary Tab): with two or more accounts and Open on the summary on, the All tab; otherwise the last
    /// account looked at (or the first).
    static func launchKey(accounts: [Account], openOnSummary: Bool, last: String?) -> String? {
        if SummaryRank.opensOnSummary(accountCount: accounts.count, openOnSummary: openOnSummary) { return summaryKey }
        return accounts.first { $0.key == last }?.key ?? accounts.first?.key
    }

    /// The notice's two buttons; either dismisses it for good. "Keep 5h first" sets every account's Headline to 5-hour.
    func answerNotice(keepFiveHourFirst: Bool) {
        if keepFiveHourFirst { for a in accounts { Prefs.setHeadline(a.key, .fiveHour) } }
        if !isDemo { Prefs.weeklyFirstNotice = WeeklyFirstNotice.done }
        noticeOwed = false
        displayChanged()
    }

    /// The account's face — the same numbers, binding and Headline the widgets draw.
    func face(_ key: String) -> Face? {
        guard let account = account(key) else { return nil }
        return Face.build(account: account, snapshot: snapshots[key], state: states[key] ?? AccountState(),
                          history: histories[key] ?? [], nowMs: nowMs)
    }

    // MARK: - Reading

    func reload() {
        guard !isDemo else { return }
        let snaps = SnapshotStore(storage: storage), stateStore = AccountStateStore(storage: storage)
        let history = HistoryStore(storage: storage), sessions = SessionLog(storage: storage)
        for account in accounts {
            let profile = Profile(key: account.key, label: account.label)
            snapshots[account.key] = snaps.load(account.key)
            states[account.key] = stateStore.load(account.key)
            histories[account.key] = history.points(profile)
            records[account.key] = sessions.records(profile)
            layouts[account.key] = CardLayout.load(account.key)
        }
        nowMs = Self.clock()
        selectedKey = Self.validSelection(selectedKey, accounts: accounts)
    }

    /// The selection, kept to something on screen: the All tab only while it shows (2+ accounts), an account only
    /// while it exists; otherwise the All tab where it shows (the default since 2026-11-02, Robin: "make the
    /// summary/all as the default one"), else the first account.
    static func validSelection(_ key: String?, accounts: [Account]) -> String? {
        let summary = SummaryRank.tabShown(accountCount: accounts.count)
        if key == summaryKey { return summary ? key : accounts.first?.key }
        if let key, accounts.contains(where: { $0.key == key }) { return key }
        return summary ? summaryKey : accounts.first?.key
    }

    /// Demo mode fills these directly.
    func set(snapshot: Snapshot?, state: AccountState, history: [HistoryPoint], records: [HistoryStats.Record], for key: String) {
        snapshots[key] = snapshot
        states[key] = state
        histories[key] = history
        self.records[key] = records
    }

    func account(_ key: String?) -> Account? { accounts.first { $0.key == key } }

    func layout(_ key: String) -> CardLayout { layouts[key] ?? CardLayout.load(key) }
    /// The layout as the screen draws it: the Headline's default order unless the user reordered (CCRM-96).
    func drawnLayout(_ key: String) -> CardLayout { layout(key).ordered(by: Prefs.headline(key)) }
    func setLayout(_ layout: CardLayout, for key: String) {
        var layout = layout
        // Showing, hiding or folding a card (reordered nil) keeps the stored order: only a move (true) makes the
        // order the user's, and Reset layout (false) hands it back to the Headline.
        if layout.reordered == nil {
            let stored = self.layout(key)
            if !stored.userOrdered { layout.order = stored.order }
            layout.reordered = stored.reordered
        }
        layouts[key] = layout.normalized
        if !isDemo { CardLayout.save(layout, key) }
    }
    var lastCheckedMs: Int64? { states.values.map(\.lastAttemptAtMs).filter { $0 > 0 }.max() }

    func usage(_ key: String) -> UsageData? { snapshots[key]?.usage(nowMs: nowMs) }

    // MARK: - Refreshing

    func refresh(manual: Bool) async {
        guard let repo, !isRefreshing else { return }
        isRefreshing = true
        defer { isRefreshing = false }
        _ = await repo.refreshAll(accounts.map(\.key), manual: manual)
        reload()
        await Surfaces.update(self)
    }

    // MARK: - Accounts

    func save() {
        guard !isDemo else { return }
        accountsStore.save(accounts)
        Task { await Surfaces.update(self) }
    }

    /// Settings changes that only the surfaces read (reset alert modes, the Live Activity switch).
    func surfacesChanged() { Task { await Surfaces.update(self) } }

    func move(from source: IndexSet, to destination: Int) {
        accounts.move(fromOffsets: source, toOffset: destination)
        save()
    }

    /// Bumped whenever a display choice changes (Settings → Appearance, an account's accent or credits switch), so the
    /// main screen redraws with it; widgets and the Live Activity reload through `surfacesChanged`.
    private(set) var displayStamp = 0
    func displayChanged() {
        displayStamp += 1
        surfacesChanged()
    }

    /// Settings → touch and hold → Clear usage history…: the trend points and the closed-window log, this account only.
    func clearHistory(_ key: String) {
        guard let account = account(key) else { return }
        let profile = Profile(key: key, label: account.label)
        HistoryStore(storage: storage).clear(profile)
        SessionLog(storage: storage).clear(profile)
        reload()
        surfacesChanged()
    }

    /// What clearing the rename field restores (spec/mobile/surfaces/app.md: "Clear the field to go back to …").
    func defaultLabel(for key: String) -> String { Account.defaultLabel(for: key, in: accounts) }

    /// The access token's expiry in the Keychain, for the account page's "renews in …" clause; nil in the sample
    /// data, or when nothing readable is stored.
    func tokenExpiresAtMs(_ key: String) -> Int64? {
        guard !isDemo else { return nil }
        return CredentialStore.shared(accessGroup: Shared.keychainGroup).load(profileKey: key)?.expiresAtMs
    }

    /// The rename sheet's e-mail suggestion for an account (see `AccountEmail`).
    func nameSuggestion(for key: String, current: String) -> String? {
        let others = accounts.filter { $0.key != key }.compactMap { states[$0.key]?.email }
        return AccountEmail.suggestion(email: states[key]?.email, others: others, current: current)
    }

    /// A blank name goes back to the default label, as Android's does. The name is capped at 16 characters.
    func rename(_ key: String, to label: String) {
        guard let i = accounts.firstIndex(where: { $0.key == key }) else { return }
        let trimmed = String(label.trimmingCharacters(in: .whitespacesAndNewlines).prefix(AccountEmail.nameLimit))
        accounts[i].label = trimmed.isEmpty ? defaultLabel(for: key) : trimmed
        save()
    }

    func remove(_ key: String) async {
        if reauthTarget == key { reauthTarget = nil }
        await repo?.forget(key)
        accounts.removeAll { $0.key == key }
        // The account on screen went: the All tab where it shows, else the first account (validSelection).
        selectedKey = Self.validSelection(selectedKey, accounts: accounts)
        save()
        reload()
    }

    /// The account a "Sign in again" is for: the next grant of its provider replaces its credentials instead of adding an
    /// account. Cleared by the Add-account sheet and when the account goes.
    var reauthTarget: String?

    /// Starts a "Sign in again": remembers the account, and returns the provider's sign-in sheet.
    func signInAgain(_ account: Account) -> Sheet {
        reauthTarget = account.key
        return Sheet.signIn(for: account.provider)
    }

    private func addAccount(provider: Provider, grant: TokenGrant) async throws {
        // Signing in again: the same account, new credentials (clears "sign-in stopped", restarts the 30-day clock).
        if let target = reauthTarget, let i = accounts.firstIndex(where: { $0.key == target && $0.provider == provider }) {
            reauthTarget = nil
            try await repo?.adopt(grant, profileKey: target)
            accounts[i].addedAtMs = Self.clock()
            selectedKey = target
            save()
            _ = await repo?.refresh(target, manual: true)
            reload()
            await Surfaces.update(self)
            return
        }
        let key = "\(provider.rawValue)-\(UUID().uuidString.prefix(8).lowercased())"
        try await repo?.adopt(grant, profileKey: key)
        let count = accounts.filter { $0.provider == provider }.count
        let label = count == 0 ? provider.displayName : "\(provider.displayName) \(count + 1)"
        accounts.append(Account(key: key, label: label, provider: provider, addedAtMs: Self.clock()))
        selectedKey = key
        save()
        _ = await repo?.refresh(key, manual: true)
        reload()
        // Reset alerts are on by default (5h If busy, Weekly Always, as on Android), so ask once, here.
        await ResetAlerts.requestPermission()
        await Surfaces.update(self)
    }

    // MARK: - Claude sign-in (PKCE, browser + pasted code)

    private var claudePending: OAuthSignIn.Pending?

    func beginClaudeSignIn() -> URL? {
        let (url, pending) = OAuthSignIn.begin(profileKey: "new")
        claudePending = pending
        return URL(string: url)
    }

    /// Returns an error sentence, or nil when the account was added.
    func completeClaudeSignIn(pasted: String) async -> String? {
        guard let pending = claudePending else { return "Start again — open the sign-in page first." }
        let (code, state) = OAuthSignIn.splitPastedCode(pasted)
        guard !code.isEmpty else { return "Paste the code Claude showed you." }
        guard state.isEmpty || state == pending.state else { return "That code is from a different sign-in — start again." }
        do {
            let response = try await api.exchangeCode(code: code, state: pending.state, verifier: pending.verifier)
            guard response.code == 200 else { return "Sign-in failed (HTTP \(response.code)) — check the code and try again." }
            guard let grant = TokenGrant.claude(body: response.body, previous: nil, nowMs: Self.clock()) else {
                return "The sign-in response was missing a token."
            }
            try await addAccount(provider: .claude, grant: grant)
            claudePending = nil
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    // MARK: - Gemini sign-in (CCRM-55: Google's page in an ASWebAuthenticationSession, then a pasted 4/ code)

    private var antigravityPending: AntigravitySignIn.Pending?

    /// A fresh attempt; "Start again" calls this again, so an older page's code is refused by its state.
    func beginAntigravitySignIn() -> URL {
        let (url, pending) = AntigravitySignIn.begin()
        antigravityPending = pending
        return url
    }

    static let antigravityCodeFailed = "That code didn’t work — it may have expired or been copied wrong. Start again."
    static let antigravityOtherSignIn = "That code is for a different sign-in — start again."

    /// Returns an error sentence (rev B I4/I5), or nil when the account was added. Never logs the code or a token.
    func completeAntigravitySignIn(pasted: String) async -> String? {
        guard let pending = antigravityPending else { return "Start again — open the sign-in page first." }
        let code: String
        switch AntigravitySignIn.parsePaste(pasted, expectedState: pending.state) {
        case .code(let c): code = c
        case .otherSignIn: return Self.antigravityOtherSignIn
        case .unreadable: return Self.antigravityCodeFailed
        }
        if isDemo { return Self.antigravityCodeFailed }
        do {
            let response = try await api.exchangeAntigravityCode(code: code, verifier: pending.verifier)
            guard response.code == 200,
                  let grant = TokenGrant.antigravity(body: response.body, previous: nil, nowMs: Self.clock()),
                  !grant.credentials.refreshToken.isEmpty
            else { return Self.antigravityCodeFailed }
            let assist = try? await api.loadCodeAssist(accessToken: grant.credentials.accessToken).1
            try await addAccount(provider: .antigravity, grant: grant.withAntigravity(project: assist?.project, plan: assist?.plan))
            antigravityPending = nil
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    // MARK: - ChatGPT sign-in (device code)

    enum DeviceFlow: Equatable {
        case stage(DeviceCodeStage, detail: String?)
        case code(DeviceCodeSignIn.Started)
        case done(label: String, plan: String?)
    }

    func startDeviceSignIn() async -> DeviceFlow {
        if isDemo {
            return .code(.init(deviceAuthId: "demo", userCode: "QK7M-4RDX", intervalSec: 5,
                               verifyUrl: "https://auth.openai.com/codex/device", expiresAtMs: Self.clock() + 872_000, profileKey: "new"))
        }
        do {
            let response = try await api.postDeviceUserCode()
            switch DeviceCodeSignIn.classifyStart(status: response.code, body: response.body, profileKey: "new", nowMs: Self.clock()) {
            case .success(let started): return .code(started)
            case .failure(.unavailable): return .stage(.unavailable, detail: nil)
            case .failure(let error): return .stage(.failed, detail: error.localizedDescription)
            }
        } catch {
            return .stage(.failed, detail: error.localizedDescription)
        }
    }

    /// Polls until the code is used, expires or is refused. Cancel the task to stop.
    func waitForDevice(_ started: DeviceCodeSignIn.Started) async -> DeviceFlow {
        if isDemo {
            // `-deviceStage expired|done` shows the other states; otherwise the demo waits.
            let args = ProcessInfo.processInfo.arguments
            if let i = args.firstIndex(of: "-deviceStage"), i + 1 < args.count {
                return args[i + 1] == "done" ? .done(label: "Personal", plan: "Plus") : .stage(.expired, detail: nil)
            }
            while !Task.isCancelled { try? await Task.sleep(nanoseconds: 1_000_000_000) }
        }
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: UInt64(max(started.intervalSec, 1)) * 1_000_000_000)
            guard let response = try? await api.pollDeviceToken(deviceAuthId: started.deviceAuthId, userCode: started.userCode)
            else { continue }
            switch DeviceCodeSignIn.classifyPoll(status: response.code, body: response.body,
                                                 nowMs: Self.clock(), expiresAtMs: started.expiresAtMs) {
            case .pending: continue
            case .expired: return .stage(.expired, detail: nil)
            case .denied(let status): return .stage(.denied, detail: "HTTP \(status)")
            case .granted(let code, let verifier):
                do {
                    let exchange = try await api.exchangeDeviceGrant(authorizationCode: code, codeVerifier: verifier)
                    guard exchange.code == 200,
                          let grant = TokenGrant.chatGPT(body: exchange.body, previous: nil, nowMs: Self.clock())
                    else { return .stage(.denied, detail: "HTTP \(exchange.code)") }
                    try await addAccount(provider: .chatgpt, grant: grant)
                    // API-CONTRACT §11's name ("prolite" → "Pro 100"), never the raw code capitalised.
                    return .done(label: accounts.last?.label ?? "ChatGPT", plan: grant.plan.map(PlanNames.chatgpt))
                } catch {
                    return .stage(.failed, detail: error.localizedDescription)
                }
            }
        }
        return .stage(.expired, detail: nil)
    }
}
