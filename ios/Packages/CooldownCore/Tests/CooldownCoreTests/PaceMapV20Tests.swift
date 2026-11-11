import Foundation
import Testing

@testable import CooldownCore

/// CCRM-109 (Quiet Accounts) and CCRM-111 (Map Layers): core §6.3 "Planned — v2.0 feedback", read from
/// `spec/core/fixtures/pace-distance/printed-v20.json` (`quiet`, `mapLabels`, `callout`, `projectionLayer`) and the mobile
/// foot line from `spec/mobile/fixtures/map-foot/cases.json`, until RUNBOOK Step 2 folds them into the old files.
@Suite("Pace map v2.0: quiet accounts, figures, callout, projection, labels")
struct PaceMapV20Tests {

    private let utc = TimeZone(identifier: "UTC")!

    private func fixture() throws -> [String: Any] { try Fixtures.json("pace-distance/printed-v20.json") }

    private func list(_ object: [String: Any], _ key: String) throws -> [[String: Any]] {
        let list = try #require(object[key] as? [[String: Any]], "fixture section \(key) missing")
        #expect(!list.isEmpty, "fixture section \(key) is empty")
        return list
    }

    private func estimate(_ c: [String: Any]) -> Projection.Estimate? {
        guard let e = c.dict("estimate") else { return nil }
        return Projection.Estimate(ratePctPerHour: 0, hitsLimitAtMs: e.i64("hitsLimitAtMs"), pctAtReset: e.num("pctAtReset") ?? 0)
    }

    /// The fixture's spent flag is the account's; the rules read it from the reading (≥ 99.5 is spent).
    private func spentMatches(_ c: [String: Any]) {
        #expect(Pace.isSpent(c.num("usedPct")) == (c.flag("spent") ?? false), "\(c.str("name") ?? "?"): spent flag")
    }

    // MARK: CCRM-109

    @Test("quiet: an account leaves the map when its row prints 0%, every case")
    func quiet() throws {
        for c in try list(try fixture(), "quiet") {
            let name = c.str("name") ?? "?"
            let used = c.num("usedPct")
            #expect(PaceMapRules.rowPrints(used) == c.str("rowPrints"), "\(name): row prints")
            let clocked = c["resetMs"] == nil || c.has("resetMs")
            #expect(PaceMapRules.isPlotted(usedPct: used, clocked: clocked) == c.flag("plotted"), "\(name): plotted")
        }
    }

    @Test("map foot line (mobile): the count, the plotted accounts and the empty message, every case")
    func foot() throws {
        for c in try list(try Fixtures.mobileJSON("map-foot/cases.json"), "footLine") {
            let name = c.str("name") ?? "?"
            let raw = try #require(c["accounts"] as? [[String: Any]])
            let accounts = raw.map { PaceMapRules.Account(usedPct: $0.num("usedPct"), clocked: $0.flag("clocked") ?? false) }
            let expected = try #require(c.dict("expected"))
            let plotted = raw.filter { PaceMapRules.isPlotted(usedPct: $0.num("usedPct"), clocked: $0.flag("clocked") ?? false) }
                .compactMap { $0.str("name") }
            #expect(plotted == (expected["plotted"] as? [String]), "\(name): plotted \(plotted)")
            let foot = PaceMapRules.foot(accounts)
            #expect(foot.left == expected.str("left"), "\(name): left")
            #expect(foot.count == expected.str("count"), "\(name): count \(foot.count ?? "nil")")
            if let empty = expected.dict("empty") {
                #expect(foot.empty?.title == empty.str("title") && foot.empty?.detail == empty.str("detail"), "\(name): empty")
            } else {
                #expect(foot.empty == nil, "\(name): no empty message")
            }
        }
    }

    // MARK: CCRM-111

