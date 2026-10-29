// Ported from CooldownMac sources/ccooldown-core/UsageParser.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// Parses the `/api/oauth/usage` response. See contract/API-CONTRACT.md §3.
///
/// The schema is undocumented and contains transient experiment fields, so everything
/// unknown is ignored and every field is optional. Windows prefer the `limits` array
/// (server-computed percent + severity) and fall back to `five_hour`/`seven_day`;
/// credits prefer `spend` and fall back to `extra_usage`.
public enum UsageParser {

    /// Returns nil when the payload is unusable — malformed, or carrying no windows, no
    /// caps and no credits. An empty success is never returned in its place.
    public static func parse(_ raw: String) -> UsageData? {
        guard let data = raw.data(using: .utf8),
              let any = try? JSONSerialization.jsonObject(with: data),
              any is [String: Any]
        else { return nil }
        return parse(root: JSON(any))
    }

    static func parse(root: JSON) -> UsageData? {
        var session: UsageWindow?
        var weekly: UsageWindow?
        var caps: [ModelCap] = []

        if let limits = root["limits"].array {
            for entry in limits {
                let item = JSON(entry)
                guard item.object != nil else { continue }
                let window = UsageWindow(
                    percent: item["percent"].double,
                    resetsAtMs: Timestamps.parseInstantMs(item["resets_at"].string),
                    serverSeverity: item["severity"].nonEmptyString
                )
                switch item["kind"].string {
                case "session":
                    session = window
                case "weekly_all":
                    weekly = window
                case "weekly_scoped":
                    let name = item["scope"]["model"]["display_name"].nonEmptyString ?? "Model"
                    caps.append(ModelCap(modelName: name, window: window))
                default:
                    break
                }
            }
        }

        if session == nil { session = windowFrom(root["five_hour"]) }
        if weekly == nil { weekly = windowFrom(root["seven_day"]) }

        // Model caps need the same fallback or they silently vanish when `limits` is
        // absent — an empty caps list is a legitimate state ("no caps on this account"),
        // so nothing would ever flag the loss (CCM-45, Android's CCBG-8). Only the two
        // attested flat siblings: `seven_day_sonnet` (OpenQuota reads it) and
        // `seven_day_opus` (null-present in captured payloads). Anything else is a guess.
        if caps.isEmpty {
            if let sonnet = windowFrom(root["seven_day_sonnet"]) {
                caps.append(ModelCap(modelName: "Sonnet", window: sonnet))
            }
            if let opus = windowFrom(root["seven_day_opus"]) {
                caps.append(ModelCap(modelName: "Opus", window: opus))
            }
        }

        let credits = creditsFrom(root)

        if session == nil, weekly == nil, caps.isEmpty, credits == nil { return nil }
        return UsageData(session: session, weekly: weekly, modelCaps: caps, credits: credits)
    }

    /// The legacy blocks report the percent under `utilization`, already on a 0-100
    /// scale, and carry no severity.
    private static func windowFrom(_ node: JSON) -> UsageWindow? {
        guard node.object != nil else { return nil }
        return UsageWindow(
            percent: node["utilization"].double,
            resetsAtMs: Timestamps.parseInstantMs(node["resets_at"].string),
            serverSeverity: nil
        )
    }

    /// Prefers `spend` — it carries currency and exponent on each amount — and falls
    /// back to `extra_usage`, which reports the same figures with a single
    /// `decimal_places`.
    ///
    /// A null limit beside real spend means the cap is switched **off**, not that the
    /// credits don't exist (CCM-44, Android's CCBG-9): the limit becomes nil and the spend
    /// is kept. Only a block carrying neither a limit nor any spend parses to nothing.
    private static func creditsFrom(_ root: JSON) -> SpendCredits? {
        let spend = root["spend"]
        let used = spend["used"]
        let limit = spend["limit"]

        if used.object != nil {
            let usedMinor = used["amount_minor"].int64 ?? 0
            let limitMinor = limit.object != nil ? (limit["amount_minor"].int64 ?? 0) : nil
            guard limitMinor != nil || usedMinor > 0 else { return nil }
            return SpendCredits(
                usedMinor: usedMinor,
                limitMinor: limitMinor,
                exponent: limit["exponent"].int ?? used["exponent"].int ?? 2,
                currency: limit["currency"].nonEmptyString ?? used["currency"].nonEmptyString ?? "USD",
                serverSeverity: spend["severity"].nonEmptyString
            )
        }

        let extra = root["extra_usage"]
        guard extra.object != nil else { return nil }
        let usedMinor = Int64(extra["used_credits"].double ?? 0)
        let limitMinor = extra["monthly_limit"].isNull ? nil : (extra["monthly_limit"].int64 ?? 0)
        guard limitMinor != nil || usedMinor > 0 else { return nil }
        return SpendCredits(
            usedMinor: usedMinor,
            limitMinor: limitMinor,
            exponent: extra["decimal_places"].int ?? 2,
            currency: extra["currency"].nonEmptyString ?? "USD",
            serverSeverity: nil
        )
    }
}
