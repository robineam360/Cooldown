import Foundation
import Testing

@testable import CooldownCore

/// CCRM-94 (iOS App): the mobile fetch repository, against a scripted API, an in-memory Keychain, a temp
/// folder standing in for the App Group, and a clock the test moves.

// MARK: - Test doubles

final class FakeAPI: UsageAPI, @unchecked Sendable {
    private let lock = NSLock()
    private var usage: [HTTPResult] = []
    private var renewals: [HTTPResult] = []
    private var profiles: [HTTPResult] = []
    private(set) var usageTokens: [String] = []
    private(set) var renewCount = 0
    private(set) var profileCount = 0
    private var inFlight = 0
    private(set) var maxInFlight = 0

    func queueUsage(_ r: HTTPResult...) { lock.withLock { usage += r } }
    func queueRenewal(_ r: HTTPResult...) { lock.withLock { renewals += r } }
    func queueProfile(_ r: HTTPResult...) { lock.withLock { profiles += r } }

    private func enter() { lock.withLock { inFlight += 1; maxInFlight = max(maxInFlight, inFlight) } }
    private func leave() { lock.withLock { inFlight -= 1 } }

    func fetchUsage(_ credentials: Credentials) async throws -> HTTPResult {
        enter(); defer { leave() }
        try await Task.sleep(nanoseconds: 2_000_000)
        return lock.withLock {
            usageTokens.append(credentials.accessToken)
            return usage.isEmpty ? HTTPResult(code: 500, body: "") : usage.removeFirst()
        }
    }
    func renew(_ credentials: Credentials) async throws -> HTTPResult {
        lock.withLock { renewCount += 1; return renewals.isEmpty ? HTTPResult(code: 500, body: "") : renewals.removeFirst() }
    }
    func fetchProfile(accessToken: String) async throws -> HTTPResult {
        lock.withLock { profileCount += 1; return profiles.isEmpty ? HTTPResult(code: 500, body: "") : profiles.removeFirst() }
    }
}

final class MemoryCredentials: CredentialStoring, @unchecked Sendable {
    private let lock = NSLock()
    private var items: [String: Credentials] = [:]
    func load(profileKey: String) -> Credentials? { lock.withLock { items[profileKey] } }
    func save(_ credentials: Credentials, profileKey: String) throws { lock.withLock { items[profileKey] = credentials } }
    func clear(profileKey: String) { _ = lock.withLock { items.removeValue(forKey: profileKey) } }
}

final class Clock: @unchecked Sendable {
    private let lock = NSLock()
    private var ms: Int64
    init(_ ms: Int64) { self.ms = ms }
    var now: Int64 { lock.withLock { ms } }
    func advance(seconds: Int64) { lock.withLock { ms += seconds * 1000 } }
}

// MARK: - Fixtures

let t0: Int64 = 1_790_000_000_000  // a fixed "now"

func iso(_ ms: Int64) -> String {
    let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
}

/// A minimal Claude usage body — the flat blocks the parser falls back to (fixture `no-limits-array`).
func claudeBody(session: Double, sessionReset: Int64, weekly: Double = 10, weeklyReset: Int64 = t0 + 3 * 86_400_000) -> String {
    #"{"five_hour":{"utilization":\#(session),"resets_at":"\#(iso(sessionReset))"},"seven_day":{"utilization":\#(weekly),"resets_at":"\#(iso(weeklyReset))"}}"#
}

func tokenBody(access: String, refresh: String, expiresIn: Int = 28_800) -> String {
    #"{"access_token":"\#(access)","refresh_token":"\#(refresh)","expires_in":\#(expiresIn)}"#
}

let proProfile = (try? Fixtures.text("profile/pro.json")) ?? ""
let freeProfile = (try? Fixtures.text("profile/free.json")) ?? ""
let planRefusal = (try? Fixtures.text("profile/403-plan-refusal.json")) ?? ""

struct Harness {
    let api = FakeAPI()
    let creds = MemoryCredentials()
    let clock = Clock(t0)
    let dir: URL
    let repo: UsageRepository
    let storage: Storage

