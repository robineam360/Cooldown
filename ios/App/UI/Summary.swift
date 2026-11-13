import CooldownCore
import SwiftUI

/// CCRM-98 (Summary Tab), wireframe design/2026-11-02-summary-tab.html rev B with the V1 caption
/// (design/2026-11-02-pace-line-label.html): every account's week on one page, in the Summary order (spec mobile §6),
/// then the Pace map. No combined figure, no 5h on the map.
struct SummaryEntry: Identifiable, Equatable {
    let account: Account
    let face: Face
    /// The plan's name as API-CONTRACT §11 gives it (mapped with the account's tier when the list is built).
    let plan: String?
    let group: SummaryRank.Group
    let rank: Int
    /// The week's samples as (share of the week gone 0…100, used %), for the map.
    let trail: [CGPoint]
    /// CCRM-111 (Map Layers): the weekly estimate the account's chart draws (`Projection.estimate` on the same samples),
    /// for the map's Projection layer and the callout's forecast; nil when there is too little to go on.
    var estimate: Projection.Estimate? = nil
    var id: String { account.key }

    var usedPct: Double? { face.weeklyPct }
    var elapsedPct: Double? { face.weeklyPace }
    var hasSession: Bool { face.hasSessionWindow }

    /// Builds and ranks the list from each account's face (the same numbers the widgets draw).
    static func build(accounts: [Account], faces: [String: Face], plans: [String: String], histories: [String: [HistoryPoint]],
                      nowMs: Int64, order: SummaryOrder = Prefs.summaryOrder) -> [SummaryEntry] {
        let entries = accounts.compactMap { a -> SummaryRank.Entry? in
            guard let f = faces[a.key] else { return nil }
            return SummaryRank.Entry(key: a.key, usedPct: f.weeklyPct, elapsedPct: f.weeklyPace,
                                     msUntilReset: f.weeklyReset.map { $0.ms - nowMs }, sessionBinding: f.sessionBinding,
                                     sessionMsUntilReset: f.reset.map { $0.ms - nowMs })
        }
        return SummaryRank.ordered(entries, order).enumerated().compactMap { i, ranked in
            guard let account = accounts.first(where: { $0.key == ranked.entry.key }), let face = faces[account.key] else { return nil }
            var trail: [CGPoint] = []
            var estimate: Projection.Estimate?
            if let reset = face.weeklyReset {
                let start = reset.ms - HistoryStats.weekMs
                let samples = Projection.weeklySamples(history: histories[account.key] ?? [], resetAtMs: reset.ms, windowLengthMs: HistoryStats.weekMs)
                trail = samples
                    .filter { $0.0 >= start && $0.0 <= nowMs }
                    .map { CGPoint(x: Double($0.0 - start) / Double(HistoryStats.weekMs) * 100, y: $0.1) }
                estimate = Projection.estimate(samples: samples, resetAtMs: reset.ms)
            }
            return SummaryEntry(account: account, face: face, plan: plans[account.key], group: ranked.group, rank: i + 1, trail: trail,
                                estimate: estimate)
        }
    }
}

extension SummaryEntry {
    /// CCRM-110 (Account Tags): the entries the Summary draws for the remembered filter and the chosen order — the filter
    /// narrows the accounts before the order ranks them (the order is unchanged by the filter), so the list, the map, its
    /// foot-line count and the snapshot all follow the same rows. `resolved` carries the control's state.
    @MainActor
    static func current(model: AppModel, filter remembered: String?, order: SummaryOrder)
        -> (entries: [SummaryEntry], resolved: SummaryFilter.Resolved) {
        let resolved = SummaryFilter.resolve(tags: model.accounts.map(\.accountTag), remembered: remembered)
        let shown = resolved.shown.map { model.accounts[$0] }
        let entries = SummaryEntry.build(
            accounts: shown,
            faces: Dictionary(uniqueKeysWithValues: shown.compactMap { a in model.face(a.key).map { (a.key, $0) } }),
            plans: Dictionary(uniqueKeysWithValues: shown.compactMap { a in
                model.states[a.key]?.plan.map { (a.key, PlanNames.display(a.provider, $0, tier: model.states[a.key]?.tier)) }
            }),
            histories: model.histories, nowMs: model.nowMs, order: order)
        return (entries, resolved)
    }
}

