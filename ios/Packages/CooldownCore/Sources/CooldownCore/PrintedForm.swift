import Foundation

/// CCRM-104 (Pace Line Parity): core §6.3 "Printed form" — the strings every family prints for the pace numbers, so two
/// installs read the same. Cases: `spec/core/fixtures/pace-distance/printed.json`. Layout and sizes are the surface's.
public struct PaceLine: Sendable, Equatable, Hashable, Codable {
    public enum Side: String, Sendable, Equatable, Hashable, Codable { case under, over, on }

    public let side: Side
    /// The duration the mark is behind or ahead ("21h 50m"); nil on the mark.
    public let duration: String?
    /// The reset's weekday ("Sun"); nil when the reset falls later the same local day.
    public let weekday: String?
    /// The reset's clock in the device's time format ("9:30 pm", "09:30").
    public let clock: String

    public init(side: Side, duration: String?, weekday: String?, clock: String) {
        self.side = side
        self.duration = duration
        self.weekday = weekday
        self.clock = clock
    }

    /// What the tone tints: "21h 50m ahead", "6d 23h behind"; nil on the mark (the whole lead is plain ink).
    public var tinted: String? {
        guard let duration else { return nil }
        return duration + (side == .over ? " ahead" : " behind")
    }

    /// The line's first part: the tinted span, or "On the mark".
    public var lead: String { tinted ?? "On the mark" }

    /// The drop order everywhere a line cannot fit: the weekday goes first, then the clock; the duration and word are the
    /// floor and are never cut (Astra C1-4: each surface measures the scaled text per string).
    public enum Drop: Int, Sendable, Equatable, CaseIterable { case none, weekday, clock }

    /// The reset part at a drop level: "Sun 9:30 pm", "9:30 pm", or nil when the clock too is dropped.
    public func reset(_ drop: Drop = .none) -> String? {
        switch drop {
        case .none: return weekday.map { "\($0) \(clock)" } ?? clock
        case .weekday: return clock
        case .clock: return nil
        }
    }

    /// The whole line at a drop level, " · " between the lead and the reset.
    public func text(_ drop: Drop = .none) -> String {
        guard let r = reset(drop) else { return lead }
        return lead + PaceText.dot + r
    }

    /// The distinct forms, widest first (core §6.3 `paceLineForms`): a reset with no weekday has one fewer.
    public var forms: [String] {
        var out: [String] = []
        for d in Drop.allCases { let t = text(d); if out.last != t { out.append(t) } }
        return out
    }

    /// The same line from the printed pieces (the fixture's `side`, `durationMs`, `reset`).
    public static func text(side: Side, durationMs: Int64?, reset: String) -> String {
        if side == .on { return "On the mark" + PaceText.dot + reset }
        let d = PaceText.behind(durationMs ?? 0)
        return d + (side == .over ? " ahead" : " behind") + PaceText.dot + reset
    }
}

extension PaceText {

    /// The Vs pace figure, signed: "+19%", "−12%" (U+2212), "0%".
    public static func signed(_ d: Int) -> String {
        d == 0 ? "0%" : d > 0 ? "+\(d)%" : "\(minus)\(-d)%"
    }

    /// The same figure inside the 5-hour ring, unsigned: "19%", "12%" (the tone alone gives the side).
    public static func unsigned(_ d: Int) -> String { "\(abs(d))%" }

    /// The Vs pace figure every surface but a ring prints. With no figure (spent, no reset clock) the usage figure,
    /// unsigned; "—" with no reading.
    public static func signedVsPaceFigure(usedPct: Double?, elapsedPct: Double?) -> String {
        guard let usedPct else { return "—" }
        guard let d = Pace.vsPace(usedPct: usedPct, elapsedPct: elapsedPct) else { return "\(Int(usedPct))%" }
        return signed(d)
    }

    /// The pace line from the numbers: `MarkTime` for the side and the duration, `Fmt.dayClock` for the reset. nil with no
    /// readout (spent, no reset clock, no reading) — the surface's held, spent, signed-out or no-reading line instead.
    public static func paceLine(usedPct: Double?, resetMs: Int64?, windowLengthMs: Int64, nowMs: Int64, use24h: Bool,
                                timeZone: TimeZone = .current) -> PaceLine? {
        guard let resetMs,
              let mt = MarkTime.of(usedPct: usedPct, msUntilReset: resetMs - nowMs, windowLengthMs: windowLengthMs, nowMs: nowMs)
        else { return nil }
        let full = Fmt.dayClock(epochMs: resetMs, nowMs: nowMs, use24h: use24h, timeZone: timeZone)
        let parts = full.split(separator: " ", maxSplits: 1).map(String.init)
        // "Sun 9:30 pm" is weekday + clock; "9:30 pm" (later today) has no weekday. A 12-hour clock has a space too.
        let hasWeekday = !Fmt.sameDay(resetMs, nowMs, timeZone: timeZone)
        let weekday = hasWeekday ? parts.first : nil
        let clock = hasWeekday ? (parts.count > 1 ? parts[1] : full) : full
        switch mt {
        case .on: return PaceLine(side: .on, duration: nil, weekday: weekday, clock: clock)
        case .under(let ago): return PaceLine(side: .under, duration: behind(ago), weekday: weekday, clock: clock)
        case .over(_, let inMs): return PaceLine(side: .over, duration: behind(inMs), weekday: weekday, clock: clock)
        }
    }
}

extension PaceText {
    /// The caption's own drop order (Astra C1-3): the whole caption, then the distance clause alone — "13 over",
    /// "10 to spare", "on it", and "52% used" in Vs pace mode. Never cut mid-word.
    public static func captionShort(_ caption: String) -> String {
        guard let r = caption.range(of: dot) else { return caption }
        let head = String(caption[..<r.lowerBound]), tail = String(caption[r.upperBound...])
        return tail.hasSuffix("pace mark") ? head : tail
    }

    /// The Pace map's words (core §6.3 Printed form): names label the dots, never numbers.
    public enum MapWords {
        public static let aboveDiagonal = "ahead of pace"
        public static let belowDiagonal = "behind pace"
        public static let diagonal = "even pace"
        public static let xStart = "week start"
        public static let xEnd = "reset"
        /// Centred between the axis ends (kept on both families, core §6.3).
        public static let xCentre = "each account's own week"
    }
}
