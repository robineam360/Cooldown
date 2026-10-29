// Ported from CooldownMac sources/ccooldown-core/Models.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

// Models, timestamp parsing, and loose-JSON access.
// See contract/BEHAVIOR-SPEC.md §1-2 and contract/API-CONTRACT.md §3.

public enum Severity: String, Sendable, Equatable {
    case normal = "NORMAL"
    case warning = "WARNING"
    case critical = "CRITICAL"
}

/// One usage window (5-hour session, 7-day all-models, or a per-model cap).
public struct UsageWindow: Sendable, Equatable {
    public let percent: Double?
    /// Epoch milliseconds. nil = not reported.
    public let resetsAtMs: Int64?
    public let serverSeverity: String?

    public init(percent: Double?, resetsAtMs: Int64?, serverSeverity: String?) {
        self.percent = percent
        self.resetsAtMs = resetsAtMs
        self.serverSeverity = serverSeverity
    }

    /// BEHAVIOR-SPEC §1. Note the asymmetry: `> 90` is strict, `>= 70` inclusive, and
    /// the server's value is OR'd with the percent rule — never AND'd. These are NOT
    /// the bar-colour breakpoints (§5); the two scales are deliberately independent.
    public var severity: Severity {
        let pct = percent ?? 0
        if serverSeverity == "critical" || pct > 90 { return .critical }
        if serverSeverity == "warning" || pct >= 70 { return .warning }
        return .normal
    }
}

public struct ModelCap: Sendable, Equatable {
    public let modelName: String
    public let window: UsageWindow

    public init(modelName: String, window: UsageWindow) {
        self.modelName = modelName
        self.window = window
    }
}

/// Pay-as-you-go credits. This is *money*, not a rate limit, so it rounds where the
/// window bars truncate. BEHAVIOR-SPEC §2.
public struct SpendCredits: Sendable, Equatable {
    public let usedMinor: Int64
    /// nil = the account's spend cap is switched **off** (CCM-44). Distinct from a zero
    /// limit, which is a cap that happens to be $0. Having a cap and having credits are
    /// different facts — conflating them once hid a paying user's whole credits row.
    public let limitMinor: Int64?
    public let exponent: Int
    public let currency: String
    public let serverSeverity: String?
    /// CCM-76 [Accounts]: ChatGPT reports pay-as-you-go credits as a standing **balance**, not
    /// spend against a cap — there is no `used`/`limit` pair at all, only money left. Additive
    /// field: nil for every Claude credits reading, which has nothing to put here. When
    /// present, it is the sole source of truth and `usedMinor`/`limitMinor` are meaningless
    /// zero/nil placeholders on that reading.
    public let balanceMinor: Int64?
    /// CCM-76 [Accounts]: ChatGPT's `unlimited` flag — a balance that is not worth reporting
    /// because there is no ceiling to run out of. Always `false` for Claude's usedMinor/
    /// limitMinor readings.
    public let unlimited: Bool

    public init(
        usedMinor: Int64, limitMinor: Int64?, exponent: Int, currency: String, serverSeverity: String?,
        balanceMinor: Int64? = nil, unlimited: Bool = false
    ) {
        self.usedMinor = usedMinor
        self.limitMinor = limitMinor
        self.exponent = exponent
        self.currency = currency
        self.serverSeverity = serverSeverity
        self.balanceMinor = balanceMinor
        self.unlimited = unlimited
    }

    public var hasLimit: Bool { (limitMinor ?? 0) > 0 }

    /// Computed from the money, never from the server's rounded integer. nil when there is
    /// no positive cap to measure against — never a synthesised 0%, because a 0% bar reads
    /// as headroom against a ceiling that doesn't exist.
    public var percent: Double? {
        guard let limitMinor, limitMinor > 0 else { return nil }
        return Double(usedMinor) * 100 / Double(limitMinor)
    }

    /// Rounded, not truncated: $5.99 of $100 reads 6%, not 5%.
    public var percentDisplay: Int? { percent.map { Int($0.rounded()) } }

    /// Clamped at zero. The percentage above is deliberately *not* clamped. nil when
    /// uncapped — "remaining" is meaningless without a ceiling.
    public var remainingMinor: Int64? {
        guard let limitMinor, limitMinor > 0 else { return nil }
        return max(0, limitMinor - usedMinor)
    }

