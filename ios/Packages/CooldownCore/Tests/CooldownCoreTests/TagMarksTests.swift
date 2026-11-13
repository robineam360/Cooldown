import Foundation
import Testing

@testable import CooldownCore

/// CCRM-118 (Header Glyphs): the Tag marks rule, read from `spec/mobile/fixtures/tags/cases.json` → `marks`.
@Suite("Tag marks (CCRM-118)")
struct TagMarksTests {
    @Test("the column, each row's slot and the snapshot column follow the two switches")
    func marks() throws {
        let list = try #require(try Fixtures.mobileJSON(AccountTagsTests.file)["marks"] as? [[String: Any]])
        #expect(list.count >= 5)
        for c in list {
            let name = c.str("name") ?? "?"
            let rows = try #require(c["rows"] as? [String]).map { AccountTag(stored: $0) }
            let m = try #require(c.dict("tagMarks"))
            let marks = TagMarks(personal: m.flag("personal") ?? true, work: m.flag("work") ?? true)
            let expected = try #require(c.dict("expected"))
            let column = marks.column(rows: rows)
            #expect(column == expected.flag("column"), "\(name): column")
            let slots: [String]? = column ? rows.map { marks.shows($0) ? "glyph" : "blank" } : nil
            #expect(slots == (expected["slots"] as? [String]), "\(name): slots")
            #expect(marks.snapshotColumn(rows: rows) == expected.flag("snapshotColumn"), "\(name): snapshot")
        }
    }
}
