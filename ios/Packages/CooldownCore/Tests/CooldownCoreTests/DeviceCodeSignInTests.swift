// Ported from CooldownMac tests/ccooldown-core-tests/DeviceCodeSignInTests.swift at 2bfbdfd (CCRM-94 (iOS App)).
import Foundation
import Testing

@testable import CooldownCore

// CCM-76 [Accounts]: the device-code flow's pure halves, ported from the Android reference
// implementation's own test suite (`CodexDeviceSignInTest.kt`). The status mapping is the part
// worth pinning: it is read straight off `poll_for_token` in
// `codex-rs/login/src/device_code_auth.rs`, where **403 and 404 both mean "not yet"** — the
// opposite of what 404 means on `/usercode`, which is "device login is switched off entirely".
// Getting those two the wrong way round would either abort every healthy sign-in or poll a dead
// flow for fifteen minutes.

private let expires: Int64 = 1_788_000_900_000 // now + 15 min
private let now: Int64 = 1_788_000_000_000

private func poll(_ status: Int, body: String = "", at: Int64 = now) -> DeviceCodeSignIn.Poll {
    DeviceCodeSignIn.classifyPoll(status: status, body: body, nowMs: at, expiresAtMs: expires)
}

@Suite("CCM-76 [Accounts] device-code sign-in")
struct DeviceCodeSignInTests {

    @Test("403 and 404 mean pending")
    func pendingStatuses() {
        #expect(poll(403) == .pending)
        #expect(poll(404) == .pending)
    }

    @Test("404 on start means unavailable — the opposite of 404 on poll, right above")
    func startFourOhFourIsUnavailable() {
        // Same status code, two different endpoints, opposite meanings: on /usercode it means
        // the flow is switched off entirely; on /token (the test right above) it means "not yet,
        // keep polling". Sitting these two tests next to each other so they can't drift back
        // together.
        let result = DeviceCodeSignIn.classifyStart(status: 404, body: "", profileKey: "p1", nowMs: now)
        #expect(result == .failure(.unavailable))
    }

