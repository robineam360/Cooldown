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
            let over = pace.map { (pct ?? 0) < 100 && (pct ?? 0) > $0 + 3 } ?? false
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
    @Environment(\.palette) private var p

    var body: some View {
        Canvas { ctx, size in
            let W = size.width, top: CGFloat = linesOnly ? 4 : 20, base = size.height - (linesOnly ? 4 : 20)
            func label(_ t: String, _ at: CGPoint, _ c: Color, _ a: UnitPoint, _ s: CGFloat, _ b: Bool) {
                if !linesOnly { Self.label(ctx, t, at: at, colour: c, anchor: a, size: s, bold: b) }
            }
            func X(_ v: Double) -> CGFloat { v * W }
            func Y(_ v: Double) -> CGFloat { base - v / 100 * (base - top) }
            let dash = StrokeStyle(lineWidth: 1, dash: [2, 4])
            for (v, c) in [(80.0, p.yellow), (90.0, p.orange), (100.0, p.red)] {
                ctx.stroke(Path { $0.move(to: .init(x: 0, y: Y(v))); $0.addLine(to: .init(x: W, y: Y(v))) },
                           with: .color(c.opacity(0.7)), style: dash)
            }
            label("100%", .init(x: W, y: Y(100) - 8), p.red, .trailing, 10, false)
            label("90%", .init(x: W, y: Y(90)), p.orange, .trailing, 10, false)
            label("80%", .init(x: W, y: Y(80) + 7), p.yellow, .trailing, 10, false)
            ctx.stroke(Path { $0.move(to: .init(x: 0, y: Y(95))); $0.addLine(to: .init(x: 13, y: Y(95))) },
                       with: .color(p.pace), style: StrokeStyle(lineWidth: 2.6, dash: [4, 3]))
            label("even pace", .init(x: 18, y: Y(95)), p.pace, .leading, 10.5, false)
            ctx.stroke(Path { $0.move(to: .init(x: 0, y: base)); $0.addLine(to: .init(x: W, y: base)) },
                       with: .color(p.line), lineWidth: 1)

            let pts = model.samples.map { CGPoint(x: X($0.x), y: Y($0.y)) }
            if let first = pts.first, let last = pts.last {
                var area = Path()
                area.move(to: .init(x: first.x, y: base))
                pts.forEach { area.addLine(to: $0) }
                area.addLine(to: .init(x: last.x, y: base))
                area.closeSubpath()
                ctx.fill(area, with: .color(accent.opacity(0.24)))
            }
            ctx.stroke(Path { $0.move(to: .init(x: X(0), y: Y(0))); $0.addLine(to: .init(x: X(1), y: Y(100))) },
                       with: .color(p.pace), style: StrokeStyle(lineWidth: 2.6, dash: [5, 4]))
            if let nowX = model.nowX {
                ctx.stroke(Path { $0.move(to: .init(x: X(nowX), y: top - 4)); $0.addLine(to: .init(x: X(nowX), y: base)) },
                           with: .color(p.sub.opacity(0.7)), lineWidth: 1)
            }
            if let last = model.samples.last, let lastPt = pts.last {
                if let proj = model.projection {
                    let x0 = last.x, v0 = last.y
                    let ex = proj > 100 ? x0 + (1 - x0) * (100 - v0) / (proj - v0) : 1
                    let end = CGPoint(x: X(ex) - (ex >= 1 ? 4 : 0), y: Y(min(proj, 100)))
                    ctx.stroke(Path { $0.move(to: lastPt); $0.addLine(to: end) },
                               with: .color(accent.opacity(0.75)), style: StrokeStyle(lineWidth: 2, dash: [4, 3]))
                    ctx.stroke(Path(ellipseIn: CGRect(x: end.x - 4, y: end.y - 4, width: 8, height: 8)),
                               with: .color(proj >= 100 ? p.red : accent.opacity(0.85)), lineWidth: 1.8)
                    if proj < 100 {
                        let v = min(proj, 100)
                        label("~\(Int(proj.rounded()))%", .init(x: X(1) - 34, y: Y(v) + (v >= 60 ? 13 : -13)),
                              p.txt, .trailing, 11.5, true)
                    }
                }
                var line = Path()
                line.addLines(pts)
                ctx.stroke(line, with: .color(model.over ? p.red : accent), style: StrokeStyle(lineWidth: 2.2, lineJoin: .round))
                for pt in pts.dropLast() {
                    ctx.fill(Path(ellipseIn: CGRect(x: pt.x - 2.4, y: pt.y - 2.4, width: 4.8, height: 4.8)), with: .color(accent))
                }
                ctx.fill(Path(ellipseIn: CGRect(x: lastPt.x - 5, y: lastPt.y - 5, width: 10, height: 10)),
                         with: .color(model.over ? p.red : accent))
                // Near the left edge the figure sits right of its dot, so it isn't cut off.
                let early = lastPt.x < 34
                label("\(Int(last.y))%", .init(x: lastPt.x + (early ? 8 : -8), y: lastPt.y - 13), p.ink, early ? .leading : .trailing, 12, true)
            }
            label(model.startLabel, .init(x: 0, y: base + 11), p.sub, .leading, 11, false)
            // "now" only where it can't run into the start or end time.
            if let nowX = model.nowX, X(nowX) > 56, X(nowX) < W - 56 {
                label("now", .init(x: X(nowX), y: base + 11), p.sub, .center, 11, false)
            }
            label(model.endLabel, .init(x: W, y: base + 11), p.sub, .trailing, 11, false)
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
            let over = pace.map { (pct ?? 0) < 100 && (pct ?? 0) > $0 + 3 } ?? false
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
                    Text(label ?? (pct.map { "\(Int($0))%" } ?? "—"))
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
