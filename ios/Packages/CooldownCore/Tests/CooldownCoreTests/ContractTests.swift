// Ported from CooldownMac tests/ccooldown-core-tests/ContractTests.swift at 2bfbdfd (CCRM-94 (iOS App)).
import Foundation
import Testing

@testable import CooldownCore

// The whole point of these tests: the Swift core is verified against the *same*
// contract fixtures the Android client's behaviour was extracted from. When the two
// clients disagree, a fixture here is what proves which one is wrong.

// MARK: - Usage parser, driven entirely by spec/core/fixtures/usage

@Test("every usage fixture has a matching expectation file")
func usageFixturesArePaired() throws {
    let names = try Fixtures.usageCaseNames()
    #expect(names.count >= 5, "expected the contract's usage fixtures, found \(names)")
}

@Test("usage fixtures parse to their expected values", arguments: try Fixtures.usageCaseNames())
func usageFixtureParses(name: String) throws {
    let raw = try Fixtures.text("usage/\(name).json")
    let expected = try Fixtures.json("usage/\(name).expected.json")

    guard expected.flag("parsed") == true else {
        #expect(UsageParser.parse(raw) == nil, "\(name) must be unusable")
        return
    }

    let data = try #require(UsageParser.parse(raw), "\(name) should have parsed")

    assertWindow(data.session, expected.dict("session"), label: "\(name).session")
    assertWindow(data.weekly, expected.dict("weekly"), label: "\(name).weekly")

    let expectedCaps = expected.list("modelCaps") as? [[String: Any]] ?? []
    #expect(data.modelCaps.count == expectedCaps.count, "\(name).modelCaps count")
    for (cap, expectedCap) in zip(data.modelCaps, expectedCaps) {
        #expect(cap.modelName == expectedCap.str("modelName"), "\(name) cap name")
        assertWindow(cap.window, expectedCap, label: "\(name).cap[\(cap.modelName)]")
    }

    assertCredits(data.credits, expected.dict("credits"), label: "\(name).credits")
}

private func assertWindow(_ actual: UsageWindow?, _ expected: [String: Any]?, label: String) {
    guard let expected else {
        #expect(actual == nil, "\(label) should be absent")
        return
    }
    guard let actual else {
        Issue.record("\(label) should be present")
        return
    }
    if expected.has("percent") {
        #expect(actual.percent == expected.num("percent"), "\(label).percent")
    } else {
        #expect(actual.percent == nil, "\(label).percent should be nil, not 0")
    }
    #expect(actual.resetsAtMs == expected.i64("resetsAtMs"), "\(label).resetsAtMs")
    #expect(actual.serverSeverity == expected.str("serverSeverity"), "\(label).serverSeverity")
    #expect(actual.severity.rawValue == expected.str("severity"), "\(label).severity")
}

