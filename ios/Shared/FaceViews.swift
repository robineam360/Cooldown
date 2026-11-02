import CooldownCore
import SwiftUI
import WidgetKit

/// CCRM-94 (iOS App): the faces of the widgets, lock-screen widgets and Live Activity (wireframe rev B §4–§6). They
/// live in Shared, not in the extension, so the app's tests can render every state to an image and compare it with
/// the wireframe; the extension wraps them in its widget configurations.

struct FaceEntry: TimelineEntry {
    let date: Date
    let faces: [Face]
    let key: String?
    var window: WindowChoice = .fiveHour
    var face: Face? { faces.first { $0.account.key == key } ?? faces.first }
}

/// Sample faces for the widget gallery, matching the wireframe's.
enum Demo {
    static var faces: [Face] {
        let now = Date.now
        func face(_ key: String, _ label: String, _ p: Provider, _ pct: Double, _ pace: Double, _ minutes: Double) -> Face {
            var f = Face(account: Account(key: key, label: label, provider: p), pct: pct, pace: pace,
                         reset: now.addingTimeInterval(minutes * 60), runOut: nil, weeklyPct: 18, weeklyPace: 43,
                         weeklyReset: now.addingTimeInterval(4 * 86_400), fetchedAt: now, status: .fresh)
            f.attachContent(sessionWindow: UsageWindow(percent: pct, resetsAtMs: now.addingTimeInterval(minutes * 60).ms, serverSeverity: nil),
                            weeklyWindow: UsageWindow(percent: 18, resetsAtMs: now.addingTimeInterval(4 * 86_400).ms, serverSeverity: nil),
                            history: [], nowMs: now.ms)
            return f
        }
        return [face("teams", "Teams", .claude, 46, 46, 95), face("product", "Product", .claude, 26, 68, 95),
                face("personal", "Personal", .chatgpt, 12, 30, 95)]
    }
}

/// The bottom line every face uses, in priority order (wireframe rev B §4).
struct FaceFootnote: View {
    let face: Face
    var weekly = false
    /// Bars' tight form: "Limit · Sun 9:30 pm" ("resets" dropped to fit, rev B).
    var limitShort = false
    @Environment(\.palette) private var p

    var body: some View {
        Group {
            switch face.status {
            case .signIn: Text("Sign in again").foregroundStyle(p.orange)
            case .plan: Text("No usage on the \(face.account.provider == .claude ? "Free" : "") plan").foregroundStyle(p.sub)
            case .stale: Text("as of \(face.fetchedAt.map(shortClock) ?? "—")").foregroundStyle(p.orange)
            case .noData: Text("Open Cooldown to check").foregroundStyle(p.sub)
            case .aging where !weekly: Text("as of \(face.fetchedAt.map(shortClock) ?? "—")").foregroundStyle(p.sub)
            default:
                if weekly {
                    Text(face.weeklyReset.map { "Resets \(CardContent.dayClock($0.ms, Date.now.ms, Display.use24h, .current))" } ?? "")
                        .foregroundStyle(p.sub)
                } else if face.atLimit || face.leadSpent, let reset = face.reset {
                    Text("Limit · \(limitShort ? "" : "resets ")\(face.resetClock(reset))").foregroundStyle(p.red)
                } else if let runOut = face.runOut {
                    Text("~ runs out \(shortClock(runOut))").foregroundStyle(p.sub)
                } else {
                    Text(face.reset.map { "Resets \(face.resetClock($0))" } ?? "No window running").foregroundStyle(p.sub)
                }
            }
        }
        .lineLimit(1).minimumScaleFactor(0.8)
    }
}

struct FaceLabel: View {
    let face: Face
    var suffix: String? = "5h"
    var size: CGFloat = 14
    @Environment(\.palette) private var p
    var body: some View {
        HStack(spacing: 6) {
            ProviderMark(provider: face.account.provider, size: size + 1)
            Text(suffix.map { "\(face.account.label) · \($0)" } ?? face.account.label).lineLimit(1)
        }
        .font(.system(size: size)).foregroundStyle(p.txt)
    }
}

struct NoAccountView: View {
    @Environment(\.palette) private var p
    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 6) { ProviderMark(provider: .claude, size: 22); ProviderMark(provider: .chatgpt, size: 22) }
            Text("Add an account in Cooldown").font(.system(size: 13)).foregroundStyle(p.txt).multilineTextAlignment(.center)
        }
    }
}

/// Every widget's frame: the card colour, the palette, and the tap target.
struct WidgetFrame<Content: View>: View {
    let key: String?
    @ViewBuilder var content: Content
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        content
            .environment(\.palette, Palette.current(scheme))
            .containerBackground(Palette.current(scheme).card, for: .widget)
            .widgetURL(key.map(DeepLink.account))
    }
}


