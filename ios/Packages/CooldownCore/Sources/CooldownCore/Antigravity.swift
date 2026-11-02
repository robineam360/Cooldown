import CryptoKit
import Foundation

/// CCRM-55 (Antigravity Account): Google Antigravity's Gemini windows, read the way Google's own Antigravity CLI reads
/// them (spec core API-CONTRACT §10). **Posture:** this presents as that CLI — its public OAuth client and a versioned
/// `antigravity/cli/<v>` User-Agent token, both Google's, either of which Google can change or close. Ported from
/// Android's `AntigravitySource` / `AntigravitySignIn` (the mobile reference).
public enum Antigravity {
    public static let clientID = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"
    public static let redirectURI = "https://antigravity.google/oauth-callback"
    public static let authorizeURL = "https://accounts.google.com/o/oauth2/v2/auth"
    public static let tokenURL = "https://oauth2.googleapis.com/token"
    public static let scope = "https://www.googleapis.com/auth/cloud-platform "
        + "https://www.googleapis.com/auth/userinfo.email "
        + "https://www.googleapis.com/auth/userinfo.profile openid"
    static let base = "https://daily-cloudcode-pa.googleapis.com/v1internal"
    public static let loadCodeAssistURL = base + ":loadCodeAssist"
    public static let quotaURL = base + ":retrieveUserQuotaSummary"

    /// The one gate Google applies: a **versioned** `antigravity/cli/<v>` token returns the Gemini groups; without the
    /// version only the Claude + GPT group comes back (measured 2026-11-02). One constant, tested to carry a version.
    public static let cliVersion = "1.4.12"
    public static var userAgent: String { userAgent(version: AppInfo.version) }
    static func userAgent(version: String) -> String {
        "Cooldown/\(version) antigravity/cli/\(cliVersion) (aidev_client; os_type=ios; arch=arm64; auth_method=consumer)"
    }

    // MARK: The client secret — build configuration only, never the repo (API-CONTRACT §10.1)

    private final class Store: @unchecked Sendable {
        let lock = NSLock()
        var secret = ""
    }
    private static let store = Store()

    /// The app hands it over once at launch from its Info.plist (an uncommitted xcconfig). Never logged.
    public static func configure(clientSecret: String?) {
        store.lock.lock(); defer { store.lock.unlock() }
        store.secret = clientSecret?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }
    static var clientSecret: String {
        store.lock.lock(); defer { store.lock.unlock() }
        return store.secret
    }
    /// Without the secret no token can be minted, so the Gemini row is hidden.
    public static var available: Bool { !clientSecret.isEmpty }

    // MARK: loadCodeAssist (§10.2)

    public struct CodeAssist: Sendable, Equatable {
        public let project: String?
        public let plan: String
    }

    /// `paidTier.id` names the plan; `currentTier` reads free on a Pro account, so never it.
    public static func parseCodeAssist(_ body: String) -> CodeAssist? {
        guard let data = body.data(using: .utf8),
              let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return nil }
        let json = JSON(object)
        let plan = switch json["paidTier"]["id"].string {
        case "g1-pro-tier": "Pro"
        case "g1-ultra-tier": "Ultra"
        default: "Free"
        }
        return CodeAssist(project: json["cloudaicompanionProject"].nonEmptyString, plan: plan)
    }
}

/// `retrieveUserQuotaSummary` → the shared model (spec core BEHAVIOR-SPEC §1.1, Antigravity rows; cases
/// `spec/core/fixtures/antigravity/cases.json`). nil when the reply carries no Gemini group — the User-Agent was
/// rejected or Google changed the reply — so the fetch path keeps the last good reading and never shows a confident
/// 0% or 100%.
public enum AntigravityUsageParser {
    public static let poolName = "Claude + GPT in Antigravity"
    /// Google slides an untouched window's reset to "now + window"; this close counts as untouched.
    static let slidingSlackMs: Int64 = 120_000

