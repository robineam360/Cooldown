// Ported from CooldownMac tests/ccooldown-core-tests/DeviceCodeCopyTests.swift at 2bfbdfd (CCRM-94 (iOS App)).
import Foundation
import Testing

@testable import CooldownCore

// CCM-76 [Accounts]: pins the device-code sheet's six states, ported from the Android reference
// implementation's own test suite (`DeviceCodeCopyTest.kt`). The copy and the state table are
// pure for the same reason `ErrorTaxonomy`'s are: a sheet with six branches is exactly the kind
// of thing where a state ships unobserved and only checkpoint 3b's device pass in a real render
// would catch it by eye.

@Suite("CCM-76 [Accounts] device-code copy")
struct DeviceCodeCopyTests {

    @Test("every stage says what happened")
    func everyStageHasABody() {
        for stage in DeviceCodeStage.allCases {
            let body = DeviceCodeCopy.body(for: stage)
            #expect(!body.trimmingCharacters(in: .whitespaces).isEmpty, "\(stage) has no body")
            // Sentences, not fragments: each state has to stand on its own. The in-flight state
            // ends in an ellipsis, which is the same claim about being finished.
            let last = body.trimmingCharacters(in: .whitespacesAndNewlines).last
            #expect(last == "." || last == "\u{2026}", "\(stage) isn't a sentence")
        }
    }

    @Test("only the waiting state shows a code")
    func onlyWaitingShowsCode() {
        let shown = Set(DeviceCodeStage.allCases.filter { DeviceCodeCopy.showsCode(for: $0) })
        #expect(shown == [.waiting])
    }

    @Test("the retry button appears only where retrying is the answer")
    func retryButtonAppearsOnlyWhereItHelps() {
        #expect(DeviceCodeCopy.primaryLabel(for: .starting) == nil)
        #expect(DeviceCodeCopy.primaryLabel(for: .waiting) == nil)
        #expect(DeviceCodeCopy.primaryLabel(for: .expired) == "Get a new code")
        #expect(DeviceCodeCopy.primaryLabel(for: .denied) == "Get a new code")
        #expect(DeviceCodeCopy.primaryLabel(for: .failed) == "Get a new code")
    }

    @Test("unavailable offers nothing to retry and names the fallback")
    func unavailableIsADeadEnd() {
        // Unavailable is the one dead end: OpenAI has the flow switched off, so another code is
        // not the answer and offering one would be a lie about what would happen.
        #expect(DeviceCodeCopy.primaryLabel(for: .unavailable) == nil)
        #expect(DeviceCodeCopy.body(for: .unavailable).contains("browser fallback"))
        #expect(!DeviceCodeCopy.showsCode(for: .unavailable))
    }

    @Test("denied names the status when there is one and reads without it")
    func deniedDetail() {
        #expect(
            DeviceCodeCopy.body(for: .denied, detail: "HTTP 400")
                == "That sign-in didn't go through (HTTP 400). Start again with a new code."
        )
        #expect(
            DeviceCodeCopy.body(for: .denied, detail: nil)
                == "That sign-in didn't go through. Start again with a new code."
        )
    }

    @Test("a start failure carries the network's own sentence")
    func failedDetail() {
        #expect(DeviceCodeCopy.body(for: .failed, detail: "timeout").contains("timeout"))
        // …and still reads when there is nothing to add.
        #expect(!DeviceCodeCopy.body(for: .failed, detail: nil).isEmpty)
    }

    @Test("expired says how long a code lasts")
    func expiredNamesTheRealLifetime() {
        #expect(DeviceCodeCopy.body(for: .expired).contains("15 minutes"))
    }
}

// CCM-124 [Accounts]: Android CCBG-33's note, verbatim.
@Test("the prerequisite note shows on waiting, expired and denied only, in Android's words")
func deviceCodePrerequisiteHint() {
    for stage in [DeviceCodeStage.waiting, .expired, .denied] {
        #expect(DeviceCodeCopy.hint(for: stage)?.hasSuffix("Work accounts need their admin to allow it.") == true)
    }
    for stage in [DeviceCodeStage.starting, .unavailable, .failed] {
        #expect(DeviceCodeCopy.hint(for: stage) == nil)
    }
    #expect(DeviceCodeCopy.hint(for: .waiting) == "First time? Turn on device code sign-in in ChatGPT → Settings → "
        + "Security, or the code won't be accepted. Work accounts need their admin to allow it.")
    #expect(DeviceCodeCopy.hint(for: .expired) == "If ChatGPT wouldn't accept the code, turn on device code sign-in "
        + "in ChatGPT → Settings → Security, then get a new code. Work accounts need their admin to allow it.")
    #expect(DeviceCodeCopy.securitySettingsURL == "https://chatgpt.com/security-settings")
    #expect(DeviceCodeCopy.securityLink == "Open ChatGPT security settings")
}

// The history-cleared line test compares against the Mac sign-in window's copy (SignInCopy), which stays on
// the desktop; the iOS sheet's wording comes from the approved wireframes.
