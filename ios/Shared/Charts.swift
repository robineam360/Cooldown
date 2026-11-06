import CooldownCore
import SwiftUI

/// CCRM-94 (iOS App): the usage bar with its pace tick. Above pace (more than 3 points over the even-pace
/// line, spec core §5) the part past the tick is red, with a straight edge at the tick (spec core §6).
struct UsageBar: View {
    let pct: Double?
    let pace: Double?
    let accent: Color
    var height: CGFloat = 8
    var dimmed = false
    /// CCRM-97 (Pace Number) V1, in-app bars only: under pace, the room between the fill and the mark is drawn as a
    /// dashed outline (spec mobile §5). Widgets have no caption and keep the plain bar.
    var showsRoom = false
    @Environment(\.palette) private var p

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let value = min(max(pct ?? 0, 0), 100)
            let fill = pct == nil || value == 0 ? 0 : max(w * value / 100, height)
            let colour = p.severity(pct, accent: accent)
            // "Show red past the pace mark" off keeps the tick but not the red (Android's switch).
            let over = Display.redPastPace && (pace.map { (pct ?? 0) < 100 && (pct ?? 0) > $0 + 3 } ?? false)
            ZStack(alignment: .leading) {
                Capsule().fill(accent.opacity(0.26))
                if over, let pace {
                    let pw = w * pace / 100
                    UnevenRoundedRectangle(topLeadingRadius: height / 2, bottomLeadingRadius: height / 2)
                        .fill(colour).frame(width: pw)
                    UnevenRoundedRectangle(bottomTrailingRadius: height / 2, topTrailingRadius: height / 2)
                        .fill(p.red).frame(width: max(fill - pw, 0)).offset(x: pw)
                } else if fill > 0 {
                    Capsule().fill(colour).frame(width: fill)
                }
                if showsRoom, let pace, let pct, pct < pace - 1, !Pace.isSpent(pct) {
                    let from = max(fill, 0), to = w * pace / 100
                    if to - from > 2 {
                        Rectangle()
                            .strokeBorder(p.sub.opacity(0.8), style: StrokeStyle(lineWidth: 1, dash: [3, 2]))
                            .frame(width: to - from, height: height)
                            .offset(x: from)
                    }
                }
                if let pace, pct != nil {
                    let ext = max(3, height * 0.45)
                    RoundedRectangle(cornerRadius: 1.5).fill(p.tick)
                        .frame(width: 3, height: height + 2 * ext)
                        .offset(x: min(w - 3, max(0, w * pace / 100 - 1.5)))
                }
            }
            .frame(height: height)
            .frame(maxHeight: .infinity)
        }
        .frame(height: height + 2 * max(3, height * 0.45))
        .opacity(dimmed ? 0.45 : 1)
        .accessibilityHidden(true)
    }
}

/// The trend chart as shipped on Android (ui/Sparkline.kt) and drawn in the approved wireframe: 80/90/100
/// guides with labels, the even-pace diagonal and its legend, the samples with dots, the "now" marker,
/// the dashed projection with its hollow end, and the time axis. Nothing removed.
struct TrendChart: View {
    struct Model: Equatable {
        /// (x in 0…1 of the window, percent)
        var samples: [CGPoint]
        var nowX: Double?
        /// Projected percent at the reset; nil = no projection. ≥ 100 stops at the crossing, unlabelled.
        var projection: Double?
        var over: Bool
        var startLabel: String
        var endLabel: String
    }
    let model: Model
    let accent: Color
    var dimmed = false
    /// CCRM-75 (Chart Height) Small: guides, pace, curve, now and projection only — no labels; the lines under the
    /// chart carry the numbers.
    var linesOnly = false
    /// Chart orientation "Time down" (Android's CCRM-75 option): usage runs left to right like the bar above it, and
    /// time runs top to bottom, with the times down the right edge.
    var down = Display.chartDown
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    /// CCRM-94 (iOS App) parity wireframes rev B §5, Android's curve fill: the area under the curve split at the even-pace
    /// diagonal (y = 100x in the chart's own units, x in 0…1 of the window, y in percent). `below` is the area under
    /// whichever is lower, the curve or the diagonal; `above` holds one polygon per stretch where the curve runs past the
    /// diagonal — the overshoot — between the curve and the diagonal. No sample past the diagonal gives no `above`.
    nonisolated static func splitFill(_ samples: [CGPoint]) -> (below: [CGPoint], above: [[CGPoint]]) {
        guard samples.count >= 2 else { return (samples, []) }
        func gap(_ q: CGPoint) -> Double { Double(q.y) - 100 * Double(q.x) }
        var seq: [CGPoint] = [samples[0]]
        for i in 1..<samples.count {
            let a = samples[i - 1], b = samples[i]
            let da = gap(a), db = gap(b)
            if da * db < 0 {
                let t = CGFloat(da / (da - db))
                seq.append(CGPoint(x: a.x + t * (b.x - a.x), y: a.y + t * (b.y - a.y)))
            }
            seq.append(b)
        }
        let below = seq.map { CGPoint(x: $0.x, y: min($0.y, 100 * $0.x)) }
        var above: [[CGPoint]] = []
        var run: [CGPoint] = []
        func flush() {
            if run.contains(where: { gap($0) > 1e-9 }) {
                above.append(run + run.reversed().map { CGPoint(x: $0.x, y: 100 * $0.x) })
            }
            run = []
        }
        for q in seq { if gap(q) >= -1e-9 { run.append(q) } else { flush() } }
        flush()
        return (below, above)
    }