private func assertCredits(_ actual: SpendCredits?, _ expected: [String: Any]?, label: String) {
    guard let expected else {
        #expect(actual == nil, "\(label) should be absent")
        return
    }
    guard let actual else {
        Issue.record("\(label) should be present")
        return
    }
    #expect(actual.usedMinor == expected.i64("usedMinor"), "\(label).usedMinor")
    #expect(actual.exponent == expected.int("exponent"), "\(label).exponent")
    #expect(actual.currency == expected.str("currency"), "\(label).currency")
    #expect(actual.serverSeverity == expected.str("serverSeverity"), "\(label).serverSeverity")
    #expect(actual.isReportable == expected.flag("isReportable"), "\(label).isReportable")

    // An absent key in the expected file asserts nil — uncapped accounts (CCM-44) carry
    // no limit, no percent and no remaining, and must never synthesise a zero.
    if expected.has("limitMinor") {
        #expect(actual.limitMinor == expected.i64("limitMinor"), "\(label).limitMinor")
    } else {
        #expect(actual.limitMinor == nil, "\(label).limitMinor should be nil (uncapped)")
    }
    if expected.has("percent") {
        #expect(abs((actual.percent ?? -1) - (expected.num("percent") ?? -2)) < 0.0001, "\(label).percent")
        #expect(actual.percentDisplay == expected.int("percentDisplay"), "\(label).percentDisplay")
    } else {
        #expect(actual.percent == nil, "\(label).percent should be nil, not 0")
        #expect(actual.percentDisplay == nil, "\(label).percentDisplay should be nil")
    }
    if expected.has("remainingMinor") {
        #expect(actual.remainingMinor == expected.i64("remainingMinor"), "\(label).remainingMinor")
    } else {
        #expect(actual.remainingMinor == nil, "\(label).remainingMinor should be nil")
    }

    // The rendered money strings are part of the contract too.
    let money = { (minor: Int64) in
        Fmt.money(minorUnits: minor, exponent: actual.exponent, currencyCode: actual.currency)
    }
    #expect(money(actual.usedMinor) == expected.str("moneyUsed"), "\(label).moneyUsed")
    if let limit = actual.limitMinor {
        #expect(money(limit) == expected.str("moneyLimit"), "\(label).moneyLimit")
    }
    if let remaining = actual.remainingMinor {
        #expect(money(remaining) == expected.str("moneyRemaining"), "\(label).moneyRemaining")
    }
}

@Test("unusable payloads all return nothing")
func unusableCases() throws {
    let cases = try #require(try Fixtures.json("usage/unusable-cases.json").list("cases") as? [[String: Any]])
    #expect(cases.count >= 5)
    for entry in cases {
        let raw = try #require(entry.str("raw"))
        let name = entry.str("name") ?? "?"
        #expect(UsageParser.parse(raw) == nil, "\(name): \(entry.str("why") ?? "")")
    }
}

// MARK: - Projection

@Test("projection estimate cases")
func projectionEstimates() throws {
    let cases = try #require(try Fixtures.json("projection/cases.json").list("estimate") as? [[String: Any]])

    for entry in cases {
        let name = entry.str("name") ?? "?"
        let samples = (entry.list("samples") as? [[Any]] ?? []).map {
            (($0[0] as? NSNumber)?.int64Value ?? 0, ($0[1] as? NSNumber)?.doubleValue ?? 0)
        }
        let resetAt = try #require(entry.i64("resetAtMs"), "\(name)")
        let actual = Projection.estimate(samples: samples, resetAtMs: resetAt)

        if entry.has("expected"), let expected = entry.dict("expected") {
            let estimate = try #require(actual, "\(name) should produce an estimate")
            #expect(abs(estimate.ratePctPerHour - (expected.num("ratePctPerHour") ?? estimate.ratePctPerHour)) < 0.01,
                    "\(name) rate")
            if expected.has("pctAtReset") {
                #expect(abs(estimate.pctAtReset - expected.num("pctAtReset")!) < 0.01, "\(name) pctAtReset")
            }
            if expected.keys.contains("hitsLimitAtMs") {
                #expect(estimate.hitsLimitAtMs == expected.i64("hitsLimitAtMs"), "\(name) hitsLimitAtMs")
            }
        } else if let constraint = entry.dict("expectedConstraint") {
            let estimate = try #require(actual, "\(name) should produce an estimate")
            let ceiling = try #require(constraint.num("ratePctPerHourLessThan"))
            #expect(estimate.ratePctPerHour < ceiling,
                    "\(name): fit \(estimate.ratePctPerHour) should undercut \(ceiling)")
        } else {
            // "expected": null — refusing to project is the required outcome.
            #expect(actual == nil, "\(name) must refuse to project: \(entry.str("why") ?? "")")
        }
    }
}