struct SummaryView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    /// CCRM-116 (Inner Panes) §3: the Usage pane (the list, scrolling on its own, the footer at its end) and the Pace
    /// pane (the map card, fixed; its plot follows the pane, at most 1.15 × its width) — on a regular-width window.
    var panes = false
    /// The tab, for the scroll position kept across a fold.
    var tab: String = AppModel.summaryKey
    /// The order, mirrored here so a tap redraws at once (a Settings change redraws the page through `displayStamp`, which
    /// re-reads it). The remembered filter is read straight from `Prefs` on every draw — never mirrored — so the memory
    /// rule's fallback to All (BEHAVIOR-MOBILE §6) shows at once and a stale copy can never bring an earlier choice back;
    /// `filterTick` redraws after a tap.
    @State private var order = Prefs.summaryOrder
    @State private var filterTick = 0

    var body: some View {
        // Read so a Settings change (the Summary order, a tag) re-sorts at once.
        let _ = model.displayStamp
        let _ = filterTick
        let (entries, resolved) = SummaryEntry.current(model: model, filter: Prefs.summaryFilter, order: order)
        let list = SummaryList(entries: entries, nowMs: model.nowMs, resolved: resolved, order: order,
                               onFilter: { Prefs.summaryFilter = $0.rawValue; filterTick += 1 },
                               onOrder: { order = $0; Prefs.summaryOrder = $0 }, onOpen: open)
        let pin = Binding(get: { model.mapPin }, set: { model.mapPin = $0 })
        Group {
            if panes {
                PaneSplit {
                    TrackedScroll(tab: tab) {
                        VStack(alignment: .leading, spacing: 12) {
                            if model.noticeOwed { WeeklyFirstNoticeView() }
                            list
                            Footer()
                        }
                        .padding(.bottom, 16)
                    }
                    .scrollBounceBehavior(.basedOnSize)
                    .refreshable { await model.refresh(manual: true) }
                } second: {
                    // The card's padding is 16 a side; the plot is the card's inner width.
                    PaneFill(minimum: 160, cap: { ($0 - 32) * 1.15 }) { h in
                        PaceMapCard(entries: entries, height: h, onOpen: open, sharedPin: pin)
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    if model.noticeOwed { WeeklyFirstNoticeView() }
                    list
                    PaceMapCard(entries: entries, height: 230, onOpen: open, sharedPin: pin)
                }
            }
        }
        // The memory rule runs when the Summary comes on screen and whenever the accounts or a tag change (`displayStamp`).
        .onAppear { model.runFilterMemory() }
        .onChange(of: model.displayStamp) { model.runFilterMemory(); filterTick += 1 }
    }

    private func open(_ key: String) { withAnimation { model.selectedKey = key } }
}

/// "Weekly" with the filter beside it (once any account is tagged) and the sort menu at the right (CCRM-110), then a row per
/// account. From six rows shown rows go to one line (rev B S2; the form follows the rows shown, not the accounts). Where the
/// line cannot hold all three the sort button drops to its glyph, then the control wraps under the title.
struct SummaryList: View {
    let entries: [SummaryEntry]
    let nowMs: Int64
    var resolved = SummaryFilter.resolve(tags: [], remembered: nil)
    var order = Prefs.summaryOrder
    var onFilter: (SummaryFilter) -> Void = { _ in }
    var onOrder: (SummaryOrder) -> Void = { _ in }
    var onOpen: (String) -> Void = { _ in }
    @Environment(\.palette) private var p
    /// CCRM-115: the row measures the glyph column's fit is decided on — the tightest row decides for the list.
    @State private var metrics: [RowFitMetric] = []
    /// A tap on a dimmed segment shows its one line for 3 s (addendum T3 B).
    @State private var notice: SummaryFilter?
    @State private var noticeToken = 0
    /// For renders only: the line as it reads right after a tap on a dimmed segment.
    var previewNotice: SummaryFilter?

