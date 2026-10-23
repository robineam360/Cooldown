import CooldownCore
import Foundation
import Observation

/// CCRM-94 (iOS App): one signed-in account as the app lists it. Order in the list is the user's order —
/// the same everywhere: tabs, sidebar, widgets, Live Activity (wireframe rev B, Settings).
struct Account: Codable, Identifiable, Equatable, Sendable {
    var key: String
    var label: String
    var provider: Provider
    var pinned = false
    var id: String { key }
}

struct AccountsStore: Sendable {
    let storage: Storage
    var file: URL { storage.file("accounts.json") }
    func load() -> [Account] {
        guard let data = try? Data(contentsOf: file) else { return [] }
        return (try? JSONDecoder().decode([Account].self, from: data)) ?? []
    }
    func save(_ accounts: [Account]) {
        guard let data = try? JSONEncoder().encode(accounts), let text = String(data: data, encoding: .utf8) else { return }
        storage.writeAtomically(text, to: file)
    }
}

/// Where the app and its widget extension share files and tokens (CCRM-93 (iOS Spike) proved both on the iPad).
enum Shared {
    static let appGroup = "group.com.robinrichardrajan.cooldown"
    static var keychainGroup: String {
        let prefix = Bundle.main.object(forInfoDictionaryKey: "AppIdentifierPrefix") as? String ?? ""
        return prefix + "com.robinrichardrajan.cooldown.shared"
    }
    static var storage: Storage {
        Storage(directory: FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup))
    }
}

/// The app's state: accounts, the latest readings the fetch repository wrote, and the two sign-in flows.
@MainActor @Observable
final class AppModel {
    var accounts: [Account] = []
    var selectedKey: String?
    var nowMs: Int64 = AppModel.clock()
    private(set) var snapshots: [String: Snapshot] = [:]
    private(set) var states: [String: AccountState] = [:]
    private(set) var histories: [String: [HistoryPoint]] = [:]
    private(set) var records: [String: [HistoryStats.Record]] = [:]
    var isRefreshing = false

    let storage: Storage
    let repo: UsageRepository?
    let api: APIClient
    private let accountsStore: AccountsStore
    let isDemo: Bool

    init(demo: DemoScenario? = DemoScenario.fromLaunchArguments()) {
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
            selectedKey = accounts.first?.key
            reload()
        }
    }

    static func clock() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

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
        }
        nowMs = Self.clock()
    }

    /// Demo mode fills these directly.
    func set(snapshot: Snapshot?, state: AccountState, history: [HistoryPoint], records: [HistoryStats.Record], for key: String) {
        snapshots[key] = snapshot
        states[key] = state
        histories[key] = history
        self.records[key] = records
    }

    func account(_ key: String?) -> Account? { accounts.first { $0.key == key } }
    var pinnedKey: String? { accounts.first(where: \.pinned)?.key ?? accounts.first?.key }
    var lastCheckedMs: Int64? { states.values.map(\.lastAttemptAtMs).filter { $0 > 0 }.max() }

    func usage(_ key: String) -> UsageData? { snapshots[key]?.usage(nowMs: nowMs) }

    // MARK: - Refreshing

    func refresh(manual: Bool) async {
        guard let repo, !isRefreshing else { return }
        isRefreshing = true
        defer { isRefreshing = false }
        _ = await repo.refreshAll(accounts.map(\.key), manual: manual)
        reload()
    }

    // MARK: - Accounts

    func save() { if !isDemo { accountsStore.save(accounts) } }

    func pin(_ key: String) {
        for i in accounts.indices { accounts[i].pinned = accounts[i].key == key }
        save()
    }

    func move(from source: IndexSet, to destination: Int) {
        accounts.move(fromOffsets: source, toOffset: destination)
        save()
    }

    func rename(_ key: String, to label: String) {
        guard let i = accounts.firstIndex(where: { $0.key == key }), !label.isEmpty else { return }
        accounts[i].label = label
        save()
    }

    func remove(_ key: String) async {
        await repo?.forget(key)
        accounts.removeAll { $0.key == key }
        if selectedKey == key { selectedKey = accounts.first?.key }
        save()
        reload()
    }

    private func addAccount(provider: Provider, grant: TokenGrant) async throws {
        let key = "\(provider.rawValue)-\(UUID().uuidString.prefix(8).lowercased())"
        try await repo?.adopt(grant, profileKey: key)
        let count = accounts.filter { $0.provider == provider }.count
        let label = count == 0 ? provider.displayName : "\(provider.displayName) \(count + 1)"
        accounts.append(Account(key: key, label: label, provider: provider))
        selectedKey = key
        save()
        _ = await repo?.refresh(key, manual: true)
        reload()
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
                    return .done(label: accounts.last?.label ?? "ChatGPT", plan: grant.plan?.capitalized)
                } catch {
                    return .stage(.failed, detail: error.localizedDescription)
                }
            }
        }
        return .stage(.expired, detail: nil)
    }
}
