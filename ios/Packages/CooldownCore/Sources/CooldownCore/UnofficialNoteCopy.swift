import Foundation

/// CCRM-120 (Unofficial Note): the grey "Unofficial integration" box at the top of every sign-in sheet, approved
/// 2026-11-19 as placement A (`design/2026-11-19-unofficial-integration-note.html`). Pinned to
/// `spec/mobile/fixtures/unofficial-note/cases.json`, which Android's tests read too.
public enum UnofficialNoteCopy {
    public static let title = "Unofficial integration"

    /// The Add account sheet's footnote: it opens with the note's title so the words match on both screens.
    public static let addFootnote = "Unofficial integration. Cooldown reads the same usage numbers the Claude, ChatGPT and Gemini apps show. Tokens stay in this device’s Keychain. Not affiliated with Anthropic, OpenAI or Google. “Gemini” and “Antigravity” are trademarks of Google LLC."

    /// The company named in the body: Anthropic, OpenAI or Google.
    public static func company(_ provider: Provider) -> String { provider.serviceName }

    public static func body(_ provider: Provider) -> String {
        let c = company(provider)
        return "Cooldown isn't made by or affiliated with \(c). It reads your usage through an unofficial connection that \(c) may change or stop at any time. Cooldown only reads usage: it never sends prompts or uses your quota."
    }

    /// ChatGPT's sheet: Starting and Waiting only; the error stages keep their own boxes, the done page has none.
    public static func shows(on stage: DeviceCodeStage) -> Bool { stage == .starting || stage == .waiting }
}