    /// `nowMs` is when the reply was **fetched** — a cached reply is re-read with its own fetch time, or every
    /// untouched window would look started once the clock moved past the slack.
    public static func parse(_ raw: String, nowMs: Int64) -> UsageData? {
        guard let data = raw.data(using: .utf8),
              let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return nil }
        var buckets: [String: JSON] = [:]
        for group in JSON(object)["groups"].array ?? [] {
            for bucket in JSON(group)["buckets"].array ?? [] {
                let b = JSON(bucket)
                if let id = b["bucketId"].nonEmptyString { buckets[id] = b }
            }
        }
        guard buckets["gemini-5h"] != nil || buckets["gemini-weekly"] != nil else { return nil }
        let pool5 = buckets["3p-5h"].map { window($0, HistoryStats.sessionMs, nowMs) }
        let poolW = buckets["3p-weekly"].map { window($0, HistoryStats.weekMs, nowMs) }
        return UsageData(
            session: buckets["gemini-5h"].map { window($0, HistoryStats.sessionMs, nowMs) },
            weekly: buckets["gemini-weekly"].map { window($0, HistoryStats.weekMs, nowMs) },
            modelCaps: buckets["gemini-argon-wtu-weekly"].map { [ModelCap(modelName: "Argon", window: window($0, HistoryStats.weekMs, nowMs))] } ?? [],
            credits: nil,
            pools: pool5 == nil && poolW == nil ? [] : [UsagePool(name: poolName, session: pool5, weekly: poolW)])
    }

    /// How many groups the reply held, for the "Reply held 1 of 3 groups" evidence line.
    public static func groupCount(_ raw: String) -> Int {
        guard let data = raw.data(using: .utf8),
              let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return 0 }
        return JSON(object)["groups"].array?.count ?? 0
    }

    private static func window(_ b: JSON, _ lengthMs: Int64, _ nowMs: Int64) -> UsageWindow {
        let fraction = b["remainingFraction"].double
        let used = fraction.map { (1 - $0) * 100 }
        let reset = Timestamps.parseInstantMs(b["resetTime"].nonEmptyString)
        let untouched = (fraction ?? 0) >= 1 && reset.map { abs($0 - (nowMs + lengthMs)) <= slidingSlackMs } == true
        return UsageWindow(percent: used, resetsAtMs: untouched ? nil : reset, serverSeverity: nil)
    }
}

/// CCRM-55: Google sign-in the way Antigravity's headless CLI does it (API-CONTRACT §10.1) — Google's page in an
/// `ASWebAuthenticationSession`, which ends on `antigravity.google/oauth-callback` showing a `4/…` code; the user
/// pastes the code or the whole callback address.
public enum AntigravitySignIn {
    public struct Pending: Sendable, Equatable {
        public let verifier: String
        public let state: String
    }

    public enum Paste: Sendable, Equatable {
        case code(String)
        /// Nothing that looks like a Google code.
        case unreadable
        /// A callback address from another attempt: never exchanged.
        case otherSignIn
    }

    /// A fresh attempt: the authorize URL and the PKCE material to keep until the paste.
    public static func begin() -> (url: URL, pending: Pending) {
        let verifier = randomURLSafe(32), state = randomURLSafe(16)
        return (authorizeURL(challenge: challenge(for: verifier), state: state), Pending(verifier: verifier, state: state))
    }

    static func challenge(for verifier: String) -> String {
        base64URL(Data(SHA256.hash(data: Data(verifier.utf8))))
    }

    public static func authorizeURL(challenge: String, state: String) -> URL {
        var c = URLComponents(string: Antigravity.authorizeURL)!
        c.queryItems = [
            .init(name: "client_id", value: Antigravity.clientID),
            .init(name: "redirect_uri", value: Antigravity.redirectURI),
            .init(name: "response_type", value: "code"),
            .init(name: "scope", value: Antigravity.scope),
            .init(name: "code_challenge", value: challenge),
            .init(name: "code_challenge_method", value: "S256"),
            .init(name: "access_type", value: "offline"),
            .init(name: "prompt", value: "consent"),
            .init(name: "state", value: state),
        ]
        return c.url!
    }

    /// The bare `4/…` code, or a whole callback address whose `state` (when it carries one) must equal
    /// `expectedState`. Whitespace and line breaks from a copy are dropped.
    public static func parsePaste(_ input: String, expectedState: String) -> Paste {
        let text = input.filter { !$0.isWhitespace }
        if text.isEmpty { return .unreadable }
        if text.hasPrefix("http://") || text.hasPrefix("https://") {
            guard let items = URLComponents(string: text)?.queryItems else { return .unreadable }
            let params = Dictionary(items.compactMap { i in i.value.map { (i.name, $0) } }, uniquingKeysWith: { a, _ in a })
            if let state = params["state"], state != expectedState { return .otherSignIn }
            guard let code = params["code"], code.hasPrefix("4/") else { return .unreadable }
            return .code(code)
        }
        return text.hasPrefix("4/") && text.count > 10 ? .code(text) : .unreadable
    }

