import CooldownCore
import Foundation
import Testing

@testable import Cooldown

/// CCRM-94 (iOS App) parity wireframes rev B §2-3: the fetch-error notices, the expiring notice, and the account
/// page's status line and condition rows.
@Suite("Notices and status lines (parity rev B)")
struct NoticeParityTests {
    let now: Int64 = 1_790_000_000_000
    let utc = TimeZone(identifier: "UTC")!
    let day: Int64 = 86_400_000

    func state(_ kind: String, message: String = "") -> AccountState {
        var s = AccountState(); s.lastKind = kind; s.lastMessage = message; s.lastAttemptAtMs = now - 12 * 60_000; return s
    }
    func snapshot(ageMs: Int64) -> Snapshot {
        Snapshot(profileKey: "k", provider: .claude, body: "{}", fetchedAtMs: now - ageMs, lastAttemptAtMs: now, lastKind: "success", lastMessage: "", plan: "Pro")
    }
    func notice(_ s: AccountState, snap: Snapshot? = nil, provider: Provider = .claude, added: Int64? = nil) -> Notice? {
        Notice.forAccount(label: "Teams", state: s, snapshot: snap, nowMs: now, provider: provider, addedAtMs: added, use24h: false, timeZone: utc)
    }

    @Test("each fetch-error kind: tone, title, evidence, button")
    func kinds() throws {
        let net = try #require(notice(state("network", message: "The Internet connection appears to be offline.")))
        #expect(net.title == "Couldn’t reach Anthropic — check your connection, or see if it’s them.")
        #expect(net.tone == .warning && net.action == "Check Anthropic status" && net.actionURL == Provider.claude.statusPageURL)
        #expect(net.evidence == "The Internet connection appears to be offline." && net.body.isEmpty)

        let srv = try #require(notice(state("server", message: "HTTP 503"), provider: .chatgpt))
        #expect(srv.title == "OpenAI’s server errored — usually theirs, usually brief.")
        #expect(srv.action == "Check OpenAI status" && srv.actionURL == Provider.chatgpt.statusPageURL && srv.evidence == "HTTP 503")

        let bad = try #require(notice(state("invalidResponse", message: "missing five_hour")))
        #expect(bad.title.hasPrefix("The server answered in a shape this app doesn’t know") && bad.action == nil && bad.tone == .warning)

        let err = try #require(notice(state("error", message: "boom")))
        #expect(err.title == "Something unexpected went wrong in the app." && err.tone == .error && err.evidence == "boom")

        var limited = state("rateLimited", message: "HTTP 429"); limited.backoffUntilMs = now + 40 * 60_000
        let rl = try #require(notice(limited))
        #expect(rl.title == "Rate limited — backing off, retries on its own." && rl.action == nil)
        #expect(rl.evidence?.hasPrefix("next try ~") == true)
        #expect(notice(state("backedOff"))?.title.hasPrefix("Rate limited") == true)
        // Not failures: nothing to say.
        for kind in ["success", "tooSoon", "noCredentials", "skipped", ""] { #expect(notice(state(kind)) == nil) }
    }

    @Test("precedence: plan › sign-in › fetch error › stale › expiring; a fetch error absorbs stale")
    func precedence() throws {
        let old = snapshot(ageMs: 7 * 3_600_000)
        var plan = state("plan"); plan.reauthNeeded = true
        #expect(notice(plan, snap: old)?.title.hasPrefix("Usage isn’t available") == true)
        var auth = state("network"); auth.reauthNeeded = true
        #expect(notice(auth, snap: old)?.title == "Teams: Sign-in stopped working")
        let net = try #require(notice(state("network", message: "offline"), snap: old))
        #expect(net.title.hasPrefix("Couldn’t reach") && net.evidence?.hasPrefix("offline · last reading ") == true)
        #expect(notice(state("network", message: "offline"), snap: snapshot(ageMs: 60_000))?.evidence == "offline")
        // Stale alone, then expiring only when nothing else applies.
        let added = now - 25 * day
        #expect(notice(state("success"), snap: old, added: added)?.title.hasPrefix("Stale") == true)
        let exp = try #require(notice(state("success"), snap: snapshot(ageMs: 60_000), added: added))
        #expect(exp.title == "Sign-in expires in 5d" && exp.tone == .quiet && exp.action == "Sign in again" && !exp.dimsCards)
    }

    @Test("expiring: Claude only, within 7 days, never when sign-in is already broken")
    func expiring() {
        let edge = now - (30 * day - 7 * day)               // 7 d left exactly
        #expect(SignInExpiry.soonLeftMs(provider: .claude, addedAtMs: edge - 60_000, reauthNeeded: false, nowMs: now) != nil)   // 7 d − 1 min left
        #expect(SignInExpiry.soonLeftMs(provider: .claude, addedAtMs: edge + 60_000, reauthNeeded: false, nowMs: now) == nil)   // 7 d + 1 min left
        #expect(SignInExpiry.soonLeftMs(provider: .claude, addedAtMs: now - 29 * day, reauthNeeded: true, nowMs: now) == nil)
        #expect(SignInExpiry.soonLeftMs(provider: .chatgpt, addedAtMs: now - 29 * day, reauthNeeded: false, nowMs: now) == nil)
        #expect(SignInExpiry.soonLeftMs(provider: .antigravity, addedAtMs: now - 29 * day, reauthNeeded: false, nowMs: now) == nil)
        #expect(SignInExpiry.soonLeftMs(provider: .claude, addedAtMs: nil, reauthNeeded: false, nowMs: now) == nil)
        #expect(SignInExpiry.soonLeftMs(provider: .claude, addedAtMs: now - 40 * day, reauthNeeded: false, nowMs: now) == 0)   // past: due
    }

    @Test("status line: checking, not checked yet, checked ago, renews in / due at next check; ChatGPT and broken: no clause")
    func statusLine() {
        let s = state("success")
        func line(_ p: Provider, _ st: AccountState, exp: Int64?, busy: Bool = false) -> String {
            AccountStatus.line(provider: p, state: st, tokenExpiresAtMs: exp, busy: busy, nowMs: now)
        }
        #expect(line(.claude, s, exp: nil, busy: true) == "Checking…")
        #expect(line(.claude, AccountState(), exp: nil) == "Not checked yet")
        #expect(line(.claude, s, exp: nil) == "Checked 12m ago")
        #expect(line(.claude, s, exp: now + 6 * 3_600_000 + 4 * 60_000) == "Checked 12m ago · renews in 6h 4m")
        #expect(line(.antigravity, s, exp: now + 3_600_000) == "Checked 12m ago · renews in 1h")
        #expect(line(.claude, s, exp: now - 1) == "Checked 12m ago · renewal due at next check")
        #expect(line(.claude, s, exp: 0) == "Checked 12m ago")
        #expect(line(.chatgpt, s, exp: now + 3_600_000) == "Checked 12m ago")
        var broken = s; broken.reauthNeeded = true
        #expect(line(.claude, broken, exp: now + 3_600_000) == "Checked 12m ago")
    }

    @Test("condition rows in Android's order, with the provider's own wording")
    func conditions() {
        var s = state("plan"); s.plan = "Free"; s.reauthNeeded = true; s.backoffUntilMs = now + 12 * 60_000
        let rows = AccountStatus.conditions(provider: .claude, state: s, addedAtMs: nil, nowMs: now)
        #expect(rows.map(\.text) == [
            "Free plan: Claude doesn’t report usage — upgrade to Pro, Max or Team to see numbers.",
            "Sign-in stopped working — sign in again below.",
            "Rate-limited — next try in 12m"])
        #expect(rows.map(\.tone) == [.red, .red, .red])
        #expect(AccountStatus.conditions(provider: .chatgpt, state: state("authNeeded"), addedAtMs: nil, nowMs: now).map(\.text)
                == ["Sign-in stopped working — sign in with a code below."])
        let exp = AccountStatus.conditions(provider: .claude, state: state("success"), addedAtMs: now - 25 * day, nowMs: now)
        #expect(exp == [.init(text: "Sign-in expires in 5d — sign in again to keep polling going.", tone: .amber)])
        #expect(AccountStatus.conditions(provider: .claude, state: state("success"), addedAtMs: now - 5 * day, nowMs: now).isEmpty)
    }

    @Test("Settings list word: sign in again › expires in › signed in")
    func listWord() {
        var broken = state("success"); broken.reauthNeeded = true
        #expect(AccountStatus.listWord(provider: .claude, state: broken, addedAtMs: now - 29 * day, nowMs: now).text == "Sign in again")
        #expect(AccountStatus.listWord(provider: .claude, state: state("success"), addedAtMs: now - 25 * day, nowMs: now).text == "Expires in 5d")
        let ok = AccountStatus.listWord(provider: .claude, state: state("success"), addedAtMs: now - 5 * day, nowMs: now)
        #expect(ok.text == "Signed in" && ok.tone == nil)
    }
}

