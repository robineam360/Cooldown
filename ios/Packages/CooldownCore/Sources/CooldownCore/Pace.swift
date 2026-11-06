import Foundation

/// CCRM-97 (Pace Number): where a weekly-length window stands against even burn (spec core BEHAVIOR-SPEC §6.1), and
/// CCRM-96 (Weekly First)'s session gate (§6.2). Pure maths; the words are `PaceText`. Cases:
/// `spec/core/fixtures/pace/cases.json`. Weekly-length windows only — the 5-hour session keeps `Projection`'s line.
public enum Pace {

    /// §7.2's exhausted line: at or above this a window is spent, whatever the pace.
    public static let spentPct = 99.5

    static let dayMs = 86_400_000.0

    public enum Band: String, Sendable, Equatable, CaseIterable {
        case spent, onPace, roomToSpare, useBeforeReset, slowDown, nearlyOut
    }

    public enum Allowance: Sendable, Equatable {
        /// Under a day to the reset: what is left, and the time it has to last.
        case hours(pctLeft: Double, msLeft: Int64)
        /// The even daily rate that lands on exactly 100% at the reset.
        case perDay(pctPerDay: Double)
    }

    public static func isSpent(_ usedPct: Double?) -> Bool { usedPct.map { $0 >= spentPct } ?? false }

    /// `(r − q) ÷ max(r, q) × 100` with `q` quota left and `r` time left; bounded −100…+100, positive over pace.
    /// `q ≤ 0` is checked first.
    public static func pace(usedPct: Double, elapsedPct: Double) -> Double {
        let q = 100 - usedPct, r = 100 - elapsedPct
        if q <= 0 { return 100 }
        if r <= 0 { return -100 }
        return (r - q) / max(r, q) * 100
    }

    /// First match wins; `|pace| ≤ 5` and the ±50 edges are as §6.1's table.
    public static func band(usedPct: Double, elapsedPct: Double) -> Band {
        if usedPct >= spentPct { return .spent }
        let p = pace(usedPct: usedPct, elapsedPct: elapsedPct)
        if abs(p) <= 5 { return .onPace }
        if p < -50 { return .useBeforeReset }
        if p < -5 { return .roomToSpare }
        if p > 50 { return .nearlyOut }
        return .slowDown
    }

    /// The band, or nil when there is no verdict: no reading, or no reset instant (`elapsedPct` nil).
    public static func band(usedPct: Double?, elapsedPct: Double?) -> Band? {
        guard let usedPct, let elapsedPct else { return nil }
        return band(usedPct: usedPct, elapsedPct: elapsedPct)
    }

    public static func allowance(usedPct: Double, msUntilReset: Int64) -> Allowance? {
        if usedPct >= spentPct || msUntilReset <= 0 { return nil }
        let q = 100 - usedPct
        if Double(msUntilReset) < dayMs { return .hours(pctLeft: q, msLeft: msUntilReset) }
        return .perDay(pctPerDay: q / (Double(msUntilReset) / dayMs))
    }

    /// §6.2: the 5-hour session is the limit about to stop the user. A missing weekly reading reads as not spent; a
    /// spent week outranks the session.
    public static func sessionBinding(sessionPct: Double?, weeklyPct: Double?, sessionHitsLimitBeforeReset: Bool) -> Bool {
        guard let sessionPct else { return false }
        if isSpent(weeklyPct) { return false }
        return sessionPct > 90 || sessionHitsLimitBeforeReset
    }

    /// CCRM-100 (Vs Pace Display): `elapsed − used` rounded toward zero (core §6.3, Robin 2026-11-06: within a point of
    /// the mark is 0 on either side, matching "On the mark"); nil when the figure falls back to the usage (no reading,
    /// no reset clock, or spent).
    public static func vsPace(usedPct: Double?, elapsedPct: Double?) -> Int? {
        guard let usedPct, let elapsedPct, !isSpent(usedPct) else { return nil }
        let d = elapsedPct - usedPct
        let n = Int((abs(d) + edgeEpsilon).rounded(.down))
        return d < 0 ? -n : n
    }