    @Test("start's other non-2xx and unparseable-2xx bodies are their own failures")
    func startOtherFailures() {
        #expect(
            DeviceCodeSignIn.classifyStart(status: 500, body: "", profileKey: "p1", nowMs: now)
                == .failure(.httpError(status: 500))
        )
        #expect(
            DeviceCodeSignIn.classifyStart(status: 200, body: "not json", profileKey: "p1", nowMs: now)
                == .failure(.missingCode)
        )
        #expect(
            DeviceCodeSignIn.classifyStart(status: 200, body: #"{"device_auth_id":"dev_1"}"#, profileKey: "p1", nowMs: now)
                == .failure(.missingCode)
        )
    }

    @Test("start's happy path returns the same Started parseStarted would")
    func startSuccess() {
        let body = #"{"device_auth_id":"dev_1","user_code":"ABCD","interval":"5"}"#
        let expected = DeviceCodeSignIn.parseStarted(body: body, profileKey: "p1", nowMs: now)!
        let result = DeviceCodeSignIn.classifyStart(status: 200, body: body, profileKey: "p1", nowMs: now)
        #expect(result == .success(expected))
    }

    @Test("pending past the fifteen-minute cap becomes expired")
    func expiryCap() {
        #expect(poll(403, at: expires) == .expired)
        #expect(poll(404, at: expires + 1) == .expired)
    }

    @Test("every other status is terminal")
    func terminalStatuses() {
        // The CLI collapses these into one hard failure, so we do too — the sheet says "start
        // again" rather than inventing a distinction the server doesn't make.
        #expect(poll(400) == .denied(status: 400))
        #expect(poll(401) == .denied(status: 401))
        #expect(poll(410) == .denied(status: 410))
        #expect(poll(500) == .denied(status: 500))
    }

    @Test("a 2xx carrying the grant hands back the server's own PKCE verifier")
    func grant() {
        let body = #"{"authorization_code":"ac_123","code_challenge":"cc","code_verifier":"cv_456"}"#
        #expect(poll(200, body: body) == .granted(authorizationCode: "ac_123", codeVerifier: "cv_456"))
    }

    @Test("a 2xx missing the grant fields is treated as pending, not as a denial")
    func shapeChangeIsPending() {
        // A shape change must not abort a sign-in that may still be alive; the fifteen-minute
        // cap bounds the retrying either way.
        #expect(poll(200, body: #"{"status":"pending"}"#) == .pending)
        #expect(poll(200, body: "not json") == .pending)
        #expect(poll(200, body: "{}", at: expires) == .expired)
    }

    @Test("the user code response is read with both spellings and a string interval")
    func parseUserCode() {
        let body = #"{"device_auth_id":"dev_1","user_code":"ABCD-EFGH","interval":"5"}"#
        let started = DeviceCodeSignIn.parseStarted(body: body, profileKey: "p3", nowMs: now)
        #expect(started?.deviceAuthId == "dev_1")
        #expect(started?.userCode == "ABCD-EFGH")
        #expect(started?.intervalSec == 5)
        #expect(started?.profileKey == "p3")
        #expect(started?.expiresAtMs == now + DeviceCodeSignIn.maxWaitMs)
        #expect(started?.verifyUrl == "https://auth.openai.com/codex/device")

        let alias = #"{"device_auth_id":"dev_2","usercode":"WXYZ","interval":"3"}"#
        #expect(DeviceCodeSignIn.parseStarted(body: alias, profileKey: "p3", nowMs: now)?.userCode == "WXYZ")
    }

    @Test("an absent, zero, negative, non-numeric or over-max interval never becomes a spin")
    func intervalEdgeCases() {
        // `interval` is effectively optional and would otherwise arrive as 0.
        let missing = #"{"device_auth_id":"dev_1","user_code":"ABCD"}"#
        #expect(DeviceCodeSignIn.parseStarted(body: missing, profileKey: "p1", nowMs: now)!.intervalSec >= 1)

        let zero = #"{"device_auth_id":"dev_1","user_code":"ABCD","interval":"0"}"#
        #expect(DeviceCodeSignIn.parseStarted(body: zero, profileKey: "p1", nowMs: now)?.intervalSec == 1)

        let negative = #"{"device_auth_id":"dev_1","user_code":"ABCD","interval":"-5"}"#
        #expect(DeviceCodeSignIn.parseStarted(body: negative, profileKey: "p1", nowMs: now)?.intervalSec == 1)

        let nonNumeric = #"{"device_auth_id":"dev_1","user_code":"ABCD","interval":"soon"}"#
        #expect(DeviceCodeSignIn.parseStarted(body: nonNumeric, profileKey: "p1", nowMs: now)?.intervalSec == 5)

        let overMax = #"{"device_auth_id":"dev_1","user_code":"ABCD","interval":"600"}"#
        #expect(DeviceCodeSignIn.parseStarted(body: overMax, profileKey: "p1", nowMs: now)?.intervalSec == 60)
    }

    @Test("a response with no code is not a started flow")
    func missingCode() {
        #expect(DeviceCodeSignIn.parseStarted(body: #"{"device_auth_id":"dev_1"}"#, profileKey: "p1", nowMs: now) == nil)
        #expect(DeviceCodeSignIn.parseStarted(body: "not json", profileKey: "p1", nowMs: now) == nil)
    }

    @Test("DeviceCodeSignInError.unavailable exists for the one status that means the flow is switched off")
    func unavailableError() {
        // `request_user_code` singles out 404 on /usercode: "device code login is not enabled
        // for this Codex server." Only the caller of `postDeviceUserCode` raises it — never the
        // poll classification, which treats a poll-time 404 as pending instead.
        let error = DeviceCodeSignIn.DeviceCodeSignInError.unavailable
        #expect(error.errorDescription?.contains("switched on") == true)
    }

    @Test("the three endpoints are the ones the CLI builds")
    func endpoints() {
        #expect(DeviceCodeSignIn.userCodeURL == "https://auth.openai.com/api/accounts/deviceauth/usercode")
        #expect(DeviceCodeSignIn.pollURL == "https://auth.openai.com/api/accounts/deviceauth/token")
        #expect(DeviceCodeSignIn.redirectURI == "https://auth.openai.com/deviceauth/callback")
        #expect(DeviceCodeSignIn.clientID == "app_EMoamEEZ73f0CkXaXp7hrann")
    }

    @Test("the exchange form body has the exact five parameters, in order, form-encoded")
    func exchangeFormBodyShape() {
        let body = DeviceCodeSignIn.exchangeFormBody(authorizationCode: "ac 1:2", codeVerifier: "cv_456")
        #expect(
            body == "grant_type=authorization_code"
                + "&code=ac+1%3A2"
                + "&redirect_uri=https%3A%2F%2Fauth.openai.com%2Fdeviceauth%2Fcallback"
                + "&client_id=app_EMoamEEZ73f0CkXaXp7hrann"
                + "&code_verifier=cv_456"
        )
    }

    // MARK: - DeviceSignInPendingStore

    private func tempStorage() -> Storage {
        let dir = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
        return Storage(directory: dir)
    }

    @Test("the pending store survives a save/load round trip, and clears cleanly")
    func pendingStoreRoundTrip() {
        let store = DeviceSignInPendingStore(storage: tempStorage())
        #expect(store.load() == nil)

        let started = DeviceCodeSignIn.Started(
            deviceAuthId: "dev_1", userCode: "ABCD-EFGH", intervalSec: 5,
            verifyUrl: DeviceCodeSignIn.verifyURL, expiresAtMs: now + DeviceCodeSignIn.maxWaitMs,
            profileKey: "personal"
        )
        store.save(started)
        #expect(store.load() == started)

        store.clear()
        #expect(store.load() == nil)
    }
}