    private var oneLine: Bool { entries.count >= SummaryFilter.oneLineFrom }
    private var glyphs: Bool {
        // A row not yet measured reports a zero width: it is left out, so the first pass never drops the glyph for a moment.
        oneLine ? RowGlyphFit.keepsGlyph(oneLine: metrics.compactMap(\.oneLine).filter { $0.line > 0 })
                : RowGlyphFit.keepsGlyph(full: metrics.compactMap(\.full).filter { $0.line > 0 })
    }

    var body: some View {
        let glyphs = glyphs
        CardBox {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 10) { title; control; Spacer(minLength: 6); sort(glyphOnly: false) }
                HStack(spacing: 10) { title; control; Spacer(minLength: 6); sort(glyphOnly: true) }
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 10) { title; Spacer(minLength: 6); sort(glyphOnly: false) }
                    if resolved.controlShown {
                        SummaryFilterControl(resolved: resolved, fill: true, onSelect: onFilter, onDisabled: showNotice)
                    }
                }
            }
            .padding(.bottom, 4)
            if let notice = notice ?? previewNotice {
                Text("No \(notice.label) accounts yet. Tag one in Settings.").font(.system(size: 12)).foregroundStyle(p.sub)
                    .padding(.top, 2).padding(.bottom, 2)
            }
            ForEach(entries) { entry in
                Rectangle().fill(p.line).frame(height: 1).padding(.vertical, 8)
                if oneLine {
                    // CCRM-104: not a Button, so a press on the figure can swap the word for its caption instead of opening.
                    SummaryOneLineRow(entry: entry, nowMs: nowMs, glyph: glyphs)
                        .onTapGesture { onOpen(entry.account.key) }
                        .accessibilityAddTraits(.isButton)
                        .trackedRow(entry.account.key)
                } else {
                    // CCRM-103: not a Button, so a tap on the row's figure can show its readout instead of opening.
                    SummaryRow(entry: entry, nowMs: nowMs, glyph: glyphs)
                        .onTapGesture { onOpen(entry.account.key) }
                        .accessibilityAddTraits(.isButton)
                        .trackedRow(entry.account.key)
                }
            }
        }
        .onPreferenceChange(RowFitKey.self) { metrics = $0 }
    }

    private func showNotice(_ filter: SummaryFilter) {
        notice = filter
        noticeToken += 1
        let token = noticeToken
        UIAccessibility.post(notification: .announcement, argument: "No \(filter.label) accounts yet. Tag one in Settings.")
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 3 * 1_000_000_000)
            if noticeToken == token { notice = nil }
        }
    }

    private var title: some View { Text("Weekly").font(.system(size: 17)).foregroundStyle(p.ink).fixedSize() }

    @ViewBuilder private var control: some View {
        if resolved.controlShown { SummaryFilterControl(resolved: resolved, onSelect: onFilter, onDisabled: showNotice) }
    }

    private func sort(glyphOnly: Bool) -> some View { SummarySortMenu(order: order, glyphOnly: glyphOnly, onSelect: onOrder) }
}

enum SummaryWords {
    /// Line 3 of a row: the pace line "1d 7h behind · Fri 9:00 AM" (CCRM-104), "Held · back at 3:40 PM · resets Fri",
    /// "Out until Thu 8:00 AM", "No reading".
    static func line(_ e: SummaryEntry, nowMs: Int64) -> String {
        let f = e.face
        guard let used = e.usedPct else { return "No reading" }
        guard let reset = f.weeklyReset, let elapsed = e.elapsedPct else { return CardContent.notStartedLine }
        if Pace.isSpent(used) { return "Out until \(CardContent.dayClock(reset.ms, nowMs, Display.use24h, .current))" }
        // CCRM-103 (Hybrid Notification) rev D S1: a held row says why in line 3 only, beside its loud ring.
        if e.group == .held, e.hasSession, let back = f.reset {
            return "Held · back at \(shortClock(back)) · resets \(CardContent.weekday(reset.ms, .current))"
        }
        return paceLine(e, nowMs: nowMs)?.text()
            ?? "\(PaceText.tight(Pace.band(usedPct: used, elapsedPct: elapsed), nil) ?? "") · resets \(CardContent.weekday(reset.ms, .current))"
    }