struct NumberFace: View {
    let entry: FaceEntry
    @Environment(\.palette) private var p
    var body: some View {
        if let face = entry.face?.leading {
            // A weekly-only plan is already led by Weekly (`leading` moved it into the main slot), so .weekly reads
            // the main slot there; only a 5h-led face reads its second slot.
            let weekly = entry.window == .weekly && !face.leadsWeekly
            let pct = weekly ? face.weeklyPct : face.pct
            let pace = weekly ? face.weeklyPace : face.pace
            VStack(alignment: .leading, spacing: 0) {
                FaceLabel(face: face, suffix: weekly ? "Weekly" : face.windowLabel)
                Spacer(minLength: 6)
                (face.status == .signIn || face.status == .plan ? Text("—") : Display.pctText(pct, pace: pace, size: (pct ?? 0) >= 100 ? 40 : 46))
                    .font(.system(size: (pct ?? 0) >= 100 ? 40 : 46, weight: .bold)).monospacedDigit()
                    .foregroundStyle(face.isDimmed || pct == nil ? p.sub : p.severity(pct, accent: face.account.accent(colorSchemeFor(p))))
                    .minimumScaleFactor(0.7).lineLimit(1)
                Spacer(minLength: 6)
                UsageBar(pct: face.status == .signIn ? nil : pct, pace: pace, accent: face.account.accent(colorSchemeFor(p)),
                         height: 8, dimmed: face.isDimmed)
                FaceFootnote(face: face, weekly: weekly).font(.system(size: 12.5)).padding(.top, 8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            NoAccountView()
        }
    }
}

struct CountdownFace: View {
    let entry: FaceEntry
    @Environment(\.palette) private var p
    var body: some View {
        if let face = entry.face?.leading {
            VStack(alignment: .leading, spacing: 4) {
                Text("\(face.windowLabel) reset").font(.system(size: 14)).foregroundStyle(p.txt)
                if let reset = face.reset, face.status != .signIn {
                    Group {
                        // A week doesn't fit a ticking H:MM:SS; Weekly counts down in days and hours.
                        if face.leadsWeekly { Text(Fmt.compactDuration(ms: max(reset.ms - Date.now.ms, 0))) }
                        else { Text(timerInterval: Date.now...max(reset, .now), countsDown: true) }
                    }
                        .font(.system(size: 35, weight: .bold)).monospacedDigit()
                        .foregroundStyle(p.ink).lineLimit(1).minimumScaleFactor(0.6)
                        .padding(.top, 8)
                    Text(face.atLimit ? "Limit · opens \(face.resetClock(reset))" : "at \(face.resetClock(reset))")
                        .font(.system(size: 13.5)).foregroundStyle(face.atLimit ? p.red : p.sub)
                } else {
                    Text("—:—").font(.system(size: 35, weight: .bold)).foregroundStyle(p.sub).padding(.top, 8)
                    FaceFootnote(face: face).font(.system(size: 13.5))
                }
                Spacer(minLength: 0)
                FaceLabel(face: face, suffix: nil, size: 13)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            NoAccountView()
        }
    }
}

/// The companion's figure: always usage, bare in Left mode; while the 5h binds, bold in its bar colour's step
/// (spec mobile §5, rev B "loud companion").
struct CompanionFigure: View {
    let face: Face
    var size: CGFloat = 13
    var blank = false
    @Environment(\.palette) private var p
    var body: some View {
        Text(blank ? "—" : Display.usage(face.companionPct))
            .font(.system(size: size, weight: face.companionLoud ? .heavy : .semibold)).monospacedDigit()
            .foregroundStyle(face.companionLoud ? p.severity(face.companionPct, accent: face.account.accent(colorSchemeFor(p))) : p.txt)
    }
}

/// Rev C (2026-10-24) small Bars, CCRM-96 (Weekly First) rev B: the Weekly block on top, the 5h row under it. At 160 pt
/// the 5h row carries no reset line; on the iPhone SE the Weekly footnote goes and its day joins the header.
struct BarsFace: View {
    let entry: FaceEntry
    @Environment(\.palette) private var p

    var body: some View {
        if let face = entry.face?.lead(.weekly) {
            ViewThatFits(in: .vertical) {
                layout(face, footnote: true)
                layout(face, footnote: false)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        } else {
            NoAccountView()
        }
    }

    private func layout(_ face: Face, footnote: Bool) -> some View {
        let accent = face.account.accent(colorSchemeFor(p))
        let blank = face.status == .signIn || face.status == .plan
        let day = face.leadsWeekly ? face.reset.map { " · \(CardContent.weekday($0.ms, .current))" } : face.reset.map { " · \(shortClock($0))" }
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                ProviderMark(provider: face.account.provider, size: 15)
                Text("\(face.account.label) · \(face.windowLabel)" + (footnote ? "" : day ?? ""))
                    .lineLimit(1).minimumScaleFactor(0.7)
            }
            .font(.system(size: 14)).foregroundStyle(p.txt)
            (blank ? Text("—") : Display.pctText(face.pct, pace: face.pace, size: 28))
                .font(.system(size: 28, weight: .bold)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
                .foregroundStyle(face.isDimmed || face.pct == nil ? p.sub : p.severity(face.pct, accent: accent))
                .padding(.top, 2)
            UsageBar(pct: blank ? nil : face.pct, pace: face.pace, accent: accent, height: 8, dimmed: face.isDimmed).padding(.top, 3)
            if footnote {
                FaceFootnote(face: face, limitShort: true).font(.system(size: 11.5)).padding(.top, 2)
            }
            if face.showsCompanion {
                HStack {
                    Text(face.companionLabel).font(.system(size: 13)).foregroundStyle(p.txt)
                    Spacer()
                    CompanionFigure(face: face, size: 15, blank: blank)
                }
                .padding(.top, footnote ? 8 : 6)
                UsageBar(pct: blank ? nil : face.companionPct, pace: face.companionPace, accent: accent, height: 6, dimmed: face.isDimmed)
                    .padding(.top, 3)
            }
        }
    }
}

/// The Strip (medium), rev B: Weekly leads (34 pt figure, bar, its reset line); the bottom row is 5h.
struct StripFace: View {
    let entry: FaceEntry
    @Environment(\.palette) private var p
    var body: some View {
        if let face = entry.face?.lead(.weekly) {
            let accent = face.account.accent(colorSchemeFor(p))
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline) {
                    FaceLabel(face: face, suffix: face.windowLabel, size: 15)
                    Spacer()
                    (face.status == .signIn ? Text("—") : Display.pctText(face.pct, pace: face.pace, size: 34))
                        .font(.system(size: 34, weight: .bold)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
                        .foregroundStyle(face.isDimmed ? p.sub : p.severity(face.pct, accent: accent))
                }
                UsageBar(pct: face.status == .signIn ? nil : face.pct, pace: face.pace, accent: accent, height: 8, dimmed: face.isDimmed)
                    .padding(.top, 6)
                stripLine(face).font(.system(size: 12.5)).padding(.top, 6)
                Spacer(minLength: 6)
                if face.showsCompanion {
                    HStack(spacing: 10) {
                        Text(face.companionLabel).font(.system(size: 13)).foregroundStyle(p.txt).frame(width: 52, alignment: .leading)
                        UsageBar(pct: face.status == .signIn ? nil : face.companionPct, pace: face.companionPace, accent: accent, height: 6, dimmed: face.isDimmed)
                        CompanionFigure(face: face, blank: face.status == .signIn).frame(width: 40, alignment: .trailing)
                    }
                }
            }
        } else {
            NoAccountView()
        }
    }

    @ViewBuilder private func stripLine(_ face: Face) -> some View {
        switch face.status {
        case .signIn, .plan, .noData, .stale, .aging: FaceFootnote(face: face)
        default:
            if face.atLimit || face.leadSpent, let reset = face.reset {
                (Text("Limit reached").foregroundStyle(p.red) + Text(" · resets \(face.resetClock(reset))").foregroundStyle(p.sub))
            } else if let runOut = face.runOut, let reset = face.reset {
                Text("~ runs out \(shortClock(runOut)) · resets \(face.resetClock(reset))").foregroundStyle(p.sub)
            } else if let reset = face.reset {
                Text(Display.clockFirst
                     ? "Resets at \(face.resetClock(reset)) · in \(Fmt.compactDuration(ms: reset.ms - Date.now.ms))"
                     : "Resets in \(Fmt.compactDuration(ms: reset.ms - Date.now.ms)) · \(face.resetClock(reset))").foregroundStyle(p.sub)
            } else {
                FaceFootnote(face: face)
            }
        }
    }
}

/// Rev C (2026-10-24): every account as a card, drawn in one of five tiers by the room each card gets — Full (big
/// number, bar, reset, pace and projection, the app's trend chart, the second window), Chart-narrow (the chart lines
/// only), Regular (no chart), Compact and Mini. CCRM-96 (Weekly First) rev B: the big number is the account's Headline
/// window, live (Auto takes over while the 5h binds); the other window is the second row.
struct OverviewFace: View {
    let entry: FaceEntry
    /// Tests render outside a widget, where the family can't be set.
    var familyOverride: WidgetFamily?
    @Environment(\.widgetFamily) private var envFamily
    private var family: WidgetFamily { familyOverride ?? envFamily }
    @Environment(\.palette) private var p

    var body: some View {
        let faces = entry.faces
        if faces.isEmpty {
            NoAccountView()
        } else if family == .systemMedium, faces.count == 1 {
            StripFace(entry: entry)
        } else {
            GeometryReader { geo in grid(faces, geo.size) }
        }
    }

    private func grid(_ faces: [Face], _ size: CGSize) -> some View {
        let plan = Self.plan(count: faces.count, family: family)
        let shown = Array(faces.prefix(plan.cap))
        let more = faces.count - shown.count
        let footer: CGFloat = more > 0 ? 20 : 0
        // One account in an Extra Large: its two windows side by side, Weekly first.
        let one = faces[0]
        let cards: [(Face, Bool)] = family == .systemExtraLarge && faces.count == 1 && !one.weeklyOnly && one.weeklyPct != nil
            ? [(one.lead(.weekly), false), (one.lead(.session), false)]
            : shown.map { ($0.lead(for: .notification), true) }
        let cols = family == .systemExtraLarge && faces.count == 1 ? (cards.count == 1 ? 1 : 2) : plan.cols
        let rows = Int((Double(cards.count) / Double(cols)).rounded(.up))
        let gap: CGFloat = cols == 1 && (cards.count > 4 || size.height < 200) ? 6 : 12
        let colGap: CGFloat = 16
        let cellW = min((size.width - colGap * CGFloat(cols - 1)) / CGFloat(cols), cols == 1 && family == .systemExtraLarge ? 420 : .infinity)
        let cellH = (size.height - footer - gap * CGFloat(rows - 1)) / CGFloat(rows)
        let tier = AccountCard.Tier(width: cellW, height: cellH)
        return VStack(alignment: .leading, spacing: 0) {
            Grid(horizontalSpacing: colGap, verticalSpacing: gap) {
                ForEach(0..<rows, id: \.self) { r in
                    GridRow {
                        ForEach(0..<cols, id: \.self) { c in
                            let i = r * cols + c
                            if i < cards.count {
                                Link(destination: DeepLink.account(cards[i].0.account.key)) {
                                    AccountCard(face: cards[i].0, companion: cards[i].1, tier: tier, width: cellW)
                                        .frame(width: cellW, height: cellH, alignment: .topLeading)
                                }
                            } else {
                                Color.clear.frame(width: cellW, height: cellH)
                            }
                        }
                    }
                }
            }
            if more > 0 {
                Text("+\(more) more · open Cooldown").font(.system(size: 12)).foregroundStyle(p.sub).padding(.top, 6)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    /// Columns and how many accounts fit, per family (rev C §2–§4).
    static func plan(count: Int, family: WidgetFamily) -> (cols: Int, cap: Int) {
        switch family {
        case .systemExtraLarge:
            switch count {
            case ...2: return (2, 2)
            case 3: return (3, 3)
            case 4: return (2, 4)
            default: return (3, 9)
            }
        case .systemMedium: return (1, count <= 3 ? 3 : 2)
        default: return (1, 6)
        }
    }
}

/// One account in the Overview, at the tier its cell allows. `face` is already led (`Face.lead`): its main slot is
/// the big number, its companion the second row.
struct AccountCard: View {
    enum Tier: Int {
        case full = 1, chartNarrow, regular, compact, mini
        init(width: CGFloat, height: CGFloat) {
            if width >= 300 && height >= 260 { self = .full }
            else if width >= 180 && height >= 260 { self = .chartNarrow }
            else if width >= 180 && height >= 120 { self = .regular }
            else if height >= 66 { self = .compact }
            else { self = .mini }
        }
    }

    let face: Face
    /// False when the card shows one window only (the Extra Large single account, its windows side by side).
    var companion = true
    let tier: Tier
    /// The cell's width — the Mini row splits it between its two bars.
    var width: CGFloat = 300
    @Environment(\.palette) private var p

    private var accent: Color { face.account.accent(colorSchemeFor(p)) }
    private var leadsWeekly: Bool { face.leadsWeekly }
    private var content: WindowContent? { face.session }
    private var pct: Double? { face.pct }
    private var pace: Double? { face.pace }
    private var blank: Bool { face.status == .signIn || face.status == .plan || face.status == .noData }
    private var showsSecond: Bool { companion && face.showsCompanion && face.companionContent != nil }
    /// Rev B T3 narrow: a cell under 300 pt words the band alone.
    private var narrow: Bool { tier == .chartNarrow || tier == .compact || tier == .mini || width < 300 }
    private var spent: Bool { face.atLimit || face.leadSpent }

    var body: some View {
        switch tier {
        case .full, .chartNarrow, .regular: tall
        case .compact: compact
        case .mini: mini
        }
    }

    // MARK: Full, Chart-narrow, Regular

    private var tall: some View {
        VStack(alignment: .leading, spacing: 0) {
            header(mark: 16, name: 15, number: tier == .full ? 40 : tier == .chartNarrow ? 30 : 34, foot: false)
            UsageBar(pct: blank ? nil : pct, pace: pace, accent: accent, height: 8, dimmed: face.isDimmed).padding(.top, 6)
            lineA.font(.system(size: 12.5)).lineLimit(1).padding(.top, 2)
            if let b = lineB { b.font(.system(size: 12.5)).lineLimit(1).padding(.top, 2) }
            if tier != .regular {
                TrendChart(model: blank ? Self.emptyChart : (content?.chart ?? Self.emptyChart), accent: accent,
                           dimmed: face.isDimmed || blank, linesOnly: tier == .chartNarrow)
                    .frame(minHeight: 60, maxHeight: 170)
                    .padding(.top, 8)
            }
            if showsSecond {
                secondRow.padding(.top, 8)
                if let w = lineW { w.font(.system(size: 11.5)).lineLimit(1).padding(.top, 3) }
            }
            if tier == .regular { Spacer(minLength: 0) }
            if face.status == .aging || face.status == .stale {
                Text("as of \(face.fetchedAt.map { CardContent.dayClock($0.ms, Date.now.ms, Display.use24h, .current) } ?? "—")")
                    .font(.system(size: 11)).foregroundStyle(face.status == .stale ? p.orange : p.sub).padding(.top, 4)
            }
        }
    }

    private func header(mark: CGFloat, name: CGFloat, number: CGFloat, foot: Bool) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            ProviderMark(provider: face.account.provider, size: mark)
            Text(title).font(.system(size: name)).foregroundStyle(p.txt).lineLimit(1).layoutPriority(1)
            if foot { footnote.font(.system(size: name - 2)).lineLimit(1) }
            Spacer(minLength: 4)
            (blank ? Text("—") : Display.pctText(pct, pace: pace, size: number))
                .font(.system(size: number, weight: .bold)).monospacedDigit().lineLimit(1).fixedSize()
                .foregroundStyle(face.isDimmed || pct == nil ? p.sub : p.severity(pct, accent: accent))
        }
    }

    /// "Teams · Weekly" (or "· 5h") while there is room to name the window.
    private var title: String {
        tier.rawValue <= 3 ? "\(face.account.label) · \(face.windowLabel)" : face.account.label
    }

    /// Line A: the reset — Weekly "Resets Sun 9:30 pm · in 4d 6h", 5h ticking live — or the state that replaces it.
    @ViewBuilder private var lineA: some View {
        switch face.status {
        case .signIn: Text("Sign in again").foregroundStyle(p.orange)
        case .plan: Text("No usage on this plan").foregroundStyle(p.sub)
        case .noData: Text("Open Cooldown to check").foregroundStyle(p.sub)
        default:
            if let c = content, c.notStarted {
                Text("No window running").foregroundStyle(p.sub)
            } else if leadsWeekly, let reset = face.reset {
                let left = Fmt.compactDuration(ms: max(reset.ms - Date.now.ms, 0))
                if spent {
                    Text("Limit · resets in \(left)").foregroundStyle(p.red)
                } else {
                    Text("Resets \(CardContent.dayClock(reset.ms, Date.now.ms, Display.use24h, .current)) · in \(left)").foregroundStyle(p.sub)
                }
            } else if let reset = face.reset {
                let tone = face.atLimit ? p.red : p.sub
                if Display.clockFirst {
                    HStack(spacing: 0) {
                        Text(face.atLimit ? "Limit · resets at \(shortClock(reset)) · in " : "Resets at \(shortClock(reset)) · in ")
                            .foregroundStyle(tone)
                        Text(timerInterval: Date.now...max(reset, .now), countsDown: true).monospacedDigit().foregroundStyle(tone)
                    }
                } else {
                    HStack(spacing: 0) {
                        Text(face.atLimit ? "Limit · resets in " : "Resets in ").foregroundStyle(tone)
                        Text(timerInterval: Date.now...max(reset, .now), countsDown: true).monospacedDigit().foregroundStyle(tone)
                        Text(" · \(shortClock(reset))").foregroundStyle(tone)
                    }
                }
            } else {
                Text("No window running").foregroundStyle(p.sub)
            }
        }
    }

    /// Line B. Weekly: the band words ("Slow down · ≈11%/day · ~ runs out Sat 3:10 am"; narrow: the band alone), or
    /// "Out until …"; 5h: its projection ("~ runs out 3:25 pm").
    private var lineB: Text? {
        guard !blank, let c = content, !c.notStarted else { return nil }
        if leadsWeekly {
            if spent { return Text(c.paceLine.hasPrefix("Out until") ? c.paceLine : "Out until \(c.resetsAt.replacingOccurrences(of: "Resets at ", with: ""))").foregroundStyle(p.red) }
            let words = narrow ? (c.band.flatMap(PaceText.bandWords) ?? "") : c.paceLine
            var t = Text(words).foregroundStyle(c.paceLineAlarm ? p.red : p.txt)
            // Rev B draws the projection beside the band only when it runs out before the reset.
            if !narrow, let proj = c.projectionShort, proj.hasPrefix("~ runs out") { t = t + Text(" · \(proj)").foregroundStyle(p.sub) }
            return t
        }
        if (c.pct ?? 0) >= 100 { return Text("Opens again when the window resets").foregroundStyle(p.sub) }
        guard let proj = c.projectionShort else { return nil }
        return Text(proj).foregroundStyle(c.paceLineAlarm ? p.red : p.sub)
    }

    private var secondRow: some View {
        HStack(spacing: 8) {
            Text(face.companionLabel).font(.system(size: 13)).foregroundStyle(p.txt).frame(width: 52, alignment: .leading)
            UsageBar(pct: blank ? nil : face.companionPct, pace: face.companionPace, accent: accent, height: 6, dimmed: face.isDimmed)
            CompanionFigure(face: face, blank: blank).frame(width: 40, alignment: .trailing)
        }
    }

    /// The second row's line. 5h: "Resets in 2h 20m · on track for ~69%" (binding: orange, live, "runs out ~3:25 pm";
    /// narrow: "Resets 5:10 pm · ~69%"). Weekly: "Resets Sun 9:30 pm · Slow down".
    private var lineW: Text? {
        guard !blank, let w = face.companionContent else { return nil }
        if w.notStarted { return Text("No window running").foregroundStyle(p.sub) }
        if leadsWeekly {
            guard let reset = face.companionReset else { return nil }
            if narrow {
                let tail = w.projectionFold.map { " · " + $0.replacingOccurrences(of: "on track for ", with: "") } ?? ""
                return Text("Resets \(shortClock(reset))\(tail)").foregroundStyle(face.companionLoud ? p.orange : p.sub)
            }
            let tail = w.projectionFold.map { " · \($0)" } ?? ""
            if face.companionLoud {
                return Text("Resets in ").foregroundStyle(p.orange)
                    + Text(timerInterval: Date.now...max(reset, .now), countsDown: true).foregroundStyle(p.orange)
                    + Text(tail).foregroundStyle(p.orange)
            }
            return Text("\(w.resetsIn)\(tail)").foregroundStyle(p.sub)
        }
        let verdict = Pace.isSpent(w.pct) ? "Out" : (w.band.flatMap(PaceText.bandWords) ?? "")
        let reset = w.resetsAt.replacingOccurrences(of: "Resets at ", with: "Resets ")
        return Text(verdict.isEmpty ? reset : "\(reset) · \(verdict)").foregroundStyle(p.sub)
    }

    // MARK: Compact and Mini

    /// The header's short status: the reset clock, or what replaces it.
    @ViewBuilder private var footnote: some View {
        switch face.status {
        case .signIn: Text("· sign in again").foregroundStyle(p.orange)
        case .stale: Text("· as of \(face.fetchedAt.map(shortClock) ?? "—")").foregroundStyle(p.orange)
        case .plan: Text("· no usage").foregroundStyle(p.sub)
        case .noData: Text("· open Cooldown").foregroundStyle(p.sub)
        default:
            if let c = content, c.notStarted { Text("· no window").foregroundStyle(p.sub) }
            else if leadsWeekly, spent, let r = face.reset { Text("· out · \(CardContent.weekday(r.ms, .current))").foregroundStyle(p.red) }
            else if leadsWeekly, let r = face.reset { Text("· resets \(CardContent.dayClock(r.ms, Date.now.ms, Display.use24h, .current))").foregroundStyle(p.sub) }
            else if face.atLimit, let r = face.reset { Text("· limit · \(shortClock(r))").foregroundStyle(p.red) }
            else if let r = face.runOut { Text("· runs out \(shortClock(r))").foregroundStyle(p.red) }
            else if let r = face.reset { Text("· resets \(shortClock(r))").foregroundStyle(p.sub) }
        }
    }

    private var leadLabel: String { face.windowLabel }

    private var compact: some View {
        VStack(alignment: .leading, spacing: 0) {
            header(mark: 14, name: 14, number: 20, foot: true)
            HStack(spacing: 8) {
                Text(leadLabel).foregroundStyle(p.sub).frame(width: 44, alignment: .leading)
                UsageBar(pct: blank ? nil : pct, pace: pace, accent: accent, height: 7, dimmed: face.isDimmed)
            }
            .font(.system(size: 12)).padding(.top, 4)
            if let b = lineB { b.font(.system(size: 12)).lineLimit(1).padding(.leading, 52).padding(.top, 1) }
            if showsSecond {
                HStack(spacing: 8) {
                    Text(face.companionLabel).font(.system(size: 12)).foregroundStyle(p.sub).frame(width: 44, alignment: .leading)
                    UsageBar(pct: blank ? nil : face.companionPct, pace: face.companionPace, accent: accent, height: 5, dimmed: face.isDimmed)
                    CompanionFigure(face: face, blank: blank).frame(width: 36, alignment: .trailing)
                }
                .padding(.top, 3)
            }
        }
    }

    private var mini: some View {
        VStack(alignment: .leading, spacing: 0) {
            header(mark: 13, name: 13, number: 17, foot: true)
            HStack(spacing: 6) {
                if !showsSecond {
                    Text(leadLabel).foregroundStyle(p.sub).frame(width: 42, alignment: .leading)
                    UsageBar(pct: blank ? nil : pct, pace: pace, accent: accent, height: 5, dimmed: face.isDimmed)
                } else {
                    // The two bars share what's left 3 : 2 (rev C T5), the lead first.
                    let bars = max(width - 42 - 42 - 36 - 6 * 4 - 6, 40)
                    Text(leadLabel).foregroundStyle(p.sub).frame(width: 42, alignment: .leading)
                    UsageBar(pct: blank ? nil : pct, pace: pace, accent: accent, height: 6, dimmed: face.isDimmed)
                        .frame(width: bars * 0.6)
                    Text(face.companionLabel).foregroundStyle(p.sub).frame(width: 42, alignment: .leading).padding(.leading, 6)
                    UsageBar(pct: blank ? nil : face.companionPct, pace: face.companionPace, accent: accent, height: 5, dimmed: face.isDimmed)
                        .frame(width: bars * 0.4)
                    CompanionFigure(face: face, size: 12, blank: blank).frame(width: 36, alignment: .trailing)
                }
            }
            .font(.system(size: 11.5)).padding(.top, 4)
        }
    }

    /// No reading: the chart still draws its guides and pace line, dimmed — never a blank hole.
    static let emptyChart = TrendChart.Model(samples: [], nowX: nil, projection: nil, over: false, startLabel: "", endLabel: "")
}

/// Lock screen (rev B §4). The rectangular is fixed Weekly-first with the 5h in its line slot; the circular and the
/// inline have no window choice and follow the Headline live. iOS tints these, so a binding 5h is bold, not coloured.
struct LockFace: View {
    let entry: FaceEntry
    var familyOverride: WidgetFamily?
    @Environment(\.widgetFamily) private var envFamily
    private var family: WidgetFamily { familyOverride ?? envFamily }

    var body: some View {
        if let base = entry.face {
            switch family {
            case .accessoryCircular:
                let face = base.lead(for: .notification)
                ZStack {
                    AccessoryWidgetBackground()
                    UsageRing(pct: face.status == .signIn ? nil : face.pct, pace: face.pace, accent: .white, lineWidth: 5,
                              label: face.status == .signIn ? "—" : (face.atLimit || face.leadSpent) ? "MAX"
                                  : Display.number(face.pct, pace: face.pace).replacingOccurrences(of: "%", with: ""),
                              sublabel: face.leadsWeekly ? "W" : "5h", fontSize: 19, mono: .white)
                        .padding(2)
                }
            case .accessoryRectangular:
                let face = base.lead(.weekly)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 5) {
                        ProviderMark(provider: face.account.provider, size: 14, color: .white)
                        Text("\(face.account.label) · \(face.windowLabel)").font(.system(size: 14, weight: .semibold)).lineLimit(1)
                            .minimumScaleFactor(0.8)
                        Spacer(minLength: 2)
                        (face.status == .signIn ? Text("—") : Display.pctText(face.pct, pace: face.pace, size: 17))
                            .font(.system(size: 17, weight: .bold)).monospacedDigit()
                    }
                    UsageBar(pct: face.status == .signIn ? nil : face.pct, pace: face.pace, accent: .white, height: 6)
                    rectLine(face).font(.system(size: 13)).opacity(0.85).lineLimit(1)
                }
            default:
                let face = base.lead(for: .notification)
                ViewThatFits {
                    Text(inline(face, withReset: true))
                    Text(inline(face, withReset: false))
                }
            }
        } else {
            Text(family == .accessoryInline ? "Cooldown: add an account" : "Add an account")
        }
    }

    private func rectLine(_ face: Face) -> Text {
        if face.status == .signIn { return Text("Sign in again") }
        if face.atLimit || face.leadSpent, let reset = face.reset { return Text("Limit · resets \(face.resetClock(reset))") }
        guard let reset = face.reset else { return Text("No window running") }
        if face.leadsWeekly {
            guard face.showsCompanion else { return Text("Resets \(face.resetClock(reset))") }
            let companion = Text("5h \(Display.usage(face.companionPct))")
            return Text("Resets \(CardContent.weekday(reset.ms, .current)) · ") + (face.companionLoud ? companion.bold() : companion)
        }
        return Text("Resets in ") + Text(reset, style: .relative)
    }

    private func inline(_ face: Face, withReset: Bool) -> String {
        let pct = face.status == .signIn ? "—" : Display.pct(face.pct, pace: face.pace)
        let when = face.reset.map { face.leadsWeekly ? CardContent.weekday($0.ms, .current) : shortClock($0) }
        let tail = withReset ? (when.map { " · resets \($0)" } ?? "") : ""
        return "\(face.account.label) \(face.windowLabel) \(pct)\(tail)"
    }
}

/// The accent follows the widget's own appearance; the palette tells us which one it is.
func colorSchemeFor(_ p: Palette) -> ColorScheme { p.bg == Palette.dark.bg ? .dark : .light }

/// The lock-screen panel: one or two rows, or the final frame at a reset. Rev B §4: each row leads with its account's
/// Headline window (Weekly by default) and carries the other window as a thin line.
struct LockPanel: View {
    let state: CooldownActivityAttributes.ContentState
    @Environment(\.palette) private var p

