import CooldownCore
import SwiftUI

/// CCRM-98 (Summary Tab), wireframe design/2026-11-02-summary-tab.html rev B with the V1 caption
/// (design/2026-11-02-pace-line-label.html): every account's week on one page, most room first (spec mobile §6),
/// then the Pace map. No combined figure, no 5h on the map.
struct SummaryEntry: Identifiable, Equatable {
    let account: Account
    let face: Face
    let plan: String?
    let group: SummaryRank.Group
    let rank: Int
    /// The week's samples as (share of the week gone 0…100, used %), for the map.
    let trail: [CGPoint]
    var id: String { account.key }

    var usedPct: Double? { face.weeklyPct }
    var elapsedPct: Double? { face.weeklyPace }
    var hasSession: Bool { face.hasSessionWindow }

    /// Builds and ranks the list from each account's face (the same numbers the widgets draw).
    static func build(accounts: [Account], faces: [String: Face], plans: [String: String], histories: [String: [HistoryPoint]],
                      nowMs: Int64) -> [SummaryEntry] {
        let entries = accounts.compactMap { a -> SummaryRank.Entry? in
            guard let f = faces[a.key] else { return nil }
            return SummaryRank.Entry(key: a.key, usedPct: f.weeklyPct, elapsedPct: f.weeklyPace,
                                     msUntilReset: f.weeklyReset.map { $0.ms - nowMs }, sessionBinding: f.sessionBinding,
                                     sessionMsUntilReset: f.reset.map { $0.ms - nowMs })
        }
        return SummaryRank.rank(entries).enumerated().compactMap { i, ranked in
            guard let account = accounts.first(where: { $0.key == ranked.entry.key }), let face = faces[account.key] else { return nil }
            var trail: [CGPoint] = []
            if let reset = face.weeklyReset {
                let start = reset.ms - HistoryStats.weekMs
                trail = Projection.weeklySamples(history: histories[account.key] ?? [], resetAtMs: reset.ms, windowLengthMs: HistoryStats.weekMs)
                    .filter { $0.0 >= start && $0.0 <= nowMs }
                    .map { CGPoint(x: Double($0.0 - start) / Double(HistoryStats.weekMs) * 100, y: $0.1) }
            }
            return SummaryEntry(account: account, face: face, plan: plans[account.key], group: ranked.group, rank: i + 1, trail: trail)
        }
    }
}

struct SummaryView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.palette) private var p
    var wide = false

    var body: some View {
        let entries = SummaryEntry.build(
            accounts: model.accounts,
            faces: Dictionary(uniqueKeysWithValues: model.accounts.compactMap { a in model.face(a.key).map { (a.key, $0) } }),
            plans: model.states.compactMapValues(\.plan), histories: model.histories, nowMs: model.nowMs)
        VStack(alignment: .leading, spacing: 12) {
            if model.noticeOwed { WeeklyFirstNoticeView() }
            if wide {
                HStack(alignment: .top, spacing: 16) {
                    SummaryList(entries: entries, nowMs: model.nowMs, onOpen: open)
                    PaceMapCard(entries: entries, height: 340)
                }
            } else {
                SummaryList(entries: entries, nowMs: model.nowMs, onOpen: open)
                PaceMapCard(entries: entries, height: 230)
            }
        }
    }

    private func open(_ key: String) { withAnimation { model.selectedKey = key } }
}

/// "Weekly · most room first", then a row per account. From six accounts rows go to one line (rev B S2).
struct SummaryList: View {
    let entries: [SummaryEntry]
    let nowMs: Int64
    var onOpen: (String) -> Void = { _ in }
    @Environment(\.palette) private var p

