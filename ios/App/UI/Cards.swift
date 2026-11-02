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

/// One account's tile and credits card, in the order, visibility and fold the user chose (the layout sheet), drawn at
/// the chosen density and chart height. Used by the iPhone tabs and both iPad halves; the iPad has room, so it shows
/// what's behind More inline.
///
/// CCRM-103 (Hybrid Notification): the 7-day and 5-hour cards are one `AccountTile`. The layout sheet still orders,
/// hides and folds `.session` and `.weekly`; the tile is drawn where the first visible one of the two sits.
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
    /// Whether the user opened or closed the tile; until then the density's default (Comfortable open, Compact folded;
    /// behind More it starts folded, as on Android).
    @State private var tileOpen: Bool?
    @State private var poolOpen = false
    @State private var moreOpen = false

    private var defaultOpen: Bool { Density(rawValue: density) != .compact }

    /// The tile's first chart: the 5h when the drawn order leads with it (Headline 5-hour, or reordered), else All.
    private var defaultChip: TileChip {
        let first = model.drawnLayout(account.key).order.first { $0 != .credits }
        return first == .session && model.usage(account.key)?.session != nil ? .five : .all
    }

    /// The two window cards collapse into the tile's one slot (`.weekly`), at the first one's position.
    static func merged(_ ids: [CardID]) -> [CardID] {
        var placed = false
        return ids.compactMap { id in
            if id == .credits { return id }
            if placed { return nil }
            placed = true
            return .weekly
        }
    }

    var body: some View {
        let accent = account.accent(scheme)
        let state = model.states[account.key] ?? AccountState()
        let snapshot = model.snapshots[account.key]
        let notice = Notice.forAccount(label: account.label, state: state, snapshot: snapshot, nowMs: model.nowMs,
                                       provider: account.provider)
        let layout = model.drawnLayout(account.key)
        let main = Self.merged(layout.main)
        let folded = Self.merged(layout.folded).filter { $0 == .credits || !main.contains(.weekly) }
        VStack(spacing: 12) {
            if model.noticeOwed { WeeklyFirstNoticeView() }
            if let notice { NoticeView(notice: notice, onAction: onSignIn) }
            if state.lastKind != "plan", model.usage(account.key) != nil {
                if twoAcross {
                    // The iPad half's header carries the credits; the tile takes the full width.
                    if main.contains(.weekly) || folded.contains(.weekly) {
                        tile(accent: accent, notice: notice, inMore: false)
                    }
                } else {
                    ForEach(main) { card($0, accent: accent, notice: notice, folded: false) }
                    if !folded.isEmpty {
                        Button { withAnimation(.easeInOut(duration: 0.2)) { moreOpen.toggle() } } label: {
                            HStack(spacing: 4) {
                                Text("More")
                                Image(systemName: moreOpen ? "arrowtriangle.up.fill" : "arrowtriangle.down.fill").font(.system(size: 8))
                                Text("· \(folded.count) \(folded.count == 1 ? "card" : "cards")")
                            }
                            .font(.system(size: 15)).foregroundStyle(p.txt)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 2).padding(.vertical, 4)
                        }
                        .buttonStyle(.plain)
                        if moreOpen { ForEach(folded) { card($0, accent: accent, notice: notice, folded: true) } }
                    }
                }
                // CCRM-55 (E2): the Claude + GPT pool inside Antigravity, only once it has been used.
                if let pool = model.usage(account.key)?.pools.first(where: \.shown) {
                    poolTile(pool, accent: accent, notice: notice)
                }
            }
        }
    }

    /// The pool's own tile, same anatomy, its name as the last line under it (rev D T6). No history is kept for it.
    @ViewBuilder
    private func poolTile(_ pool: UsagePool, accent: Color, notice: Notice?) -> some View {
        let dimmed = notice?.tone == .error || notice?.tone == .warning
        let weekly = CardContent.window(title: "All", window: pool.weekly, kind: HistoryStats.weeklyKind, history: [],
                                        nowMs: model.nowMs, use24h: Display.use24h)
        let five = CardContent.window(title: "5-hour window", window: pool.session, kind: HistoryStats.sessionKind, history: [],
                                      nowMs: model.nowMs, use24h: Display.use24h)
        if weekly != nil || five != nil {
            AccountTile(weekly: weekly, five: five, accent: accent, open: $poolOpen, dimmed: dimmed,
                        weekGate: Pace.isSpent(pool.weekly?.percent)
                            ? CardContent.weekGateNote(weeklyResetMs: pool.weekly?.resetsAtMs, nowMs: model.nowMs) : nil,
                        footnote: pool.name)
        }
    }

    /// The account's tile: Weekly (All models) and its model limit as bars, the 5h as the ring.
    @ViewBuilder
    private func tile(accent: Color, notice: Notice?, inMore: Bool) -> some View {
        let usage = model.usage(account.key)
        let history = model.histories[account.key] ?? []
        let snapshot = model.snapshots[account.key]
        let dimmed = notice?.tone == .error || notice?.tone == .warning
        let size = ChartSize(rawValue: chartSize) ?? .medium
        let all = CardContent.window(title: "All", window: usage?.weekly, kind: HistoryStats.weeklyKind,
                                     history: history, nowMs: model.nowMs, use24h: Display.use24h)
        // CCRM-99 (Model Limits Toggle): hidden, the tile has the one Weekly bar and no model chip.
        let cap = usage?.modelCaps.first.flatMap { Prefs.showModelLimits(account.key) ? $0 : nil }.flatMap {
            CardContent.window(title: $0.modelName.replacingOccurrences(of: "Claude ", with: ""), window: $0.window,
                               kind: HistoryStats.weeklyKind, history: [], nowMs: model.nowMs, use24h: Display.use24h)
        }
        let five = CardContent.window(title: "5-hour window", window: usage?.session, kind: HistoryStats.sessionKind,
                                      history: history, nowMs: model.nowMs, use24h: Display.use24h)
        if all != nil || five != nil {
            AccountTile(weekly: all, cap: cap, five: five, accent: accent,
                        open: Binding(get: { tileOpen ?? (!inMore && defaultOpen) },
                                      set: { tileOpen = $0 }),
                        defaultChip: defaultChip, chartHeight: size.height(width: cardWidth), linesOnly: size.linesOnly,
                        dimmed: dimmed, weekGate: Self.weekGate(usage, nowMs: model.nowMs),
                        rightResetLine: dimmed ? "as of \(CardContent.clock(snapshot?.fetchedAtMs ?? 0, Display.use24h, .current))" : nil)
        }
    }

    /// CCRM-102: the note for the 5h card while the week is spent, else nil.
    static func weekGate(_ usage: UsageData?, nowMs: Int64) -> String? {
        // The same predicate as the faces (`Face.weekGated`) and Android: spent alone gates.
        guard let weekly = usage?.weekly, Pace.isSpent(weekly.percent) else { return nil }
        return CardContent.weekGateNote(weeklyResetMs: weekly.resetsAtMs, nowMs: nowMs)
    }

    @ViewBuilder
    private func card(_ id: CardID, accent: Color, notice: Notice?, folded: Bool) -> some View {
        switch id {
        case .session, .weekly:
            tile(accent: accent, notice: notice, inMore: folded)
        case .credits:
            if Display.creditsVisible(account.key), let credits = CardContent.credits(model.usage(account.key)?.credits) {
                CreditsCard(text: credits.text, pct: credits.pct, isBalance: credits.pct == nil)
            }
        }
    }
}

