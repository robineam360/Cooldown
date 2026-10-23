// Ported from CooldownMac sources/ccooldown-core/DeviceCodeCopy.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// The device-code sheet's six states and their words (CCM-76 [Accounts]), ported **verbatim**
/// from the already-shipped, tested Android copy (`DeviceCodeCopy.kt`/`DeviceCodeStage` in the
/// sibling app) — this is proven, user-tested English, not to be rephrased.
///
/// Four of the six states (`starting`, `waiting`, `expired`, `denied`) match
/// `design/chatgpt-account-wireframe.html` §4, already approved. `unavailable` (404 on the
/// initial user-code request — OpenAI has device sign-in switched off for this client) and
/// `failed` (couldn't reach OpenAI to start at all) are Android-only additions, found necessary
/// after real usage; both reuse `expired`'s layout (no live code, one retry button — or none,
/// for `unavailable`) with different body text rather than a new layout. Whether/how the two
/// extra states fit the approved wireframe is checkpoint 3b's question to raise with Robin —
/// this checkpoint only ports the Core copy those states need to exist first.
///
/// Pure, so the copy is pinned by a test rather than only by eye — the same reasoning
/// `ErrorTaxonomy` documents for its own table: a sheet with six branches is exactly the kind of
/// thing where a state ships unobserved.
///
/// Every state answers the same two questions in the same order: what happened, and what to
/// tap. `primaryLabel` is nil where there is nothing to retry.
public enum DeviceCodeStage: Sendable, Equatable, CaseIterable {
    /// Asking OpenAI for a code; no code to show yet.
    case starting

    /// A live code is on screen and the poll is running.
    case waiting

    /// Past the 15-minute window without the user finishing.
    case expired

    /// The poll returned a terminal non-2xx. The Codex CLI collapses every one of those into a
    /// single failure with no distinct "denied", so neither does this.
    case denied

    /// HTTP 404 from `/usercode` — OpenAI has the flow switched off for this client.
    case unavailable

    /// Couldn't reach OpenAI to start at all. Not a flow state and not in the wireframe — it
    /// reuses `.expired`'s shape (no live code, one button to try again) with the network's own
    /// sentence, rather than inventing a sixth layout.
    case failed
}

public enum DeviceCodeCopy {

    public static let title = "Sign in to ChatGPT"

    /// Under the granted state's "Fetching your usage…", only when this sign-in wiped the card's
    /// history for a different account (CCM-102 [Accounts], `design/chatgpt-history-cleared-wireframe.html`).
    /// Worded like `SignInCopy.historyCleared`.
    public static let historyCleared =
        "A different ChatGPT account signed in, so this card's old history was cleared."

    /// The instruction above the code, shown only while a code exists.
    public static let instructionPrefix = "Go to"
    public static let instructionSuffix = "and enter this code"

    /// Under the code, next to the countdown.
    public static let waitingNote = "Waiting for you to finish…"

    /// - Parameter detail: the failure's own sentence, used by `.failed` and to name the status
    ///   on `.denied`. Ignored elsewhere.
    public static func body(for stage: DeviceCodeStage, detail: String? = nil) -> String {
        switch stage {
        case .starting:
            return "Asking OpenAI for a code…"
        case .waiting:
            return "Open the page in any browser — it can be a different device — sign in to "
                + "ChatGPT, and enter this code."
        case .expired:
            return "That code expired before it was used. Codes last 15 minutes."
        case .denied:
            let suffix = detail.map { " (\($0))" } ?? ""
            return "That sign-in didn't go through\(suffix). Start again with a new code."
        // The fallback is named, not offered: the browser PKCE route is only built if this ever
        // happens (CCM-76 [Accounts]), and it hasn't.
        case .unavailable:
            return "OpenAI has code sign-in switched off for this client, so there's no code "
                + "to enter. Signing in would need the browser fallback, which this version "
                + "doesn't have. Try again later."
        case .failed:
            let suffix = detail.map { " — \($0)" } ?? ""
            return "Couldn't reach OpenAI to get a code\(suffix)."
        }
    }

    /// The one action worth offering, or nil when there is nothing to retry.
    public static func primaryLabel(for stage: DeviceCodeStage) -> String? {
        switch stage {
        case .starting, .waiting, .unavailable: return nil
        case .expired, .denied, .failed: return "Get a new code"
        }
    }

    /// Whether the code, the countdown and the copy/open buttons are on screen.
    public static func showsCode(for stage: DeviceCodeStage) -> Bool { stage == .waiting }

    // MARK: - The prerequisite note (CCM-124 [Accounts], Android CCBG-33)

    /// OpenAI refuses every device code until "Enable device code sign-in for Codex, Excel,
    /// PowerPoint, and Word" is on in ChatGPT's security settings — off by default, and a
    /// workspace member can't turn it on without their admin. When it's off the poll just waits
    /// out the code, so the sheet says it up front. Android's words, verbatim.
    /// Wireframe: design/device-code-prerequisite-wireframe.html rev B (Fable + Astra).
    public static let securitySettingsURL = "https://chatgpt.com/security-settings"
    public static let securityLink = "Open ChatGPT security settings"

    private static let admin = "Work accounts need their admin to allow it."

    /// The note's words for `stage`, or nil where it has none: before a code exists, and where
    /// the setting can't be the cause (the flow switched off, the network).
    public static func hint(for stage: DeviceCodeStage) -> String? {
        switch stage {
        case .waiting:
            return "First time? Turn on device code sign-in in ChatGPT → Settings → Security, "
                + "or the code won't be accepted. \(admin)"
        case .expired, .denied:
            return "If ChatGPT wouldn't accept the code, turn on device code sign-in in "
                + "ChatGPT → Settings → Security, then get a new code. \(admin)"
        case .starting, .unavailable, .failed:
            return nil
        }
    }
}