    init(provider: Provider = .claude, expiresAtMs: Int64 = t0 + 8 * 3_600_000) {
        dir = FileManager.default.temporaryDirectory.appending(path: "cooldown-repo-\(UUID().uuidString)")
        storage = Storage(directory: dir)
        let clock = self.clock
        repo = UsageRepository(api: api, credentials: creds, storage: storage, now: { clock.now })
        try? creds.save(Credentials(accessToken: "a0", refreshToken: "r0", expiresAtMs: expiresAtMs,
                                    accountId: provider == .chatgpt ? "acct" : nil, provider: provider),
                        profileKey: "teams")
    }
}

// MARK: - Tests

@Suite("Mobile fetch repository")
struct UsageRepositoryTests {

    @Test("a success writes the state, a history sample and the App Group snapshot")
    func success() async throws {
        let h = Harness()
        h.api.queueUsage(HTTPResult(code: 200, body: claudeBody(session: 42, sessionReset: t0 + 3_600_000)))
        h.api.queueProfile(HTTPResult(code: 200, body: proProfile))
        let r = await h.repo.refresh("teams", manual: false)
        #expect(r.outcome == .success)
        let state = await h.repo.state("teams")
        #expect(state.lastSuccessAtMs == t0)
        #expect(state.lastKind == "success")
        #expect(state.plan == "Pro")
        let snap = try #require(await h.repo.snapshot("teams"))
        #expect(snap.usage(nowMs: t0)?.session?.percent == 42)
        #expect(HistoryStore(storage: h.storage).points(Profile(key: "teams", label: "")).count == 1)
    }

    @Test("no saved token means no fetch")
    func noCredentials() async {
        let h = Harness()
        h.creds.clear(profileKey: "teams")
        #expect(await h.repo.refresh("teams", manual: true).outcome == .noCredentials)
        #expect(h.api.usageTokens.isEmpty)
    }

    @Test("inside 180 s of a success any fetch is refused; past it, it runs")
    func minInterval() async {
        let h = Harness()
        h.api.queueProfile(HTTPResult(code: 200, body: proProfile))
        h.api.queueUsage(HTTPResult(code: 200, body: claudeBody(session: 10, sessionReset: t0 + 3_600_000)),
                         HTTPResult(code: 200, body: claudeBody(session: 11, sessionReset: t0 + 3_600_000)))
        _ = await h.repo.refresh("teams", manual: false)
        h.clock.advance(seconds: 60)
        #expect(await h.repo.refresh("teams", manual: true).outcome == .tooSoon(secondsLeft: 120, floor: .rateLimit))
        h.clock.advance(seconds: 121)
        #expect(await h.repo.refresh("teams", manual: true).outcome == .success)
        #expect(h.api.usageTokens.count == 2)
    }

    @Test("429 backs off 5 min, then 10; an automatic fetch waits, a manual one goes through")
    func backoff() async {
        let h = Harness()
        h.api.queueUsage(HTTPResult(code: 429, body: ""), HTTPResult(code: 429, body: ""))
        #expect(await h.repo.refresh("teams", manual: false).outcome == .rateLimited)
        #expect(await h.repo.state("teams").backoffUntilMs == t0 + 5 * 60_000)
        h.clock.advance(seconds: 60)
        #expect(await h.repo.refresh("teams", manual: false).outcome == .backedOff(minutesLeft: 5))
        #expect(h.api.usageTokens.count == 1, "the automatic fetch made no call")
        #expect(await h.repo.refresh("teams", manual: true).outcome == .rateLimited)
        #expect(await h.repo.state("teams").backoffUntilMs == t0 + 60_000 + 10 * 60_000)
    }

    @Test("a token expiring within 5 minutes is renewed first, and the rotated family is saved")
    func renewAhead() async {
        let h = Harness(expiresAtMs: t0 + 60_000)
        h.api.queueRenewal(HTTPResult(code: 200, body: tokenBody(access: "a1", refresh: "r1")))
        h.api.queueProfile(HTTPResult(code: 200, body: proProfile))
        h.api.queueUsage(HTTPResult(code: 200, body: claudeBody(session: 5, sessionReset: t0 + 3_600_000)))
        #expect(await h.repo.refresh("teams", manual: false).outcome == .success)
        #expect(h.api.usageTokens == ["a1"])
        #expect(h.creds.load(profileKey: "teams")?.refreshToken == "r1")
    }