// MARK: - CCRM-103 (Hybrid Notification): the shape grammar's account tile (design/2026-11-02-shape-grammar.html rev D
// §1, §2, §2b, §6 `itile()`)

/// An in-app bar ended per Appearance › Usage end mark (rev D §1e, spec mobile §5 "Shape grammar"): Round keeps
/// `UsageBar`'s capsule; Line and Pace colour end the fill square at the usage and add a 1.5 pt line, 1.5 pt past each
/// edge, over a 1 pt gap in the surface colour, coloured per `UsageEndMark.role` (the fill's colour, ink, or Vs pace's
/// green / red). The pace tick stays on top.
struct GrammarBar: View {
    let pct: Double?
    let pace: Double?
    let accent: Color
    var height: CGFloat = 8
    var dimmed = false
    var showsRoom = true
    var endMark: UsageEndMark = Display.usageEndMark
    /// The surface under the bar, for the end mark's gap; nil = the card colour.
    var surface: Color? = nil
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        UsageBar(pct: pct, pace: pace, accent: accent, height: height, dimmed: dimmed, showsRoom: showsRoom)
            .overlay {
                if endMark != .round, let pct, pct > 0 {
                    Canvas { ctx, size in draw(ctx, size: size, pct: pct) }
                        .opacity(dimmed ? 0.45 : 1)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
            }
    }

