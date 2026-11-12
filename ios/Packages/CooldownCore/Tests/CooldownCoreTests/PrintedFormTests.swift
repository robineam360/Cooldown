import Foundation
import Testing

@testable import CooldownCore

/// CCRM-104 (Pace Line Parity): core §6.3 "Printed form", read from the fixture Android's PrintedFormFixturesTest reads.
@Suite("printed form: signed figure, durations, day clock, caption, map words")
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

    // paceLine (the reset named), paceLineForms, endToEnd and the day grid: `PrintedFormV20Tests`, on this same file.

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

    @Test("caption drop order: the distance clause alone, or the used figure in Vs pace")
    func captionShort() {
        #expect(PaceText.captionShort("40% pace mark · 12 over") == "12 over")
        #expect(PaceText.captionShort("40% pace mark · 10 to spare") == "10 to spare")
        #expect(PaceText.captionShort("60% pace mark · on it") == "on it")
        #expect(PaceText.captionShort("41% used · 60% pace mark") == "41% used")
    }

    @Test("map words")
    func mapLabels() throws {
        let fx = try Fixtures.json("pace-distance/printed.json")
        let labels = try #require(fx["mapLabels"] as? [String: Any])
        let m = try #require(labels["words"] as? [String: Any])
        #expect(PaceText.MapWords.aboveDiagonal == m.str("aboveDiagonal"))
        #expect(PaceText.MapWords.belowDiagonal == m.str("belowDiagonal"))
        #expect(PaceText.MapWords.diagonal == m.str("diagonal"))
        #expect(PaceText.MapWords.xStart == m.str("xStart"))
        #expect(PaceText.MapWords.xEnd == m.str("xEnd"))
        // CCRM-107 (Day Grid): the centre note left the axis for the card's foot line.
        #expect(PaceText.MapWords.xCentre == m.str("footNote"))
    }
}