@Test("pace severity cases")
func projectionSeverity() throws {
    let cases = try #require(try Fixtures.json("projection/cases.json").list("severity") as? [[String: Any]])
    #expect(cases.count >= 10)

    let names: [String: Projection.PaceSeverity] = [
        "untracked": .untracked, "healthy": .healthy, "close": .close,
        "runningOut": .runningOut, "spent": .spent
    ]
    let reasons: [String: Projection.PaceSuppression] = [
        "youngWindow": .youngWindow, "tooLittleUsage": .tooLittleUsage,
        "noProjection": .noProjection
    ]

    for entry in cases {
        let name = entry.str("name") ?? "?"
        let estimate = entry.dict("estimate").map {
            Projection.Estimate(
                ratePctPerHour: 1,
                hitsLimitAtMs: $0.i64("hitsLimitAtMs"),
                pctAtReset: $0.num("pctAtReset") ?? 0)
        }
        let actual = Projection.outlook(
            usedPct: try #require(entry.num("usedPct"), "\(name)"),
            elapsedMs: try #require(entry.i64("elapsedMs"), "\(name)"),
            windowLengthMs: try #require(entry.i64("windowLengthMs"), "\(name)"),
            estimate: estimate)

        let expected = try #require(entry.dict("expected"), "\(name)")
        #expect(actual.severity == names[expected.str("severity") ?? ""],
                "\(name): \(entry.str("why") ?? "")")
        #expect(actual.suppression == expected.str("suppression").flatMap { reasons[$0] },
                "\(name) suppression")
        // The invariant the struct promises, asserted against every case rather than
        // trusted: a reason exactly when there is no verdict.
        #expect((actual.suppression != nil) == (actual.severity == .untracked), "\(name) invariant")
    }
}

// Pace-alert cases (`alerts/pace-cases.json`) are a desktop feature and live in the desktop layer, not
// spec/core (CCRM-92 (Shared Spec)); mobile has reset pings only (spec/mobile/BEHAVIOR-MOBILE.md §2).

@Test("window binding cases")
func projectionBinding() throws {
    let cases = try #require(try Fixtures.json("projection/cases.json").list("binding") as? [[String: Any]])

    for entry in cases {
        let name = entry.str("name") ?? "?"

        if let expected = entry.dict("expected"), entry["history"] == nil {
            #expect(Projection.tolerance(windowLengthMs: HistoryStats.sessionMs) == expected.i64("sessionToleranceMs"),
                    "\(name) session tolerance")
            #expect(Projection.tolerance(windowLengthMs: HistoryStats.weekMs) == expected.i64("weeklyToleranceMs"),
                    "\(name) weekly tolerance")
            continue
        }

        let history = (entry.list("history") as? [[String: Any]] ?? []).map {
            HistoryPoint(
                atMs: $0.i64("atMs") ?? 0,
                sessionPct: $0.has("sessionPct") ? $0.num("sessionPct") : nil,
                sessionResetAtMs: $0.i64("sessionResetAtMs") ?? 0,
                weeklyPct: $0.has("weeklyPct") ? $0.num("weeklyPct") : nil,
                weeklyResetAtMs: $0.i64("weeklyResetAtMs") ?? 0
            )
        }
        let resetAt = try #require(entry.i64("windowResetAtMs"), "\(name)")
        let length = try #require(entry.i64("windowLengthMs"), "\(name)")
        let isWeekly = entry.str("kind") == "weekly"

        let actual = isWeekly
            ? Projection.weeklySamples(history: history, resetAtMs: resetAt, windowLengthMs: length)
            : Projection.sessionSamples(history: history, resetAtMs: resetAt, windowLengthMs: length)

        let expectedPairs = (entry.list("expected") as? [[Any]] ?? []).map {
            (($0[0] as? NSNumber)?.int64Value ?? 0, ($0[1] as? NSNumber)?.doubleValue ?? 0)
        }
        #expect(actual.count == expectedPairs.count, "\(name) count — \(entry.str("why") ?? "")")
        for (got, want) in zip(actual, expectedPairs) {
            #expect(got.0 == want.0 && abs(got.1 - want.1) < 0.0001, "\(name) sample")
        }
    }
}

// MARK: - History

