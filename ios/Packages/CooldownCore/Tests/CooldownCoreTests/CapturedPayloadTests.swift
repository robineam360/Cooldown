import Foundation
import Testing

@testable import CooldownCore

/// CCRM-94 (iOS App): the two payloads captured on the phone (`spec/core/fixtures/captured/`), held to the
/// same values Android's own tests assert — `UsageParserTest` and `ChatGptUsageParserTest` — so both mobile
/// clients read the same live shapes identically.

@Test("captured Claude 2026-09 payload parses despite a dozen new and codenamed keys")
func capturedClaudePayload() throws {
    let data = try #require(UsageParser.parse(try Fixtures.text("captured/claude-usage-2026-09.json")))
    let session = try #require(data.session)
    let weekly = try #require(data.weekly)
    #expect(session.percent == 4.0)
    #expect(weekly.percent == 42.0)
    #expect(session.resetsAtMs != nil)
    #expect(weekly.resetsAtMs != nil)
    // The money that is real: $123.68 of $400.00 in pay-as-you-go credits.
    let credits = try #require(data.credits)
    #expect(credits.usedMinor == 12_368)
    #expect(credits.limitMinor == 40_000)
}

@Test("captured ChatGPT 2026-09 payload reads both windows, classified by duration")
func capturedChatGPTPayload() throws {
    // Captured when the 5-hour window had 8,798 s left, so "now" is its reset minus that.
    let nowMs: Int64 = (1_788_712_966 - 8_798) * 1000
    let data = try #require(ChatGPTUsageParser.parse(try Fixtures.text("captured/chatgpt-usage-2026-09.json"), nowMs: nowMs))
    let session = try #require(data.session)
    let weekly = try #require(data.weekly)
    #expect(session.percent == 9.0)
    #expect(weekly.percent == 79.0)
    #expect(session.resetsAtMs == 1_788_712_966_000)
    #expect(weekly.resetsAtMs == 1_788_882_142_000)
}