    static func randomURLSafe(_ bytes: Int) -> String {
        var data = Data(count: bytes)
        _ = data.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, bytes, $0.baseAddress!) }
        return base64URL(data)
    }

    static func base64URL(_ data: Data) -> String {
        data.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

extension TokenGrant {
    /// Google's token reply (§10.1 steps 3–4). A refresh carries no new refresh token: the previous one and the
    /// cached project (in `accountId`) stay. The email comes from the `id_token`.
    public static func antigravity(body: String, previous: Credentials?, nowMs: Int64) -> TokenGrant? {
        guard let data = body.data(using: .utf8),
              let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return nil }
        let json = JSON(object)
        guard let access = json["access_token"].nonEmptyString else { return nil }
        let refresh = json["refresh_token"].nonEmptyString ?? previous?.refreshToken ?? ""
        let expiresIn = json["expires_in"].int64 ?? 0
        return TokenGrant(
            credentials: Credentials(accessToken: access, refreshToken: refresh,
                                     expiresAtMs: expiresIn > 0 ? nowMs + expiresIn * 1000 : 0,
                                     accountId: previous?.accountId, provider: .antigravity),
            email: json["id_token"].nonEmptyString.flatMap(JWT.payload)?["email"].nonEmptyString,
            plan: nil, tier: nil, accountUuid: nil)
    }

    /// The same grant with the Cloud project (cached in the credentials' account-id slot) and the plan.
    public func withAntigravity(project: String?, plan: String?) -> TokenGrant {
        TokenGrant(credentials: Credentials(accessToken: credentials.accessToken, refreshToken: credentials.refreshToken,
                                            expiresAtMs: credentials.expiresAtMs, accountId: project ?? credentials.accountId,
                                            provider: credentials.provider),
                   email: email, plan: plan ?? self.plan, tier: tier, accountUuid: accountUuid)
    }
}

// MARK: - Network (§10.2–10.3): two POST reads and the form token calls

extension APIClient {
    /// Reads the project from the credentials, else `loadCodeAssist`; a stale cached project answering 4xx is
    /// read again once (§10.4).
    public func fetchAntigravityUsage(_ credentials: Credentials) async throws -> HTTPResult {
        let cached = credentials.accountId
        var project = cached
        if project == nil {
            let (r, assist) = try await loadCodeAssist(accessToken: credentials.accessToken)
            guard let p = assist?.project else { return r }
            project = p
        }
        let response = try await postAntigravity(Antigravity.quotaURL, credentials.accessToken, ["project": project ?? ""])
        if cached != nil, (400...499).contains(response.code), response.code != 401, response.code != 429 {
            guard let fresh = try await loadCodeAssist(accessToken: credentials.accessToken).1?.project else { return response }
            return try await postAntigravity(Antigravity.quotaURL, credentials.accessToken, ["project": fresh])
        }
        return response
    }

    public func loadCodeAssist(accessToken: String) async throws -> (HTTPResult, Antigravity.CodeAssist?) {
        let r = try await postAntigravity(Antigravity.loadCodeAssistURL, accessToken, ["metadata": ["ideType": "ANTIGRAVITY"]])
        return (r, r.code == 200 ? Antigravity.parseCodeAssist(r.body) : nil)
    }

    public func exchangeAntigravityCode(code: String, verifier: String) async throws -> HTTPResult {
        try await postAntigravityForm([
            ("grant_type", "authorization_code"), ("code", code), ("client_id", Antigravity.clientID),
            ("client_secret", Antigravity.clientSecret), ("redirect_uri", Antigravity.redirectURI), ("code_verifier", verifier),
        ])
    }

    public func refreshAntigravityToken(_ refreshToken: String) async throws -> HTTPResult {
        try await postAntigravityForm([
            ("grant_type", "refresh_token"), ("refresh_token", refreshToken), ("client_id", Antigravity.clientID),
            ("client_secret", Antigravity.clientSecret),
        ])
    }

    static func antigravityRequest(_ url: String, accessToken: String, body: [String: Any]) throws -> URLRequest {
        var request = URLRequest(url: URL(string: url)!, timeoutInterval: 20)
        request.httpMethod = "POST"
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(Antigravity.userAgent, forHTTPHeaderField: "User-Agent")
        request.httpShouldHandleCookies = false
        return request
    }

    private func postAntigravity(_ url: String, _ accessToken: String, _ body: [String: Any]) async throws -> HTTPResult {
        try await send(Self.antigravityRequest(url, accessToken: accessToken, body: body))
    }

    private func postAntigravityForm(_ fields: [(String, String)]) async throws -> HTTPResult {
        var request = URLRequest(url: URL(string: Antigravity.tokenURL)!, timeoutInterval: 20)
        request.httpMethod = "POST"
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&+=/:?")
        request.httpBody = fields.map { "\($0.0)=\($0.1.addingPercentEncoding(withAllowedCharacters: allowed) ?? "")" }
            .joined(separator: "&").data(using: .utf8)
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue(Antigravity.userAgent, forHTTPHeaderField: "User-Agent")
        request.httpShouldHandleCookies = false
        return try await send(request)
    }
}
