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
                } else if face.atLimit, let reset = face.reset {
                    Text("Limit · resets \(face.resetClock(reset))").foregroundStyle(p.red)
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
            let weekly = entry.window == .weekly
            let pct = weekly ? face.weeklyPct : face.pct
            let pace = weekly ? face.weeklyPace : face.pace
            VStack(alignment: .leading, spacing: 0) {
                FaceLabel(face: face, suffix: weekly ? "Weekly" : face.windowLabel)
                Spacer(minLength: 6)
                (face.status == .signIn || face.status == .plan ? Text("—") : Display.pctText(pct, size: (pct ?? 0) >= 100 ? 40 : 46))
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

/// Rev C (2026-10-24): one account's 5h and Weekly as two bars in the small size — replaces the small Ring. Below
/// 136 pt the Weekly reset line goes; below 119 pt the 5h reset line goes too and its clock joins the header.
struct BarsFace: View {
    let entry: FaceEntry
    @Environment(\.palette) private var p

    var body: some View {
        if let face = entry.face {
            ViewThatFits(in: .vertical) {
                layout(face, weeklyReset: true, sessionReset: true)
                layout(face, weeklyReset: false, sessionReset: true)
                layout(face, weeklyReset: false, sessionReset: false)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        } else {
            NoAccountView()
        }
    }

    private func layout(_ face: Face, weeklyReset: Bool, sessionReset: Bool) -> some View {
        let accent = face.account.accent(colorSchemeFor(p))
        let lead = face.weeklyOnly
        let pct = lead ? face.weeklyPct : face.pct, pace = lead ? face.weeklyPace : face.pace
        let blank = face.status == .signIn || face.status == .plan
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                ProviderMark(provider: face.account.provider, size: 15)
                Text("\(face.account.label) · \(lead ? "Weekly" : "5h")"
                     + (!sessionReset && !lead ? face.reset.map { " · \(shortClock($0))" } ?? "" : ""))
                    .lineLimit(1).minimumScaleFactor(0.7)
            }
            .font(.system(size: 14)).foregroundStyle(p.txt)
            (blank ? Text("—") : Display.pctText(pct, size: 28))
                .font(.system(size: 28, weight: .bold)).monospacedDigit().lineLimit(1)
                .foregroundStyle(face.isDimmed || pct == nil ? p.sub : p.severity(pct, accent: accent))
                .padding(.top, 2)
            UsageBar(pct: blank ? nil : pct, pace: pace, accent: accent, height: 8, dimmed: face.isDimmed).padding(.top, 3)
            if sessionReset || lead {
                FaceFootnote(face: face, weekly: lead).font(.system(size: 11.5)).padding(.top, 2)
            }
            if !lead {
                HStack {
                    Text("Weekly").font(.system(size: 13)).foregroundStyle(p.txt)
                    Spacer()
                    Text(blank ? "—" : Display.number(face.weeklyPct))
                        .font(.system(size: 15, weight: .semibold)).monospacedDigit().foregroundStyle(p.txt)
                }
                .padding(.top, sessionReset ? 8 : 6)
                UsageBar(pct: blank ? nil : face.weeklyPct, pace: face.weeklyPace, accent: accent, height: 6, dimmed: face.isDimmed)
                    .padding(.top, 3)
                if weeklyReset, !blank, face.status != .noData {
                    FaceFootnote(face: face, weekly: true).font(.system(size: 11.5)).padding(.top, 2)
                }
            }
        }
    }
}

struct StripFace: View {
    let entry: FaceEntry
    @Environment(\.palette) private var p
    var body: some View {
        if let face = entry.face?.leading {
            let accent = face.account.accent(colorSchemeFor(p))
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline) {
                    FaceLabel(face: face, suffix: face.windowLabel, size: 15)
                    Spacer()
                    (face.status == .signIn ? Text("—") : Display.pctText(face.pct, size: 34))
                        .font(.system(size: 34, weight: .bold)).monospacedDigit()
                        .foregroundStyle(face.isDimmed ? p.sub : p.severity(face.pct, accent: accent))
                }
                UsageBar(pct: face.status == .signIn ? nil : face.pct, pace: face.pace, accent: accent, height: 8, dimmed: face.isDimmed)
                    .padding(.top, 6)
                stripLine(face).font(.system(size: 12.5)).padding(.top, 6)
                Spacer(minLength: 6)
                if !face.leadsWeekly {
                    HStack(spacing: 10) {
                        Text("Weekly").font(.system(size: 13)).foregroundStyle(p.txt).frame(width: 52, alignment: .leading)
                        UsageBar(pct: face.status == .signIn ? nil : face.weeklyPct, pace: face.weeklyPace, accent: accent, height: 6, dimmed: face.isDimmed)
                        Text(face.status == .signIn ? "—" : Display.number(face.weeklyPct))
                            .font(.system(size: 13, weight: .semibold)).foregroundStyle(p.txt).frame(width: 40, alignment: .trailing)
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
            if face.atLimit, let reset = face.reset {
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
/// number, 5h bar, reset, pace and projection, the app's trend chart, Weekly), Chart-narrow (the chart lines only),
/// Regular (no chart), Compact and Mini. Large stacks cards; Extra Large always uses columns so no bar runs wider
/// than 420 pt; Medium shows one account as the Strip, more as minis. Past the cap, "+N more · open Cooldown".
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
        // One account in an Extra Large: its 5h and its Weekly side by side.
        let cards: [(Face, AccountCard.Lead)] = family == .systemExtraLarge && faces.count == 1 && !faces[0].weeklyOnly
            ? [(faces[0], .sessionOnly), (faces[0], .weekly)]
            : shown.map { ($0, $0.weeklyOnly ? .weekly : .session) }
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
                                    AccountCard(face: cards[i].0, lead: cards[i].1, tier: tier, width: cellW)
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

/// One account in the Overview, at the tier its cell allows.
struct AccountCard: View {
    enum Lead { case session, sessionOnly, weekly }
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
    let lead: Lead
    let tier: Tier
    /// The cell's width — the Mini row splits it between its two bars.
    var width: CGFloat = 300
    @Environment(\.palette) private var p

    private var accent: Color { face.account.accent(colorSchemeFor(p)) }
    private var leadsWeekly: Bool { lead == .weekly }
    private var content: WindowContent? { leadsWeekly ? face.weekly : face.session }
    private var pct: Double? { leadsWeekly ? face.weeklyPct : face.pct }
    private var pace: Double? { leadsWeekly ? face.weeklyPace : face.pace }
    private var blank: Bool { face.status == .signIn || face.status == .plan || face.status == .noData }
    private var showsWeeklyRow: Bool { lead == .session && face.weekly != nil }
    private var narrow: Bool { tier == .chartNarrow || tier == .compact || tier == .mini }

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
            if showsWeeklyRow {
                weeklyRow.padding(.top, 8)
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
            (blank ? Text("—") : Display.pctText(pct, size: number))
                .font(.system(size: number, weight: .bold)).monospacedDigit().lineLimit(1).fixedSize()
                .foregroundStyle(face.isDimmed || pct == nil ? p.sub : p.severity(pct, accent: accent))
        }
    }

    private var title: String {
        switch lead {
        case .sessionOnly: "\(face.account.label) · 5h"
        case .weekly where tier.rawValue <= 3: "\(face.account.label) · Weekly"
        default: face.account.label
        }
    }

    /// Line A: the reset, ticking live — or the state that replaces it.
    @ViewBuilder private var lineA: some View {
        switch face.status {
        case .signIn: Text("Sign in again").foregroundStyle(p.orange)
        case .plan: Text("No usage on this plan").foregroundStyle(p.sub)
        case .noData: Text("Open Cooldown to check").foregroundStyle(p.sub)
        default:
            if let c = content, c.notStarted {
                Text("No window running").foregroundStyle(p.sub)
            } else if leadsWeekly, let reset = face.weeklyReset {
                Text("Resets \(CardContent.dayClock(reset.ms, Date.now.ms, Display.use24h, .current))").foregroundStyle(p.sub)
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

    /// Line B: the pace verdict and the projection ("9% above even pace · ~ runs out 7:51 pm").
    private var lineB: Text? {
        guard !blank, let c = content, !c.notStarted else { return nil }
        if (c.pct ?? 0) >= 100 { return Text("Opens again when the window resets").foregroundStyle(p.sub) }
        let verdict = narrow ? c.paceLine.replacingOccurrences(of: "even pace", with: "pace") : c.paceLine
        var t = Text(verdict).foregroundStyle(c.paceLineAlarm ? p.red : p.txt)
        if let proj = c.projectionShort { t = t + Text(" · \(proj)").foregroundStyle(p.sub) }
        return t
    }

    private var weeklyRow: some View {
        HStack(spacing: 8) {
            Text("Weekly").font(.system(size: 13)).foregroundStyle(p.txt).frame(width: 52, alignment: .leading)
            UsageBar(pct: blank ? nil : face.weeklyPct, pace: face.weeklyPace, accent: accent, height: 6, dimmed: face.isDimmed)
            Text(blank ? "—" : Display.number(face.weeklyPct))
                .font(.system(size: 13, weight: .semibold)).monospacedDigit().foregroundStyle(p.txt)
                .frame(width: 40, alignment: .trailing)
        }
    }

    private var lineW: Text? {
        guard !blank, let w = face.weekly else { return nil }
        if w.notStarted { return Text("No window running").foregroundStyle(p.sub) }
        let verdict = tier == .full ? w.paceLine : w.paceLine.replacingOccurrences(of: "even pace", with: "pace")
        return Text("\(w.resetsAt.replacingOccurrences(of: "Resets at ", with: "Resets ")) · \(verdict)").foregroundStyle(p.sub)
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
            else if leadsWeekly, let r = face.weeklyReset { Text("· resets \(CardContent.dayClock(r.ms, Date.now.ms, Display.use24h, .current))").foregroundStyle(p.sub) }
            else if face.atLimit, let r = face.reset { Text("· limit · \(shortClock(r))").foregroundStyle(p.red) }
            else if let r = face.runOut { Text("· runs out \(shortClock(r))").foregroundStyle(p.red) }
            else if let r = face.reset { Text("· resets \(shortClock(r))").foregroundStyle(p.sub) }
        }
    }

    private var compact: some View {
        VStack(alignment: .leading, spacing: 0) {
            header(mark: 14, name: 14, number: 20, foot: true)
            HStack(spacing: 8) {
                Text(leadsWeekly ? "Weekly" : "5h").foregroundStyle(p.sub).frame(width: leadsWeekly ? 44 : 30, alignment: .leading)
                UsageBar(pct: blank ? nil : pct, pace: pace, accent: accent, height: 7, dimmed: face.isDimmed)
            }
            .font(.system(size: 12)).padding(.top, 4)
            if let b = lineB { b.font(.system(size: 12)).lineLimit(1).padding(.leading, leadsWeekly ? 52 : 38).padding(.top, 1) }
            if showsWeeklyRow {
                HStack(spacing: 8) {
                    Text("Weekly").font(.system(size: 12)).foregroundStyle(p.sub).frame(width: 44, alignment: .leading)
                    UsageBar(pct: blank ? nil : face.weeklyPct, pace: face.weeklyPace, accent: accent, height: 5, dimmed: face.isDimmed)
                    Text(blank ? "—" : Display.number(face.weeklyPct))
                        .font(.system(size: 13, weight: .semibold)).monospacedDigit().foregroundStyle(p.txt)
                        .frame(width: 36, alignment: .trailing)
                }
                .padding(.top, 3)
            }
        }
    }

    private var mini: some View {
        VStack(alignment: .leading, spacing: 0) {
            header(mark: 13, name: 13, number: 17, foot: true)
            HStack(spacing: 6) {
                if leadsWeekly {
                    Text("Weekly").foregroundStyle(p.sub).frame(width: 42, alignment: .leading)
                    UsageBar(pct: blank ? nil : face.weeklyPct, pace: face.weeklyPace, accent: accent, height: 5, dimmed: face.isDimmed)
                } else {
                    Text("5h").foregroundStyle(p.sub).frame(width: 22, alignment: .leading)
                    // The two bars share what's left 3 : 2 (rev C T5).
                    let bars = max(width - 22 - 42 - 36 - 6 * 4 - 6, 40)
                    UsageBar(pct: blank ? nil : face.pct, pace: face.pace, accent: accent, height: 6, dimmed: face.isDimmed)
                        .frame(width: bars * 0.6)
                    Text("Weekly").foregroundStyle(p.sub).frame(width: 42, alignment: .leading).padding(.leading, 6)
                    UsageBar(pct: blank ? nil : face.weeklyPct, pace: face.weeklyPace, accent: accent, height: 5, dimmed: face.isDimmed)
                        .frame(width: bars * 0.4)
                    Text(blank ? "—" : Display.number(face.weeklyPct))
                        .font(.system(size: 12, weight: .semibold)).monospacedDigit().foregroundStyle(p.txt)
                        .frame(width: 36, alignment: .trailing)
                }
            }
            .font(.system(size: 11.5)).padding(.top, 4)
        }
    }

    /// No reading: the chart still draws its guides and pace line, dimmed — never a blank hole.
    static let emptyChart = TrendChart.Model(samples: [], nowX: nil, projection: nil, over: false, startLabel: "", endLabel: "")
}

struct LockFace: View {
    let entry: FaceEntry
    var familyOverride: WidgetFamily?
    @Environment(\.widgetFamily) private var envFamily
    private var family: WidgetFamily { familyOverride ?? envFamily }

    var body: some View {
        if let face = entry.face?.leading {
            switch family {
            case .accessoryCircular:
                ZStack {
                    AccessoryWidgetBackground()
                    UsageRing(pct: face.status == .signIn ? nil : face.pct, pace: face.pace, accent: .white, lineWidth: 5,
                              label: face.status == .signIn ? "—" : face.atLimit ? "MAX" : (Display.value(face.pct).map { "\($0)" } ?? "—"),
                              sublabel: face.leadsWeekly ? "W" : "5h", fontSize: 19, mono: .white)
                        .padding(2)
                }
            case .accessoryRectangular:
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 5) {
                        ProviderMark(provider: face.account.provider, size: 14, color: .white)
                        Text("\(face.account.label) · \(face.windowLabel)").font(.system(size: 14, weight: .semibold)).lineLimit(1)
                        Spacer(minLength: 2)
                        (face.status == .signIn ? Text("—") : Display.pctText(face.pct, size: 17))
                            .font(.system(size: 17, weight: .bold)).monospacedDigit()
                    }
                    UsageBar(pct: face.status == .signIn ? nil : face.pct, pace: face.pace, accent: .white, height: 6)
                    Group {
                        if face.status == .signIn { Text("Sign in again") }
                        else if face.atLimit, let reset = face.reset { Text("Limit · resets \(face.resetClock(reset))") }
                        else if let reset = face.reset { Text("Resets in ") + Text(reset, style: .relative) }
                        else { Text("No window running") }
                    }
                    .font(.system(size: 13)).opacity(0.85).lineLimit(1)
                }
            default:
                ViewThatFits {
                    Text(inline(face, withReset: true))
                    Text(inline(face, withReset: false))
                }
            }
        } else {
            Text(family == .accessoryInline ? "Cooldown: add an account" : "Add an account")
        }
    }

    private func inline(_ face: Face, withReset: Bool) -> String {
        let pct = face.status == .signIn ? "—" : Display.pct(face.pct)
        let tail = withReset ? (face.reset.map { " · resets \(face.resetClock($0))" } ?? "") : ""
        return "\(face.account.label) \(face.windowLabel) \(pct)\(tail)"
    }
}

/// The accent follows the widget's own appearance; the palette tells us which one it is.
func colorSchemeFor(_ p: Palette) -> ColorScheme { p.bg == Palette.dark.bg ? .dark : .light }

/// The lock-screen panel: one or two rows, or the final frame at a reset.
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

    /// Duet row (measured under 160 pt for two): label with the countdown, the 5h bar, a thin Weekly line.
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
                if row.weeklyPct != nil || row.signIn {
                    HStack(spacing: 7) {
                        Text("Weekly").frame(width: 42, alignment: .leading)
                        UsageBar(pct: row.signIn ? nil : row.weeklyPct, pace: row.weeklyPace, accent: accent, height: 4, dimmed: row.asOf != nil)
                        Text(row.signIn ? "—" : Display.number(row.weeklyPct)).monospacedDigit().frame(width: 36, alignment: .trailing)
                    }
                    .font(.system(size: 11.5)).foregroundStyle(p.txt)
                }
            }
            BigNumber(row: row, size: (row.pct ?? 0) >= 100 ? 32 : 38).frame(width: 84, alignment: .trailing)
        }
    }

    /// One account: the full 5h row with the clock time, and Weekly.
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
            if row.weeklyPct != nil {
                HStack(spacing: 8) {
                    Text("Weekly").frame(width: 48, alignment: .leading)
                    UsageBar(pct: row.weeklyPct, pace: row.weeklyPace, accent: accent, height: 5)
                    Text(Display.number(row.weeklyPct))
                }
                .font(.system(size: 12.5)).foregroundStyle(p.txt)
            }
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
            Text(long ? "Resets \(row.clock(reset))" : row.clock(reset)).foregroundStyle(p.txt)
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
        (row.signIn ? Text("—") : Display.pctText(row.shownPct, size: size))
            .font(.system(size: size, weight: .semibold)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.6)
            .foregroundStyle(row.signIn || row.asOf != nil ? p.sub : p.severity(row.shownPct, accent: row.accent))
    }
}
