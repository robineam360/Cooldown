import Foundation
import Testing

@testable import CooldownCore

/// RUNBOOK-v2.0 Step 6: CCRM-96 (Weekly First), CCRM-97 (Pace Number), CCRM-98 (Summary Tab) and CCRM-100 (Vs Pace
/// Display) against the spec's own cases, read directly from `spec/core/fixtures/pace` and `spec/mobile/fixtures/*` —
/// the same files Android's PaceFixturesTest reads. Every case runs; a missing file fails rather than passing empty.

private func cases(_ object: [String: Any], _ key: String) throws -> [[String: Any]] {
    let list = try #require(object[key] as? [[String: Any]], "fixture section \(key) missing")
    #expect(!list.isEmpty, "fixture section \(key) is empty")
    return list
}

private func near(_ a: Double, _ b: Double, _ tol: Double = 0.01) -> Bool { abs(a - b) <= tol }

@Suite("v2.0 fixtures: pace, headline, summary, display")
struct V20FixtureTests {

    // MARK: core §6.1–6.2

    @Test("pace and band, every case")
    func pace() throws {
        for c in try cases(Fixtures.json("pace/cases.json"), "pace") {
            let name = c.str("name") ?? "?"
            let used = try #require(c.num("usedPct")), elapsed = try #require(c.num("elapsedPct"))
            let expected = try #require(c.dict("expected"))
            let p = Pace.pace(usedPct: used, elapsedPct: elapsed)
            #expect(near(p, try #require(expected.num("pace"))), "\(name): pace \(p)")
            #expect(Pace.band(usedPct: used, elapsedPct: elapsed).rawValue == expected.str("band"), "\(name): band")
        }
    }