    var body: some View {
        CardBox {
            HStack {
                Text("Weekly").font(.system(size: 17)).foregroundStyle(p.ink)
                Spacer()
                Text("most room first").font(.system(size: 13)).foregroundStyle(p.sub)
            }
            .padding(.bottom, 4)
            ForEach(entries) { entry in
                Rectangle().fill(p.line).frame(height: 1).padding(.vertical, 8)
                if entries.count >= 6 {
                    Button { onOpen(entry.account.key) } label: { SummaryOneLineRow(entry: entry, nowMs: nowMs) }
                        .buttonStyle(.plain)
                } else {
                    // CCRM-103: not a Button, so a tap on the row's figure can show its readout instead of opening.
                    SummaryRow(entry: entry, nowMs: nowMs)
                        .onTapGesture { onOpen(entry.account.key) }
                        .accessibilityAddTraits(.isButton)
                }
            }
        }
    }
}

enum SummaryWords {
    /// Line 3 of a row: "Room to spare · ≈22%/day · resets Fri", "Held · back at 3:40 pm · resets Fri", "Out until Thu
    /// 8:00 am", "No reading".
    static func line(_ e: SummaryEntry, nowMs: Int64) -> String {
        let f = e.face
        guard let used = e.usedPct else { return "No reading" }
        guard let reset = f.weeklyReset, let elapsed = e.elapsedPct else { return CardContent.notStartedLine }
        if Pace.isSpent(used) { return "Out until \(CardContent.dayClock(reset.ms, nowMs, Display.use24h, .current))" }
        // CCRM-103 (Hybrid Notification) rev D S1: a held row says why in line 3 only, beside its loud ring.
        if e.group == .held, e.hasSession, let back = f.reset {
            return "Held · back at \(shortClock(back)) · resets \(CardContent.weekday(reset.ms, .current))"
        }
        let band = Pace.band(usedPct: used, elapsedPct: elapsed)
        let words = PaceText.tight(band, Pace.allowance(usedPct: used, msUntilReset: reset.ms - nowMs)) ?? ""
        return "\(words) · resets \(CardContent.weekday(reset.ms, .current))"
    }

    /// CCBG-60 (Summary Line Clip): line 3 without its " · resets <day>" clause, for a row too narrow for it.
    static func withoutReset(_ line: String) -> String {
        line.range(of: " · resets ").map { String(line[..<$0.lowerBound]) } ?? line
    }

    /// The held reason's time, "back 3:40 pm". CCRM-103 (Hybrid Notification) rev D S1: no longer drawn beside the
    /// name (the held row says it in line 3, `line`); kept for the tests that read it.
    static func companion(_ e: SummaryEntry) -> String? {
        guard e.usedPct != nil, e.hasSession, e.group == .held, let back = e.face.reset else { return nil }
        return "back \(shortClock(back))"
    }

    /// CCRM-103 §2b: the Weekly figure's tap readout, shown in line 3 for 3 s; nil when spent, unclocked or unread.
    static func readout(_ e: SummaryEntry, nowMs: Int64) -> String? {
        PaceText.markReadout(MarkTime.of(usedPct: e.usedPct, msUntilReset: e.face.weeklyReset.map { $0.ms - nowMs },
                                         windowLengthMs: HistoryStats.weekMs, nowMs: nowMs),
                             use24h: Display.use24h)
    }

