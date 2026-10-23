// Ported from CooldownMac sources/ccooldown-core/APIClient.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

public struct HTTPResult: Sendable, Equatable {
    public let code: Int
    public let body: String

    // A public struct's auto-synthesized memberwise init is `internal`, not `public` — every
    // caller of a `DeviceSignInAPI`/`UsageAPI`-conforming fake outside this module (a preview
    // tool building fixtures with only ordinary `import CCooldownCore`, not `@testable`) needs
    // to build one. CCM-76 [Accounts] checkpoint 3b's `device-code-preview` is the first such
    // caller; test targets already got this for free via `@testable import`.
    public init(code: Int, body: String) {
        self.code = code
        self.body = body
    }
}

/// The app's version, build and commit (CCM-116 [Onboarding]). Core never reads a bundle by
/// convention, so the app hands these over once at launch (`configure`) from its own
/// `Info.plist`; until then — and in `swift run`, the previews and the tests — it reads as a
/// development build.
public enum AppInfo {
    private final class Store: @unchecked Sendable {
        let lock = NSLock()
        var version: String?
        var build: String?
        var commit: String?
    }
    private static let store = Store()

    /// Call once, early in `applicationDidFinishLaunching`. Empty strings count as absent.
    public static func configure(version: String?, build: String?, commit: String?) {
        store.lock.lock(); defer { store.lock.unlock() }
        store.version = present(version)
        store.build = present(build)
        store.commit = present(commit)
    }

    /// Empty or whitespace counts as absent.
    static func present(_ value: String?) -> String? {
        guard let value, !value.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        return value
    }

    /// "1.0", or nil for a development build.
    public static var marketingVersion: String? {
        store.lock.lock(); defer { store.lock.unlock() }
        return store.version
    }
    public static var build: String? {
        store.lock.lock(); defer { store.lock.unlock() }
        return store.build
    }
    /// The short git hash `build-app.sh` stamps into the bundle, when it did.
    public static var commit: String? {
        store.lock.lock(); defer { store.lock.unlock() }
        return store.commit
    }
    /// The User-Agent's version: the marketing version, or "dev".
    public static var version: String { marketingVersion ?? "dev" }
}


/// Raw HTTP for the endpoints we use. See contract/API-CONTRACT.md §2-§6.
///
/// **The two endpoints have different User-Agent rules — get this wrong and you'll chase a
/// phantom.**
///
/// - token endpoint (`platform.claude.com`): **never** send a claude-code User-Agent.
///   Its WAF 429-blocks `claude-code`, browser-like (`Mozilla`), `curl`, and **empty**
///   UAs, while allowing library-shaped ones (`okhttp`, `axios`, …).
/// - usage endpoint (`api.anthropic.com`): send **our own honest User-Agent**
///   (`Cooldown/<version> (macOS)`). This used to send the claude-code UA on the theory that
///   the endpoint required it; **verified 2026-09-20 that it does not gate on User-Agent at
///   all** — `swift run ua-probe` hit `GET /api/oauth/usage` on both signed-in profiles with
///   the claude-code UA, our own UA, and no UA, and got `status=200 parsed` in all six cases.
///   See `contract/API-CONTRACT.md` §2-§3 and the 2026-09-20 user-agent probe (maintainer notes)
///   for the full table. Android's client (CCRM-68) reached the same conclusion first and
///   already sends `Cooldown/<version> (Android)`; both clients now match.
public struct APIClient: Sendable {

    /// USAGE ENDPOINT: our own honest User-Agent — verified 2026-09-20 that the endpoint does
    /// not gate on User-Agent (`ua-probe`). See the type doc comment above.
    public static var userAgent: String { userAgent(version: AppInfo.version) }
    static func userAgent(version: String) -> String { "Cooldown/\(version) (\(platform))" }

    /// API-CONTRACT §2: our own honest User-Agent names the platform — "iOS" on iPhone and iPad.
    static var platform: String {
        #if os(iOS)
        "iOS"
        #else
        "macOS"
        #endif
    }

    /// Control value only, for `ua-probe`'s three-way comparison — **no longer sent by the
    /// app**. This was the UA believed (wrongly) to be required on the usage endpoint; kept so
    /// the probe has a fixed "old behaviour" arm to diff the honest UA against.
    public static let claudeCodeUserAgent = "claude-code/2.1.214"

