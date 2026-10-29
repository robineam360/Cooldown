// Ported from CooldownMac sources/ccooldown-core/ChatGPTCredentials.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// CCM-76 [Accounts]: what `APIClient.fetchChatGPTUsage` needs — access token, account id, and the
/// token's JWT-derived expiry.
public struct ChatGPTCredentials: Sendable, Equatable {
    public let accessToken: String
    public let accountId: String
    /// From the access token's JWT `exp` claim. nil when unknown — `ChatGPTCredentialStatus`
    /// then reads as `.ok` (an unknown expiry is never treated as expired), the same
    /// convention `ClaudeCodeLoginDiscovery`'s own status calculation uses.
    public let expiresAtMs: Int64?

    public init(accessToken: String, accountId: String, expiresAtMs: Int64?) {
        self.accessToken = accessToken
        self.accountId = accountId
        self.expiresAtMs = expiresAtMs
    }
}

// The Mac's `ChatGPTCredentialBuilder` (credentials from a local Codex login) stays on the desktop: a phone has
// no Codex login file. On iOS these credentials come from the device-code sign-in (`DeviceCodeSignIn`).
