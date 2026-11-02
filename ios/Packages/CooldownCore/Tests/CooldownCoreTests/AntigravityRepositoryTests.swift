import Foundation
import Testing

@testable import CooldownCore

/// CCRM-55 (Antigravity Account): the fetch path's Antigravity rules (API-CONTRACT §10.4) against the scripted API.
@Suite("CCRM-55 Antigravity fetch path")
struct AntigravityRepositoryTests {
    @Test("a 200 with no Gemini group is invalidResponse with its evidence, and the last good reading stays")
    func poolOnlyReply() async throws {
        let h = Harness(provider: .antigravity)
        let good = try Fixtures.text("antigravity/pool-used.json")
        h.api.queueUsage(HTTPResult(code: 200, body: good))
        #expect(await h.repo.refresh("teams", manual: false).outcome == .success)
        h.clock.advance(seconds: 600)
        h.api.queueUsage(HTTPResult(code: 200, body: try Fixtures.text("antigravity/ua-rejected.json")))
        let r = await h.repo.refresh("teams", manual: true)
        guard case .invalidResponse = r.outcome else { Issue.record("got \(r.outcome)"); return }
        let state = await h.repo.state("teams")
        #expect(state.lastKind == "invalidResponse" && state.lastMessage.hasPrefix("Reply held "))
        let snap = try #require(await h.repo.snapshot("teams"))
        #expect(snap.body == good)
        #expect(abs((snap.usage(nowMs: h.clock.now)?.weekly?.percent ?? 0) - 18) < 1e-6)
        #expect(h.api.profileCount == 0)
    }

    @Test("a refresh renews the Google token and keeps the refresh token and project")
    func renew() async throws {
        let h = Harness(provider: .antigravity, expiresAtMs: t0 + 60_000)
        h.api.queueRenewal(HTTPResult(code: 200, body: #"{"access_token":"a1","expires_in":3599}"#))
        h.api.queueUsage(HTTPResult(code: 200, body: try Fixtures.text("antigravity/pro-light-use.json")))
        #expect(await h.repo.refresh("teams", manual: false).outcome == .success)
        let creds = try #require(h.creds.load(profileKey: "teams"))
        #expect(creds.accessToken == "a1" && creds.refreshToken == "r0" && creds.provider == .antigravity)
    }
}