    @Test("a 401 renews once and retries")
    func authRetry() async {
        let h = Harness()
        h.api.queueUsage(HTTPResult(code: 401, body: ""),
                         HTTPResult(code: 200, body: claudeBody(session: 7, sessionReset: t0 + 3_600_000)))
        h.api.queueRenewal(HTTPResult(code: 200, body: tokenBody(access: "a2", refresh: "r2")))
        h.api.queueProfile(HTTPResult(code: 200, body: proProfile))
        #expect(await h.repo.refresh("teams", manual: false).outcome == .success)
        #expect(h.api.usageTokens == ["a0", "a2"])
    }

    @Test("a renewal the server refuses means sign in again")
    func renewalRefused() async {
        let h = Harness()
        h.api.queueUsage(HTTPResult(code: 401, body: ""))
        h.api.queueRenewal(HTTPResult(code: 400, body: ""))
        #expect(await h.repo.refresh("teams", manual: false).outcome == .authNeeded)
        #expect(await h.repo.state("teams").reauthNeeded)
    }

    @Test("a transient renewal failure retries, and becomes sign-in-again after 6 hours")
    func renewalStuck() async {
        let h = Harness(expiresAtMs: t0 + 60_000)
        h.api.queueRenewal(HTTPResult(code: 500, body: ""), HTTPResult(code: 500, body: ""))
        let first = await h.repo.refresh("teams", manual: false).outcome
        guard case .error(let detail) = first else { Issue.record("expected an error, got \(first)"); return }
        #expect(detail.contains("will retry"))
        h.clock.advance(seconds: 6 * 3600 + 1)
        #expect(await h.repo.refresh("teams", manual: false).outcome == .authNeeded)
    }

    @Test("Free plan 403: parked, the snapshot dropped, then only the profile is read until the plan changes")
    func planParking() async throws {
        let h = Harness()
        h.api.queueUsage(HTTPResult(code: 200, body: claudeBody(session: 3, sessionReset: t0 + 3_600_000)),
                         HTTPResult(code: 403, body: planRefusal))
        h.api.queueProfile(HTTPResult(code: 200, body: proProfile), HTTPResult(code: 200, body: freeProfile))
        _ = await h.repo.refresh("teams", manual: false)
        #expect(await h.repo.snapshot("teams") != nil)
        h.clock.advance(seconds: 200)
        guard case .plan(let copy) = await h.repo.refresh("teams", manual: false).outcome else { Issue.record("not parked"); return }
        #expect(copy.contains("Free plan"))
        #expect(await h.repo.snapshot("teams") == nil)
        // Still Free: the profile is read, the usage endpoint is not.
        h.api.queueProfile(HTTPResult(code: 200, body: freeProfile))
        let calls = h.api.usageTokens.count
        h.clock.advance(seconds: 200)
        guard case .plan = await h.repo.refresh("teams", manual: false).outcome else { Issue.record("not parked"); return }
        #expect(h.api.usageTokens.count == calls)
        // Upgraded: usage is fetched again.
        h.api.queueProfile(HTTPResult(code: 200, body: proProfile))
        h.api.queueUsage(HTTPResult(code: 200, body: claudeBody(session: 1, sessionReset: t0 + 3_600_000)))
        h.clock.advance(seconds: 200)
        #expect(await h.repo.refresh("teams", manual: false).outcome == .success)
    }

