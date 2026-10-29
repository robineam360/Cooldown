import Foundation

/// CCRM-94 (iOS App): one fetch, for one account, the way the mobile family does it — ported from
/// Android's `UsageRepository.doFetch` (the mobile reference), not from the Mac's desktop repository,
/// whose local logins, live readings, activity triggers and fetch budget a phone has no use for.
///
/// Rules, in order (spec core API-CONTRACT §5–6, BEHAVIOR-SPEC §8, §10; spec mobile §1):
/// 1. No credentials → `.noCredentials`.
/// 2. Gates: an automatic fetch waits out the 429 backoff; any fetch inside `minIntervalMs` of the last
///    success is refused (`.tooSoon`). A manual fetch bypasses the backoff — someone is looking.
/// 3. Renew first when the access token expires within 5 minutes.
/// 4. A Claude account parked on a no-usage plan reads its profile instead of the usage endpoint, until
///    the plan changes.
/// 5. Fetch; on an auth failure renew once and retry.
/// 6. Success: state, history sample, reset detection (closed windows to `SessionLog`), snapshot.
///    Failure: state and snapshot status only — the last good numbers stay.
/// All fetches are serialised, so two accounts or a tap and a refresh never race on token rotation.
public actor UsageRepository {

    /// spec mobile §1: 180 s since the last success, for manual and automatic fetches alike on iOS.
    public static let minIntervalMs: Int64 = 180_000
    /// How often the plan tag is re-read when nothing forces it.
    public static let planRecheckMs: Int64 = 24 * 60 * 60_000

    let api: any UsageAPI
    let credentials: any CredentialStoring
    let states: AccountStateStore
    let snapshots: SnapshotStore
    let history: HistoryStore
    let sessions: SessionLog
    let now: @Sendable () -> Int64
    private let gate = SerialGate()

    public init(
        api: any UsageAPI, credentials: any CredentialStoring, storage: Storage,
        now: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }
    ) {
        self.api = api
        self.credentials = credentials
        self.states = AccountStateStore(storage: storage)
        self.snapshots = SnapshotStore(storage: storage)
        self.history = HistoryStore(storage: storage)
        self.sessions = SessionLog(storage: storage)
        self.now = now
    }

    /// The result of one fetch, plus any windows that closed with it (for reset alerts).
    public struct Result: Sendable, Equatable {
        public let outcome: FetchOutcome
        public let closed: [ClosedWindow]
    }

    public func refresh(_ profileKey: String, manual: Bool) async -> Result {
        await gate.run { await self.fetch(profileKey, manual: manual) }
    }

    public func refreshAll(_ profileKeys: [String], manual: Bool) async -> [String: Result] {
        var results: [String: Result] = [:]
        for key in profileKeys { results[key] = await refresh(key, manual: manual) }
        return results
    }

    public func state(_ profileKey: String) -> AccountState { states.load(profileKey) }
    public func snapshot(_ profileKey: String) -> Snapshot? { snapshots.load(profileKey) }

    /// Signing out: the token, the state, the snapshot and the history all go.
    public func forget(_ profileKey: String) {
        credentials.clear(profileKey: profileKey)
        states.clear(profileKey)
        snapshots.clear(profileKey)
        let profile = Profile(key: profileKey, label: profileKey)
        history.clear(profile)
        sessions.clear(profile)
    }

    /// After a sign-in: save the grant and start the account clean.
    public func adopt(_ grant: TokenGrant, profileKey: String) throws {
        try credentials.save(grant.credentials, profileKey: profileKey)
        var state = AccountState()
        state.email = grant.email
        state.plan = grant.plan
        state.tier = grant.tier
        states.save(state, profileKey)
    }

    // MARK: - The fetch

    private func fetch(_ key: String, manual: Bool) async -> Result {
        let t0 = now()
        var state = states.load(key)
        guard let creds = credentials.load(profileKey: key) else {
            return finish(key, &state, .noCredentials, at: t0)
        }

        // 2. Gates. Neither writes anything: nothing was attempted.
        if !manual, t0 < state.backoffUntilMs {
            return Result(outcome: .backedOff(minutesLeft: (state.backoffUntilMs - t0) / 60_000 + 1), closed: [])
        }
        if state.lastSuccessAtMs > 0, t0 - state.lastSuccessAtMs < Self.minIntervalMs {
            return Result(outcome: .tooSoon(secondsLeft: (Self.minIntervalMs - (t0 - state.lastSuccessAtMs)) / 1000,
                                            floor: .rateLimit), closed: [])
        }

        // 3. Renew ahead of expiry.
        var current = creds
        if creds.expiresAtMs > 0, creds.expiresAtMs < t0 + RateLimits.expiryMarginMs {
            guard let renewed = await renew(key, creds, &state) else { return renewalFailed(key, &state, at: t0) }
            current = renewed
        }

        // 4. Parked on a no-usage plan: read the profile, not the usage endpoint.
        if current.provider == .claude, state.lastKind == "plan" || PlanTag.isBlockedPlan(state.plan) {
            let tag = await readPlan(current, &state, at: t0)
            if tag == nil || tag?.usageBlocked == true {
                return finish(key, &state, .plan(Self.planCopy(state.plan)), at: t0)
            }
        }

        // 5. Fetch, renewing once on an auth failure.
        do {
            var response = try await api.fetchUsage(current)
            if Self.isAuthFailure(response.code, current.provider) {
                guard let renewed = await renew(key, credentials.load(profileKey: key) ?? current, &state) else {
                    return renewalFailed(key, &state, at: t0)
                }
                current = renewed
                response = try await api.fetchUsage(current)
            }
            return await handle(response, key: key, creds: current, state: &state, at: t0)
        } catch {
            return finish(key, &state, .network(error.localizedDescription), at: t0)
        }
    }

    private func handle(_ response: HTTPResult, key: String, creds: Credentials, state: inout AccountState, at t0: Int64) async -> Result {
        let parsed: UsageData? = response.code == 200 ? parse(response.body, creds.provider, at: t0) : nil
        if let parsed {
            let at = now()
            state.lastSuccessAtMs = at
            state.consecutive429 = 0
            state.backoffUntilMs = 0
            state.firstRenewalFailAtMs = 0
            state.reauthNeeded = false
            let profile = Profile(key: key, label: key)
            history.record(profile, data: parsed, atMs: at)
            let closed = ResetDetection.observe(&state, data: parsed, nowMs: at)
            for window in closed {
                sessions.record(profile, kind: window.kind, resetAtMs: window.resetAtMs,
                                peakPct: window.peakPct, hitLimit: window.hitLimit, nowMs: at)
            }
            if creds.provider == .chatgpt, let tag = ChatGPTUsageParser.planTag(response.body) {
                state.plan = tag.plan
            } else if creds.provider == .claude, at - state.planCheckedAtMs > Self.planRecheckMs {
                _ = await readPlan(creds, &state, at: at)
            }
            let body = creds.provider == .chatgpt ? ChatGPTUsageParser.redactedBodyForStorage(response.body) : response.body
            snapshots.save(Snapshot(profileKey: key, provider: creds.provider, body: body, fetchedAtMs: at,
                                    lastAttemptAtMs: at, lastKind: "success", lastMessage: "", plan: state.plan))
            let result = finish(key, &state, .success, at: at)
            return Result(outcome: result.outcome, closed: closed)
        }
        switch response.code {
        case 200:
            return finish(key, &state, .invalidResponse("Unrecognized response shape"), at: t0)
        case 429:
            // 5 min × 2^n, n = the 429s before this one (API-CONTRACT §6).
            state.backoffUntilMs = t0 + RateLimits.backoffMs(consecutive429: state.consecutive429)
            state.consecutive429 += 1
            return finish(key, &state, .rateLimited, at: t0)
        case _ where Self.isAuthFailure(response.code, creds.provider):
            state.reauthNeeded = true
            return finish(key, &state, .authNeeded, at: t0)
        case 403 where creds.provider == .claude && PlanTag.isPlanRefusal(response.body):
            _ = await readPlan(creds, &state, at: t0)
            if state.plan == nil { state.plan = "Free" }
            // An account with no windows has no number to be stale from (Android CCBG-27).
            snapshots.clear(key)
            return finish(key, &state, .plan(Self.planCopy(state.plan)), at: t0)
        default:
            return finish(key, &state, .server(code: response.code), at: t0)
        }
    }

    private func parse(_ body: String, _ provider: Provider, at nowMs: Int64) -> UsageData? {
        switch provider {
        case .claude: UsageParser.parse(body)
        case .chatgpt: ChatGPTUsageParser.parse(body, nowMs: nowMs)
        }
    }

    // MARK: - Renewal

    /// A fresh access token, the rotated family saved at once (API-CONTRACT §6); nil on failure.
    private func renew(_ key: String, _ creds: Credentials, _ state: inout AccountState) async -> Credentials? {
        do {
            let response = try await api.renew(creds)
            guard response.code == 200 else {
                if (400...403).contains(response.code) { state.reauthNeeded = true }
                state.lastMessage = "HTTP \(response.code)"
                return nil
            }
            let grant: TokenGrant? = switch creds.provider {
            case .claude: TokenGrant.claude(body: response.body, previous: creds, nowMs: now())
            case .chatgpt: TokenGrant.chatGPT(body: response.body, previous: creds, nowMs: now())
            }
            guard let grant else { state.lastMessage = "no token in response"; return nil }
            try credentials.save(grant.credentials, profileKey: key)
            state.reauthNeeded = false
            state.firstRenewalFailAtMs = 0
            if let email = grant.email { state.email = email }
            return grant.credentials
        } catch {
            state.lastMessage = error.localizedDescription
            return nil
        }
    }

    /// Android `authFailure`: a renewal the server refused means "sign in again"; a transient one is
    /// retried until it has failed for `RateLimits.stuckRefreshMs` (6 h), because the endpoint 429s dead tokens too.
    private func renewalFailed(_ key: String, _ state: inout AccountState, at t0: Int64) -> Result {
        if state.reauthNeeded { return finish(key, &state, .authNeeded, at: t0) }
        if state.firstRenewalFailAtMs == 0 {
            state.firstRenewalFailAtMs = t0
        } else if t0 - state.firstRenewalFailAtMs > RateLimits.stuckRefreshMs {
            state.reauthNeeded = true
            return finish(key, &state, .authNeeded, at: t0)
        }
        let detail = state.lastMessage.isEmpty ? "" : " (\(state.lastMessage))"
        return finish(key, &state, .error("token refresh failed\(detail) — will retry"), at: t0)
    }

    // MARK: - Plan

    @discardableResult
    private func readPlan(_ creds: Credentials, _ state: inout AccountState, at t0: Int64) async -> PlanTag? {
        guard let response = try? await api.fetchProfile(accessToken: creds.accessToken), response.code == 200,
              let tag = PlanTag.parse(response.body)
        else { return nil }
        state.plan = tag.plan
        state.tier = tag.tier
        state.planCheckedAtMs = t0
        return tag
    }

    static func planCopy(_ plan: String?) -> String {
        "Claude doesn't report usage on the \(plan ?? "Free") plan — Pro, Max or Team is needed."
    }

    static func isAuthFailure(_ code: Int, _ provider: Provider) -> Bool {
        code == 401 || (provider == .chatgpt && code == 403)
    }

    // MARK: - Bookkeeping

    private func finish(_ key: String, _ state: inout AccountState, _ outcome: FetchOutcome, at t0: Int64) -> Result {
        state.lastAttemptAtMs = t0
        state.lastKind = Self.kind(outcome)
        if outcome != .success { state.lastMessage = outcome.message } else { state.lastMessage = "" }
        states.save(state, key)
        if outcome != .success, var snapshot = snapshots.load(key) {
            snapshot.lastAttemptAtMs = t0
            snapshot.lastKind = state.lastKind
            snapshot.lastMessage = state.lastMessage
            snapshot.plan = state.plan
            snapshots.save(snapshot)
        }
        return Result(outcome: outcome, closed: [])
    }

    static func kind(_ outcome: FetchOutcome) -> String {
        switch outcome {
        case .success: "success"
        case .tooSoon: "tooSoon"
        case .backedOff: "backedOff"
        case .rateLimited: "rateLimited"
        case .authNeeded: "authNeeded"
        case .noCredentials: "noCredentials"
        case .network: "network"
        case .server: "server"
        case .invalidResponse: "invalidResponse"
        case .error: "error"
        case .plan: "plan"
        case .onlineDisabled, .inFlight, .superseded: "skipped"
        }
    }
}

/// Runs async work one at a time, in arrival order — the "one mutex for all fetches" rule (API-CONTRACT §6).
/// An actor alone isn't enough: it lets a second call in at every `await`.
actor SerialGate {
    private var tail: Task<Void, Never>?

    func run<T: Sendable>(_ work: @escaping @Sendable () async -> T) async -> T {
        let previous = tail
        let task = Task<T, Never> {
            await previous?.value
            return await work()
        }
        tail = Task { _ = await task.value }
        return await task.value
    }
}
