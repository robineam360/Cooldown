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
    @Environment(\.palette) private var p

    var body: some View {
        Canvas { ctx, size in
            let W = size.width, top: CGFloat = 20, base = size.height - 20
            func X(_ v: Double) -> CGFloat { v * W }
            func Y(_ v: Double) -> CGFloat { base - v / 100 * (base - top) }
            let dash = StrokeStyle(lineWidth: 1, dash: [2, 4])
            for (v, c) in [(80.0, p.yellow), (90.0, p.orange), (100.0, p.red)] {
                ctx.stroke(Path { $0.move(to: .init(x: 0, y: Y(v))); $0.addLine(to: .init(x: W, y: Y(v))) },
                           with: .color(c.opacity(0.7)), style: dash)
            }
            label(ctx, "100%", at: .init(x: W, y: Y(100) - 8), colour: p.red, anchor: .trailing)
            label(ctx, "90%", at: .init(x: W, y: Y(90)), colour: p.orange, anchor: .trailing)
            label(ctx, "80%", at: .init(x: W, y: Y(80) + 7), colour: p.yellow, anchor: .trailing)
            ctx.stroke(Path { $0.move(to: .init(x: 0, y: Y(95))); $0.addLine(to: .init(x: 13, y: Y(95))) },
                       with: .color(p.pace), style: StrokeStyle(lineWidth: 2.6, dash: [4, 3]))
            label(ctx, "even pace", at: .init(x: 18, y: Y(95)), colour: p.pace, anchor: .leading, size: 10.5)
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
                        label(ctx, "~\(Int(proj.rounded()))%", at: .init(x: X(1) - 34, y: Y(v) + (v >= 60 ? 13 : -13)),
                              colour: p.txt, anchor: .trailing, size: 11.5, bold: true)
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
                label(ctx, "\(Int(last.y))%", at: .init(x: lastPt.x - 8, y: lastPt.y - 13), colour: p.ink,
                      anchor: .trailing, size: 12, bold: true)
            }
            label(ctx, model.startLabel, at: .init(x: 0, y: base + 11), colour: p.sub, anchor: .leading, size: 11)
            if let nowX = model.nowX {
                label(ctx, "now", at: .init(x: X(nowX), y: base + 11), colour: p.sub, anchor: .center, size: 11)
            }
            label(ctx, model.endLabel, at: .init(x: W, y: base + 11), colour: p.sub, anchor: .trailing, size: 11)
        }
        .opacity(dimmed ? 0.45 : 1)
        .accessibilityHidden(true)
    }

    private func label(_ ctx: GraphicsContext, _ text: String, at point: CGPoint, colour: Color,
                       anchor: UnitPoint, size: CGFloat = 10, bold: Bool = false) {
        ctx.draw(Text(text).font(.system(size: size, weight: bold ? .bold : .regular)).foregroundStyle(colour),
                 at: point, anchor: anchor)
    }
}