    @Test("allowance, every case")
    func allowance() throws {
        for c in try cases(Fixtures.json("pace/cases.json"), "allowance") {
            let name = c.str("name") ?? "?"
            let a = Pace.allowance(usedPct: try #require(c.num("usedPct")), msUntilReset: try #require(c.i64("msUntilReset")))
            guard let expected = c.dict("expected") else {
                #expect(a == nil, "\(name): expected no allowance")
                continue
            }
            switch (expected.str("mode"), a) {
            case ("perDay", .perDay(let rate)?):
                #expect(near(rate, try #require(expected.num("pctPerDay"))), "\(name): \(rate)")
            case ("hours", .hours(let left, let ms)?):
                #expect(near(left, try #require(expected.num("pctLeft"))), "\(name)")
                #expect(ms == expected.i64("msLeft"), "\(name)")
            default:
                Issue.record("\(name): got \(String(describing: a))")
            }
        }
    }

    @Test("sessionBinding, every case")
    func sessionBinding() throws {
        for c in try cases(Fixtures.json("pace/cases.json"), "sessionBinding") {
            let got = Pace.sessionBinding(sessionPct: c.num("sessionPct"), weeklyPct: c.num("weeklyPct"),
                                          sessionHitsLimitBeforeReset: c.flag("sessionHitsLimitBeforeReset") ?? false)
            #expect(got == c.flag("expected"), "\(c.str("name") ?? "?")")
        }
    }

    // MARK: mobile §5 — Headline

    @Test("headline resolution, every case")
    func headline() throws {
        for c in try cases(Fixtures.mobileJSON("headline/cases.json"), "cases") {
            let setting = try #require(Headline(rawValue: c.str("setting") ?? ""))
            let surface = try #require(HeadlineSurface(rawValue: c.str("surface") ?? ""))
            let got = HeadlineRule.lead(setting: setting, surface: surface, hasSession: c.flag("hasSession") ?? false,
                                        sessionBinding: c.flag("sessionBinding") ?? false)
            #expect(got.rawValue == c.str("expected"), "\(c.str("name") ?? "?")")
        }
    }

    @Test("the Headline defaults to Weekly, and an unknown stored value reads as the default")
    func headlineDefault() {
        #expect(Headline(key: nil) == .weekly)
        #expect(Headline(key: "fiveHour") == .fiveHour)
        #expect(Headline(key: "garbage") == .weekly)
    }

    // MARK: mobile §6 — Summary

    @Test("summary ranking, every case")
    func ranking() throws {
        for c in try cases(Fixtures.mobileJSON("summary/cases.json"), "ranking") {
            let name = c.str("name") ?? "?"
            let accounts = try #require(c["accounts"] as? [[String: Any]])
            let entries = accounts.map { a in
                SummaryRank.Entry(key: a.str("name") ?? "", usedPct: a.num("usedPct"), elapsedPct: a.num("elapsedPct"),
                                  msUntilReset: a.i64("msUntilReset"), sessionBinding: a.flag("sessionBinding") ?? false,
                                  sessionMsUntilReset: a.i64("sessionMsUntilReset"))
            }
            let expected = try #require(c["expected"] as? [[String: Any]])
            let got = SummaryRank.rank(entries)
            #expect(got.map(\.entry.key) == expected.map { $0.str("name") ?? "" }, "\(name): order")
            #expect(got.map(\.group.rawValue) == expected.map { $0.str("group") ?? "" }, "\(name): groups")
        }
    }

    @Test("summary tab and launch, every case")
    func tab() throws {
        for c in try cases(Fixtures.mobileJSON("summary/cases.json"), "tab") {
            let name = c.str("name") ?? "?"
            let n = try #require(c.int("accountCount"))
            #expect(SummaryRank.tabShown(accountCount: n) == c.flag("expectedTabShown"), "\(name): tab")
            // The toggle shows exactly when the tab does (spec mobile §6).
            #expect(SummaryRank.tabShown(accountCount: n) == c.flag("expectedToggleShown"), "\(name): toggle")
            let opens = SummaryRank.opensOnSummary(accountCount: n, openOnSummary: c.flag("openOnSummary") ?? true)
            #expect((opens ? "summary" : "lastViewedAccount") == c.str("expectedOpensOn"), "\(name): opens on")
        }
    }

    @Test("a reading without a reset clock ranks after every clocked eligible row")
    func unclockedRanksLast() {
        let got = SummaryRank.rank([
            .init(key: "unclocked", usedPct: 0, elapsedPct: nil, msUntilReset: nil, sessionBinding: false, sessionMsUntilReset: nil),
            .init(key: "over", usedPct: 90, elapsedPct: 50, msUntilReset: 1, sessionBinding: false, sessionMsUntilReset: nil),
        ])
        #expect(got.map(\.entry.key) == ["over", "unclocked"])
        #expect(got.allSatisfy { $0.group == .eligible })
    }

    @Test("Summary order, every case")
    func summaryOrder() throws {
        for c in try cases(Fixtures.mobileJSON("summary/cases.json"), "order") {
            let accounts = try #require(c["accounts"] as? [[String: Any]])
            let entries = accounts.map {
                SummaryRank.Entry(key: $0.str("name") ?? "", usedPct: $0.num("usedPct"), elapsedPct: nil,
                                  msUntilReset: ($0["msUntilReset"] as? NSNumber)?.int64Value, sessionBinding: false,
                                  sessionMsUntilReset: nil)
            }
            let expected = try #require(c["expected"] as? [String: [String]])
            for (key, order) in [("yours", SummaryOrder.yours), ("resets", .resets), ("used", .used)] {
                #expect(SummaryRank.ordered(entries, order).map(\.entry.key) == expected[key], "\(c.str("name") ?? "?") · \(key)")
            }
            #expect(SummaryRank.ordered(entries, .room) == SummaryRank.rank(entries))
            let groups = Dictionary(uniqueKeysWithValues: SummaryRank.rank(entries).map { ($0.entry.key, $0.group) })
            for order in SummaryOrder.allCases {
                for r in SummaryRank.ordered(entries, order) { #expect(groups[r.entry.key] == r.group) }
            }
        }
        #expect(SummaryOrder(key: nil) == .yours && SummaryOrder(key: "alphabetical") == .yours)
        #expect(SummaryOrder.allCases.map(\.header) == ["your order", "most room first", "resets soonest", "most used"])
    }

    // MARK: mobile §5 — display

    @Test("Vs pace figure, every case")
    func vsPaceFigure() throws {
        for c in try cases(Fixtures.mobileJSON("display/cases.json"), "vsPaceFigure") {
            let elapsed = (c.flag("hasResetClock") ?? true) ? c.num("elapsedPct") : nil
            let got = PaceText.vsPaceFigure(usedPct: c.num("usedPct"), elapsedPct: elapsed)
            #expect(got == c.str("expected"), "\(c.str("name") ?? "?"): \(got)")
            let tone = PaceText.vsPaceTone(usedPct: c.num("usedPct"), elapsedPct: elapsed)
            #expect(tone.rawValue == c.str("tone"), "\(c.str("name") ?? "?"): \(tone)")
        }
    }

    @Test("pace-mark caption, every case")
    func caption() throws {
        for c in try cases(Fixtures.mobileJSON("display/cases.json"), "caption") {
            let mode = try #require(PaceText.Mode(rawValue: c.str("mode") ?? ""))
            let elapsed = (c.flag("hasResetClock") ?? true) ? c.num("elapsedPct") : nil
            let got = PaceText.caption(mode: mode, usedPct: c.num("usedPct"), elapsedPct: elapsed)
            #expect(got == c.str("expected"), "\(c.str("name") ?? "?"): \(got ?? "nil")")
        }
    }

    @Test("caption side, every case")
    func captionSide() throws {
        for c in try cases(Fixtures.mobileJSON("display/cases.json"), "captionSide") {
            let right = PaceText.captionRightOfMark(barWidth: try #require(c.num("barWidthDp")), markPct: try #require(c.num("markPct")),
                                                    captionWidth: try #require(c.num("captionWidthDp")))
            #expect((right ? "right" : "left") == c.str("expectedSide"), "\(c.str("name") ?? "?")")
        }
    }

    // MARK: words (spec mobile §5's tables; no fixture section)

    @Test("band words, allowance and the tight form")
    func words() {
        #expect(PaceText.bandWords(.onPace) == "On pace")
        #expect(PaceText.bandWords(.roomToSpare) == "Room to spare")
        #expect(PaceText.bandWords(.useBeforeReset) == "Use before reset")
        #expect(PaceText.bandWords(.slowDown) == "Slow down")
        #expect(PaceText.bandWords(.nearlyOut) == "Nearly out")
        #expect(PaceText.bandWords(.spent) == nil)
        #expect(PaceText.allowance(.perDay(pctPerDay: 11.2208)) == "≈11%/day")
        #expect(PaceText.allowance(.perDay(pctPerDay: 0.4)) == "<1%/day")
        #expect(PaceText.allowance(.hours(pctLeft: 40, msLeft: 21_600_000)) == "40% left, 6h")
        #expect(PaceText.allowance(.hours(pctLeft: 40, msLeft: 29 * 60_000)) == "40% left, <1h")
        #expect(PaceText.usedAndMark(usedPct: 52.9, elapsedPct: 38.6) == "52% used (mark 39%)")
        #expect(PaceText.tight(.slowDown, .perDay(pctPerDay: 11.2)) == "Slow down · ≈11%/day")
        #expect(PaceText.tight(.onPace, nil) == "On pace")
        #expect(PaceText.tight(.spent, nil) == nil)
    }

    @Test("the notice is owed only to installs that had an account before v2.0")
    func notice() {
        #expect(WeeklyFirstNotice.settle(hadAccountsBefore: true) == WeeklyFirstNotice.pending)
        #expect(WeeklyFirstNotice.settle(hadAccountsBefore: false) == WeeklyFirstNotice.done)
    }
}