@Test("history bar cases")
func historyBars() throws {
    let cases = try #require(try Fixtures.json("history/cases.json").list("bars") as? [[String: Any]])

    for entry in cases {
        let name = entry.str("name") ?? "?"
        let kind = entry.str("kind") ?? HistoryStats.sessionKind
        let records = (entry.list("records") as? [[String: Any]] ?? []).map {
            HistoryStats.Record(
                kind: $0.str("kind") ?? "",
                resetAtMs: $0.i64("resetAtMs") ?? 0,
                peakPct: $0.num("peakPct") ?? 0,
                hitLimit: $0.flag("hitLimit") ?? false
            )
        }
        let current = entry.dict("current").map {
            HistoryStats.Bar(
                startMs: $0.i64("startMs") ?? 0,
                resetMs: $0.i64("resetMs") ?? 0,
                peakPct: $0.num("peakPct") ?? 0,
                hitLimit: $0.flag("hitLimit") ?? false,
                current: true
            )
        }

        let bars = HistoryStats.bars(records: records, kind: kind, current: current)

        if let count = entry.int("expectedCount") {
            #expect(bars.count == count, "\(name) — \(entry.str("why") ?? "")")
        }

        if let byReset = entry.dict("expectedHitLimitByResetMs") {
            for (resetKey, want) in byReset {
                let bar = bars.first { String($0.resetMs) == resetKey }
                #expect(bar?.hitLimit == (want as? NSNumber)?.boolValue, "\(name) hitLimit at \(resetKey)")
            }
            // Derived, not read from the record — pins the 99.5 threshold itself.
            for bar in bars {
                #expect(bar.hitLimit == (bar.peakPct >= HistoryStats.hitLimitThreshold),
                        "\(name): hitLimit must follow the 99.5 rule at \(bar.peakPct)")
            }
        }

        if let expected = entry.list("expected") as? [[String: Any]] {
            #expect(bars.count == expected.count, "\(name) count")
            for (bar, want) in zip(bars, expected) {
                #expect(bar.startMs == want.i64("startMs"), "\(name) startMs")
                #expect(bar.resetMs == want.i64("resetMs"), "\(name) resetMs")
                #expect(bar.peakPct == want.num("peakPct"), "\(name) peakPct")
                #expect(bar.hitLimit == want.flag("hitLimit"), "\(name) hitLimit")
                #expect(bar.current == want.flag("current"), "\(name) current")
            }
        }
    }
}

@Test("week grouping cases")
func historyWeeks() throws {
    let cases = try #require(try Fixtures.json("history/cases.json").list("weeks") as? [[String: Any]])

    for entry in cases {
        let name = entry.str("name") ?? "?"
        let zone = try #require(TimeZone(identifier: entry.str("zone") ?? "UTC"), "\(name) zone")
        let bars = (entry.list("bars") as? [[String: Any]] ?? []).map {
            HistoryStats.Bar(
                startMs: $0.i64("startMs") ?? 0, resetMs: 0,
                peakPct: $0.num("peakPct") ?? 0, hitLimit: false, current: false
            )
        }
        let weeks = HistoryStats.weeks(bars: bars, timeZone: zone)
        let expected = try #require(entry.list("expected") as? [[String: Any]])

        #expect(weeks.count == expected.count, "\(name) week count — \(entry.str("why") ?? "")")
        for (week, want) in zip(weeks, expected) {
            #expect(week.monday == want.str("monday"), "\(name) monday")
            #expect(week.bars.count == want.int("barCount"), "\(name) bar count")
        }
    }
}

@Test("retention windows match the contract")
func historyRetention() throws {
    let retention = try #require(try Fixtures.json("history/cases.json").dict("retention"))
    let rawSamples = try #require(retention.i64("rawSamplesMaxAgeMs"))
    let closedWindows = try #require(retention.i64("closedWindowsMaxAgeMs"))
    // Unwrapped and explicitly Int64 on both sides: comparing an `Int64?` against an
    // `Int` literal here reports a spurious failure with both sides printing equal.
    #expect(rawSamples == Int64(8) * 24 * 60 * 60_000)
    #expect(closedWindows == Int64(366) * 24 * 60 * 60_000)
}

// MARK: - Timestamps

