import SwiftUI
import Testing
import UIKit

@testable import Cooldown

/// CCBG-88 (Tab Strip Truncates): History's strip follows the shared rule (`spec/mobile/surfaces/app.md` §2.3 Fit) — fixed
/// in equal shares while every label fits its share, scrolling once one would not — not the account count.
@MainActor
@Suite("Tab strip fit")
struct TabStripFitTests {

    /// The strip's two forms, with boxes standing in for the labels; the fallback is 99 pt tall so the render tells which won.
    private func pickedFixed(_ widths: [CGFloat], column: CGFloat) -> Bool {
        let view = ViewThatFits(in: .horizontal) {
            EqualShares { ForEach(Array(widths.enumerated()), id: \.offset) { Color.red.frame(width: $0.element, height: 10) } }
            Color.blue.frame(width: 10, height: 99)
        }
        .frame(width: column)
        let renderer = ImageRenderer(content: view)
        renderer.proposedSize = ProposedViewSize(width: column, height: nil)
        return (renderer.uiImage?.size.height ?? 0) < 50
    }

    @Test("the rule: fixed while the widest label fits its equal share")
    func rule() {
        #expect(EqualShares.fits(labelWidths: [100, 113, 90], width: 340))
        #expect(!EqualShares.fits(labelWidths: [100, 114, 90], width: 340))   // 3 × 114 > 340
        #expect(!EqualShares.fits(labelWidths: [60, 200], width: 340))         // two accounts, one long name: scrolls
        #expect(EqualShares.fits(labelWidths: [80, 80, 80, 80], width: 340))  // four short names stay fixed
        #expect(EqualShares.fits(labelWidths: [], width: 340))
    }

    @Test("at a 340 pt column three accounts scroll when one label is wider than a third, else stay fixed")
    func layoutFollowsTheRule() {
        #expect(pickedFixed([100, 110, 90], column: 340))
        #expect(!pickedFixed([100, 125, 90], column: 340))
        #expect(pickedFixed([80, 80, 80, 80], column: 340))
        #expect(!pickedFixed([60, 200], column: 340))
    }
}