/// CCRM-94 (iOS App) parity wireframes rev B §1: the rename sheet's rules.
@Suite("Rename (parity rev B)")
struct RenameParityTests {
    @Test("the e-mail suggestion: local part, or the domain's first label when another account shares the local part")
    func suggestion() {
        #expect(AccountEmail.suggestion(email: "robin@eam360.com", others: [], current: "Claude") == "robin")
        #expect(AccountEmail.suggestion(email: "robin@eam360.com", others: ["Robin@gmail.com"], current: "Claude") == "eam360")
        #expect(AccountEmail.suggestion(email: "robin@eam360.com", others: [], current: " Robin ") == nil)   // already the name
        #expect(AccountEmail.suggestion(email: nil, others: [], current: "Claude") == nil)
        #expect(AccountEmail.suggestion(email: "no-at-sign", others: [], current: "Claude") == nil)
        #expect(AccountEmail.suggestion(email: "averyveryverylongname@x.io", others: [], current: "Claude")?.count == 16)
    }

    @Test("a name is capped at 16 characters; blank goes back to the default")
    @MainActor func cap() {
        let model = AppModel(demo: .standard)
        let key = model.accounts[0].key
        model.rename(key, to: String(repeating: "x", count: 20))
        #expect(model.accounts[0].label.count == 16)
        model.rename(key, to: "   ")
        #expect(model.accounts[0].label == model.defaultLabel(for: key))
    }

