import CooldownCore
import SwiftUI
import Testing
import WidgetKit

@testable import Cooldown

/// RUNBOOK-v2.0 Steps 6a and 6c on iOS: CCRM-102 (Spent Week Gate) and CCRM-55 (Antigravity Account), per
/// design/2026-11-02-antigravity-account.html rev B (§2 I1–I5, §3b, §4, §5). Renders go to SHOT_DIR when set.
@MainActor
@Suite("Gemini and the spent-week gate", .serialized)
struct GeminiAndGateTests {
    let r = SurfaceRenderTests()
    var now: Date { r.now }

    /// Teams with its week spent and a fresh 5h at 34% (the wireframe's 3b-1).
    func spentWeek(headline: Headline = .weekly) -> Face {
        r.face("Teams", .claude, pct: 34, pace: 53, minutes: 140, weeklyPct: 100, weeklyPace: 60, headline: headline)
    }

    // MARK: - 6a · the gate

    @Test("the note names the weekly reset; a spent week gates the 5h, an unspent one never does")
    func gateRule() throws {
        let reset = now.ms + 3 * 86_400_000
        let note = CardContent.weekGateNote(weeklyResetMs: reset, nowMs: now.ms)
        #expect(note.hasPrefix("Weekly limit reached — 5h can't be used until "))
        #expect(note.hasSuffix(CardContent.dayClock(reset, now.ms, Display.use24h, .current)))
        let spent = UsageData(session: UsageWindow(percent: 34, resetsAtMs: now.ms + 3_600_000, serverSeverity: nil),
                              weekly: UsageWindow(percent: 99.5, resetsAtMs: reset, serverSeverity: nil), modelCaps: [], credits: nil)
        #expect(AccountCards.weekGate(spent, nowMs: now.ms) == note)
        let fine = UsageData(session: spent.session, weekly: UsageWindow(percent: 99.4, resetsAtMs: reset, serverSeverity: nil),
                             modelCaps: [], credits: nil)
        #expect(AccountCards.weekGate(fine, nowMs: now.ms) == nil)
        // On the faces: a 5h main slot under a spent week is gated; a Weekly lead is just spent.
        #expect(spentWeek(headline: .fiveHour).lead(for: .notification).weekGated)
        #expect(!spentWeek().lead(for: .notification).weekGated)
        #expect(spentWeek().weekGated)  // the unled face (Number, Countdown, Control Center read 5h)
        #expect(!r.face("Teams", .claude, pct: 34, pace: 53, weeklyPct: 52, weeklyPace: 39).weekGated)
        let row = CooldownActivityAttributes.Row(spentWeek(headline: .fiveHour).lead(for: .notification))
        #expect(row.isWeekGated)
    }

    @Test("renders: the greyed 5h card (folded and open), Number, Countdown, Overview, lock, Live Activity")
    func gateRenders() throws {
        let f = spentWeek()
        let five = try #require(f.session)
        let note = CardContent.weekGateNote(weeklyResetMs: f.weeklyReset?.ms, nowMs: now.ms)
        let accent = f.account.accent(.dark)
        r.save("v21-gate-5h-folded", WindowCard(content: five, accent: accent, expanded: .constant(false), weekGate: note),
               width: 390, height: 190, background: .black)
        r.save("v21-gate-5h-open", WindowCard(content: five, accent: accent, expanded: .constant(true), chartHeight: 120, weekGate: note),
               width: 390, height: 360, background: .black)
        r.save("v21-gate-5h-folded-light", WindowCard(content: five, accent: f.account.accent(.light), expanded: .constant(false), weekGate: note),
               width: 390, height: 190, scheme: .light)
        let entry = FaceEntry(date: now, faces: [f], key: f.account.key)
        r.save("v21-gate-number-5h", NumberFace(entry: entry), width: 170, height: 170)
        r.save("v21-gate-countdown", CountdownFace(entry: entry), width: 170, height: 170)
        let fiveLed = spentWeek(headline: .fiveHour)
        r.save("v21-gate-overview-5h", OverviewFace(entry: FaceEntry(date: now, faces: [fiveLed], key: nil), familyOverride: .systemLarge),
               width: 346, height: 346)
        let wall = Color(hex: 0x6E5F95)
        let fiveEntry = FaceEntry(date: now, faces: [fiveLed], key: fiveLed.account.key)
        r.save("v21-gate-lock-circle", LockFace(entry: fiveEntry, familyOverride: .accessoryCircular).foregroundStyle(.white),
               width: 104, height: 104, background: wall)
        r.save("v21-gate-la", LockPanel(state: .init(rows: [.init(fiveLed.lead(for: .notification)), .init(r.face("Personal", .chatgpt, pct: nil, pace: nil, weeklyPct: 50, weeklyPace: 67, weeklyOnly: true).leading)], ended: nil)),
               width: 370, height: 160, background: Color(hex: 0x1C1C1E))
    }

