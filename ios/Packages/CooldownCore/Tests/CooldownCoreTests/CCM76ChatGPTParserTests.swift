// Ported from CooldownMac tests/ccooldown-core-tests/CCM76ChatGPTParserTests.swift at 2bfbdfd (CCRM-94 (iOS App)).
import Foundation
import Testing

@testable import CooldownCore

// CCM-76 [Accounts]: `ChatGPTUsageParser` against `contract/fixtures/chatgpt/`, one test per
// fixture, plus the four normalisation rules from
// Android's ChatGPT provider notes (2026-09-20, maintainer notes) and the ChatGPT request builder.

@Test("plus-both-windows.json: real captured shape parses session, weekly, no caps, no credits")
func chatGPTPlusBothWindows() throws {
    let raw = try Fixtures.text("chatgpt/plus-both-windows.json")
    let data = try #require(ChatGPTUsageParser.parse(raw, nowMs: 0))

    let session = try #require(data.session)
    #expect(session.percent == 9)
    #expect(session.resetsAtMs == 1_788_712_966_000)

    let weekly = try #require(data.weekly)
    #expect(weekly.percent == 79)
    #expect(weekly.resetsAtMs == 1_788_882_142_000)

    #expect(data.modelCaps.isEmpty)
    #expect(data.credits == nil)

    #expect(ChatGPTUsageParser.planTag(raw)?.plan == "Plus")
}

@Test("weekly-only.json: no 5-hour limit on this account — a weekly limit in primary_window, session nil")
func chatGPTWeeklyOnly() throws {
    let raw = try Fixtures.text("chatgpt/weekly-only.json")
    let data = try #require(ChatGPTUsageParser.parse(raw, nowMs: 0))

    #expect(data.session == nil)
    let weekly = try #require(data.weekly)
    #expect(weekly.percent == 42)
    #expect(weekly.resetsAtMs == 1_788_900_000_000)

    #expect(ChatGPTUsageParser.planTag(raw)?.plan == "Pro")
}

@Test("phantom-untouched-session.json: CCBG-30 — used_percent 0 and reset_after_seconds near the full window means not-started")
func chatGPTPhantomWindow() throws {
    let raw = try Fixtures.text("chatgpt/phantom-untouched-session.json")
    let data = try #require(ChatGPTUsageParser.parse(raw, nowMs: 0))

    let session = try #require(data.session)
    #expect(session.percent == 0)
    #expect(session.resetsAtMs == nil, "a phantom window must not report the reset it was handed")

    // The weekly window in the same payload is a genuine reading and must be untouched.
    let weekly = try #require(data.weekly)
    #expect(weekly.percent == 15)
    #expect(weekly.resetsAtMs == 1_788_900_000_000)
}

@Test("a window at 0% that has genuinely just started (small reset_after_seconds) is not treated as phantom")
func chatGPTZeroPercentButRunning() {
    let entry: [String: Any] = [
        "used_percent": 0, "limit_window_seconds": 18000, "reset_after_seconds": 100, "reset_at": 1_788_712_966
    ]
    let root: [String: Any] = ["rate_limit": ["primary_window": entry]]
    let data = ChatGPTUsageParser.parse(root: JSONForTests(root), nowMs: 0)
    #expect(data.session?.percent == 0)
    #expect(data.session?.resetsAtMs == 1_788_712_966_000, "reset_after_seconds far from the window length is a real reading, not a phantom")
}

@Test("credits-balance.json: has_credits true, a plain balance — reportable, no used/limit/percent")
func chatGPTCreditsBalance() throws {
    let raw = try Fixtures.text("chatgpt/credits-balance.json")
    let data = try #require(ChatGPTUsageParser.parse(raw, nowMs: 0))
    let credits = try #require(data.credits)

    #expect(credits.balanceMinor == 1240)
    #expect(credits.unlimited == false)
    #expect(credits.isReportable == true)
    #expect(credits.limitMinor == nil)
    #expect(credits.percent == nil)
}

@Test("credits-unlimited.json: unlimited flag makes an existing balance not worth reporting")
func chatGPTCreditsUnlimited() throws {
    let raw = try Fixtures.text("chatgpt/credits-unlimited.json")
    let data = try #require(ChatGPTUsageParser.parse(raw, nowMs: 0))
    let credits = try #require(data.credits)

    #expect(credits.unlimited == true)
    #expect(credits.isReportable == false, "unlimited must suppress rendering even though the object exists")
}

@Test("credits has_credits == false parses to no credits at all")
func chatGPTCreditsFalse() throws {
    let raw = try Fixtures.text("chatgpt/plus-both-windows.json")
    let data = try #require(ChatGPTUsageParser.parse(raw, nowMs: 0))
    #expect(data.credits == nil)
}

