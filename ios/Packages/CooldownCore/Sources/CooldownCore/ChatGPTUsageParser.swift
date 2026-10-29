// Ported from CooldownMac sources/ccooldown-core/ChatGPTUsageParser.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// Parses `GET https://chatgpt.com/backend-api/wham/usage` into the **same** `UsageData` the
/// Claude parser produces (CCM-76 [Accounts]), so every downstream consumer — projection,
/// alerts, gauges — works unchanged regardless of provider. See
/// Android's ChatGPT provider notes (2026-09-20, maintainer notes) for the reference implementation
/// this ports, and `contract/BEHAVIOR-SPEC.md`'s Provider column for the rules pinned here.
///
/// Unlike `UsageParser` (Claude), an empty-but-well-formed payload is **not** an error: a
/// missing `rate_limit` yields an empty `UsageData` (all nils, no caps, no credits), not `nil`.
/// `nil` is reserved for input that isn't even a JSON object.
public enum ChatGPTUsageParser {

    static let sessionLengthSeconds = 18_000
    static let weeklyLengthSeconds = 604_800

    /// `nil` only for genuinely malformed input — not valid JSON, or not an object at the top
    /// level. Everything else, including a payload with no `rate_limit` at all, parses to a
    /// (possibly empty) `UsageData`.
    ///
    /// `nowMs` is a required parameter, not defaulted to `Date()` — this is otherwise a pure
    /// function (every test fixes it), and a default reading the wall clock would let a future
    /// caller reach for it and get a non-deterministic result by accident. Callers pass
    /// `Int64(Date().timeIntervalSince1970 * 1000)` explicitly.
    public static func parse(_ raw: String, nowMs: Int64) -> UsageData? {
        guard let data = raw.data(using: .utf8),
              let any = try? JSONSerialization.jsonObject(with: data),
              let object = any as? [String: Any]
        else { return nil }
        return parse(root: JSON(object), nowMs: nowMs)
    }

    /// The `plan_type` field alone, mapped through `PlanTag.fromChatGPT`. A separate entry
    /// point from `parse` because plan and usage are read from the same response but consumed
    /// on different cadences (BEHAVIOR-SPEC §9.2).
    public static func planTag(_ raw: String) -> PlanTag? {
        guard let data = raw.data(using: .utf8),
              let any = try? JSONSerialization.jsonObject(with: data),
              let object = any as? [String: Any]
        else { return nil }
        return PlanTag.fromChatGPT(planType: JSON(object)["plan_type"].string)
    }

    static func parse(root: JSON, nowMs: Int64) -> UsageData {
        var session: UsageWindow?
        var weekly: UsageWindow?

        let rateLimit = root["rate_limit"]
        for slot in ["primary_window", "secondary_window"] {
            guard let classified = classifiedWindow(rateLimit[slot], nowMs: nowMs) else { continue }
            switch classified.lengthSeconds {
            case sessionLengthSeconds: session = classified.window
            case weeklyLengthSeconds: weekly = classified.window
            default: break // an unrecognised window length is dropped, never guessed at
            }
        }

        var caps: [ModelCap] = []
        if let list = root["additional_rate_limits"].array {
            for entry in list {
                let item = JSON(entry)
                guard let name = item["limit_name"].nonEmptyString else { continue }
                let entryRateLimit = item["rate_limit"]
                // Model caps are always 7-day windows (BEHAVIOR-SPEC §1); whichever slot
                // carries the 604800-second window is the cap's window, mirroring the
                // top-level classify-by-length rule rather than trusting the slot name.
                for slot in ["primary_window", "secondary_window"] {
                    guard let classified = classifiedWindow(entryRateLimit[slot], nowMs: nowMs),
                          classified.lengthSeconds == weeklyLengthSeconds
                    else { continue }
                    caps.append(ModelCap(modelName: name, window: classified.window))
                }
            }
        }

        return UsageData(session: session, weekly: weekly, modelCaps: caps, credits: creditsFrom(root))
    }

