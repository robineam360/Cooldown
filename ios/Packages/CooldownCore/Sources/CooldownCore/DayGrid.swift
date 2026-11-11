import Foundation

/// CCRM-107 (Day Grid), core §6.3 "Planned — v2.0 feedback": the grid under every 7-day chart, 5h chart and the Pace map.
/// Vertical lines at the window's start + k × (length / n), k = 1…n−1 (window-relative days or hours, not midnights, so
/// the n columns are equal); horizontal lines at k/n of 100%, so each crossing (k, k) sits on the even-pace diagonal.
/// The axis strip: the start label at the left edge, the ordinals 2…n−1 centred in their columns, the reset label at the
/// right edge. The column holding now is bold; an ordinal gives way to "now" or to an end label it would touch, and every
/// ordinal drops when a column is narrower than `dropBelowColumn`. Cases: `printed-v20.json` → `dayGrid`. Ink and sizes
/// are the family's (iOS: `line` at 60%, 0.5 pt; ordinals 9.5 pt).
public enum DayGrid {

    /// 7 for a weekly chart and the Pace map, 5 for a 5-hour chart.
    public static func columns(windowLengthMs: Int64) -> Int { windowLengthMs <= 5 * 3_600_000 ? 5 : 7 }

    /// The grid fractions k/n, k = 1…n−1: the verticals' x share of the window and the horizontals' share of 100%.
    public static func fractions(columns n: Int) -> [Double] { n > 1 ? (1..<n).map { Double($0) / Double(n) } : [] }

    /// The 1-based column holding now; start + k × length/n itself belongs to column k + 1. Clamped to 1…n.
    public static func nowColumn(columns n: Int, elapsedMs: Int64, windowLengthMs: Int64) -> Int {
        guard n > 0, windowLengthMs > 0 else { return 1 }
        // Integer arithmetic, so a now exactly on a column line is never pushed back by rounding.
        let k = Int((max(elapsedMs, 0) * Int64(n)) / windowLengthMs) + 1
        return min(max(k, 1), n)
    }

    public struct Ordinal: Sendable, Equatable {
        public let text: String
        /// 1-based; ordinals run 2…n−1.
        public let column: Int
        public let drawn: Bool
        /// The column's centre in the plot's own units (0…plotWidth).
        public let centre: Double
        public let bold: Bool
    }

    /// The ordinals and which of them draw, measured along the time axis (x on a chart running across; the label heights
    /// on a "Time down" chart, whose axis runs down). `now` is nil when the chart shows no "now" label; `nowColumn` still
    /// makes its ordinal bold.
    public static func ordinals(columns n: Int, plotWidth: Double, nowColumn: Int, nowAt: Double?,
                                startWidth: Double, endWidth: Double, ordinalWidth: Double, nowWidth: Double,
                                nowGap: Double = 6, endGap: Double = 4, dropBelowColumn: Double = 14) -> [Ordinal] {
        guard n > 2, plotWidth > 0 else { return [] }
        let col = plotWidth / Double(n)
        func gap(_ a: ClosedRange<Double>, _ b: ClosedRange<Double>) -> Double {
            max(a.lowerBound - b.upperBound, b.lowerBound - a.upperBound)
        }
        let startBox = 0...startWidth, endBox = (plotWidth - endWidth)...plotWidth
        let nowBox = nowAt.map { ($0 - nowWidth / 2)...($0 + nowWidth / 2) }
        return (2..<n).map { k in
            let c = (Double(k) - 0.5) * col
            let box = (c - ordinalWidth / 2)...(c + ordinalWidth / 2)
            var drawn = col >= dropBelowColumn
            if drawn, let nowBox, gap(box, nowBox) < nowGap { drawn = false }
            if drawn, gap(box, startBox) < endGap || gap(box, endBox) < endGap { drawn = false }
            return Ordinal(text: "\(k)", column: k, drawn: drawn, centre: c, bold: drawn && k == nowColumn)
        }
    }
}
