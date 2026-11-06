import Foundation

/// CCRM-103 (Hybrid Notification), the shape grammar (`design/2026-11-02-shape-grammar.html` rev D, frozen
/// 2026-11-02): the logic the iOS surfaces share — core §6.3's `markTime`, the tap readout's words (§2b, C4) and the
/// Appearance › Usage end mark (§1e, C1). Cases: `spec/core/fixtures/pace-distance/cases.json` → `markTime`,
/// `spec/mobile/fixtures/display/cases.json` → `markReadout`.
public enum MarkTime: Sendable, Equatable {
    /// Over the mark: the mark reaches the usage at `atMs`, `inMs` from now.
    case over(atMs: Int64, inMs: Int64)
    /// Under the mark: the mark was at the usage `agoMs` ago.
    case under(agoMs: Int64)
    /// Under a point apart.
    case on

    /// Any clocked window; nil when spent, with no reset clock (`msUntilReset` nil) or no reading. The side uses §6's
    /// elapsed percent clamped 0…100, as the caption's does.
    public static func of(usedPct: Double?, msUntilReset: Int64?, windowLengthMs: Int64, nowMs: Int64) -> MarkTime? {
        guard let usedPct, let msUntilReset, windowLengthMs > 0, !Pace.isSpent(usedPct) else { return nil }
        let elapsedPct = min(max(Double(windowLengthMs - msUntilReset) * 100 / Double(windowLengthMs), 0), 100)
        if Pace.onMark(usedPct: usedPct, elapsedPct: elapsedPct) { return .on }
        let startMs = nowMs + msUntilReset - windowLengthMs
        let atMs = startMs + Int64((usedPct / 100 * Double(windowLengthMs)).rounded())
        return usedPct > elapsedPct ? .over(atMs: atMs, inMs: atMs - nowMs) : .under(agoMs: nowMs - atMs)
    }
}

extension PaceText {

    /// The tap readout: "Pace catches up Thu 5 Nov, 12:50 PM" over the mark, "1d 4h behind pace" under it, "On the
    /// pace mark" within a point; nil — the tap does nothing — when `markTime` is nil.
    public static func markReadout(_ mt: MarkTime?, use24h: Bool, timeZone: TimeZone = .current,
                                   locale: Locale = .current) -> String? {
        switch mt {
        case nil: return nil
        case .on: return "On the pace mark"
        case .under(let ago): return "\(behind(ago)) behind pace"
        case .over(let at, _):
            let f = DateFormatter()
            f.locale = locale
            f.timeZone = timeZone
            f.dateFormat = use24h ? "EEE d MMM, HH:mm" : "EEE d MMM, h:mm a"
            return "Pace catches up " + f.string(from: Date(timeIntervalSince1970: Double(at) / 1000))
        }
    }

    /// "1d 4h", under a day "14h 57m", under an hour "57m"; every part rounded down.
    public static func behind(_ ms: Int64) -> String {
        let m = ms / 60_000
        let d = m / 1_440
        let h = m % 1_440 / 60
        if d > 0 { return "\(d)d \(h)h" }
        if h > 0 { return "\(h)h \(m % 60)m" }
        return "\(m)m"
    }
}

/// Appearance › Usage end mark (rev D §1e): `round` (E0, the pre-v2.0 cap) · `line` (E1, the default: the fill ends
/// square with a short line in its colour) · `pace` (E2b: that line green under the ±5 on-pace band, red over it, ink
/// inside it). An absent or unknown value reads as `line`.
public enum UsageEndMark: String, Sendable, Equatable, CaseIterable {
    case round, line, pace
    public static let `default` = UsageEndMark.line
    public init(key: String?) { self = key.flatMap(UsageEndMark.init(rawValue:)) ?? .default }

    /// The line's colour role: nil = no line (`round`); `.fill` = the fill's own colour; else the pace side.
    public enum Role: Sendable, Equatable { case fill, ink, under, over }

    public func role(usedPct: Double?, elapsedPct: Double?) -> Role? {
        switch self {
        case .round: return nil
        case .line: return .fill
        case .pace:
            guard let usedPct, let elapsedPct else { return .ink }
            if abs(Pace.pace(usedPct: usedPct, elapsedPct: elapsedPct)) <= 5 { return .ink }
            return elapsedPct > usedPct ? .under : .over
        }
    }
}