    /// One `primary_window`/`secondary_window` entry: `used_percent`, `limit_window_seconds`
    /// (what actually decides session vs. weekly vs. dropped — never the slot name), and the
    /// reset instant (`reset_at` preferred, `reset_after_seconds` + `nowMs` as fallback).
    ///
    /// CCBG-30 phantom window: OpenAI always returns a reset time, even for a window nothing
    /// has touched yet. `used_percent == 0` AND `reset_after_seconds >= limit_window_seconds -
    /// 1` reads as "not started" — `resetsAtMs` comes back nil, matching Claude's own omitted
    /// `resets_at` for the same state.
    ///
    /// Both conditions are required, and **both fields must actually be present** to test
    /// them: a window with no `used_percent` at all never reads as `== 0` (an absent percent
    /// is not treated as zero here — the rest of this parser keeps that same "absent is
    /// absent, not zero" convention, e.g. `UsageWindow.severity`'s own `percent ?? 0` is a
    /// deliberately different, display-only rule); a window with `reset_at` but no
    /// `reset_after_seconds` at all cannot be tested either and keeps its `reset_at`-derived
    /// `resetsAtMs` — the rule only ever fires off `reset_after_seconds`, never a length
    /// derived from `reset_at`. Every fixture and captured payload seen so far sends both
    /// fields together, so this hasn't been observed to matter in practice.
    private static func classifiedWindow(
        _ json: JSON, nowMs: Int64
    ) -> (lengthSeconds: Int, window: UsageWindow)? {
        guard json.object != nil, let lengthSeconds = json["limit_window_seconds"].int else { return nil }

        let percent = json["used_percent"].double
        let resetAtSeconds = json["reset_at"].int64
        let resetAfterSeconds = json["reset_after_seconds"].int64

        var resetsAtMs: Int64?
        if let resetAtSeconds {
            resetsAtMs = millisecondsFromEpochSeconds(resetAtSeconds)
        } else if let resetAfterSeconds {
            resetsAtMs = millisecondsFromEpochSeconds(resetAfterSeconds).flatMap {
                let (sum, overflowed) = nowMs.addingReportingOverflow($0)
                return overflowed ? nil : sum
            }
        }

        if let percent, percent == 0, let resetAfterSeconds, resetAfterSeconds >= Int64(lengthSeconds) - 1 {
            resetsAtMs = nil
        }

        return (lengthSeconds, UsageWindow(percent: percent, resetsAtMs: resetsAtMs, serverSeverity: nil))
    }

    /// `has_credits == false` (or absent) → nil, matching "nothing to report" everywhere else
    /// in this app. Otherwise a balance-shaped `SpendCredits` (BEHAVIOR-SPEC's Provider column
    /// on §2): `balance` is a JSON string-or-number, coerced to minor units USD; `unlimited`
    /// rides straight through to `SpendCredits.isReportable`.
    private static func creditsFrom(_ root: JSON) -> SpendCredits? {
        let credits = root["credits"]
        guard credits["has_credits"].bool == true else { return nil }
        return SpendCredits(
            usedMinor: 0, limitMinor: nil, exponent: 2, currency: "USD", serverSeverity: nil,
            balanceMinor: balanceMinor(credits["balance"]), unlimited: credits["unlimited"].bool ?? false)
    }

    /// CCM-76 [Accounts]: `ProfileState.lastBody` is persisted to disk and restored on
    /// relaunch, and the ChatGPT usage response carries `email`, `user_id` and `account_id` —
    /// fields Claude's own usage body never sends. A future ChatGPT fetch path must call this
    /// on `response.body` before assigning it to `lastBody`, never store the raw body. Falls
    /// back to the raw string, unredacted, only when the body isn't a JSON object at all — the
    /// same "can't parse it, can't scrub it" boundary every other tolerant reader here has;
    /// callers that reach that branch should prefer storing nothing rather than this fallback.
    public static func redactedBodyForStorage(_ raw: String) -> String {
        guard let data = raw.data(using: .utf8),
              var object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        else { return raw }
        for key in ["email", "user_id", "account_id"] {
            object.removeValue(forKey: key)
        }
        guard let redacted = try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]),
              let text = String(data: redacted, encoding: .utf8)
        else { return raw }
        return text
    }

    /// `balance` is documented as a string ("0", "12.40") but the schema is undocumented
    /// upstream, so a bare JSON number is accepted too. Rounded, not truncated, matching
    /// BEHAVIOR-SPEC §2's money rule.
    private static func balanceMinor(_ json: JSON) -> Int64? {
        if let string = json.string, let value = Double(string) { return Int64(exactly: (value * 100).rounded()) }
        if let value = json.double { return Int64(exactly: (value * 100).rounded()) }
        return nil
    }

    /// Overflow-guarded — matches `StatusLineUsageParser`/`CodexSessionScan`'s
    /// `millisecondsFromEpochSeconds` (CCM-104 [Readout] checkpoint 1, Opus review of `ac00b1a`):
    /// a `reset_at`/`reset_after_seconds` this large has never been observed, but a bare `* 1000`
    /// traps on one that is (Opus re-review of `05d374d`, F6 — this file predates CCM-104 and was
    /// missed by the original overflow-safety pass, which fixed only the two new CCM-104 parsers).
    private static func millisecondsFromEpochSeconds(_ seconds: Int64) -> Int64? {
        let (product, overflow) = seconds.multipliedReportingOverflow(by: 1000)
        return overflow ? nil : product
    }
}
