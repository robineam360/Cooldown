import CooldownCore
import SwiftUI
import Testing
import WidgetKit

@testable import Cooldown

/// RUNBOOK-v2.0 Step 6: CCRM-96 (Weekly First), CCRM-97 (Pace Number), CCRM-98 (Summary Tab), CCRM-99 (Model Limits
/// Toggle) and CCRM-100 (Vs Pace Display) on iOS — the wording and the led faces, the v1.9 downgrade, and every new
/// state rendered (to SHOT_DIR when set) beside design/2026-11-02-ios-weekly-first.html rev B and the Summary rev B.
/// Serialized: the display-mode cases write the shared App Group defaults.
@MainActor
@Suite("v2.0 surfaces", .serialized)
struct V20SurfaceTests {
    let r = SurfaceRenderTests()
    var now: Date { r.now }

    /// The wireframe's moment: Teams (5h 34% at 53.3% gone; Weekly 52% at 38.9% gone).
    func teams(_ pct: Double = 34, minutes: Double = 140, runOut: Double? = nil, weekly: Double = 52, weeklyPace: Double = 38.9,
               headline: Headline = .weekly, binding: Bool? = nil) -> Face {
        r.face("Teams", .claude, pct: pct, pace: 100 - minutes / 3, minutes: minutes, runOut: runOut,
               weeklyPct: weekly, weeklyPace: weeklyPace, headline: headline, binding: binding)
    }
    var personal: Face { r.face("Personal", .chatgpt, pct: nil, pace: nil, weeklyPct: 50, weeklyPace: 67, weeklyOnly: true) }

    func withMode(_ mode: PaceText.Mode, _ body: () -> Void) {
        let d = Prefs.defaults
        let before = (d.object(forKey: Display.Key.usageLeft), d.object(forKey: Display.Key.usageVsPace))
        Display.setMode(mode)
        body()
        d.set(before.0, forKey: Display.Key.usageLeft)
        d.set(before.1, forKey: Display.Key.usageVsPace)
    }

    // MARK: - The led face

    @Test("Weekly leads by default; the 5h rides as the companion; a spent week drops it")
    func lead() {
        let f = teams().lead(for: .notification)
        #expect(f.leadsWeekly && f.pct == 52 && f.companionPct == 34 && f.companionLabel == "5h" && f.showsCompanion)
        #expect(!f.companionLoud)
        #expect(teams(headline: .fiveHour).lead(for: .notification).pct == 34)
        // Auto takes over while the 5h binds (core §6.2); Weekly stays Weekly, with the companion loud.
        #expect(teams(93, minutes: 50, headline: .auto).lead(for: .notification).leadsWeekly == false)
        let loud = teams(93, minutes: 50).lead(for: .notification)
        #expect(loud.leadsWeekly && loud.companionLoud)
        let spent = teams(weekly: 100).lead(.weekly)
        #expect(spent.leadSpent && !spent.showsCompanion)
        // A weekly-only plan is unchanged: Weekly alone.
        let wk = personal.lead(for: .notification)
        #expect(wk.leadsWeekly && wk.companionPct == nil)
    }

    @Test("Face.build reads the Headline and evaluates sessionBinding from the reading")
    func build() throws {
        let key = "v20-\(UUID().uuidString.prefix(6))"
        defer { Prefs.defaults.removeObject(forKey: "headline.\(key)") }
        Prefs.setHeadline(key, .auto)
        let nowMs = now.ms
        let iso = ISO8601DateFormatter()
        let body = #"{"five_hour":{"utilization":93,"resets_at":"\#(iso.string(from: now.addingTimeInterval(3000)))"},"seven_day":{"utilization":52,"resets_at":"\#(iso.string(from: now.addingTimeInterval(4 * 86_400)))"}}"#
        let snap = Snapshot(profileKey: key, provider: .claude, body: body, fetchedAtMs: nowMs, lastAttemptAtMs: nowMs,
                            lastKind: "success", lastMessage: "", plan: "Max")
        let f = Face.build(account: Account(key: key, label: "T", provider: .claude), snapshot: snap, state: AccountState(),
                           history: [], nowMs: nowMs)
        #expect(f.headline == .auto && f.sessionBinding && f.hasSessionWindow)
        #expect(!f.lead(for: .notification).leadsWeekly)
        #expect(f.lead(for: .newWidget).leadsWeekly)
    }

    // MARK: - Wording

