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
              runOut: Double? = nil, status: Face.Status = .fresh, fetchedMinutesAgo: Double = 3) -> Face {
        // A unique key per sample: rows and rings are identified by account key.
        Face(account: Account(key: "\(label)-\(Int(pct ?? -1))-\(status)", label: label, provider: provider), pct: pct, pace: pace,
             reset: now.addingTimeInterval(minutes * 60), runOut: runOut.map { now.addingTimeInterval($0 * 60) },
             weeklyPct: 18, weeklyPace: 43, weeklyReset: now.addingTimeInterval(4 * 86_400),
             fetchedAt: now.addingTimeInterval(-fetchedMinutesAgo * 60), status: status)
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
            save("ring-\(state)", RingFace(entry: entry, familyOverride: .systemSmall), width: 170, height: 170)
            save("strip-\(state)", StripFace(entry: entry), width: 364, height: 170)
        }
        let three = [states[0].1, face("Product", .claude, pct: 100, pace: 60), face("Personal", .chatgpt, pct: 12, pace: 30, status: .signIn)]
        save("ring3", RingFace(entry: FaceEntry(date: now, faces: three, key: nil), familyOverride: .systemMedium), width: 364, height: 170)
        save("overview", OverviewFace(entry: FaceEntry(date: now, faces: [states[0].1, face("Product", .claude, pct: 62, pace: 40, runOut: 130), states[5].1], key: nil)), width: 364, height: 382)
        save("number-light", NumberFace(entry: FaceEntry(date: now, faces: [states[5].1], key: nil)), width: 170, height: 170, scheme: .light)
        save("empty", NumberFace(entry: FaceEntry(date: now, faces: [], key: nil)), width: 170, height: 170)
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
