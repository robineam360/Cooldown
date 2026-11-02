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
    /// CCRM-102 (Spent Week Gate): the week is spent, so this 5h is disabled — figure, bar, chart and reset line at
    /// 45%, no caption, no projection — and this note sits under it at full opacity.
    var weekGate: String? = nil
    @Environment(\.palette) private var p

    var body: some View {
        CardBox {
            Group {
                header
                UsageBar(pct: content.pct, pace: content.pace, accent: accent, height: 11, dimmed: dimmed, showsRoom: weekGate == nil)
                    .padding(.top, 12)
                if weekGate == nil, let caption = content.caption, let mark = content.pace {
                    PaceCaption(text: caption, markPct: mark).padding(.top, 1)
                }
                if weekGate != nil {
                    gatedBody
                } else {
                    openOrFolded
                }
            }
            .opacity(weekGate == nil ? 1 : 0.45)
            if let weekGate {
                Text(weekGate).font(.system(size: 14.5)).foregroundStyle(p.ink).padding(.top, 8)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(content.title), \(content.pct.map { "\(Int($0)) percent" } ?? "no reading"). \(weekGate ?? content.paceLine). \(content.resetsIn).")
    }

    /// Greyed: the chart when open, and the reset line alone (rev B §3b: "on track for ~N%" dropped).
    @ViewBuilder private var gatedBody: some View {
        if expanded || !collapsible, let chart = content.chart {
            TrendChart(model: chart.withoutProjection, accent: accent, dimmed: dimmed, linesOnly: linesOnly)
                .frame(height: chartHeight).padding(.top, 12)
        }
        if content.notStarted {
            Text(content.resetsIn).font(.system(size: 14.5)).foregroundStyle(p.sub).padding(.top, 10)
        } else if expanded || !collapsible {
            HStack {
                Text(content.leadReset)
                Spacer(minLength: 8)
                Text(rightResetLine ?? content.secondReset)
            }
            .font(.system(size: 14.5)).foregroundStyle(p.txt).padding(.top, 10)
        } else {
            Text(content.leadReset).font(.system(size: 14.5)).foregroundStyle(p.txt).padding(.top, 10)
        }
    }

    @ViewBuilder private var openOrFolded: some View {
            if expanded || !collapsible {
                if let chart = content.chart {
                    TrendChart(model: chart, accent: accent, dimmed: dimmed, linesOnly: linesOnly)
                        .frame(height: chartHeight)
                        .padding(.top, 12)
                }
                if content.notStarted {
                    Text(content.resetsIn).font(.system(size: 14.5)).foregroundStyle(p.sub).padding(.top, 10)
                } else {
                    if !content.paceLine.isEmpty {
                        Text(content.paceLineFull)
                            .foregroundStyle(content.paceLineAlarm ? p.red : p.txt)
                            .font(.system(size: 14.5)).padding(.top, 6)
                    }
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

    private var header: some View {
        Button { if collapsible { withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() } } } label: {
            HStack(spacing: 8) {
                Text(content.title).foregroundStyle(p.txt)
                Spacer()
                Display.pctText(content.pct, pace: content.pace, size: 19)
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
                Display.pctText(c.pct, pace: c.pace, size: 17).font(.system(size: 17, weight: .bold)).monospacedDigit()
                    .foregroundStyle(dimmed ? p.sub : (Pace.isSpent(c.pct) ? p.red : p.ink))
                    .lineLimit(1).minimumScaleFactor(0.7)
            }
            .font(.system(size: 16))
            UsageBar(pct: c.pct, pace: c.pace, accent: accent, height: 9, dimmed: dimmed, showsRoom: true).padding(.top, 10)
            if let caption = c.caption, let mark = c.pace {
                PaceCaption(text: caption, markPct: mark, size: short ? 10.5 : 11.5).padding(.top, 1)
            }
            if expanded || !collapsible {
                if let chart = c.chart {
                    TrendChart(model: chart, accent: accent, dimmed: dimmed, linesOnly: linesOnly)
                        .frame(height: chartHeight).padding(.top, 10)
                }
                if !c.notStarted, !c.paceLine.isEmpty {
                    Text(short ? c.paceLine : c.paceLineFull).foregroundStyle(c.paceLineAlarm ? p.red : p.txt).font(.system(size: 13.5))
                        .fixedSize(horizontal: false, vertical: true).padding(.top, 6)
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

/// CCRM-96 (Weekly First) F1: shown once above the cards after the update, on installs that had an account before it.
struct WeeklyFirstNoticeView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Weekly now leads").font(.system(size: 15, weight: .semibold)).foregroundStyle(p.ink)
            Text("The weekly limit is the one every plan has, so it's now the big number everywhere, with 5h beside it. Prefer 5h on top?")
                .font(.system(size: 14)).foregroundStyle(p.txt)
            HStack(spacing: 10) {
                Button("Keep Weekly") { model.answerNotice(keepFiveHourFirst: false) }
                    .padding(.horizontal, 14).padding(.vertical, 7)
                    .background(Provider.claude.accent(scheme), in: Capsule()).foregroundStyle(.black)
                Button("Keep 5h first") { model.answerNotice(keepFiveHourFirst: true) }
                    .padding(.horizontal, 14).padding(.vertical, 7)
                    .overlay(Capsule().stroke(p.line, lineWidth: 1)).foregroundStyle(p.ink)
            }
            .font(.system(size: 14.5, weight: .semibold)).buttonStyle(.plain).padding(.top, 8)
        }
        .padding(.horizontal, 13).padding(.vertical, 11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(p.fill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

struct NoticeView: View {
    let notice: Notice
    var onAction: () -> Void = {}
    @Environment(\.palette) private var p
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(notice.title).font(.system(size: 15, weight: .semibold)).foregroundStyle(p.ink)
            if let evidence = notice.evidence {
                Text(evidence).font(.system(size: 12.5).monospaced()).foregroundStyle(p.sub)
            }
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

    /// Comfortable opens the first window card and folds the second on iPhone (both open on iPad); since CCRM-96
    /// (Weekly First) the first is Weekly unless the Headline is 5-hour or the cards were reordered.
    private var defaultOpen: Set<CardID> {
        if Density(rawValue: density) == .compact { return [] }
        // A weekly-only plan has no 5h card: its Weekly card opens in its place.
        if model.usage(account.key)?.session == nil { return [.weekly] }
        if twoAcross { return [.session, .weekly] }
        return Set(model.drawnLayout(account.key).order.filter { $0 != .credits }.prefix(1))
    }

    var body: some View {
        let accent = account.accent(scheme)
        let state = model.states[account.key] ?? AccountState()
        let snapshot = model.snapshots[account.key]
        let notice = Notice.forAccount(label: account.label, state: state, snapshot: snapshot, nowMs: model.nowMs,
                                       provider: account.provider)
        let layout = model.drawnLayout(account.key)
        VStack(spacing: 12) {
            if model.noticeOwed { WeeklyFirstNoticeView() }
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
                // CCRM-55 (E2): the Claude + GPT pool inside Antigravity, only once it has been used.
                if let pool = model.usage(account.key)?.pools.first(where: \.shown) {
                    poolCards(pool, accent: accent, notice: notice)
                }
            }
        }
    }

    /// The pool's own pair, Weekly then 5h, the same anatomy under a group label. No history is kept for it.
    @ViewBuilder
    private func poolCards(_ pool: UsagePool, accent: Color, notice: Notice?) -> some View {
        let dimmed = notice?.tone == .error || notice?.tone == .warning
        let weekly = CardContent.window(title: "All", window: pool.weekly, kind: HistoryStats.weeklyKind, history: [],
                                        nowMs: model.nowMs, use24h: Display.use24h)
        let five = CardContent.window(title: "5-hour window", window: pool.session, kind: HistoryStats.sessionKind, history: [],
                                      nowMs: model.nowMs, use24h: Display.use24h)
        Text(pool.name).font(.system(size: 15, weight: .semibold)).foregroundStyle(p.txt)
            .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 2).padding(.top, 4)
        if let weekly {
            WeeklyCard(all: weekly, cap: nil, accent: accent, expanded: poolBinding("w"), dimmed: dimmed)
        }
        if let five {
            WindowCard(content: five, accent: accent, expanded: poolBinding("s"), dimmed: dimmed,
                       weekGate: Pace.isSpent(pool.weekly?.percent)
                           ? CardContent.weekGateNote(weeklyResetMs: pool.weekly?.resetsAtMs, nowMs: model.nowMs) : nil)
        }
    }

    @State private var poolOpen: Set<String> = []
    private func poolBinding(_ id: String) -> Binding<Bool> {
        Binding(get: { poolOpen.contains(id) }, set: { if $0 { poolOpen.insert(id) } else { poolOpen.remove(id) } })
    }

    /// CCRM-102: the note for the 5h card while the week is spent, else nil.
    static func weekGate(_ usage: UsageData?, nowMs: Int64) -> String? {
        // The same predicate as the faces (`Face.weekGated`) and Android: spent alone gates.
        guard let weekly = usage?.weekly, Pace.isSpent(weekly.percent) else { return nil }
        return CardContent.weekGateNote(weeklyResetMs: weekly.resetsAtMs, nowMs: nowMs)
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
                           rightResetLine: dimmed ? "as of \(CardContent.clock(snapshot?.fetchedAtMs ?? 0, Display.use24h, .current))" : nil,
                           weekGate: Self.weekGate(usage, nowMs: model.nowMs))
            }
        case .weekly:
            if let all = CardContent.window(title: "All", window: usage?.weekly, kind: HistoryStats.weeklyKind,
                                            history: history, nowMs: model.nowMs, use24h: Display.use24h) {
                // CCRM-99 (Model Limits Toggle): hidden, All takes the card's full width.
                let cap = usage?.modelCaps.first.flatMap { Prefs.showModelLimits(account.key) ? $0 : nil }.flatMap {
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
