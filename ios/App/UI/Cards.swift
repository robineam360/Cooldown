import CooldownCore
import SwiftUI

/// CCRM-94 (iOS App): the account cards of wireframe rev B §1–2 — the Android v1.7 cards, nothing removed.
struct CardBox<Content: View>: View {
    @ViewBuilder var content: Content
    @Environment(\.palette) private var p
    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(p.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

/// The 5-hour card: title, number, bar with pace tick, the full chart when expanded, the pace and
/// projection lines, and both reset lines. In Compact (or behind More) it folds to a title, the bar and one line,
/// and a tap opens the chart in place (CCRM-72 (Main Screen Redesign)).
struct WindowCard: View {
    let content: WindowContent
    let accent: Color
    @Binding var expanded: Bool
    var collapsible = true
    var chartHeight: CGFloat = 150
    var linesOnly = false
    var dimmed = false
    var rightResetLine: String?
    @Environment(\.palette) private var p

    var body: some View {
        CardBox {
            header
            UsageBar(pct: content.pct, pace: content.pace, accent: accent, height: 11, dimmed: dimmed)
                .padding(.top, 12)
            if expanded || !collapsible {
                if let chart = content.chart {
                    TrendChart(model: chart, accent: accent, dimmed: dimmed, linesOnly: linesOnly)
                        .frame(height: chartHeight)
                        .padding(.top, 12)
                }
                if content.notStarted {
                    Text(content.resetsIn).font(.system(size: 14.5)).foregroundStyle(p.sub).padding(.top, 10)
                } else {
                    Text(content.paceLine)
                        .foregroundStyle(content.paceLineAlarm ? p.red : p.txt)
                        .font(.system(size: 14.5)).padding(.top, 6)
                    if let line = content.projectionLine {
                        Text(line).foregroundStyle(p.txt).font(.system(size: 14.5)).padding(.top, 4)
                    }
                    HStack {
                        Text(content.leadReset)
                        Spacer(minLength: 8)
                        Text(rightResetLine ?? content.secondReset)
                    }
                    .font(.system(size: 14.5)).foregroundStyle(p.txt).padding(.top, 10)
                }
            } else {
                Text(CardContent.compactLine(content))
                    .font(.system(size: 14.5)).foregroundStyle(content.paceLineAlarm ? p.red : p.txt).padding(.top, 10)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(content.title), \(content.pct.map { "\(Int($0)) percent" } ?? "no reading"). \(content.paceLine). \(content.resetsIn).")
    }

    private var header: some View {
        Button { if collapsible { withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() } } } label: {
            HStack(spacing: 8) {
                Text(content.title).foregroundStyle(p.txt)
                Spacer()
                Display.pctText(content.pct, size: 19)
                    .font(.system(size: 19, weight: .bold)).monospacedDigit()
                    .foregroundStyle(dimmed ? p.sub : ((content.pct ?? 0) >= 100 ? p.red : p.ink))
                if collapsible {
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(p.sub)
                }
            }
            .font(.system(size: 17))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// The 7-day card: "All" and, when the account has one, a model cap side by side (Android's two halves).
struct WeeklyCard: View {
    let all: WindowContent
    let cap: WindowContent?
    let accent: Color
    @Binding var expanded: Bool
    var collapsible = true
    var chartHeight: CGFloat = 120
    var linesOnly = false
    var dimmed = false
    @Environment(\.palette) private var p

    var body: some View {
        CardBox {
            Button { if collapsible { withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() } } } label: {
                HStack {
                    Text("7-day window").foregroundStyle(p.txt)
                    Spacer()
                    if collapsible {
                        Image(systemName: expanded ? "chevron.up" : "chevron.down")
                            .font(.system(size: 15, weight: .semibold)).foregroundStyle(p.sub)
                    }
                }
                .font(.system(size: 17)).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.bottom, 10)
            if let cap {
                HStack(alignment: .top, spacing: 16) { half(all, short: true); half(cap, short: true) }
            } else {
                half(all, short: false)
            }
            if !(expanded || !collapsible) {
                Text(CardContent.compactLine(all)).font(.system(size: 13.5))
                    .foregroundStyle(all.paceLineAlarm ? p.red : p.txt).padding(.top, 8)
            }
        }
    }

    private func half(_ c: WindowContent, short: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(c.title).foregroundStyle(p.txt)
                Spacer()
                Display.pctText(c.pct, size: 17).font(.system(size: 17, weight: .bold)).monospacedDigit()
                    .foregroundStyle(dimmed ? p.sub : p.ink)
            }
            .font(.system(size: 16))
            UsageBar(pct: c.pct, pace: c.pace, accent: accent, height: 9, dimmed: dimmed).padding(.top, 10)
            if expanded || !collapsible {
                if let chart = c.chart {
                    TrendChart(model: chart, accent: accent, dimmed: dimmed, linesOnly: linesOnly)
                        .frame(height: chartHeight).padding(.top, 10)
                }
                if !c.notStarted {
                    Text(c.paceLine).foregroundStyle(c.paceLineAlarm ? p.red : p.txt).font(.system(size: 13.5)).padding(.top, 6)
                }
                if let line = c.projectionLine {
                    Text(line).foregroundStyle(p.txt).font(.system(size: 13.5)).padding(.top, 3)
                }
                Text(short || c.notStarted ? c.leadReset
                     : "\(c.leadReset) · \(c.secondReset.replacingOccurrences(of: "Resets at ", with: ""))")
                    .font(.system(size: 13.5)).foregroundStyle(p.txt).padding(.top, 6)
            }
        }
    }
}

struct CreditsCard: View {
    let text: String
    let pct: Int?
    let isBalance: Bool
    @Environment(\.palette) private var p
    var body: some View {
        CardBox {
            HStack(spacing: 8) {
                Text(isBalance ? "Credits" : "Usage credits").fontWeight(isBalance ? .regular : .semibold).foregroundStyle(p.txt)
                if !isBalance { Text(text).foregroundStyle(p.txt) }
                Spacer()
                if isBalance {
                    Text(text).fontWeight(.semibold)
                } else if let pct {
                    Text("\(pct)%").fontWeight(.bold)
                    Image(systemName: "info.circle").foregroundStyle(p.sub).font(.system(size: 14))
                }
            }
            .font(.system(size: 16))
        }
    }
}

struct NoticeView: View {
    let notice: Notice
    var onAction: () -> Void = {}
    @Environment(\.palette) private var p
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(notice.title).font(.system(size: 15, weight: .semibold)).foregroundStyle(p.ink)
            Text(notice.body).font(.system(size: 14)).foregroundStyle(p.txt)
            if let action = notice.action {
                Button(action, action: onAction)
                    .font(.system(size: 14.5, weight: .semibold))
                    .padding(.horizontal, 14).padding(.vertical, 7)
                    .background(p.ink, in: Capsule()).foregroundStyle(p.bg)
                    .padding(.top, 8)
            }
        }
        .padding(.horizontal, 13).padding(.vertical, 11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tint.opacity(0.16), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
    private var tint: Color { notice.tone == .error ? p.red : p.orange }
}

/// One account's cards, in the order, visibility and fold the user chose (the layout sheet), drawn at the chosen
/// density and chart height. Used by the iPhone tabs and both iPad halves; the iPad has room, so it shows what's
/// behind More inline.
struct AccountCards: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    let account: Account
    var twoAcross = false
    /// The card's width, for Large chart heights.
    var cardWidth: CGFloat = 338
    var onSignIn: () -> Void = {}
    @AppStorage("density") private var density = Density.comfortable.rawValue
    @AppStorage("chartSize") private var chartSize = ChartSize.medium.rawValue
    /// Which cards the user opened or closed; until then, the density's defaults (wireframe rev B: 5-hour open and
    /// 7-day folded on iPhone, both open on iPad; Compact folds everything).
    @State private var open: Set<CardID>?
    @State private var moreOpen = false

    private var defaultOpen: Set<CardID> {
        if Density(rawValue: density) == .compact { return [] }
        // A weekly-only plan has no 5h card: its Weekly card opens in its place.
        if model.usage(account.key)?.session == nil { return [.weekly] }
        return twoAcross ? [.session, .weekly] : [.session]
    }

    var body: some View {
        let accent = account.accent(scheme)
        let state = model.states[account.key] ?? AccountState()
        let snapshot = model.snapshots[account.key]
        let notice = Notice.forAccount(label: account.label, state: state, snapshot: snapshot, nowMs: model.nowMs)
        let layout = model.layout(account.key)
        VStack(spacing: 12) {
            if let notice { NoticeView(notice: notice, onAction: onSignIn) }
            if state.lastKind != "plan", model.usage(account.key) != nil {
                if twoAcross {
                    let ids = layout.order.filter { !layout.hidden.contains($0) }
                    let windows = ids.filter { $0 != .credits }
                    HStack(alignment: .top, spacing: 14) {
                        ForEach(windows) { card($0, accent: accent, notice: notice, folded: false) }
                    }
                } else {
                    ForEach(layout.main) { card($0, accent: accent, notice: notice, folded: false) }
                    if !layout.folded.isEmpty {
                        Button { withAnimation(.easeInOut(duration: 0.2)) { moreOpen.toggle() } } label: {
                            HStack(spacing: 4) {
                                Text("More")
                                Image(systemName: moreOpen ? "arrowtriangle.up.fill" : "arrowtriangle.down.fill").font(.system(size: 8))
                                Text("· \(layout.folded.count) \(layout.folded.count == 1 ? "card" : "cards")")
                            }
                            .font(.system(size: 15)).foregroundStyle(p.txt)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 2).padding(.vertical, 4)
                        }
                        .buttonStyle(.plain)
                        if moreOpen { ForEach(layout.folded) { card($0, accent: accent, notice: notice, folded: true) } }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func card(_ id: CardID, accent: Color, notice: Notice?, folded: Bool) -> some View {
        let usage = model.usage(account.key)
        let history = model.histories[account.key] ?? []
        let snapshot = model.snapshots[account.key]
        let dimmed = notice?.tone == .error || notice?.tone == .warning
        let size = ChartSize(rawValue: chartSize) ?? .medium
        let height = size.height(width: cardWidth)
        // A card behind More starts folded whatever the density (as on Android).
        let binding = Binding(
            get: { (open ?? defaultOpen.subtracting(folded ? [id] : [])).contains(id) },
            set: { var o = open ?? defaultOpen; if $0 { o.insert(id) } else { o.remove(id) }; open = o })
        switch id {
        case .session:
            if let five = CardContent.window(title: "5-hour window", window: usage?.session, kind: HistoryStats.sessionKind,
                                             history: history, nowMs: model.nowMs, use24h: Display.use24h) {
                WindowCard(content: five, accent: accent, expanded: binding, chartHeight: height,
                           linesOnly: size.linesOnly, dimmed: dimmed,
                           rightResetLine: dimmed ? "as of \(CardContent.clock(snapshot?.fetchedAtMs ?? 0, Display.use24h, .current))" : nil)
            }
        case .weekly:
            if let all = CardContent.window(title: "All", window: usage?.weekly, kind: HistoryStats.weeklyKind,
                                            history: history, nowMs: model.nowMs, use24h: Display.use24h) {
                let cap = usage?.modelCaps.first.flatMap {
                    CardContent.window(title: $0.modelName.replacingOccurrences(of: "Claude ", with: ""), window: $0.window,
                                       kind: HistoryStats.weeklyKind, history: [], nowMs: model.nowMs, use24h: Display.use24h)
                }
                WeeklyCard(all: all, cap: cap, accent: accent, expanded: binding,
                           chartHeight: cap == nil ? height : min(height, 120), linesOnly: size.linesOnly, dimmed: dimmed)
            }
        case .credits:
            if Display.creditsVisible(account.key), let credits = CardContent.credits(usage?.credits) {
                CreditsCard(text: credits.text, pct: credits.pct, isBalance: credits.pct == nil)
            }
        }
    }
}
