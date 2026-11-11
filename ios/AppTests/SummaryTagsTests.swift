import CooldownCore
import SwiftUI
import Testing
import UIKit

@testable import Cooldown

/// CCRM-110 (Account Tags), CCRM-112 (Summary Snapshot) and the iOS follow-up to CCRM-113 (Sample Port) on iOS, wireframe
/// design/2026-11-10-v20-feedback-wireframes.html rev B §6, §8 and §9: the additive tag key, the filter and the sort over the
/// Summary's rows, the snapshot's rows, and the sample's three accounts. The filter rules are pinned by CooldownCore's
/// `AccountTagsTests` on `spec/mobile/fixtures/tags`; every drawn state is rendered to SHOT_DIR when it is set.
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

    @Test("the tag key is additive: a v1.9-era reader reads a tagged account, and an account without it reads as None")
    func downgrade() throws {
        let tagged = Account(key: "k1", label: "Teams", provider: .claude, addedAtMs: 1_700_000_000_000, tag: "work")
        let data = try JSONEncoder().encode([tagged, Account(key: "k2", label: "Lab", provider: .antigravity)])

        // The v1.9 reader ignores the key and keeps everything it knows.
        let old = try JSONDecoder().decode([V19Account].self, from: data)
        #expect(old.map(\.key) == ["k1", "k2"] && old[0].label == "Teams" && old[0].provider == .claude)
        #expect(old[0].addedAtMs == 1_700_000_000_000 && old[0].pinned == false)

        // None is an absent key, not a null or an empty string.
        let json = try #require(String(data: data, encoding: .utf8))
        #expect(json.components(separatedBy: "\"tag\"").count == 2, "only the tagged account carries the key: \(json)")

        // What v1.9 writes back (no tag) reads as None in this build.
        let back = try JSONDecoder().decode([Account].self, from: try JSONEncoder().encode(old))
        #expect(back.map(\.accountTag) == [nil, nil])

        // A file written before the key existed.
        let legacy = #"[{"key":"a","label":"Teams","provider":"claude","pinned":false}]"#.data(using: .utf8)!
        let read = try JSONDecoder().decode([Account].self, from: legacy)
        #expect(read.count == 1 && read[0].tag == nil && read[0].accountTag == nil)
    }

    @Test("a tag this build does not know survives a round trip and reads as None")
    func unknownTag() throws {
        let data = #"[{"key":"a","label":"Teams","provider":"claude","pinned":false,"tag":"family"}]"#.data(using: .utf8)!
        let account = try #require(try JSONDecoder().decode([Account].self, from: data).first)
        #expect(account.tag == "family" && account.accountTag == nil)
        let again = try JSONDecoder().decode([Account].self, from: try JSONEncoder().encode([account]))
        #expect(again.first?.tag == "family")
    }

    @Test("setTag sets and clears the account's tag; the sample keeps it in memory")
    func setTag() {
        let realBefore = AccountsStore(storage: Shared.storage).load()
        let model = AppModel(demo: .standard)
        model.isSample = true
        model.setTag("lab", .personal)
        #expect(model.account("lab")?.accountTag == .personal)
        model.setTag("lab", nil)
        #expect(model.account("lab")?.tag == nil)
        model.setTag("nobody", .work)   // no such account: nothing happens
        #expect(model.accounts.map(\.key) == ["teams", "personal", "lab"])
        #expect(AccountsStore(storage: Shared.storage).load() == realBefore)
    }

    // MARK: The Summary's rows follow the filter and the order

    @Test("the filter narrows the rows, the order ranks what is shown, and an untagged account shows only under All")
    func rows() {
        let model = AppModel(demo: .standard)
        func keys(_ filter: String?, _ order: SummaryOrder = .yours) -> [String] {
            SummaryEntry.current(model: model, filter: filter, order: order).entries.map(\.account.key)
        }
        #expect(keys(nil) == ["teams", "personal", "lab"] && keys("all") == ["teams", "personal", "lab"])
        #expect(keys("work") == ["teams"] && keys("personal") == ["personal"])
        #expect(keys("family") == ["teams", "personal", "lab"], "an unknown remembered filter reads as All")

        let work = SummaryEntry.current(model: model, filter: "work", order: .yours)
        #expect(work.resolved.controlShown && work.resolved.filter == .work && work.resolved.snapshotHeader == "Work accounts")
        #expect(work.entries.map(\.rank) == [1], "the rank counts the rows shown")
        #expect(SummaryEntry.current(model: model, filter: "personal", order: .yours).resolved.snapshotHeader == "Personal accounts")

        // The order is unchanged by the filter: the same order applies to the rows shown.
        let used = SummaryEntry.current(model: model, filter: nil, order: .used).entries.compactMap(\.usedPct)
        #expect(used == used.sorted(by: >), "Most used: highest Weekly first")
    }

    @Test("a segment with no accounts is disabled and a remembered filter on it reads as All; untagged: no control")
    func disabledAndUntagged() {
        let disabled = AppModel(demo: .tagsdisabled)
        let r1 = SummaryEntry.current(model: disabled, filter: "work", order: .yours)
        #expect(r1.resolved.controlShown && r1.resolved.segmentsEnabled?[.work] == false && r1.resolved.filter == .all)
        #expect(r1.entries.count == 3)
        let untagged = AppModel(demo: .untagged)
        let r2 = SummaryEntry.current(model: untagged, filter: "work", order: .yours)
        #expect(!r2.resolved.controlShown && r2.entries.count == 3)
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
        #expect(SummaryEntry.current(model: model, filter: "work", order: .yours).resolved.oneLineRows, "Work shows six: still one line")
    }

    // MARK: The sample (CCRM-113's iOS follow-up)

    @Test("the sample is Teams (Claude), Personal (ChatGPT) and Lab (Gemini): the same three accounts as Android's, no Product")
    func sample() {
        let model = AppModel(demo: .standard)
        #expect(model.accounts.map(\.label) == ["Teams", "Personal", "Lab"])
        #expect(model.accounts.map(\.provider) == [.claude, .chatgpt, .antigravity])
        #expect(model.accounts.map(\.accountTag) == [.work, .personal, nil], "Teams Work, Personal Personal, Lab untagged")
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
        let four = build([teams, personal, chatgpt, atlas])
        let work = build([gemini, stale, atlas, unread, ("Tiny", .chatgpt, 0.2, 40, .fresh)])
        let eight = build([teams, personal, chatgpt, gemini, lab, atlas, ("Ops", .claude, 33, 36, .fresh), ("Client", .antigravity, 74, 90, .fresh)])
        let six = build([teams, personal, chatgpt, gemini, lab, atlas])
        for scheme in [ColorScheme.dark, .light] {
            let t = scheme == .dark ? "dark" : "light"
            withMode(.used) {
                shoot("i3-snapshot-default-\(t)", SummarySnapshotView(entries: four, title: "All accounts", orderName: "your order",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers()), scheme: scheme)
                shoot("i3-snapshot-work-filter-\(t)", SummarySnapshotView(entries: work, title: "Work accounts", orderName: "most room first",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers(figures: true, trails: true, projection: true)), scheme: scheme)
                shoot("i3-snapshot-eight-one-line-sample-\(t)", SummarySnapshotView(entries: eight, title: "All accounts", orderName: "your order",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers(figures: true, trails: true, projection: false), sample: true),
                      scheme: scheme)
                shoot("i3-snapshot-six-one-line-\(t)", SummarySnapshotView(entries: six, title: "Personal accounts", orderName: "resets soonest",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers()), scheme: scheme)
            }
            withMode(.left) {
                shoot("i3-snapshot-left-\(t)", SummarySnapshotView(entries: four, title: "All accounts", orderName: "your order",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers(figures: true, trails: true, projection: false)), scheme: scheme)
            }
            withMode(.vsPace) {   // Vs pace renders as Used on the card and its map
                shoot("i3-snapshot-vspace-as-used-\(t)", SummarySnapshotView(entries: four, title: "All accounts", orderName: "your order",
                      asOfMs: now.ms - 120_000, nowMs: now.ms, layers: MapLayers(figures: true, trails: true, projection: false)), scheme: scheme)
            }
        }
    }

    func list(_ entries: [SummaryEntry], _ resolved: SummaryFilter.Resolved, order: SummaryOrder = .yours) -> some View {
        SummaryList(entries: entries, nowMs: now.ms, resolved: resolved, order: order).environment(\.menusAsLabels, true)
    }

    @Test("render: the Summary header — no tags, All, Work, a disabled segment, narrow and wrapped — dark and light")
    func headers() {
        let two = build([teams, personal])
        let tags: [AccountTag?] = [.work, .personal]
        for scheme in [ColorScheme.dark, .light] {
            let t = scheme == .dark ? "dark" : "light"
            func save(_ name: String, _ v: some View, width: CGFloat = 393, height: CGFloat = 240) {
                r.save("i3-header-\(name)-\(t)", v, width: width, height: height, scheme: scheme, background: scheme == .dark ? .black : nil)
            }
            save("untagged", list(two, SummaryFilter.resolve(tags: [nil, nil], remembered: nil)))
            save("all", list(two, SummaryFilter.resolve(tags: tags, remembered: nil)))
            save("work", list(Array(two.prefix(1)), SummaryFilter.resolve(tags: tags, remembered: "work"), order: .room))
            save("work-disabled", list(two, SummaryFilter.resolve(tags: [nil, .personal], remembered: "work")))
            save("narrow-glyph", list(two, SummaryFilter.resolve(tags: tags, remembered: "personal")), width: 320)
            save("narrow-wrapped", list(two, SummaryFilter.resolve(tags: tags, remembered: nil)), width: 280, height: 280)
        }
    }

    @Test("render: the tag chip and the picker rows' glyphs")
    func chips() {
        for scheme in [ColorScheme.dark, .light] {
            let t = scheme == .dark ? "dark" : "light"
            r.save("i3-tag-chips-\(t)", HStack(spacing: 8) {
                PlanChip(provider: .claude, plan: "Max"); TagChip(tag: .work); TagChip(tag: .personal)
            }, width: 260, height: 56, scheme: scheme)
        }
        #expect(TagGlyph.symbol(.personal) == "person" && TagGlyph.symbol(.work) == "briefcase")
    }
}
