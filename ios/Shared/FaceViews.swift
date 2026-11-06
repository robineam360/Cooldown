import CooldownCore
import SwiftUI
import WidgetKit

/// CCRM-94 (iOS App): the faces of the widgets, lock-screen widgets and Live Activity (wireframe rev B §4–§6). They
/// live in Shared, not in the extension, so the app's tests can render every state to an image and compare it with
/// the wireframe; the extension wraps them in its widget configurations.
///
/// CCRM-103 (Hybrid Notification), the shape grammar (`design/2026-11-02-shape-grammar.html` rev D §6): the 5h is
/// always a hollow ring on the left, the Weekly always a bar on the right; no "5h" word; figures monospaced with the
/// small "%"; bars end per Appearance › Usage end mark. A plan with no 5h shows no ring on the small faces, the Live
/// Activity and the lock screen (B1), a greyed ring with a cross on the Overview's tiles (rev C).

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
            case .plan: Text(face.account.provider == .claude ? "No usage on the Free plan" : "No usage on this plan").foregroundStyle(p.sub)
            case .stale: Text("as of \(face.fetchedAt.map(shortClock) ?? "—")").foregroundStyle(p.orange)
            case .noData: Text("Open Cooldown to check").foregroundStyle(p.sub)
            case .aging where !weekly: Text("as of \(face.fetchedAt.map(shortClock) ?? "—")").foregroundStyle(p.sub)
            default:
                if weekly {
                    Text(face.weeklyReset.map { "Resets \(CardContent.dayClock($0.ms, Date.now.ms, Display.use24h, .current))" } ?? "")
                        .foregroundStyle(p.sub)
                } else if face.atLimit || face.leadSpent, let reset = face.reset {
                    Text("Limit · \(limitShort ? "" : "resets ")\(face.resetClock(reset))").foregroundStyle(p.red)
                } else if face.leadsWeekly, let line = face.session?.line {
                    // CCRM-104 (Pace Line Parity): Bars small — "6d 23h behind · 9:30 pm"; the weekday drops, then the clock.
                    FitLine(variants: line.variants(rest: p.sub, ink: p.ink, scheme: colorSchemeFor(p)))
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
            // CCBG-71 (iPad Number Title Clip): "ChatGPT · Weekly" when it fits, else the name alone (shrunk to fit at
            // most 85%) rather than "ChatGPT · We…" — the face's figure and bar already say which window.
            ViewThatFits(in: .horizontal) {
                Text(suffix.map { "\(face.account.label) · \($0)" } ?? face.account.label).lineLimit(1).fixedSize()
                Text(face.account.label).lineLimit(1).minimumScaleFactor(0.85)
            }
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

// MARK: - The grammar's pieces

/// The grammar's ink (the ring's own): the figure on the mark, and the Pace colour end mark inside the on-pace band.
func faceInk(_ scheme: ColorScheme) -> Color { scheme == .dark ? Color(hex: 0xECECEC) : Color(hex: 0x1F1F1F) }

/// CCRM-103: a 7-day bar. Round (E0) is today's capsule (`UsageBar`); Line (E1) and Pace colour (E2b) end the fill
/// square at the usage with a 1.5 pt line 1.5 pt past each edge over a 1 pt gap in the surface colour, coloured per
/// `UsageEndMark.role` (the fill's colour; or green / red / ink by the pace side). Widgets draw no dashed room.
struct FaceBar: View {
    let pct: Double?
    let pace: Double?
    let accent: Color
    var height: CGFloat = 8
    var dimmed = false
    /// The surface under the bar, for the end mark's gap; nil = the palette's card.
    var surface: Color?
    var endMark: UsageEndMark = Display.usageEndMark
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        if endMark == .round {
            UsageBar(pct: pct, pace: pace, accent: accent, height: height, dimmed: dimmed)
        } else {
            squared
        }
    }

    private var squared: some View {
        let ext = max(3, height * 0.45)
        return GeometryReader { geo in
            let w = geo.size.width
            let value = min(max(pct ?? 0, 0), 100)
            let fill = pct == nil || value == 0 ? 0 : max(w * value / 100, height / 2)
            let colour = p.severity(pct, accent: accent)
            // "Show red past the pace mark" off keeps the tick but not the red (Android's switch).
            let over = Display.redPastPace && (pace.map { (pct ?? 0) < 100 && (pct ?? 0) > $0 + 3 } ?? false)
            ZStack(alignment: .leading) {
                ZStack(alignment: .leading) {
                    Rectangle().fill(accent.opacity(0.26))
                    if fill > 0 { Rectangle().fill(colour).frame(width: fill) }
                    if fill > 0, over, let pace {
                        let pw = w * pace / 100
                        Rectangle().fill(p.red).frame(width: max(fill - pw, 0)).offset(x: pw)
                    }
                }
                .clipShape(Capsule())
                if fill > 0, let role = endMark.role(usedPct: pct, elapsedPct: pace) {
                    let x = min(max(fill, 0.75), w - 0.75)
                    Rectangle().fill(surface ?? p.card).frame(width: 3.5, height: height + 5).offset(x: x - 1.75)
                    Rectangle().fill(roleColour(role, fill: colour)).frame(width: 1.5, height: height + 3).offset(x: x - 0.75)
                }
                if let pace, pct != nil {
                    RoundedRectangle(cornerRadius: 1.5).fill(p.tick)
                        .frame(width: 3, height: height + 2 * ext)
                        .offset(x: min(w - 3, max(0, w * pace / 100 - 1.5)))
                }
            }
            .frame(height: height)
            .frame(maxHeight: .infinity)
        }
        .frame(height: height + 2 * ext)
        .opacity(dimmed ? 0.45 : 1)
        .accessibilityHidden(true)
    }

    private func roleColour(_ role: UsageEndMark.Role, fill: Color) -> Color {
        switch role {
        case .fill: fill
        case .ink: faceInk(scheme)
        case .under: Display.vsUnder(scheme)
        case .over: Display.vsOver(scheme)
        }
    }
}