    /// TOKEN ENDPOINT ONLY — a library-shaped UA, set **explicitly**.
    ///
    /// The Android client omits the header and lets OkHttp's default through, but
    /// "send no header" isn't portable: `URLSession` substitutes its own default, and an
    /// empty UA is known-blocked. Naming ourselves is deterministic.
    ///
    /// **VERIFIED 2026-07-31**: a real authorization-code exchange with this exact value
    /// returned 200 from a Team account. The WAF accepts it. Don't change the shape casually —
    /// browser-like and empty UAs are known-blocked.
    public static let tokenUserAgent = "CCooldown/1.0"

    static let usageURL = URL(string: "https://api.anthropic.com/api/oauth/usage")!
    /// CCM-68 [Accounts]: the plan tag. Same host, headers and User-Agent rule as usage —
    /// see API-CONTRACT §9.
    static let profileURL = URL(string: "https://api.anthropic.com/api/oauth/profile")!
    static let tokenURL = URL(string: "https://platform.claude.com/v1/oauth/token")!
    static let betaHeader = "oauth-2025-04-20"

    /// CCM-76 [Accounts]: ChatGPT's usage endpoint — read-only, no other endpoint, ever. See
    /// Android's ChatGPT provider notes (2026-09-20, maintainer notes).
    static let chatGPTUsageURL = URL(string: "https://chatgpt.com/backend-api/wham/usage")!

    /// CCM-76 [Accounts]: the three device-code sign-in endpoints — see `DeviceCodeSignIn` for
    /// the pure protocol logic these back.
    static let deviceUserCodeURL = URL(string: DeviceCodeSignIn.userCodeURL)!
    static let deviceTokenPollURL = URL(string: DeviceCodeSignIn.pollURL)!
    static let deviceTokenExchangeURL = URL(string: "https://auth.openai.com/oauth/token")!

    let session: URLSession

    public init(session: URLSession = .shared) {
        self.session = session
    }

    public func fetchUsage(accessToken: String) async throws -> HTTPResult {
        var request = URLRequest(url: Self.usageURL, timeoutInterval: 20)
        request.httpMethod = "GET"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(Self.betaHeader, forHTTPHeaderField: "anthropic-beta")
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        // CCM-104 [Readout]: `LiveUsageMerge`'s one-directional merge rule (2026-09-24) trusts a
        // fetch's timestamp as its actual data time — that premise needs this response to never
        // be a cached one served without a round trip.
        request.cachePolicy = .reloadIgnoringLocalCacheData
        return try await perform(request)
    }

    /// CCM-68 [Accounts]: `/api/oauth/profile` — the plan tag. Same headers as `fetchUsage`.
    public func fetchProfile(accessToken: String) async throws -> HTTPResult {
        var request = URLRequest(url: Self.profileURL, timeoutInterval: 20)
        request.httpMethod = "GET"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(Self.betaHeader, forHTTPHeaderField: "anthropic-beta")
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        return try await perform(request)
    }

    /// CCM-76 [Accounts]: the ChatGPT usage request, built as a pure function so it's testable
    /// without a network round trip — `URL`, `Authorization`, `Accept`, our own honest
    /// User-Agent (CCM-72), and `ChatGPT-Account-Id` only when the account id is non-empty (a
    /// Codex login always carries one, but the header must never be sent empty).
    public static func chatGPTUsageRequest(accessToken: String, accountId: String) -> URLRequest {
        var request = URLRequest(url: chatGPTUsageURL, timeoutInterval: 20)
        request.httpMethod = "GET"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        if !accountId.isEmpty {
            request.setValue(accountId, forHTTPHeaderField: "ChatGPT-Account-Id")
        }
        // CCM-76 [Accounts] §9.6's read-only guarantee, made literal: `chatgpt.com` is a
        // browser domain, and the default `true` would both attach any cookie already in
        // `URLSession.shared`'s shared `HTTPCookieStorage` and persist any `Set-Cookie` the
        // response sends back. This call carries its own bearer token and needs none of that.
        request.httpShouldHandleCookies = false
        // CCM-104 [Readout]: same reasoning as `fetchUsage` above — `LiveUsageMerge`'s
        // one-directional merge rule needs this response to be a real round trip, never served
        // from a local cache.
        request.cachePolicy = .reloadIgnoringLocalCacheData
        return request
    }