    /// The eligible row's pace line (core §6.3 Printed form); nil for held, spent, unread and unclocked rows, whose own
    /// lines stand. CCBG-60 (Summary Line Clip) is now the one drop order: weekday, clock, never the duration and word.
    static func paceLine(_ e: SummaryEntry, nowMs: Int64) -> PaceLine? {
        guard e.group == .eligible, let reset = e.face.weeklyReset else { return nil }
        return PaceText.paceLine(usedPct: e.usedPct, resetMs: reset.ms, windowLengthMs: HistoryStats.weekMs, nowMs: nowMs,
                                 use24h: Display.use24h)
    }

    /// The held reason's time, "back 3:40 PM". CCRM-103 (Hybrid Notification) rev D S1: no longer drawn beside the
    /// name (the held row says it in line 3, `line`); kept for the tests that read it.
    static func companion(_ e: SummaryEntry) -> String? {
        guard e.usedPct != nil, e.hasSession, e.group == .held, let back = e.face.reset else { return nil }
        return "back \(shortClock(back))"
    }

    /// CCRM-104 (Pace Line Parity) D5: the pace-mark caption a press on the figure underlines (or, in a one-line row,
    /// shows in the word slot) for 3 s; nil when spent, unclocked or unread.
    static func caption(_ e: SummaryEntry) -> String? { PaceText.caption(mode: Display.mode, usedPct: e.usedPct, elapsedPct: e.elapsedPct) }

    /// The one-line row's word: the band, "Held · 3:40 PM" when held (never "5h", as on Android), "Out · Thu" when spent.
    static func word(_ e: SummaryEntry) -> String {
        switch e.group {
        case .noReading: return "No reading"
        case .held: return "Held · \(e.face.reset.map(shortClock) ?? "")"
        case .spent: return "Out · \(e.face.weeklyReset.map { CardContent.weekday($0.ms, .current) } ?? "")"
        case .eligible: return Pace.band(usedPct: e.usedPct, elapsedPct: e.elapsedPct).flatMap(PaceText.bandWords) ?? "Not started"
        }
    }
}

/// CCRM-103 (Hybrid Notification) rev D §2 S1: a 48 pt ring (the 5h) on the row's left, spanning its lines, in place of
/// the "5h 34%" companion; a plan with no 5h keeps the slot, greyed with a cross, so every row has the same frame.
struct SummaryRow: View {
    let entry: SummaryEntry
    let nowMs: Int64
    /// CCRM-115: draw the tag's glyph before the provider's mark (the list's one decision, `RowGlyphFit`).
    var glyph = true
    @State private var lineWidth: CGFloat = 0
    @State private var chipWidth: CGFloat = 0
    @State private var figureWidth: CGFloat = 0
    @State private var revealed = false
    @State private var revealToken = 0
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let accent = entry.account.accent(scheme)
        let spent = entry.group == .spent
        let readout = SummaryWords.caption(entry)
        let used = entry.usedPct, pace = entry.elapsedPct
        HStack(alignment: .center, spacing: 12) {
            ring(accent: accent)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 7) {
                    // CCRM-115 (Row Tag Glyph): a person or briefcase, 14 pt box + 4, before the mark; the list drops the
                    // column from every row at once when a name would fall under its 64 pt floor.
                    HStack(spacing: 4) {
                        if glyph { TagRowGlyph(tag: entry.account.accountTag) }
                        ProviderMark(provider: entry.account.provider, size: 18)
                    }
                    // CCBG-60 (Summary Line Clip): the name gives way before the pill and the figure.
                    Text(entry.account.label).font(.system(size: 15)).foregroundStyle(p.ink).lineLimit(1)
                        .accessibilityLabel("\(entry.account.label), \(entry.account.accountTag.accountWord)")
                    PlanChip(provider: entry.account.provider, plan: entry.plan).fixedSize()
                        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { chipWidth = $0 }
                    Spacer(minLength: 6)
                    // CCBG-56 (Summary Readout Tap): the figure's own target, at least 48 pt wide and 12 pt past it
                    // above and below, so the row's tap (open the account) never takes it.
                    Display.grammarFigure(Display.number(used, pace: pace), size: 17)
                        .foregroundStyle(Display.figureColour(used, pace: pace, ink: p.ink,
                                                              usage: used == nil ? p.sub : p.severity(used, accent: accent), scheme))
                        .fixedSize()
                        .frame(minWidth: 48, alignment: .trailing)
                        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { figureWidth = $0 }
                        .contentShape(Rectangle().inset(by: -12))
                        // Only while there is a readout (Fable F7): otherwise the figure opens the account with the row.
                        .onTapGesture(count: 1, perform: { show(readout) })
                        .allowsHitTesting(readout != nil)
                }
                GrammarBar(pct: used, pace: pace, accent: accent, height: 10, surface: p.card).padding(.top, 6)
                if let caption = readout, let mark = pace {
                    PaceCaption(text: caption, markPct: mark, underlined: revealed).padding(.top, 1)
                }
                // CCRM-104: the pace line, one drop order (weekday, then clock, never the duration and word).
                if let line = SummaryWords.paceLine(entry, nowMs: nowMs) {
                    PaceLineView(line: line).font(.system(size: 13)).padding(.top, 4)
                } else {
                    Text(SummaryWords.line(entry, nowMs: nowMs)).font(.system(size: 13)).lineLimit(1).minimumScaleFactor(0.8)
                        .foregroundStyle(spent || entry.group == .noReading ? p.sub : p.txt).padding(.top, 4)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { lineWidth = $0 - 48 - 12 }
        .preference(key: RowFitKey.self, value: [fitMetric])
        .opacity(spent ? 0.6 : 1)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityActions { if let readout { Button(readout) { show(readout) } } }
    }

