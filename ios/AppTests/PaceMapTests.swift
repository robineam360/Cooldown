import CooldownCore
import SwiftUI
import Testing
import UIKit

@testable import Cooldown

/// CCRM-109 (Quiet Accounts) and CCRM-111 (Map Layers) on iOS, wireframe design/2026-11-10-v20-feedback-wireframes.html
/// rev B §5 and §7: the scene the map draws (names placed by core `MapLabels` with the app's own measured text), the tap
/// targets, the callout's placement, the quiet accounts, and every drawn state rendered to SHOT_DIR when set. The
/// rules themselves are pinned by CooldownCore's `PaceMapV20Tests` on the shared fixtures.
@MainActor
@Suite("Pace map: layers, callout, quiet accounts", .serialized)
struct PaceMapTests {
    let r = SurfaceRenderTests()
    var now: Date { r.now }

    struct Spec {
        let name: String
        let provider: Provider
        let used: Double?
        let gone: Double
        /// The projected % at the reset the history is drawn to give (nil = no history, so no estimate).
        var proj: Double? = nil
    }

    // The wireframe's accounts (rev B `AC`).
    static let teams = Spec(name: "Teams", provider: .claude, used: 52, gone: 39, proj: 74)
    static let personal = Spec(name: "Personal", provider: .chatgpt, used: 50, gone: 68, proj: 71)
    static let gpt = Spec(name: "ChatGPT", provider: .chatgpt, used: 6, gone: 30, proj: 20)
    static let gemini = Spec(name: "Gemini", provider: .antigravity, used: 91, gone: 83, proj: 112)
    static let lab = Spec(name: "Lab", provider: .claude, used: 61, gone: 61, proj: 96)
    static let atlas = Spec(name: "Atlas", provider: .claude, used: 100, gone: 70)
    static let six = [teams, personal, gpt, gemini, lab, atlas]
    static let ten = six + [Spec(name: "Ops", provider: .claude, used: 33, gone: 36, proj: 80),
                            Spec(name: "Research", provider: .chatgpt, used: 28, gone: 33, proj: 60),
                            Spec(name: "Client", provider: .antigravity, used: 74, gone: 90, proj: 82),
                            Spec(name: "Work", provider: .claude, used: 30, gone: 44, proj: 66)]
    static let notes = Spec(name: "Notes", provider: .chatgpt, used: 0.4, gone: 22)
    static let sandbox = Spec(name: "Sandbox", provider: .antigravity, used: 0, gone: 9)
    static let product = Spec(name: "Product", provider: .claude, used: nil, gone: 0)

    /// Entries in the given order; each projected account gets 36 h of samples on a straight line that ends at its
    /// reading and reaches `proj` at the reset, so `Projection.estimate` returns that projection.
    func entries(_ specs: [Spec]) -> [SummaryEntry] {
        var faces: [Face] = []
        var histories: [String: [HistoryPoint]] = [:]
        for s in specs {
            var f = r.face(s.name, s.provider, pct: 20, pace: 50, weeklyPct: s.used ?? 0, weeklyPace: s.gone)
            if s.used == nil {
                f = Face(account: f.account, pct: nil, pace: nil, reset: nil, runOut: nil, weeklyPct: nil, weeklyPace: nil,
                         weeklyReset: nil, fetchedAt: nil, status: .noData)
            }
            faces.append(f)
            guard let used = s.used, let proj = s.proj, let reset = f.weeklyReset?.ms else { continue }
            let left = Double(reset - now.ms) / 3_600_000
            let rate = (proj - used) / left
            histories[f.account.key] = stride(from: 36.0, through: 0, by: -2).map { h in
                HistoryPoint(atMs: now.ms - Int64(h * 3_600_000), sessionPct: nil, sessionResetAtMs: 0,
                             weeklyPct: max(0, used - rate * h), weeklyResetAtMs: reset)
            }
        }
        return SummaryEntry.build(accounts: faces.map(\.account),
                                  faces: Dictionary(uniqueKeysWithValues: faces.map { ($0.account.key, $0) }),
                                  plans: [:], histories: histories, nowMs: now.ms, order: .yours)
    }

    func plotted(_ e: [SummaryEntry]) -> [SummaryEntry] {
        e.filter { PaceMapRules.isPlotted(usedPct: $0.usedPct, clocked: $0.clocked) }
    }

