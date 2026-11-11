import Foundation

/// CCRM-109 (Quiet Accounts) and CCRM-111 (Map Layers), core §6.3 "Planned — v2.0 feedback" and the mobile foot line
/// (wireframe design/2026-11-10-v20-feedback-wireframes.html rev B §5 and §7). Pure, so the fixtures
/// (`printed-v20.json` `quiet`, `mapLabels.figures`, `callout`, `projectionLayer`; `spec/mobile/fixtures/map-foot`) test
/// every rule without drawing.
public enum PaceMapRules {

    // MARK: CCRM-109 (Quiet Accounts)

    /// The row's printed Weekly used figure: a window row truncates (`Fmt.windowPercentDisplay`), so 0.99 prints "0%".
    public static func rowPrints(_ usedPct: Double?) -> String? {
        usedPct.map { "\(Fmt.windowPercentDisplay($0))%" }
    }

    /// Whether the row prints "0%" Weekly used: the account leaves the map (the list still shows it).
    public static func isQuiet(_ usedPct: Double?) -> Bool {
        usedPct.map { Fmt.windowPercentDisplay($0) < 1 } ?? false
    }

    /// On the map: a reading whose row prints 1% or more, with a reset clock (the x).
    public static func isPlotted(usedPct: Double?, clocked: Bool) -> Bool {
        usedPct != nil && clocked && !isQuiet(usedPct)
    }

    public struct Account: Sendable, Equatable {
        public let usedPct: Double?
        public let clocked: Bool
        public init(usedPct: Double?, clocked: Bool) {
            self.usedPct = usedPct
            self.clocked = clocked
        }
    }

    public struct Foot: Sendable, Equatable {
        /// "each account's own week".
        public let left: String
        /// "2 at 0% · 1 no reading"; nil when nothing is left off.
        public let count: String?
        /// Every account at 0%: the message in the behind wedge.
        public let empty: Empty?
    }

    public struct Empty: Sendable, Equatable {
        public let title: String
        public let detail: String
        public static let message = Empty(title: "Nothing to plot yet", detail: "every account is at 0% this week")
    }

    /// The foot line (mobile): readings that print 0% are counted "N at 0%" (a not-started week included); accounts
    /// with no reading — and the rare reading of 1% or more with no reset clock, which has no x — "N no reading"; each
    /// part left out at zero. The message shows when every account is at 0% (rev B draws no other empty map).
    public static func foot(_ accounts: [Account]) -> Foot {
        let atZero = accounts.filter { isQuiet($0.usedPct) }.count
        let noReading = accounts.filter { a in a.usedPct == nil || (!isQuiet(a.usedPct) && !a.clocked) }.count
        let plotted = accounts.filter { isPlotted(usedPct: $0.usedPct, clocked: $0.clocked) }.count
        let parts = [atZero > 0 ? "\(atZero) at 0%" : nil, noReading > 0 ? "\(noReading) no reading" : nil].compactMap { $0 }
        return Foot(left: PaceText.MapWords.xCentre, count: parts.isEmpty ? nil : parts.joined(separator: PaceText.dot),
                    empty: plotted == 0 && atZero > 0 && noReading == 0 ? .message : nil)
    }

    // MARK: CCRM-111 (Map Layers): the figure after the name

    public enum FigureMode: String, Sendable, Equatable { case used, left, vs }

    /// The figure the Figures layer prints after a name: "52%", "48% left", "−13%" / "+19%" / "0%" (Vs pace, signed,
    /// falling back to Used with no reset clock). Used and Left truncate like the row; spent prints "100%", or "0%" in
    /// Left (as drawn, no "left"). nil with no reading.
    public static func figure(mode: FigureMode, usedPct: Double?, elapsedPct: Double?) -> String? {
        guard let used = usedPct else { return nil }
        if Pace.isSpent(used) { return mode == .left ? "0%" : "100%" }
        if mode == .vs, let d = Pace.vsPace(usedPct: used, elapsedPct: elapsedPct) { return PaceText.signed(d) }
        if mode == .left { return "\(max(0, Fmt.windowPercentDisplay(100 - used)))% left" }
        return "\(Fmt.windowPercentDisplay(used))%"
    }

