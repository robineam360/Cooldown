// Ported from CooldownMac sources/ccooldown-core/DeviceCodeSignIn.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// OpenAI's device-code sign-in (CCM-76 [Accounts]), the flow the Codex CLI uses. Verified
/// against `codex-rs/login/src/device_code_auth.rs`, and **ported from the already-shipped,
/// tested Android implementation** (`CodexDeviceSignIn.kt` in the sibling app) rather than
/// re-derived — that file has been through real-world testing this port inherits.
///
/// Strictly better than what this app does for Anthropic: the phone asks for a short code, the
/// user types it on `auth.openai.com/codex/device` in any browser — even a different device —
/// and **the server hands back the PKCE verifier**. No localhost socket, no app link, no pasted
/// secret.
///
/// This type is pure — no networking inside it, mirroring `OAuthSignIn`'s shape. The HTTP calls
/// live in `APIClient` (`postDeviceUserCode`, `pollDeviceToken`, `exchangeDeviceGrant`); the
/// app-layer state machine that drives the poll loop is checkpoint 3b's job, not this one's.
///
/// The three URLs are built the way `request_device_code` builds them: the issuer, then
/// `/api/accounts` for the two device endpoints, while the verification URL is
/// `{issuer}/codex/device` — composed by the client, *not* read from the response.
public enum DeviceCodeSignIn {

    /// The Codex CLI's own client id — `CLIENT_ID` in `codex-rs/login/src/auth/manager.rs`.
    public static let clientID = "app_EMoamEEZ73f0CkXaXp7hrann"

    static let issuer = "https://auth.openai.com"

    public static let userCodeURL = issuer + "/api/accounts/deviceauth/usercode"
    public static let pollURL = issuer + "/api/accounts/deviceauth/token"
    public static let verifyURL = issuer + "/codex/device"

    /// `redirect_uri` for the exchange. An HTTPS URL the client never navigates to.
    public static let redirectURI = issuer + "/deviceauth/callback"

    /// `max_wait` in `poll_for_token`, and what the CLI's own prompt tells the user.
    public static let maxWaitMs: Int64 = 15 * 60 * 1000

    /// The response's `interval` is a **string** (`deserialize_interval` parses it) and
    /// defaults to 0 when absent — which would spin — so it is floored at 1 and capped at a
    /// minute so a silly value can't strand the sheet.
    private static let minIntervalSec = 1
    private static let maxIntervalSec = 60
    private static let fallbackIntervalSec = 5

    /// Everything the sheet needs to show a live code and keep polling. Must survive process
    /// death: the user leaves for the browser — possibly on a different device entirely — and
    /// the app may be relaunched before they come back. Same reasoning `OAuthSignIn.Pending`
    /// documents for its own struct; `DeviceSignInPendingStore` below is its persistence
    /// counterpart.
    public struct Started: Sendable, Equatable, Codable {
        public let deviceAuthId: String
        public let userCode: String
        public let intervalSec: Int
        public let verifyUrl: String
        public let expiresAtMs: Int64
        public let profileKey: String

        public init(
            deviceAuthId: String,
            userCode: String,
            intervalSec: Int,
            verifyUrl: String,
            expiresAtMs: Int64,
            profileKey: String
        ) {
            self.deviceAuthId = deviceAuthId
            self.userCode = userCode
            self.intervalSec = intervalSec
            self.verifyUrl = verifyUrl
            self.expiresAtMs = expiresAtMs
            self.profileKey = profileKey
        }
    }

    public enum Poll: Sendable, Equatable {
        /// Not yet — the user hasn't finished on the web page.
        case pending

        /// Past the 15-minute window; the user needs a new code.
        case expired

        /// Terminal, and not pending. The CLI collapses every non-2xx that isn't 403/404 into
        /// one hard failure (`"device auth failed with status {n}"`), so an explicit refusal
        /// and an invalidated code are the same fact to us; the sheet says "start again" either
        /// way rather than inventing a distinction the server doesn't make.
        case denied(status: Int)

        case granted(authorizationCode: String, codeVerifier: String)
    }

