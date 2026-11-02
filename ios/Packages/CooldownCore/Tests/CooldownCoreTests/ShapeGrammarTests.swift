import Foundation
import Testing

@testable import CooldownCore

/// RUNBOOK-v2.0 Step 6d, CCRM-103 (Hybrid Notification): core §6.3's `markTime` and the tap readout's words, read from
/// the same fixtures Android's ShapeGrammarFixturesTest reads.
@Suite("shape grammar: markTime, readout, end mark")
struct ShapeGrammarTests {

    private func list(_ object: [String: Any], _ key: String) throws -> [[String: Any]] {
        let l = try #require(object[key] as? [[String: Any]], "fixture section \(key) missing")
        #expect(!l.isEmpty)
        return l
    }

    private func i64(_ c: [String: Any], _ key: String) -> Int64? { (c[key] as? NSNumber)?.int64Value }

    private func markTime(_ c: [String: Any]) -> MarkTime? {
        MarkTime.of(usedPct: c.num("usedPct"), msUntilReset: i64(c, "msUntilReset"),
                    windowLengthMs: i64(c, "windowLengthMs") ?? 0, nowMs: i64(c, "nowMs") ?? 0)
    }

    @Test("markTime, every core case")
    func core() throws {
        for c in try list(Fixtures.json("pace-distance/cases.json"), "markTime") {
            let got = markTime(c)
            guard let e = c["expected"] as? [String: Any] else {
                #expect(got == nil, "\(c.str("name") ?? "?")")
                continue
            }
            let want: MarkTime
            switch e.str("side") {
            case "on": want = .on
            case "under": want = .under(agoMs: i64(e, "markWasAtUsedAgoMs") ?? -1)
            default: want = .over(atMs: i64(e, "markReachesUsedAtMs") ?? -1, inMs: i64(e, "inMs") ?? -1)
            }
            #expect(got == want, "\(c.str("name") ?? "?")")
        }
    }

    @Test("readout words, every mobile case")
    func readout() throws {
        for c in try list(Fixtures.mobileJSON("display/cases.json"), "markReadout") {
            let zone = try #require(TimeZone(identifier: c.str("zone") ?? ""))
            let got = PaceText.markReadout(markTime(c), use24h: c.flag("use24h") ?? false, timeZone: zone,
                                           locale: Locale(identifier: "en_US_POSIX"))
            #expect(got == c.str("expected"), "\(c.str("name") ?? "?"): \(got ?? "nil")")
        }
    }

    @Test("durations round every part down")
    func durations() {
        #expect(PaceText.behind(59_999) == "0m")
        #expect(PaceText.behind(3_599_999) == "59m")
        #expect(PaceText.behind(3_600_000) == "1h 0m")
        #expect(PaceText.behind(86_399_999) == "23h 59m")
        #expect(PaceText.behind(86_400_000) == "1d 0h")
    }

    @Test("end mark: default, unknown keys, colour roles")
    func endMark() {
        #expect(UsageEndMark(key: nil) == .line)
        #expect(UsageEndMark(key: "bead") == .line)
        #expect(UsageEndMark(key: "round") == .round)
        #expect(UsageEndMark.round.role(usedPct: 64, elapsedPct: 52) == nil)
        #expect(UsageEndMark.line.role(usedPct: 64, elapsedPct: 52) == .fill)
        #expect(UsageEndMark.pace.role(usedPct: 64, elapsedPct: 52) == .over)
        #expect(UsageEndMark.pace.role(usedPct: 34, elapsedPct: 53.3) == .under)
        #expect(UsageEndMark.pace.role(usedPct: 50, elapsedPct: 52) == .ink)
    }
}