    var body: some View {
        Group {
            if let ended = state.ended {
                HStack(spacing: 12) {
                    ProviderMark(provider: Provider(rawValue: ended.provider) ?? .claude, size: 26)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(ended.label) · 5h reset").font(.system(size: 16, weight: .semibold))
                        Text("Usage is back at 0%. The last window peaked at \(ended.peak)%.").font(.system(size: 13.5)).foregroundStyle(p.txt)
                    }
                }
            } else if state.rows.count == 1, let row = state.rows.first {
                single(row)
            } else {
                VStack(spacing: 10) { ForEach(state.rows, id: \.key) { duetRow($0) } }
            }
        }
        .foregroundStyle(p.ink)
        .padding(.horizontal, 16).padding(.vertical, 12)
    }

    /// Duet row (measured under 160 pt for two): label with the reset, the lead bar, the other window's thin line.
    private func duetRow(_ row: CooldownActivityAttributes.Row) -> some View {
        let accent = row.accent
        return HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    ProviderMark(provider: row.providerValue, size: 14)
                    Text("\(row.label) · \(row.windowLabel)").font(.system(size: 14)).lineLimit(1)
                    Text("·").foregroundStyle(p.txt)
                    // No fixedSize here: a live timer's natural width is unbounded in a widget, and forcing it
                    // crashed the extension's layout (the two-account panel never appeared). RowLine bounds its timer.
                    RowLine(row: row, long: false).font(.system(size: 12)).lineLimit(1).layoutPriority(1)
                }
                UsageBar(pct: row.signIn ? nil : row.shownPct, pace: row.hasReset ? nil : row.pace, accent: accent, height: 7, dimmed: row.asOf != nil)
                companionLine(row, width: 42, barHeight: 4, size: 11.5)
            }
            BigNumber(row: row, size: (row.pct ?? 0) >= 100 ? 32 : 38).frame(width: 84, alignment: .trailing)
        }
    }

    /// The thin second line: "5h ▬ 34%" under a Weekly lead (bold in its colour step while it binds), "Weekly ▬ 52%"
    /// under a 5h lead; none when the lead week is spent.
    @ViewBuilder private func companionLine(_ row: CooldownActivityAttributes.Row, width: CGFloat, barHeight: CGFloat, size: CGFloat) -> some View {
        if (row.weeklyPct != nil || row.signIn) && !row.isSpent {
            let loud = row.companionLoud ?? false
            HStack(spacing: 7) {
                Text(row.companionLabel).frame(width: width, alignment: .leading)
                UsageBar(pct: row.signIn ? nil : row.weeklyPct, pace: row.weeklyPace,
                         accent: row.accent, height: barHeight, dimmed: row.asOf != nil)
                Text(row.signIn ? "—" : Display.usage(row.weeklyPct)).monospacedDigit()
                    .fontWeight(loud ? .heavy : .regular)
                    .foregroundStyle(loud ? p.severity(row.weeklyPct, accent: row.accent) : p.txt)
                    .frame(width: 36, alignment: .trailing)
            }
            .font(.system(size: size)).foregroundStyle(p.txt)
        }
    }

    /// One account: the lead row with its line, and the other window.
    private func single(_ row: CooldownActivityAttributes.Row) -> some View {
        let accent = row.accent
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 7) {
                    HStack(spacing: 6) { ProviderMark(provider: row.providerValue, size: 15); Text("\(row.label) · \(row.windowLabel)").font(.system(size: 15)) }
                    UsageBar(pct: row.signIn ? nil : row.shownPct, pace: row.hasReset ? nil : row.pace, accent: accent, height: 8, dimmed: row.asOf != nil)
                    RowLine(row: row, long: true).font(.system(size: 12.5))
                }
                BigNumber(row: row, size: 52)
            }
            companionLine(row, width: 48, barHeight: 5, size: 12.5)
        }
    }
}