    /// What this row's line 1 has to share, for `RowGlyphFit`: everything but the name and the glyph column (the mark, the
    /// plan chip, the figure and the gaps between them), none of it dependent on whether the glyph is drawn.
    private var fitMetric: RowFitMetric {
        let gaps = 28.0 + 6 - (chipWidth == 0 ? 7 : 0)   // five items at 7 pt, the spacer's 6 pt floor, no gap for an absent chip
        let fixed = 18 + gaps + Double(chipWidth) + Double(max(48, figureWidth))
        let name = (entry.account.label as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 15)]).width.rounded(.up)
        return RowFitMetric(id: entry.id, full: .init(line: Double(lineWidth), name: Double(name), fixed: fixed), oneLine: nil)
    }

    /// The 5h ring: greyed while the week is spent (CCRM-102 (Spent Week Gate)), a cross for a plan with no 5h, "—"
    /// with no reading.
    private func ring(accent: Color) -> some View {
        let f = entry.face
        let state: GrammarRing.State = entry.group == .noReading ? .normal
            : !entry.hasSession ? .notApplicable : entry.group == .spent ? .grey : .normal
        let pct = state == .notApplicable ? nil : f.pct
        return GrammarRing(
            pct: pct, pace: f.pace, accent: accent,
            figure: state == .notApplicable ? nil : Display.ringNumber(pct, pace: f.pace),
            figureColour: state == .normal && Display.isDistance(pct, pace: f.pace)
                ? Display.figureColour(pct, pace: f.pace, ink: p.ink, usage: p.ink, scheme) : nil,
            state: state, surface: p.card)
            .frame(width: 48, height: 48)
    }

    private func show(_ readout: String?) {
        guard readout != nil else { return }
        revealed = true
        revealToken += 1
        let token = revealToken
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: AccountTile.readoutSeconds * 1_000_000_000)
            if revealToken == token { revealed = false }
        }
    }
}

