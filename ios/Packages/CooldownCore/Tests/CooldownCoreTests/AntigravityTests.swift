import Foundation
import Testing

@testable import CooldownCore

/// CCRM-55 (Antigravity Account), RUNBOOK-v2.0 Step 6c: the parser and the plan on every case in
/// `spec/core/fixtures/antigravity/cases.json` (read directly, as Android's test reads it), the User-Agent's versioned
/// CLI token, the paste rules, and the secret's absence hiding the provider.
@Suite("CCRM-55 Antigravity", .serialized)
struct AntigravityTests {

    private func cases(_ key: String) throws -> [[String: Any]] {
        let list = try #require(try Fixtures.json("antigravity/cases.json")[key] as? [[String: Any]])
        #expect(!list.isEmpty)
        return list
    }

    private func checkWindow(_ w: UsageWindow?, _ expected: [String: Any]?, _ label: String) {
        guard let expected else { #expect(w == nil, "\(label): expected no window"); return }
        guard let w else { Issue.record("\(label): window missing"); return }
        #expect(abs((w.percent ?? -1) - (expected.num("usedPct") ?? -2)) <= 1e-6, "\(label): used \(w.percent ?? -1)")
        let reset = expected.str("resetsAt").flatMap(Timestamps.parseInstantMs)
        #expect(w.resetsAtMs == reset, "\(label): reset \(String(describing: w.resetsAtMs))")
    }

    @Test("retrieveUserQuotaSummary, every fixture case")
    func parse() throws {
        for c in try cases("parse") {
            let file = try #require(c.str("file"))
            let body = try Fixtures.text("antigravity/\(file)")
            let expected = try #require(c.dict("expected"))
            let got = AntigravityUsageParser.parse(body, nowMs: try #require(c.i64("nowMs")))
            switch expected.str("status") {
            case "ok":
                let data = try #require(got, "\(file): parsed nothing")
                checkWindow(data.session, expected.dict("session"), "\(file) session")
                checkWindow(data.weekly, expected.dict("weekly"), "\(file) weekly")
                let caps = expected["modelCaps"] as? [[String: Any]] ?? []
                #expect(data.modelCaps.map(\.modelName) == caps.map { $0.str("name") ?? "" }, "\(file): caps")
                for (cap, e) in zip(data.modelCaps, caps) { checkWindow(cap.window, e, "\(file) \(cap.modelName)") }
                let pools = expected["pools"] as? [[String: Any]] ?? []
                #expect(data.pools.map(\.name) == pools.map { $0.str("name") ?? "" }, "\(file): pools")
                for (pool, e) in zip(data.pools, pools) {
                    checkWindow(pool.session, e.dict("session"), "\(file) pool 5h")
                    checkWindow(pool.weekly, e.dict("weekly"), "\(file) pool weekly")
                    #expect(pool.shown == e.flag("shown"), "\(file): pool shown")
                }
            case "invalidResponse":
                // Valid JSON, but no Gemini group: never 0% or 100%.
                #expect(got == nil, "\(file)")
                #expect(AntigravityUsageParser.groupCount(body) <= 1, "\(file)")
            case "unparseable":
                #expect(got == nil, "\(file)")
            default:
                Issue.record("\(file): unknown status")
            }
        }
    }

    @Test("loadCodeAssist: plan from paidTier.id, and the project, every fixture case")
    func plan() throws {
        for c in try cases("plan") {
            let file = try #require(c.str("file"))
            let assist = try #require(Antigravity.parseCodeAssist(try Fixtures.text("antigravity/\(file)")))
            let expected = try #require(c.dict("expected"))
            #expect(assist.plan == expected.str("plan"), "\(file)")
            #expect(assist.project == expected.str("project"), "\(file)")
        }
    }

    @Test("a cached reply is read with its own fetch time, not now")
    func cachedReply() throws {
        let c = try #require(try cases("parse").first { $0.str("file") == "not-started.json" })
        let fetchedAt = try #require(c.i64("nowMs"))
        let snapshot = Snapshot(profileKey: "g", provider: .antigravity, body: try Fixtures.text("antigravity/not-started.json"),
                                fetchedAtMs: fetchedAt, lastAttemptAtMs: fetchedAt, lastKind: "success", lastMessage: "", plan: "Pro")
        // An hour later, the untouched windows still read as not started.
        let data = try #require(snapshot.usage(nowMs: fetchedAt + 3_600_000))
        #expect(data.session?.resetsAtMs == nil && data.weekly?.resetsAtMs == nil)
    }

    @Test("the User-Agent carries a versioned antigravity/cli token and names iOS")
    func userAgent() {
        let ua = Antigravity.userAgent(version: "2.0")
        #expect(ua.range(of: #"antigravity/cli/\d"#, options: .regularExpression) != nil)
        #expect(ua.hasPrefix("Cooldown/2.0 ") && ua.contains("os_type=ios"))
        let request = try? APIClient.antigravityRequest(Antigravity.quotaURL, accessToken: "t", body: ["project": "p"])
        #expect(request?.value(forHTTPHeaderField: "User-Agent")?.contains("antigravity/cli/\(Antigravity.cliVersion)") == true)
        #expect(request?.httpMethod == "POST")
    }

    @Test("paste: a bare 4/ code, a callback address with the right state, a different sign-in, junk")
    func paste() {
        #expect(AntigravitySignIn.parsePaste(" 4/0AVMBt8kQ2xLpR7wZ\n", expectedState: "s") == .code("4/0AVMBt8kQ2xLpR7wZ"))
        #expect(AntigravitySignIn.parsePaste("https://antigravity.google/oauth-callback?state=s&code=4/0AVMBt8kQ2xLpR7wZ",
                                             expectedState: "s") == .code("4/0AVMBt8kQ2xLpR7wZ"))
        #expect(AntigravitySignIn.parsePaste("https://antigravity.google/oauth-callback?code=4/0AVMBt8kQ2xLpR7wZ",
                                             expectedState: "s") == .code("4/0AVMBt8kQ2xLpR7wZ"))
        #expect(AntigravitySignIn.parsePaste("https://antigravity.google/oauth-callback?state=old&code=4/0AVMBt8kQ2xLpR7wZ",
                                             expectedState: "s") == .otherSignIn)
        #expect(AntigravitySignIn.parsePaste("hello", expectedState: "s") == .unreadable)
        #expect(AntigravitySignIn.parsePaste("4/x", expectedState: "s") == .unreadable)
        #expect(AntigravitySignIn.parsePaste("", expectedState: "s") == .unreadable)
    }

    @Test("the authorize URL carries the CLI's client, the hosted redirect, PKCE S256, offline and consent")
    func authorize() throws {
        let (url, pending) = AntigravitySignIn.begin()
        let items = try #require(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems)
        func v(_ n: String) -> String? { items.first { $0.name == n }?.value }
        #expect(v("client_id") == Antigravity.clientID && v("redirect_uri") == Antigravity.redirectURI)
        #expect(v("code_challenge_method") == "S256" && v("code_challenge") == AntigravitySignIn.challenge(for: pending.verifier))
        #expect(v("access_type") == "offline" && v("prompt") == "consent" && v("state") == pending.state)
        #expect(v("scope")?.contains("cloud-platform") == true)
    }

    @Test("no secret configured: the provider is unavailable (the Gemini row hidden)")
    func secret() {
        Antigravity.configure(clientSecret: nil)
        #expect(!Antigravity.available)
        Antigravity.configure(clientSecret: "  ")
        #expect(!Antigravity.available)
        Antigravity.configure(clientSecret: "x")
        #expect(Antigravity.available)
        Antigravity.configure(clientSecret: nil)
    }

    @Test("token reply: email from the id_token; a refresh keeps the refresh token and the cached project")
    func token() throws {
        let payload = Data(#"{"email":"you@gmail.com"}"#.utf8).base64EncodedString().replacingOccurrences(of: "=", with: "")
        let body = #"{"access_token":"a","refresh_token":"r","expires_in":3599,"id_token":"h.\#(payload).s"}"#
        let grant = try #require(TokenGrant.antigravity(body: body, previous: nil, nowMs: 1000))
        #expect(grant.email == "you@gmail.com" && grant.credentials.provider == .antigravity)
        let withProject = grant.withAntigravity(project: "p", plan: "Pro")
        #expect(withProject.credentials.accountId == "p" && withProject.plan == "Pro")
        let renewed = try #require(TokenGrant.antigravity(body: #"{"access_token":"b","expires_in":3599}"#,
                                                          previous: withProject.credentials, nowMs: 2000))
        #expect(renewed.credentials.refreshToken == "r" && renewed.credentials.accountId == "p")
    }
}
