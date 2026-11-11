import Foundation

/// CCRM-110 (Account Tags): an optional tag on an account — None, Personal or Work — and the Summary's All · Personal ·
/// Work filter (wireframe design/2026-11-10-v20-feedback-wireframes.html rev B §6, BEHAVIOR-MOBILE §6). Pure, so
/// `spec/mobile/fixtures/tags/cases.json` tests every rule without drawing.
public enum AccountTag: String, Sendable, Equatable, CaseIterable {
    case personal, work

    /// The stored spelling is each client's; iOS keeps `"personal"` / `"work"` in the account's additive `tag` field, and
    /// an absent, empty or unknown value (a later build's tag, read back after a downgrade) reads as None.
    public init?(stored: String?) {
        guard let stored, let tag = AccountTag(rawValue: stored) else { return nil }
        self = tag
    }

    public var label: String {
        switch self {
        case .personal: "Personal"
        case .work: "Work"
        }
    }
}

/// The Summary filter. `all` shows every account; a tag shows only the accounts carrying it (untagged accounts show only
/// under All).
public enum SummaryFilter: String, Sendable, Equatable, CaseIterable {
    case all, personal, work

    /// A remembered value that is absent or unknown reads as All.
    public init(remembered: String?) { self = remembered.flatMap(SummaryFilter.init(rawValue:)) ?? .all }

    public var tag: AccountTag? { AccountTag(rawValue: rawValue) }

    public var label: String {
        switch self {
        case .all: "All"
        case .personal: "Personal"
        case .work: "Work"
        }
    }

    /// The share snapshot's header under this filter.
    public var snapshotHeader: String {
        switch self {
        case .all: "All accounts"
        case .personal: "Personal accounts"
        case .work: "Work accounts"
        }
    }

    /// From this many rows shown the list (and the snapshot) go to one line each.
    public static let oneLineFrom = 6

    public struct Resolved: Sendable, Equatable {
        /// The control shows once any account has a tag.
        public let controlShown: Bool
        /// A segment with no accounts is disabled (All always has them); nil while the control is hidden.
        public let segmentsEnabled: [SummaryFilter: Bool]?
        /// The filter in force: the remembered one, or All when it is unknown, disabled or has no accounts.
        public let filter: SummaryFilter
        /// Indices into the accounts passed in (your order), the accounts shown.
        public let shown: [Int]
        /// The one-line row form follows the rows shown, not the accounts.
        public let oneLineRows: Bool
        public var snapshotHeader: String { filter.snapshotHeader }
    }

    /// Applies the remembered filter to the accounts' tags (in your order). The control hides when nothing is tagged, and
    /// everything then shows under All whatever was remembered.
    public static func resolve(tags: [AccountTag?], remembered: String?) -> Resolved {
        let anyTag = tags.contains { $0 != nil }
        guard anyTag else {
            return Resolved(controlShown: false, segmentsEnabled: nil, filter: .all, shown: Array(tags.indices),
                            oneLineRows: tags.count >= oneLineFrom)
        }
        let enabled: [SummaryFilter: Bool] = [
            .all: !tags.isEmpty,
            .personal: tags.contains(.personal),
            .work: tags.contains(.work),
        ]
        var filter = SummaryFilter(remembered: remembered)
        if enabled[filter] != true { filter = .all }
        let shown = tags.indices.filter { filter.tag == nil || tags[$0] == filter.tag }
        return Resolved(controlShown: true, segmentsEnabled: enabled, filter: filter, shown: shown,
                        oneLineRows: shown.count >= oneLineFrom)
    }
}
