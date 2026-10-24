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
            Face(account: Account(key: key, label: label, provider: p), pct: pct, pace: pace,
                 reset: now.addingTimeInterval(minutes * 60), runOut: nil, weeklyPct: 18, weeklyPace: 43,
                 weeklyReset: now.addingTimeInterval(4 * 86_400), fetchedAt: now, status: .fresh)
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
                    Text(face.weeklyReset.map { "Resets \(CardContent.dayClock($0.ms, Date.now.ms, false, .current))" } ?? "")
                        .foregroundStyle(p.sub)
                } else if face.atLimit, let reset = face.reset {
                    Text("Limit · resets \(shortClock(reset))").foregroundStyle(p.red)
                } else if let runOut = face.runOut {
                    Text("~ runs out \(shortClock(runOut))").foregroundStyle(p.sub)
                } else {
                    Text(face.reset.map { "Resets \(shortClock($0))" } ?? "No window running").foregroundStyle(p.sub)
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
        if let face = entry.face {
            let weekly = entry.window == .weekly
            let pct = weekly ? face.weeklyPct : face.pct
            let pace = weekly ? face.weeklyPace : face.pace
            VStack(alignment: .leading, spacing: 0) {
                FaceLabel(face: face, suffix: weekly ? "Weekly" : "5h")
                Spacer(minLength: 6)
                Text(face.status == .signIn || face.status == .plan ? "—" : (pct.map { "\(Int($0))%" } ?? "—"))
                    .font(.system(size: (pct ?? 0) >= 100 ? 40 : 46, weight: .bold)).monospacedDigit()
                    .foregroundStyle(face.isDimmed || pct == nil ? p.sub : p.severity(pct, accent: face.account.provider.accent(colorSchemeFor(p))))
                    .minimumScaleFactor(0.7).lineLimit(1)
                Spacer(minLength: 6)
                UsageBar(pct: face.status == .signIn ? nil : pct, pace: pace, accent: face.account.provider.accent(colorSchemeFor(p)),
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
        if let face = entry.face {
            VStack(alignment: .leading, spacing: 4) {
                Text("5h reset").font(.system(size: 14)).foregroundStyle(p.txt)
                if let reset = face.reset, face.status != .signIn {
                    Text(timerInterval: Date.now...max(reset, .now), countsDown: true)
                        .font(.system(size: 35, weight: .bold)).monospacedDigit()
                        .foregroundStyle(p.ink).lineLimit(1).minimumScaleFactor(0.6)
                        .padding(.top, 8)
                    Text(face.atLimit ? "Limit · opens \(shortClock(reset))" : "at \(shortClock(reset))")
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

struct RingFace: View {
    let entry: FaceEntry
    /// Tests render the medium face outside a widget, where the family can't be set.
    var familyOverride: WidgetFamily?
    @Environment(\.widgetFamily) private var envFamily
    private var family: WidgetFamily { familyOverride ?? envFamily }
    @Environment(\.palette) private var p

    var body: some View {
        if entry.faces.isEmpty {
            NoAccountView()
        } else if family == .systemMedium {
            HStack(alignment: .top, spacing: 0) {
                ForEach(entry.faces.prefix(3), id: \.account.key) { face in
                    Link(destination: DeepLink.account(face.account.key)) { one(face, ring: 80, line: 6.5) }
                        .frame(maxWidth: .infinity)
                }
            }
        } else if let face = entry.face {
            one(face, ring: 98, line: 8)
        }
    }

    private func one(_ face: Face, ring: CGFloat, line: CGFloat) -> some View {
        VStack(spacing: 2) {
            UsageRing(pct: face.status == .signIn ? nil : face.pct, pace: face.pace,
                      accent: face.account.provider.accent(colorSchemeFor(p)), lineWidth: line,
                      fontSize: ring * 0.23, dimmed: face.isDimmed)
                .frame(width: ring, height: ring)
            FaceLabel(face: face, suffix: nil, size: 13).padding(.top, 6)
            Group {
                if face.status == .fresh, let reset = face.reset { Text(shortClock(reset)).foregroundStyle(p.sub) }
                else { FaceFootnote(face: face) }
            }
            .font(.system(size: 11.5))
        }
    }
}

struct StripFace: View {
    let entry: FaceEntry
    @Environment(\.palette) private var p
    var body: some View {
        if let face = entry.face {
            let accent = face.account.provider.accent(colorSchemeFor(p))
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline) {
                    FaceLabel(face: face, size: 15)
                    Spacer()
                    Text(face.status == .signIn ? "—" : (face.pct.map { "\(Int($0))%" } ?? "—"))
                        .font(.system(size: 34, weight: .bold)).monospacedDigit()
                        .foregroundStyle(face.isDimmed ? p.sub : p.severity(face.pct, accent: accent))
                }
                UsageBar(pct: face.status == .signIn ? nil : face.pct, pace: face.pace, accent: accent, height: 8, dimmed: face.isDimmed)
                    .padding(.top, 6)
                stripLine(face).font(.system(size: 12.5)).padding(.top, 6)
                Spacer(minLength: 6)
                HStack(spacing: 10) {
                    Text("Weekly").font(.system(size: 13)).foregroundStyle(p.txt).frame(width: 52, alignment: .leading)
                    UsageBar(pct: face.status == .signIn ? nil : face.weeklyPct, pace: face.weeklyPace, accent: accent, height: 6, dimmed: face.isDimmed)
                    Text(face.status == .signIn ? "—" : (face.weeklyPct.map { "\(Int($0))%" } ?? "—"))
                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(p.txt).frame(width: 40, alignment: .trailing)
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
                (Text("Limit reached").foregroundStyle(p.red) + Text(" · resets \(shortClock(reset))").foregroundStyle(p.sub))
            } else if let runOut = face.runOut, let reset = face.reset {
                Text("~ runs out \(shortClock(runOut)) · resets \(shortClock(reset))").foregroundStyle(p.sub)
            } else if let reset = face.reset {
                Text("Resets in \(Fmt.compactDuration(ms: reset.ms - Date.now.ms)) · \(shortClock(reset))").foregroundStyle(p.sub)
            } else {
                FaceFootnote(face: face)
            }
        }
    }
}

struct OverviewFace: View {
    let entry: FaceEntry
    @Environment(\.palette) private var p
    var body: some View {
        if entry.faces.isEmpty {
            NoAccountView()
        } else {
            VStack(spacing: 0) {
                HStack {
                    ForEach(entry.faces.prefix(3), id: \.account.key) { face in
                        VStack(spacing: 4) {
                            UsageRing(pct: face.pct, pace: face.pace, accent: face.account.provider.accent(colorSchemeFor(p)),
                                      lineWidth: 5.5, fontSize: 15, dimmed: face.isDimmed)
                                .frame(width: 62, height: 62)
                            FaceLabel(face: face, suffix: nil, size: 12)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                ForEach(entry.faces.prefix(3), id: \.account.key) { face in row(face).padding(.top, 10) }
                Spacer(minLength: 0)
            }
        }
    }

    private func row(_ face: Face) -> some View {
        let accent = face.account.provider.accent(colorSchemeFor(p))
        return Link(destination: DeepLink.account(face.account.key)) {
            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    FaceLabel(face: face, suffix: nil, size: 14)
                    Spacer()
                    FaceFootnote(face: face).font(.system(size: 12))
                }
                line("5h", face.pct, face.pace, accent, height: 7, bold: true, dimmed: face.isDimmed)
                line("Weekly", face.weeklyPct, face.weeklyPace, accent, height: 5, bold: false, dimmed: face.isDimmed)
            }
        }
    }

    private func line(_ name: String, _ pct: Double?, _ pace: Double?, _ accent: Color, height: CGFloat, bold: Bool, dimmed: Bool) -> some View {
        HStack(spacing: 10) {
            Text(name).font(.system(size: 12)).foregroundStyle(p.sub).frame(width: 44, alignment: .leading)
            UsageBar(pct: pct, pace: pace, accent: accent, height: height, dimmed: dimmed)
            Text(pct.map { "\(Int($0))%" } ?? "—").font(.system(size: bold ? 14 : 13, weight: bold ? .bold : .regular))
                .foregroundStyle(bold ? p.severity(pct, accent: accent) : p.txt).frame(width: 42, alignment: .trailing)
        }
    }
}

struct LockFace: View {
    let entry: FaceEntry
    var familyOverride: WidgetFamily?
    @Environment(\.widgetFamily) private var envFamily
    private var family: WidgetFamily { familyOverride ?? envFamily }

    var body: some View {
        if let face = entry.face {
            switch family {
            case .accessoryCircular:
                ZStack {
                    AccessoryWidgetBackground()
                    UsageRing(pct: face.status == .signIn ? nil : face.pct, pace: face.pace, accent: .white, lineWidth: 5,
                              label: face.status == .signIn ? "—" : face.atLimit ? "MAX" : (face.pct.map { "\(Int($0))" } ?? "—"),
                              sublabel: "5h", fontSize: 19, mono: .white)
                        .padding(2)
                }
            case .accessoryRectangular:
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 5) {
                        ProviderMark(provider: face.account.provider, size: 14, color: .white)
                        Text("\(face.account.label) · 5h").font(.system(size: 14, weight: .semibold)).lineLimit(1)
                        Spacer(minLength: 2)
                        Text(face.status == .signIn ? "—" : (face.pct.map { "\(Int($0))%" } ?? "—"))
                            .font(.system(size: 17, weight: .bold)).monospacedDigit()
                    }
                    UsageBar(pct: face.status == .signIn ? nil : face.pct, pace: face.pace, accent: .white, height: 6)
                    Group {
                        if face.status == .signIn { Text("Sign in again") }
                        else if face.atLimit, let reset = face.reset { Text("Limit · resets \(shortClock(reset))") }
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
        let pct = face.status == .signIn ? "—" : (face.pct.map { "\(Int($0))%" } ?? "—")
        let tail = withReset ? (face.reset.map { " · resets \(shortClock($0))" } ?? "") : ""
        return "\(face.account.label) 5h \(pct)\(tail)"
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
        let accent = row.providerValue.accent(.dark)
        return HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    ProviderMark(provider: row.providerValue, size: 14)
                    Text("\(row.label) · 5h").font(.system(size: 14)).lineLimit(1)
                    Text("·").foregroundStyle(p.txt)
                    RowLine(row: row, long: false).font(.system(size: 12)).lineLimit(1).fixedSize()
                }
                UsageBar(pct: row.signIn ? nil : row.pct, pace: row.pace, accent: accent, height: 7, dimmed: row.asOf != nil)
                HStack(spacing: 7) {
                    Text("Weekly").frame(width: 42, alignment: .leading)
                    UsageBar(pct: row.signIn ? nil : row.weeklyPct, pace: row.weeklyPace, accent: accent, height: 4, dimmed: row.asOf != nil)
                    Text(row.signIn ? "—" : (row.weeklyPct.map { "\(Int($0))%" } ?? "—")).monospacedDigit().frame(width: 36, alignment: .trailing)
                }
                .font(.system(size: 11.5)).foregroundStyle(p.txt)
            }
            BigNumber(row: row, size: (row.pct ?? 0) >= 100 ? 32 : 38).frame(width: 84, alignment: .trailing)
        }
    }

    /// One account: the full 5h row with the clock time, and Weekly.
    private func single(_ row: CooldownActivityAttributes.Row) -> some View {
        let accent = row.providerValue.accent(.dark)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 7) {
                    HStack(spacing: 6) { ProviderMark(provider: row.providerValue, size: 15); Text("\(row.label) · 5h").font(.system(size: 15)) }
                    UsageBar(pct: row.signIn ? nil : row.pct, pace: row.pace, accent: accent, height: 8, dimmed: row.asOf != nil)
                    RowLine(row: row, long: true).font(.system(size: 12.5))
                }
                BigNumber(row: row, size: 52)
            }
            HStack(spacing: 8) {
                Text("Weekly").frame(width: 48, alignment: .leading)
                UsageBar(pct: row.weeklyPct, pace: row.weeklyPace, accent: accent, height: 5)
                Text(row.weeklyPct.map { "\(Int($0))%" } ?? "—")
            }
            .font(.system(size: 12.5)).foregroundStyle(p.txt)
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
        } else if let reset = row.reset {
            if (row.pct ?? 0) >= 100 {
                HStack(spacing: 0) {
                    Text(long ? "Limit reached · resets in " : "limit · ").foregroundStyle(p.red)
                    Text(timerInterval: Date.now...max(reset, .now), countsDown: true).monospacedDigit().foregroundStyle(p.txt)
                }
            } else if let runOut = row.runOut {
                Text("runs out \(shortClock(runOut))").foregroundStyle(p.txt)
            } else {
                HStack(spacing: 0) {
                    if long { Text("Resets in ").foregroundStyle(p.txt) }
                    Text(timerInterval: Date.now...max(reset, .now), countsDown: true).monospacedDigit().foregroundStyle(p.txt)
                    if long { Text(" · \(shortClock(reset))").foregroundStyle(p.txt) }
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
        Text(row.signIn ? "—" : (row.pct.map { "\(Int($0))%" } ?? "—"))
            .font(.system(size: size, weight: .semibold)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.6)
            .foregroundStyle(row.signIn || row.asOf != nil ? p.sub : p.severity(row.pct, accent: row.providerValue.accent(.dark)))
    }
}