    @Test("mapLabels words: the axis and the foot note")
    func words() throws {
        let w = try #require(try fixture().dict("mapLabels")?.dict("words"))
        #expect(PaceText.MapWords.aboveDiagonal == w.str("aboveDiagonal"))
        #expect(PaceText.MapWords.belowDiagonal == w.str("belowDiagonal"))
        #expect(PaceText.MapWords.diagonal == w.str("diagonal"))
        #expect(PaceText.MapWords.xStart == w.str("xStart"))
        #expect(PaceText.MapWords.xEnd == w.str("xEnd"))
        #expect(PaceText.MapWords.xCentre == w.str("footNote"))
    }

    @Test("mapLabels figures: Used, Left, Vs pace, spent, truncation, every case")
    func figures() throws {
        let labels = try #require(try fixture().dict("mapLabels"))
        for c in try list(labels, "figures") {
            let name = c.str("name") ?? "?"
            spentMatches(c)
            let mode = try #require(PaceMapRules.FigureMode(rawValue: c.str("mode") ?? ""))
            let got = PaceMapRules.label(name: c.str("account") ?? "", mode: mode, usedPct: c.num("usedPct"), elapsedPct: c.num("elapsedPct"))
            #expect(got == c.str("label"), "\(name): \(got ?? "nil")")
        }
    }

    @Test("callout forecast: the third line, every case")
    func forecast() throws {
        let callout = try #require(try fixture().dict("callout"))
        for c in try list(callout, "forecast") {
            let name = c.str("name") ?? "?"
            spentMatches(c)
            let got = PaceMapRules.forecast(estimate: estimate(c), usedPct: c.num("usedPct"), resetMs: c.i64("resetMs"),
                                            nowMs: try #require(c.i64("nowMs")), use24h: c.flag("hour24") ?? false, timeZone: utc)
            #expect(got == c.str("text"), "\(name): \(got ?? "nil")")
            #expect(PaceMapRules.runsOut(estimate: estimate(c), usedPct: c.num("usedPct"), resetMs: c.i64("resetMs"))
                    == (c.str("text")?.hasPrefix("100% at ") == true && c.str("text") != "100% at reset"), "\(name): runs out")
        }
    }

    @Test("projection layer: the tail's end and the ring, every case")
    func projection() throws {
        for c in try list(try fixture(), "projectionLayer") {
            let name = c.str("name") ?? "?"
            spentMatches(c)
            let got = PaceMapRules.projection(estimate: estimate(c), usedPct: c.num("usedPct"), resetMs: c.i64("resetMs"),
                                              windowLengthMs: HistoryStats.weekMs)
            guard let layer = c.dict("layer") else {
                #expect(got == nil, "\(name): expected no tail")
                continue
            }
            let end = try #require(got, "\(name): expected a tail")
            #expect(abs(end.weekGonePct - (layer.num("endWeekGonePct") ?? -1)) < 1e-6, "\(name): x \(end.weekGonePct)")
            #expect(abs(end.usedPct - (layer.num("endUsedPct") ?? -1)) < 1e-6, "\(name): y \(end.usedPct)")
            #expect(end.runsOut == (layer.str("ring") == "red"), "\(name): ring")
            #expect(end.runsOut == (layer.str("tailEnd") == "red"), "\(name): tail end")
        }
    }