    private func draw(_ ctx: GraphicsContext, size: CGSize, pct: Double) {
        let w = size.width, h = height, top = (size.height - h) / 2
        let value = min(max(pct, 0), 100)
        let fill = min(max(w * value / 100, h), w)
        let colour = p.severity(pct, accent: accent)
        // The same rule as UsageBar: red past the tick only with the switch on and more than 3 points over.
        let over = Display.redPastPace && (pace.map { pct < 100 && pct > $0 + 3 } ?? false)
        let pw: CGFloat = pace.map { w * CGFloat(min(max($0, 0), 100)) / 100 } ?? 0
        // Square the capsule's end: its last half-height drawn as a plain rectangle.
        let x0 = max(0, fill - h / 2)
        if over && x0 < pw {
            ctx.fill(Path(CGRect(x: x0, y: top, width: pw - x0, height: h)), with: .color(colour))
            ctx.fill(Path(CGRect(x: pw, y: top, width: max(fill - pw, 0), height: h)), with: .color(p.red))
        } else {
            ctx.fill(Path(CGRect(x: x0, y: top, width: fill - x0, height: h)), with: .color(over ? p.red : colour))
        }
        let line: Color
        switch endMark.role(usedPct: pct, elapsedPct: pace) {
        case .fill?: line = colour
        case .ink?: line = p.ink
        case .under?: line = Display.vsUnder(scheme)
        case .over?: line = Display.vsOver(scheme)
        case nil: return
        }
        let x = min(max(fill, 0.75), w - 0.75)
        ctx.fill(Path(CGRect(x: x - 1.75, y: top - 2.5, width: 3.5, height: h + 5)), with: .color(surface ?? p.card))
        ctx.fill(Path(roundedRect: CGRect(x: x - 0.75, y: top - 1.5, width: 1.5, height: h + 3), cornerRadius: 0.75),
                 with: .color(line))
        if let pace {
            let ext = max(3, h * 0.45)
            ctx.fill(Path(roundedRect: CGRect(x: min(w - 3, max(0, w * pace / 100 - 1.5)), y: top - ext, width: 3, height: h + 2 * ext),
                          cornerRadius: 1.5), with: .color(p.tick))
        }
    }
}

/// The open tile's chart chips: All · <model> · 5h.
enum TileChip: Hashable { case all, model, five }
/// What a tap readout replaces: the ring's 5h line, or a bar's caption.
enum TileTap: Hashable { case ring, weekly, model }

/// One account: the 5h `GrammarRing` left (72 pt), the 7-day bars stacked right — "Weekly · All models" first, its figure
/// at the ring's number size and an 8 pt bar, then the model limit (13.5 pt label, 15 pt figure, 6 pt bar) — each with
/// its V1 `PaceCaption` and dashed room. Folded, the band line and the 5h line (led by a 12 pt ring glyph, no "5h") sit
/// under it; open, chips pick the chart and its lines (the 5h's chart moved here from its own card). Tapping a figure
/// swaps its own line for the tap readout for 3 s; tapping the ring also selects the 5h chip on an open tile.
struct AccountTile: View {
    let weekly: WindowContent?
    var cap: WindowContent? = nil
    /// nil = the plan has no 5h window: the ring slot stays, greyed with a cross (rev D §7).
    let five: WindowContent?
    let accent: Color
    @Binding var open: Bool
    var defaultChip: TileChip = .all
    var ringSize: CGFloat = 72
    var chartHeight: CGFloat = 150
    var linesOnly = false
    var dimmed = false
    /// CCRM-102 (Spent Week Gate): the week is spent — the ring greys and this note is the 5h line.
    var weekGate: String? = nil
    var rightResetLine: String? = nil
    /// A last line under the folded tile (the pool's name).
    var footnote: String? = nil
    @State private var chip: TileChip?
    @State private var reveal: TileTap?
    @State private var revealToken = 0
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    static let readoutSeconds: UInt64 = 3

    private var chips: [TileChip] {
        var list: [TileChip] = []
        if weekly != nil { list.append(.all) }
        if cap != nil { list.append(.model) }
        if five != nil { list.append(.five) }
        return list
    }
    private var selected: TileChip {
        let c = chip ?? defaultChip
        return chips.contains(c) ? c : chips.first ?? .all
    }
    private var ringState: GrammarRing.State { five == nil ? .notApplicable : weekGate != nil ? .grey : .normal }
    /// The ring's readout; a greyed 5h (spent week) has none.
    private var ringReadout: String? { weekGate == nil ? five?.readout() : nil }

