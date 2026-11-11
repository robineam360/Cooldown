import Foundation
import Testing

@testable import CooldownCore

/// CCRM-110 (Account Tags) and CCRM-112 (Summary Snapshot) after Robin's 2026-11-11 change — every account Personal or
/// Work, a filter memory that falls back to All — read from `spec/mobile/fixtures/tags/cases-personal-work.json`
/// (BEHAVIOR-MOBILE §6). Step 2 folds that file into `cases.json`; this suite follows it.
@Suite("Account tags and the Summary filter (CCRM-110)")
struct AccountTagsTests {
    static let file = "tags/cases-personal-work.json"

    private func cases(_ key: String) throws -> [[String: Any]] {
        let list = try #require(try Fixtures.mobileJSON(Self.file)[key] as? [[String: Any]])
        #expect(list.count >= 5, "\(key): the fixture has its cases")
        return list
    }

    private func tags(_ accounts: [[String: Any]]) -> [AccountTag] { accounts.map { AccountTag(stored: $0.str("storedTag")) } }

    @Test("filter: the effective tags, control, enabled segments, the filter in force, rows shown, one-line form, header, stored value")
    func filter() throws {
        for c in try cases("filter") {
            let name = c.str("name") ?? "?"
            let accounts = try #require(c["accounts"] as? [[String: Any]])
            let names = accounts.compactMap { $0.str("name") }
            let tags = tags(accounts)
            let expected = try #require(c.dict("expected"))
            #expect(tags.map(\.rawValue) == (expected["effectiveTags"] as? [String]), "\(name): effective tags")
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
            // storedAfter is the value once the memory rule has run; the rule runs only while the Summary exists, so the
            // one-account case keeps its stored value (the fixture says so since d4d1598).
            #expect(SummaryFilter.memory(tags: tags, stored: c.str("rememberedFilter")) == expected.str("storedAfter"), "\(name): stored after")
        }
    }

    /// Replays a `memory` sequence against a store that holds the accounts and the one stored value, the way the app does:
    /// each change runs the rule; a segment can be chosen only while it is enabled.
    private struct Store {
        var accounts: [(name: String, tag: AccountTag)]
        var stored: String?
        var tags: [AccountTag] { accounts.map(\.tag) }
        mutating func rule() { stored = SummaryFilter.memory(tags: tags, stored: stored) }
        var filter: SummaryFilter { SummaryFilter.resolve(tags: tags, remembered: stored).filter }
    }

    @Test("memory: each sequence of tag, add, remove and choose steps leaves the filter in force and the stored value as listed")
    func memory() throws {
        for c in try cases("memory") {
            let name = c.str("name") ?? "?"
            let accounts = try #require(c["accounts"] as? [[String: Any]])
            var store = Store(accounts: zip(accounts.compactMap { $0.str("name") }, tags(accounts)).map { ($0, $1) },
                              stored: c.str("storedFilter"))
            let steps = try #require(c["steps"] as? [[String: Any]])
            let expected = try #require(c["expected"] as? [[String: Any]])
            #expect(steps.count == expected.count, "\(name)")
            for (i, step) in steps.enumerated() {
                switch step.str("do") {
                case "open": store.rule()
                case "tag":
                    let i = try #require(store.accounts.firstIndex { $0.name == step.str("account") })
                    store.accounts[i].tag = AccountTag(stored: step.str("tag"))
                    store.rule()
                case "add":
                    store.accounts.append((step.str("account") ?? "", AccountTag(stored: step.str("tag"))))
                    store.rule()
                case "remove":
                    store.accounts.removeAll { $0.name == step.str("account") }
                    store.rule()
                case "choose":
                    let want = SummaryFilter(remembered: step.str("filter"))
                    let enabled = SummaryFilter.resolve(tags: store.tags, remembered: nil).segmentsEnabled?[want] ?? true
                    if enabled { store.stored = want.rawValue }
                    store.rule()
                default: Issue.record("\(name): unknown step \(step)")
                }
                #expect(store.filter.rawValue == expected[i].str("filter"), "\(name) step \(i + 1): filter in force")
                #expect(store.stored == expected[i].str("stored"), "\(name) step \(i + 1): stored")
            }
        }
    }

