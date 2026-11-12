import Foundation
import Testing

@testable import CooldownCore

/// CCRM-108 (Reset Named) and CCRM-107 (Day Grid): core §6.3, read from
/// `spec/core/fixtures/pace-distance/printed.json` (written at RUNBOOK Step 1 as `printed-v20.json`, folded in at Step 2).
@Suite("printed form v2.0: the reset named, its drop order, the day grid")
struct PrintedFormV20Tests {

    private let utc = TimeZone(identifier: "UTC")!

    private func fixture() throws -> [String: Any] { try Fixtures.json("pace-distance/printed.json") }

    private func list(_ key: String) throws -> [[String: Any]] {
        let list = try #require(try fixture()[key] as? [[String: Any]], "fixture section \(key) missing")
        #expect(!list.isEmpty, "fixture section \(key) is empty")
        return list
    }

    private func i64(_ c: [String: Any], _ key: String) -> Int64? { (c[key] as? NSNumber)?.int64Value }

    private func local(_ s: String) -> Int64 {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = utc
        f.dateFormat = "yyyy-MM-dd'T'HH:mm"
        return Int64((f.date(from: s)?.timeIntervalSince1970 ?? 0) * 1000)
    }

    /// A `PaceLine` built the way `PaceText.paceLine` builds it, from the case's side, duration, now and reset.
    private func line(_ c: [String: Any]) -> PaceLine? {
        guard let side = c.str("side"), let s = PaceLine.Side(rawValue: side) else { return nil }
        let now = local(c.str("nowLocal") ?? ""), reset = local(c.str("resetLocal") ?? "")
        let full = Fmt.dayClock(epochMs: reset, nowMs: now, use24h: c.flag("hour24") ?? false, timeZone: utc)
        let parts = full.split(separator: " ", maxSplits: 1).map(String.init)
        let hasDay = !Fmt.sameDay(reset, now, timeZone: utc)
        return PaceLine(side: s, duration: s == .on ? nil : PaceText.behind(i64(c, "durationMs") ?? 0),
                        weekday: hasDay ? parts.first : nil, clock: hasDay ? (parts.count > 1 ? parts[1] : full) : full)
    }