/// CCRM-103: a figure in the grammar — SF Pro with monospaced digits and the small "%", the Usage display's number
/// (Vs pace unsigned, green under the mark and red over it). `used` keeps the Used figure in the inherited colour: the
/// lock screen, which iOS tints (Q "Vs pace on the lock screen").
struct FaceFigure: View {
    let pct: Double?
    let pace: Double?
    let accent: Color
    let size: CGFloat
    var weight: Font.Weight = .bold
    var blank = false
    var dimmed = false
    var used = false
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Display.grammarFigure(text, size: size, weight: weight)
            .foregroundStyle(colour)
            .lineLimit(1).minimumScaleFactor(0.7)
    }

    private var text: String {
        if blank { return "—" }
        if used { return pct.map { "\(Int($0))%" } ?? "—" }
        return Display.number(pct, pace: pace)
    }

    private var colour: AnyShapeStyle {
        if used { return AnyShapeStyle(HierarchicalShapeStyle.primary) }
        if blank || dimmed || pct == nil { return AnyShapeStyle(p.sub) }
        return AnyShapeStyle(Display.figureColour(pct, pace: pace, ink: p.ink, usage: p.severity(pct, accent: accent), scheme))
    }
}

/// CCRM-103: the 5h ring on a face — `GrammarRing` with the figure the surface asks for. Where the page sets the
/// figure's size (the Number hero 24 pt, the Strip 22, the lock-screen circular 19, the Mini row and the Island 11) the
/// figure is drawn here at that size, fitted to the bore as the ring fits its own.
struct FaceRing: View {
    enum Figure: Equatable {
        /// The Usage display's figure: "34%", "66%" (Left), "19%" green (Vs pace).
        case number
        /// The same without the "%": the Mini row and the compact Island.
        case digits
        /// Always the Used figure: the lock screen.
        case used
        case usedDigits
        case text(String)
        /// The bare ring: the 5h line's glyph, the minimal Island, the inline lock screen's image.
        case none
    }

    let five: Face.FiveHour
    let accent: Color
    var figure: Figure = .number
    /// CCRM-102 (Spent Week Gate): the week is spent.
    var greyed = false
    /// Sign-in needed, or no reading: the grey ring with "—".
    var blank = false
    var dimmed = false
    var figureSize: CGFloat?
    var surface: Color?
    var mono: Color?
    /// A fixed band, as a fraction of the ring's size (the lock circular: the page's `wring`, 5 of 64).
    var bandRatio: CGFloat?
    @Environment(\.palette) private var p
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let state: GrammarRing.State = greyed || blank || five.pct == nil ? .grey : .normal
        GeometryReader { geo in
            GrammarRing(pct: blank ? nil : five.pct, pace: five.pace, accent: accent,
                        figure: figureSize == nil ? text : nil, figureColour: vsColour, state: state,
                        surface: surface ?? p.card, mono: mono,
                        bandOverride: bandRatio.map { $0 * min(geo.size.width, geo.size.height) })
        }
            .overlay {
                if let size = figureSize, let text {
                    GeometryReader { geo in
                        let s = min(geo.size.width, geo.size.height)
                        Display.grammarFigure(text, size: size, weight: loud(state) && vsColour == nil ? .heavy : .bold)
                            .foregroundStyle(ownColour(state))
                            .lineLimit(1).minimumScaleFactor(0.5)
                            .frame(width: max(1, 2 * (0.39 * s - (bandRatio.map { $0 * s } ?? GrammarRing.band(s)) / 2 - 1.5) * 0.92))
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .accessibilityHidden(true)
                }
            }
            .opacity(dimmed ? 0.45 : 1)
    }

    private var pct: Double? { blank ? nil : five.pct }

    private var text: String? {
        let usage = Display.ringNumber(pct, pace: five.pace)
        switch figure {
        case .none: return nil
        case .text(let t): return t
        case .number: return pct == nil ? "—" : usage
        case .digits: return pct == nil ? "—" : String(usage.hasSuffix("%") ? usage.dropLast() : Substring(usage))
        case .used: return pct.map { "\(Int($0))%" } ?? "—"
        case .usedDigits: return pct.map { "\(Int($0))" } ?? "—"
        }
    }

    /// Vs pace: green under the mark, red over it; nil (the ring's own ink or loud step) on it, on a fallback, or
    /// where the figure is always the Used one.
    private var vsColour: Color? {
        guard figure == .number || figure == .digits, Display.mode == .vsPace, !greyed, pct != nil else { return nil }
        switch PaceText.vsPaceTone(usedPct: pct, elapsedPct: five.pace) {
        case .under: return Display.vsUnder(scheme)
        case .over: return Display.vsOver(scheme)
        case .on, .usage: return nil
        }
    }

    private func loud(_ state: GrammarRing.State) -> Bool { state == .normal && (pct ?? 0) > 90 }

    /// `GrammarRing`'s figure colour, for the figure drawn here.
    private func ownColour(_ state: GrammarRing.State) -> Color {
        let ink = mono ?? faceInk(scheme)
        if state == .grey { return ink.opacity(pct == nil ? 0.5 : 0.45) }
        if let vsColour { return mono ?? vsColour }
        return loud(state) ? (mono ?? p.severity(pct, accent: accent)) : ink
    }
}

