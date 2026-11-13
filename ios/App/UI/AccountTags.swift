import CooldownCore
import SwiftUI

/// CCRM-110 (Account Tags) and CCRM-115 (Row Tag Glyph), wireframe design/2026-11-10-v20-feedback-wireframes.html rev B §6
/// and the 2026-11-11 addendum: the tag's glyph and chip, the pickers (account page row, touch-and-hold submenu, the Add
/// account sheet's control), and the Summary's header — the All · Personal · Work control and the sort menu. Every account
/// is Personal or Work; there is no None.
enum TagGlyph {
    /// SF Symbols `person` for Personal, `briefcase` for Work (the chip's ink, never a colour; Robin, 2026-11-10).
    static func symbol(_ tag: AccountTag) -> String {
        switch tag {
        case .personal: "person"
        case .work: "briefcase"
        }
    }
}

/// CCRM-115: the glyph that leads a Summary row, before the provider's mark (or between the rank disc and the name on a
/// one-line row): the chip's 12 pt symbol in a 14 pt box on a full row, 11 pt in a 12 pt box on one-line and snapshot rows,
/// `sub` ink, never a colour or the word. Hidden from VoiceOver: the row's name reads "Teams, Work account".
/// CCRM-118 (Header Glyphs) §9: a tag whose mark is off (`marked` false) leaves a clear box of the same size while the
/// column exists, so the names align.
struct TagRowGlyph: View {
    let tag: AccountTag
    var compact = false
    var marked = true
    @Environment(\.palette) private var p
    var body: some View {
        Group {
            if marked {
                Image(systemName: TagGlyph.symbol(tag)).font(.system(size: compact ? 11 : 12, weight: .regular))
                    .foregroundStyle(p.sub)
            } else {
                Color.clear
            }
        }
        .frame(width: compact ? 12 : 14, height: compact ? 12 : 14)
        .accessibilityHidden(true)
    }
}

/// The neutral chip after the plan chip in Settings: an outline, a 12 pt glyph before the word (Robin, 2026-11-10:
/// glyph + text). On a row that cannot hold the word beside a warning, the word drops and the glyph stays (addendum T4).
struct TagChip: View {
    let tag: AccountTag
    var size: CGFloat = 11
    var showWord = true
    @Environment(\.palette) private var p
    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: TagGlyph.symbol(tag)).font(.system(size: 12, weight: .regular))
            if showWord { Text(tag.label).font(.system(size: size, weight: .semibold)) }
        }
        .foregroundStyle(p.sub)
        .padding(.horizontal, 7).padding(.vertical, 2)
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(p.sub.opacity(0.5), lineWidth: 1))
        .fixedSize()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Tag: \(tag.label)")
    }
}

/// Personal · Work as picker rows, glyphs leading, a check on the current one.
struct TagPickerRows: View {
    let selection: AccountTag
    let onSelect: (AccountTag) -> Void
    var body: some View {
        Picker("Tag", selection: Binding(get: { selection }, set: { onSelect($0) })) {
            ForEach(AccountTag.allCases, id: \.self) { tag in
                Label(tag.label, systemImage: TagGlyph.symbol(tag)).tag(tag)
            }
        }
    }
}

/// The touch-and-hold menu's "Tag" submenu; its icon is always the current glyph.
struct TagSubmenu: View {
    @Environment(AppModel.self) private var model
    let account: Account
    var body: some View {
        Menu {
            TagPickerRows(selection: account.accountTag) { model.setTag(account.key, $0) }
        } label: {
            Label("Tag", systemImage: TagGlyph.symbol(account.accountTag))
        }
    }
}

/// The Add account sheet's "This account is" control (addendum §Adding and tagging, T1 (i)): label 13.5 pt, a two-segment
/// bar in the Summary control's style (36 pt high, radius 10, 15 pt words with 14 pt glyphs, the chosen one a 13% fill and
/// semibold), caption 12.5 pt. Personal is selected every time the sheet opens.
struct AddTagControl: View {
    @Binding var tag: AccountTag
    @Environment(\.palette) private var p

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("This account is").font(.system(size: 13.5)).foregroundStyle(p.txt)
            HStack(spacing: 0) {
                ForEach(Array(AccountTag.allCases.enumerated()), id: \.element) { i, option in
                    let on = tag == option
                    Button { tag = option } label: {
                        HStack(spacing: 6) {
                            Image(systemName: TagGlyph.symbol(option)).font(.system(size: 14)).accessibilityHidden(true)
                            Text(option.label).font(.system(size: 15, weight: on ? .semibold : .regular))
                        }
                        .foregroundStyle(p.ink)
                        .frame(maxWidth: .infinity, minHeight: 36)
                        .background(on ? p.ink.opacity(0.13) : Color.clear)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .overlay(alignment: .leading) { if i > 0 { Rectangle().fill(p.sub.opacity(0.5)).frame(width: 1) } }
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            .frame(height: 36)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(p.sub.opacity(0.5), lineWidth: 1))
            .accessibilityElement(children: .contain)
            .accessibilityLabel("This account is")
            Text("Groups it on the Summary. You can change it later.").font(.system(size: 12.5)).foregroundStyle(p.sub)
        }
    }
}