    // MARK: - 6c · Gemini

    func gemini(_ file: String) throws -> (Face, UsageData) {
        let c = try #require((try JSONSerialization.jsonObject(with: Data(contentsOf: Self.fixtures.appending(path: "cases.json"))) as? [String: Any])?["parse"] as? [[String: Any]])
        let nowMs = try #require(c.first { $0["file"] as? String == file }?["nowMs"] as? NSNumber).int64Value
        let body = try String(contentsOf: Self.fixtures.appending(path: file), encoding: .utf8)
        let snap = Snapshot(profileKey: "gemini-\(file)", provider: .antigravity, body: body, fetchedAtMs: nowMs,
                            lastAttemptAtMs: nowMs, lastKind: "success", lastMessage: "", plan: "Pro")
        let face = Face.build(account: Account(key: "gemini-\(file)", label: "Gemini", provider: .antigravity), snapshot: snap,
                              state: AccountState(), history: [], nowMs: nowMs + 60_000)
        return (face, try #require(snap.usage(nowMs: nowMs + 60_000)))
    }

    static let fixtures: URL = URL(filePath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().appending(path: "spec/core/fixtures/antigravity")

    @Test("the provider is Gemini, in Gemini Blue, with its own mark")
    func provider() {
        #expect(Provider.antigravity.displayName == "Gemini")
        #expect(Provider.antigravity.markAsset == "GeminiMark")
        #expect(UIImage(named: "GeminiMark") != nil)
        #expect(Sheet.signIn(for: .antigravity) == .geminiSignIn)
        #expect(Sheet.signIn(for: .chatgpt) == .chatGPTSignIn && Sheet.signIn(for: .claude) == .claudeSignIn)
    }

    @Test("H: a pool-only reply reads 'Google changed something', with its evidence, and only for Gemini")
    func notice() {
        var state = AccountState()
        state.lastKind = "invalidResponse"
        state.lastMessage = "Reply held 1 of 3 groups"
        let n = Notice.forAccount(label: "Gemini", state: state, snapshot: nil, nowMs: now.ms, provider: .antigravity)
        #expect(n?.title == "Google changed something — usage unavailable")
        #expect(n?.evidence == "Reply held 1 of 3 groups")
        #expect(n?.body == "Cooldown won’t guess. An app update may be needed.")
        #expect(Notice.forAccount(label: "Teams", state: state, snapshot: nil, nowMs: now.ms, provider: .claude) == nil)
    }

    @Test("a Gemini reading: Weekly leads, Argon is a model limit, the untouched pool is hidden and the used one shown")
    func reading() throws {
        let (light, lightData) = try gemini("pro-light-use.json")
        #expect(light.lead(for: .notification).leadsWeekly)
        #expect(lightData.modelCaps.map(\.modelName) == ["Argon"])
        #expect(lightData.pools.first?.shown == false)
        let (_, used) = try gemini("pool-used.json")
        #expect(used.pools.first?.shown == true && used.pools.first?.name == "Claude + GPT in Antigravity")
        let (notStarted, _) = try gemini("not-started.json")
        #expect(notStarted.weekly?.notStarted == true && notStarted.session?.notStarted == true)
        let (spent, _) = try gemini("weekly-spent.json")
        #expect(spent.weekGated && spent.lead(for: .notification).leadSpent)
    }

    @Test("renders: Gemini widgets, cards with Argon, the pool pair, the sign-in sheet, the H notice")
    func geminiRenders() throws {
        for file in ["pro-light-use.json", "pool-used.json", "not-started.json", "weekly-spent.json"] {
            let (f, _) = try gemini(file)
            let tag = file.replacingOccurrences(of: ".json", with: "")
            let entry = FaceEntry(date: now, faces: [f], key: f.account.key)
            r.save("v21-gemini-bars-\(tag)", BarsFace(entry: entry), width: 160, height: 160)
            r.save("v21-gemini-strip-\(tag)", StripFace(entry: entry), width: 364, height: 170)
            r.save("v21-gemini-number-\(tag)", NumberFace(entry: FaceEntry(date: now, faces: [f], key: f.account.key, window: .weekly)),
                   width: 170, height: 170)
        }
        // App cards through a demo model holding one Gemini account.
        let model = AppModel(demo: .standard)
        for file in ["pool-used.json", "weekly-spent.json"] {
            let body = try String(contentsOf: Self.fixtures.appending(path: file), encoding: .utf8)
            let (f, _) = try gemini(file)
            let key = f.account.key
            model.accounts = [f.account]
            model.nowMs = (f.fetchedAt?.ms ?? now.ms) + 60_000
            var state = AccountState(); state.plan = "Pro"; state.lastKind = "success"
            model.set(snapshot: Snapshot(profileKey: key, provider: .antigravity, body: body, fetchedAtMs: f.fetchedAt?.ms ?? 0,
                                         lastAttemptAtMs: f.fetchedAt?.ms ?? 0, lastKind: "success", lastMessage: "", plan: "Pro"),
                      state: state, history: [], records: [], for: key)
            r.save("v21-gemini-cards-\(file.replacingOccurrences(of: ".json", with: ""))",
                   VStack { AccountCards(account: f.account); Spacer(minLength: 0) }.environment(model), width: 393, height: 900, background: .black)
        }
        // Argon as the 7-day card's model half.
        let (light, lightData) = try gemini("pro-light-use.json")
        let argon = try #require(lightData.modelCaps.first)
        let cap = try #require(CardContent.window(title: "Argon", window: argon.window, kind: HistoryStats.weeklyKind, history: [], nowMs: now.ms))
        r.save("v21-gemini-weekly-argon", WeeklyCard(all: try #require(light.weekly), cap: cap, accent: light.account.accent(.dark),
                                                     expanded: .constant(true)), width: 390, height: 420, background: .black)
        r.save("v21-gemini-signin", GeminiSignInSheet().environment(model), width: 393, height: 360, background: .black)
        r.save("v21-gemini-add-nosecret", AddAccountSheet(sheet: .constant(nil)), width: 393, height: 520, background: .black)
        // A build with the secret shows the Gemini row (I1); a dummy value, never a real one, and cleared after.
        Antigravity.configure(clientSecret: "render-only")
        #expect(Antigravity.available)
        r.save("v21-gemini-add", AddAccountSheet(sheet: .constant(nil)), width: 393, height: 620, background: .black)
        r.save("v21-gemini-empty", EmptyStateView(sheet: .constant(nil)), width: 393, height: 760, background: .black)
        Antigravity.configure(clientSecret: nil)
        var state = AccountState(); state.lastKind = "invalidResponse"; state.lastMessage = "Reply held 1 of 3 groups"
        if let n = Notice.forAccount(label: "Gemini", state: state, snapshot: nil, nowMs: now.ms, provider: .antigravity) {
            r.save("v21-gemini-notice-h", NoticeView(notice: n), width: 393, height: 190, background: .black)
        }
    }
}