    /// The one-line row's word: the band, "Held · 3:40 pm" when held (never "5h", as on Android), "Out · Thu" when spent.
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
    @State private var revealed = false
    @State private var revealToken = 0
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let accent = entry.account.accent(scheme)
        let spent = entry.group == .spent
        let readout = SummaryWords.readout(entry, nowMs: nowMs)
        let used = entry.usedPct, pace = entry.elapsedPct
        HStack(alignment: .center, spacing: 12) {
            ring(accent: accent)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 7) {
                    ProviderMark(provider: entry.account.provider, size: 18)
                    // CCBG-60 (Summary Line Clip): the name gives way before the pill and the figure.
                    Text(entry.account.label).font(.system(size: 15)).foregroundStyle(p.ink).lineLimit(1)
                    PlanChip(plan: entry.plan).fixedSize()
                    Spacer(minLength: 6)
                    // CCBG-56 (Summary Readout Tap): the figure's own target, at least 48 pt wide and 12 pt past it
                    // above and below, so the row's tap (open the account) never takes it.
                    Display.grammarFigure(Display.number(used, pace: pace), size: 17)
                        .foregroundStyle(Display.figureColour(used, pace: pace, ink: p.ink,
                                                              usage: used == nil ? p.sub : p.severity(used, accent: accent), scheme))
                        .fixedSize()
                        .frame(minWidth: 48, alignment: .trailing)
                        .contentShape(Rectangle().inset(by: -12))
                        .onTapGesture { show(readout) }
                }
                GrammarBar(pct: used, pace: pace, accent: accent, height: 10, surface: p.card).padding(.top, 6)
                if let caption = PaceText.caption(mode: Display.mode, usedPct: used, elapsedPct: pace), let mark = pace {
                    PaceCaption(text: caption, markPct: mark).padding(.top, 1)
                }
                if revealed, let readout {
                    Text(readout).font(.system(size: 13, weight: .semibold)).underline().foregroundStyle(p.ink)
                        .padding(.top, 4).lineLimit(1)
                } else {
                    // CCBG-60: under the width, "resets <day>" goes first.
                    let full = SummaryWords.line(entry, nowMs: nowMs)
                    ViewThatFits(in: .horizontal) {
                        Text(full).font(.system(size: 13)).lineLimit(1).fixedSize()
                        Text(SummaryWords.withoutReset(full)).font(.system(size: 13)).lineLimit(1)
                    }
                    .foregroundStyle(spent || entry.group == .noReading ? p.sub : p.txt).padding(.top, 4)
                }
            }
        }
        .opacity(spent ? 0.6 : 1)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityHint(readout ?? "")
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
            figure: state == .notApplicable ? nil : Display.number(pct, pace: f.pace),
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
    let entry: SummaryEntry
    let nowMs: Int64
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let accent = entry.account.accent(scheme)
        let quiet = entry.group == .spent || entry.group == .noReading
        HStack(spacing: 8) {
            Text("\(entry.rank)").font(.system(size: 10, weight: .bold)).foregroundStyle(.black)
                .frame(width: 16, height: 16).background(accent, in: Circle())
            Text(entry.account.label).font(.system(size: 15)).foregroundStyle(p.ink).lineLimit(1)
                .fixedSize().frame(minWidth: 44, alignment: .leading).layoutPriority(1)
            UsageBar(pct: entry.usedPct, pace: entry.elapsedPct, accent: accent, height: 7).frame(minWidth: 50, maxWidth: 110)
            Text(SummaryWords.word(entry)).font(.system(size: 12.5, weight: entry.group == .held ? .semibold : .regular))
                .foregroundStyle(entry.group == .held ? p.orange : quiet ? p.sub : p.txt).lineLimit(1).frame(width: 96, alignment: .leading)
            Text(Display.number(entry.usedPct, pace: entry.elapsedPct)).font(.system(size: 15, weight: .bold)).monospacedDigit()
                .foregroundStyle(entry.usedPct == nil ? p.sub : p.severity(entry.usedPct, accent: accent))
                .frame(width: 46, alignment: .trailing)
        }
        .opacity(quiet ? 0.6 : 1)
        .contentShape(Rectangle())
    }
}

/// The Pace map: each account's week as (share gone, used), over-pace above the diagonal. Spent dots carry an ×;
/// no-reading accounts are left off; from six accounts the dots are numbered by rank instead of named.
struct PaceMapCard: View {
    let entries: [SummaryEntry]
    var height: CGFloat = 230
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        CardBox {
            HStack {
                Text("Pace map").font(.system(size: 17)).foregroundStyle(p.ink)
                Spacer()
                Text("Weekly, where each account stands").font(.system(size: 13)).foregroundStyle(p.sub)
            }
            PaceMap(entries: entries.filter { $0.usedPct != nil && $0.elapsedPct != nil }, numbered: entries.count >= 6)
                .frame(height: height).padding(.top, 10)
        }
    }
}