    @Test("weekly cards speak in band words; the 5h card in its projection")
    func wording() throws {
        let f = teams()
        let w = try #require(f.weekly)
        #expect(w.paceLine.hasPrefix("Slow down · ≈"))
        #expect(w.paceLineAlarm)
        #expect(w.paceLineFull.contains(" until "))
        #expect(CardContent.compactLine(w).contains(" · resets "))
        #expect(w.caption == "39% pace mark · 13 over")
        let five = try #require(f.session)
        #expect(five.paceLine.isEmpty)
        #expect(CardContent.compactLine(five).hasPrefix("Resets in "))
        #expect(five.caption?.hasSuffix("under") == true)
        let spent = try #require(teams(weekly: 100).weekly)
        #expect(spent.paceLine.hasPrefix("Out until ") && spent.caption == nil && spent.paceLineAlarm)
        #expect(CardContent.compactLine(spent).hasPrefix("Out until "))
        withMode(.vsPace) {
            #expect(w.caption == "52% used · mark 39%")
            #expect(Display.number(52, pace: 38.9) == "13%")  // |38.9 − 52| toward zero (core §6.3, 2026-11-06), red: unsigned since rev D §7b
            #expect(Display.number(100, pace: 89.8) == "100%")
            #expect(Display.usage(34) == "34%")
        }
        withMode(.left) {
            #expect(w.caption == "61% pace mark · 13 over")
            #expect(Display.usage(34) == "66%")
        }
    }

    // MARK: - Downgrade (RUNBOOK-v2.0 Step 3's rule, iOS half)

    /// v1.9's accessors, verbatim: `Display.usageLeft` was `defaults.bool(forKey: "display.usageLeft")`, and its
    /// CardLayout decoded order/hidden/more only.
    struct V19Layout: Decodable { var order: [String]; var hidden: [String]; var more: [String] }