    /// GET-only, read-only — no other ChatGPT endpoint is ever called from this app.
    public func fetchChatGPTUsage(accessToken: String, accountId: String) async throws -> HTTPResult {
        try await perform(Self.chatGPTUsageRequest(accessToken: accessToken, accountId: accountId))
    }

    /// CCM-76 [Accounts]: asks OpenAI for a device user code — the first leg of
    /// `DeviceCodeSignIn`. A 404 here means device sign-in is switched off for this client
    /// (`DeviceCodeSignIn.DeviceCodeSignInError.unavailable`); the caller decides that, this
    /// only returns the raw result. Cookies off and our own honest User-Agent, matching the
    /// read-only-cookies-off convention `chatGPTUsageRequest` already established for the
    /// ChatGPT GET path.
    public func postDeviceUserCode() async throws -> HTTPResult {
        var request = URLRequest(url: Self.deviceUserCodeURL, timeoutInterval: 20)
        request.httpMethod = "POST"
        request.httpBody = try JSONSerialization.data(withJSONObject: ["client_id": DeviceCodeSignIn.clientID])
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.httpShouldHandleCookies = false
        return try await perform(request)
    }

    /// CCM-76 [Accounts]: polls for the grant — the caller runs `DeviceCodeSignIn.classifyPoll`
    /// on the result. Same cookies-off, honest-User-Agent treatment as `postDeviceUserCode`.
    public func pollDeviceToken(deviceAuthId: String, userCode: String) async throws -> HTTPResult {
        var request = URLRequest(url: Self.deviceTokenPollURL, timeoutInterval: 20)
        request.httpMethod = "POST"
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "device_auth_id": deviceAuthId,
            "user_code": userCode
        ])
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.httpShouldHandleCookies = false
        return try await perform(request)
    }

    /// CCM-76 [Accounts]: redeems the grant. **Form-encoded**, unlike `postToken` below which is
    /// JSON — the two really do differ (matching Android's `exchange()`, itself verbatim from
    /// `exchange_code_for_tokens` in `codex-rs/login/src/server.rs`). Cookies off and our own
    /// honest User-Agent, same as the other two device endpoints.
    public func exchangeDeviceGrant(authorizationCode: String, codeVerifier: String) async throws -> HTTPResult {
        let form = DeviceCodeSignIn.exchangeFormBody(authorizationCode: authorizationCode, codeVerifier: codeVerifier)
        var request = URLRequest(url: Self.deviceTokenExchangeURL, timeoutInterval: 20)
        request.httpMethod = "POST"
        request.httpBody = form.data(using: .utf8)
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.httpShouldHandleCookies = false
        return try await perform(request)
    }

    /// Exchanges a PKCE authorization code for a fresh token family.
    public func exchangeCode(code: String, state: String, verifier: String) async throws -> HTTPResult {
        try await postToken([
            "grant_type": "authorization_code",
            "code": code,
            "state": state,
            "client_id": OAuthSignIn.clientID,
            "redirect_uri": OAuthSignIn.redirectURI,
            "code_verifier": verifier
        ])
    }

    public func refreshToken(_ refreshToken: String) async throws -> HTTPResult {
        try await postToken([
            "grant_type": "refresh_token",
            "refresh_token": refreshToken,
            "client_id": OAuthSignIn.clientID
        ])
    }

    /// JSON body, not form-encoded — this is what Claude's own SDK posts here.
    private func postToken(_ payload: [String: String]) async throws -> HTTPResult {
        var request = URLRequest(url: Self.tokenURL, timeoutInterval: 20)
        request.httpMethod = "POST"
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        request.setValue(Self.betaHeader, forHTTPHeaderField: "anthropic-beta")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(Self.tokenUserAgent, forHTTPHeaderField: "User-Agent")
        return try await perform(request)
    }

    private func perform(_ request: URLRequest) async throws -> HTTPResult {
        let (data, response) = try await session.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        return HTTPResult(code: code, body: String(data: data, encoding: .utf8) ?? "")
    }
}

// MARK: - Rate limiting

