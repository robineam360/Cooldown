import Foundation

/// CCRM-118 (Header Glyphs), approved 2026-11-13 (`design/2026-11-13-ccrm118-header-glyphs.html` §2, §9, §10;
/// BEHAVIOR-MOBILE §6 "Tag marks"): the Appearance setting that shows or hides each tag's mark — the person / briefcase
/// badge on account icons, the Summary row glyph, the account header, the snapshot column. Stored as
/// `tagMarks.personal` / `tagMarks.work`; absent reads as on. The filter and the Settings chip never read it.
public struct TagMarks: Sendable, Equatable {
    public var personal: Bool
    public var work: Bool
    public init(personal: Bool = true, work: Bool = true) { self.personal = personal; self.work = work }

    public static let personalKey = "tagMarks.personal"
    public static let workKey = "tagMarks.work"

    /// Whether `tag`'s mark is drawn.
    public func shows(_ tag: AccountTag) -> Bool { tag == .work ? work : personal }

    /// The Summary's glyph column exists while any row shown carries a marked tag; then `RowGlyphFit` decides as before.
    public func column(rows: [AccountTag]) -> Bool { rows.contains(where: shows) }

    /// The share snapshot's column: the rows carry both tags and either is marked.
    public func snapshotColumn(rows: [AccountTag]) -> Bool {
        rows.contains(.personal) && rows.contains(.work) && column(rows: rows)
    }
}
