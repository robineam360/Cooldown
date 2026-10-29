import Foundation
import Testing

@testable import Cooldown

/// CCRM-94 (iOS App): the card layout keeps Android's rules — one card always above More, hidden wins over More,
/// unknown or missing ids repaired.
@Suite("Card layout")
struct CardLayoutTests {
    @Test func standardShowsEverythingInOrder() {
        #expect(CardLayout.standard.main == [.session, .weekly, .credits])
        #expect(CardLayout.standard.folded.isEmpty)
    }

    @Test func lastCardAboveMoreCannotGo() {
        let one = CardLayout.standard.hiding(.weekly).folding(.credits)
        #expect(one.main == [.session])
        #expect(!one.canHide(.session) && !one.canFold(.session))
        #expect(one.hiding(.session).main == [.session])
        #expect(one.folding(.session).main == [.session])
    }

    @Test func absentCardsDoNotCountTowardsTheLastOne() {
        let l = CardLayout.standard.hiding(.weekly)
        #expect(l.canHide(.session))
        #expect(!l.canHide(.session, among: [.session, .weekly]))
        #expect(!l.canFold(.session, among: [.session, .weekly]))
    }

    @Test func hiddenWinsOverMore() {
        let l = CardLayout.standard.folding(.weekly).hiding(.weekly)
        #expect(l.folded.isEmpty && !l.more.contains(.weekly))
        #expect(l.showing(.weekly).main == [.session, .weekly, .credits])
    }

    @Test func normalizedRepairsOrder() {
        let broken = CardLayout(order: [.credits, .credits], hidden: [.session, .weekly, .credits], more: [])
        let fixed = broken.normalized
        #expect(fixed.order == [.credits, .session, .weekly])
        #expect(fixed.main == [.credits])
    }

    @Test func movingKeepsEveryCard() {
        let moved = CardLayout.standard.moving(fromOffsets: [2], toOffset: 0)
        #expect(moved.order == [.credits, .session, .weekly])
    }

    @Test func savesPerAccount() {
        let key = "test-\(UUID().uuidString)"
        defer { UserDefaults.standard.removeObject(forKey: "layout.\(key)") }
        #expect(CardLayout.load(key) == .standard)
        let l = CardLayout.standard.folding(.credits)
        CardLayout.save(l, key)
        #expect(CardLayout.load(key) == l)
    }

    @Test func chartHeights() {
        #expect(ChartSize.small.height(width: 360) == 72 && ChartSize.small.linesOnly)
        #expect(ChartSize.medium.height(width: 360) == 120)
        #expect(ChartSize.large.height(width: 360) == 180 && ChartSize.large.height(width: 900) == 300)
    }
}