/// CCRM-103 rev D §6: the model limit's row on Bars (iBars: 12 pt, label 38, 5 pt bar, figure 28) and Strip (iStrip:
/// 13 pt, label 44, 6 pt bar, figure 36) — the label, the bar with its end mark, the figure in the Usage display.
struct FaceModelRow: View {
    let limit: Face.ModelLimit
    let accent: Color
    let fontSize: CGFloat
    let labelWidth: CGFloat
    let barHeight: CGFloat
    let figureWidth: CGFloat
    var spacing: CGFloat = 6
    var dimmed = false
    @Environment(\.palette) private var p

    var body: some View {
        HStack(spacing: spacing) {
            Text(limit.name).font(.system(size: fontSize)).foregroundStyle(p.txt)
                .lineLimit(1).minimumScaleFactor(0.8).frame(width: labelWidth, alignment: .leading)
            FaceBar(pct: limit.pct, pace: limit.pace, accent: accent, height: barHeight, dimmed: dimmed)
            FaceFigure(pct: limit.pct, pace: limit.pace, accent: accent, size: fontSize, dimmed: dimmed)
                .frame(width: figureWidth, alignment: .trailing)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(limit.name) \(limit.pct.map { "\(Int($0))%" } ?? "no reading")")
    }
}

/// The greyed ring with a cross: a plan with no 5h, on the Overview's tiles (rev C).
struct FaceCrossRing: View {
    @Environment(\.palette) private var p
    var body: some View {
        GrammarRing(pct: nil, pace: nil, accent: p.sub, figure: nil, state: .notApplicable, surface: p.card)
    }
}

// MARK: - Number (small)

/// Window 5h: the ring hero (84 pt, the figure 24 pt), the label without "· 5h" (rev D §6 iNum5). Window Weekly:
/// unchanged, the Weekly figure and its bar (iNumW). A plan with no 5h reads Weekly either way (B1).
struct NumberFace: View {
    let entry: FaceEntry
    @Environment(\.palette) private var p
    var body: some View {
        if let face = entry.face?.leading {
            // A weekly-only plan is already led by Weekly (`leading` moved it into the main slot), so .weekly reads
            // the main slot there; only a 5h-led face reads its second slot.
            let weekly = entry.window == .weekly && !face.leadsWeekly
            if !weekly && !face.leadsWeekly, let five = face.fiveHour {
                hero(face, five)
            } else {
                weeklyFace(face, weekly: weekly)
            }
        } else {
            NoAccountView()
        }
    }

    private func hero(_ face: Face, _ five: Face.FiveHour) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            FaceLabel(face: face, suffix: nil)
            Spacer(minLength: 4)
            FaceRing(five: five, accent: face.account.accent(colorSchemeFor(p)), greyed: face.weekGated,
                     blank: face.status == .signIn || face.status == .plan, dimmed: face.isDimmed, figureSize: 24)
                .frame(maxWidth: 84, maxHeight: 84)
                .frame(maxWidth: .infinity)
            Spacer(minLength: 4)
            heroLine(face).font(.system(size: 12.5))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// "Resets in 2h 20m · 5:10 pm" (Clock first: "Resets at 5:10 pm · in 2h 20m"), or what replaces it; "Weekly
    /// spent" at full opacity while the week is spent (CCRM-102).
    @ViewBuilder private func heroLine(_ face: Face) -> some View {
        if face.weekGated {
            Text(CardContent.weekSpentStamp).foregroundStyle(p.ink).lineLimit(1)
        } else if face.status == .fresh, !face.atLimit, face.runOut == nil, let reset = face.reset {
            let left = Fmt.compactDuration(ms: max(reset.ms - Date.now.ms, 0))
            Text(Display.clockFirst ? "Resets at \(shortClock(reset)) · in \(left)" : "Resets in \(left) · \(shortClock(reset))")
                .foregroundStyle(p.sub).lineLimit(1).minimumScaleFactor(0.8)
        } else {
            FaceFootnote(face: face)
        }
    }