    var body: some View {
        Canvas { ctx, size in
            let W = size.width, H = size.height, down = self.down
            // The plot area; time runs along x (across) or y (down), usage along the other axis.
            let left: CGFloat = 0
            let right: CGFloat = down ? W - (linesOnly ? 4 : 52) : W
            let top: CGFloat = linesOnly ? 4 : (down ? 16 : 20)
            let bottom: CGFloat = H - (linesOnly ? 4 : (down ? 6 : 20))
            func P(_ x: Double, _ v: Double) -> CGPoint {
                down ? CGPoint(x: left + v / 100 * (right - left), y: top + x * (bottom - top))
                     : CGPoint(x: left + x * (right - left), y: bottom - v / 100 * (bottom - top))
            }
            func label(_ t: String, _ at: CGPoint, _ c: Color, _ a: UnitPoint, _ s: CGFloat, _ b: Bool) {
                if !linesOnly { Self.label(ctx, t, at: at, colour: c, anchor: a, size: s, bold: b) }
            }
            func line(_ a: CGPoint, _ b: CGPoint) -> Path { Path { $0.move(to: a); $0.addLine(to: b) } }
            let dash = StrokeStyle(lineWidth: 1, dash: [2, 4])
            let guides: [(Double, Color)] = [(80, p.yellow), (90, p.orange), (100, p.red)]
            for (v, c) in guides {
                ctx.stroke(line(P(0, v), P(1, v)), with: .color(c.opacity(0.7)), style: dash)
            }
            if down {
                for (v, c) in guides { label("\(Int(v))%", .init(x: P(0, v).x, y: top - 8), c, .center, 9.5, false) }
                ctx.stroke(line(.init(x: 4, y: bottom - 6), .init(x: 17, y: bottom - 6)),
                           with: .color(p.pace), style: StrokeStyle(lineWidth: 2.6, dash: [4, 3]))
                label("even pace", .init(x: 22, y: bottom - 6), p.pace, .leading, 10.5, false)
            } else {
                label("100%", .init(x: W, y: P(0, 100).y - 8), p.red, .trailing, 10, false)
                label("90%", .init(x: W, y: P(0, 90).y), p.orange, .trailing, 10, false)
                label("80%", .init(x: W, y: P(0, 80).y + 7), p.yellow, .trailing, 10, false)
                ctx.stroke(line(.init(x: 0, y: P(0, 95).y), .init(x: 13, y: P(0, 95).y)),
                           with: .color(p.pace), style: StrokeStyle(lineWidth: 2.6, dash: [4, 3]))
                label("even pace", .init(x: 18, y: P(0, 95).y), p.pace, .leading, 10.5, false)
            }
            ctx.stroke(line(P(0, 0), P(1, 0)), with: .color(p.line), lineWidth: 1)

            let pts = model.samples.map { P($0.x, $0.y) }
            // Rev B §5: the pace wash first — a faint red triangle above the even-pace diagonal, only while over pace.
            if model.over {
                let wash = Path { $0.move(to: P(0, 0)); $0.addLine(to: P(1, 100)); $0.addLine(to: P(0, 100)); $0.closeSubpath() }
                ctx.fill(wash, with: .color(p.red.opacity(scheme == .dark ? 0.10 : 0.07)))
            }
            if let first = model.samples.first, let last = model.samples.last {
                let split = Self.splitFill(model.samples)
                var below = Path()
                below.move(to: P(first.x, 0))
                split.below.forEach { below.addLine(to: P($0.x, $0.y)) }
                below.addLine(to: P(last.x, 0))
                below.closeSubpath()
                ctx.fill(below, with: .color(accent.opacity(scheme == .dark ? 0.20 : 0.18)))
                for polygon in split.above {
                    var path = Path()
                    path.addLines(polygon.map { P($0.x, $0.y) })
                    path.closeSubpath()
                    ctx.fill(path, with: .color(p.orange.opacity(scheme == .dark ? 0.34 : 0.30)))
                }
            }
            ctx.stroke(line(P(0, 0), P(1, 100)), with: .color(p.pace), style: StrokeStyle(lineWidth: 2.6, dash: [5, 4]))
            if let nowX = model.nowX {
                ctx.stroke(line(P(nowX, 0), P(nowX, 104)), with: .color(p.sub.opacity(0.7)), lineWidth: 1)
            }
            if let last = model.samples.last, let lastPt = pts.last {
                if let proj = model.projection {
                    let x0 = last.x, v0 = last.y
                    let ex = proj > 100 ? x0 + (1 - x0) * (100 - v0) / (proj - v0) : 1
                    var end = P(ex, min(proj, 100))
                    if ex >= 1 { if down { end.y -= 4 } else { end.x -= 4 } }
                    ctx.stroke(line(lastPt, end), with: .color(accent.opacity(0.75)), style: StrokeStyle(lineWidth: 2, dash: [4, 3]))
                    ctx.stroke(Path(ellipseIn: CGRect(x: end.x - 4, y: end.y - 4, width: 8, height: 8)),
                               with: .color(proj >= 100 ? p.red : accent.opacity(0.85)), lineWidth: 1.8)
                    if proj < 100 {
                        let v = min(proj, 100)
                        let at = down ? CGPoint(x: end.x - 10, y: end.y - 8)
                                      : CGPoint(x: P(1, v).x - 34, y: P(1, v).y + (v >= 60 ? 13 : -13))
                        label("~\(Int(proj.rounded()))%", at, p.txt, .trailing, 11.5, true)
                    }
                }
                var path = Path()
                path.addLines(pts)
                ctx.stroke(path, with: .color(accent), style: StrokeStyle(lineWidth: 2.2, lineJoin: .round))
                for pt in pts.dropLast() {
                    ctx.fill(Path(ellipseIn: CGRect(x: pt.x - 2.4, y: pt.y - 2.4, width: 4.8, height: 4.8)), with: .color(accent))
                }
                ctx.fill(Path(ellipseIn: CGRect(x: lastPt.x - 5, y: lastPt.y - 5, width: 10, height: 10)),
                         with: .color(accent))
                if down {
                    // Right of the dot, unless that would run into the times.
                    let roomy = lastPt.x < right - 40
                    label("\(Int(last.y))%", .init(x: lastPt.x + (roomy ? 9 : -9), y: lastPt.y), p.ink, roomy ? .leading : .trailing, 12, true)
                } else {
                    // Near the left edge the figure sits right of its dot, so it isn't cut off.
                    let early = lastPt.x < 34
                    label("\(Int(last.y))%", .init(x: lastPt.x + (early ? 8 : -8), y: lastPt.y - 13), p.ink, early ? .leading : .trailing, 12, true)
                }
            }
            if down {
                label(model.startLabel, .init(x: W, y: top + 8), p.sub, .trailing, 11, false)
                if let nowX = model.nowX, P(nowX, 0).y > top + 18, P(nowX, 0).y < bottom - 18 {
                    label("now", .init(x: W, y: P(nowX, 0).y), p.sub, .trailing, 11, false)
                }
                label(model.endLabel, .init(x: W, y: bottom - 2), p.sub, .trailing, 11, false)
            } else {
                label(model.startLabel, .init(x: 0, y: bottom + 11), p.sub, .leading, 11, false)
                // "now" only where it can't run into the start or end time.
                if let nowX = model.nowX, P(nowX, 0).x > 56, P(nowX, 0).x < W - 56 {
                    label("now", .init(x: P(nowX, 0).x, y: bottom + 11), p.sub, .center, 11, false)
                }
                label(model.endLabel, .init(x: W, y: bottom + 11), p.sub, .trailing, 11, false)
            }
        }
        .opacity(dimmed ? 0.45 : 1)
        .accessibilityHidden(true)
    }

