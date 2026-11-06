import CooldownCore
import SwiftUI
import Testing
import UIKit

@testable import Cooldown

/// CCRM-104 (Pace Line Parity): the one drop order (weekday, then clock, never the duration and word — Astra C1-4) and
/// the Pace map's name placement (D7, Astra C1-1).
@MainActor
@Suite("Pace line fit and Pace map placement")
struct PaceLineFitTests {

    private func width<V: View>(_ v: V, in w: CGFloat) -> CGFloat {
        UIHostingController(rootView: v).sizeThatFits(in: CGSize(width: w, height: 200)).width
    }

    private func variants(_ line: PaceLine, size: CGFloat) -> [Text] {
        line.variants(rest: .gray, ink: .black, scheme: .light).map { $0.font(.system(size: size)) }
    }

    @Test("FitLine shows the first variant that fits; the duration and word are the floor")
    func dropOrder() {
        let lines = [
            PaceLine(side: .over, duration: "23h 59m", weekday: "Wed", clock: "12:30 PM"),
            PaceLine(side: .under, duration: "6d 23h", weekday: "Sun", clock: "9:30 pm"),
            PaceLine(side: .on, duration: nil, weekday: nil, clock: "9:30 pm"),
        ]
        for line in lines {
            let v = variants(line, size: 11.5)
            let natural = v.map { width($0.fixedSize(), in: 1000) }
            #expect(natural[0] >= natural[1] && natural[1] >= natural[2], "\(line.text()): widest first")
            for avail in [126.0, 160, 208, 300] {
                let got = width(FitLine(variants: v), in: avail)
                let fitting = natural.first { $0 <= avail + 0.5 }
                if let fitting {
                    #expect(abs(got - fitting) < 1.5, "\(line.text()) at \(avail): \(got) vs \(fitting)")
                } else {
                    // Nothing fits: the floor scales (0.8) before it would truncate, so it never exceeds the column.
                    #expect(got <= avail + 0.5, "\(line.text()) at \(avail): \(got)")
                }
            }
        }
    }

    @Test("Pace map: Studio on Work's spot moves; no label overlaps another, a dot or an axis word")
    func collision() {
        let plot = CGRect(x: 0, y: 0, width: 339, height: 230)
        let dots: [PaceMapLayout.Dot] = [
            .init(key: "gpt", name: "ChatGPT", point: CGPoint(x: 120, y: 190)),
            .init(key: "gem", name: "Gemini", point: CGPoint(x: 250, y: 40)),
            .init(key: "work", name: "Work", point: CGPoint(x: 90, y: 120)),
            .init(key: "studio", name: "Studio", point: CGPoint(x: 90, y: 120)),
            .init(key: "design", name: "Design", point: CGPoint(x: 92, y: 122)),
        ]
        let obstacles = [CGRect(x: 5, y: 8, width: 70, height: 12), CGRect(x: 250, y: 200, width: 70, height: 12)]
        let placed = PaceMapLayout.place(dots: dots, plot: plot, rightEdge: 335, obstacles: obstacles,
                                         size: { CGSize(width: Double($0.count) * 6.2, height: 12) })
        let by = Dictionary(uniqueKeysWithValues: placed.map { ($0.key, $0) })
        #expect(placed.count == dots.count)
        #expect(by["work"]?.spot == .right)
        // Studio shares Work's spot, so its name cannot go right; Design's own dot sits where "below" would be.
        #expect(by["studio"]?.spot != .right && by["design"]?.spot != .right)
        // A lone collision with nothing else near goes below (the lower label is the one that moves).
        let pair = PaceMapLayout.place(dots: Array(dots[2...3]), plot: plot, rightEdge: 335, obstacles: obstacles,
                                       size: { CGSize(width: Double($0.count) * 6.2, height: 12) })
        #expect(pair.first { $0.key == "work" }?.spot == .right && pair.first { $0.key == "studio" }?.spot == .below)
        for a in placed {
            #expect(plot.contains(a.rect), "\(a.key) inside the plot")
            for o in obstacles { #expect(!o.intersects(a.rect), "\(a.key) off the axis words") }
            for b in placed where b.key != a.key { #expect(!a.rect.intersects(b.rect), "\(a.key) vs \(b.key)") }
            for d in dots { #expect(!a.rect.intersects(CGRect(x: d.point.x - 4, y: d.point.y - 4, width: 8, height: 8)), "\(a.key) off dot \(d.key)") }
        }
        if let design = by["design"], design.spot == .leader { #expect(design.leader != nil && design.rect.maxX <= 335 + 0.5) }
    }

    @Test("Pace map: a lone dot near the right edge puts its name left, never off the plot")
    func edge() {
        let plot = CGRect(x: 0, y: 0, width: 339, height: 230)
        let dots = [PaceMapLayout.Dot(key: "a", name: "Atlas", point: CGPoint(x: 330, y: 30))]
        let placed = PaceMapLayout.place(dots: dots, plot: plot, rightEdge: 335, obstacles: [], size: { _ in CGSize(width: 32, height: 12) })
        #expect(placed.first?.spot == .below || placed.first?.spot == .left)
        #expect(placed.first.map { plot.contains($0.rect) } == true)
    }
}