    /// The render rule: a cap *or* actual spend earns the row. Replaces the old
    /// `limitMinor > 0` gate, which equated having a cap with having credits and made an
    /// uncapped account's spend vanish (Android's CCBG-9, ported as CCM-44).
    ///
    /// CCM-76 [Accounts]: a ChatGPT balance reading (`balanceMinor != nil`) is reportable on
    /// its own terms — there's no `used`/`limit` pair to fall back to — **unless** `unlimited`
    /// is set, which is ChatGPT's own "not worth reporting" signal.
    public var isReportable: Bool {
        if unlimited { return false }
        if balanceMinor != nil { return true }
        return hasLimit || usedMinor > 0
    }
}

public struct UsageData: Sendable, Equatable {
    public let session: UsageWindow?
    public let weekly: UsageWindow?
    public let modelCaps: [ModelCap]
    public let credits: SpendCredits?

    public init(session: UsageWindow?, weekly: UsageWindow?, modelCaps: [ModelCap], credits: SpendCredits?) {
        self.session = session
        self.weekly = weekly
        self.modelCaps = modelCaps
        self.credits = credits
    }

    /// CCM-76 [Accounts]: true when nothing at all was read — no session, no weekly, no model
    /// caps, no credits. `UsageParser` (Claude) never produces this: an equally empty payload
    /// returns `nil` from `parse` instead. `ChatGPTUsageParser` can, deliberately, for a
    /// well-formed response with no `rate_limit` key (BEHAVIOR-SPEC §1.1) — a fetch path
    /// consuming that parser must treat `isEmpty == true` the same way a Claude caller treats
    /// `parse` returning `nil`: `.invalidResponse`, keeping the last-good reading, never a
    /// fresh 0% success.
    public var isEmpty: Bool {
        session == nil && weekly == nil && modelCaps.isEmpty && credits == nil
    }
}

// MARK: - Timestamps

public enum Timestamps {
    /// Parses an ISO-8601 instant with offset to epoch milliseconds, **truncating**
    /// sub-millisecond precision rather than rounding — `…59.956769+00:00` → `…999956`.
    ///
    /// Hand-rolled rather than handed to `ISO8601DateFormatter` wholesale: the server
    /// sends six fractional digits, and going through a `TimeInterval` would put the
    /// result at the mercy of binary floating-point before the truncation happens.
    /// Splitting the fraction off keeps it exact and matches the Android client's
    /// `Instant.toEpochMilli()` semantics.
    public static func parseInstantMs(_ raw: String?) -> Int64? {
        guard let raw, !raw.isEmpty else { return nil }

        var withoutFraction = raw
        var fractionMs: Int64 = 0

        if let dot = raw.firstIndex(of: ".") {
            var index = raw.index(after: dot)
            var digits = ""
            while index < raw.endIndex, raw[index].isNumber {
                digits.append(raw[index])
                index = raw.index(after: index)
            }
            fractionMs = Int64((digits + "000").prefix(3)) ?? 0
            withoutFraction = String(raw[raw.startIndex..<dot]) + String(raw[index...])
        }

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        guard let date = formatter.date(from: withoutFraction) else { return nil }
        return Int64(date.timeIntervalSince1970) * 1000 + fractionMs
    }
}

// MARK: - Loose JSON access

/// A thin read-only view over `JSONSerialization` output.
///
/// The usage schema is undocumented, carries transient experiment fields, and reports
/// the same numbers under two different shapes, so `Codable` is the wrong tool — every
/// field is optional and unknown keys must be ignored. Missing and explicitly-null are
/// treated identically, and a wrong-typed value reads as absent rather than throwing.
struct JSON {
    let raw: Any?

    init(_ raw: Any?) {
        if raw is NSNull { self.raw = nil } else { self.raw = raw }
    }

    subscript(key: String) -> JSON { JSON(object?[key]) }

    var object: [String: Any]? { raw as? [String: Any] }
    var array: [Any]? { raw as? [Any] }
    var isNull: Bool { raw == nil }
    var double: Double? { (raw as? NSNumber)?.doubleValue }
    var int: Int? { (raw as? NSNumber)?.intValue }
    var int64: Int64? { (raw as? NSNumber)?.int64Value }
    var string: String? { raw as? String }
    var bool: Bool? { raw as? Bool }

    /// An empty string means "absent" throughout this API — `severity: ""` is not a value.
    var nonEmptyString: String? {
        guard let s = raw as? String, !s.isEmpty else { return nil }
        return s
    }
}
