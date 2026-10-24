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
            if let first = model.samples.first, let last = model.samples.last {
                var area = Path()
                area.move(to: P(first.x, 0))
                pts.forEach { area.addLine(to: $0) }
                area.addLine(to: P(last.x, 0))
                area.closeSubpath()
                ctx.fill(area, with: .color(accent.opacity(0.24)))
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
                ctx.stroke(path, with: .color(model.over ? p.red : accent), style: StrokeStyle(lineWidth: 2.2, lineJoin: .round))
                for pt in pts.dropLast() {
                    ctx.fill(Path(ellipseIn: CGRect(x: pt.x - 2.4, y: pt.y - 2.4, width: 4.8, height: 4.8)), with: .color(accent))
                }
                ctx.fill(Path(ellipseIn: CGRect(x: lastPt.x - 5, y: lastPt.y - 5, width: 10, height: 10)),
                         with: .color(model.over ? p.red : accent))
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
                    Text(label ?? Display.number(pct))
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