    /// Added before every floor and half-up rounding of a percentage difference (spec mobile §5): binary floats put
    /// `2.3 − 0.3` at 1.9999999999999998, which would floor to 1 (the display cases pin "+2%").
    public static let edgeEpsilon = 1e-9
}

/// CCRM-96 (Weekly First): the per-account Headline — which window a surface leads with (spec mobile §5). Cases:
/// `spec/mobile/fixtures/headline/cases.json`.
public enum Headline: String, Sendable, Equatable, CaseIterable, Codable {
    case weekly, fiveHour, auto
    public static let `default` = Headline.weekly
    public init(key: String?) { self = key.flatMap(Headline.init(rawValue:)) ?? .default }
}

public enum WindowKind: String, Sendable, Equatable {
    case weekly, session
}

public enum HeadlineSurface: String, Sendable, Equatable {
    /// Android's pinned notification; on iOS every face with no window choice of its own that follows the Headline
    /// live — the Overview big number, the iPad sidebar's Headline segment, the lock-screen circular and inline.
    case notification
    /// One window and no room for a companion: Weekly behaves as Auto here.
    case statusGauge
    /// A widget's window choice when it is placed; stored per widget afterwards.
    case newWidget
    /// The app's default card order (a layout reordered in the sheet wins).
    case cardOrder
}

public enum HeadlineRule {
    /// The window `surface` leads with. An account without a session window always leads with Weekly.
    public static func lead(setting: Headline, surface: HeadlineSurface, hasSession: Bool, sessionBinding: Bool) -> WindowKind {
        guard hasSession else { return .weekly }
        let auto: WindowKind = sessionBinding ? .session : .weekly
        switch surface {
        case .notification:
            switch setting {
            case .weekly: return .weekly
            case .fiveHour: return .session
            case .auto: return auto
            }
        case .statusGauge:
            return setting == .fiveHour ? .session : auto
        case .newWidget, .cardOrder:
            return setting == .fiveHour ? .session : .weekly
        }
    }
}

/// CCRM-98 (Summary Tab): the All tab's Weekly list, most room first (spec mobile §6). Cases:
/// `spec/mobile/fixtures/summary/cases.json`.
public enum SummaryRank {

    public enum Group: String, Sendable, Equatable {
        case eligible, held, spent, noReading
    }

    public struct Entry: Sendable, Equatable {
        public let key: String
        /// Weekly used; nil = no reading.
        public let usedPct: Double?
        /// §6's elapsedPercent of the weekly window; nil = no reset clock.
        public let elapsedPct: Double?
        /// Weekly reset minus now; nil = no reset clock.
        public let msUntilReset: Int64?
        /// Core §6.2's predicate, already evaluated.
        public let sessionBinding: Bool
        /// 5h reset minus now; nil = no clock.
        public let sessionMsUntilReset: Int64?

        public init(key: String, usedPct: Double?, elapsedPct: Double?, msUntilReset: Int64?, sessionBinding: Bool,
                    sessionMsUntilReset: Int64?) {
            self.key = key
            self.usedPct = usedPct
            self.elapsedPct = elapsedPct
            self.msUntilReset = msUntilReset
            self.sessionBinding = sessionBinding
            self.sessionMsUntilReset = sessionMsUntilReset
        }
    }

    public struct Ranked: Sendable, Equatable {
        public let entry: Entry
        public let group: Group
    }

