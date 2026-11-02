// Ported from CooldownMac sources/ccooldown-core/AccountRegistry.swift (the Provider enum only) at 2bfbdfd (CCRM-94 (iOS App)).
import Foundation

/// Which service an account belongs to (CCM-38 [Accounts]). Only `.claude` is live; `.chatgpt`
/// arrives with CCM-76.
public enum Provider: String, Codable, Sendable {
    case claude
    case chatgpt
    /// CCRM-55 (Antigravity Account): Google's Gemini windows, read through Antigravity (API-CONTRACT §10).
    case antigravity

    /// CCM-126 [Readout]: the company whose servers answer — "Couldn't reach Anthropic/OpenAI".
    /// Two CLIs ("Claude Code"/"Codex"), two services (these); product names are for accounts
    /// and plans.
    public var serviceName: String {
        switch self {
        case .claude: "Anthropic"
        case .chatgpt: "OpenAI"
        case .antigravity: "Google"
        }
    }

    /// CCM-126 [Readout]: the service's status page, for "Check status" links.
    public var statusPageURL: String {
        switch self {
        case .claude: "https://status.anthropic.com"
        case .chatgpt: "https://status.openai.com"
        case .antigravity: "https://status.cloud.google.com"
        }
    }
}