    // MARK: The scene

    @Test("names: inside the card, off the words, the dots, the rings and each other; leaders at most 48 pt",
          arguments: [CGSize(width: 339, height: 230), CGSize(width: 288, height: 210), CGSize(width: 340, height: 340)])
    func scene(size: CGSize) {
        for specs in [Self.six, Self.ten] {
            for layers in [MapLayers(), MapLayers(figures: true, trails: true, projection: false),
                           MapLayers(figures: true, trails: true, projection: true), MapLayers(figures: false, trails: false, projection: true)] {
                for mode in PaceText.Mode.allCases {
                    let s = PaceMapScene.make(entries: plotted(entries(specs)), size: size, layers: layers, mode: mode)
                    let tag = "\(specs.count) accounts \(size) \(layers) \(mode)"
                    #expect(s.items.count == specs.count, "\(tag): every reading plotted")
                    let boxes = s.items.compactMap { i in s.box(i).map { (i.key, $0) } }
                    for (key, b) in boxes {
                        #expect(b.minX >= -0.01 && b.maxX <= size.width + 0.01 && b.minY >= -0.01 && b.maxY <= size.height + 0.01,
                                "\(tag): \(key) inside the canvas \(b)")
                        for w in s.words { #expect(!b.intersects(w), "\(tag): \(key) off a word") }
                        for i in s.items {
                            let dot = CGRect(x: i.point.x - 7, y: i.point.y - 7, width: 14, height: 14)
                            #expect(!b.intersects(dot), "\(tag): \(key) off \(i.key)'s dot")
                        }
                        for ring in s.rings { #expect(!b.intersects(ring), "\(tag): \(key) off a ring") }
                        for (other, o) in boxes where other != key {
                            #expect(!b.insetBy(dx: -1.9, dy: -1.9).intersects(o), "\(tag): \(key) vs \(other)")
                        }
                    }
                    for i in s.items {
                        if let l = i.label?.leader { #expect(l.distance <= 48, "\(tag): \(i.key) leader \(l.distance)") }
                        if let fig = i.figure { #expect(layers.figures && !fig.isEmpty) } else { #expect(!layers.figures) }
                        #expect((i.tailEnd != nil) == (layers.projection && !Pace.isSpent(i.used)), "\(tag): \(i.key) tail")
                    }
                }
            }
        }
    }

    @Test("projection: Gemini runs out (on the 100% line), Atlas is spent (none), the rest end on the right edge")
    func projection() {
        let s = PaceMapScene.make(entries: plotted(entries(Self.six)), size: CGSize(width: 339, height: 230),
                                  layers: MapLayers(figures: false, trails: true, projection: true), mode: .used)
        for i in s.items {
            switch i.name {
            case "Atlas": #expect(i.projection == nil)
            case "Gemini":
                #expect(i.projection?.runsOut == true && abs((i.tailEnd?.y ?? 0) - s.P(0, 100).y) < 0.01)
                #expect((i.tailEnd?.x ?? 0) < s.plot.maxX)
            default:
                #expect(i.projection?.runsOut == false && abs((i.tailEnd?.x ?? 0) - s.plot.maxX) < 0.01, "\(i.name)")
            }
        }
        let teams = s.items.first { $0.name == "Teams" }
        #expect(abs((teams?.projection?.usedPct ?? 0) - 74) < 0.5, "the estimate the history was drawn for")
    }

    @Test("quiet accounts leave the map; the foot line counts them and the unread")
    func quiet() {
        let e = entries([Self.teams, Self.personal, Self.notes, Self.sandbox, Self.product])
        #expect(plotted(e).map(\.account.label) == ["Teams", "Personal"])
        #expect(PaceMapRules.foot(e.map { .init(usedPct: $0.usedPct, clocked: $0.clocked) }).count == "2 at 0% · 1 no reading")
        let all = entries([Self.notes, Self.sandbox])
        let foot = PaceMapRules.foot(all.map { .init(usedPct: $0.usedPct, clocked: $0.clocked) })
        #expect(plotted(all).isEmpty && foot.count == "2 at 0%" && foot.empty?.title == "Nothing to plot yet")
    }

    @Test("a tap: the nearest dot within its 48 pt target, or a name; the empty plot clears")
    func taps() {
        let s = PaceMapScene.make(entries: plotted(entries(Self.six)), size: CGSize(width: 339, height: 230), layers: MapLayers(), mode: .used)
        for i in s.items {
            #expect(s.hit(i.point) == i.key, "\(i.name) on its dot")
            if let b = s.box(i), s.items.allSatisfy({ hypot($0.point.x - b.midX, $0.point.y - b.midY) > 24 || $0.key == i.key }) {
                #expect(s.hit(CGPoint(x: b.midX, y: b.midY)) == i.key, "\(i.name) on its name")
            }
        }
        #expect(s.hit(CGPoint(x: s.plot.minX + 4, y: s.plot.minY + 4)) == nil, "the empty top-left corner")
        // Two dots both in reach (about 11 pt apart): the nearer wins, from either side (judge F3).
        let close = PaceMapScene.make(entries: plotted(entries([Self.teams, Spec(name: "Near", provider: .chatgpt, used: 55, gone: 42)])),
                                      size: CGSize(width: 339, height: 230), layers: MapLayers(), mode: .used)
        let a = close.items[0], b = close.items[1]
        #expect(hypot(a.point.x - b.point.x, a.point.y - b.point.y) < 24)
        func at(_ t: CGFloat) -> CGPoint { CGPoint(x: a.point.x + t * (b.point.x - a.point.x), y: a.point.y + t * (b.point.y - a.point.y)) }
        #expect(close.hit(at(0.3)) == a.key)
        #expect(close.hit(at(0.7)) == b.key)
    }

    @Test("tap: the empty plot clears, a new account pins, the pinned one opens; a vanished account drops the pin")
    func tapTransitions() {
        #expect(PaceMap.tap(pinned: nil, hit: nil) == (nil, nil))
        #expect(PaceMap.tap(pinned: "teams", hit: nil) == (nil, nil))
        #expect(PaceMap.tap(pinned: nil, hit: "teams") == ("teams", nil))
        #expect(PaceMap.tap(pinned: "lab", hit: "teams") == ("teams", nil))
        #expect(PaceMap.tap(pinned: "teams", hit: "teams") == ("teams", "teams"))
        #expect(PaceMap.keepPin("teams", keys: ["lab", "teams"]) == "teams")
        #expect(PaceMap.keepPin("teams", keys: ["lab"]) == nil)
        #expect(PaceMap.keepPin(nil, keys: ["lab"]) == nil)
    }

    @Test("a trail needs history: fewer than two samples draws none (as shipped)")
    func trailNeedsHistory() {
        #expect(PaceMap.trail([], gone: 70, used: 100) == nil)
        #expect(PaceMap.trail([CGPoint(x: 10, y: 5)], gone: 70, used: 100) == nil)
        let t = PaceMap.trail([CGPoint(x: 10, y: 5), CGPoint(x: 20, y: 9)], gone: 30, used: 120)
        #expect(t == [CGPoint(x: 0, y: 0), CGPoint(x: 10, y: 5), CGPoint(x: 20, y: 9), CGPoint(x: 30, y: 100)])
        // The render's spent Atlas has no history, so it gets a dot and no line.
        let atlas = entries([Self.atlas])[0]
        #expect(atlas.trail.count <= 1 && PaceMap.trail(atlas.trail, gone: 70, used: 100) == nil)
    }

    @Test("the callout opens above-right, flips left at the right edge and below at the top, clamped to the plot")
    func callout() {
        let plot = CGRect(x: 30, y: 6, width: 305, height: 206), size = CGSize(width: 150, height: 54)
        let mid = CalloutLayout.origin(dot: CGPoint(x: 100, y: 150), size: size, plot: plot)
        #expect(mid == CGPoint(x: 110, y: 150 - 54 - 8))
        let right = CalloutLayout.origin(dot: CGPoint(x: 300, y: 150), size: size, plot: plot)
        #expect(abs(right.x - 140) < 0.001, "flips left: 300 − 10 − 150")
        let top = CalloutLayout.origin(dot: CGPoint(x: 300, y: 20), size: size, plot: plot)
        #expect(top == CGPoint(x: 140, y: 30))
        let corner = CalloutLayout.origin(dot: CGPoint(x: 40, y: 205), size: CGSize(width: 320, height: 54), plot: plot)
        #expect(corner.x >= plot.minX && corner.y + 54 <= plot.maxY)
    }

    @Test("the trail's last 24 h starts where the line crosses a seventh before the dot")
    func lastDay() {
        let pts = [CGPoint(x: 0, y: 0), CGPoint(x: 20, y: 10), CGPoint(x: 40, y: 30)]
        let d = PaceMap.lastDay(pts, gone: 40)
        #expect(d.count == 2 && abs(d[0].x - (40 - 100.0 / 7)) < 1e-9 && d.last == pts.last)
        #expect(PaceMap.lastDay(pts, gone: 10).first == pts.first)
    }

    @Test("the layers are remembered; Figures off, Trails on, Projection off by default")
    func remembered() {
        let d = Prefs.standard
        let keys = [MapLayers.Key.figures, MapLayers.Key.trails, MapLayers.Key.projection]
        let before = keys.map { d.object(forKey: $0) }
        keys.forEach { d.removeObject(forKey: $0) }
        #expect(MapLayers.saved == MapLayers(figures: false, trails: true, projection: false))
        MapLayers.saved = MapLayers(figures: true, trails: false, projection: true)
        #expect(MapLayers.saved == MapLayers(figures: true, trails: false, projection: true))
        for (k, v) in zip(keys, before) { d.set(v, forKey: k) }
    }

    // MARK: Renders (SHOT_DIR)

    func card(_ e: [SummaryEntry], _ layers: MapLayers = MapLayers(), pinned: String? = nil, height: CGFloat = 230) -> PaceMapCard {
        PaceMapCard(entries: e, height: height, layers: layers, pinned: pinned.flatMap { n in e.first { $0.account.label == n }?.account.key })
    }

    func withMode(_ mode: PaceText.Mode, _ body: () -> Void) {
        let d = Prefs.defaults
        let before = (d.object(forKey: Display.Key.usageLeft), d.object(forKey: Display.Key.usageVsPace))
        Display.setMode(mode)
        body()
        d.set(before.0, forKey: Display.Key.usageLeft)
        d.set(before.1, forKey: Display.Key.usageVsPace)
    }

    @Test("render: every chip combination, the callout, quiet accounts, dense, narrow and the iPad square, dark and light")
    func renders() {
        let six = entries(Self.six), ten = entries(Self.ten)
        let fig = MapLayers(figures: true, trails: true, projection: false), proj = MapLayers(figures: false, trails: true, projection: true)
        for scheme in [ColorScheme.dark, .light] {
            let t = scheme == .dark ? "dark" : "light"
            func save(_ name: String, _ v: some View, width: CGFloat = 393, height: CGFloat = 330) {
                r.save("i2-map-\(name)-\(t)", v, width: width, height: height, scheme: scheme, background: scheme == .dark ? .black : nil)
            }
            save("defaults", card(six))
            withMode(.used) { save("figures-used", card(six, fig)) }
            withMode(.vsPace) { save("figures-vspace", card(six, fig)) }
            withMode(.left) { save("figures-left-trails-off", card(six, MapLayers(figures: true, trails: false, projection: false))) }
            save("projection", card(six, proj))
            withMode(.used) { save("all-three", card(six, MapLayers(figures: true, trails: true, projection: true))) }
            withMode(.used) {
                save("callout-teams", card(six, pinned: "Teams"))
                save("callout-gemini-top-right", card(six, proj, pinned: "Gemini"))
                save("callout-atlas-spent", card(six, pinned: "Atlas"))
            }
            save("quiet-two-hidden", card(entries([Self.teams, Self.personal, Self.lab, Self.gemini, Self.atlas, Self.gpt, Self.notes, Self.sandbox])))
            save("quiet-all-zero", card(entries([Self.notes, Self.sandbox, Spec(name: "x", provider: .claude, used: 0.2, gone: 40)])))
            save("quiet-one-account", card(entries([Self.personal])))
            save("quiet-unread-count", card(entries([Self.teams, Self.personal, Self.notes, Self.product])))
            withMode(.used) { save("dense-ten-figures", card(ten, fig)) }
            save("narrow-320", card(six, height: 210), width: 320, height: 330)
            save("narrow-300-wrap", card(six, height: 210), width: 300, height: 360)
            withMode(.used) {
                save("ipad-square", card(ten, MapLayers(figures: true, trails: true, projection: true), height: 340), width: 420, height: 440)
            }
        }
    }
}
