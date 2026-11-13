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

    /// CCRM-97 (Pace Number): the 5h session gets no Pace and no pace-gap line (spec mobile §5) — its line is the
    /// projection; the bar keeps its red slice past the mark (`over`, core §5's 3 points).
    @Test("the 5h card has no pace line; the bar still turns red past the mark")
    func paceLine() throws {
        #expect(try #require(content(48, elapsed: 0.46)).paceLine.isEmpty)
        let below = try #require(content(24, elapsed: 0.46))
        #expect(below.paceLine.isEmpty && !below.paceLineAlarm && !below.over)
        #expect(below.caption == "46% pace mark · 22 under")
        let above = try #require(content(62, elapsed: 0.40))
        #expect(above.paceLine.isEmpty)
        #expect(above.over)
        #expect(above.caption == "40% pace mark · 22 over")
    }

    /// CCRM-104 (Pace Line Parity): a weekly window carries the pace line (core §6.3 Printed form); the 5h, a spent week
    /// and a window that has not started do not.
    @Test("a weekly window carries its pace line: behind, ahead, on the mark; none for the 5h, spent or not started")
    func weeklyPaceLine() throws {
        func weekly(_ pct: Double?, elapsed: Double) -> WindowContent? {
            CardContent.window(title: "Weekly", window: UsageWindow(
                percent: pct, resetsAtMs: now + Int64((1 - elapsed) * Double(HistoryStats.weekMs)), serverSeverity: nil),
                kind: HistoryStats.weeklyKind, history: [], nowMs: now, use24h: false, timeZone: utc)
        }
        let behind = try #require(weekly(40, elapsed: 0.6071))
        let line = try #require(behind.line)
        #expect(line.side == .under && line.tinted?.hasSuffix(" behind") == true)
        #expect(CardContent.compactLine(behind) == line.text() && behind.paceLineFull == line.text())
        let ahead = try #require(weekly(91, elapsed: 0.8318)?.line)
        #expect(ahead.side == .over && ahead.tinted?.hasSuffix(" ahead") == true && ahead.text().contains(" ahead · "))
        let on = try #require(weekly(60.5, elapsed: 0.6071)?.line)
        #expect(on.side == .on && on.text().hasPrefix("On the mark · "))
        #expect(try #require(weekly(100, elapsed: 0.5)).line == nil)
        #expect(try #require(weekly(99.6, elapsed: 0.5)).line == nil)
        #expect(try #require(content(24, elapsed: 0.46)).line == nil)
        let idle = try #require(CardContent.window(
            title: "Weekly", window: UsageWindow(percent: 0, resetsAtMs: nil, serverSeverity: nil),
            kind: HistoryStats.weeklyKind, history: [], nowMs: now, timeZone: utc))
        #expect(idle.line == nil)
    }

    @Test("a window with no reset time hasn't started: the card stays, saying so; a missing window drops the card")
    func notStartedAndAbsent() throws {
        let idle = try #require(CardContent.window(
            title: "5-hour window", window: UsageWindow(percent: 0, resetsAtMs: nil, serverSeverity: nil),
            kind: HistoryStats.sessionKind, history: [], nowMs: now, timeZone: utc))
        #expect(idle.notStarted && idle.chart == nil && idle.pace == nil)
        #expect(idle.resetsIn == "Starts when a message is sent")
        #expect(CardContent.compactLine(idle) == "Starts when a message is sent")
        // A weekly-only ChatGPT account reports no 5-hour window at all: no card, as on Android.
        #expect(CardContent.window(title: "5-hour window", window: nil, kind: HistoryStats.sessionKind,
                                   history: [], nowMs: now, timeZone: utc) == nil)
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

    @Test("reset lines name the day and use core's AM/PM (CCBG-82)")
    func resetLines() throws {
        let c = try #require(content(10, elapsed: 0.5))
        #expect(c.resetsIn == "Resets in 2h 30m")
        #expect(c.resetsAt.hasPrefix("Resets at "))
        #expect(c.resetsAt.hasSuffix("AM") || c.resetsAt.hasSuffix("PM"))  // CCBG-82: core's case
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

        // Staleness alone (the last attempt was fine); a fetch error absorbs it (NoticeParityTests).
        state = AccountState(); state.lastKind = "success"
        let old = Snapshot(profileKey: "k", provider: .claude, body: "{}", fetchedAtMs: now - 7 * 3_600_000,
                           lastAttemptAtMs: now, lastKind: "network", lastMessage: "", plan: nil)
        #expect(Notice.forAccount(label: "Teams", state: state, snapshot: old, nowMs: now)?.title.hasPrefix("Stale — nothing since") == true)
    }

    @Test("the plan notice names the account's own provider, not Anthropic")
    func planNoticeNamesProvider() {
        var state = AccountState(); state.lastKind = "plan"; state.plan = "Free"
        for provider in [Provider.claude, .chatgpt, .antigravity] {
            let notice = Notice.forAccount(label: "A", state: state, snapshot: nil, nowMs: now, provider: provider)
            #expect(notice?.body.hasPrefix("\(provider.displayName) only reports usage windows") == true)
            #expect(notice?.body.contains("Anthropic") == false)
        }
    }

    @Test("a cleared rename restores the positional default: the provider's name, then \"<name> 2\"")
    func defaultLabels() {
        let accounts = [Account(key: "c1", label: "Teams", provider: .claude), Account(key: "g1", label: "Home", provider: .chatgpt),
                        Account(key: "c2", label: "Work", provider: .claude)]
        #expect(Account.defaultLabel(for: "c1", in: accounts) == "Claude")
        #expect(Account.defaultLabel(for: "c2", in: accounts) == "Claude 2")
        #expect(Account.defaultLabel(for: "g1", in: accounts) == "ChatGPT")
        #expect(Account.defaultLabel(for: "nope", in: accounts) == "")
        // Reordered: c2 first, while c1 is still called "Claude" — c2's default must not duplicate it.
        let moved = [Account(key: "c2", label: "Claude 2", provider: .claude), Account(key: "c1", label: "Claude", provider: .claude)]
        #expect(Account.defaultLabel(for: "c2", in: moved) == "Claude 2")
        #expect(Account.defaultLabel(for: "c1", in: moved) == "Claude 3")  // "Claude 2" is c2's
    }
}

