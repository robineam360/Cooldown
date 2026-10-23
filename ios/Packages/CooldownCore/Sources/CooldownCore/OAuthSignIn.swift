// Ported from CooldownMac sources/ccooldown-core/OAuthSignIn.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import CryptoKit
import Foundation

/// PKCE authorization-code sign-in. See contract/API-CONTRACT.md §4.
///
/// The token family is minted **on this Mac**, so no other machine shares the refresh
/// token and nothing external can rotate it away. That is a deliberate design property,
/// not an accident — do not move token minting to a server.
public enum OAuthSignIn {

    public static let authorizeURL = "https://claude.com/cai/oauth/authorize"
    public static let redirectURI = "https://platform.claude.com/oauth/code/callback"
    public static let clientID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"

    /// The exact scope set Claude Code requests. A **reduced set is rejected at grant
    /// time** with "Invalid request format", even though only `user:profile` is needed
    /// for usage — it must match the client's registered set verbatim.
    public static let scope = "org:create_api_key user:profile user:inference "
        + "user:sessions:claude_code user:mcp_servers user:file_upload"

    /// The authorization-code family is capped around 30 days from sign-in. The token
    /// response omits the real figure, so this is an estimate for the re-sign-in prompt
    /// — present it as approximate, never as fact.
    public static let estimatedFamilyMs: Int64 = 30 * 24 * 60 * 60 * 1000

    /// Must survive process death: the user leaves for the browser, and the app may be
    /// relaunched before they come back with the code.
    public struct Pending: Sendable, Equatable, Codable {
        public let verifier: String
        public let state: String
        public let profileKey: String
    }

    /// Builds the authorize URL and the PKCE material for the return trip.
    ///
    /// The query is assembled **by hand**, in this parameter order, with form-encoding.
    /// Both matter: a subtly different request is accepted at the consent page and then
    /// rejected at grant-submit with "Invalid request format".
    public static func begin(profileKey: String) -> (url: String, pending: Pending) {
        let verifier = randomURLSafe(byteCount: 32)
        // `state` IS the verifier, exactly as Claude Code and opencode do it. A shorter
        // separate state was the last deviation from the working request and still drew
        // "Invalid request format". On-device, the marginal CSRF benefit doesn't justify
        // diverging from what is known to work.
        let state = verifier

        let url = authorizeURL + "?"
            + "code=true"
            + "&client_id=" + formEncode(clientID)
            + "&response_type=code"
            + "&redirect_uri=" + formEncode(redirectURI)
            + "&scope=" + formEncode(scope)
            + "&code_challenge=" + formEncode(challenge(for: verifier))
            + "&code_challenge_method=S256"
            + "&state=" + formEncode(state)

        return (url, Pending(verifier: verifier, state: state, profileKey: profileKey))
    }

    /// Splits the `code#state` string the callback page shows the user.
    ///
    /// A mismatched state means the code came from a different sign-in attempt. No `#`
    /// at all means the whole string is the code and the state check is skipped.
    public static func splitPastedCode(_ pasted: String) -> (code: String, returnedState: String) {
        let trimmed = pasted.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let hash = trimmed.firstIndex(of: "#") else { return (trimmed, "") }
        return (String(trimmed[trimmed.startIndex..<hash]), String(trimmed[trimmed.index(after: hash)...]))
    }

    // MARK: - PKCE primitives

    static func randomURLSafe(byteCount: Int) -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        for index in bytes.indices { bytes[index] = UInt8.random(in: 0...255) }
        return base64URL(Data(bytes))
    }

    static func challenge(for verifier: String) -> String {
        let digest = SHA256.hash(data: Data(verifier.compactMap { $0.asciiValue }))
        return base64URL(Data(digest))
    }

    static func base64URL(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    /// `application/x-www-form-urlencoded`, matching Java's `URLEncoder.encode`:
    /// spaces become `+` and `:` becomes `%3A`.
    ///
    /// A general-purpose URL escaper is the wrong tool here — it emits `%20` for spaces,
    /// which is one of the deviations that failed at grant time.
    static func formEncode(_ value: String) -> String {
        var out = ""
        for byte in Array(value.utf8) {
            switch byte {
            case 0x30...0x39, 0x41...0x5A, 0x61...0x7A,  // 0-9 A-Z a-z
                 0x2E, 0x2D, 0x2A, 0x5F:                  // . - * _
                out.append(Character(UnicodeScalar(byte)))
            case 0x20:
                out.append("+")
            default:
                out += String(format: "%%%02X", byte)
            }
        }
        return out
    }
}