    var body: some View {
        CardBox {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .center, spacing: 14) {
                    ring
                    VStack(alignment: .leading, spacing: 8) {
                        if let weekly { barRow(weekly, lead: true) }
                        if let cap { barRow(cap, lead: false) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                if !open { foldedLines }
            }
            .contentShape(Rectangle())
            .onTapGesture { withAnimation(.easeInOut(duration: 0.2)) { open.toggle() } }
            if open { openPart }
        }
    }

    // MARK: The ring

    private var ring: some View {
        let pct = five?.pct, pace = five?.pace
        return GrammarRing(
            pct: pct, pace: pace, accent: accent,
            figure: five == nil ? nil : Display.number(pct, pace: pace),
            figureColour: ringState == .normal && Display.isDistance(pct, pace: pace)
                ? Display.figureColour(pct, pace: pace, ink: p.ink, usage: p.ink, scheme) : nil,
            state: ringState,
            projection: open && selected == .five && weekGate == nil ? five?.chart?.projection : nil,
            surface: p.card)
            .frame(width: ringSize, height: ringSize)
            .opacity(dimmed ? 0.45 : 1)
            .contentShape(Circle())
            .onTapGesture { tapRing() }
            .accessibilityHint(ringReadout ?? "")
            .accessibilityAddTraits(five == nil ? [] : .isButton)
            .accessibilityAction { tapRing() }
    }

    private func tapRing() {
        guard five != nil else { return }
        if open { withAnimation(.easeInOut(duration: 0.2)) { chip = .five } }
        show(.ring, ringReadout)
    }

    /// Swaps `target`'s line for the readout for 3 s; nothing when there is no readout.
    private func show(_ target: TileTap, _ text: String?) {
        guard text != nil else { return }
        reveal = target
        revealToken += 1
        let token = revealToken
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: Self.readoutSeconds * 1_000_000_000)
            if revealToken == token { reveal = nil }
        }
    }

    // MARK: The bars

    private func barRow(_ c: WindowContent, lead: Bool) -> some View {
        let target: TileTap = lead ? .weekly : .model
        let readout = c.readout()
        let label = lead ? (cap != nil ? "Weekly · All models" : "Weekly") : c.title
        let usageColour = c.pct == nil ? p.sub : p.severity(c.pct, accent: accent)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(label).font(.system(size: lead ? 15 : 13.5)).foregroundStyle(lead ? p.ink : p.txt).lineLimit(1)
                Spacer(minLength: 4)
                Display.grammarFigure(Display.number(c.pct, pace: c.pace), size: lead ? (0.25 * ringSize).rounded() : 15)
                    .foregroundStyle(dimmed ? p.sub : Display.figureColour(c.pct, pace: c.pace, ink: p.ink, usage: usageColour, scheme))
                    .lineLimit(1)
                    .contentShape(Rectangle())
                    .onTapGesture { show(target, readout) }
            }
            .frame(minHeight: 20)
            GrammarBar(pct: c.pct, pace: c.pace, accent: accent, height: lead ? 8 : 6, dimmed: dimmed, surface: p.card)
                .padding(.top, 2)
            if reveal == target, let readout {
                Text(readout).font(.system(size: 12.5, weight: .semibold)).underline().foregroundStyle(p.ink)
                    .lineLimit(1).padding(.top, 2)
            } else if let caption = c.caption, let mark = c.pace {
                PaceCaption(text: caption, markPct: mark, size: 12.5).padding(.top, 2)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityHint(readout ?? "")
        .accessibilityAction { show(target, readout) }
    }

    // MARK: Folded: the band line and the 5h line

    @ViewBuilder private var foldedLines: some View {
        if let weekly {
            Text(CardContent.compactLine(weekly)).font(.system(size: 14.5, weight: .semibold))
                .foregroundStyle(weekly.paceLineAlarm ? p.red : p.txt)
                .fixedSize(horizontal: false, vertical: true).padding(.top, 10)
        }
        if let weekGate {
            Text(weekGate).font(.system(size: 14.5)).foregroundStyle(p.ink)
                .fixedSize(horizontal: false, vertical: true).padding(.top, weekly == nil ? 10 : 2)
        } else if let five {
            HStack(alignment: .center, spacing: 4) {
                FiveGlyph(pct: five.pct, pace: five.pace, accent: accent)
                    .frame(width: 12, height: 12)
                    .accessibilityHidden(true)
                if reveal == .ring, let r = ringReadout {
                    Text(r).font(.system(size: 14.5, weight: .semibold)).underline().foregroundStyle(p.ink).lineLimit(1)
                } else {
                    Text(CardContent.compactLine(five)).font(.system(size: 14.5))
                        .foregroundStyle(five.paceLineAlarm ? p.red : p.sub)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .opacity(dimmed ? 0.45 : 1)
            .padding(.top, weekly == nil ? 10 : 2)
        }
        if let footnote {
            Text(footnote).font(.system(size: 14.5)).foregroundStyle(p.sub).padding(.top, 2)
        }
    }

    // MARK: Open: chips, the chosen chart and its lines

    @ViewBuilder private var openPart: some View {
        if chips.count > 1 {
            HStack(spacing: 8) { ForEach(chips, id: \.self) { chipButton($0) } }
                .padding(.top, 12).padding(.bottom, 8)
        } else {
            Color.clear.frame(height: 12)
        }
        switch selected {
        case .all: if let weekly { weeklyDetail(weekly) }
        case .model: if let cap { weeklyDetail(cap) }
        case .five: if let five { fiveDetail(five) }
        }
    }

    private func chipButton(_ c: TileChip) -> some View {
        let on = selected == c
        let name = switch c {
        case .all: "All"
        case .model: cap?.title ?? ""
        case .five: "5h"
        }
        return Button { withAnimation(.easeInOut(duration: 0.2)) { chip = c } } label: {
            Text(name).font(.system(size: 14)).foregroundStyle(p.ink).lineLimit(1)
                .padding(.horizontal, 14).padding(.vertical, 4)
                .background(on ? p.fill : .clear, in: RoundedRectangle(cornerRadius: 9))
                .overlay(RoundedRectangle(cornerRadius: 9).stroke(on ? p.fill : p.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(c == .five ? "5-hour window" : name)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func resetRow(_ lead: String, _ second: String) -> some View {
        HStack {
            Text(lead)
            Spacer(minLength: 8)
            Text(second)
        }
        .font(.system(size: 14.5)).foregroundStyle(p.txt).padding(.top, 10)
    }

    /// Weekly or a model limit: today's 7-day chart and lines at full width.
    @ViewBuilder private func weeklyDetail(_ c: WindowContent) -> some View {
        if let chart = c.chart {
            TrendChart(model: chart, accent: accent, dimmed: dimmed, linesOnly: linesOnly).frame(height: chartHeight)
        }
        if c.notStarted {
            Text(c.resetsIn).font(.system(size: 14.5)).foregroundStyle(p.sub).padding(.top, 10)
        } else {
            if !c.paceLine.isEmpty {
                Text(c.paceLineFull).font(.system(size: 14.5)).foregroundStyle(c.paceLineAlarm ? p.red : p.txt)
                    .fixedSize(horizontal: false, vertical: true).padding(.top, 8)
            }
            if let line = c.projectionLine {
                Text(line).font(.system(size: 14.5)).foregroundStyle(p.txt)
                    .fixedSize(horizontal: false, vertical: true).padding(.top, 4)
            }
            resetRow(c.leadReset, c.secondReset)
        }
    }

    /// The 5h: today's 5-hour card body under the chart (greyed while the week is spent, CCRM-102). A ring tap's readout
    /// takes the projection line's place for 3 s.
    @ViewBuilder private func fiveDetail(_ c: WindowContent) -> some View {
        if let weekGate {
            Group {
                if let chart = c.chart {
                    TrendChart(model: chart.withoutProjection, accent: accent, dimmed: dimmed, linesOnly: linesOnly)
                        .frame(height: chartHeight)
                }
                if c.notStarted {
                    Text(c.resetsIn).font(.system(size: 14.5)).foregroundStyle(p.sub).padding(.top, 10)
                } else {
                    resetRow(c.leadReset, rightResetLine ?? c.secondReset)
                }
            }
            .opacity(0.45)
            Text(weekGate).font(.system(size: 14.5)).foregroundStyle(p.ink).padding(.top, 8)
                .fixedSize(horizontal: false, vertical: true)
        } else {
            if let chart = c.chart {
                TrendChart(model: chart, accent: accent, dimmed: dimmed, linesOnly: linesOnly).frame(height: chartHeight)
            }
            if c.notStarted {
                Text(c.resetsIn).font(.system(size: 14.5)).foregroundStyle(p.sub).padding(.top, 10)
            } else {
                if !c.paceLine.isEmpty {
                    Text(c.paceLineFull).font(.system(size: 14.5)).foregroundStyle(c.paceLineAlarm ? p.red : p.txt).padding(.top, 8)
                }
                if reveal == .ring, let r = ringReadout {
                    Text(r).font(.system(size: 14.5, weight: .semibold)).underline().foregroundStyle(p.ink).padding(.top, 6)
                } else if let line = c.projectionLine {
                    Text(line).font(.system(size: 14.5)).foregroundStyle(p.txt)
                        .fixedSize(horizontal: false, vertical: true).padding(.top, 6)
                }
                resetRow(c.leadReset, rightResetLine ?? c.secondReset)
            }
        }
    }
}