    private func weeklyFace(_ face: Face, weekly: Bool) -> some View {
        let pct = weekly ? face.weeklyPct : face.pct
        let pace = weekly ? face.weeklyPace : face.pace
        let accent = face.account.accent(colorSchemeFor(p))
        let blank = face.status == .signIn || face.status == .plan
        let size: CGFloat = (pct ?? 0) >= 100 ? 40 : 46
        return VStack(alignment: .leading, spacing: 0) {
            FaceLabel(face: face, suffix: "Weekly")
            Spacer(minLength: 6)
            FaceFigure(pct: pct, pace: pace, accent: accent, size: size, blank: blank, dimmed: face.isDimmed)
            Spacer(minLength: 6)
            FaceBar(pct: face.status == .signIn ? nil : pct, pace: pace, accent: accent, height: 8, dimmed: face.isDimmed)
            FaceFootnote(face: face, weekly: weekly).font(.system(size: 12.5)).padding(.top, 8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Countdown (small)

/// To the 5h reset: the 40 pt ring and "Resets" lead, the count, "at 5:10 pm", the account at the foot (rev D §6 iCd).
/// A Weekly countdown (a plan with no 5h) keeps today's face, with no ring.
struct CountdownFace: View {
    let entry: FaceEntry
    @Environment(\.palette) private var p
    var body: some View {
        if let face = entry.face?.leading {
            if !face.leadsWeekly, face.status != .signIn, let reset = face.reset, let five = face.fiveHour {
                ringed(face, five, reset)
            } else {
                today(face)
            }
        } else {
            NoAccountView()
        }
    }

    private func ringed(_ face: Face, _ five: Face.FiveHour, _ reset: Date) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                FaceRing(five: five, accent: face.account.accent(colorSchemeFor(p)), greyed: face.weekGated,
                         blank: face.status == .plan, dimmed: face.isDimmed)
                    .frame(width: 40, height: 40)
                Text("Resets").font(.system(size: 13)).foregroundStyle(p.sub)
            }
            Text(timerInterval: Date.now...max(reset, .now), countsDown: true)
                .font(.system(size: 35, weight: .bold)).monospacedDigit()
                .foregroundStyle(p.ink).lineLimit(1).minimumScaleFactor(0.6)
                .opacity(face.weekGated ? 0.45 : 1)
                .padding(.top, 10)
            Group {
                if face.weekGated {
                    Text(CardContent.weekSpentStamp).foregroundStyle(p.ink)
                } else {
                    Text(face.atLimit ? "Limit · opens \(face.resetClock(reset))" : "at \(face.resetClock(reset))")
                        .foregroundStyle(face.atLimit ? p.red : p.sub)
                }
            }
            .font(.system(size: 13.5)).lineLimit(1).padding(.top, 2)
            Spacer(minLength: 0)
            FaceLabel(face: face, suffix: nil, size: 13)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func today(_ face: Face) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\(face.windowLabel) reset").font(.system(size: 14)).foregroundStyle(p.txt)
            if let reset = face.reset, face.status != .signIn {
                // A week doesn't fit a ticking H:MM:SS; Weekly counts down in days and hours.
                Group {
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
    }
}

// MARK: - Bars (small)

/// Rev D §6 iBars: "Teams · Weekly", the 5h ring (50 pt) left of the Weekly figure (28 pt) and its 8 pt bar, the Weekly
/// reset under them. A plan with no 5h: the Weekly figure and bar take the width (B1). On the iPhone SE the reset line
/// goes and its day joins the header (rev C).
struct BarsFace: View {
    let entry: FaceEntry
    @Environment(\.palette) private var p

    var body: some View {
        if let face = entry.face?.grammar {
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
        let weekPct = face.leadsWeekly ? face.pct : nil
        let day = face.leadsWeekly ? face.reset.map { " · \(CardContent.weekday($0.ms, .current))" } : nil
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                ProviderMark(provider: face.account.provider, size: 15)
                // CCBG-71's rule, as FaceLabel: the full title, else without the day, else the name alone.
                ViewThatFits(in: .horizontal) {
                    Text("\(face.account.label) · Weekly" + (footnote ? "" : day ?? "")).lineLimit(1).fixedSize()
                    Text("\(face.account.label) · Weekly").lineLimit(1).fixedSize()
                    Text(face.account.label).lineLimit(1).minimumScaleFactor(0.85)
                }
            }
            .font(.system(size: 14)).foregroundStyle(p.txt)
            HStack(spacing: 10) {
                if let five = face.fiveHour {
                    FaceRing(five: five, accent: accent, greyed: face.fiveGreyed, blank: blank, dimmed: face.isDimmed)
                        .frame(width: 50, height: 50)
                }
                VStack(alignment: .leading, spacing: 0) {
                    FaceFigure(pct: weekPct, pace: face.pace, accent: accent, size: 28, blank: blank, dimmed: face.isDimmed)
                    FaceBar(pct: blank ? nil : weekPct, pace: face.pace, accent: accent, height: 8, dimmed: face.isDimmed)
                        .padding(.top, 5)
                }
            }
            .padding(.top, 8)
            if footnote {
                FaceFootnote(face: face, limitShort: true).font(.system(size: 11.5)).padding(.top, 6)
            }
            // The first model limit (Fable, Argon …) as a 5 pt bar; none when the plan reports none or it is hidden.
            if !blank, let limit = face.modelLimit {
                FaceModelRow(limit: limit, accent: accent, fontSize: 12, labelWidth: 38, barHeight: 5, figureWidth: 28,
                             dimmed: face.isDimmed)
                    .padding(.top, 6)
            }
        }
    }
}

// MARK: - Strip (medium)

/// Rev D §6 iStrip: the 5h ring (84 pt, the figure 22 pt) left; the right is the Weekly — "Teams · Weekly" with its
/// figure (30 pt), the bar, and the band · allowance · reset line. A plan with no 5h: the Weekly takes the width (B1).
struct StripFace: View {
    let entry: FaceEntry
    @Environment(\.palette) private var p
    var body: some View {
        if let face = entry.face?.grammar {
            let accent = face.account.accent(colorSchemeFor(p))
            let blank = face.status == .signIn || face.status == .plan
            let weekPct = face.leadsWeekly ? face.pct : nil
            HStack(alignment: .center, spacing: 14) {
                if let five = face.fiveHour {
                    FaceRing(five: five, accent: accent, greyed: face.fiveGreyed, blank: blank, dimmed: face.isDimmed, figureSize: 22)
                        .frame(width: 84, height: 84)
                }
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        FaceLabel(face: face, suffix: "Weekly", size: 15).layoutPriority(1)
                        Spacer(minLength: 4)
                        FaceFigure(pct: weekPct, pace: face.pace, accent: accent, size: 30, blank: blank, dimmed: face.isDimmed)
                            .fixedSize()
                    }
                    FaceBar(pct: blank ? nil : weekPct, pace: face.pace, accent: accent, height: 8, dimmed: face.isDimmed)
                        .padding(.top, 6)
                    FaceWeeklyLine(face: face).font(.system(size: 12.5)).padding(.top, 6)
                    if !blank, let limit = face.modelLimit {
                        FaceModelRow(limit: limit, accent: accent, fontSize: 13, labelWidth: 44, barHeight: 6,
                                     figureWidth: 36, spacing: 8, dimmed: face.isDimmed)
                            .padding(.top, 10)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        } else {
            NoAccountView()
        }
    }
}

/// The Weekly's line on a grammar face: the pace line "6d 23h behind · Sun 9:30 pm", "Out until Sun 9:30 pm", or the
/// state that replaces it.
struct FaceWeeklyLine: View {
    let face: Face
    var asOf = false
    @Environment(\.palette) private var p

    @ViewBuilder var body: some View {
        switch face.status {
        case .signIn: Text("Sign in again").foregroundStyle(p.orange).lineLimit(1)
        case .plan: Text("No usage on this plan").foregroundStyle(p.sub).lineLimit(1)
        case .noData: Text("Open Cooldown to check").foregroundStyle(p.sub).lineLimit(1)
        case .stale where asOf:
            Text("as of \(face.fetchedAt.map(shortClock) ?? "—")").foregroundStyle(p.orange).lineLimit(1)
        default:
            if face.leadsWeekly, let c = face.session {
                // CCRM-104 (Pace Line Parity): the pace line, with the one drop order (weekday, clock, then 0.8 scale).
                if let line = c.line {
                    PaceLineView(line: line)
                } else {
                    Text(CardContent.compactLine(c)).foregroundStyle(c.paceLineAlarm ? p.red : p.txt).lineLimit(1).truncationMode(.tail)
                }
            } else {
                Text("Weekly not started").foregroundStyle(p.sub).lineLimit(1)
            }
        }
    }
}

// MARK: - Overview (Medium to Extra Large)

/// Rev C (2026-10-24): every account as a card, drawn in one of five tiers by the room each card gets. CCRM-103 rev D
/// §6: the Full, Chart-narrow and Regular tiers are the account tile (the 5h ring left, the Weekly right, the lines
/// under it; Full and Chart-narrow add the Weekly's chart); Compact is ring 46 beside the Weekly; Mini one row with a
/// ring 30.
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
        // One tile carries both windows, so one account in an Extra Large is one tile, at most 420 pt wide.
        let cards = shown.map(\.grammar)
        let cols = family == .systemExtraLarge && faces.count == 1 ? 1 : plan.cols
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
                                Link(destination: DeepLink.account(cards[i].account.key)) {
                                    AccountCard(face: cards[i], tier: tier, width: cellW)
                                        .frame(width: cellW, height: cellH, alignment: tier == .mini ? .leading : .topLeading)
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

/// One account in the Overview, at the tier its cell allows. `face` is a grammar face (`Face.grammar`): the Weekly in
/// its main slot, the 5h (`fiveHour`) for the ring.
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
    let tier: Tier
    /// The cell's width: the tile's ring is 72 pt from 300 pt up, 64 under it.
    var width: CGFloat = 300
    @Environment(\.palette) private var p

    private var accent: Color { face.account.accent(colorSchemeFor(p)) }
    private var blank: Bool { face.status == .signIn || face.status == .plan || face.status == .noData }
    private var weekPct: Double? { face.leadsWeekly ? face.pct : nil }

    var body: some View {
        switch tier {
        case .full, .chartNarrow, .regular: tile
        case .compact: compact
        case .mini: mini
        }
    }

    private func ring(_ five: Face.FiveHour, figure: FaceRing.Figure = .number, figureSize: CGFloat? = nil) -> some View {
        FaceRing(five: five, accent: accent, figure: figure, greyed: face.fiveGreyed, blank: blank, dimmed: face.isDimmed,
                 figureSize: figureSize)
    }

    private func figure(_ size: CGFloat) -> some View {
        FaceFigure(pct: weekPct, pace: face.pace, accent: accent, size: size, blank: blank, dimmed: face.isDimmed).fixedSize()
    }

    private func bar(_ height: CGFloat) -> some View {
        FaceBar(pct: blank ? nil : weekPct, pace: face.pace, accent: accent, height: height, dimmed: face.isDimmed)
    }

    // MARK: Full, Chart-narrow, Regular — the tile

    /// Rev D §6 T3 (`itile`): ring S left (72 from 300 pt, else 64; a plan with no 5h keeps the slot, greyed with a
    /// cross), the Weekly's name, figure (0.25 S) and 8 pt bar right; under the tile the Weekly line, then the 5h line
    /// led by the ring glyph. Full and Chart-narrow add the Weekly's chart under it, as the app.
    private var tile: some View {
        let s: CGFloat = width >= 300 ? 72 : 64
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: 14) {
                Group {
                    if let five = face.fiveHour { ring(five) } else { FaceCrossRing() }
                }
                .frame(width: s, height: s)
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        ProviderMark(provider: face.account.provider, size: 16)
                        Text("\(face.account.label) · Weekly").font(.system(size: 15)).foregroundStyle(p.txt)
                            .lineLimit(1).layoutPriority(1)
                        Spacer(minLength: 4)
                        figure((0.25 * s).rounded())
                    }
                    bar(8).padding(.top, 6)
                    // The tile's model limit (itile's Fable row): 6 pt bar, as the app's tile.
                    if !blank, let limit = face.modelLimit {
                        FaceModelRow(limit: limit, accent: accent, fontSize: 13, labelWidth: 44, barHeight: 6, figureWidth: 32,
                                     dimmed: face.isDimmed)
                            .padding(.top, 8)
                    }
                }
            }
            FaceWeeklyLine(face: face).font(.system(size: 14.5, weight: .semibold)).padding(.top, 10)
            if let five = face.fiveHour, let line = fiveLine(five) {
                HStack(spacing: 4) {
                    FiveGlyph(pct: blank ? nil : five.pct, pace: five.pace, accent: accent, greyed: face.fiveGreyed)
                        .frame(width: 12, height: 12)
                    line.lineLimit(1)
                }
                .font(.system(size: 14.5)).padding(.top, 2)
            }
            if tier != .regular {
                TrendChart(model: blank ? Self.emptyChart : (face.session?.chart ?? Self.emptyChart), accent: accent,
                           dimmed: face.isDimmed || blank, linesOnly: tier == .chartNarrow)
                    .frame(minHeight: 60, maxHeight: 170)
                    .padding(.top, 8)
            }
            if tier == .regular { Spacer(minLength: 0) }
            if face.status == .aging || face.status == .stale {
                Text("as of \(face.fetchedAt.map { CardContent.dayClock($0.ms, Date.now.ms, Display.use24h, .current) } ?? "—")")
                    .font(.system(size: 11)).foregroundStyle(face.status == .stale ? p.orange : p.sub).padding(.top, 4)
            }
        }
    }

