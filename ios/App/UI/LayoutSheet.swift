import CooldownCore
import SwiftUI

/// CCRM-94 (iOS App): the layout sheet, ported from Android's (CCRM-25 (Card Layout), CCRM-35 (Layout Reset)) as an iOS
/// list — per account: show or hide each card, drag to reorder, fold cards behind More, reset. At least one card always
/// stays shown above More.
struct LayoutSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var p
    let accountKey: String

    var body: some View {
        let layout = model.drawnLayout(accountKey)
        let available = Self.sheetCards(layout, self.available)
        NavigationStack {
            List {
                Section {
                    ForEach(layout.order.filter { available.contains($0) && !layout.more.contains($0) }) { id in
                        row(id, layout: layout, behindMore: false)
                    }
                        .onMove { move(within: false, $0, $1) }
                } header: { Text("On the screen") } footer: {
                    if layout.main.filter(available.contains).count <= 1 { Text("At least one card must stay visible above More.").foregroundStyle(p.yellow) }
                }
                Section("Behind More") {
                    let folded = layout.order.filter { available.contains($0) && layout.more.contains($0) }
                    if folded.isEmpty {
                        Text("Nothing folded. Swipe a card left to put it behind More.").font(.system(size: 14)).foregroundStyle(p.sub)
                    }
                    ForEach(folded) { id in row(id, layout: layout, behindMore: true) }
                        .onMove { move(within: true, $0, $1) }
                }
            }
            .environment(\.editMode, .constant(.active))
            .navigationTitle("Layout")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .top) {
                Text("Show, hide and reorder the cards on \(model.account(accountKey)?.label ?? "this account")’s screen.")
                    .font(.system(size: 14)).foregroundStyle(p.txt).frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20).padding(.vertical, 6)
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Reset layout") { var reset = CardLayout.standard; reset.reordered = false; model.setLayout(reset, for: accountKey) }
                }
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func row(_ id: CardID, layout: CardLayout, behindMore: Bool) -> some View {
        let available = Self.sheetCards(layout, self.available)
        let shown = !layout.hidden.contains(id)
        let canHide = !shown || layout.canHide(id, among: available)
        return HStack(spacing: 12) {
            Image(systemName: shown ? icon(id) : "minus.circle").foregroundStyle(shown ? p.ink : p.sub).frame(width: 24)
            Text(id.title).foregroundStyle(shown ? p.ink : p.sub)
            Spacer()
            Toggle(id.title, isOn: Binding(
                get: { shown },
                set: { show in model.setLayout(Self.both(id, layout) { show ? $0.showing($1) : $0.hiding($1) }, for: accountKey) }))
                .labelsHidden()
                .disabled(shown && !canHide)
        }
        .swipeActions(edge: .trailing) {
            if behindMore {
                Button("Above More") { model.setLayout(Self.both(id, layout) { $0.unfolding($1) }, for: accountKey) }.tint(.blue)
            } else if layout.canFold(id, among: available) {
                Button("Behind More") { model.setLayout(Self.both(id, layout) { $0.folding($1) }, for: accountKey) }.tint(.gray)
            }
        }
    }

    /// The cards this account has (Android lists only those): credits only when the reading carries them.
    private var available: Set<CardID> {
        model.usage(accountKey)?.credits == nil || !Display.creditsVisible(accountKey) ? [.session, .weekly] : Set(CardID.allCases)
    }

    private func icon(_ id: CardID) -> String {
        switch id {
        case .session: "clock"
        case .weekly: "calendar"
        case .credits: "creditcard"
        }
    }

    /// Reorders within one section; the other section's cards keep their places in the shared order. The Usage row's
    /// second window id travels right behind it.
    private func move(within behindMore: Bool, _ source: IndexSet, _ destination: Int) {
        model.setLayout(Self.moved(model.drawnLayout(accountKey), available, behindMore, source, destination), for: accountKey)
    }

    /// The reorder itself, pure: the sheet's rows of one section move; the Usage row's other window id travels right
    /// behind it.
    nonisolated static func moved(_ layout: CardLayout, _ available: Set<CardID>, _ behindMore: Bool,
                                  _ source: IndexSet, _ destination: Int) -> CardLayout {
        let sheet = sheetCards(layout, available)
        var section = layout.order.filter { sheet.contains($0) && layout.more.contains($0) == behindMore }
        section.move(fromOffsets: source, toOffset: destination)
        var it = section.makeIterator()
        let rep = usageID(layout)
        var copy = layout
        copy.order = layout.order.filter { $0 == rep || $0 == .credits }
            .map { sheet.contains($0) && layout.more.contains($0) == behindMore ? (it.next() ?? $0) : $0 }
            .flatMap { $0 == rep ? [rep, other(rep)] : [$0] }
        copy.reordered = true
        return copy
    }

    // MARK: CCRM-103 (Hybrid Notification): the two window cards are one row, "Usage"

    /// The row's id: the window card the tile is drawn under (`AccountCards.merged`: the first shown above More, else
    /// behind it), else the first in the order — so a layout saved before the tile shows the row as the screen does.
    nonisolated static func usageID(_ layout: CardLayout) -> CardID {
        (layout.main + layout.folded + layout.order).first { $0 != .credits } ?? .weekly
    }
    nonisolated static func other(_ id: CardID) -> CardID { id == .session ? .weekly : .session }

    /// The sheet's rows: the Usage row and, when the reading carries them, the credits.
    nonisolated static func sheetCards(_ layout: CardLayout, _ available: Set<CardID>) -> Set<CardID> {
        available.subtracting([other(usageID(layout))])
    }

    /// Hiding, showing, folding or unfolding the Usage row does it to both window ids, so a flag left on the other
    /// (by a layout saved before the tile) can never contradict the row.
    nonisolated static func both(_ id: CardID, _ layout: CardLayout, _ op: (CardLayout, CardID) -> CardLayout) -> CardLayout {
        id == .credits ? op(layout, id) : op(op(layout, .session), .weekly)
    }
}