@Test("microsecond timestamps truncate to milliseconds")
func timestampTruncation() {
    #expect(Timestamps.parseInstantMs("2026-07-27T11:49:59.956769+00:00") == 1_785_152_999_956)
    #expect(Timestamps.parseInstantMs("2026-07-30T18:59:59.956800+00:00") == 1_785_437_999_956)
    // No fraction, and a Z offset, must both work.
    #expect(Timestamps.parseInstantMs("2026-07-27T11:49:59+00:00") == 1_785_152_999_000)
    #expect(Timestamps.parseInstantMs("2026-07-27T11:49:59Z") == 1_785_152_999_000)
    // Truncation, never rounding: .999999 must not carry into the next second.
    #expect(Timestamps.parseInstantMs("2026-07-27T11:49:59.999999+00:00") == 1_785_152_999_999)
    #expect(Timestamps.parseInstantMs(nil) == nil)
    #expect(Timestamps.parseInstantMs("") == nil)
    #expect(Timestamps.parseInstantMs("garbage") == nil)
}

// MARK: - Severity, independent of the parser

@Test("severity ORs the server value with the percent rule")
func severityDerivation() {
    func severity(_ pct: Double?, _ server: String?) -> Severity {
        UsageWindow(percent: pct, resetsAtMs: nil, serverSeverity: server).severity
    }
    // Boundaries: > 90 is strict, >= 70 inclusive.
    #expect(severity(69.9, nil) == .normal)
    #expect(severity(70, nil) == .warning)
    #expect(severity(90, nil) == .warning)
    #expect(severity(90.1, nil) == .critical)
    // The server can escalate a low percent...
    #expect(severity(5, "warning") == .warning)
    #expect(severity(5, "critical") == .critical)
    // ...but a benign server value never de-escalates a high percent.
    #expect(severity(95, "normal") == .critical)
    #expect(severity(72, "normal") == .warning)
    // A null percent is 0, not an error.
    #expect(severity(nil, nil) == .normal)
}

// MARK: - Money

@Test("money renders the way Claude does")
func moneyFormatting() {
    #expect(Fmt.money(minorUnits: 599, exponent: 2, currencyCode: "USD") == "$5.99")
    #expect(Fmt.money(minorUnits: 10000, exponent: 2, currencyCode: "USD") == "$100.00")
    #expect(Fmt.money(minorUnits: 9401, exponent: 2, currencyCode: "USD") == "$94.01")
    #expect(Fmt.money(minorUnits: 957, exponent: 2, currencyCode: "USD") == "$9.57")
    // Grouping, and an exponent of 0.
    #expect(Fmt.money(minorUnits: 123456789, exponent: 2, currencyCode: "USD") == "$1,234,567.89")
    #expect(Fmt.money(minorUnits: 1500, exponent: 0, currencyCode: "USD") == "$1,500")
}

@Test("window percentages truncate where credits round")
func truncationVsRounding() {
    #expect(Fmt.windowPercentDisplay(99.7) == 99)
    #expect(Fmt.windowPercentDisplay(5.99) == 5)
    let credits = SpendCredits(usedMinor: 599, limitMinor: 10000, exponent: 2, currency: "USD", serverSeverity: nil)
    // Explicit Int(...) per the Swift Testing optional-vs-literal quirk (the maintainer handover).
    #expect(credits.percentDisplay == Int(6))
}

@Test("credits clamp remaining but not percent")
func creditsOverspend() {
    let over = SpendCredits(usedMinor: 12000, limitMinor: 10000, exponent: 2, currency: "USD", serverSeverity: nil)
    #expect(over.remainingMinor == Int64(0))
    #expect(abs((over.percent ?? 0) - 120.0) < 0.0001)
    #expect(over.isReportable)
}