    /// The 5h line after its glyph: "resets in 2h 20m · on track for ~69%" (orange while it binds or runs out, red at
    /// the limit); "Weekly spent" while the week is spent (CCRM-102).
    private func fiveLine(_ five: Face.FiveHour) -> Text? {
        if face.fiveGreyed { return Text(CardContent.weekSpentStamp).foregroundStyle(p.ink) }
        guard !blank, let c = five.content else { return nil }
        let words = CardContent.compactLine(c)
        let tone = (c.pct ?? 0) >= 100 ? p.red : (face.companionLoud || c.paceLineAlarm) ? p.orange : p.sub
        return Text(words.prefix(1).lowercased() + words.dropFirst()).foregroundStyle(tone)
    }

    // MARK: Compact and Mini

    /// Rev D §6 t4: ring 46 beside the name and Weekly figure (20 pt), the 7 pt bar, the Weekly line. A plan with no
    /// 5h aligns flush left (B1).
    private var compact: some View {
        HStack(alignment: .center, spacing: 10) {
            if let five = face.fiveHour { ring(five).frame(width: 46, height: 46) }
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    ProviderMark(provider: face.account.provider, size: 14)
                    Text(face.account.label).font(.system(size: 14)).foregroundStyle(p.txt).lineLimit(1).layoutPriority(1)
                    Spacer(minLength: 4)
                    figure(20)
                }
                bar(7).padding(.top, 5)
                FaceWeeklyLine(face: face, asOf: true).font(.system(size: 12)).padding(.top, 5)
            }
        }
        .frame(maxHeight: .infinity)
    }

    /// Rev D §6 t5: one row — ring 30 with "34" (11 pt, no "%"; an empty slot for a plan with no 5h), the mark, the
    /// name (58 pt), the Weekly's 5 pt bar and figure (12 pt).
    private var mini: some View {
        HStack(spacing: 8) {
            Group {
                if let five = face.fiveHour { ring(five, figure: .digits, figureSize: 11) } else { Color.clear }
            }
            .frame(width: 30, height: 30)
            ProviderMark(provider: face.account.provider, size: 13)
            Text(face.account.label).font(.system(size: 13)).foregroundStyle(p.txt).lineLimit(1).frame(width: 58, alignment: .leading)
            bar(5)
            FaceFigure(pct: weekPct, pace: face.pace, accent: accent, size: 12, blank: blank, dimmed: face.isDimmed)
                .frame(width: 30, alignment: .trailing)
        }
        .frame(height: 42)
    }

    /// No reading: the chart still draws its guides and pace line, dimmed — never a blank hole.
    static let emptyChart = TrendChart.Model(samples: [], nowX: nil, projection: nil, over: false, startLabel: "", endLabel: "")
}

