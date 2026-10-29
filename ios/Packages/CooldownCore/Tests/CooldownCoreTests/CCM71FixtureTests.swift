// Ported from CooldownMac tests/ccooldown-core-tests/CCM71FixtureTests.swift at 2bfbdfd (CCRM-94 (iOS App)).
import Foundation
import Testing

@testable import CooldownCore

// CCM-71 [Foundation] — one fixture set for every client.
//
// The four fixtures these tests drive (`contract/fixtures/usage/uncapped-credits.json`,
// `no-limits-array.json`, `null-keys-2026-09.json`, `resets-at-drift.jsonl`) are the canonical,
// cross-client copies of edge cases Android had already hit. `usageFixtureParses` in
// `ContractTests.swift` already walks every `usage/*.json` generically; these tests exist
// alongside it because the brief asks for one test per fixture that names the documented
// behaviour explicitly, rather than relying only on the generic table-walk to notice a
// regression.

@Suite("CCM-71 [Foundation] shared fixtures")
struct CCM71FixtureTests {

    // MARK: - uncapped-credits.json (Android CCBG-9 / CCM-44)

    @Test("uncapped-credits.json: a null spend.limit beside real spend.used stays reportable")
    func uncappedCreditsFixture() throws {
        let raw = try Fixtures.text("usage/uncapped-credits.json")
        let data = try #require(UsageParser.parse(raw), "uncapped-credits.json should parse")

        let credits = try #require(data.credits, "spend.used with no limit must still produce credits")
        #expect(credits.usedMinor == 1842)
        #expect(!credits.hasLimit, "spend.limit: null means uncapped, not absent spend")
        #expect(credits.limitMinor == nil)
        #expect(credits.percent == nil, "no limit means no percent — never a synthesised 0%")
        #expect(credits.percentDisplay == nil)
        #expect(credits.remainingMinor == nil)
        #expect(credits.isReportable, "real spend earns the row even with the cap switched off")
    }

    // MARK: - no-limits-array.json (Android CCBG-8 / CCM-45)

    @Test("no-limits-array.json: session, weekly and model caps all fall back off the flat blocks")
    func noLimitsArrayFixture() throws {
        let raw = try Fixtures.text("usage/no-limits-array.json")
        let data = try #require(UsageParser.parse(raw), "no-limits-array.json should parse")

        let session = try #require(data.session, "session must fall back to five_hour with no limits[]")
        #expect(session.percent == 8.0)
        let weekly = try #require(data.weekly, "weekly must fall back to seven_day with no limits[]")
        #expect(weekly.percent == 52.0)

        #expect(data.modelCaps.count == 2, "seven_day_sonnet and seven_day_opus fall back too")
        #expect(data.modelCaps.map(\.modelName) == ["Sonnet", "Opus"])
        #expect(data.modelCaps[0].window.percent == 61.0)
        #expect(data.modelCaps[1].window.percent == 3.0)

        #expect(data.credits == nil, "no spend or extra_usage block: no credits, not zero-valued ones")
    }

    // MARK: - null-keys-2026-09.json

    @Test("null-keys-2026-09.json: named-but-null experiment keys are ignored, not fatal")
    func nullKeysFixture() throws {
        let raw = try Fixtures.text("usage/null-keys-2026-09.json")
        let data = try #require(UsageParser.parse(raw), "a payload with unknown null keys must still parse")

        #expect(data.session?.percent == 3.0)
        #expect(data.weekly?.percent == 22.0)
        let credits = try #require(data.credits)
        #expect(credits.usedMinor == 210)
        #expect(credits.limitMinor == 5000)
        // seven_day_breakdown, seven_day_oauth_apps, nimbus_quill, and the legacy blocks'
        // always-null limit_dollars/used_dollars/remaining_dollars must not have derailed
        // parsing of the authoritative limits[]/spend blocks above.
    }

    // MARK: - resets-at-drift.jsonl (Android CCBG-4 / WindowReset.sameWindow)

    @Test("resets-at-drift.jsonl: sub-second drift binds as the same window, a full window later does not")
    func resetsAtDriftBindsSameWindow() throws {
        let text = try Fixtures.text("usage/resets-at-drift.jsonl")
        let lines = text.split(separator: "\n").map(String.init)

        var polls: [(poll: Int, resetsAtMs: Int64, windowLengthMs: Int64)] = []
        for line in lines {
            guard let data = line.data(using: .utf8),
                  let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let poll = object.int("poll")
            else { continue }  // the leading `_meta` line
            polls.append((
                poll: poll,
                resetsAtMs: try #require(object.i64("resetsAtMs"), "poll \(poll)"),
                windowLengthMs: try #require(object.i64("windowLengthMs"), "poll \(poll)")
            ))
        }
        #expect(polls.count == 7, "six drifting polls of one window, plus the genuine reset")

        // Polls 1-6: every later poll still binds to the very first reading of the window,
        // exactly as reset detection re-anchors on each poll — drift must not accumulate
        // past tolerance across a run of polls.
        let first = polls[0]
        for poll in polls[1...5] {
            #expect(
                WindowReset.sameWindow(first.resetsAtMs, poll.resetsAtMs, windowLengthMs: first.windowLengthMs),
                "poll \(poll.poll) drifted only sub-second from poll 1 — must still be the same window")
        }

        // Poll 7 is a full window (~7 days) later: the genuine reset must NOT bind to poll 6,
        // or every milestone earned in the old window would silently re-fire as new.
        let last6 = polls[5]
        let reset = polls[6]
        #expect(
            !WindowReset.sameWindow(last6.resetsAtMs, reset.resetsAtMs, windowLengthMs: last6.windowLengthMs),
            "a full window later must be a different window, not drift")
    }
}