    @Test("paceLine: the reset named, the tinted span")
    func paceLine() throws {
        for c in try list("paceLine") {
            let name = c.str("name") ?? "?"
            guard let l = line(c) else { #expect(c.str("text") == nil, "\(name)"); continue }
            #expect(l.text() == c.str("text"), "\(name): \(l.text())")
            #expect(l.reset() == c.str("reset"), "\(name)")
            #expect(l.tinted == c.str("tinted"), "\(name)")
            if let side = c.str("side"), let s = PaceLine.Side(rawValue: side) {
                #expect(PaceLine.text(side: s, durationMs: i64(c, "durationMs"), reset: c.str("reset") ?? "") == c.str("text"), "\(name)")
            }
        }
    }

    @Test("paceLineForms: resets, the bare reset, the weekday alone, the floor; a bare clock means today")
    func paceLineForms() throws {
        for c in try list("paceLineForms") {
            let name = c.str("name") ?? "?"
            let want = (c["forms"] as? [String]) ?? []
            guard let l = line(c) else { #expect(want.isEmpty, "\(name)"); continue }
            #expect(l.forms == want, "\(name): \(l.forms)")
        }
    }

    @Test("end to end: numbers to the figure and the line")
    func endToEnd() throws {
        let e2e = try #require(try fixture()["endToEnd"] as? [String: Any])
        let cases = try #require(e2e["cases"] as? [[String: Any]])
        #expect(!cases.isEmpty)
        for c in cases {
            let name = c.str("name") ?? "?"
            let now = i64(c, "nowMs") ?? 0, reset = i64(c, "resetMs") ?? 0, len = i64(c, "windowLengthMs") ?? 0
            let elapsed = Fmt.elapsedPercent(resetsAtMs: reset, windowLengthMs: len, nowMs: now)
            if let figure = c.str("figure") {
                #expect(PaceText.signedVsPaceFigure(usedPct: c.num("usedPct"), elapsedPct: elapsed) == figure, "\(name)")
            }
            let l = PaceText.paceLine(usedPct: c.num("usedPct"), resetMs: reset, windowLengthMs: len, nowMs: now, use24h: false, timeZone: utc)
            if let want = c.str("line") {
                #expect(l?.text() == want, "\(name): \(l?.text() ?? "nil")")
            } else {
                // No line: core §6.1's spent week, which `CardContent` gates before it asks for a line.
                #expect(Pace.isSpent(c.num("usedPct")), "\(name): a case with no line must be a spent week")
            }
        }
    }

    @Test("drop order: resets, then the clock, never the duration and word")
    func dropOrder() {
        let l = PaceLine(side: .over, duration: "23h 59m", weekday: "Wed", clock: "12:30 PM")
        #expect(l.text(.none) == "23h 59m ahead · resets Wed 12:30 PM")
        #expect(l.text(.word) == "23h 59m ahead · Wed 12:30 PM")
        #expect(l.text(.clock) == "23h 59m ahead · Wed")
        #expect(l.text(.floor) == "23h 59m ahead")
        let today = PaceLine(side: .on, duration: nil, weekday: nil, clock: "11:00 PM")
        #expect(today.forms == ["On the mark · resets 11:00 PM", "On the mark · 11:00 PM", "On the mark"])
        #expect(today.text(.clock) == "On the mark", "later today never prints a bare weekday or a bare clock past the bare-clock form")
    }

    @Test("day grid: lines, the now column, ordinals that give way")
    func dayGrid() throws {
        for c in try list("dayGrid") {
            let name = c.str("name") ?? "?"
            let n = try #require((c["columns"] as? NSNumber)?.intValue)
            let width = try #require(c.num("plotWidth"))
            let start = local(c.str("startLocal") ?? ""), now = local(c.str("nowLocal") ?? "")
            let length: Int64 = n == 7 ? 7 * 86_400_000 : 5 * 3_600_000
            let ex = try #require(c.dict("expected"))
            #expect(DayGrid.columns(windowLengthMs: length) == n, "\(name)")

            let verticals = try #require(ex["verticals"] as? [[String: Any]])
            let fr = DayGrid.fractions(columns: n)
            #expect(fr.count == verticals.count, "\(name)")
            for (f, v) in zip(fr, verticals) {
                #expect(abs(f - (v.num("xFraction") ?? -1)) < 1e-6, "\(name)")
                let k = Int64((v["k"] as? NSNumber)?.intValue ?? 0)
                #expect(start + k * length / Int64(n) == local(v.str("atLocal") ?? ""), "\(name): vertical \(k)")
            }
            let horizontals = try #require(ex["horizontals"] as? [[String: Any]])
            #expect(fr.count == horizontals.count, "\(name)")
            for (f, h) in zip(fr, horizontals) { #expect(abs(100 * f - (h.num("pct") ?? -1)) < 1e-6, "\(name)") }

            let col = DayGrid.nowColumn(columns: n, elapsedMs: now - start, windowLengthMs: length)
            #expect(col == (ex["nowColumn"] as? NSNumber)?.intValue, "\(name): now column \(col)")
            // The fixture's own nowX feeds the give-way rule, so the test checks DayGrid, not a copy of the chart's formula.
            let nowX = try #require(ex.num("nowX"))

            let w = try #require(c.dict("widths"))
            let got = DayGrid.ordinals(columns: n, plotWidth: width, nowColumn: col, nowAt: (c.flag("nowLabel") ?? false) ? nowX : nil,
                                       startWidth: w.num("start") ?? 0, endWidth: w.num("end") ?? 0,
                                       ordinalWidth: w.num("ordinal") ?? 0, nowWidth: w.num("now") ?? 0,
                                       nowGap: c.num("nowGap") ?? 6, endGap: c.num("endGap") ?? 4,
                                       dropBelowColumn: c.num("dropBelowColumn") ?? 14)
            let want = try #require(ex["ordinals"] as? [[String: Any]])
            #expect(got.count == want.count, "\(name)")
            for (g, o) in zip(got, want) {
                #expect(g.text == o.str("text"), "\(name)")
                #expect(g.drawn == (o.flag("drawn") ?? !g.drawn), "\(name): ordinal \(g.text) drawn \(g.drawn) (\(o.str("why") ?? ""))")
                if g.drawn {
                    #expect(abs(g.centre - (o.num("centreX") ?? -1)) < 1e-3, "\(name): ordinal \(g.text) centre \(g.centre)")
                    #expect(g.bold == (o.flag("bold") ?? false), "\(name): ordinal \(g.text) bold")
                }
            }
        }
    }
}
