import Foundation

/// CCRM-97 (Pace Number) and CCRM-100 (Vs Pace Display): the words for `Pace`'s numbers (spec mobile BEHAVIOR-MOBILE
/// §5). Cases: `spec/mobile/fixtures/display/cases.json`. The signed Pace is never printed.
public enum PaceText {

    public static let minus = "\u{2212}"
    public static let dot = " \u{00B7} "

    /// The global Used / Left / Vs pace token (CCRM-22 (Used or Left) gained a third value).
    public enum Mode: String, Sendable, Equatable, CaseIterable {
        case used, left, vsPace
    }

    /// nil for `.spent`: a spent window says "Out until <reset>" instead.
    public static func bandWords(_ band: Pace.Band) -> String? {
        switch band {
        case .onPace: "On pace"
        case .roomToSpare: "Room to spare"
        case .useBeforeReset: "Use before reset"
        case .slowDown: "Slow down"
        case .nearlyOut: "Nearly out"
        case .spent: nil
        }
    }

    /// Over pace: the bands an in-app line reddens (iOS reddens the whole line, spec mobile §5).
    public static func isOverPace(_ band: Pace.Band) -> Bool { band == .slowDown || band == .nearlyOut }

    /// "≈11%/day", "<1%/day", "40% left, 6h", or "40% left, <1h" under 30 minutes.
    public static func allowance(_ a: Pace.Allowance) -> String {
        switch a {
        case .perDay(let rate): rate < 1 ? "<1%/day" : "≈\(Int(rate.rounded()))%/day"
        case .hours(let left, let ms):
            ms < 30 * 60_000 ? "\(Int(left.rounded()))% left, <1h"
                : "\(Int(left.rounded()))% left, \(Int((Double(ms) / 3_600_000).rounded()))h"
        }
    }

    /// The tight form: "Slow down · ≈11%/day"; the band alone when there is no allowance; nil when spent.
    public static func tight(_ band: Pace.Band, _ allowance: Pace.Allowance?) -> String? {
        guard let words = bandWords(band) else { return nil }
        guard let allowance else { return words }
        return words + dot + Self.allowance(allowance)
    }

    /// The figure in Vs pace mode, unsigned since the shape grammar (rev D §7b, CCRM-103 (Hybrid Notification)):
    /// "10%", "20%", "0%" — `vsPaceTone` carries the side as a colour. Falls back to the Used figure when spent or
    /// unclocked, "—" with no reading.
    public static func vsPaceFigure(usedPct: Double?, elapsedPct: Double?) -> String {
        guard let usedPct else { return "—" }
        guard let d = Pace.vsPace(usedPct: usedPct, elapsedPct: elapsedPct) else { return "\(Int(usedPct))%" }
        return "\(abs(d))%"
    }

    /// Which colour a Vs pace figure takes: green under the mark, red over it, ink on it; `.usage` = the fallback
    /// usage figure in its severity colours.
    public enum VsTone: String, Sendable, Equatable { case under, over, on, usage }

    public static func vsPaceTone(usedPct: Double?, elapsedPct: Double?) -> VsTone {
        guard let d = Pace.vsPace(usedPct: usedPct, elapsedPct: elapsedPct) else { return .usage }
        return d > 0 ? .under : d < 0 ? .over : .on
    }

    static func halfUp(_ x: Double) -> Int { Int((x + 0.5 + Pace.edgeEpsilon).rounded(.down)) }

    /// The pace mark's own value, as the caption names it.
    public static func markPct(_ elapsedPct: Double) -> Int { halfUp(elapsedPct) }

    /// The V1 caption under an in-app bar: "60% pace mark · 10 under" (Used), "40% pace mark · 10 to spare" (Left),
    /// "52% used · 39% pace mark" (Vs pace, core §6.3 since 2026-11-06). nil — no caption — when spent, unclocked or unread.
    public static func caption(mode: Mode, usedPct: Double?, elapsedPct: Double?) -> String? {
        guard let usedPct, let elapsedPct, !Pace.isSpent(usedPct) else { return nil }
        let m = markPct(elapsedPct)
        if mode == .vsPace { return "\(Int(usedPct))% used\(dot)\(m)% pace mark" }
        let d = elapsedPct - usedPct
        let n = halfUp(abs(d))
        let words: String
        if Pace.onMark(usedPct: usedPct, elapsedPct: elapsedPct) { words = "on it" }
        else if d > 0 { words = mode == .left ? "\(n) to spare" : "\(n) under" }
        else { words = "\(n) over" }
        return "\(mode == .left ? 100 - m : m)% pace mark\(dot)\(words)"
    }

    /// The Vs pace line where a figure has no caption under it (Live Activity, Dynamic Island): "52% used (mark 39%)".
    public static func usedAndMark(usedPct: Double?, elapsedPct: Double?) -> String? {
        guard let usedPct, let elapsedPct, !Pace.isSpent(usedPct) else { return nil }
        return "\(Int(usedPct))% used (mark \(markPct(elapsedPct))%)"
    }

    /// True = the caption starts `gap` right of the mark; false = it ends `gap` left of it.
    public static func captionRightOfMark(barWidth: Double, markPct: Double, captionWidth: Double, gap: Double = 5) -> Bool {
        barWidth * markPct / 100 + gap + captionWidth <= barWidth
    }
}