struct SummaryOneLineRow: View {
    /// CCBG-84: the narrowest a name gets once the glyph has left — 64 pt, or its own width when shorter (never under 44,
    /// the column the names have always kept).
    static func nameFloor(_ label: String) -> CGFloat {
        let own = ceil((label as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 15)]).width)
        return max(44, min(own, 64))
    }

    let entry: SummaryEntry
    let nowMs: Int64
    /// CCRM-115: the glyph between the rank disc and the name (12 pt box + 4), while the bar keeps its 50 pt floor.
    var glyph = true
    @State private var lineWidth: CGFloat = 0
    @State private var revealed = false
    @State private var revealToken = 0
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let accent = entry.account.accent(scheme)
        let quiet = entry.group == .spent || entry.group == .noReading
        let caption = SummaryWords.caption(entry)
        let line = SummaryWords.paceLine(entry, nowMs: nowMs)
        HStack(spacing: 8) {
            Text("\(entry.rank)").font(.system(size: 10, weight: .bold)).foregroundStyle(.black)
                .frame(width: 16, height: 16).background(accent, in: Circle())
            HStack(spacing: 4) {
                if glyph { TagRowGlyph(tag: entry.account.accountTag, compact: true) }
                // CCBG-84 (One-line Name Overrun), option A (Robin 2026-11-12): beside the glyph the name is whole, as
                // `RowGlyphFit` decided; once the glyph has left, a name that still does not fit ellipsizes down to its
                // 64 pt floor (or its own width) and the bar keeps its 50 pt.
                Text(entry.account.label).font(.system(size: 15)).foregroundStyle(p.ink).lineLimit(1)
                    .accessibilityLabel("\(entry.account.label), \(entry.account.accountTag.accountWord)")
                    .fixedSize(horizontal: glyph, vertical: false)
                    .frame(minWidth: glyph ? 44 : Self.nameFloor(entry.account.label), alignment: .leading)
            }
            .layoutPriority(1)
            UsageBar(pct: entry.usedPct, pace: entry.elapsedPct, accent: accent, height: 7).frame(minWidth: 50, maxWidth: 110)
            // CCRM-104: the word slot is the pace line's tinted span alone; a press on the figure swaps it for the caption.
            Group {
                if revealed, let caption {
                    // The caption's own drop order (Astra C1-3): whole, then the distance clause alone.
                    FitLine(variants: [caption, PaceText.captionShort(caption)].map {
                        Text($0).font(.system(size: 12, weight: .semibold)).underline().foregroundStyle(p.ink)
                    })
                } else if let line {
                    Text(line.lead).font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(line.tone(scheme, ink: p.ink))
                } else {
                    Text(SummaryWords.word(entry)).font(.system(size: 12.5, weight: entry.group == .held ? .semibold : .regular))
                        .foregroundStyle(entry.group == .held ? p.orange : quiet ? p.sub : p.txt)
                }
            }
            .lineLimit(1).minimumScaleFactor(0.8).frame(width: 96, alignment: .leading)
            Display.grammarFigure(Display.number(entry.usedPct, pace: entry.elapsedPct), size: 15)
                .foregroundStyle(Display.figureColour(entry.usedPct, pace: entry.elapsedPct, ink: p.ink,
                                                      usage: entry.usedPct == nil ? p.sub : p.severity(entry.usedPct, accent: accent), scheme))
                .lineLimit(1).minimumScaleFactor(0.8)
                .frame(width: 52, alignment: .trailing)
                // The row's tap opens the account; the figure's own target shows the caption (48 pt, as the two-line row).
                .contentShape(Rectangle().inset(by: -10))
                .onTapGesture { show(caption) }
                .allowsHitTesting(caption != nil)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { lineWidth = $0 }
        .preference(key: RowFitKey.self, value: [fitMetric])
        .opacity(quiet ? 0.6 : 1)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityActions { if let caption { Button(caption) { show(caption) } } }
    }

    /// The disc (16), the name, the word (96), the figure (52) and four 8 pt gaps; the bar is the one flexible column.
    private var fitMetric: RowFitMetric {
        // The name by its font, not by what an overfull row left it: it never truncates on a one-line row.
        let natural = (entry.account.label as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 15)]).width.rounded(.up)
        let name = Double(max(44, natural))
        return RowFitMetric(id: entry.id, full: nil, oneLine: .init(line: Double(lineWidth), fixed: 16 + name + 96 + 52 + 32))
    }

    private func show(_ caption: String?) {
        guard caption != nil else { return }
        revealed = true
        revealToken += 1
        let token = revealToken
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: AccountTile.readoutSeconds * 1_000_000_000)
            if revealToken == token { revealed = false }
        }
    }
}

/// One Summary row's measures for `RowGlyphFit`, gathered up to the list.
struct RowFitMetric: Equatable {
    let id: String
    let full: RowGlyphFit.FullRow?
    let oneLine: RowGlyphFit.OneLineRow?
}

struct RowFitKey: PreferenceKey {
    static let defaultValue: [RowFitMetric] = []
    static func reduce(value: inout [RowFitMetric], nextValue: () -> [RowFitMetric]) { value += nextValue() }
}