    private nonisolated static func label(_ ctx: GraphicsContext, _ text: String, at point: CGPoint, colour: Color,
                       anchor: UnitPoint, size: CGFloat = 10, bold: Bool = false) {
        ctx.draw(Text(text).font(.system(size: size, weight: bold ? .bold : .regular)).foregroundStyle(colour),
                 at: point, anchor: anchor)
    }
}

/// The widget ring (CCRM-78 (Widgets Reborn) faces): a thin track, the fill from 12 o'clock, the overshoot red from the
/// pace tick, the tick itself, and the number in the middle. `mono` draws everything in one colour for the lock
/// screen, where iOS tints widgets itself (wireframe rev B §5).
struct UsageRing: View {
    let pct: Double?
    let pace: Double?
    let accent: Color
    var lineWidth: CGFloat = 7
    var label: String?
    var sublabel: String?
    var fontSize: CGFloat?
    var mono: Color?
    var dimmed = false
    @Environment(\.palette) private var p

    var body: some View {
        GeometryReader { geo in
            let s = min(geo.size.width, geo.size.height)
            let value = min(max(pct ?? 0, 0), 100) / 100
            let colour = mono ?? p.severity(pct, accent: accent)
            // "Show red past the pace mark" off keeps the tick but not the red (Android's switch).
            let over = Display.redPastPace && (pace.map { (pct ?? 0) < 100 && (pct ?? 0) > $0 + 3 } ?? false)
            ZStack {
                Circle().stroke(mono?.opacity(0.32) ?? p.ringTrack, lineWidth: mono == nil ? max(1.5, lineWidth * 0.3) : lineWidth)
                if pct != nil, value > 0 {
                    if over, let pace {
                        arc(0, pace / 100).stroke(colour, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                        arc(pace / 100, value).stroke(mono ?? p.red, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                    } else {
                        arc(0, value).stroke(colour, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                    }
                }
                if let pace, pct != nil {
                    Capsule().fill(mono ?? p.tick)
                        .frame(width: max(2, lineWidth * 0.42), height: lineWidth * 1.9)
                        .offset(y: -(s - lineWidth) / 2)
                        .rotationEffect(.degrees(pace / 100 * 360))
                }
                VStack(spacing: 0) {
                    Text(label ?? Display.ringNumber(pct, pace: pace))
                        .font(.system(size: fontSize ?? s * 0.22, weight: .bold)).monospacedDigit()
                        .foregroundStyle(mono ?? (pct == nil ? p.sub : colour))
                        .minimumScaleFactor(0.6).lineLimit(1)
                    if let sublabel {
                        Text(sublabel).font(.system(size: s * 0.13, weight: .semibold)).foregroundStyle(mono ?? p.sub)
                    }
                }
                .padding(lineWidth * 1.2)
            }
            .frame(width: s, height: s)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(lineWidth / 2)
        }
        .opacity(dimmed ? 0.45 : 1)
        .accessibilityElement()
        .accessibilityLabel(pct.map { "\(Int($0)) percent" } ?? "No reading")
    }

    private func arc(_ from: Double, _ to: Double) -> some Shape {
        Circle().trim(from: from, to: to).rotation(.degrees(-90))
    }
}

/// CCRM-97 (Pace Number) V1: the pace-mark caption under an in-app bar — "39% pace mark · 13 over", the mark's
/// figure bold — starting 5 pt right of the mark, or ending 5 pt left of it when it would not fit (spec mobile §5,
/// `PaceText.captionRightOfMark`).
struct PaceCaption: View {
    let text: String
    let markPct: Double
    var size: CGFloat = 11.5
    /// CCRM-104 (Pace Line Parity) D5: a press on the figure underlines the caption for 3 s.
    var underlined = false
    @Environment(\.palette) private var p

    var body: some View {
        CaptionLayout(markPct: markPct) {
            styled.font(.system(size: size)).underline(underlined, color: p.ink).foregroundStyle(p.txt).lineLimit(1).fixedSize()
        }
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityLabel(text)
    }

    /// The first word ("39%", or "52%" in Vs pace mode) bold, the rest regular.
    private var styled: Text {
        guard let space = text.firstIndex(of: " ") else { return Text(text).bold() }
        return Text(text[..<space]).bold() + Text(text[space...])
    }
}

private struct CaptionLayout: Layout {
    let markPct: Double
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let size = subviews.first?.sizeThatFits(.unspecified) ?? .zero
        return CGSize(width: proposal.width ?? size.width, height: size.height)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard let caption = subviews.first else { return }
        let size = caption.sizeThatFits(.unspecified)
        let mark = bounds.width * markPct / 100
        let right = PaceText.captionRightOfMark(barWidth: bounds.width, markPct: markPct, captionWidth: size.width)
        let x = right ? mark + 5 : max(0, mark - 5 - size.width)
        caption.place(at: CGPoint(x: bounds.minX + x, y: bounds.minY), proposal: .unspecified)
    }
}

extension TrendChart.Model {
    /// CCRM-102 (Spent Week Gate): a greyed 5h draws no projection — it can't be used before the weekly reset.
    var withoutProjection: TrendChart.Model {
        var copy = self
        copy.projection = nil
        return copy
    }
}

/// CCRM-103 (Hybrid Notification), the shape grammar's ring (`design/2026-11-02-shape-grammar.html` rev D `ring()`;
/// Android's `GrammarRing`): the 5h, always. Centre-line radius 0.39 S; band 0.13 S (0.10 S at 48 pt and under, never
/// under 3); track ink 16% (22% greyed); the fill the account colour's severity step, at least 9% when non-zero, a round
/// cap only under the Round end mark; the red slice from the tick to the fill tip more than 3 points over; the dashed
/// room under the mark (≥ 28 pt); the usage end mark; the pace tick across the band; the open tile's dotted projection.
/// The figure is SF Pro with monospaced digits (0.25 S, 0.29 S at 48 and under), fitted to the bore. No "5h" text; the
/// accessibility label says "5-hour window, 34 percent".
struct GrammarRing: View {
    enum State { case normal, grey, notApplicable }

    let pct: Double?
    let pace: Double?
    let accent: Color
    /// nil = no figure (the bare small ring); otherwise the text in the bore (`Display.number`).
    var figure: String?
    var figureColour: Color?
    var state: State = .normal
    var projection: Double?
    var endMark: UsageEndMark = Display.usageEndMark
    /// The surface under the ring, for the end mark's gap.
    var surface: Color = .clear
    /// The lock screen tints widgets: draw in one colour.
    var mono: Color?
    /// A fixed band in pt, where the page sets one (the lock circular's `wring`, sw 5); nil = the proportional band.
    var bandOverride: CGFloat?
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        GeometryReader { geo in
            let s = min(geo.size.width, geo.size.height)
            ZStack {
                Canvas { ctx, size in draw(ctx, s: min(size.width, size.height)) }
                if let figure, state != .notApplicable {
                    let small = s <= 48
                    let base = s * (figure.count >= 4 ? (small ? 0.24 : 0.21) : (small ? 0.29 : 0.25))
                    Display.grammarFigure(figure, size: base, weight: loud && figureColour == nil ? .heavy : .bold)
                        .foregroundStyle(textColour)
                        .lineLimit(1).minimumScaleFactor(0.5)
                        .frame(width: max(1, 2 * (0.39 * s - (bandOverride ?? Self.band(s)) / 2 - 1.5) * 0.92))
                }
            }
            .frame(width: s, height: s)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .accessibilityElement()
        .accessibilityLabel(state == .notApplicable ? "No 5-hour window"
                            : pct.map { "5-hour window, \(Int($0)) percent" } ?? "5-hour window, no reading")
    }

    static func band(_ s: CGFloat) -> CGFloat { max(3, (s <= 48 ? 0.10 : 0.13) * s) }
    static func bore(_ s: CGFloat) -> CGFloat { 0.39 * s - band(s) / 2 }

    private var ink: Color { mono ?? (scheme == .dark ? Color(hex: 0xECECEC) : Color(hex: 0x1F1F1F)) }
    private var loud: Bool { state == .normal && (pct ?? 0) > 90 }
    private var textColour: Color {
        if state == .grey { return ink.opacity(0.45) }
        if pct == nil { return ink.opacity(0.5) }
        if let figureColour { return mono ?? figureColour }
        return loud ? (mono ?? p.severity(pct, accent: accent)) : ink
    }

    private func draw(_ ctx: GraphicsContext, s: CGFloat) {
        let sw = bandOverride ?? Self.band(s), r = 0.39 * s, c = CGPoint(x: s / 2, y: s / 2)
        func arc(_ a: Double, _ b: Double, radius: CGFloat = r) -> Path {
            var path = Path()
            path.addArc(center: c, radius: radius, startAngle: .degrees(-90 + a * 360), endAngle: .degrees(-90 + b * 360),
                        clockwise: false)
            return path
        }
        func pt(_ f: Double, _ radius: CGFloat) -> CGPoint {
            CGPoint(x: c.x + radius * sin(2 * .pi * f), y: c.y - radius * cos(2 * .pi * f))
        }
        ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)),
                   with: .color(ink.opacity(state == .normal ? 0.16 : 0.22)), lineWidth: sw)
        if state == .notApplicable {
            let k = 0.12 * s
            var x = Path()
            x.move(to: CGPoint(x: c.x - k, y: c.y - k)); x.addLine(to: CGPoint(x: c.x + k, y: c.y + k))
            x.move(to: CGPoint(x: c.x + k, y: c.y - k)); x.addLine(to: CGPoint(x: c.x - k, y: c.y + k))
            ctx.stroke(x, with: .color(ink.opacity(0.45)), style: StrokeStyle(lineWidth: max(1.5, s / 28), lineCap: .round))
            return
        }
        guard state == .normal, let pct else { return }
        let f = pct <= 0 ? 0 : max(min(pct, 100) / 100, 0.09)
        let fill = mono ?? p.severity(pct, accent: accent)
        let red = Display.redPastPace && (pace.map { pct > $0 + 3 } ?? false)
        let roundCap = endMark == .round && !red
        if f > 0 {
            ctx.stroke(arc(0, min(f, 0.9999)), with: .color(fill), style: StrokeStyle(lineWidth: sw, lineCap: roundCap ? .round : .butt))
        }
        if let pace, pct < pace, s >= 28 {
            let g = min(max(pace / 100, 0), 1)
            let a0 = f + (roundCap && f > 0 ? Double(sw / 2 / (2 * .pi * r)) : 0)
            if g - a0 > 0.01 {
                let lw = max(1, s / 60), ro = r + sw / 2 - lw / 2, rn = r - sw / 2 + lw / 2, dash = max(1.6, s / 26)
                var room = arc(a0, g, radius: ro)
                room.addLine(to: pt(g, rn))
                room.addArc(center: c, radius: rn, startAngle: .degrees(-90 + g * 360), endAngle: .degrees(-90 + a0 * 360), clockwise: true)
                ctx.stroke(room, with: .color((mono ?? accent).opacity(0.6)), style: StrokeStyle(lineWidth: lw, dash: [dash, dash * 0.75]))
            }
        }
        if let projection, projection / 100 > f {
            let a = f + 0.03, b = min(projection, 100) / 100
            if b > a {
                ctx.stroke(arc(a, b), with: .color(fill.opacity(0.75)),
                           style: StrokeStyle(lineWidth: sw * 0.4, lineCap: .round, dash: [0.1, sw * 0.8]))
            }
        }
        if red, let pace {
            ctx.stroke(arc(min(max(pace / 100, 0), 1), min(f, 0.9999)), with: .color(mono ?? p.red), lineWidth: sw)
        }
        if let role = endMark.role(usedPct: pct, elapsedPct: pace), f > 0 {
            let colour: Color = mono ?? {
                switch role {
                case .fill: return fill
                case .ink: return ink
                case .under: return Display.vsUnder(scheme)
                case .over: return Display.vsOver(scheme)
                }
            }()
            var line = Path()
            line.move(to: pt(f, r - sw * 0.85)); line.addLine(to: pt(f, r + sw * 0.85))
            let lw = max(1.3, s / 40)
            ctx.stroke(line, with: .color(surface), lineWidth: lw + 2)
            ctx.stroke(line, with: .color(colour), style: StrokeStyle(lineWidth: lw, lineCap: .round))
        }
        if let pace, pct > 0 {
            let g = min(max(pace / 100, 0), 1)
            var tick = Path()
            tick.move(to: pt(g, r - sw)); tick.addLine(to: pt(g, r + sw))
            ctx.stroke(tick, with: .color(ink.opacity(0.9)), style: StrokeStyle(lineWidth: max(1.5, s / 32), lineCap: .round))
        }
    }
}