/// Cadence and backoff rules. See contract/API-CONTRACT.md §6.
public enum RateLimits {
    /// CCM-175 [Readout]: the background check's cadence for every account Cooldown has nothing
    /// newer for — an app sign-in, an account with This Mac off, or a local account whose latest
    /// live reading and fetch are both older than this (usage from claude.ai on the web or a
    /// phone shows nowhere on this Mac). Was 15 minutes, with 5 for the menu bar's account only
    /// (CCM-164 [Readout]); one account checked this often is twelve requests an hour.
    public static let backgroundCheckIntervalMinutes: Int64 = 5
    /// CCM-165 [Readout]: a manual refresh (the footer's ↻, the widget's refresh) inside this
    /// window is refused, not queued. It runs from the *later* of the last success and the last
    /// network attempt (`manualFloorStartMs`), so a failed or 429'd attempt starts it too: a
    /// refused or failing tap can't be repeated more than once a minute.
    public static let manualFloorMs: Int64 = 60_000
    /// The automatic reasons (launch, popover opened, account switched, Usage pane shown —
    /// `FetchTrigger.launch` / `.userView`, and `RefreshPolicy.autoFloorMs`) don't fetch again
    /// inside this window since the last *success*. Was the manual floor too until CCM-165
    /// [Readout] split the two.
    public static let autoMinIntervalMs: Int64 = 180_000

    /// CCM-165 [Readout]: where the manual floor starts — the later of the last success and the
    /// last attempt — or nil when there has been neither. One helper, so the repository's refusal
    /// and the popover's countdown read the same instant.
    public static func manualFloorStartMs(fetchedAtMs: Int64, lastAttemptAtMs: Int64) -> Int64? {
        let start = max(fetchedAtMs, lastAttemptAtMs)
        return start > 0 ? start : nil
    }

    /// CCM-175 [Readout] (judge F1): whether an activity settle may fetch now — 60 s from the
    /// later of the last success and the last attempt. Checked before the discovery pass, so a
    /// refused settle costs no Keychain read.
    public static func activityAllows(fetchedAtMs: Int64, lastAttemptAtMs: Int64, nowMs: Int64) -> Bool {
        guard let start = manualFloorStartMs(fetchedAtMs: fetchedAtMs, lastAttemptAtMs: lastAttemptAtMs) else { return true }
        return nowMs - start >= activityMinIntervalMs
    }
    /// The same rule for an activity-triggered poll (CCM-54 [Foundation]), per account and
    /// deliberately its own constant.
    ///
    /// CCM-175 [Readout]: 60 s (was 300 s). An activity fetch now goes only to the account whose
    /// Claude Code folder changed — never every account — and is skipped while a terminal status
    /// line is already reporting it, so the busiest case is one request a minute for one account
    /// in active, non-terminal use (the VS Code panel or the desktop app). The 429 backoff still
    /// holds it off, as before.
    public static let activityMinIntervalMs: Int64 = 60_000
    /// Refresh the access token proactively when it expires within this margin.
    public static let expiryMarginMs: Int64 = 5 * 60_000
    /// Renewals failing continuously for this long mean the refresh token is dead, not
    /// blipping — the token endpoint 429s on dead tokens, which looks transient.
    public static let stuckRefreshMs: Int64 = 6 * 60 * 60_000

    /// `5 min × 2^n`, capped at 60 minutes. Reset `consecutive429` on success.
    public static func backoffMs(consecutive429: Int) -> Int64 {
        let shift = min(consecutive429, 4)
        return min(5 * 60_000 << Int64(shift), 60 * 60_000)
    }
}

// MARK: - ChatGPT fetch outcome classification

/// CCM-76 [Accounts]: classifies a raw `HTTPResult` from `fetchChatGPTUsage` into the same
/// `FetchOutcome` vocabulary `UsageRepository.fetch` uses for Claude, so both providers read
/// the same `ErrorTaxonomy` kinds — 401/403 fold into the existing auth-failure taxonomy
/// (`.authNeeded` → `ErrorKind.auth`), 429 into `.rateLimited`, anything else non-200 into the
/// generic `.server(code:)` bucket. The caller supplies the parsed `UsageData` itself on 200;
/// this only classifies failures, since only Core knows how a 200 was (or wasn't) parsed.
public enum ChatGPTFetchOutcome {
    /// `nil` on 200 — the caller decides success vs. `.invalidResponse` from whether
    /// `ChatGPTUsageParser.parse` returned something.
    public static func classify(code: Int) -> FetchOutcome? {
        switch code {
        case 200: return nil
        case 401, 403: return .authNeeded
        case 429: return .rateLimited
        default: return .server(code: code)
        }
    }
}
