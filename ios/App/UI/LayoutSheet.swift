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
        let layout = model.layout(accountKey)
        let available = self.available
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
                    Button("Reset layout") { model.setLayout(.standard, for: accountKey) }
                }
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func row(_ id: CardID, layout: CardLayout, behindMore: Bool) -> some View {
        let shown = !layout.hidden.contains(id)
        let canHide = !shown || layout.canHide(id, among: available)
        return HStack(spacing: 12) {
            Image(systemName: shown ? icon(id) : "minus.circle").foregroundStyle(shown ? p.ink : p.sub).frame(width: 24)
            Text(id.title).foregroundStyle(shown ? p.ink : p.sub)
            Spacer()
            Toggle(id.title, isOn: Binding(
                get: { shown },
                set: { model.setLayout($0 ? layout.showing(id) : layout.hiding(id), for: accountKey) }))
                .labelsHidden()
                .disabled(shown && !canHide)
        }
        .swipeActions(edge: .trailing) {
            if behindMore {
                Button("Above More") { model.setLayout(layout.unfolding(id), for: accountKey) }.tint(.blue)
            } else if layout.canFold(id, among: available) {
                Button("Behind More") { model.setLayout(layout.folding(id), for: accountKey) }.tint(.gray)
            }
        }
    }

    /// The cards this account has (Android lists only those): credits only when the reading carries them.
    private var available: Set<CardID> {
        model.usage(accountKey)?.credits == nil ? [.session, .weekly] : Set(CardID.allCases)
    }

    private func icon(_ id: CardID) -> String {
        switch id {
        case .session: "clock"
        case .weekly: "calendar"
        case .credits: "creditcard"
        }
    }

    /// Reorders within one section; the other section's cards keep their places in the shared order.
    private func move(within behindMore: Bool, _ source: IndexSet, _ destination: Int) {
        let layout = model.layout(accountKey)
        var section = layout.order.filter { layout.more.contains($0) == behindMore }
        section.move(fromOffsets: source, toOffset: destination)
        var it = section.makeIterator()
        var copy = layout
        copy.order = layout.order.map { layout.more.contains($0) == behindMore ? (it.next() ?? $0) : $0 }
        model.setLayout(copy, for: accountKey)
    }
}
