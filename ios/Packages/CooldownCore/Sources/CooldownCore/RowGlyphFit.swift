import Foundation

/// CCRM-115 (Row Tag Glyph): the glyph column's drop-out on the Summary's rows (addendum
/// design/2026-11-11-v20-narrow-duet-and-row-glyph-wireframes.html §Row glyph, BEHAVIOR-MOBILE §6). Pure widths in points,
/// so one rule decides for the whole list and a test can pin it.
///
/// Full rows: the name ellipsizes first, down to its floor (64 pt, or its own width when shorter); then the glyph column
/// leaves **every** row shown at once; then the name ellipsizes on. One-line rows: the bar shrinks to its floor (50 pt), then
/// the column leaves. It is decided on the tightest row shown.
public enum RowGlyphFit {
    /// A full row's glyph box (14 pt) and its gap to the provider mark (4 pt): 18 pt in all.
    public static let fullColumn = 18.0
    /// A one-line row's glyph (12 pt box) and its gap to the name (4 pt): 16 pt in all.
    public static let oneLineColumn = 16.0
    public static let nameFloor = 64.0
    public static let barFloor = 50.0

    /// One full row's measures: the width its line 1 has to share (the text column, right of the ring), the name's natural
    /// width, and everything else on line 1 (mark, plan chip, figure and the gaps between them).
    public struct FullRow: Sendable, Equatable {
        public let line: Double, name: Double, fixed: Double
        public init(line: Double, name: Double, fixed: Double) { self.line = line; self.name = name; self.fixed = fixed }
        /// The room the name would get beside the glyph column.
        public var nameRoomWithGlyph: Double { line - fixed - fullColumn }
    }

    /// One one-line row's measures: the row's width and everything on it except the bar and the glyph column.
    public struct OneLineRow: Sendable, Equatable {
        public let line: Double, fixed: Double
        public init(line: Double, fixed: Double) { self.line = line; self.fixed = fixed }
        public var barWithGlyph: Double { line - fixed - oneLineColumn }
    }

    /// True while every full row keeps its name at its floor beside the glyph column.
    public static func keepsGlyph(full rows: [FullRow]) -> Bool {
        rows.allSatisfy { $0.nameRoomWithGlyph >= min(nameFloor, $0.name) }
    }

    /// True while every one-line row keeps its bar at its floor beside the glyph column.
    public static func keepsGlyph(oneLine rows: [OneLineRow]) -> Bool {
        rows.allSatisfy { $0.barWithGlyph >= barFloor }
    }
}