/// CCRM-103 (Hybrid Notification): the small 5h glyph — the 12 pt lead of the 5h line, the lock-screen inline image —
/// drawn as the page's `gauge()`, the Android status-bar ring's geometry (24ths of the box: hairline 1.493 at radius 9.2,
/// band 4, the needle from the hub to 11.2, the hub, a cross from 99.5), so it matches Android's glyph. Greyed (a spent
/// week): the arc at 40% ink and no needle. No text, no label of its own.
struct FiveGlyph: View {
    let pct: Double?
    let pace: Double?
    let accent: Color
    var greyed = false
    var mono: Color?
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Canvas { ctx, size in
            let s = min(size.width, size.height), u = s / 24, c = CGPoint(x: s / 2, y: s / 2), r = 9.2 * u
            let ink = mono ?? (scheme == .dark ? Color.white : Color.black)
            func arc(_ a: Double, _ b: Double) -> Path {
                var path = Path()
                path.addArc(center: c, radius: r, startAngle: .degrees(-90 + a * 360), endAngle: .degrees(-90 + min(b, 0.9999) * 360),
                            clockwise: false)
                return path
            }
            ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)),
                       with: .color(ink.opacity(greyed ? 0.3 : 0.5)), lineWidth: 1.493 * u)
            guard let pct else { return }
            if greyed {
                ctx.stroke(arc(0, max(min(pct, 100) / 100, 0.09)), with: .color(ink.opacity(0.4)), lineWidth: 4 * u)
                return
            }
            let f = pct > 0 ? max(min(pct, 100) / 100, 0.09) : 0
            if f > 0 { ctx.stroke(arc(0, f), with: .color(mono ?? p.severity(pct, accent: accent)), lineWidth: 4 * u) }
            if let pace, pct > pace + 3, Display.redPastPace {
                ctx.stroke(arc(min(max(pace / 100, 0), 1), f), with: .color(mono ?? p.red), lineWidth: 4 * u)
            }
            if let pace {
                let a = 2 * Double.pi * min(max(pace / 100, 0), 1), rim = 11.2 * u
                var needle = Path()
                needle.move(to: c)
                needle.addLine(to: CGPoint(x: c.x + rim * sin(a), y: c.y - rim * cos(a)))
                ctx.stroke(needle, with: .color(ink.opacity(0.95)), style: StrokeStyle(lineWidth: 1.792 * u, lineCap: .round))
                let hub = 1.344 * u * 1.4
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - hub, y: c.y - hub, width: 2 * hub, height: 2 * hub)), with: .color(ink.opacity(0.95)))
            }
            if pct >= 99.5 {
                let k = 4.2 * u
                var x = Path()
                x.move(to: CGPoint(x: c.x - k, y: c.y - k)); x.addLine(to: CGPoint(x: c.x + k, y: c.y + k))
                x.move(to: CGPoint(x: c.x + k, y: c.y - k)); x.addLine(to: CGPoint(x: c.x - k, y: c.y + k))
                ctx.stroke(x, with: .color(ink.opacity(0.9)), style: StrokeStyle(lineWidth: 2 * u, lineCap: .round))
            }
        }
        .accessibilityHidden(true)
    }
}