/// The countdown (ticking live), "runs out", "limit", "as of" or "sign in again".
struct RowLine: View {
    let row: CooldownActivityAttributes.Row
    let long: Bool
    @Environment(\.palette) private var p

    var body: some View {
        if row.signIn {
            Text(long ? "Sign-in stopped working · open Cooldown" : "sign in again").foregroundStyle(p.orange)
        } else if let asOf = row.asOf {
            Text("as of \(shortClock(asOf))").foregroundStyle(p.orange)
        } else if row.hasReset, let reset = row.reset {
            // Drawn when iOS marks the activity stale at the first reset: the window is over, so no 0:00.
            Text(long ? "\(row.windowLabel) reset at \(row.clock(reset)) · back at 0%" : "reset · 0%").foregroundStyle(p.txt)
        } else if row.leadsWeekly, let reset = row.reset {
            if row.isSpent { Text(long ? "Out until \(row.clock(reset))" : "out · \(row.clock(reset))").foregroundStyle(p.red) }
            else { Text(long ? "Resets \(row.clock(reset))" : row.clock(reset)).foregroundStyle(p.txt) }
        } else if let reset = row.reset {
            if (row.pct ?? 0) >= 100 {
                HStack(spacing: 0) {
                    Text(long ? "Limit reached · resets in " : "limit · ").foregroundStyle(p.red)
                    Text(timerInterval: Date.now...max(reset, .now), countsDown: true).monospacedDigit().foregroundStyle(p.txt)
                        .frame(maxWidth: long ? nil : 58, alignment: .leading)
                }
            } else if let runOut = row.runOut {
                Text("runs out \(shortClock(runOut))").foregroundStyle(p.txt)
            } else {
                HStack(spacing: 0) {
                    if long && Display.clockFirst { Text("Resets at \(shortClock(reset)) · in ").foregroundStyle(p.txt) }
                    else if long { Text("Resets in ").foregroundStyle(p.txt) }
                    Text(timerInterval: Date.now...max(reset, .now), countsDown: true).monospacedDigit().foregroundStyle(p.txt)
                        .frame(maxWidth: long ? nil : 58, alignment: .leading)
                    if long && !Display.clockFirst { Text(" · \(shortClock(reset))").foregroundStyle(p.txt) }
                }
            }
        } else {
            Text("no window running").foregroundStyle(p.sub)
        }
    }
}