// MARK: - Lock screen

/// Lock screen (rev D §6). iOS tints these, so colour can't carry anything: every figure is the Used one, whatever the
/// Usage display (Q "Vs pace on the lock screen"). Circular: the 5h ring with digits only. Rectangular: ring 44 beside
/// the Weekly ("Teams" and its figure, the bar, "Weekly · Sun"). Inline: the ring glyph as an image, then
/// "34% · Teams Weekly 52%" ("34% · Weekly 52%" where that does not fit). A plan with no 5h: no ring (B1).
struct LockFace: View {
    let entry: FaceEntry
    var familyOverride: WidgetFamily?
    @Environment(\.widgetFamily) private var envFamily
    private var family: WidgetFamily { familyOverride ?? envFamily }

    var body: some View {
        if let base = entry.face {
            let face = base.grammar
            switch family {
            case .accessoryCircular: circular(face)
            case .accessoryRectangular: rectangular(face)
            default: inline(face)
            }
        } else {
            Text(family == .accessoryInline ? "Cooldown: add an account" : "Add an account")
        }
    }

    @ViewBuilder private func circular(_ face: Face) -> some View {
        if let five = face.fiveHour {
            ZStack {
                AccessoryWidgetBackground()
                FaceRing(five: five, accent: .white, figure: (five.pct ?? 0) >= 100 && face.status != .signIn ? .text("MAX") : .usedDigits,
                         greyed: face.fiveGreyed, blank: face.status == .signIn, figureSize: 19, surface: .clear, mono: .white,
                         bandRatio: 5.0 / 64.0)
                    .padding(4)
            }
        } else {
            // A plan with no 5h (Robin, 2026-11-02): no ring — the Weekly as Android's no-5h status-bar glyph, the small
            // Weekly bar with its pace tick (the red cross when full), the Used figure above it. No "W".
            ZStack {
                AccessoryWidgetBackground()
                VStack(spacing: 2) {
                    Display.grammarFigure(face.status == .signIn ? "—" : face.leadSpent ? "MAX" : used(face.pct, face), size: 17)
                        .foregroundStyle(.white)
                        .lineLimit(1).minimumScaleFactor(0.6)
                    WeeklyBarGlyph(pct: face.status == .signIn ? nil : face.pct, pace: face.pace, accent: .white, mono: .white)
                        .frame(width: 40, height: 28)
                }
                .padding(.horizontal, 6)
            }
            .accessibilityElement()
            .accessibilityLabel(face.status == .signIn ? "Weekly, no reading" : "Weekly, \(face.pct.map { "\(Int($0)) percent" } ?? "no reading")")
        }
    }