    @Test("a ChatGPT snapshot is saved without email, user_id or account_id (spec core §9.6)")
    func chatGPTRedaction() async throws {
        let h = Harness(provider: .chatgpt)
        let body = try Fixtures.text("captured/chatgpt-usage-2026-09.json")
        h.clock.advance(seconds: (1_788_712_966_000 - 8_798_000 - t0) / 1000)
        h.api.queueUsage(HTTPResult(code: 200, body: body))
        #expect(await h.repo.refresh("teams", manual: false).outcome == .success)
        let snap = try #require(await h.repo.snapshot("teams"))
        for key in ["\"email\"", "\"user_id\"", "\"account_id\""] { #expect(!snap.body.contains(key)) }
        #expect(snap.usage(nowMs: h.clock.now)?.weekly?.percent == 79)
        #expect(await h.repo.state("teams").plan != nil)
    }

    @Test("a reset closes the old window at its peak; drift does not")
    func resetDetection() async throws {
        let h = Harness()
        let resetA = t0 + 3_600_000
        h.api.queueProfile(HTTPResult(code: 200, body: proProfile))
        h.api.queueUsage(HTTPResult(code: 200, body: claudeBody(session: 40, sessionReset: resetA)),
                         HTTPResult(code: 200, body: claudeBody(session: 55, sessionReset: resetA + 300)),
                         HTTPResult(code: 200, body: claudeBody(session: 5, sessionReset: resetA + 5 * 3_600_000)))
        #expect(await h.repo.refresh("teams", manual: false).closed.isEmpty)
        h.clock.advance(seconds: 600)
        #expect(await h.repo.refresh("teams", manual: false).closed.isEmpty, "300 ms of drift is the same window")
        h.clock.advance(seconds: 3600)
        let closed = await h.repo.refresh("teams", manual: false).closed
        #expect(closed == [ClosedWindow(kind: "session", resetAtMs: resetA, peakPct: 55)])
        let records = SessionLog(storage: h.storage).records(Profile(key: "teams", label: ""))
        #expect(records.count == 1)
        #expect(records.first?.peakPct == 55)
        #expect(await h.repo.state("teams").sessionPeak == 5)
    }

    @Test("a failure keeps the last good numbers and records why")
    func failureKeepsNumbers() async throws {
        let h = Harness()
        h.api.queueProfile(HTTPResult(code: 200, body: proProfile))
        h.api.queueUsage(HTTPResult(code: 200, body: claudeBody(session: 33, sessionReset: t0 + 3_600_000)),
                         HTTPResult(code: 503, body: ""))
        _ = await h.repo.refresh("teams", manual: false)
        h.clock.advance(seconds: 200)
        #expect(await h.repo.refresh("teams", manual: false).outcome == .server(code: 503))
        let snap = try #require(await h.repo.snapshot("teams"))
        #expect(snap.usage(nowMs: h.clock.now)?.session?.percent == 33)
        #expect(snap.lastKind == "server")
        #expect(snap.fetchedAtMs == t0)
    }

    @Test("fetches never overlap, even for different accounts started together")
    func serialised() async throws {
        let h = Harness()
        try h.creds.save(Credentials(accessToken: "b0", refreshToken: "rb", expiresAtMs: t0 + 8 * 3_600_000), profileKey: "product")
        for _ in 0..<2 { h.api.queueProfile(HTTPResult(code: 200, body: proProfile)) }
        h.api.queueUsage(HTTPResult(code: 200, body: claudeBody(session: 1, sessionReset: t0 + 3_600_000)),
                         HTTPResult(code: 200, body: claudeBody(session: 2, sessionReset: t0 + 3_600_000)))
        async let a = h.repo.refresh("teams", manual: false)
        async let b = h.repo.refresh("product", manual: false)
        _ = await (a, b)
        #expect(h.api.maxInFlight == 1)
    }

    @Test("token grants: Claude keeps the old refresh token when none is rotated; ChatGPT reads the JWTs")
    func grants() throws {
        let previous = Credentials(accessToken: "a", refreshToken: "keep", expiresAtMs: 0)
        let claude = try #require(TokenGrant.claude(body: #"{"access_token":"x","expires_in":3600}"#, previous: previous, nowMs: 1000))
        #expect(claude.credentials.refreshToken == "keep")
        #expect(claude.credentials.expiresAtMs == 1000 + 3_600_000)

        func jwt(_ payload: String) -> String {
            let b = Data(payload.utf8).base64EncodedString().replacingOccurrences(of: "=", with: "")
                .replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
            return "h.\(b).s"
        }
        let access = jwt(#"{"exp":2000000000,"https://api.openai.com/profile":{"email":"me@example.com"}}"#)
        let id = jwt(#"{"https://api.openai.com/auth":{"chatgpt_account_id":"acc-1","chatgpt_plan_type":"plus"}}"#)
        let chat = try #require(TokenGrant.chatGPT(
            body: #"{"access_token":"\#(access)","refresh_token":"rt","id_token":"\#(id)"}"#, previous: nil, nowMs: 0))
        #expect(chat.credentials.expiresAtMs == 2_000_000_000_000)
        #expect(chat.credentials.accountId == "acc-1")
        #expect(chat.credentials.provider == .chatgpt)
        #expect(chat.plan == "plus")
        #expect(chat.email == "me@example.com")
    }
}
