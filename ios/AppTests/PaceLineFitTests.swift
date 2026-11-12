import CooldownCore
import SwiftUI
import Testing
import UIKit

@testable import Cooldown

/// CCRM-104 (Pace Line Parity) and CCRM-108 (Reset Named): the one drop order ("resets", then the clock — a reset on
/// another day keeps its weekday to the last — never the duration and word; Astra C1-4). The Pace map's name placement
/// moved to core `MapLabels` with CCRM-111 (Map Layers): `PaceMapTests` and CooldownCore's `PaceMapV20Tests`.
@MainActor
@Suite("Pace line fit")
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
            PaceLine(side: .under, duration: "6d 23h", weekday: "Sun", clock: "9:30 PM"),
            PaceLine(side: .on, duration: nil, weekday: nil, clock: "9:30 PM"),
        ]
        for line in lines {
            let v = variants(line, size: 11.5)
            let natural = v.map { width($0.fixedSize(), in: 1000) }
            #expect(zip(natural, natural.dropFirst()).allSatisfy { $0 >= $1 }, "\(line.text()): widest first")
            #expect(v.count == line.forms.count, "\(line.text()): one variant per printed form")
            for avail in [100.0, 126, 160, 208, 300] {
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
}