@Test("model-caps.json: additional_rate_limits produces one ModelCap, classified by length not slot")
func chatGPTModelCaps() throws {
    let raw = try Fixtures.text("chatgpt/model-caps.json")
    let data = try #require(ChatGPTUsageParser.parse(raw, nowMs: 0))

    #expect(data.session?.percent == 12)
    #expect(data.weekly?.percent == 33)

    #expect(data.modelCaps.count == 1)
    let cap = try #require(data.modelCaps.first)
    #expect(cap.modelName == "gpt-6-thinking")
    #expect(cap.window.percent == 55)
    #expect(cap.window.resetsAtMs == 1_788_950_000_000)
}

@Test("no-rate-limit.json: a missing rate_limit key is tolerant, not an error — an empty, non-nil UsageData")
func chatGPTNoRateLimit() throws {
    let raw = try Fixtures.text("chatgpt/no-rate-limit.json")
    let data = try #require(ChatGPTUsageParser.parse(raw, nowMs: 0))
    #expect(data.session == nil)
    #expect(data.weekly == nil)
    #expect(data.modelCaps.isEmpty)
    #expect(data.credits == nil)
}

@Test("malformed.json: not valid JSON at all returns nil, never a guessed empty UsageData")
func chatGPTMalformed() throws {
    let raw = try Fixtures.text("chatgpt/malformed.json")
    #expect(ChatGPTUsageParser.parse(raw, nowMs: 0) == nil)
}

@Test("reset_after_seconds is used when reset_at is absent")
func chatGPTResetAfterFallback() {
    let entry: [String: Any] = ["used_percent": 30, "limit_window_seconds": 18000, "reset_after_seconds": 500]
    let root: [String: Any] = ["rate_limit": ["primary_window": entry]]
    let data = ChatGPTUsageParser.parse(root: JSONForTests(root), nowMs: 1_000_000)
    // Compared via a named Int64 rather than an inline `a + b` literal expression — the
    // Testing macro's inline arithmetic mis-diffed here despite both sides being equal.
    let expected: Int64 = 1_500_000
    #expect(data.session?.resetsAtMs == expected)
}

@Test("phantom boundary: reset_after_seconds == limit_window_seconds - 1 fires, one second less does not")
func chatGPTPhantomBoundary() {
    func session(resetAfterSeconds: Int) -> UsageWindow? {
        let entry: [String: Any] = [
            "used_percent": 0, "limit_window_seconds": 18000, "reset_after_seconds": resetAfterSeconds,
            "reset_at": 1_788_712_966
        ]
        let root: [String: Any] = ["rate_limit": ["primary_window": entry]]
        return ChatGPTUsageParser.parse(root: JSONForTests(root), nowMs: 0).session
    }

    #expect(session(resetAfterSeconds: 17999)?.resetsAtMs == nil, "17999 == limit_window_seconds - 1: fires")
    #expect(
        session(resetAfterSeconds: 17998)?.resetsAtMs == 1_788_712_966_000,
        "17998 is one second short of the boundary: a real reading, not a phantom")
}

@Test("windows classify by limit_window_seconds even when reversed onto the other slot")
func chatGPTWindowsReversedSlots() {
    let root: [String: Any] = [
        "rate_limit": [
            "primary_window": [
                "used_percent": 60, "limit_window_seconds": 604800, "reset_after_seconds": 300_000,
                "reset_at": 1_788_900_000
            ],
            "secondary_window": [
                "used_percent": 8, "limit_window_seconds": 18000, "reset_after_seconds": 9000,
                "reset_at": 1_788_712_966
            ]
        ]
    ]
    let data = ChatGPTUsageParser.parse(root: JSONForTests(root), nowMs: 0)
    #expect(data.session?.percent == 8, "the 18000-second window is the session, wherever it sits")
    #expect(data.weekly?.percent == 60, "the 604800-second window is weekly, wherever it sits")
}

@Test("an unrecognised limit_window_seconds is dropped, never guessed at")
func chatGPTUnrecognisedWindowLength() {
    let root: [String: Any] = [
        "rate_limit": [
            "primary_window": [
                "used_percent": 50, "limit_window_seconds": 86400, "reset_after_seconds": 1000,
                "reset_at": 1_788_712_966
            ]
        ]
    ]
    let data = ChatGPTUsageParser.parse(root: JSONForTests(root), nowMs: 0)
    #expect(data.session == nil)
    #expect(data.weekly == nil)
}

