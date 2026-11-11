import CooldownCore
import SwiftUI
import Testing
import UIKit

@testable import Cooldown

/// CCRM-110 (Account Tags), CCRM-112 (Summary Snapshot), CCRM-115 (Row Tag Glyph) and the iOS follow-up to CCRM-113 (Sample
/// Port) on iOS, wireframe design/2026-11-10-v20-feedback-wireframes.html rev B §6, §8 and §9 with its 2026-11-11 addendum:
/// the additive tag key (every account Personal or Work, no None), the filter and its memory over the Summary's rows, the
/// snapshot's rows, and the sample's three accounts. The filter rules are pinned by CooldownCore's `AccountTagsTests` on
/// `spec/mobile/fixtures/tags/cases-personal-work.json`, and the memory sequences are replayed here against the real model and
/// store; every drawn state is rendered to SHOT_DIR when it is set.
@MainActor
@Suite("Summary tags, snapshot and sample (v2.0)", .serialized)
struct SummaryTagsTests {
    let r = SurfaceRenderTests()
    var now: Date { r.now }

    // MARK: The tag key (additive, round-trips through the v1.9-era reader)

    /// `Account` as v1.9 declared it: no `tag`.
    private struct V19Account: Codable, Equatable {
        var key: String
        var label: String
        var provider: Provider
        var pinned = false
        var addedAtMs: Int64? = nil
    }

    @Test("the tag key is additive: a v1.9-era reader reads a tagged account, and an account without it reads as Personal")
    func downgrade() throws {
        let tagged = Account(key: "k1", label: "Teams", provider: .claude, addedAtMs: 1_700_000_000_000, tag: "work")
        let data = try JSONEncoder().encode([tagged, Account(key: "k2", label: "Lab", provider: .antigravity)])

        // The v1.9 reader ignores the key and keeps everything it knows.
        let old = try JSONDecoder().decode([V19Account].self, from: data)
        #expect(old.map(\.key) == ["k1", "k2"] && old[0].label == "Teams" && old[0].provider == .claude)
        #expect(old[0].addedAtMs == 1_700_000_000_000 && old[0].pinned == false)

        // Nothing stored is an absent key, not a null or an empty string (nothing is written at upgrade).
        let json = try #require(String(data: data, encoding: .utf8))
        #expect(json.components(separatedBy: "\"tag\"").count == 2, "only the tagged account carries the key: \(json)")

        // What v1.9 writes back (no tag) reads as Personal in this build.
        let back = try JSONDecoder().decode([Account].self, from: try JSONEncoder().encode(old))
        #expect(back.map(\.accountTag) == [.personal, .personal] && back.allSatisfy { $0.tag == nil })

        // A file written before the key existed.
        let legacy = #"[{"key":"a","label":"Teams","provider":"claude","pinned":false}]"#.data(using: .utf8)!
        let read = try JSONDecoder().decode([Account].self, from: legacy)
        #expect(read.count == 1 && read[0].tag == nil && read[0].accountTag == .personal)
    }

    @Test("a tag this build does not know survives a round trip and reads as Personal; so does an empty one")
    func unknownTag() throws {
        let data = #"[{"key":"a","label":"Teams","provider":"claude","pinned":false,"tag":"family"}]"#.data(using: .utf8)!
        let account = try #require(try JSONDecoder().decode([Account].self, from: data).first)
        #expect(account.tag == "family" && account.accountTag == .personal)
        let again = try JSONDecoder().decode([Account].self, from: try JSONEncoder().encode([account]))
        #expect(again.first?.tag == "family")
        let empty = #"[{"key":"a","label":"Teams","provider":"claude","pinned":false,"tag":""}]"#.data(using: .utf8)!
        #expect(try JSONDecoder().decode([Account].self, from: empty).first?.accountTag == .personal)
    }