    private func rectangular(_ face: Face) -> some View {
        HStack(spacing: 8) {
            if let five = face.fiveHour {
                FaceRing(five: five, accent: .white, figure: .used, greyed: face.fiveGreyed, blank: face.status == .signIn,
                         surface: .clear, mono: .white)
                    .frame(width: 44, height: 44)
            }
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 4) {
                    ProviderMark(provider: face.account.provider, size: 13, color: .white)
                    Text(face.account.label).font(.system(size: 13, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.8)
                    Spacer(minLength: 2)
                    FaceFigure(pct: face.leadsWeekly ? face.pct : nil, pace: nil, accent: .white, size: 17,
                                  blank: face.status == .signIn, used: true)
                        .fixedSize()
                }
                FaceBar(pct: face.status == .signIn || !face.leadsWeekly ? nil : face.pct, pace: face.pace, accent: .white,
                           height: 6, surface: .clear)
                    .padding(.top, 5).padding(.bottom, 4)
                rectLine(face).font(.system(size: 12)).opacity(0.88).lineLimit(1)
            }
        }
    }

    private func rectLine(_ face: Face) -> Text {
        if face.status == .signIn { return Text("Sign in again") }
        guard face.leadsWeekly, let reset = face.reset else { return Text("Weekly not started") }
        if face.leadSpent { return Text("Limit · resets \(face.resetClock(reset))") }
        return Text("Weekly · \(CardContent.weekday(reset.ms, .current))")
    }

    @ViewBuilder private func inline(_ face: Face) -> some View {
        let week = used(face.leadsWeekly ? face.pct : nil, face)
        if let five = face.fiveHour {
            let fig = used(five.pct, face)
            let long = "\(fig) · \(face.account.label) Weekly \(week)", short = "\(fig) · Weekly \(week)"
            ViewThatFits {
                if let glyph = glyph(five, greyed: face.fiveGreyed) {
                    Label { Text(long) } icon: { glyph }
                    Label { Text(short) } icon: { glyph }
                }
                Text(short)
            }
        } else {
            Text("\(face.account.label) Weekly \(week)")
        }
    }

    private func used(_ pct: Double?, _ face: Face) -> String {
        face.status == .signIn ? "—" : pct.map { "\(Int($0))%" } ?? "—"
    }

    /// The ring glyph as an image: the inline slot takes one image and one line of text.
    @MainActor private func glyph(_ five: Face.FiveHour, greyed: Bool) -> Image? {
        let renderer = ImageRenderer(content: FiveGlyph(pct: five.pct, pace: five.pace, accent: .white, greyed: greyed, mono: .white)
            .frame(width: 16, height: 16)
            .environment(\.colorScheme, .dark))
        renderer.scale = 3
        return renderer.uiImage.map { Image(uiImage: $0).renderingMode(.template) }
    }
}

/// The accent follows the widget's own appearance; the palette tells us which one it is.
func colorSchemeFor(_ p: Palette) -> ColorScheme { p.bg == Palette.dark.bg ? .dark : .light }

// MARK: - Live Activity

/// The lock-screen panel: one or two rows, or the final frame at a reset. Rev D §6 laRow: the 5h ring (48 pt alone,
/// 36 pt in the Duet) left, the Weekly stack — "Teams · Weekly", its bar, its lines — and the Weekly figure (44 / 36
/// pt) right; the thin 5h line is gone. A plan with no 5h keeps an empty ring slot, so both bars start at one x.
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
                laRow(row, ring: 48, big: 44, single: true)
            } else {
                VStack(spacing: 10) { ForEach(state.rows, id: \.key) { laRow($0, ring: 36, big: 36, single: false) } }
            }
        }
        .foregroundStyle(p.ink)
        .padding(.horizontal, 16).padding(.vertical, 12)
    }

    private func laRow(_ row: CooldownActivityAttributes.Row, ring: CGFloat, big: CGFloat, single: Bool) -> some View {
        HStack(spacing: 12) {
            Group {
                if let five = row.five {
                    FaceRing(five: five, accent: row.accent, greyed: row.isWeekGated, blank: row.signIn, dimmed: row.asOf != nil,
                             surface: p.card)
                } else {
                    Color.clear
                }
            }
            .frame(width: ring, height: ring)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 5) {
                    ProviderMark(provider: row.providerValue, size: 15)
                    Text("\(row.label) · Weekly").font(.system(size: 15, weight: .semibold)).lineLimit(1)
                }
                FaceBar(pct: row.signIn ? nil : row.weekPct, pace: row.weekPace, accent: row.accent, height: 8,
                           dimmed: row.asOf != nil, surface: p.card)
                    .padding(.top, 6)
                ForEach(Array(PanelLines.lines(row, single: single, p).enumerated()), id: \.offset) { i, line in
                    line.font(.system(size: single ? 12.5 : 12)).padding(.top, i == 0 ? 6 : 2)
                }
            }
            FaceFigure(pct: row.weekPct, pace: row.weekPace, accent: row.accent, size: big, weight: .semibold,
                          blank: row.signIn, dimmed: row.asOf != nil)
                .fixedSize()
        }
    }
}