enum Fixtures {
    /// The captured credits shape: $123.90 of $400.00.
    static let claudeWithCredits = #"{"five_hour":{"utilization":4.0,"resets_at":"2026-09-16T11:29:59.851590+00:00"},"spend":{"used":{"amount_minor":12390,"currency":"USD","exponent":2},"limit":{"amount_minor":40000,"currency":"USD","exponent":2},"percent":31,"severity":"normal","enabled":true}}"#
}

/// CCRM-94 (iOS App) parity wireframes rev B §5: the credits card's three shapes.
@Suite("Credits card (parity rev B)")
struct CreditsCardTests {
    func credits(used: Int64, limit: Int64?, balance: Int64? = nil) -> SpendCredits {
        SpendCredits(usedMinor: used, limitMinor: limit, exponent: 2, currency: "USD", serverSeverity: nil, balanceMinor: balance)
    }

    @Test("capped, money left: amounts, rounded percent, bar, money left and the cover line")
    func capped() throws {
        let c = try #require(CardContent.creditsCard(credits(used: 957, limit: 4000)))
        #expect(c.amounts == "$9.57 / $40.00" && c.figure == "24%" && c.showsInfo)
        #expect(c.footer == "$30.43 left · covers you when you hit your plan limits" && !c.footerAlarm)
        #expect(abs((c.barPct ?? 0) - 23.925) < 0.001)
    }

    @Test("capped and all spent: red footer, bar full")
    func spent() throws {
        let c = try #require(CardContent.creditsCard(credits(used: 4000, limit: 4000)))
        #expect(c.figure == "100%" && c.footerAlarm && c.barPct == 100)
        #expect(c.footer == "All credits spent — nothing left to cover plan overruns")
        #expect(try #require(CardContent.creditsCard(credits(used: 5000, limit: 4000))).barPct == 100)
    }

    @Test("balance only and no cap: no bar, no info glyph")
    func noCap() throws {
        let b = try #require(CardContent.creditsCard(credits(used: 0, limit: nil, balance: 1240)))
        #expect(b.amounts == "$12.40 balance" && b.figure == "No cap" && !b.showsInfo && b.barPct == nil)
        #expect(b.footer == "Covers you when you hit your plan limits")
        let s = try #require(CardContent.creditsCard(credits(used: 957, limit: nil)))
        #expect(s.amounts == "$9.57 spent" && s.figure == "No cap" && s.barPct == nil)
        #expect(s.footer == "No monthly spend limit — credits cover you when you hit your plan limits")
        #expect(CardContent.creditsCard(credits(used: 0, limit: nil)) == nil)
        #expect(CardContent.creditsCard(nil) == nil)
    }

    @Test("the provenance note says the figure is computed, not an estimate")
    func provenance() {
        #expect(CardContent.CreditsCardContent.provenance == "Computed from the exact amounts — finer than the server’s rounded figure, not an estimate.")
    }
}