struct BigNumber: View {
    let row: CooldownActivityAttributes.Row
    let size: CGFloat
    @Environment(\.palette) private var p
    var body: some View {
        (row.signIn ? Text("—") : Display.pctText(row.shownPct, pace: row.hasReset ? nil : row.pace, size: size))
            .font(.system(size: size, weight: .semibold)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.6)
            .foregroundStyle(row.signIn || row.asOf != nil ? p.sub : p.severity(row.shownPct, accent: row.accent))
    }
}

/// The Island's expanded line (rev B §4): Weekly lead "Resets Sun 9:30 pm · 5h 34%" (5h bold while it binds; Vs pace
/// mode leads with "52% used (mark 39%)"); a 5h lead while it binds "~ runs out 3:25 pm · resets in 0:50:12 · Weekly 52%".
struct IslandLine: View {
    let row: CooldownActivityAttributes.Row
    var body: some View {
        if row.leadsWeekly, let reset = row.reset, !row.signIn, row.asOf == nil, !row.hasReset {
            let used = Display.mode == .vsPace ? PaceText.usedAndMark(usedPct: row.pct, elapsedPct: row.pace) : nil
            let head = used.map { "\($0) · resets \(CardContent.weekday(reset.ms, .current))" } ?? (row.isSpent ? "Out until \(row.clock(reset))" : "Resets \(row.clock(reset))")
            if let five = row.weeklyPct, !row.isSpent {
                let companion = Text("5h \(Display.usage(five))")
                Text("\(head) · ") + ((row.companionLoud ?? false) ? companion.bold() : companion)
            } else {
                Text(head)
            }
        } else if !row.leadsWeekly, let projection = row.projection, let reset = row.reset, !row.signIn, row.asOf == nil, !row.hasReset,
                  (row.pct ?? 0) < 100 {
            HStack(spacing: 0) {
                Text("\(projection) · resets in ")
                Text(timerInterval: Date.now...max(reset, .now), countsDown: true).monospacedDigit().frame(maxWidth: 64)
                if let weekly = row.weeklyPct { Text(" · Weekly \(Display.usage(weekly))") }
            }
        } else {
            RowLine(row: row, long: true)
        }
    }
}