    /// `entries` in tab order; the result is display order.
    public static func rank(_ entries: [Entry]) -> [Ranked] {
        let read = entries.filter { $0.usedPct != nil }
        let spent = read.filter { Pace.isSpent($0.usedPct) }
        let live = read.filter { !Pace.isSpent($0.usedPct) }
        // No reset clock means no Pace: such a row sorts after every clocked one.
        func pace(_ e: Entry) -> Double { e.elapsedPct.map { Pace.pace(usedPct: e.usedPct ?? 0, elapsedPct: $0) } ?? .greatestFiniteMagnitude }
        let eligible = stable(live.filter { !$0.sessionBinding }) { a, b in
            let pa = pace(a), pb = pace(b)
            if pa != pb { return pa < pb }
            return (a.msUntilReset ?? .max) < (b.msUntilReset ?? .max)
        }
        let held = stable(live.filter(\.sessionBinding)) { ($0.sessionMsUntilReset ?? .max) < ($1.sessionMsUntilReset ?? .max) }
        let spentSorted = stable(spent) { ($0.msUntilReset ?? .max) < ($1.msUntilReset ?? .max) }
        return eligible.map { Ranked(entry: $0, group: .eligible) }
            + held.map { Ranked(entry: $0, group: .held) }
            + spentSorted.map { Ranked(entry: $0, group: .spent) }
            + entries.filter { $0.usedPct == nil }.map { Ranked(entry: $0, group: .noReading) }
    }

    /// A stable sort: equal rows keep tab order.
    private static func stable(_ items: [Entry], by less: (Entry, Entry) -> Bool) -> [Entry] {
        items.enumerated().sorted { a, b in
            if less(a.element, b.element) { return true }
            if less(b.element, a.element) { return false }
            return a.offset < b.offset
        }.map(\.element)
    }

    /// Appearance › Summary order (Robin, 2026-11-02; spec mobile §6): the list in `order`. Every row keeps the group
    /// `rank` gives it; only the order changes. `entries` are in your order. Cases: `summary/cases.json` → `order`.
    public static func ordered(_ entries: [Entry], _ order: SummaryOrder) -> [Ranked] {
        let ranked = rank(entries)
        if order == .room { return ranked }
        let group = Dictionary(uniqueKeysWithValues: ranked.map { ($0.entry.key, $0.group) })
        let sorted: [Entry]
        switch order {
        case .yours, .room: sorted = entries
        // Stable: ties keep your order; no clock / no reading go last, in your order.
        case .resets: sorted = stable(entries) { ($0.msUntilReset ?? .max) < ($1.msUntilReset ?? .max) }
        case .used: sorted = stable(entries) { ($0.usedPct ?? -.infinity) > ($1.usedPct ?? -.infinity) }
        }
        return sorted.map { Ranked(entry: $0, group: group[$0.key] ?? .noReading) }
    }

    /// The All tab exists with two or more accounts; so does its Appearance toggle.
    public static func tabShown(accountCount: Int) -> Bool { accountCount >= 2 }

    /// True = launch opens the All tab; false = the last account looked at, as before.
    public static func opensOnSummary(accountCount: Int, openOnSummary: Bool) -> Bool {
        tabShown(accountCount: accountCount) && openOnSummary
    }
}

/// CCRM-96 (Weekly First): the one-time "Weekly now leads" notice, owed only to installs that had an account before
/// v2.0. Stored as one string, so a v1.9 downgrade ignores it.
public enum WeeklyFirstNotice {
    /// Owed: shown on the next app open until answered, then `done`.
    public static let pending = "pending"
    public static let done = "done"

    /// The value written at the first v2.0 launch.
    public static func settle(hadAccountsBefore: Bool) -> String { hadAccountsBefore ? pending : done }
}

/// Appearance › Summary order (Robin, 2026-11-02): `yours` (default) the account order; `room` the §6 ranking;
/// `resets` the Weekly that resets next first; `used` the highest Weekly first. An absent or unknown value is `yours`.
public enum SummaryOrder: String, Sendable, Equatable, CaseIterable {
    case yours, room, resets, used
    public static let `default` = SummaryOrder.yours
    public init(key: String?) { self = key.flatMap(SummaryOrder.init(rawValue:)) ?? .default }

    public var label: String {
        switch self {
        case .yours: "Your order"
        case .room: "Most room first"
        case .resets: "Resets soonest"
        case .used: "Most used"
        }
    }

    /// How the list's header names it.
    public var header: String { label.lowercased() }
}
