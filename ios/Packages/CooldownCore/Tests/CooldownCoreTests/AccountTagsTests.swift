import Foundation
import Testing

@testable import CooldownCore

/// CCRM-110 (Account Tags) and CCRM-112 (Summary Snapshot), read from `spec/mobile/fixtures/tags/cases.json` (rev B §6, §8).
@Suite("Account tags and the Summary filter (CCRM-110)")
struct AccountTagsTests {

    private func cases() throws -> [[String: Any]] {
        let list = try #require(try Fixtures.mobileJSON("tags/cases.json")["filter"] as? [[String: Any]])
        #expect(list.count >= 8)
        return list
    }

    @Test("filter: control, enabled segments, the filter in force, the rows shown, the one-line form and the snapshot header")
    func filter() throws {
        for c in try cases() {
            let name = c.str("name") ?? "?"
            let accounts = try #require(c["accounts"] as? [[String: Any]])
            let names = accounts.compactMap { $0.str("name") }
            let tags = accounts.map { AccountTag(stored: $0.str("tag")) }
            let expected = try #require(c.dict("expected"))
            let r = SummaryFilter.resolve(tags: tags, remembered: c.str("rememberedFilter"))
            #expect(r.controlShown == expected.flag("controlShown"), "\(name): control")
            if let seg = expected.dict("segmentsEnabled") {
                #expect(r.segmentsEnabled?[.all] == seg.flag("all") && r.segmentsEnabled?[.personal] == seg.flag("personal")
                        && r.segmentsEnabled?[.work] == seg.flag("work"), "\(name): segments")
            } else {
                #expect(r.segmentsEnabled == nil, "\(name): no segments")
            }
            #expect(r.filter.rawValue == expected.str("filter"), "\(name): filter \(r.filter)")
            #expect(r.shown.map { names[$0] } == (expected["shown"] as? [String]), "\(name): shown")
            #expect(r.oneLineRows == expected.flag("oneLineRows"), "\(name): one-line rows")
            #expect(r.snapshotHeader == expected.str("snapshotHeader"), "\(name): header")
        }
    }

    @Test("an absent, empty or unknown stored tag reads as None; the two known spellings round-trip")
    func stored() {
        #expect(AccountTag(stored: nil) == nil)
        #expect(AccountTag(stored: "") == nil)
        #expect(AccountTag(stored: "family") == nil)
        #expect(AccountTag(stored: "Work") == nil)   // the spelling is exact
        for tag in AccountTag.allCases { #expect(AccountTag(stored: tag.rawValue) == tag) }
        #expect(AccountTag.personal.label == "Personal" && AccountTag.work.label == "Work")
    }

    @Test("the filter's own labels and the memory of an unknown value")
    func labels() {
        #expect(SummaryFilter.allCases.map(\.label) == ["All", "Personal", "Work"])
        #expect(SummaryFilter(remembered: nil) == .all && SummaryFilter(remembered: "family") == .all)
        #expect(SummaryFilter(remembered: "work") == .work)
    }

    @Test("nothing tagged: every account shows under All whatever was remembered, in your order")
    func untagged() {
        let r = SummaryFilter.resolve(tags: [nil, nil, nil], remembered: "work")
        #expect(!r.controlShown && r.filter == .all && r.shown == [0, 1, 2] && r.segmentsEnabled == nil)
    }
}