/// The Weekly's lines in the Live Activity (CCRM-104 (Pace Line Parity)). One account: the pace line "6d 23h behind · Sun
/// 9:30 pm" over the 5h's line led by its ring glyph ("◔ resets in 2h 20m · on track for ~69%"); the Duet: the pace line
/// alone. States replace them: sign in, "as of", spent, reset, not started.
enum PanelLines {
    private static func text(_ t: Text) -> AnyView { AnyView(t.lineLimit(1)) }

    static func lines(_ row: CooldownActivityAttributes.Row, single: Bool, _ p: Palette) -> [AnyView] {
        if row.signIn { return [text(Text(single ? "Sign-in stopped working · open Cooldown" : "Sign in again").foregroundStyle(p.orange))] }
        if let asOf = row.asOf { return [text(Text("as of \(shortClock(asOf))").foregroundStyle(p.orange))] }
        guard row.leadsWeekly, let reset = row.reset else { return [text(Text("Weekly not started").foregroundStyle(p.sub))] }
        let clock = row.clock(reset)
        if row.hasReset { return [text(Text(single ? "Weekly reset at \(clock) · back at 0%" : "Weekly reset · 0%").foregroundStyle(p.txt))] }
        if row.isSpent { return [text(Text("Out until \(clock)").foregroundStyle(p.red))] }
        if let line = row.paceLine {
            let pace = AnyView(FitLine(variants: line.variants(rest: p.txt, ink: p.ink, scheme: colorSchemeFor(p))))
            return single ? [pace, fiveLine(row, p)].compactMap { $0 } : [pace]
        }
        if single {
            return [row.weeklyLine.map { text(Text($0).foregroundStyle(p.txt)) }, text(Text("Resets \(clock)").foregroundStyle(p.txt))].compactMap { $0 }
        }
        return [text(Text(row.weeklyWords.map { "\($0) · \(clock)" } ?? "Resets \(clock)").foregroundStyle(p.txt))]
    }

    /// "◔ resets in 2h 20m · on track for ~69%": the 5h's own line, led by a small ring glyph (orange while it binds).
    static func fiveLine(_ row: CooldownActivityAttributes.Row, _ p: Palette) -> AnyView? {
        guard let five = row.five, !row.isWeekGated, let reset = row.fiveReset, reset > .now else { return nil }
        var words = "resets in \(Fmt.compactDuration(ms: reset.ms - Date.now.ms))"
        if let fold = row.fiveFold { words += " · \(fold)" }
        return AnyView(HStack(spacing: 4) {
            FiveGlyph(pct: row.signIn ? nil : five.pct, pace: five.pace, accent: row.accent, greyed: false)
                .frame(width: 11, height: 11).accessibilityHidden(true)
            Text(words).lineLimit(1).foregroundStyle((row.companionLoud ?? false) ? p.orange : p.sub)
        })
    }
}

/// The expanded Island (rev D §6 LA3): ring 48 left; "Teams · Weekly" (15 pt) with the Weekly figure (34 pt), the
/// 8 pt bar and "Resets Sun 9:30 pm · in 4d 6h" right.
struct IslandExpanded: View {
    let row: CooldownActivityAttributes.Row
    @Environment(\.palette) private var p

    var body: some View {
        HStack(spacing: 14) {
            if let five = row.five {
                FaceRing(five: five, accent: row.accent, greyed: row.isWeekGated, blank: row.signIn, dimmed: row.asOf != nil,
                         surface: .black)
                    .frame(width: 48, height: 48)
            }
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline) {
                    HStack(spacing: 6) {
                        ProviderMark(provider: row.providerValue, size: 18)
                        Text("\(row.label) · Weekly").font(.system(size: 15, weight: .semibold)).lineLimit(1)
                    }
                    Spacer(minLength: 4)
                    FaceFigure(pct: row.weekPct, pace: row.weekPace, accent: row.accent, size: 34, weight: .semibold,
                                  blank: row.signIn, dimmed: row.asOf != nil)
                        .fixedSize()
                }
                FaceBar(pct: row.signIn ? nil : row.weekPct, pace: row.weekPace, accent: row.accent, height: 8,
                           dimmed: row.asOf != nil, surface: .black)
                IslandLine(row: row).font(.system(size: 13)).foregroundStyle(p.sub).padding(.top, 8)
            }
        }
        .foregroundStyle(p.ink)
    }
}

/// The Island's expanded line: the pace line "6d 23h behind · Sun 9:30 pm" (CCRM-104; "Out until …" while the week is
/// spent), or the state that replaces it.
struct IslandLine: View {
    let row: CooldownActivityAttributes.Row
    var body: some View {
        if row.leadsWeekly, let line = row.paceLine, !row.isSpent, !row.signIn, row.asOf == nil, !row.hasReset {
            // CCRM-104 (Pace Line Parity) D3: the pace line; the reset is inside it.
            PaceLineView(line: line)
        } else if row.leadsWeekly, let reset = row.reset, !row.signIn, row.asOf == nil, !row.hasReset {
            let left = Fmt.compactDuration(ms: max(reset.ms - Date.now.ms, 0))
            Text("\(row.isSpent ? "Out until" : "Resets") \(row.clock(reset)) · in \(left)").lineLimit(1)
        } else if let first = PanelLines.lines(row, single: true, .dark).first {
            first.lineLimit(1)
        }
    }
}
