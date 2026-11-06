import Foundation
import Testing

@testable import CooldownCore

/// CCRM-104 (Pace Line Parity): core §6.3 "Printed form", read from the fixture Android's PrintedFormFixturesTest reads.
@Suite("printed form: signed figure, durations, pace line, caption, map words")
struct PrintedFormTests {

    private func list(_ key: String) throws -> [[String: Any]] {
        let fx = try Fixtures.json("pace-distance/printed.json")
        return try #require(fx[key] as? [[String: Any]], "fixture section \(key) missing")
    }

    private func i64(_ c: [String: Any], _ key: String) -> Int64? { (c[key] as? NSNumber)?.int64Value }

    private func local(_ s: String, _ zone: TimeZone) -> Int64 {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = zone
        f.dateFormat = "yyyy-MM-dd'T'HH:mm"
        return Int64((f.date(from: s)?.timeIntervalSince1970 ?? 0) * 1000)
    }

    @Test("figure: signed text, unsigned ring text")
    func figure() throws {
        for c in try list("figure") {
            let name = c.str("name") ?? "?"
            guard let d = (c["vsPace"] as? NSNumber)?.intValue else { continue } // the no-figure case: surfaces fall back to usage
            #expect(PaceText.signed(d) == c.str("text"), "\(name)")
            #expect(PaceText.unsigned(d) == c.str("ringText"), "\(name)")
        }
        #expect(PaceText.signedVsPaceFigure(usedPct: 99.6, elapsedPct: 50) == "99%")
        #expect(PaceText.signedVsPaceFigure(usedPct: 40, elapsedPct: nil) == "40%")
        #expect(PaceText.signedVsPaceFigure(usedPct: nil, elapsedPct: 50) == "—")
        #expect(!PaceText.signed(-12).contains("-"))
    }

    @Test("duration: the two largest units, rounded down")
    func duration() throws {
        for c in try list("duration") { #expect(PaceText.behind(i64(c, "ms") ?? -1) == c.str("text"), "\(c["ms"] ?? "?")") }
    }

    @Test("dayClock: weekday dropped later the same local day")
    func dayClock() throws {
        let zone = TimeZone(identifier: "UTC")!
        for c in try list("dayClock") {
            let now = local(c.str("nowLocal") ?? "", zone), reset = local(c.str("resetLocal") ?? "", zone)
            let got = Fmt.dayClock(epochMs: reset, nowMs: now, use24h: c.flag("hour24") ?? false, timeZone: zone)
            #expect(got == c.str("text"), "\(c.str("name") ?? "?"): \(got)")
        }
    }

    @Test("paceLine: text, tinted span and tone side")
    func paceLine() throws {
        for c in try list("paceLine") {
            let name = c.str("name") ?? "?"
            guard let side = c.str("side"), let s = PaceLine.Side(rawValue: side) else { continue }
            #expect(PaceLine.text(side: s, durationMs: i64(c, "durationMs"), reset: c.str("reset") ?? "") == c.str("text"), "\(name)")
            if let tinted = c.str("tinted") {
                let l = PaceLine(side: s, duration: PaceText.behind(i64(c, "durationMs") ?? 0), weekday: nil, clock: "")
                #expect(l.tinted == tinted, "\(name)")
            }
        }
    }

    @Test("end to end: numbers to the figure and the line")
    func endToEnd() throws {
        let fx = try Fixtures.json("pace-distance/printed.json")
        let e2e = try #require(fx["endToEnd"] as? [String: Any])
        let zone = TimeZone(identifier: "UTC")!
        for c in try #require(e2e["cases"] as? [[String: Any]]) {
            let name = c.str("name") ?? "?"
            let now = i64(c, "nowMs") ?? 0, reset = i64(c, "resetMs") ?? 0, len = i64(c, "windowLengthMs") ?? 0
            let elapsed = Fmt.elapsedPercent(resetsAtMs: reset, windowLengthMs: len, nowMs: now)
            #expect(PaceText.signedVsPaceFigure(usedPct: c.num("usedPct"), elapsedPct: elapsed) == c.str("figure"), "\(name)")
            let line = PaceText.paceLine(usedPct: c.num("usedPct"), resetMs: reset, windowLengthMs: len, nowMs: now, use24h: false, timeZone: zone)
            #expect(line?.text() == c.str("line"), "\(name): \(line?.text() ?? "nil")")
        }
    }

    @Test("drop order: weekday, then clock, never the duration and word")
    func dropOrder() {
        let l = PaceLine(side: .over, duration: "23h 59m", weekday: "Wed", clock: "12:30 PM")
        #expect(l.text(.none) == "23h 59m ahead · Wed 12:30 PM")
        #expect(l.text(.weekday) == "23h 59m ahead · 12:30 PM")
        #expect(l.text(.clock) == "23h 59m ahead")
        #expect(PaceLine(side: .on, duration: nil, weekday: nil, clock: "11:00 PM").text() == "On the mark · 11:00 PM")
        #expect(PaceLine(side: .under, duration: "1h 0m", weekday: nil, clock: "9:00 pm").text(.weekday) == "1h 0m behind · 9:00 pm")
    }

    @Test("caption: every printed case")
    func caption() throws {
        for c in try list("caption") {
            let name = c.str("name") ?? "?"
            let mode = try #require(PaceText.Mode(rawValue: c.str("mode") ?? ""))
            let mark = c.num("mark") ?? 0
            if mode == .vsPace {
                let got = PaceText.caption(mode: .vsPace, usedPct: c.num("usedFigure"), elapsedPct: mark)
                #expect(got == c.str("text"), "\(name): \(got ?? "nil")")
                continue
            }
            let dist = c.num("distance") ?? 0
            let used: Double = c.str("side") == "over" ? mark + dist : c.str("side") == "under" ? mark - dist : mark
            let got = PaceText.caption(mode: mode, usedPct: used, elapsedPct: mark)
            #expect(got == c.str("text"), "\(name): \(got ?? "nil")")
        }
    }

    @Test("map words")
    func mapLabels() throws {
        let fx = try Fixtures.json("pace-distance/printed.json")
        let m = try #require(fx["mapLabels"] as? [String: Any])
        #expect(PaceText.MapWords.aboveDiagonal == m.str("aboveDiagonal"))
        #expect(PaceText.MapWords.belowDiagonal == m.str("belowDiagonal"))
        #expect(PaceText.MapWords.diagonal == m.str("diagonal"))
        #expect(PaceText.MapWords.xStart == m.str("xStart"))
        #expect(PaceText.MapWords.xEnd == m.str("xEnd"))
    }
}