    @Test("mapLabels placement: the dense snapshot map and the projection case, spot, box, leader and reasons")
    func placement() throws {
        let labels = try #require(try fixture().dict("mapLabels"))
        for c in try list(labels, "placement") {
            let name = c.str("name") ?? "?"
            let plot = try #require(c.dict("plot")), g = try #require(c.dict("geometry"))
            let w = try #require(plot.num("width")), h = try #require(plot.num("height"))
            let m = try #require(g.dict("nameAreaMargin")), s = try #require(g.dict("spots"))
            let geometry = MapLabels.Geometry(
                dotHalf: try #require(g.num("dotHalf")), nameGap: try #require(g.num("nameGap")), fixedGap: try #require(g.num("fixedGap")),
                margin: (m.num("left") ?? 0, m.num("top") ?? 0, m.num("right") ?? 0, m.num("bottom") ?? 0),
                rightDx: s.num("rightDx") ?? 0, belowDx: s.num("belowDx") ?? 0, belowDy: s.num("belowDy") ?? 0,
                leftDx: s.num("leftDx") ?? 0, aboveDy: s.num("aboveDy") ?? 0,
                leaderDistances: (g["leaderDistances"] as? [NSNumber])?.map(\.doubleValue) ?? [])
            func rect(_ any: Any?) -> MapLabels.Rect? {
                guard let b = any as? [NSNumber], b.count == 4 else { return nil }
                return MapLabels.Rect(b[0].doubleValue, b[1].doubleValue, b[2].doubleValue, b[3].doubleValue)
            }
            let fixed = (c["fixed"] as? [[String: Any]] ?? []).compactMap { rect($0["box"]) }
            let rings = (c["rings"] as? [[String: Any]] ?? []).compactMap { rect($0["box"]) }
            let dots = try #require(c["dots"] as? [[String: Any]]).map { d -> MapLabels.Dot in
                let tail = d.dict("tail").map { t in
                    ((t.num("toWeekGonePct") ?? 0) / 100 * w, (100 - (t.num("toUsedPct") ?? 0)) / 100 * h)
                }
                return MapLabels.Dot(key: d.str("name") ?? "", x: (d.num("weekGonePct") ?? 0) / 100 * w,
                                     y: (100 - (d.num("usedPct") ?? 0)) / 100 * h,
                                     w: d.num("nameWidth") ?? 0, h: d.num("nameHeight") ?? 0, tailEnd: tail)
            }
            let expected = try #require(c.dict("expected"))
            #expect(MapLabels.order(dots).map(\.key) == (expected["order"] as? [String]), "\(name): order")
            let got = MapLabels.place(dots: dots, plotWidth: w, plotHeight: h, geometry: geometry, fixed: fixed, rings: rings)
            for e in try #require(expected["names"] as? [[String: Any]]) {
                let who = e.str("name") ?? "?"
                let p = try #require(got.first { $0.key == who }, "\(name): \(who) missing")
                #expect(p.spot.rawValue == e.str("spot"), "\(name): \(who) spot \(p.spot)")
                if let box = rect(e["box"]) {
                    let b = try #require(p.box, "\(name): \(who) expected a box")
                    #expect(abs(b.x - box.x) < 1e-3 && abs(b.y - box.y) < 1e-3 && abs(b.w - box.w) < 1e-3 && abs(b.h - box.h) < 1e-3,
                            "\(name): \(who) box \(b)")
                } else {
                    #expect(p.box == nil, "\(name): \(who) expected no box")
                }
                if let l = e.dict("leader") {
                    let got = try #require(p.leader, "\(name): \(who) expected a leader")
                    let end = try #require(l["end"] as? [NSNumber])
                    #expect(got.distance == l.num("distance") && got.direction.rawValue == l.str("direction"), "\(name): \(who) leader")
                    #expect(abs(got.endX - end[0].doubleValue) < 1e-3 && abs(got.endY - end[1].doubleValue) < 1e-3, "\(name): \(who) end")
                } else {
                    #expect(p.leader == nil, "\(name): \(who) expected no leader")
                }
                #expect(p.refusedFor == Set(e["refusedFor"] as? [String] ?? []), "\(name): \(who) reasons \(p.refusedFor.sorted())")
            }
        }
    }

    @Test("leaders stay short: never past the longest distance")
    func leaderLength() throws {
        let labels = try #require(try fixture().dict("mapLabels"))
        for c in try list(labels, "placement") {
            let max = (c.dict("geometry")?["leaderDistances"] as? [NSNumber])?.map(\.doubleValue).max() ?? 0
            for e in (c.dict("expected")?["names"] as? [[String: Any]] ?? []) {
                if let d = e.dict("leader")?.num("distance") { #expect(d <= max) }
            }
        }
    }
}