@Test("uncapped credits keep the spend and refuse to invent a percent")
func creditsUncapped() {
    // The cap switched off (CCM-44): real spend, null limit. The row is still reportable,
    // but nothing pretends there's a ceiling — no percent, no remaining, no synthesised 0%.
    let uncapped = SpendCredits(usedMinor: 297, limitMinor: nil, exponent: 2, currency: "USD", serverSeverity: nil)
    #expect(uncapped.isReportable)
    #expect(!uncapped.hasLimit)
    #expect(uncapped.percent == nil)
    #expect(uncapped.percentDisplay == nil)
    #expect(uncapped.remainingMinor == nil)

    // No cap and no spend: nothing to report.
    let nothing = SpendCredits(usedMinor: 0, limitMinor: nil, exponent: 2, currency: "USD", serverSeverity: nil)
    #expect(!nothing.isReportable)

    // A zero cap is a cap, not "uncapped" — but with no spend it still isn't reportable.
    let zero = SpendCredits(usedMinor: 0, limitMinor: 0, exponent: 2, currency: "USD", serverSeverity: nil)
    #expect(!zero.isReportable)
    #expect(zero.percent == nil, "a zero limit must not divide")
}

// MARK: - Even-pace line

@Test("even-pace elapsed percent")
func evenPace() {
    let length = HistoryStats.sessionMs
    let reset: Int64 = 1_000_000_000
    #expect(Fmt.elapsedPercent(resetsAtMs: reset, windowLengthMs: length, nowMs: reset - length) == 0)
    #expect(Fmt.elapsedPercent(resetsAtMs: reset, windowLengthMs: length, nowMs: reset) == 100)
    #expect(Fmt.elapsedPercent(resetsAtMs: reset, windowLengthMs: length, nowMs: reset - length / 2) == 50)
    // Clamped at both ends, never negative or over 100.
    #expect(Fmt.elapsedPercent(resetsAtMs: reset, windowLengthMs: length, nowMs: reset - length * 2) == 0)
    #expect(Fmt.elapsedPercent(resetsAtMs: reset, windowLengthMs: length, nowMs: reset + length) == 100)
    #expect(Fmt.elapsedPercent(resetsAtMs: nil, windowLengthMs: length, nowMs: reset) == nil)
}

// MARK: - OAuth request shape

@Test("form encoding matches Java URLEncoder, not percent-encoding")
func formEncoding() {
    // The two rules that broke the grant when done differently.
    #expect(OAuthSignIn.formEncode("a b") == "a+b")
    #expect(OAuthSignIn.formEncode("user:profile") == "user%3Aprofile")
    #expect(OAuthSignIn.formEncode("https://platform.claude.com/oauth/code/callback")
            == "https%3A%2F%2Fplatform.claude.com%2Foauth%2Fcode%2Fcallback")
    // Unreserved set is kept verbatim.
    #expect(OAuthSignIn.formEncode("aZ09.-*_") == "aZ09.-*_")
}

@Test("authorize URL has the exact parameter order and encoding")
func authorizeURLShape() {
    let (url, pending) = OAuthSignIn.begin(profileKey: "personal")

    #expect(url.hasPrefix("https://claude.com/cai/oauth/authorize?code=true&client_id="))
    // Parameter order is part of the contract.
    let expectedOrder = ["code=true", "&client_id=", "&response_type=code", "&redirect_uri=",
                         "&scope=", "&code_challenge=", "&code_challenge_method=S256", "&state="]
    var cursor = url.startIndex
    for token in expectedOrder {
        let found = url.range(of: token, range: cursor..<url.endIndex)
        #expect(found != nil, "missing or out-of-order: \(token)")
        cursor = found?.upperBound ?? cursor
    }
    // Scope is form-encoded: spaces as '+', colons as %3A, and never %20.
    #expect(url.contains("&scope=org%3Acreate_api_key+user%3Aprofile+user%3Ainference"))
    #expect(!url.contains("%20"))
    // state IS the verifier.
    #expect(pending.state == pending.verifier)
    #expect(url.contains("&state=" + OAuthSignIn.formEncode(pending.verifier)))
    // The challenge is base64url of SHA-256, so no padding and no '+' or '/'.
    let challenge = OAuthSignIn.challenge(for: pending.verifier)
    #expect(!challenge.contains("=") && !challenge.contains("+") && !challenge.contains("/"))
    #expect(challenge.count == 43)
}

