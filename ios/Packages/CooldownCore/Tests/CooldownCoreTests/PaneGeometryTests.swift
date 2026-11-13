import CoreGraphics
@testable import CooldownCore
import XCTest

/// CCRM-116 (Inner Panes): the pane rule, pinned to the wireframe's measured postures
/// (design/2026-11-12-iphone-duo-inner-screen.html §2 "Postures, measured").
final class PaneGeometryTests: XCTestCase {
    func testWideFlatIsAnEvenSplitSideBySide() {
        // Wide inner screen: content x 20–847 (827 wide), y 60–657 (597 high).
        let s = PaneGeometry.split(size: CGSize(width: 827, height: 597))
        XCTAssertEqual(s.axis, .sideBySide)
        XCTAssertFalse(s.onFold)
        XCTAssertEqual(s.first.width, 405.5)
        XCTAssertEqual(s.second.minX, 421.5)
        XCTAssertEqual(s.second.width, 405.5)
        XCTAssertEqual(s.first.height, 597)
    }

    func testWideHalfOpenPutsTheDividerOnTheFold() {
        // Fold band 32 pt at window x 459.5–491.5 → content x 439.5–471.5.
        let s = PaneGeometry.split(size: CGSize(width: 827, height: 597), fold: CGRect(x: 439.5, y: -60, width: 32, height: 669))
        XCTAssertTrue(s.onFold)
        XCTAssertEqual(s.first.width, 439.5)
        XCTAssertEqual(s.second.minX, 471.5)
        XCTAssertEqual(s.second.width, 355.5)
    }

    func testTallFlatStacks() {
        // Tall inner screen: content y 86–856 (770 high), 629 wide.
        let s = PaneGeometry.split(size: CGSize(width: 629, height: 770))
        XCTAssertEqual(s.axis, .stacked)
        XCTAssertEqual(s.first.height, 377)
        XCTAssertEqual(s.second.minY, 393)
        XCTAssertEqual(s.second.height, 377)
        XCTAssertEqual(s.first.width, 629)
    }

    func testTallHalfOpenUsesTheFold() {
        // Fold band at window y 459.5–491.5 → content y 373.5–405.5.
        let s = PaneGeometry.split(size: CGSize(width: 629, height: 770), fold: CGRect(x: -20, y: 373.5, width: 669, height: 32))
        XCTAssertTrue(s.onFold)
        XCTAssertEqual(s.first.height, 373.5)
        XCTAssertEqual(s.second.minY, 405.5)
        XCTAssertEqual(s.second.height, 364.5)
    }

    func testAFoldAlongTheWrongAxisOrOffTheAreaIsIgnored() {
        let size = CGSize(width: 827, height: 597)
        // A horizontal band cannot divide side-by-side panes.
        XCTAssertFalse(PaneGeometry.split(size: size, fold: CGRect(x: 0, y: 280, width: 827, height: 32)).onFold)
        // A band that would leave a sliver falls back to the even split.
        XCTAssertFalse(PaneGeometry.split(size: size, fold: CGRect(x: 40, y: 0, width: 32, height: 597)).onFold)
        XCTAssertFalse(PaneGeometry.split(size: size, fold: CGRect(x: 900, y: 0, width: 32, height: 597)).onFold)
    }

    func testFlatToHalfOpenMovesTheDividerOnlyALittle() {
        // §3b: "Flat vs half open: the divider moves ≤ 12 pt" (tall), ≤ 34 pt overall (§4).
        let size = CGSize(width: 629, height: 770)
        let flat = PaneGeometry.split(size: size)
        let open = PaneGeometry.split(size: size, fold: CGRect(x: 0, y: 373.5, width: 629, height: 32))
        XCTAssertLessThanOrEqual(abs(flat.second.minY - open.second.minY), 34)
        XCTAssertEqual(flat.axis, open.axis)
    }

    func testIPadFollowsTheSameRule() {
        XCTAssertEqual(PaneGeometry.axis(for: CGSize(width: 780, height: 1100)), .stacked)   // portrait
        XCTAssertEqual(PaneGeometry.axis(for: CGSize(width: 860, height: 760)), .sideBySide) // landscape detail column
    }

    func testASinglePaneNeverCrossesTheFold() {
        let size = CGSize(width: 827, height: 597)
        let fold = CGRect(x: 439.5, y: 0, width: 32, height: 597)
        XCTAssertEqual(PaneGeometry.single(size: size, fold: fold), PaneGeometry.split(size: size, fold: fold).first)
        let flat = PaneGeometry.single(size: size)
        XCTAssertEqual(flat.midX, size.width / 2)
        XCTAssertEqual(flat.width, 405.5)
    }
}
