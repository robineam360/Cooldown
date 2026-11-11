import Foundation

/// CCRM-110 (Account Tags): every account is Personal or Work — chosen when it is added, changed from its menu — and the
/// Summary's All · Personal · Work filter (wireframes design/2026-11-10-v20-feedback-wireframes.html rev B §6 and the
/// 2026-11-11 addendum, BEHAVIOR-MOBILE §6). Pure, so `spec/mobile/fixtures/tags/cases-personal-work.json` tests every
/// rule without drawing.
public enum AccountTag: String, Sendable, Equatable, CaseIterable {
    case personal, work

    /// The stored spelling is each client's; iOS keeps `"personal"` / `"work"` in the account's additive `tag` field. A
    /// missing, empty or unknown value (an account an earlier build saved, a later build's tag read back after a downgrade)
    /// reads as Personal — there is no None (Robin, 2026-11-11) — and nothing is written at upgrade.
    public init(stored: String?) {
        self = stored.flatMap(AccountTag.init(rawValue:)) ?? .personal
    }

    public var label: String {
        switch self {
        case .personal: "Personal"
        case .work: "Work"
        }
    }

    /// VoiceOver's reading of a row's name: "Teams, Work account".
    public var accountWord: String { "\(label) account" }
}

/// The Summary filter. `all` shows every account; a tag shows only the accounts carrying it.
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
        /// The control shows whenever the Summary exists (two or more accounts); every account has a tag, so it needs no
        /// other condition.
        public let controlShown: Bool
        /// A segment with no accounts is disabled (All always has them); nil while the control is hidden.
        public let segmentsEnabled: [SummaryFilter: Bool]?
        /// The filter in force: the remembered one when it names an enabled segment, or All.
        public let filter: SummaryFilter
        /// Indices into the accounts passed in (your order), the accounts shown.
        public let shown: [Int]
        /// The one-line row form follows the rows shown, not the accounts.
        public let oneLineRows: Bool
        /// The row glyph column (CCRM-115 (Row Tag Glyph)) on the share snapshot: only while the rows shown carry both tags.
        public let snapshotGlyphs: Bool
        public var snapshotHeader: String { filter.snapshotHeader }
    }

    /// Applies the remembered filter to the accounts' tags (in your order). The control exists with the Summary — two or
    /// more accounts — and shows every account under All when it does not.
    public static func resolve(tags: [AccountTag], remembered: String?) -> Resolved {
        guard tags.count >= 2 else {
            return Resolved(controlShown: false, segmentsEnabled: nil, filter: .all, shown: Array(tags.indices),
                            oneLineRows: false, snapshotGlyphs: false)
        }
        let enabled: [SummaryFilter: Bool] = [
            .all: true,
            .personal: tags.contains(.personal),
            .work: tags.contains(.work),
        ]
        var filter = SummaryFilter(remembered: remembered)
        if enabled[filter] != true { filter = .all }
        let shown = tags.indices.filter { filter.tag == nil || tags[$0] == filter.tag }
        let both = Set(shown.map { tags[$0] }).count == 2
        return Resolved(controlShown: true, segmentsEnabled: enabled, filter: filter, shown: shown,
                        oneLineRows: shown.count >= oneLineFrom, snapshotGlyphs: both)
    }

    /// The memory rule (BEHAVIOR-MOBILE §6, the same on both clients): the value to store once the rule has run. A stored
    /// value the filter in force does not match — unknown, or naming a group with no accounts — becomes `all`; a missing
    /// value stays missing; and the rule runs only while the Summary exists (two or more accounts), otherwise the stored
    /// value is returned as it is. The caller evaluates it whenever the accounts, a tag or the Summary change and when the
    /// Summary comes on screen; tagging later never brings an earlier choice back because nothing else rewrites it.
    public static func memory(tags: [AccountTag], stored: String?) -> String? {
        guard tags.count >= 2, let stored else { return stored }
        return resolve(tags: tags, remembered: stored).filter.rawValue == stored ? stored : SummaryFilter.all.rawValue
    }
}