    /// "Teams 52%", the name and its figure as one label.
    public static func label(name: String, mode: FigureMode, usedPct: Double?, elapsedPct: Double?) -> String? {
        figure(mode: mode, usedPct: usedPct, elapsedPct: elapsedPct).map { "\(name) \($0)" }
    }

    /// Which colour the figure takes: the severity colour for Used and Left (and the Vs pace fallback), green under,
    /// red over, ink on the mark.
    public static func figureTone(mode: FigureMode, usedPct: Double?, elapsedPct: Double?) -> PaceText.VsTone {
        guard mode == .vs, let used = usedPct, !Pace.isSpent(used) else { return .usage }
        return PaceText.vsPaceTone(usedPct: used, elapsedPct: elapsedPct)
    }

    // MARK: CCRM-111: the callout's third line and the projection layer

    /// Whether the account gets a forecast at all: an estimate, not spent, with a reset clock.
    static func forecastable(_ estimate: Projection.Estimate?, usedPct: Double?, resetMs: Int64?) -> Projection.Estimate? {
        guard let estimate, resetMs != nil, !Pace.isSpent(usedPct) else { return nil }
        return estimate
    }

    /// The callout's third line: "~74% at reset" (rounded half up), or "100% at Wed 6:10 PM" when it runs out before
    /// the reset (the weekday dropped later the same local day). nil — no third line — with no estimate, spent, or
    /// no reset clock.
    public static func forecast(estimate: Projection.Estimate?, usedPct: Double?, resetMs: Int64?, nowMs: Int64,
                                use24h: Bool, timeZone: TimeZone = .current) -> String? {
        guard let e = forecastable(estimate, usedPct: usedPct, resetMs: resetMs), let reset = resetMs else { return nil }
        if let hit = e.hitsLimitAtMs, hit < reset {
            return "100% at \(Fmt.dayClock(epochMs: hit, nowMs: nowMs, use24h: use24h, timeZone: timeZone))"
        }
        return "~\(PaceText.halfUp(e.pctAtReset))% at reset"
    }

    /// Whether the forecast line (and the ring) is the red "runs out" one.
    public static func runsOut(estimate: Projection.Estimate?, usedPct: Double?, resetMs: Int64?) -> Bool {
        guard let e = forecastable(estimate, usedPct: usedPct, resetMs: resetMs), let reset = resetMs,
              let hit = e.hitsLimitAtMs else { return false }
        return hit < reset
    }

    public struct ProjectionEnd: Sendable, Equatable {
        /// Where the tail ends, in the map's own units (share of the week gone, used %).
        public let weekGonePct: Double
        public let usedPct: Double
        /// Runs out before the reset: the ring sits on the 100% line, ring and tail end red.
        public let runsOut: Bool
    }

    /// The Projection layer's tail end: a hollow ring on the right edge at the projected %, or on the 100% line at the
    /// week-gone % where it runs out. nil — no tail, no ring — with no estimate, spent, or no reset clock.
    public static func projection(estimate: Projection.Estimate?, usedPct: Double?, resetMs: Int64?,
                                  windowLengthMs: Int64) -> ProjectionEnd? {
        guard let e = forecastable(estimate, usedPct: usedPct, resetMs: resetMs), let reset = resetMs else { return nil }
        if let hit = e.hitsLimitAtMs, hit < reset {
            let start = reset - windowLengthMs
            return ProjectionEnd(weekGonePct: Double(hit - start) / Double(windowLengthMs) * 100, usedPct: 100, runsOut: true)
        }
        return ProjectionEnd(weekGonePct: 100, usedPct: min(100, e.pctAtReset), runsOut: false)
    }
}
