import CooldownCore
import SwiftUI

/// CCRM-110 (Account Tags), wireframe design/2026-11-10-v20-feedback-wireframes.html rev B §6: the tag's glyph and chip,
/// the pickers (account page row, touch-and-hold submenu), and the Summary's header — the All · Personal · Work control
/// and the sort menu.
enum TagGlyph {
    /// SF Symbols `person` for Personal, `briefcase` for Work (the chip's ink, never a colour; Robin, 2026-11-10).
    static func symbol(_ tag: AccountTag) -> String {
        switch tag {
        case .personal: "person"
        case .work: "briefcase"
        }
    }
}

/// The neutral chip after the plan chip in Settings: an outline, a 12 pt glyph before the word (Robin, 2026-11-10:
/// glyph + text).
struct TagChip: View {
    let tag: AccountTag
    var size: CGFloat = 11
    @Environment(\.palette) private var p
    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: TagGlyph.symbol(tag)).font(.system(size: 12, weight: .regular))
            Text(tag.label).font(.system(size: size, weight: .semibold))
        }
        .foregroundStyle(p.sub)
        .padding(.horizontal, 7).padding(.vertical, 2)
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(p.sub.opacity(0.5), lineWidth: 1))
        .fixedSize()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Tag: \(tag.label)")
    }
}

/// None · Personal · Work as picker rows, glyphs leading. `selection` is the stored tag; nil is None.
struct TagPickerRows: View {
    let selection: AccountTag?
    let onSelect: (AccountTag?) -> Void
    var body: some View {
        Picker("Tag", selection: Binding(get: { selection }, set: { onSelect($0) })) {
            Text("None").tag(AccountTag?.none)
            ForEach(AccountTag.allCases, id: \.self) { tag in
                Label(tag.label, systemImage: TagGlyph.symbol(tag)).tag(AccountTag?.some(tag))
            }
        }
    }
}

/// The touch-and-hold menu's "Tag" submenu.
struct TagSubmenu: View {
    @Environment(AppModel.self) private var model
    let account: Account
    var body: some View {
        Menu {
            TagPickerRows(selection: account.accountTag) { model.setTag(account.key, $0) }
        } label: {
            Label("Tag", systemImage: account.accountTag.map(TagGlyph.symbol) ?? "tag")
        }
    }
}

/// The Summary's All · Personal · Work control: 13 pt, 28 pt high, an outlined segmented bar; the chosen segment a tonal fill
/// and semibold, a segment with no accounts disabled at 38%. Shown only once an account carries a tag.
struct SummaryFilterControl: View {
    let resolved: SummaryFilter.Resolved
    var fill = false
    let onSelect: (SummaryFilter) -> Void
    @Environment(\.palette) private var p

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(SummaryFilter.allCases.enumerated()), id: \.element) { i, filter in
                let on = resolved.filter == filter
                let enabled = resolved.segmentsEnabled?[filter] ?? true
                Button { onSelect(filter) } label: {
                    Text(filter.label).font(.system(size: 13, weight: on ? .semibold : .regular))
                        .foregroundStyle(p.ink)
                        .padding(.horizontal, 9)
                        .frame(maxWidth: fill ? .infinity : nil, minHeight: 28)
                        .background(on ? p.ink.opacity(0.13) : Color.clear)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(!enabled)
                .opacity(enabled ? 1 : 0.38)
                .overlay(alignment: .leading) { if i > 0 { Rectangle().fill(p.sub.opacity(0.5)).frame(width: 1) } }
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .frame(height: 28)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(p.sub.opacity(0.5), lineWidth: 1))
        .fixedSize(horizontal: !fill, vertical: true)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Filter")
    }
}

private struct MenusAsLabelsKey: EnvironmentKey { static let defaultValue = false }
extension EnvironmentValues {
    /// `ImageRenderer` cannot draw a `Menu` (it paints a no-entry sign), so the render tests ask for the button's own label.
    var menusAsLabels: Bool {
        get { self[MenusAsLabelsKey.self] }
        set { self[MenusAsLabelsKey.self] = newValue }
    }
}

/// "your order ▾": the four Summary orders as a native pull-down with a check on the current one; it writes the same
/// setting as Appearance › Summary order, which stays. At a narrow width it shows its glyph alone ("⇅ ▾").
struct SummarySortMenu: View {
    let order: SummaryOrder
    var glyphOnly = false
    let onSelect: (SummaryOrder) -> Void
    @Environment(\.palette) private var p
    @Environment(\.menusAsLabels) private var asLabel

    var body: some View {
        if asLabel { label } else { menu }
    }

    private var menu: some View {
        Menu {
            Picker("Sort", selection: Binding(get: { order }, set: { onSelect($0) })) {
                ForEach(SummaryOrder.allCases, id: \.self) { Text($0.label).tag($0) }
            }
        } label: { label }
        .accessibilityLabel("Sort: \(order.header)")
    }

    private var label: some View {
        HStack(spacing: 4) {
            if glyphOnly { Image(systemName: "arrow.up.arrow.down").font(.system(size: 12, weight: .medium)) }
            else { Text(order.header).font(.system(size: 13)).lineLimit(1) }
            Image(systemName: "chevron.down").font(.system(size: 9, weight: .semibold))
        }
        .foregroundStyle(p.txt)
        .padding(.horizontal, 7).padding(.vertical, 3)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(p.line, lineWidth: 1))
        .fixedSize()
    }
}