    @Test("setTag sets the account's tag (Personal or Work); the sample keeps it in memory")
    func setTag() {
        let realBefore = AccountsStore(storage: Shared.storage).load()
        let model = AppModel(demo: .standard)
        model.isSample = true
        try? FileManager.default.removeItem(at: model.storage.file("accounts.json"))
        model.setTag("lab", .personal)
        #expect(model.account("lab")?.accountTag == .personal && model.account("lab")?.tag == "personal")
        model.setTag("lab", .work)
        #expect(model.account("lab")?.accountTag == .work)
        model.setTag("nobody", .work)   // no such account: nothing happens
        #expect(model.accounts.map(\.key) == ["teams", "personal", "lab"])
        #expect(AccountsStore(storage: Shared.storage).load() == realBefore)
        // Nothing was saved anywhere: the demo store has no accounts file either (the isDemo guard in `save`).
        #expect(!FileManager.default.fileExists(atPath: model.storage.file("accounts.json").path()), "the sample wrote an accounts file")
    }

    @Test("the Summary filter set in the sample goes to the sample copy; the real suite is untouched after Exit")
    func sampleFilterIsolated() {
        let key = "summaryFilter"
        let realGroup = UserDefaults(suiteName: Shared.appGroup) ?? .standard
        let before = realGroup.object(forKey: key)
        defer { realGroup.set(before, forKey: key); SampleStore.end() }
        realGroup.set("personal", forKey: key)
        SampleStore.begin()
        #expect(Prefs.summaryFilter == "personal", "the sample starts from the user's own filter")
        Prefs.summaryFilter = "work"
        #expect(Prefs.summaryFilter == "work")
        SampleStore.end()
        #expect(realGroup.string(forKey: key) == "personal" && Prefs.summaryFilter == "personal")
    }

    // MARK: The memory rule, replayed against the real model and store