@Test("pasted code splits on the first hash")
func pastedCodeSplit() {
    let (code, state) = OAuthSignIn.splitPastedCode("  abc123#xyz789  ")
    #expect(code == "abc123")
    #expect(state == "xyz789")
    // No hash: the whole string is the code and the state check is skipped.
    #expect(OAuthSignIn.splitPastedCode("abc123") == ("abc123", ""))
}

@Test("the token endpoint never sees a claude-code, empty, browser-like or curl User-Agent")
func tokenUserAgentRule() {
    // The token endpoint must never see a claude-code UA, and never an empty one.
    #expect(!APIClient.tokenUserAgent.contains("claude-code"))
    #expect(!APIClient.tokenUserAgent.isEmpty)
    #expect(!APIClient.tokenUserAgent.lowercased().contains("mozilla"))
    #expect(!APIClient.tokenUserAgent.lowercased().contains("curl"))
}

@Test("the usage endpoint gets our own honest User-Agent, not claude-code's")
func usageUserAgentIsHonest() {
    // CCM-72 [Foundation]: verified 2026-09-20 (ua-probe) that the usage endpoint does not
    // gate on User-Agent, so the claude-code spoof is retired for anything the app sends.
    #expect(APIClient.userAgent.hasPrefix("Cooldown/"))
    #expect(APIClient.userAgent.hasSuffix("(\(APIClient.platform))"))  // "(iOS)" on iPhone/iPad, "(macOS)" in `swift test`
    #expect(!APIClient.userAgent.contains("claude-code"))
    // The old value is kept only as ua-probe's control arm — still shaped like a real
    // claude-code UA so the probe's comparison stays meaningful, but no longer sent by us.
    #expect(APIClient.claudeCodeUserAgent.hasPrefix("claude-code/"))
}

@Test("the usage request actually carries the honest User-Agent header, not claude-code's")
func usageRequestSendsHonestUserAgent() async throws {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [UserAgentCapturingURLProtocol.self]
    let client = APIClient(session: URLSession(configuration: configuration))

    _ = try await client.fetchUsage(accessToken: "test-token")

    let sentUA = capturedRequestBox.get()?.value(forHTTPHeaderField: "User-Agent")
    #expect(sentUA == APIClient.userAgent)
    #expect(sentUA?.contains("claude-code") == false)
}

/// Thread-safe box for the one request `UserAgentCapturingURLProtocol` records — a plain
/// `static var` on the protocol itself isn't concurrency-safe under Swift 6, and URLProtocol's
/// own storage is instance-per-request, not shared with the test that wants to read it back.
private final class RequestBox: @unchecked Sendable {
    private let lock = NSLock()
    private var request: URLRequest?

    func set(_ request: URLRequest) {
        lock.lock(); defer { lock.unlock() }
        self.request = request
    }

    func get() -> URLRequest? {
        lock.lock(); defer { lock.unlock() }
        return request
    }
}

private let capturedRequestBox = RequestBox()

/// Captures the last request handed to `URLSession` without hitting the network — used to
/// pin the exact header values `APIClient` sends, not just the constants it exposes.
private final class UserAgentCapturingURLProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        capturedRequestBox.set(request)
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data("{}".utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

@Test("429 backoff doubles and caps at an hour")
func backoffSchedule() {
    #expect(RateLimits.backoffMs(consecutive429: 0) == 5 * 60_000)
    #expect(RateLimits.backoffMs(consecutive429: 1) == 10 * 60_000)
    #expect(RateLimits.backoffMs(consecutive429: 2) == 20 * 60_000)
    #expect(RateLimits.backoffMs(consecutive429: 3) == 40 * 60_000)
    #expect(RateLimits.backoffMs(consecutive429: 4) == 60 * 60_000)
    #expect(RateLimits.backoffMs(consecutive429: 99) == 60 * 60_000)
}
