import CooldownCore
import SwiftUI
import Testing
import WidgetKit

@testable import Cooldown

/// CCRM-94 (iOS App): renders every widget, lock-screen and Live Activity face in every state (wireframe rev B §4–§6)
/// to PNGs in SHOT_DIR, for comparison with design/2026-10-23-ios-wireframes.html. Without SHOT_DIR it only checks
/// that each face renders.
@MainActor
@Suite("Surface faces render")
struct SurfaceRenderTests {
    let now = Date.now

    func face(_ label: String, _ provider: Provider, pct: Double?, pace: Double?, minutes: Double = 95,
              runOut: Double? = nil, status: Face.Status = .fresh, fetchedMinutesAgo: Double = 3,
              weeklyPct: Double = 18, weeklyPace: Double = 43, weeklyOnly: Bool = false, notStarted: Bool = false,
              headline: Headline = .weekly, binding: Bool? = nil) -> Face {
        // A unique key per sample: rows and rings are identified by account key.
        let reset = now.addingTimeInterval(minutes * 60), weeklyReset = now.addingTimeInterval((1 - weeklyPace / 100) * 7 * 86_400)
        var f = Face(account: Account(key: "\(label)-\(Int(pct ?? -1))-\(status)-\(weeklyOnly)", label: label, provider: provider),
                     pct: weeklyOnly ? nil : notStarted ? 0 : pct, pace: notStarted ? nil : pace,
                     reset: weeklyOnly || notStarted ? nil : reset, runOut: runOut.map { now.addingTimeInterval($0 * 60) },
                     weeklyPct: weeklyPct, weeklyPace: weeklyPace, weeklyReset: weeklyReset,
                     fetchedAt: now.addingTimeInterval(-fetchedMinutesAgo * 60), status: status)
        // A steady climb to the current reading: points every 20 minutes through the 5h window, every 12 hours
        // through the week, so the chart and the projection have something real to draw.
        let sessionStart = reset.ms - HistoryStats.sessionMs, weekStart = weeklyReset.ms - HistoryStats.weekMs
        var history: [HistoryPoint] = []
        var t = max(sessionStart, weekStart)
        while t <= now.ms {
            let sFrac = Double(t - sessionStart) / Double(now.ms - sessionStart)
            let wFrac = Double(t - weekStart) / Double(now.ms - weekStart)
            history.append(HistoryPoint(atMs: t, sessionPct: t >= sessionStart ? (pct ?? 0) * max(sFrac, 0) : nil,
                                        sessionResetAtMs: t >= sessionStart ? reset.ms : 0,
                                        weeklyPct: weeklyPct * wFrac, weeklyResetAtMs: weeklyReset.ms))
            t += t >= sessionStart ? 20 * 60_000 : 12 * 3_600_000
        }
        f.attachContent(sessionWindow: weeklyOnly ? nil : UsageWindow(percent: notStarted ? 0 : pct, resetsAtMs: notStarted ? nil : reset.ms, serverSeverity: nil),
                        weeklyWindow: UsageWindow(percent: weeklyPct, resetsAtMs: weeklyReset.ms, serverSeverity: nil),
                        history: history, nowMs: now.ms)
        // v2.0 (CCRM-96 (Weekly First)): what Face.build sets from the Headline and core §6.2.
        f.headline = headline
        f.hasSessionWindow = !weeklyOnly
        f.sessionBinding = binding ?? Pace.sessionBinding(sessionPct: weeklyOnly ? nil : pct, weeklyPct: weeklyPct,
                                                         sessionHitsLimitBeforeReset: runOut != nil)
        return f
    }

    var states: [(String, Face)] {
        [("onpace", face("Teams", .claude, pct: 46, pace: 46)),
         ("above", face("Teams", .claude, pct: 62, pace: 40, minutes: 179, runOut: 130)),
         ("max", face("Teams", .claude, pct: 100, pace: 60, minutes: 42)),
         ("stale", face("Product", .claude, pct: 26, pace: 68, status: .stale, fetchedMinutesAgo: 420)),
         ("signin", face("Product", .claude, pct: 26, pace: 68, status: .signIn)),
         ("chatgpt", face("Personal", .chatgpt, pct: 26, pace: 68))]
    }