    /// `start`'s three pure failure modes, mirrored from Android's `start()`: a 404 means
    /// device-code login is switched off for this client (`.unavailable` — a 404 from *polling*
    /// means something else entirely, see `classifyPoll`, and must never produce this); any
    /// other non-2xx is a bare HTTP failure (`start()`'s `IOException("HTTP $code")`); a 2xx
    /// body `parseStarted` can't read is `.missingCode` (`start()`'s
    /// `"device sign-in response was missing a code"`).
    public enum DeviceCodeSignInError: Error, Equatable, LocalizedError {
        case unavailable
        case httpError(status: Int)
        case missingCode

        public var errorDescription: String? {
            switch self {
            case .unavailable: return "Device sign-in isn't switched on for this client"
            case .httpError(let status): return "HTTP \(status)"
            case .missingCode: return "device sign-in response was missing a code"
            }
        }
    }

    /// The pure half of `start` (the network call is `APIClient.postDeviceUserCode`, 3b's job to
    /// drive). Mirrors Android's `start()` exactly: 404 → `.unavailable`, any other non-2xx →
    /// `.httpError`, a 2xx `parseStarted` can't read → `.missingCode`, otherwise the parsed
    /// `Started`. This is the one rule in the whole protocol where 404 means the opposite thing
    /// it means in `classifyPoll` — getting it backwards either aborts every healthy sign-in or
    /// polls a dead flow for fifteen minutes, so it is classified here, once, rather than left
    /// for each caller to reimplement.
    public static func classifyStart(
        status: Int, body: String, profileKey: String, nowMs: Int64
    ) -> Result<Started, DeviceCodeSignInError> {
        if status == 404 { return .failure(.unavailable) }
        guard (200...299).contains(status) else { return .failure(.httpError(status: status)) }
        guard let started = parseStarted(body: body, profileKey: profileKey, nowMs: nowMs) else {
            return .failure(.missingCode)
        }
        return .success(started)
    }

    /// Pure half of `start` (the network call is 3b's, via `APIClient.postDeviceUserCode`).
    /// Mirrors Android's `parseStarted`. Assumes `device_auth_id`/`user_code` arrive as JSON
    /// strings, as they always have in practice (opaque server-minted tokens) — a numeric JSON
    /// value for either would read as absent here rather than being coerced, unlike Kotlin's
    /// `optString`. Not worth a `JSON`-wide coercion helper for a wire shape nobody sends.
    public static func parseStarted(body: String, profileKey: String, nowMs: Int64) -> Started? {
        guard let data = body.data(using: .utf8),
              let any = try? JSONSerialization.jsonObject(with: data),
              let object = any as? [String: Any]
        else { return nil }
        let json = JSON(object)
        let id = json["device_auth_id"].nonEmptyString ?? ""
        // The CLI accepts both spellings (`#[serde(alias = "usercode")]`).
        let code = json["user_code"].nonEmptyString ?? json["usercode"].nonEmptyString ?? ""
        guard !id.isEmpty, !code.isEmpty else { return nil }
        return Started(
            deviceAuthId: id,
            userCode: code,
            intervalSec: intervalFrom(json),
            verifyUrl: verifyURL,
            expiresAtMs: nowMs + maxWaitMs,
            profileKey: profileKey
        )
    }

    /// `interval` is documented (and always sent) as a JSON string; the `.int` branch below is
    /// defensive only and clamps non-positive numbers to the fallback rather than negative-then-
    /// floor like the string branch does — a harmless divergence from Kotlin's optString/optInt
    /// double fallback on a wire shape nobody actually sends numerically.
    private static func intervalFrom(_ json: JSON) -> Int {
        let field = json["interval"]
        let parsed: Int?
        if let s = field.string {
            parsed = Int(s.trimmingCharacters(in: .whitespacesAndNewlines))
        } else if let n = field.int, n > 0 {
            parsed = n
        } else {
            parsed = nil
        }
        let raw = parsed ?? fallbackIntervalSec
        return min(max(raw, minIntervalSec), maxIntervalSec)
    }

