// Ported from CooldownMac sources/ccooldown-core/UsageRepository.swift (FloorKind and FetchOutcome only) at 2bfbdfd
// (CCRM-94 (iOS App)); owned here from now on. Spec core BEHAVIOR-SPEC §10.
import Foundation

/// Which floor refused a fetch. Worth distinguishing in the status line: an activity poll
/// turned away with "wait 245s" would read as a bug against the 180-second rate-limit floor.
public enum FloorKind: String, Sendable, Equatable, Codable {
    case rateLimit
    case activity

    var label: String {
        switch self {
        case .rateLimit: "rate-limit floor"
        case .activity: "activity floor"
        }
    }
}

/// Exactly one of these per fetch. BEHAVIOR-SPEC §10.
public enum FetchOutcome: Sendable, Equatable {
    case success
    case tooSoon(secondsLeft: Int64, floor: FloorKind)
    case backedOff(minutesLeft: Int64)
    case rateLimited
    case authNeeded
    case noCredentials
    /// CCM-69 [Panel]: a thrown transport failure (`URLError`, DNS, TLS, …) from the `catch`
    /// block around the usage fetch — the associated string is `error.localizedDescription`.
    case network(String)
    /// CCM-69 [Panel]: any HTTP status the fetch doesn't otherwise recognise (not 200/401/403/
    /// 429) — usually a 5xx. The associated value is the raw status code.
    case server(code: Int)
    /// CCM-69 [Panel]: a 200 whose body `UsageParser` can't read — the schema moved. Not a
    /// success and not a network error either.
    case invalidResponse(String)
    /// The internal fallback for anything none of the above categorise — a programmer error,
    /// or a sign-in failure (`SignInResult` reuses this case; see `completeSignIn`).
    case error(String)
    /// CCM-67 [Readout]: the usage endpoint refused this profile with
    /// `oauth_not_allowed_for_organization` — Free plan, most often, but an org admin can
    /// disable OAuth app access for any plan, so the tag alone never rules this out. The
    /// associated string is the exact copy to show, chosen by `parkedCopy(planTag:)` from
    /// whatever the plan endpoint most recently confirmed. Terminal until the plan (or the
    /// refusal) changes — see BEHAVIOR-SPEC §10.1.
    case plan(String)
    /// CCM-105 [Accounts] decision 2: this profile's ONLINE source is switched off. The gate
    /// lives inside `UsageRepository.fetch` (see `setOnlineDisabled`), so this is returned by
    /// `refresh`/`refreshAll`/a manual fetch alike, with no network call and no state change —
    /// the cache and history stay exactly as they were. `message` carries the exact copy
    /// checkpoint 3's popover refresh note uses (build plan decision 9), so both read from one
    /// literal rather than two that could drift.
    case onlineDisabled
    /// CCM-136 [Readout]: another attempt for this profile is already on the network — no
    /// request, no state change.
    case inFlight
    /// CCM-136 [Readout]: the attempt's credentials changed underneath it (sign-out, sign-in, a
    /// rotated or vanished discovered login). It wrote nothing: no state, no history point, no
    /// session record, no reset ping, no Keychain save.
    case superseded

    /// What the status line shows.
    public var message: String {
        switch self {
        case .success: "OK"
        case .tooSoon(let seconds, let floor): "Skipped — wait \(seconds)s (\(floor.label))"
        case .backedOff(let minutes): "Backing off after 429 — next try in ~\(minutes)m"
        case .rateLimited: "Rate limited (429) — backing off"
        case .authNeeded: "Re-auth needed — sign in again"
        case .noCredentials: "No account — open settings"
        case .network(let detail): "Network: \(detail)"
        case .server(let code): "HTTP \(code)"
        case .invalidResponse: "Error: unrecognized response shape"
        case .error(let detail): "Error: \(detail)"
        case .plan(let copy): copy
        case .onlineDisabled: "Online is off for this account"
        case .inFlight: "Already checking"
        case .superseded: "Superseded"
        }
    }

    /// The exact copy CCM-67 [Readout] specifies for a Free-plan refusal.
    public static let freePlanCopy =
        "Claude doesn't report usage on the Free plan — upgrade to Pro, Max or Team."

    /// The copy for a parked profile, chosen from whatever plan is currently on record —
    /// Free gets the upgrade-shaped copy; any other known plan names itself (an org can
    /// disable OAuth app access on any plan); an unconfirmed plan says so honestly rather
    /// than guessing a name.
    public static func parkedCopy(planTag: String?) -> String {
        switch planTag {
        case "Free": return freePlanCopy
        case .some(let plan): return "Claude isn't allowing this app to read usage for the \(plan) plan."
        case nil: return "Claude isn't allowing this app to read usage for this account's plan."
        }
    }

    public var isSuccess: Bool { self == .success }
}