    func save<V: View>(_ name: String, _ view: V, width: CGFloat, height: CGFloat, scheme: ColorScheme = .dark,
                       background: Color? = nil) {
        let palette = Palette.current(scheme)
        let framed = view
            .padding(16)
            .frame(width: width, height: height)
            .background(background ?? palette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .environment(\.palette, palette)
            .environment(\.colorScheme, scheme)
        let renderer = ImageRenderer(content: framed)
        renderer.scale = 2
        let image = renderer.uiImage
        #expect(image != nil, "\(name) did not render")
        if let dir = ProcessInfo.processInfo.environment["SHOT_DIR"], let png = image?.pngData() {
            try? png.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
        }
    }

    @Test("home widgets, every state")
    func homeWidgets() {
        for (state, f) in states {
            let entry = FaceEntry(date: now, faces: [f], key: f.account.key)
            save("number-\(state)", NumberFace(entry: entry), width: 170, height: 170)
            save("countdown-\(state)", CountdownFace(entry: entry), width: 170, height: 170)
            save("bars-\(state)", BarsFace(entry: entry), width: 160, height: 160)
            save("strip-\(state)", StripFace(entry: entry), width: 364, height: 170)
        }
        for (state, f) in extraStates {
            save("bars-\(state)", BarsFace(entry: FaceEntry(date: now, faces: [f], key: f.account.key)), width: 160, height: 160)
        }
        save("bars-se", BarsFace(entry: FaceEntry(date: now, faces: [states[0].1], key: nil)), width: 141, height: 141)
        save("bars-light", BarsFace(entry: FaceEntry(date: now, faces: [states[1].1], key: nil)), width: 160, height: 160, scheme: .light)
        save("number-light", NumberFace(entry: FaceEntry(date: now, faces: [states[5].1], key: nil)), width: 170, height: 170, scheme: .light)
        save("empty", NumberFace(entry: FaceEntry(date: now, faces: [], key: nil)), width: 170, height: 170)
    }

    var extraStates: [(String, Face)] {
        [("weeklyonly", face("Personal", .chatgpt, pct: nil, pace: nil, weeklyPct: 42, weeklyPace: 50, weeklyOnly: true)),
         ("notstarted", face("ChatGPT", .chatgpt, pct: 0, pace: nil, weeklyPct: 13, weeklyPace: 7, notStarted: true)),
         ("nodata", face("Claude", .claude, pct: nil, pace: nil, status: .noData))]
    }

    /// Rev C's Overview: every account count in Medium, Large and Extra Large (iPad 11" More Space sizes), and the states.
    @Test("Overview, rev C: counts and families")
    func overview() {
        let pool = [face("ChatGPT", .chatgpt, pct: 5, pace: 2, minutes: 290, weeklyPct: 13, weeklyPace: 7),
                    face("Claude", .claude, pct: 55, pace: 70, minutes: 90, weeklyPct: 75, weeklyPace: 80),
                    face("Work", .claude, pct: 62, pace: 40, minutes: 180, runOut: 128, weeklyPct: 48, weeklyPace: 45),
                    face("Product", .claude, pct: 100, pace: 85, minutes: 45, weeklyPct: 91, weeklyPace: 70),
                    extraStates[0].1, states[4].1, states[3].1, extraStates[1].1, states[5].1]
        for n in [1, 2, 3, 4, 6, 9] {
            let entry = FaceEntry(date: now, faces: Array(pool.prefix(n)), key: nil)
            save("ov-large-\(n)", OverviewFace(entry: entry, familyOverride: .systemLarge), width: 346, height: 346)
            save("ov-xl-\(n)", OverviewFace(entry: entry, familyOverride: .systemExtraLarge), width: 724, height: 346)
            if n <= 4 { save("ov-medium-\(n)", OverviewFace(entry: entry, familyOverride: .systemMedium), width: 346, height: 157) }
        }
        let two = FaceEntry(date: now, faces: Array(pool.prefix(2)), key: nil)
        save("ov-large-2-light", OverviewFace(entry: two, familyOverride: .systemLarge), width: 346, height: 346, scheme: .light)
        save("ov-large-2-iphone", OverviewFace(entry: two, familyOverride: .systemLarge), width: 338, height: 354)
        for (i, f) in [pool[4], extraStates[1].1, states[4].1, states[3].1, pool[2], pool[3], extraStates[2].1].enumerated() {
            save("ov-state-\(i)", OverviewFace(entry: FaceEntry(date: now, faces: [f], key: nil), familyOverride: .systemLarge),
                 width: 346, height: 346)
        }
        save("ov-states-t4", OverviewFace(entry: FaceEntry(date: now, faces: [extraStates[1].1, pool[4], states[4].1], key: nil),
                                          familyOverride: .systemLarge), width: 346, height: 346)
    }

    /// A weekly-only plan leads with Weekly on every 5h-led surface, and a Live Activity row whose window has reset
    /// reads "reset · 0%" rather than a stopped 0:00 (iPad device check, 2026-10-24).
    @Test("weekly-only plans and a reset Live Activity row")
    func weeklyOnlyAndReset() {
        let weekly = extraStates[0].1
        let entry = FaceEntry(date: now, faces: [weekly], key: weekly.account.key)
        save("wk-number", NumberFace(entry: entry), width: 170, height: 170)
        save("wk-countdown", CountdownFace(entry: entry), width: 170, height: 170)
        save("wk-strip", StripFace(entry: entry), width: 364, height: 170)
        let wall = Color(hex: 0x6E5F95)
        save("wk-lock-circle", LockFace(entry: entry, familyOverride: .accessoryCircular).foregroundStyle(.white), width: 104, height: 104, background: wall)
        save("wk-lock-rect", LockFace(entry: entry, familyOverride: .accessoryRectangular).foregroundStyle(.white), width: 204, height: 108, background: wall)
        let bg = Color(hex: 0x1C1C1E)
        // CCRM-103: rows are Weekly-led; the 5h whose window has reset is the ring's, read 0%.
        var reset = CooldownActivityAttributes.Row(states[0].1)
        reset.fiveReset = now.addingTimeInterval(-60)
        #expect(reset.five?.pct == 0 && reset.five?.pace == nil)
        save("la-reset-duet", LockPanel(state: .init(rows: [reset, CooldownActivityAttributes.Row(states[5].1)], ended: nil)),
             width: 370, height: 160, background: bg)
        save("la-weekly-duet", LockPanel(state: .init(rows: [CooldownActivityAttributes.Row(weekly), CooldownActivityAttributes.Row(states[0].1)], ended: nil)),
             width: 370, height: 160, background: bg)
        save("la-weekly-single", LockPanel(state: .init(rows: [CooldownActivityAttributes.Row(weekly)], ended: nil)), width: 370, height: 160, background: bg)
        #expect(weekly.leading.leadsWeekly && weekly.leading.pct == 42 && weekly.leading.weeklyPct == nil)
        #expect(states[0].1.leading == states[0].1)
    }

    /// The Android Appearance ports (approved 24 Oct): Used/Left, Clock first, 24-hour, Time down, red off, theme and
    /// account colours — each rendered on the faces that show it.
    @Test("display choices")
    func displayChoices() {
        let d = Prefs.defaults
        let keys = [Display.Key.usageLeft, Display.Key.clockFirst, Display.Key.timeFormat, Display.Key.chartDown,
                    Display.Key.redPastPace, Display.Key.themeColour]
        defer { keys.forEach { d.removeObject(forKey: $0) } }
        let work = face("Work", .claude, pct: 62, pace: 40, minutes: 180, runOut: 128, weeklyPct: 48, weeklyPace: 45)
        func shoot(_ tag: String) {
            let entry = FaceEntry(date: now, faces: [work], key: work.account.key)
            save("dc-\(tag)-number", NumberFace(entry: entry), width: 170, height: 170)
            save("dc-\(tag)-strip", StripFace(entry: entry), width: 364, height: 170)
            save("dc-\(tag)-overview", OverviewFace(entry: FaceEntry(date: now, faces: [work], key: nil), familyOverride: .systemLarge),
                 width: 346, height: 346)
        }
        shoot("default")
        d.set(true, forKey: Display.Key.usageLeft); shoot("left"); d.removeObject(forKey: Display.Key.usageLeft)
        d.set(true, forKey: Display.Key.clockFirst); d.set("24", forKey: Display.Key.timeFormat); shoot("clock24")
        d.removeObject(forKey: Display.Key.clockFirst); d.removeObject(forKey: Display.Key.timeFormat)
        d.set(true, forKey: Display.Key.chartDown); shoot("down"); d.removeObject(forKey: Display.Key.chartDown)
        d.set(false, forKey: Display.Key.redPastPace); shoot("nored"); d.removeObject(forKey: Display.Key.redPastPace)
        d.set("Blue", forKey: Display.Key.themeColour); shoot("blue")
        #expect(Display.accent(key: "x", provider: .claude, .dark) != Provider.claude.accent(.dark))
    }

    @Test("lock-screen widgets")
    func lockWidgets() {
        let wall = Color(hex: 0x6E5F95)
        for (state, f) in states.prefix(5) {
            let entry = FaceEntry(date: now, faces: [f], key: f.account.key)
            save("lock-circle-\(state)", LockFace(entry: entry, familyOverride: .accessoryCircular).foregroundStyle(.white),
                 width: 104, height: 104, background: wall)
            save("lock-rect-\(state)", LockFace(entry: entry, familyOverride: .accessoryRectangular).foregroundStyle(.white),
                 width: 204, height: 108, background: wall)
            save("lock-inline-\(state)", LockFace(entry: entry, familyOverride: .accessoryInline).foregroundStyle(.white),
                 width: 342, height: 52, background: wall)
        }
    }

    /// CCRM-103 (Hybrid Notification), rev D §6: the shape grammar on every iOS face — the 5h a ring, the Weekly a bar,
    /// whatever the Headline; a plan with no 5h per B1; a spent week greys the ring; the three end marks; Vs pace.
    @Test("shape grammar: faces, end marks, Vs pace, no 5h, spent week")
    func shapeGrammar() {
        let d = Prefs.defaults
        defer { [Display.Key.usageEndMark, Display.Key.usageLeft, Display.Key.usageVsPace].forEach { d.removeObject(forKey: $0) } }
        let teams = face("Teams", .claude, pct: 34, pace: 53, minutes: 140, weeklyPct: 52, weeklyPace: 39)
        let binding = face("Teams", .claude, pct: 93, pace: 83, minutes: 50, runOut: 35, weeklyPct: 52, weeklyPace: 39)
        let spent = face("Teams", .claude, pct: 12, pace: 20, minutes: 240, weeklyPct: 100, weeklyPace: 55)
        let personal = extraStates[0].1

        // The model: the grammar face is Weekly-led whatever the Headline, the 5h its companion; no 5h, no ring.
        for headline in [Headline.weekly, .fiveHour, .auto] {
            var f = teams
            f.headline = headline
            let g = f.lead(for: .notification).grammar
            #expect(g.leadsWeekly && g.pct == 52 && g.fiveHour?.pct == 34)
            #expect(CooldownActivityAttributes.Row(f.lead(for: .notification)).five?.pct == 34)
            #expect(CooldownActivityAttributes.Row(f.lead(for: .notification)).weekPct == 52)
        }
        #expect(personal.grammar.fiveHour == nil && CooldownActivityAttributes.Row(personal.leading).five == nil)
        #expect(spent.grammar.fiveGreyed && spent.fiveGreyed && !teams.grammar.fiveGreyed)

        // Fable E1.1: the first model limit rides on the face (hidden by Model limits off, none when the plan has none),
        // whatever leads, and Bars and Strip draw it.
        let fableCap = ModelCap(modelName: "Claude Fable",
                                window: UsageWindow(percent: 30, resetsAtMs: now.addingTimeInterval(0.61 * 7 * 86_400).ms, serverSeverity: nil))
        let limit = Face.limit(fableCap, show: true, nowMs: now.ms)
        #expect(limit?.name == "Fable" && limit?.pct == 30 && limit?.pace != nil)
        #expect(Face.limit(fableCap, show: false, nowMs: now.ms) == nil && Face.limit(nil, show: true, nowMs: now.ms) == nil)
        let passed = ModelCap(modelName: "Argon", window: UsageWindow(percent: 80, resetsAtMs: now.addingTimeInterval(-60).ms, serverSeverity: nil))
        #expect(Face.limit(passed, show: true, nowMs: now.ms)?.pct == 0)
        var fable = teams
        fable.modelLimit = limit
        #expect(fable.grammar.modelLimit == limit && fable.lead(.session).modelLimit == limit && fable.leading.modelLimit == limit)

        func shoot(_ tag: String) {
            for (name, f) in [("teams", teams), ("binding", binding), ("spent", spent), ("personal", personal), ("fable", fable)] {
                let entry = FaceEntry(date: now, faces: [f], key: f.account.key)
                save("g-\(tag)-bars-\(name)", BarsFace(entry: entry), width: 158 + 32, height: 158 + 32)
                save("g-\(tag)-number5-\(name)", NumberFace(entry: entry), width: 158 + 32, height: 158 + 32)
                save("g-\(tag)-numberw-\(name)", NumberFace(entry: FaceEntry(date: now, faces: [f], key: nil, window: .weekly)),
                     width: 158 + 32, height: 158 + 32)
                save("g-\(tag)-countdown-\(name)", CountdownFace(entry: entry), width: 158 + 32, height: 158 + 32)
                save("g-\(tag)-strip-\(name)", StripFace(entry: entry), width: 338 + 32, height: 158 + 32)
            }
            let all = [teams, binding, spent, personal]
            save("g-\(tag)-ov-large-t4", OverviewFace(entry: FaceEntry(date: now, faces: all, key: nil), familyOverride: .systemLarge),
                 width: 338 + 32, height: 354 + 32)
            save("g-\(tag)-ov-medium-t5", OverviewFace(entry: FaceEntry(date: now, faces: [teams, spent, personal], key: nil),
                                                       familyOverride: .systemMedium), width: 338 + 32, height: 158 + 32)
            save("g-\(tag)-ov-large-t3", OverviewFace(entry: FaceEntry(date: now, faces: [teams, personal], key: nil),
                                                      familyOverride: .systemLarge), width: 338 + 32, height: 354 + 32)
            save("g-\(tag)-ov-large-t1", OverviewFace(entry: FaceEntry(date: now, faces: [binding], key: nil), familyOverride: .systemLarge),
                 width: 338 + 32, height: 354 + 32)
            save("g-\(tag)-ov-xl-1", OverviewFace(entry: FaceEntry(date: now, faces: [teams], key: nil), familyOverride: .systemExtraLarge),
                 width: 724, height: 346)
            let bg = Color(hex: 0x1C1C1E)
            let rows = [teams, personal].map { CooldownActivityAttributes.Row($0) }
            save("g-\(tag)-la-single", LockPanel(state: .init(rows: [rows[0]], ended: nil)), width: 361 + 32, height: 160, background: bg)
            save("g-\(tag)-la-duet", LockPanel(state: .init(rows: rows, ended: nil)), width: 361 + 32, height: 160, background: bg)
            save("g-\(tag)-la-spent", LockPanel(state: .init(rows: [CooldownActivityAttributes.Row(spent), rows[1]], ended: nil)),
                 width: 361 + 32, height: 160, background: bg)
            save("g-\(tag)-island-expanded", IslandExpanded(row: rows[0]), width: 361 + 40, height: 110, background: .black)
        }
        shoot("line")
        Display.setUsageEndMark(.round); shoot("round")
        Display.setUsageEndMark(.pace); shoot("pace")
        d.removeObject(forKey: Display.Key.usageEndMark)
        Display.setMode(.vsPace); shoot("vs")
        Display.setMode(.left); shoot("left")
        Display.setMode(.used)
        // The iPhone SE's small widget (141 pt) with the model limit: the reset line goes first (rev C).
        save("g-bars-se-fable", BarsFace(entry: FaceEntry(date: now, faces: [fable], key: nil)), width: 141, height: 141)

        // The lock screen keeps the Used figure in Vs pace (iOS tints it): circular, rectangular, inline.
        let wall = Color(hex: 0x6E5F95)
        Display.setMode(.vsPace)
        for (name, f) in [("teams", teams), ("binding", binding), ("spent", spent), ("personal", personal)] {
            let entry = FaceEntry(date: now, faces: [f], key: f.account.key)
            save("g-lock-circle-\(name)", LockFace(entry: entry, familyOverride: .accessoryCircular).foregroundStyle(.white),
                 width: 72 + 32, height: 72 + 32, background: wall)
            save("g-lock-rect-\(name)", LockFace(entry: entry, familyOverride: .accessoryRectangular).foregroundStyle(.white),
                 width: 172 + 32, height: 76 + 32, background: wall)
            save("g-lock-inline-\(name)", LockFace(entry: entry, familyOverride: .accessoryInline).foregroundStyle(.white),
                 width: 310 + 32, height: 52, background: wall)
        }
        Display.setMode(.used)
        // The compact Island's leading ring (30 pt, "34" at 11 pt).
        if let five = CooldownActivityAttributes.Row(teams).five {
            save("g-island-compact-ring", FaceRing(five: five, accent: teams.account.accent(.dark), figure: .digits, figureSize: 11,
                                                   surface: .black).frame(width: 30, height: 30),
                 width: 30 + 32, height: 30 + 32, background: .black)
        }
    }

    @Test("Live Activity panel: Duet with Weekly, one account, stale and sign-in, ended")
    func liveActivity() {
        let rows = states.map { CooldownActivityAttributes.Row($0.1) }
        let bg = Color(hex: 0x1C1C1E)
        save("la-duet", LockPanel(state: .init(rows: [rows[5], rows[0]], ended: nil)), width: 370, height: 160, background: bg)
        save("la-above-max", LockPanel(state: .init(rows: [rows[1], rows[2]], ended: nil)), width: 370, height: 160, background: bg)
        save("la-stale-signin", LockPanel(state: .init(rows: [rows[3], rows[4]], ended: nil)), width: 370, height: 160, background: bg)
        save("la-single", LockPanel(state: .init(rows: [rows[0]], ended: nil)), width: 370, height: 160, background: bg)
        save("la-ended", LockPanel(state: .init(rows: [], ended: .init(label: "Teams", provider: "claude", peak: 87))),
             width: 370, height: 110, background: bg)
    }

    @Test("Live Activity: stale at the first Weekly or 5h reset; the ended frame's peak is the 5h's")
    func liveActivityStaleAndPeak() {
        let now = Date(timeIntervalSince1970: 1_793_620_200)
        var row = CooldownActivityAttributes.Row(states[0].1)
        row.weeklyLeads = true
        row.pct = 52
        row.weeklyPct = 93
        row.reset = now.addingTimeInterval(4 * 86_400)
        row.fiveReset = now.addingTimeInterval(50 * 60)
        #expect(CooldownActivityAttributes.staleDate([row], now: now) == now.addingTimeInterval(50 * 60))
        row.fiveReset = now.addingTimeInterval(-60)
        #expect(CooldownActivityAttributes.staleDate([row], now: now) == now.addingTimeInterval(4 * 86_400))
        // The frame is drawn after the 5h has reset: the peak is still the 93 it reached.
        #expect(CooldownActivityAttributes.endedPeak(row) == 93)
        row.weeklyPct = nil
        #expect(CooldownActivityAttributes.endedPeak(row) == 52)
    }
}