    @Test("v2.0-written keys read by v1.9's accessors give v1.9 behaviour")
    func downgrade() throws {
        let d = Prefs.defaults
        withMode(.vsPace) {
            #expect(d.bool(forKey: "display.usageLeft") == false, "Vs pace on → v1.9 sees Used")
            #expect(Display.mode == .vsPace)
        }
        withMode(.left) { #expect(d.bool(forKey: "display.usageLeft")) }
        // A reordered v2.0 layout still decodes in v1.9, in the user's order; an untouched one stays standard (v1.9's
        // own default), because v2.0 never rewrites the stored order.
        var moved = CardLayout.standard.moving(fromOffsets: [1], toOffset: 0)
        moved.hidden = [.credits]
        let v19 = try JSONDecoder().decode(V19Layout.self, from: JSONEncoder().encode(moved))
        #expect(v19.order == ["weekly", "session", "credits"] && v19.hidden == ["credits"])
        // Every other v2.0 key is new (headline.*, modelLimits.*, openOnSummary, lastAccount, weeklyFirstNotice,
        // display.usageVsPace) and v1.9 never reads it; the sidebar's "headline" tag reads as v1.9's 5h.
    }

    // MARK: - Judge findings F1, F3, F5

    @Test("F1: a Number set to Weekly on a weekly-only account shows the weekly reading, not —")
    func numberWeeklyOnly() {
        let wk = personal
        let face = wk.leading
        #expect(face.leadsWeekly && face.pct == 50 && face.weeklyPct == nil)
        let entry = FaceEntry(date: now, faces: [wk], key: wk.account.key, window: .weekly)
        // What NumberFace reads for .weekly on this face.
        let weekly = entry.window == .weekly && !face.leadsWeekly
        #expect((weekly ? face.weeklyPct : face.pct) == 50)
        r.save("v20-number-weeklyonly-weekly", NumberFace(entry: entry), width: 170, height: 170)
        r.save("v20-number-weekly", NumberFace(entry: FaceEntry(date: now, faces: [teams()], key: nil, window: .weekly)), width: 170, height: 170)
    }

    @Test("F3: Vs pace in v2.0, Left chosen in v1.9, re-upgrade reads Left and clears the stale flag")
    func modeRoundTrip() {
        let d = Prefs.defaults
        withMode(.vsPace) {
            #expect(Display.mode == .vsPace)
            d.set(true, forKey: "display.usageLeft")   // v1.9's Left: it writes only this key
            #expect(Display.mode == .left)
            Display.repairMode()
            #expect(!d.bool(forKey: Display.Key.usageVsPace) && Display.mode == .left)
            Display.setMode(.vsPace)
            #expect(Display.mode == .vsPace && !d.bool(forKey: "display.usageLeft"))
        }
        #expect(Display.mode(left: true, vsPace: true) == .left)
        #expect(Display.mode(left: false, vsPace: true) == .vsPace)
        #expect(Display.mode(left: false, vsPace: false) == .used)
    }

    @Test("F5: the All tab selection falls back to an account once fewer than two remain")
    func selectionAfterRemove() {
        let a = Account(key: "a", label: "A", provider: .claude), b = Account(key: "b", label: "B", provider: .chatgpt)
        #expect(AppModel.validSelection(AppModel.summaryKey, accounts: [a, b]) == AppModel.summaryKey)
        #expect(AppModel.validSelection(AppModel.summaryKey, accounts: [a]) == "a")
        #expect(AppModel.validSelection(AppModel.summaryKey, accounts: []) == nil)
        #expect(AppModel.validSelection("b", accounts: [a]) == "a")
        #expect(AppModel.validSelection("a", accounts: [a, b]) == "a")
    }

    // MARK: - Card order, launch

    @Test("card order follows the Headline unless the user reordered; hide and fold keep it the Headline's")
    func order() {
        #expect(CardLayout.standard.ordered(by: .weekly).order == [.weekly, .session, .credits])
        #expect(CardLayout.standard.ordered(by: .auto).order == [.weekly, .session, .credits])
        #expect(CardLayout.standard.ordered(by: .fiveHour).order == [.session, .weekly, .credits])
        let moved = CardLayout.standard.ordered(by: .weekly).moving(fromOffsets: [1], toOffset: 0)
        #expect(moved.userOrdered && moved.ordered(by: .weekly).order == [.session, .weekly, .credits])
        // A v1.9 layout with its own order counts as reordered; one at the standard order does not.
        #expect(CardLayout(order: [.credits, .session, .weekly], hidden: [], more: []).userOrdered)
        #expect(!CardLayout.standard.folding(.credits).userOrdered)
    }

    @Test("launch opens the All tab with 2+ accounts and the toggle on, else the last account looked at")
    func launch() {
        let a = [Account(key: "a", label: "A", provider: .claude), Account(key: "b", label: "B", provider: .chatgpt)]
        #expect(AppModel.launchKey(accounts: a, openOnSummary: true, last: "b") == AppModel.summaryKey)
        #expect(AppModel.launchKey(accounts: a, openOnSummary: false, last: "b") == "b")
        #expect(AppModel.launchKey(accounts: a, openOnSummary: false, last: "gone") == "a")
        #expect(AppModel.launchKey(accounts: [a[0]], openOnSummary: true, last: nil) == "a")
    }

    // MARK: - Summary

    var four: [Face] {
        [teams(),
         r.face("Product", .claude, pct: 93, pace: 83, minutes: 50, weeklyPct: 30, weeklyPace: 72, binding: true),
         personal,
         r.face("Side", .claude, pct: 0, pace: 10, weeklyPct: 100, weeklyPace: 89.8)]
    }

    /// The C1 cases read the ranking ("Most room first"); "Your order" is the Summary's default since 2026-11-02.
    func entries(_ faces: [Face], order: SummaryOrder = .room) -> [SummaryEntry] {
        SummaryEntry.build(accounts: faces.map(\.account), faces: Dictionary(uniqueKeysWithValues: faces.map { ($0.account.key, $0) }),
                           plans: [:], histories: [:], nowMs: now.ms, order: order)
    }

    @Test("the Summary ranks the wireframe's four accounts as C1")
    func summaryOrder() {
        let e = entries(four)
        #expect(e.map(\.account.label) == ["Personal", "Teams", "Product", "Side"])
        // Your order keeps the accounts as listed, each row still in its group.
        let yours = entries(four, order: .yours)
        #expect(yours.map(\.account.label) == four.map(\.account.label))
        #expect(Set(yours.map(\.group)) == Set(e.map(\.group)))
        #expect(e.map(\.group) == [.eligible, .eligible, .held, .spent])
        // CCRM-103 (Hybrid Notification): the ring replaced "5h 93%" and "no 5h"; only the held reason's time stays.
        #expect(SummaryWords.companion(e[2])?.hasPrefix("back ") == true)
        #expect(SummaryWords.companion(e[0]) == nil && SummaryWords.companion(e[1]) == nil)
        #expect(SummaryWords.line(e[3], nowMs: now.ms).hasPrefix("Out until "))
        #expect(SummaryWords.line(e[0], nowMs: now.ms).hasPrefix("Room to spare · ≈"))
        // §2b: the Weekly figure's tap readout — under (Personal 50 vs 67), over (Teams 52 vs 38.9), none when spent.
        #expect(SummaryWords.readout(e[0], nowMs: now.ms)?.hasSuffix(" behind pace") == true)
        #expect(SummaryWords.readout(e[1], nowMs: now.ms)?.hasPrefix("Pace catches up ") == true)
        #expect(SummaryWords.readout(e[3], nowMs: now.ms) == nil)
    }

    // MARK: - CCRM-103 (Hybrid Notification): the account tile, rev D §2, §6, §7

    @Test("tile readouts: each figure's own words, none for an unclocked or spent window")
    func tileReadouts() throws {
        let f = teams()
        let w = try #require(f.weekly), five = try #require(f.session)
        #expect(w.readout()?.hasPrefix("Pace catches up ") == true)   // 52% against a 38.9% mark: over
        #expect(five.readout()?.hasSuffix(" behind pace") == true)    // 34% against 53.3%: under
        #expect(try #require(teams(weekly: 100).weekly).readout() == nil)
        #expect(try #require(r.face("Idle", .claude, pct: 0, pace: nil, notStarted: true).session).readout() == nil)
    }

    @Test("tile renders: folded and open on each chip, no 5h, spent week, no reading, binding; Used, Left, Vs pace; end marks")
    func tiles() throws {
        let f = teams()
        let accent = f.account.accent(.dark)
        let w = try #require(f.weekly), five = try #require(f.session)
        let fable = try #require(CardContent.window(title: "Fable", window: UsageWindow(percent: 30, resetsAtMs: f.weeklyReset?.ms, serverSeverity: nil),
                                                    kind: HistoryStats.weeklyKind, history: [], nowMs: now.ms))
        func shoot(_ tag: String) {
            r.save("v21-tile-folded-\(tag)", AccountTile(weekly: w, cap: fable, five: five, accent: accent, open: .constant(false)),
                   width: 393, height: 230, background: .black)
            r.save("v21-tile-open-all-\(tag)", AccountTile(weekly: w, cap: fable, five: five, accent: accent, open: .constant(true)),
                   width: 393, height: 520, background: .black)
            r.save("v21-tile-open-5h-\(tag)", AccountTile(weekly: w, cap: fable, five: five, accent: accent, open: .constant(true), defaultChip: .five),
                   width: 393, height: 520, background: .black)
        }
        shoot("used")
        withMode(.left) { shoot("left") }
        withMode(.vsPace) { shoot("vspace") }
        let pw = try #require(personal.weekly)
        r.save("v21-tile-no5h", AccountTile(weekly: pw, five: nil, accent: personal.account.accent(.dark), open: .constant(false)),
               width: 393, height: 200, background: .black)
        let spent = teams(weekly: 100)
        r.save("v21-tile-spent", AccountTile(weekly: try #require(spent.weekly), five: try #require(spent.session), accent: accent,
                                             open: .constant(false),
                                             weekGate: CardContent.weekGateNote(weeklyResetMs: spent.weeklyReset?.ms, nowMs: now.ms)),
               width: 393, height: 220, background: .black)
        let binding = teams(93, minutes: 50, runOut: 35)
        r.save("v21-tile-binding", AccountTile(weekly: try #require(binding.weekly), cap: fable, five: try #require(binding.session),
                                               accent: accent, open: .constant(false)),
               width: 393, height: 230, background: .black)
        let unread = try #require(CardContent.window(title: "All", window: UsageWindow(percent: nil, resetsAtMs: nil, serverSeverity: nil),
                                                     kind: HistoryStats.weeklyKind, history: [], nowMs: now.ms))
        r.save("v21-tile-noreading", AccountTile(weekly: unread, five: nil, accent: accent, open: .constant(false)),
               width: 393, height: 200, background: .black)
        r.save("v21-tile-light", AccountTile(weekly: w, cap: fable, five: five, accent: f.account.accent(.light), open: .constant(false)),
               width: 393, height: 230, scheme: .light)
        let d = Prefs.defaults, before = d.object(forKey: Display.Key.usageEndMark)
        defer { d.set(before, forKey: Display.Key.usageEndMark) }
        for mark in UsageEndMark.allCases {
            Display.setUsageEndMark(mark)
            r.save("v21-tile-end-\(mark.rawValue)", AccountTile(weekly: w, cap: fable, five: five, accent: accent, open: .constant(false)),
                   width: 393, height: 230, background: .black)
        }
        r.save("v21-settings-endmark", EndMarkPreviews(), width: 393, height: 90)
    }

    // MARK: - Renders: every new state

    @Test("rev B widgets: Weekly first, companions, binding, spent, last day, Auto")
    func widgets() {
        let states: [(String, Face)] = [
            ("weekly", teams()), ("binding", teams(93, minutes: 50, runOut: 35)), ("auto-binding", teams(93, minutes: 50, runOut: 35, headline: .auto)),
            ("spent", teams(weekly: 100)), ("lastday", teams(12, weekly: 60, weeklyPace: 96.4)), ("fivehour", teams(headline: .fiveHour)),
            ("personal", personal)]
        for (name, f) in states {
            let entry = FaceEntry(date: now, faces: [f], key: f.account.key)
            r.save("v20-bars-\(name)", BarsFace(entry: entry), width: 160, height: 160)
            r.save("v20-strip-\(name)", StripFace(entry: entry), width: 364, height: 170)
            r.save("v20-overview-\(name)", OverviewFace(entry: FaceEntry(date: now, faces: [f], key: nil), familyOverride: .systemLarge),
                   width: 346, height: 346)
            let wall = Color(hex: 0x6E5F95)
            r.save("v20-lock-rect-\(name)", LockFace(entry: entry, familyOverride: .accessoryRectangular).foregroundStyle(.white),
                   width: 204, height: 108, background: wall)
            r.save("v20-lock-circle-\(name)", LockFace(entry: entry, familyOverride: .accessoryCircular).foregroundStyle(.white),
                   width: 104, height: 104, background: wall)
            r.save("v20-lock-inline-\(name)", LockFace(entry: entry, familyOverride: .accessoryInline).foregroundStyle(.white),
                   width: 300, height: 40, background: wall)
        }
        r.save("v20-bars-se", BarsFace(entry: FaceEntry(date: now, faces: [teams()], key: nil)), width: 141, height: 141)
        r.save("v20-bars-light", BarsFace(entry: FaceEntry(date: now, faces: [teams()], key: nil)), width: 160, height: 160, scheme: .light)
        let two = FaceEntry(date: now, faces: [personal, teams()], key: nil)
        r.save("v20-overview-t3", OverviewFace(entry: two, familyOverride: .systemLarge), width: 346, height: 346)
        r.save("v20-overview-t3-narrow", OverviewFace(entry: two, familyOverride: .systemLarge), width: 290, height: 346)
        r.save("v20-overview-t3-light", OverviewFace(entry: two, familyOverride: .systemLarge), width: 346, height: 346, scheme: .light)
        r.save("v20-overview-xl-1", OverviewFace(entry: FaceEntry(date: now, faces: [teams()], key: nil), familyOverride: .systemExtraLarge),
               width: 724, height: 346)
        r.save("v20-overview-medium-3", OverviewFace(entry: FaceEntry(date: now, faces: [personal, teams(), four[1]], key: nil),
                                                    familyOverride: .systemMedium), width: 346, height: 157)
    }

    @Test("Live Activity: Weekly first, the 5h thin line, binding, Auto takeover, spent")
    func liveActivity() {
        let bg = Color(hex: 0x1C1C1E)
        func row(_ f: Face) -> CooldownActivityAttributes.Row { .init(f.lead(for: .notification)) }
        r.save("v20-la-duet", LockPanel(state: .init(rows: [row(teams()), row(personal)], ended: nil)), width: 370, height: 160, background: bg)
        r.save("v20-la-binding", LockPanel(state: .init(rows: [row(teams(93, minutes: 50, runOut: 35)), row(personal)], ended: nil)),
               width: 370, height: 160, background: bg)
        r.save("v20-la-auto", LockPanel(state: .init(rows: [row(teams(93, minutes: 50, runOut: 35, headline: .auto)), row(personal)], ended: nil)),
               width: 370, height: 160, background: bg)
        r.save("v20-la-spent", LockPanel(state: .init(rows: [row(teams(weekly: 100)), row(personal)], ended: nil)),
               width: 370, height: 160, background: bg)
        r.save("v20-la-single", LockPanel(state: .init(rows: [row(teams())], ended: nil)), width: 370, height: 160, background: bg)
        let island = row(teams())
        #expect(island.leadsWeekly && island.weeklyPct == 34 && island.companionLabel == "5h")
        r.save("v20-island-weekly", IslandLine(row: island).environment(\.palette, .dark).foregroundStyle(.white), width: 360, height: 40, background: .black)
        r.save("v20-island-auto", IslandLine(row: row(teams(93, minutes: 50, runOut: 35, headline: .auto))).foregroundStyle(.white),
               width: 360, height: 40, background: .black)
    }

    @Test("app cards: band words, the V1 caption with dashed room, the 5h projection; Vs pace and Left")
    func cards() throws {
        let f = teams()
        let w = try #require(f.weekly), five = try #require(f.session)
        func shoot(_ tag: String) {
            r.save("v20-card-weekly-\(tag)", WeeklyCard(all: w, cap: nil, accent: f.account.accent(.dark), expanded: .constant(true)),
                   width: 390, height: 420, background: .black)
            r.save("v20-card-weekly-folded-\(tag)", WeeklyCard(all: w, cap: nil, accent: f.account.accent(.dark), expanded: .constant(false)),
                   width: 390, height: 170, background: .black)
            r.save("v20-card-5h-\(tag)", WindowCard(content: five, accent: f.account.accent(.dark), expanded: .constant(true), chartHeight: 120),
                   width: 390, height: 360, background: .black)
            r.save("v20-card-5h-folded-\(tag)", WindowCard(content: five, accent: f.account.accent(.dark), expanded: .constant(false)),
                   width: 390, height: 150, background: .black)
        }
        shoot("used")
        withMode(.left) { shoot("left") }
        withMode(.vsPace) { shoot("vspace") }
        let fable = try #require(CardContent.window(title: "Fable", window: UsageWindow(percent: 30, resetsAtMs: f.weeklyReset?.ms, serverSeverity: nil),
                                                    kind: HistoryStats.weeklyKind, history: [], nowMs: now.ms))
        r.save("v20-card-weekly-halves", WeeklyCard(all: w, cap: fable, accent: f.account.accent(.dark), expanded: .constant(true)),
               width: 390, height: 420, background: .black)
        let spent = try #require(teams(weekly: 100).weekly)
        r.save("v20-card-weekly-spent", WeeklyCard(all: spent, cap: nil, accent: f.account.accent(.dark), expanded: .constant(true)),
               width: 390, height: 420, background: .black)
        let binding = try #require(teams(93, minutes: 50, runOut: 35).session)
        r.save("v20-card-5h-folded-binding", WindowCard(content: binding, accent: f.account.accent(.dark), expanded: .constant(false)),
               width: 390, height: 150, background: .black)
    }

    @Test("Summary: the ranked list (2, 4, 7 accounts) and the Pace map, dark and light")
    func summary() {
        let seven = four + [r.face("Lab", .claude, pct: 5, pace: 10, weeklyPct: 20, weeklyPace: 25),
                            r.face("Design", .claude, pct: 12, pace: 40, weeklyPct: 70, weeklyPace: 55),
                            Face(account: Account(key: "ops", label: "Ops", provider: .claude), pct: nil, pace: nil, reset: nil,
                                 runOut: nil, weeklyPct: nil, weeklyPace: nil, weeklyReset: nil, fetchedAt: nil, status: .noData)]
        for (tag, faces) in [("2", [teams(), personal]), ("4", four), ("7", seven)] {
            let e = entries(faces)
            r.save("v20-summary-list-\(tag)", SummaryList(entries: e, nowMs: now.ms), width: 393, height: faces.count >= 6 ? 380 : 140 + 110 * CGFloat(faces.count),
                   background: .black)
            r.save("v20-summary-map-\(tag)", PaceMapCard(entries: e), width: 393, height: 300, background: .black)
        }
        r.save("v20-summary-list-4-light", SummaryList(entries: entries(four), nowMs: now.ms), width: 393, height: 580, scheme: .light)
        r.save("v20-summary-map-4-wide", PaceMapCard(entries: entries(four), height: 340), width: 420, height: 410, background: .black)
        withMode(.vsPace) {
            r.save("v20-summary-list-4-vspace", SummaryList(entries: entries(four), nowMs: now.ms), width: 393, height: 580, background: .black)
        }
    }
}
