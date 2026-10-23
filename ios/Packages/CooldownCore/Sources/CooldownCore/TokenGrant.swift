import Foundation

/// CCRM-94 (iOS App): what a token endpoint handed back — after a sign-in or a renewal — read the way the
/// mobile reference reads it (Android's `ClaudeSource`/`ChatGptSource.parseTokenResponse`; the Mac's
/// `completeSignIn` and `parseDeviceGrant` agree). API-CONTRACT §4.5 for Claude, §9.6-adjacent for ChatGPT.
public struct TokenGrant: Sendable, Equatable {
    public let credentials: Credentials
    public let email: String?
    public let plan: String?
    public let tier: String?
    public let accountUuid: String?

    /// Claude's `/v1/oauth/token` response. `access_token` is required; a renewal may leave
    /// `refresh_token` out, in which case the previous one stays (rotation is optional).
    public static func claude(body: String, previous: Credentials?, nowMs: Int64) -> TokenGrant? {
        guard let json = object(body), let access = json["access_token"].nonEmptyString else { return nil }
        guard let refresh = json["refresh_token"].nonEmptyString ?? previous?.refreshToken, !refresh.isEmpty else {
            return nil
        }
        let expiresIn = json["expires_in"].int64 ?? 0
        return TokenGrant(
            credentials: Credentials(
                accessToken: access, refreshToken: refresh,
                expiresAtMs: expiresIn > 0 ? nowMs + expiresIn * 1000 : 0,
                accountId: previous?.accountId, provider: .claude),
            email: json["account"]["email_address"].nonEmptyString,
            plan: json["subscriptionType"].nonEmptyString,
            tier: json["rate_limit_tier"].nonEmptyString,
            accountUuid: json["account"]["uuid"].nonEmptyString)
    }

    /// OpenAI's `/oauth/token` response, for the device-code exchange and for renewals. `expires_in` is
    /// usually absent, so the access token's JWT `exp` is the fallback; the id token's
    /// `https://api.openai.com/auth` claim carries the account id and plan; the access token's
    /// `https://api.openai.com/profile` claim carries the email.
    public static func chatGPT(body: String, previous: Credentials?, nowMs: Int64) -> TokenGrant? {
        guard let json = object(body), let access = json["access_token"].nonEmptyString else { return nil }
        let refresh = json["refresh_token"].nonEmptyString ?? previous?.refreshToken ?? ""
        let accessPayload = JWT.payload(access)
        let expiresIn = json["expires_in"].int64 ?? 0
        let expiresAtMs = expiresIn > 0
            ? nowMs + expiresIn * 1000
            : (accessPayload.flatMap { $0["exp"].double }.map { Int64($0 * 1000) } ?? 0)
        let auth = json["id_token"].nonEmptyString.flatMap(JWT.payload)?["https://api.openai.com/auth"]
        return TokenGrant(
            credentials: Credentials(
                accessToken: access, refreshToken: refresh, expiresAtMs: expiresAtMs,
                accountId: auth?["chatgpt_account_id"].nonEmptyString ?? previous?.accountId,
                provider: .chatgpt),
            email: accessPayload?["https://api.openai.com/profile"]["email"].nonEmptyString,
            plan: auth?["chatgpt_plan_type"].nonEmptyString,
            tier: nil,
            accountUuid: nil)
    }

    private static func object(_ body: String) -> JSON? {
        guard let data = body.data(using: .utf8),
              let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        else { return nil }
        return JSON(object)
    }
}

/// Reads a JWT's payload without verifying it — only to learn what the server already told us (expiry,
/// account id, email). Ported from the Mac's `CodexLoginDiscovery.decodeJWTPayload`.
enum JWT {
    static func payload(_ token: String) -> JSON? {
        let parts = token.split(separator: ".")
        guard parts.count == 3, let data = base64URLDecode(String(parts[1])),
              let raw = try? JSONSerialization.jsonObject(with: data)
        else { return nil }
        return JSON(raw)
    }

    private static func base64URLDecode(_ value: String) -> Data? {
        var base64 = value.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while base64.count % 4 != 0 { base64 += "=" }
        return Data(base64Encoded: base64)
    }
}