@Test("redactedBodyForStorage strips email, user_id and account_id, keeps everything else")
func chatGPTRedactedBodyForStorage() throws {
    let raw = try Fixtures.text("chatgpt/plus-both-windows.json")
    let redacted = ChatGPTUsageParser.redactedBodyForStorage(raw)
    for key in ["email", "user_id", "account_id"] {
        #expect(!redacted.contains("\"\(key)\""), "\(key) must not survive redaction")
    }
    #expect(redacted.contains("\"plan_type\""), "unrelated fields must survive redaction")
    #expect(redacted.contains("plus"))
}

@Test("redactedBodyForStorage falls back to the raw string for input that isn't a JSON object")
func chatGPTRedactedBodyForStorageMalformed() {
    #expect(ChatGPTUsageParser.redactedBodyForStorage("not json") == "not json")
}

@Test("UsageData.isEmpty is true only when session, weekly, modelCaps and credits are all absent")
func chatGPTUsageDataIsEmpty() throws {
    let empty = UsageData(session: nil, weekly: nil, modelCaps: [], credits: nil)
    #expect(empty.isEmpty)

    let raw = try Fixtures.text("chatgpt/no-rate-limit.json")
    let data = try #require(ChatGPTUsageParser.parse(raw, nowMs: 0))
    #expect(data.isEmpty)

    let plusBoth = try #require(
        ChatGPTUsageParser.parse(try Fixtures.text("chatgpt/plus-both-windows.json"), nowMs: 0))
    #expect(!plusBoth.isEmpty)
}

@Test("plan_type maps onto PlanTag's existing labels")
func chatGPTPlanTagMapping() {
    #expect(PlanTag.fromChatGPT(planType: "plus")?.plan == "Plus")
    #expect(PlanTag.fromChatGPT(planType: "pro")?.plan == "Pro")
    #expect(PlanTag.fromChatGPT(planType: "team")?.plan == "Team")
    #expect(PlanTag.fromChatGPT(planType: "business")?.plan == "Team")
    #expect(PlanTag.fromChatGPT(planType: "free")?.plan == "Free")
    #expect(PlanTag.fromChatGPT(planType: "enterprise")?.plan == "Enterprise")
    #expect(PlanTag.fromChatGPT(planType: nil) == nil)
    #expect(PlanTag.fromChatGPT(planType: "") == nil)
}

// MARK: - Request builder

@Test("the ChatGPT usage request carries the right URL, auth, accept and user-agent headers")
func chatGPTRequestShape() {
    let request = APIClient.chatGPTUsageRequest(accessToken: "tok-123", accountId: "acct-abc")
    #expect(request.url?.absoluteString == "https://chatgpt.com/backend-api/wham/usage")
    #expect(request.httpMethod == "GET")
    #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer tok-123")
    #expect(request.value(forHTTPHeaderField: "Accept") == "application/json")
    #expect(request.value(forHTTPHeaderField: "User-Agent") == APIClient.userAgent)
    #expect(request.value(forHTTPHeaderField: "ChatGPT-Account-Id") == "acct-abc")
    #expect(
        request.httpShouldHandleCookies == false,
        "chatgpt.com is a browser domain — never attach or persist cookies for this request")
    #expect(
        request.cachePolicy == .reloadIgnoringLocalCacheData,
        "CCM-104 [Readout]'s one-directional merge rule treats fetchedAtMs as this response's real receipt time — it must never be a cached response served without a round trip")
}

@Test("the account-id header is absent, not empty, when the account id is empty")
func chatGPTRequestNoAccountId() {
    let request = APIClient.chatGPTUsageRequest(accessToken: "tok-123", accountId: "")
    #expect(request.value(forHTTPHeaderField: "ChatGPT-Account-Id") == nil)
}

// MARK: - Fetch outcome classification

@Test("401 and 403 both classify as authNeeded; 429 as rateLimited; anything else as server(code:)")
func chatGPTFetchOutcomeClassification() {
    #expect(ChatGPTFetchOutcome.classify(code: 401) == .authNeeded)
    #expect(ChatGPTFetchOutcome.classify(code: 403) == .authNeeded)
    #expect(ChatGPTFetchOutcome.classify(code: 429) == .rateLimited)
    #expect(ChatGPTFetchOutcome.classify(code: 500) == .server(code: 500))
    #expect(ChatGPTFetchOutcome.classify(code: 200) == nil)
}

// MARK: - Codex login -> ChatGPT credentials seam
// Stays on the desktop: a phone has no local Codex login (spec core §9.6).

// MARK: - Test helper

/// A tiny shim so tests can hand `ChatGPTUsageParser.parse(root:nowMs:)` a raw dictionary
/// without round-tripping it through JSON text — `JSON` itself is `internal`, so tests reach
/// it through the same-module `@testable import`.
private func JSONForTests(_ raw: [String: Any]) -> JSON { JSON(raw) }
