import Foundation
import SwiftUI

/// CCRM-94 (iOS App): the main screen's card layout, ported from Android's `CardLayout` (CCRM-25 (Card Layout), CCRM-35
/// (Layout Reset), CCRM-72 (Main Screen Redesign)) — per account: which cards show, in what order, and which are folded
/// behind "More". Every mutator keeps the shape valid through `normalized`.
enum CardID: String, CaseIterable, Codable, Identifiable {
    case session, weekly, credits
    var id: String { rawValue }
    var title: String {
        switch self {
        case .session: "5-hour window"
        case .weekly: "7-day window"
        case .credits: "Usage credits"
        }
    }
}

struct CardLayout: Codable, Equatable {
    var order: [CardID]
    var hidden: Set<CardID>
    var more: Set<CardID>

    /// Every card shown, in declaration order.
    static let standard = CardLayout(order: CardID.allCases, hidden: [], more: [])

    /// The cards above More, in order.
    var main: [CardID] { order.filter { !hidden.contains($0) && !more.contains($0) } }
    /// The cards folded behind More, in order.
    var folded: [CardID] { order.filter { more.contains($0) && !hidden.contains($0) } }

    /// Drops unknown ids, appends missing ones, keeps hidden and more inside order, lets hidden win over more, and
    /// keeps at least one card shown above More (the first one is brought back when none is).
    var normalized: CardLayout {
        var seen = Set<CardID>()
        var order = self.order.filter { seen.insert($0).inserted }
        order += CardID.allCases.filter { !seen.contains($0) }
        var hidden = self.hidden.intersection(order)
        var more = self.more.intersection(order).subtracting(hidden)
        if !order.contains(where: { !hidden.contains($0) && !more.contains($0) }), let first = order.first {
            hidden.remove(first)
            more.remove(first)
        }
        return CardLayout(order: order, hidden: hidden, more: more)
    }

    func moving(fromOffsets source: IndexSet, toOffset destination: Int) -> CardLayout {
        var copy = self
        copy.order.move(fromOffsets: source, toOffset: destination)
        return copy.normalized
    }
    func hiding(_ id: CardID) -> CardLayout { var c = self; c.hidden.insert(id); return c.normalized }
    func showing(_ id: CardID) -> CardLayout { var c = self; c.hidden.remove(id); return c.normalized }
    func folding(_ id: CardID) -> CardLayout { var c = self; c.more.insert(id); return c.normalized }
    func unfolding(_ id: CardID) -> CardLayout { var c = self; c.more.remove(id); return c.normalized }

    /// Whether hiding or folding `id` would still leave a card above More (Android's `canHide`/`canFold`). Only the
    /// cards the account has count, so an account without credits cannot hide its last real card.
    func canHide(_ id: CardID, among available: Set<CardID> = Set(CardID.allCases)) -> Bool {
        order.contains { $0 != id && available.contains($0) && !hidden.contains($0) && !more.contains($0) }
    }
    func canFold(_ id: CardID, among available: Set<CardID> = Set(CardID.allCases)) -> Bool { canHide(id, among: available) }

    // MARK: - Storage, per account

    static func load(_ key: String) -> CardLayout {
        guard let data = UserDefaults.standard.data(forKey: "layout.\(key)"),
              let layout = try? JSONDecoder().decode(CardLayout.self, from: data) else { return .standard }
        return layout.normalized
    }
    static func save(_ layout: CardLayout, _ key: String) {
        if let data = try? JSONEncoder().encode(layout.normalized) { UserDefaults.standard.set(data, forKey: "layout.\(key)") }
    }
}

/// CCRM-72 (Main Screen Redesign) Density: Comfortable keeps every chart open; Compact folds each card to a title, a bar
/// and one line — tap it to open the chart in place.
enum Density: String, CaseIterable {
    case comfortable, compact
}

/// CCRM-75 (Chart Height): how tall an open chart draws. Small keeps just the lines; Large follows the width.
enum ChartSize: String, CaseIterable {
    case small, medium, large

    func height(width: CGFloat) -> CGFloat {
        switch self {
        case .small: 72
        case .medium: 120
        case .large: min(max(width * 0.45, 180), 300)
        }
    }
    var linesOnly: Bool { self == .small }
}
