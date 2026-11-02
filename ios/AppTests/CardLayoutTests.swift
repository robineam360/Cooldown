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

    /// CCRM-103 (Hybrid Notification): the layout sheet shows the tile as one "Usage" row (Robin, 2026-11-02).
    @Test func theTileIsOneUsageRow() {
        #expect(CardID.session.title == "Usage" && CardID.weekly.title == "Usage" && CardID.credits.title == "Usage credits")
        let all: Set<CardID> = [.session, .weekly, .credits]
        // The row stands for the window card the tile is drawn under.
        #expect(LayoutSheet.usageID(.standard) == .session)
        #expect(LayoutSheet.sheetCards(.standard, all) == [.session, .credits])
        let legacy = CardLayout(order: [.session, .weekly, .credits], hidden: [.session], more: [])
        #expect(LayoutSheet.usageID(legacy) == .weekly)
        // Hiding or folding the row moves both window ids together.
        let hidden = LayoutSheet.both(.weekly, .standard) { $0.hiding($1) }
        #expect(hidden.hidden.isSuperset(of: [.session, .weekly]) && hidden.main == [.credits])
        let shown = LayoutSheet.both(.weekly, legacy) { $0.showing($1) }
        #expect(shown.hidden.isEmpty)
        let folded = LayoutSheet.both(.session, .standard) { $0.folding($1) }
        #expect(folded.folded == [.session, .weekly])
    }

    /// Moving the Usage row carries its other window id right behind it (6ee08e6 judge F3).
    @Test func movingTheUsageRowKeepsTheWindowsTogether() {
        let all: Set<CardID> = [.session, .weekly, .credits]
        // Rows: [Usage (session), credits]; move credits to the top.
        let up = LayoutSheet.moved(.standard, all, false, IndexSet(integer: 1), 0)
        #expect(up.order == [.credits, .session, .weekly] && up.reordered == true)
        let back = LayoutSheet.moved(up, all, false, IndexSet(integer: 0), 2)
        #expect(back.order == [.session, .weekly, .credits])
    }

    /// CCBG-67 (iPad Sidebar Tap): the highlight follows the tapped row, the pinned one included.
    @Test func theSidebarHighlightFollowsTheTap() {
        // Two accounts: Claude pinned on top, Personal below.
        #expect(IPadLandscape.highlightedKey(selectedKey: "claude", pinnedKey: "claude", bottomKey: "personal",
                                             showsSummary: false) == "claude")
        #expect(IPadLandscape.highlightedKey(selectedKey: "personal", pinnedKey: "claude", bottomKey: "personal",
                                             showsSummary: false) == "personal")
        // Nothing chosen yet: the bottom half's account.
        #expect(IPadLandscape.highlightedKey(selectedKey: nil, pinnedKey: "claude", bottomKey: "personal",
                                             showsSummary: false) == "personal")
        #expect(IPadLandscape.highlightedKey(selectedKey: "claude", pinnedKey: "claude", bottomKey: "personal",
                                             showsSummary: true) == nil)
    }
}