    @Test("a removal of the last account leaves none (the app returns to its first-launch screen)")
    @MainActor func removeLast() async {
        let model = AppModel(demo: .standard)
        for a in model.accounts { await model.remove(a.key) }
        #expect(model.accounts.isEmpty)
    }
}

/// CCRM-94 (iOS App) parity rev B: the judge's two blockers — signing in again replaces the account's credentials, and the
/// expiry phrase never prints "in any moment".
@Suite("Sign in again and expiry wording")
struct SignInAgainTests {
    @Test("once the estimate runs out the phrase says so instead of \"in any moment\"")
    func phrase() {
        #expect(SignInExpiry.phrase(leftMs: 0) == "may expire any moment")
        #expect(SignInExpiry.phrase(leftMs: 5 * 86_400_000 + 3 * 3_600_000) == "expires in 5d 3h")
        let now: Int64 = 1_790_000_000_000
        var s = AccountState(); s.lastKind = "success"; s.lastAttemptAtMs = now
        let n = Notice.forAccount(label: "T", state: s, snapshot: nil, nowMs: now, provider: .claude, addedAtMs: now - 40 * 86_400_000)
        #expect(n?.title == "Sign-in may expire any moment")
        #expect(AccountStatus.listWord(provider: .claude, state: s, addedAtMs: now - 40 * 86_400_000, nowMs: now).text == "Expiring now")
    }

    @Test("the Remove copy adds the first-launch line only for the last account")
    func removeCopy() {
        #expect(RemoveCopy.message(accountCount: 1).contains("only account"))
        #expect(!RemoveCopy.message(accountCount: 2).contains("only account"))
    }

    @Test("a sign-in again targets the account: the model remembers it and offers the provider's sheet")
    @MainActor func target() {
        let model = AppModel(demo: .standard)
        let account = model.accounts[0]
        #expect(model.signInAgain(account) == Sheet.signIn(for: account.provider))
        #expect(model.reauthTarget == account.key)
    }
}
