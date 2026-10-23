import CooldownCore
import Foundation
import Testing

@testable import Cooldown

/// CCRM-94 (iOS App): what the cards and notices say (wireframe rev B), built from a reading and the clock.
@Suite("Card wording and notices")
struct CardContentTests {
    let now: Int64 = 1_790_000_000_000
    let utc = TimeZone(identifier: "UTC")!

    /// A 5-hour window `elapsed` of the way through, reading `pct`.
    func window(_ pct: Double?, elapsed: Double) -> UsageWindow {
        UsageWindow(percent: pct, resetsAtMs: now + Int64((1 - elapsed) * Double(HistoryStats.sessionMs)), serverSeverity: nil)
    }

    func content(_ pct: Double?, elapsed: Double, history: [HistoryPoint] = []) -> WindowContent? {
        CardContent.window(title: "5-hour window", window: window(pct, elapsed: elapsed), kind: HistoryStats.sessionKind,
                           history: history, nowMs: now, timeZone: utc)
    }

    @Test("within 3 points of the even-pace line is on pace; beyond it, below or above")
    func paceLine() throws {
        #expect(try #require(content(48, elapsed: 0.46)).paceLine == "On even pace")
        let below = try #require(content(24, elapsed: 0.46))
        #expect(below.paceLine == "22% below even pace")
        #expect(!below.paceLineAlarm)
        let above = try #require(content(62, elapsed: 0.40))
        #expect(above.paceLine == "22% above even pace")
        #expect(above.paceLineAlarm)
        #expect(above.over)
    }

    @Test("at 100% the card says the limit is reached and draws no projection")
    func atLimit() throws {
        let c = try #require(content(100, elapsed: 0.6))
        #expect(c.paceLine == "Limit reached")
        #expect(c.projectionLine == "Opens again when the window resets")
        #expect(c.chart?.projection == nil)
        #expect(!c.over)
    }

    @Test("a young window says why there's no projection")
    func youngWindow() throws {
        let c = try #require(content(2, elapsed: 0.001))
        #expect(c.projectionLine == Projection.PaceSuppression.youngWindow.explanation)
    }

    @Test("a steady climb projects the percent at the reset, and the chart ends where the sentence does")
    func projection() throws {
        let reset = now + Int64(0.5 * Double(HistoryStats.sessionMs))
        let start = reset - HistoryStats.sessionMs
        let history = stride(from: 0.0, through: 0.45, by: 0.05).map { x in
            HistoryPoint(atMs: start + Int64(x * Double(HistoryStats.sessionMs)), sessionPct: x * 60,
                         sessionResetAtMs: reset, weeklyPct: nil, weeklyResetAtMs: 0)
        }
        let c = try #require(CardContent.window(title: "5-hour window",
            window: UsageWindow(percent: 30, resetsAtMs: reset, serverSeverity: nil),
            kind: HistoryStats.sessionKind, history: history, nowMs: now, timeZone: utc))
        let line = try #require(c.projectionLine)
        #expect(line.hasPrefix("At this pace: ~60% when the window resets"))
        #expect(c.chart?.projection.map { Int($0.rounded()) } == 60)
    }

    @Test("reset lines name the day and use lower-case am/pm, as on Android")
    func resetLines() throws {
        let c = try #require(content(10, elapsed: 0.5))
        #expect(c.resetsIn == "Resets in 2h 30m")
        #expect(c.resetsAt.hasPrefix("Resets at "))
        #expect(c.resetsAt.hasSuffix("am") || c.resetsAt.hasSuffix("pm"))
    }

    @Test("credits read as used of limit with a percent, or as a balance")
    func credits() throws {
        let usage = try #require(UsageParser.parse(Fixtures.claudeWithCredits))
        let capped = try #require(CardContent.credits(usage.credits))
        #expect(capped.text == "$123.90 / $400.00")
        #expect(capped.pct == 31)
    }

    @Test("notices: the plan refusal, a broken sign-in, staleness after 6 hours, or none")
    func notices() {
        var state = AccountState()
        let snapshot = Snapshot(profileKey: "k", provider: .claude, body: "{}", fetchedAtMs: now - 3 * 60_000,
                                lastAttemptAtMs: now, lastKind: "success", lastMessage: "", plan: "Pro")
        #expect(Notice.forAccount(label: "Teams", state: state, snapshot: snapshot, nowMs: now) == nil)

        state.lastKind = "plan"; state.plan = "Free"
        #expect(Notice.forAccount(label: "Teams", state: state, snapshot: nil, nowMs: now)?.title == "Usage isn’t available on the Free plan")

        state.lastKind = "authNeeded"; state.reauthNeeded = true
        let auth = Notice.forAccount(label: "Teams", state: state, snapshot: snapshot, nowMs: now)
        #expect(auth?.title == "Teams: Sign-in stopped working")
        #expect(auth?.action == "Sign in again")

        state = AccountState(); state.lastKind = "network"
        let old = Snapshot(profileKey: "k", provider: .claude, body: "{}", fetchedAtMs: now - 7 * 3_600_000,
                           lastAttemptAtMs: now, lastKind: "network", lastMessage: "", plan: nil)
        #expect(Notice.forAccount(label: "Teams", state: state, snapshot: old, nowMs: now)?.title.hasPrefix("Stale — nothing since") == true)
    }
}

enum Fixtures {
    /// The captured credits shape: $123.90 of $400.00.
    static let claudeWithCredits = #"{"five_hour":{"utilization":4.0,"resets_at":"2026-09-16T11:29:59.851590+00:00"},"spend":{"used":{"amount_minor":12390,"currency":"USD","exponent":2},"limit":{"amount_minor":40000,"currency":"USD","exponent":2},"percent":31,"severity":"normal","enabled":true}}"#
}