/// CCRM-94 (iOS App) parity wireframes rev B §4: History's width rule and footnote.
@Suite("History layout (parity rev B)")
struct HistoryLayoutTests {
    @Test("two panes on a regular width, one on a compact width")
    func columns() {
        // CCRM-116 (Inner Panes): two panes on any regular-width window, no pt breakpoint.
        #expect(HistoryLayout.twoColumns(regularWidth: true))
        #expect(!HistoryLayout.twoColumns(regularWidth: false))
    }

    @Test("the footnote names the pane's kind, or both when the columns are side by side")
    func footnote() {
        #expect(HistoryLayout.footnote(twoColumns: false, kind: HistoryStats.sessionKind).contains("Peak 5h readings are sampled"))
        #expect(HistoryLayout.footnote(twoColumns: false, kind: HistoryStats.weeklyKind).contains("Peak weekly readings are sampled"))
        #expect(HistoryLayout.footnote(twoColumns: true, kind: HistoryStats.sessionKind).contains("Peak 5h and weekly readings are sampled when the app checks."))
    }
}

/// CCRM-94 (iOS App) parity wireframes rev B §5: the chart's curve fill, split at the even-pace diagonal.
@Suite("Chart fill split (parity rev B)")
struct ChartFillTests {
    @Test("a curve that stays under the diagonal has no overshoot")
    func under() {
        let r = TrendChart.splitFill([CGPoint(x: 0, y: 0), CGPoint(x: 0.5, y: 20), CGPoint(x: 1, y: 60)])
        #expect(r.above.isEmpty)
        #expect(r.below.count == 3 && r.below.allSatisfy { $0.y <= 100 * $0.x + 1e-9 })
    }

    @Test("a curve past the diagonal gets an overshoot polygon between the curve and the diagonal, from the crossing")
    func over() throws {
        // Starts under (x 0.1, 5% < 10), ends over (x 0.5, 80% > 50).
        let r = TrendChart.splitFill([CGPoint(x: 0.1, y: 5), CGPoint(x: 0.3, y: 20), CGPoint(x: 0.5, y: 80)])
        let poly = try #require(r.above.first)
        #expect(r.above.count == 1)
        let first = try #require(poly.first)
        #expect(abs(Double(first.y) - 100 * Double(first.x)) < 1e-6)       // begins at the crossing, on the diagonal
        #expect(poly.contains { $0.y > 100 * $0.x + 1 })                     // and reaches above it
        #expect(r.below.allSatisfy { $0.y <= 100 * $0.x + 1e-9 })           // the lower fill never crosses the diagonal
    }

    @Test("fewer than two samples: nothing to split")
    func tiny() {
        #expect(TrendChart.splitFill([]).above.isEmpty)
        #expect(TrendChart.splitFill([CGPoint(x: 0.2, y: 90)]).above.isEmpty)
    }
}

/// CCRM-94 (iOS App) parity wireframes rev B §5-6, the judge's fixes: "spent" is the money left, not the rounded percent;
/// the share card's row order follows the Headline.
@Suite("Credits spent and share-card order (parity rev B)")
struct ParityFixTests {
    @Test("$39.81 of $40.00 is not spent: $0.19 left, no alarm; $40.00 of $40.00 is spent")
    func notSpentYet() throws {
        func c(_ used: Int64) -> SpendCredits { SpendCredits(usedMinor: used, limitMinor: 4000, exponent: 2, currency: "USD", serverSeverity: nil) }
        let almost = try #require(CardContent.creditsCard(c(3981)))
        #expect(almost.figure == "100%" && !almost.footerAlarm)
        #expect(almost.footer == "$0.19 left · covers you when you hit your plan limits")
        let spent = try #require(CardContent.creditsCard(c(4000)))
        #expect(spent.footerAlarm && spent.footer.hasPrefix("All credits spent"))
    }

    @Test("share-card order: Weekly leads by default and under Auto unless 5h binds; 5-hour leads under Headline 5-hour; spent week leads always; no 5h = one row")
    func order() {
        func o(_ h: Headline, session: Bool = true, binding: Bool = false, spent: Bool = false) -> [WindowKind] {
            ShareCardPlan.order(headline: h, hasSession: session, sessionBinding: binding, weeklySpent: spent)
        }
        #expect(o(.weekly) == [.weekly, .session])
        #expect(o(.auto) == [.weekly, .session])
        #expect(o(.auto, binding: true) == [.session, .weekly])
        #expect(o(.fiveHour) == [.session, .weekly])
        #expect(o(.fiveHour, spent: true) == [.weekly, .session])
        #expect(o(.auto, binding: true, spent: true) == [.weekly, .session])
        #expect(o(.fiveHour, session: false) == [.weekly])
    }
}