    @Test("an absent, empty or unknown stored tag reads as Personal; the two known spellings round-trip")
    func stored() {
        #expect(AccountTag(stored: nil) == .personal)
        #expect(AccountTag(stored: "") == .personal)
        #expect(AccountTag(stored: "family") == .personal)
        #expect(AccountTag(stored: "Work") == .personal)   // the spelling is exact
        for tag in AccountTag.allCases { #expect(AccountTag(stored: tag.rawValue) == tag) }
        #expect(AccountTag.allCases == [.personal, .work], "no None")
        #expect(AccountTag.personal.label == "Personal" && AccountTag.work.label == "Work")
        #expect(AccountTag.work.accountWord == "Work account" && AccountTag.personal.accountWord == "Personal account")
    }

    @Test("the filter's own labels and the memory of an unknown value")
    func labels() {
        #expect(SummaryFilter.allCases.map(\.label) == ["All", "Personal", "Work"])
        #expect(SummaryFilter(remembered: nil) == .all && SummaryFilter(remembered: "family") == .all)
        #expect(SummaryFilter(remembered: "work") == .work)
    }

    @Test("the control exists from two accounts; one account has none and shows under All")
    func controlFromTwo() {
        let one = SummaryFilter.resolve(tags: [.work], remembered: "work")
        #expect(!one.controlShown && one.filter == .all && one.shown == [0] && one.segmentsEnabled == nil)
        let two = SummaryFilter.resolve(tags: [.personal, .personal], remembered: nil)
        #expect(two.controlShown && two.segmentsEnabled?[.work] == false && two.shown == [0, 1])
    }

    @Test("the snapshot draws the glyph column only while the rows shown carry both tags")
    func snapshotGlyphs() {
        let both: [AccountTag] = [.work, .personal, .work]
        #expect(SummaryFilter.resolve(tags: both, remembered: "all").snapshotGlyphs)
        #expect(!SummaryFilter.resolve(tags: both, remembered: "work").snapshotGlyphs, "a filter shows one tag")
        #expect(!SummaryFilter.resolve(tags: [.personal, .personal], remembered: nil).snapshotGlyphs, "an upgraded install")
        #expect(!SummaryFilter.resolve(tags: [.work, .work], remembered: nil).snapshotGlyphs)
    }

    @Test("the memory rule: unknown or empty-group values become all, a missing value stays missing, no Summary no rule")
    func memoryRule() {
        let mixed: [AccountTag] = [.work, .personal]
        #expect(SummaryFilter.memory(tags: mixed, stored: nil) == nil)
        #expect(SummaryFilter.memory(tags: mixed, stored: "family") == "all")
        #expect(SummaryFilter.memory(tags: mixed, stored: "work") == "work")
        #expect(SummaryFilter.memory(tags: [.personal, .personal], stored: "work") == "all")
        #expect(SummaryFilter.memory(tags: [.personal], stored: "work") == "work", "one account: no Summary, the rule waits")
        #expect(SummaryFilter.memory(tags: [], stored: "family") == "family")
    }
}

/// CCRM-115 (Row Tag Glyph): the drop-out rule, one decision for the whole list.
@Suite("Row glyph fit (CCRM-115)")
struct RowGlyphFitTests {
    @Test("a full row keeps the glyph while its name keeps 64 pt (or its own width); the tightest row decides")
    func full() {
        // line 266 (a 17e's text column), fixed 182 − 64 = 118 + a 40 pt chip: room with glyph = 266 − 158 − 18 = 90.
        let roomy = RowGlyphFit.FullRow(line: 266, name: 80, fixed: 158)
        #expect(roomy.nameRoomWithGlyph == 90 && RowGlyphFit.keepsGlyph(full: [roomy]))
        let tight = RowGlyphFit.FullRow(line: 266, name: 80, fixed: 190)   // room 58 < 64
        #expect(!RowGlyphFit.keepsGlyph(full: [tight]))
        #expect(!RowGlyphFit.keepsGlyph(full: [roomy, tight]), "the column leaves every row at once")
        // A short name's floor is its own width: "Lab" (22 pt) needs only 22.
        #expect(RowGlyphFit.keepsGlyph(full: [RowGlyphFit.FullRow(line: 266, name: 22, fixed: 224)]))   // room 24
        #expect(!RowGlyphFit.keepsGlyph(full: [RowGlyphFit.FullRow(line: 266, name: 22, fixed: 232)]))  // room 16
        #expect(RowGlyphFit.keepsGlyph(full: []))
    }

    @Test("a one-line row keeps the glyph while its bar keeps 50 pt")
    func oneLine() {
        #expect(RowGlyphFit.keepsGlyph(oneLine: [.init(line: 326, fixed: 240)]), "bar 70")
        #expect(RowGlyphFit.keepsGlyph(oneLine: [.init(line: 326, fixed: 260)]), "bar 50 exactly")
        #expect(!RowGlyphFit.keepsGlyph(oneLine: [.init(line: 326, fixed: 261)]))
        #expect(!RowGlyphFit.keepsGlyph(oneLine: [.init(line: 326, fixed: 240), .init(line: 326, fixed: 270)]))
        #expect(RowGlyphFit.fullColumn == 18 && RowGlyphFit.oneLineColumn == 16)
    }
}