/// The Summary's All · person N · briefcase N control (CCRM-118 (Header Glyphs) §8): 28 pt high, an outlined segmented bar;
/// "All" a word, the two tags a 14 pt glyph + 4 + the group's size among all accounts (13 pt, tabular). The chosen segment a
/// tonal fill and semibold, digit included. An empty group keeps full ink with its 0: a tap answers instead of selecting
/// (`onDisabled` shows "No Work accounts yet. Tag one in Settings." for 3 s). Always shows both tags, whatever Tag marks says.
struct SummaryFilterControl: View {
    let resolved: SummaryFilter.Resolved
    /// The group sizes among all accounts, never the rows shown.
    var counts: [AccountTag: Int] = [:]
    var fill = false
    let onSelect: (SummaryFilter) -> Void
    var onDisabled: (SummaryFilter) -> Void = { _ in }
    @Environment(\.palette) private var p

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(SummaryFilter.allCases.enumerated()), id: \.element) { i, filter in
                let on = resolved.filter == filter
                let enabled = resolved.segmentsEnabled?[filter] ?? true
                let weight: Font.Weight = on ? .semibold : .regular
                Button { if enabled { onSelect(filter) } else { onDisabled(filter) } } label: {
                    Group {
                        if let tag = filter.tag {
                            HStack(spacing: 4) {
                                Image(systemName: TagGlyph.symbol(tag)).font(.system(size: 14, weight: weight)).accessibilityHidden(true)
                                Text("\(counts[tag] ?? 0)").font(.system(size: 13, weight: weight)).monospacedDigit()
                            }
                        } else {
                            Text(filter.label).font(.system(size: 13, weight: weight))
                        }
                    }
                    .foregroundStyle(p.ink)
                    .padding(.horizontal, 9)
                    .frame(minWidth: filter.tag == nil ? 33 : 45, maxWidth: fill ? .infinity : nil, minHeight: 28)
                    .background(on ? p.ink.opacity(0.13) : Color.clear)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .overlay(alignment: .leading) { if i > 0 { Rectangle().fill(p.sub.opacity(0.5)).frame(width: 1) } }
                .accessibilityLabel(Self.spoken(filter, count: filter.tag.map { counts[$0] ?? 0 } ?? counts.values.reduce(0, +)))
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

    /// "Work, 3 accounts" · "Work, 1 account" · "Work, no accounts"; All reads the total.
    static func spoken(_ filter: SummaryFilter, count: Int) -> String {
        return "\(filter.label), " + (count == 0 ? "no accounts" : count == 1 ? "1 account" : "\(count) accounts")
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

/// CCRM-118 (Header Glyphs) §8: each Summary order's glyph — the button always shows the current order's, the menu rows
/// show their own. line.3.horizontal (your order), ring 20 % (most room first), timer (resets soonest), ring 85 % (most
/// used); the two rings are drawn here (a template image, so the button and the menu rows share one glyph).
enum SortGlyph {
    @MainActor
    static func image(_ order: SummaryOrder) -> Image {
        switch order {
        case .yours: Image(systemName: "line.3.horizontal")
        case .room: Image(uiImage: ring(0.20))
        case .resets: Image(systemName: "timer")
        case .used: Image(uiImage: ring(0.85))
        }
    }

    /// A template ring, 18 pt box: a faint full track and the arc (`fraction` of the turn, clockwise from the top) in full.
    @MainActor
    static func ring(_ fraction: Double) -> UIImage {
        if let hit = cache[fraction] { return hit }
        let side: CGFloat = 18, line: CGFloat = 2.2
        let r = (side - line) / 2, c = CGPoint(x: side / 2, y: side / 2)
        let image = UIGraphicsImageRenderer(size: CGSize(width: side, height: side)).image { ctx in
            let g = ctx.cgContext
            g.setLineWidth(line)
            g.setLineCap(.round)
            g.setStrokeColor(UIColor.black.withAlphaComponent(0.3).cgColor)
            g.addArc(center: c, radius: r, startAngle: 0, endAngle: .pi * 2, clockwise: false)
            g.strokePath()
            g.setStrokeColor(UIColor.black.cgColor)
            g.addArc(center: c, radius: r, startAngle: -.pi / 2, endAngle: -.pi / 2 + .pi * 2 * fraction, clockwise: false)
            g.strokePath()
        }.withRenderingMode(.alwaysTemplate)
        cache[fraction] = image
        return image
    }
    @MainActor private static var cache: [Double: UIImage] = [:]
}

/// The Summary's sort control: the current order's glyph and a chevron, always — the words are only in the menu (a native
/// pull-down with the order's glyph on each row and a check on the current one). Writes the same setting as Appearance ›
/// Summary order, which stays. One width for every order.
struct SummarySortMenu: View {
    let order: SummaryOrder
    let onSelect: (SummaryOrder) -> Void
    @Environment(\.palette) private var p
    @Environment(\.menusAsLabels) private var asLabel

    var body: some View {
        if asLabel { label } else { menu }
    }

    private var menu: some View {
        Menu {
            Picker("Sort", selection: Binding(get: { order }, set: { onSelect($0) })) {
                ForEach(SummaryOrder.allCases, id: \.self) { o in Label { Text(o.label) } icon: { SortGlyph.image(o) }.tag(o) }
            }
        } label: { label }
        .accessibilityLabel("Sort: \(order.header)")
    }

    private var label: some View {
        HStack(spacing: 4) {
            SortGlyph.image(order).resizable().scaledToFit().frame(width: 14, height: 14)
            Image(systemName: "chevron.down").font(.system(size: 9, weight: .semibold))
        }
        .foregroundStyle(p.txt)
        .padding(.horizontal, 7).padding(.vertical, 3)
        .frame(height: 28)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(p.line, lineWidth: 1))
        .fixedSize()
    }
}