struct PaceMap: View {
    let entries: [SummaryEntry]
    var numbered = false
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Canvas { ctx, size in
            let left: CGFloat = 30, right = size.width - 4, top: CGFloat = 6, bottom = size.height - 18
            func P(_ x: Double, _ y: Double) -> CGPoint {
                CGPoint(x: left + x / 100 * (right - left), y: bottom - y / 100 * (bottom - top))
            }
            func label(_ t: String, _ at: CGPoint, _ c: Color, _ a: UnitPoint, _ s: CGFloat = 10, _ bold: Bool = false) {
                ctx.draw(Text(t).font(.system(size: s, weight: bold ? .bold : .regular)).foregroundStyle(c), at: at, anchor: a)
            }
            var over = Path()
            over.move(to: P(0, 0)); over.addLine(to: P(0, 100)); over.addLine(to: P(100, 100)); over.closeSubpath()
            ctx.fill(over, with: .color(p.red.opacity(0.08)))
            for v in [0.0, 50, 100] {
                ctx.stroke(Path { $0.move(to: P(0, v)); $0.addLine(to: P(100, v)) }, with: .color(p.line), lineWidth: 1)
                label("\(Int(v))%", CGPoint(x: left - 4, y: P(0, v).y), p.sub, .trailing, 9.5)
            }
            ctx.stroke(Path { $0.move(to: P(0, 0)); $0.addLine(to: P(100, 100)) }, with: .color(p.pace),
                       style: StrokeStyle(lineWidth: 1.6, dash: [3, 3]))
            label("over pace", P(3, 94), p.sub, .leading, 10)
            label("room to spare", P(97, 6), p.sub, .trailing, 10)
            var even = ctx
            even.translateBy(x: P(82, 82).x, y: P(82, 82).y)
            even.rotate(by: .radians(-atan2(Double(bottom - top), Double(right - left))))
            even.draw(Text("even pace").font(.system(size: 9.5)).foregroundStyle(p.pace), at: CGPoint(x: 0, y: -7), anchor: .center)
            label("week start", CGPoint(x: left, y: bottom + 10), p.sub, .leading, 10)
            label("each account's own week", CGPoint(x: (left + right) / 2, y: bottom + 10), p.sub, .center, 9.5)
            label("reset", CGPoint(x: right, y: bottom + 10), p.sub, .trailing, 10)
            for e in entries {
                guard let used = e.usedPct, let gone = e.elapsedPct else { continue }
                let accent = e.account.accent(scheme)
                if e.trail.count > 1 {
                    var path = Path()
                    path.addLines([P(0, 0)] + e.trail.map { P($0.x, $0.y) } + [P(gone, used)])
                    ctx.stroke(path, with: .color(accent.opacity(0.7)), lineWidth: 1.4)
                }
                let dot = P(gone, min(used, 100))
                if numbered {
                    ctx.fill(Path(ellipseIn: CGRect(x: dot.x - 7, y: dot.y - 7, width: 14, height: 14)), with: .color(accent))
                    label("\(e.rank)", dot, .black, .center, 9, true)
                } else if Pace.isSpent(used) {
                    ctx.stroke(Path(ellipseIn: CGRect(x: dot.x - 5, y: dot.y - 5, width: 10, height: 10)), with: .color(accent), lineWidth: 1.6)
                    ctx.stroke(Path { $0.move(to: CGPoint(x: dot.x - 3, y: dot.y - 3)); $0.addLine(to: CGPoint(x: dot.x + 3, y: dot.y + 3))
                        $0.move(to: CGPoint(x: dot.x + 3, y: dot.y - 3)); $0.addLine(to: CGPoint(x: dot.x - 3, y: dot.y + 3)) },
                               with: .color(accent), lineWidth: 1.4)
                } else {
                    ctx.fill(Path(ellipseIn: CGRect(x: dot.x - 4.5, y: dot.y - 4.5, width: 9, height: 9)), with: .color(accent))
                }
                if !numbered {
                    let rightSide = dot.x < right - 60
                    label(e.account.label, CGPoint(x: dot.x + (rightSide ? 8 : -8), y: dot.y - 9), p.ink, rightSide ? .leading : .trailing, 11, true)
                }
            }
        }
        .accessibilityLabel("Pace map")
    }
}