    @Test("memory: every fixture sequence replayed against AppModel and the sample's settings copy")
    func memoryReplay() async throws {
        let realGroup = UserDefaults(suiteName: Shared.appGroup) ?? .standard
        let before = realGroup.object(forKey: "summaryFilter")
        defer { realGroup.set(before, forKey: "summaryFilter"); SampleStore.end() }
        let file = URL(filePath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appending(path: "spec/mobile/fixtures/tags/cases-personal-work.json")
        let json = try #require(try JSONSerialization.jsonObject(with: Data(contentsOf: file)) as? [String: Any])
        let sequences = try #require(json["memory"] as? [[String: Any]])
        #expect(sequences.count >= 5)
        for c in sequences {
            let name = c["name"] as? String ?? "?"
            SampleStore.begin()   // the sample's copy of the settings: the user's own are never written
            let model = AppModel(demo: .standard)
            model.isSample = true
            model.accounts = (c["accounts"] as? [[String: Any]] ?? []).map {
                Account(key: ($0["name"] as? String ?? "").lowercased(), label: $0["name"] as? String ?? "", provider: .claude,
                        tag: $0["storedTag"] as? String)
            }
            Prefs.summaryFilter = c["storedFilter"] as? String
            let steps = c["steps"] as? [[String: Any]] ?? [], expected = c["expected"] as? [[String: Any]] ?? []
            #expect(steps.count == expected.count && !steps.isEmpty, "\(name)")
            for (i, step) in steps.enumerated() {
                let key = (step["account"] as? String ?? "").lowercased()
                switch step["do"] as? String {
                case "open": model.runFilterMemory()
                case "tag": model.setTag(key, AccountTag(stored: step["tag"] as? String))
                case "add":
                    model.accounts.append(Account(key: key, label: step["account"] as? String ?? "", provider: .claude, tag: step["tag"] as? String))
                    model.runFilterMemory()
                case "remove": await model.remove(key)
                case "choose":
                    // The control's own tap: only an enabled segment can be chosen.
                    let want = SummaryFilter(remembered: step["filter"] as? String)
                    let enabled = SummaryEntry.current(model: model, filter: nil, order: .yours).resolved.segmentsEnabled?[want] ?? true
                    if enabled { Prefs.summaryFilter = want.rawValue }
                default: Issue.record("\(name): unknown step \(step)")
                }
                let now = SummaryEntry.current(model: model, filter: Prefs.summaryFilter, order: .yours).resolved
                #expect(now.filter.rawValue == expected[i]["filter"] as? String, "\(name) step \(i + 1): filter in force")
                #expect(Prefs.summaryFilter == expected[i]["stored"] as? String, "\(name) step \(i + 1): stored")
            }
            SampleStore.end()
        }
    }

    @Test("a screenshot demo launch never writes the user's stored filter; nothing runs with fewer than two accounts")
    func memoryGuards() {
        let realGroup = UserDefaults(suiteName: Shared.appGroup) ?? .standard
        let before = realGroup.object(forKey: "summaryFilter")
        defer { realGroup.set(before, forKey: "summaryFilter") }
        realGroup.set("family", forKey: "summaryFilter")
        let demo = AppModel(demo: .standard)   // isDemo, not the sample
        demo.runFilterMemory()
        #expect(realGroup.string(forKey: "summaryFilter") == "family")
    }

    @Test("the Add account control: Personal by default, written when the account is created, ignored by sign in again")
    func pendingTag() {
        let model = AppModel(demo: .standard)
        #expect(model.pendingTag == .personal, "Personal is selected every time")
        let work = AppModel.newAccount(key: "claude-1", provider: .claude, existing: [], tag: .work, addedAtMs: 1)
        #expect(work.tag == "work" && work.accountTag == .work && work.label == "Claude")
        let second = AppModel.newAccount(key: "claude-2", provider: .claude, existing: [work], tag: .personal, addedAtMs: 2)
        #expect(second.tag == "personal" && second.label == "Claude 2")
    }

    // MARK: The Summary's rows follow the filter and the order

    @Test("the filter narrows the rows, the order ranks what is shown")
    func rows() {
        let model = AppModel(demo: .standard)
        func keys(_ filter: String?, _ order: SummaryOrder = .yours) -> [String] {
            SummaryEntry.current(model: model, filter: filter, order: order).entries.map(\.account.key)
        }
        #expect(keys(nil) == ["teams", "personal", "lab"] && keys("all") == ["teams", "personal", "lab"])
        #expect(keys("work") == ["teams", "lab"] && keys("personal") == ["personal"])
        #expect(keys("family") == ["teams", "personal", "lab"], "an unknown remembered filter reads as All")

        let work = SummaryEntry.current(model: model, filter: "work", order: .yours)
        #expect(work.resolved.controlShown && work.resolved.filter == .work && work.resolved.snapshotHeader == "Work accounts")
        #expect(work.entries.map(\.rank) == [1, 2], "the rank counts the rows shown")
        #expect(SummaryEntry.current(model: model, filter: "personal", order: .yours).resolved.snapshotHeader == "Personal accounts")

        // The order is unchanged by the filter: the same order applies to the rows shown.
        let used = SummaryEntry.current(model: model, filter: nil, order: .used).entries.compactMap(\.usedPct)
        #expect(used == used.sorted(by: >), "Most used: highest Weekly first")
    }

    @Test("the upgrade: nothing stored reads as Personal, Work is disabled and a remembered Work reads as All; the control is there")
    func upgraded() {
        let model = AppModel(demo: .upgraded)
        #expect(model.accounts.allSatisfy { $0.tag == nil && $0.accountTag == .personal })
        let r = SummaryEntry.current(model: model, filter: "work", order: .yours)
        #expect(r.resolved.controlShown && r.resolved.segmentsEnabled?[.work] == false && r.resolved.filter == .all)
        #expect(r.entries.count == 3)
        #expect(SummaryEntry.current(model: model, filter: "personal", order: .yours).entries.count == 3)
    }

    @Test("the sort menu lists the four Summary orders, named as Appearance names them, and the header names the current one")
    func sortMenu() {
        #expect(SummaryOrder.allCases.map(\.label) == ["Your order", "Most room first", "Resets soonest", "Most used"])
        #expect(SummaryOrder.allCases.map(\.header) == ["your order", "most room first", "resets soonest", "most used"])
    }

    @Test("one-line rows follow the rows shown: ten accounts go to one line, Personal's three stay two-line")
    func oneLineFollowsRows() {
        let model = AppModel(demo: .dense)
        #expect(SummaryEntry.current(model: model, filter: nil, order: .yours).resolved.oneLineRows)
        let personal = SummaryEntry.current(model: model, filter: "personal", order: .yours)
        #expect(personal.entries.count == 3 && !personal.resolved.oneLineRows)
        #expect(SummaryEntry.current(model: model, filter: "work", order: .yours).resolved.oneLineRows, "Work shows seven: still one line")
    }

    // MARK: The sample (CCRM-113's iOS follow-up)

    @Test("the sample is Teams (Claude), Personal (ChatGPT) and Lab (Gemini): the same three accounts as Android's, no Product")
    func sample() {
        let model = AppModel(demo: .standard)
        #expect(model.accounts.map(\.label) == ["Teams", "Personal", "Lab"])
        #expect(model.accounts.map(\.provider) == [.claude, .chatgpt, .antigravity])
        #expect(model.accounts.map(\.accountTag) == [.work, .personal, .work], "Teams Work, Personal Personal, Lab Work")
        for scenario in DemoScenario.allCases where scenario != .upgraded {
            if let lab = AppModel(demo: scenario).account("lab") { #expect(lab.accountTag == .work, "Lab is Work in every scenario: \(scenario)") }
        }
        #expect(!model.accounts.contains { $0.key == "product" || $0.label == "Product" })
        let lab = model.states["lab"]
        #expect(PlanNames.display(.antigravity, lab?.plan ?? "", tier: lab?.tier) == "Pro")
        let entries = SummaryEntry.current(model: model, filter: nil, order: .yours).entries
        #expect(entries.count == 3 && entries.allSatisfy { $0.usedPct != nil }, "every sample account has a reading")
        // The All · Personal · Work control shows in the sample.
        #expect(SummaryEntry.current(model: model, filter: nil, order: .yours).resolved.controlShown)
    }

    // MARK: The snapshot's rows

    private func build(_ specs: [(name: String, provider: Provider, used: Double?, gone: Double, status: Face.Status)]) -> [SummaryEntry] {
        var faces: [Face] = []
        var histories: [String: [HistoryPoint]] = [:]
        for s in specs {
            var f = r.face(s.name, s.provider, pct: 20, pace: 50, status: s.status, weeklyPct: s.used ?? 0, weeklyPace: s.gone)
            if s.used == nil {
                f = Face(account: f.account, pct: nil, pace: nil, reset: nil, runOut: nil, weeklyPct: nil, weeklyPace: nil,
                         weeklyReset: nil, fetchedAt: nil, status: .noData)
            }
            faces.append(f)
            guard let used = s.used, used < 99.5, let reset = f.weeklyReset?.ms else { continue }
            let left = Double(reset - now.ms) / 3_600_000
            let rate = (min(used * 1.4, 99) - used) / left
            histories[f.account.key] = stride(from: 36.0, through: 0, by: -2).map { h in
                HistoryPoint(atMs: now.ms - Int64(h * 3_600_000), sessionPct: nil, sessionResetAtMs: 0,
                             weeklyPct: max(0, used - rate * h), weeklyResetAtMs: reset)
            }
        }
        return SummaryEntry.build(accounts: faces.map(\.account),
                                  faces: Dictionary(uniqueKeysWithValues: faces.map { ($0.account.key, $0) }),
                                  plans: [:], histories: histories, nowMs: now.ms, order: .yours)
    }

    private typealias Spec = (name: String, provider: Provider, used: Double?, gone: Double, status: Face.Status)
    private let teams: Spec = ("Teams", .claude, 52, 39, .fresh)
    private let personal: Spec = ("Personal", .chatgpt, 50, 68, .fresh)
    private let chatgpt: Spec = ("ChatGPT", .chatgpt, 6, 30, .fresh)
    private let atlas: Spec = ("Atlas", .claude, 100, 70, .fresh)
    private let gemini: Spec = ("Gemini", .antigravity, 91, 83, .fresh)
    private let lab: Spec = ("Lab", .claude, 61, 61, .fresh)
    private let stale: Spec = ("Stale", .chatgpt, 44, 50, .stale)
    private let signedOut: Spec = ("Signed out", .claude, 30, 50, .signIn)
    private let unread: Spec = ("Product", .claude, nil, 0, .fresh)

    @Test("the snapshot's pace slot: the pace span, Out until, Stale, Sign in again, No reading; dead rows draw no bar fill")
    func slots() {
        let e = build([teams, atlas, stale, signedOut, unread])
        func slot(_ name: String, short: Bool = false) -> (String, String) {
            let entry = e.first { $0.account.label == name }!
            let s = SummarySnapshotView.slot(entry, nowMs: now.ms, short: short)
            let tone: String
            switch s.tone { case .quiet: tone = "quiet"; case .red: tone = "red"; case .orange: tone = "orange"; case .pace: tone = "pace" }
            return (s.text, tone)
        }
        #expect(slot("Teams").1 == "pace" && !slot("Teams").0.isEmpty)
        #expect(slot("Atlas").0.hasPrefix("Out until") && slot("Atlas").1 == "red")
        #expect(slot("Atlas", short: true).0.hasPrefix("Out ·"), "the one-line form is the short word")
        #expect(slot("Stale") == ("Stale", "quiet"))
        #expect(slot("Signed out") == ("Sign in again", "quiet"))
        #expect(slot("Product") == ("No reading", "quiet"))
        for name in ["Signed out", "Product"] {
            #expect(SummarySnapshotView.dead(e.first { $0.account.label == name }!), "\(name): no bar fill")
        }
        #expect(!SummarySnapshotView.dead(e.first { $0.account.label == "Stale" }!), "a stale reading still draws its bar")
        #expect(SummarySnapshotView.resets(e.first { $0.account.label == "Teams" }!, nowMs: now.ms)?.hasPrefix("Resets ") == true)
        #expect(SummarySnapshotView.resets(e.first { $0.account.label == "Product" }!, nowMs: now.ms) == nil)
    }

    @Test("the snapshot sheet's card follows the filter, the order and the layers, names the newest reading shown, and is nil without one")
    func snapshotCard() throws {
        let model = AppModel(demo: .standard)
        let layers = MapLayers(figures: true, trails: false, projection: true)
        let work = try #require(SummarySnapshotSheet.make(model: model, filter: "work", order: .room, layers: layers))
        #expect(work.title == "Work accounts" && work.orderName == "most room first")
        #expect(work.entries.map(\.account.key) == ["teams", "lab"])
        #expect(!work.glyphs, "a filter shows one tag: no glyph column on the card")
        #expect(work.layers == layers, "the layers drawn on screen, not the defaults")
        #expect(work.asOfMs == model.snapshots["teams"]?.fetchedAtMs, "the newest reading among the rows shown")
        #expect(!work.sample)

        let all = try #require(SummarySnapshotSheet.make(model: model, filter: nil, order: .yours, layers: MapLayers()))
        #expect(all.title == "All accounts" && all.entries.map(\.account.key) == ["teams", "personal", "lab"])
        #expect(all.glyphs, "both tags on the card: the glyph column")
        let upgraded = try #require(SummarySnapshotSheet.make(model: AppModel(demo: .upgraded), filter: nil, order: .yours, layers: MapLayers()))
        #expect(!upgraded.glyphs, "an upgraded install shares the card without the column")
        let newest = model.accounts.compactMap { model.snapshots[$0.key]?.fetchedAtMs }.max()
        #expect(all.asOfMs == newest)
        model.isSample = true
        #expect(SummarySnapshotSheet.make(model: model, filter: nil, order: .yours, layers: MapLayers())?.sample == true)

        #expect(SummarySnapshotSheet.make(model: AppModel(demo: .noreading), filter: nil, order: .yours, layers: MapLayers()) == nil)
    }

    // MARK: Renders

    func withMode(_ mode: PaceText.Mode, _ body: () -> Void) {
        let d = Prefs.defaults
        let before = (d.object(forKey: Display.Key.usageLeft), d.object(forKey: Display.Key.usageVsPace))
        Display.setMode(mode)
        body()
        d.set(before.0, forKey: Display.Key.usageLeft)
        d.set(before.1, forKey: Display.Key.usageVsPace)
    }

    /// The card as the sheet renders it: 300 pt wide at 3×.
    func shoot(_ name: String, _ card: SummarySnapshotView, scheme: ColorScheme) {
        let palette = Palette.current(scheme)
        let renderer = ImageRenderer(content: card.environment(\.palette, palette).environment(\.colorScheme, scheme))
        renderer.scale = 3
        let image = renderer.uiImage
        #expect(image != nil, "\(name) did not render")
        if let dir = ProcessInfo.processInfo.environment["SHOT_DIR"], let png = image?.pngData() {
            try? png.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
        }
    }

    @Test("render: the snapshot, every state the wireframe draws, dark and light")
    func snapshots() {
        let four = tagged(build([teams, personal, chatgpt, atlas]), work: ["Teams", "Atlas"])
        let work = tagged(build([gemini, stale, atlas, unread, ("Tiny", .chatgpt, 0.2, 40, .fresh)]), work: ["Gemini", "Stale", "Atlas", "Product", "Tiny"])
        let eight = tagged(build([teams, personal, chatgpt, gemini, lab, atlas, ("Ops", .claude, 33, 36, .fresh), ("Client", .antigravity, 74, 90, .fresh)]),
                           work: ["Teams", "Gemini", "Lab", "Atlas", "Ops", "Client"])
        let six = tagged(build([teams, personal, chatgpt, gemini, lab, atlas]), work: [])
        let fourPersonal = tagged(four, work: [])
        for scheme in [ColorScheme.dark, .light] {
            let t = scheme == .dark ? "dark" : "light"
            withMode(.used) {
                shoot("i4-snapshot-default-glyphs-\(t)", SummarySnapshotView(entries: four, title: "All accounts", orderName: "your order",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers(), glyphs: true), scheme: scheme)
                shoot("i4-snapshot-default-one-tag-\(t)", SummarySnapshotView(entries: fourPersonal, title: "All accounts", orderName: "your order",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers()), scheme: scheme)
                shoot("i4-snapshot-work-filter-\(t)", SummarySnapshotView(entries: work, title: "Work accounts", orderName: "most room first",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers(figures: true, trails: true, projection: true)), scheme: scheme)
                shoot("i4-snapshot-eight-one-line-sample-\(t)", SummarySnapshotView(entries: eight, title: "All accounts", orderName: "your order",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers(figures: true, trails: true, projection: false), sample: true, glyphs: true),
                      scheme: scheme)
                shoot("i4-snapshot-six-one-line-\(t)", SummarySnapshotView(entries: six, title: "Personal accounts", orderName: "resets soonest",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers()), scheme: scheme)
            }
            withMode(.left) {
                shoot("i4-snapshot-left-\(t)", SummarySnapshotView(entries: four, title: "All accounts", orderName: "your order",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers(figures: true, trails: true, projection: false)), scheme: scheme)
            }
            withMode(.vsPace) {   // Vs pace renders as Used on the card and its map
                shoot("i4-snapshot-vspace-as-used-\(t)", SummarySnapshotView(entries: four, title: "All accounts", orderName: "your order",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers(figures: true, trails: true, projection: false)), scheme: scheme)
            }
        }
    }

    /// The entries with the named accounts marked Work (the rest read Personal).
    func tagged(_ entries: [SummaryEntry], work: Set<String>) -> [SummaryEntry] {
        entries.map { e in
            var account = e.account
            account.tag = work.contains(account.label) ? AccountTag.work.rawValue : AccountTag.personal.rawValue
            return SummaryEntry(account: account, face: e.face, plan: e.plan, group: e.group, rank: e.rank, trail: e.trail,
                                estimate: e.estimate)
        }
    }

    func list(_ entries: [SummaryEntry], _ resolved: SummaryFilter.Resolved, order: SummaryOrder = .yours) -> some View {
        SummaryList(entries: entries, nowMs: now.ms, resolved: resolved, order: order).environment(\.menusAsLabels, true)
    }

    @Test("render: the Summary — the upgrade, both tags, each filter, narrow and wrapped, the glyph column dropped — dark and light")
    func headers() {
        let three = tagged(build([teams, personal, lab]), work: ["Teams", "Lab"])
        let tags = three.map(\.account.accountTag)
        let personalOnly = tagged(three, work: [])
        for scheme in [ColorScheme.dark, .light] {
            let t = scheme == .dark ? "dark" : "light"
            func save(_ name: String, _ v: some View, width: CGFloat = 393, height: CGFloat = 400) {
                r.save("i4-list-\(name)-\(t)", v, width: width, height: height, scheme: scheme, background: scheme == .dark ? .black : nil)
            }
            save("upgraded", list(personalOnly, SummaryFilter.resolve(tags: personalOnly.map(\.account.accountTag), remembered: "work")))
            save("all", list(three, SummaryFilter.resolve(tags: tags, remembered: nil)))
            save("work", list(three.filter { $0.account.accountTag == .work }, SummaryFilter.resolve(tags: tags, remembered: "work"), order: .room), height: 330)
            save("personal", list(three.filter { $0.account.accountTag == .personal }, SummaryFilter.resolve(tags: tags, remembered: "personal")), height: 240)
            save("narrow", list(three, SummaryFilter.resolve(tags: tags, remembered: nil)), width: 320, height: 420)
            save("narrow-wrapped", list(three, SummaryFilter.resolve(tags: tags, remembered: nil)), width: 280, height: 440)
            let six = tagged(build([teams, personal, chatgpt, gemini, lab, atlas]), work: ["Teams", "Gemini", "Lab", "Atlas"])
            save("one-line", list(six, SummaryFilter.resolve(tags: six.map(\.account.accountTag), remembered: nil)), height: 330)
            save("one-line-narrow", list(six, SummaryFilter.resolve(tags: six.map(\.account.accountTag), remembered: nil)), width: 300, height: 330)
        }
    }

    @Test("render: the tag chip (word and glyph-only), the row glyphs, the Add account control and the picker rows' glyphs")
    func chips() {
        for scheme in [ColorScheme.dark, .light] {
            let t = scheme == .dark ? "dark" : "light"
            r.save("i4-tag-chips-\(t)", HStack(spacing: 8) {
                PlanChip(provider: .claude, plan: "Max"); TagChip(tag: .work); TagChip(tag: .personal); TagChip(tag: .work, showWord: false)
                TagRowGlyph(tag: .personal); TagRowGlyph(tag: .work); TagRowGlyph(tag: .work, compact: true)
            }, width: 330, height: 56, scheme: scheme)
            r.save("i4-add-tag-control-\(t)", AddTagControl(tag: .constant(.personal)).padding(16), width: 393, height: 130, scheme: scheme)
            r.save("i4-add-tag-control-work-\(t)", AddTagControl(tag: .constant(.work)).padding(16), width: 393, height: 130, scheme: scheme)
        }
        #expect(TagGlyph.symbol(.personal) == "person" && TagGlyph.symbol(.work) == "briefcase")
    }
}