/// CCRM-103 (Hybrid Notification): the Weekly as a small bar — Android's no-5h status-bar glyph (N3, the page's
/// `barGlyph()`): a 21 × 10-unit capsule in a 24-unit box (scaled to the frame's width), the fill clipped to it (at
/// least 9% when non-zero), red past the tick when more than 3 points over, a 1.2-unit outline at 50%, and the pace
/// tick (1.8 units at 95%) in a cleared 4.2-unit halo. At a truncated 100 it is the red cross alone, as on Android.
/// Used on the lock-screen circular for a plan with no 5h (Robin, 2026-11-02).
struct WeeklyBarGlyph: View {
    let pct: Double?
    let pace: Double?
    let accent: Color
    var mono: Color?
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Canvas { ctx, size in
            // The tick spans 16 units of height (3 past each edge of the 10-unit capsule), so the unit fits both ways
            // and the glyph centres in its frame; nothing is clipped at any aspect.
            let u = min(size.width / 24, size.height / 17), ink = mono ?? (scheme == .dark ? Color.white : Color.black)
            let cy = size.height / 2, ox = (size.width - 24 * u) / 2
            if let pct, Int(pct) >= 100 {
                let a = min(7 * u, size.height / 2 - 1.7 * u), c = CGPoint(x: size.width / 2, y: cy)
                var x = Path()
                x.move(to: CGPoint(x: c.x - a, y: c.y - a)); x.addLine(to: CGPoint(x: c.x + a, y: c.y + a))
                x.move(to: CGPoint(x: c.x + a, y: c.y - a)); x.addLine(to: CGPoint(x: c.x - a, y: c.y + a))
                ctx.stroke(x, with: .color(mono ?? p.red), style: StrokeStyle(lineWidth: 3.4 * u, lineCap: .round))
                return
            }
            let x0 = ox + 1.5 * u, w = 21 * u, h = 10 * u, y0 = cy - h / 2, rr = h / 2
            let capsule = Path(roundedRect: CGRect(x: x0, y: y0, width: w, height: h), cornerRadius: rr)
            ctx.fill(capsule, with: .color(ink.opacity(0.16)))
            if let pct {
                let fw = w * max(min(pct, 100), pct > 0 ? 9 : 0) / 100
                var clip = ctx
                clip.clip(to: capsule)
                clip.fill(Path(CGRect(x: x0, y: y0, width: fw, height: h)), with: .color(mono.map { $0.opacity(0.95) } ?? p.severity(pct, accent: accent)))
                if let pace, pct > pace + 3, Display.redPastPace {
                    let tx = w * min(max(pace, 0), 100) / 100
                    clip.fill(Path(CGRect(x: x0 + tx, y: y0, width: max(fw - tx, 0), height: h)), with: .color(mono.map { $0.opacity(0.95) } ?? p.red))
                }
            }
            ctx.stroke(Path(roundedRect: CGRect(x: x0 + 0.6 * u, y: y0 + 0.6 * u, width: w - 1.2 * u, height: h - 1.2 * u),
                            cornerRadius: rr - 0.6 * u), with: .color(ink.opacity(0.5)), lineWidth: 1.2 * u)
            if let pct, pct > 0, let pace {
                let tx = x0 + w * min(max(pace, 0), 100) / 100
                var halo = Path(); halo.move(to: CGPoint(x: tx, y: y0 - 3.4 * u)); halo.addLine(to: CGPoint(x: tx, y: y0 + h + 3.4 * u))
                var cleared = ctx
                cleared.blendMode = .clear
                cleared.stroke(halo, with: .color(.black), lineWidth: 4.2 * u)
                var tick = Path(); tick.move(to: CGPoint(x: tx, y: y0 - 3 * u)); tick.addLine(to: CGPoint(x: tx, y: y0 + h + 3 * u))
                ctx.stroke(tick, with: .color(ink.opacity(0.95)), style: StrokeStyle(lineWidth: 1.8 * u, lineCap: .round))
            }
        }
        .accessibilityHidden(true)
    }
}