    /// The status mapping, straight from `poll_for_token`:
    ///
    /// - **2xx** → the grant, carrying `authorization_code` and `code_verifier`.
    /// - **403 or 404** → *pending*; sleep `interval` and go round again. Note that 404 means
    ///   the opposite thing here than it does on `/usercode`, where it means the whole flow is
    ///   switched off — hence `DeviceCodeSignInError.unavailable` is raised only from `start`.
    /// - **anything else** → terminal failure. The CLI has no separate "denied" status.
    ///
    /// Expiry is the client's own 15-minute cap, checked before returning `.pending` so a dead
    /// flow can't poll forever.
    public static func classifyPoll(status: Int, body: String, nowMs: Int64, expiresAtMs: Int64) -> Poll {
        if (200...299).contains(status) {
            // A 2xx that carries neither field is a shape change, not a grant. Treated as
            // pending because the 15-minute cap bounds it; a `.denied` here would abort a
            // sign-in that may still be perfectly alive.
            return grantedFrom(body) ?? expiredOrPending(nowMs: nowMs, expiresAtMs: expiresAtMs)
        }
        if status == 403 || status == 404 { return expiredOrPending(nowMs: nowMs, expiresAtMs: expiresAtMs) }
        return .denied(status: status)
    }

    private static func grantedFrom(_ body: String) -> Poll? {
        guard let data = body.data(using: .utf8),
              let any = try? JSONSerialization.jsonObject(with: data),
              let object = any as? [String: Any]
        else { return nil }
        let json = JSON(object)
        guard let code = json["authorization_code"].nonEmptyString,
              let verifier = json["code_verifier"].nonEmptyString
        else { return nil }
        return .granted(authorizationCode: code, codeVerifier: verifier)
    }

    private static func expiredOrPending(nowMs: Int64, expiresAtMs: Int64) -> Poll {
        expiresAtMs > 0 && nowMs >= expiresAtMs ? .expired : .pending
    }

    /// The exact form body `APIClient.exchangeDeviceGrant` posts — pulled out to a pure
    /// function so the shape is pinned by a test rather than only by eye, mirroring how
    /// `OAuthSignIn.begin` pins its own by-hand query construction. Parameter order matches
    /// `exchange_code_for_tokens` in `codex-rs/login/src/server.rs` verbatim (Android's
    /// `exchange()` builds the identical string).
    public static func exchangeFormBody(authorizationCode: String, codeVerifier: String) -> String {
        "grant_type=authorization_code"
            + "&code=" + OAuthSignIn.formEncode(authorizationCode)
            + "&redirect_uri=" + OAuthSignIn.formEncode(redirectURI)
            + "&client_id=" + OAuthSignIn.formEncode(clientID)
            + "&code_verifier=" + OAuthSignIn.formEncode(codeVerifier)
    }
}

/// Persists `DeviceCodeSignIn.Started` across a relaunch, the same way `PendingSignInStore`
/// (in `ProfileState.swift`) persists `OAuthSignIn.Pending` for the Claude flow — this is that
/// store's ChatGPT/device-code sibling, kept next to the type it stores rather than alongside
/// the Claude one, since the two flows' pending shapes never mix.
///
/// Deliberately **not** the Keychain, for the same reason `PendingSignInStore` isn't: nothing
/// here is a credential, and a `deviceAuthId`/`userCode` pair is worthless once the flow
/// finishes or expires.
public struct DeviceSignInPendingStore: Sendable {

    let storage: Storage

    public init(storage: Storage) { self.storage = storage }

    private var file: URL { storage.file("pending-device-signin.json") }

    public func load() -> DeviceCodeSignIn.Started? {
        guard let data = try? Data(contentsOf: file) else { return nil }
        return try? JSONDecoder().decode(DeviceCodeSignIn.Started.self, from: data)
    }

    public func save(_ started: DeviceCodeSignIn.Started) {
        guard let data = try? JSONEncoder().encode(started),
              let text = String(data: data, encoding: .utf8)
        else { return }
        storage.writeAtomically(text, to: file)
    }

    public func clear() {
        try? FileManager.default.removeItem(at: file)
    }
}
