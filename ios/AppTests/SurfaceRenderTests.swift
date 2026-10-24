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
              weeklyPct: Double = 18, weeklyPace: Double = 43, weeklyOnly: Bool = false, notStarted: Bool = false) -> Face {
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

    @Test("lock-screen widgets")
    func lockWidgets() {
        let wall = Color(hex: 0x6E5F95)
        for (state, f) in states.prefix(5) {
            let entry = FaceEntry(date: now, faces: [f], key: f.account.key)
            save("lock-circle-\(state)", LockFace(entry: entry, familyOverride: .accessoryCircular).foregroundStyle(.white),
                 width: 104, height: 104, background: wall)
            save("lock-rect-\(state)", LockFace(entry: entry, familyOverride: .accessoryRectangular).foregroundStyle(.white),
                 width: 204, height: 108, background: wall)
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
}
