// Ported from CooldownMac sources/ccooldown-core/PlanTag.swift at 2bfbdfd (CCRM-94 (iOS App)); owned here from now on.
import Foundation

/// What `/api/oauth/profile` says about the plan, reduced to the label the Accounts pane can
/// wear as a chip. CCM-68 [Accounts], porting Android's CCRM-64 (`ClaudePlan.kt`).
///
/// Pure JSON in, value out — no network here, so `PlanTagTests` exercises it straight off
/// `contract/fixtures/profile/*.json`.
///
/// Observed shapes (captured on Android's Fold 7, 2026-09-11, redacted, mirrored under
/// `contract/fixtures/profile/`): `organization_type` `"claude_free"` (`rate_limit_tier`
/// `"default_claude_ai"`, `seat_tier` null), `"claude_team"` with `seat_tier`
/// `"team_standard"` (`rate_limit_tier` `"default_raven"`) and a premium seat under some
/// other name (`rate_limit_tier` `"default_claude_max_5x"` — the tier that carries Claude
/// Code). Pro and Max are read directly off `organization_type`, and — for the shapes that
/// carry none — inferred from `account.has_claude_pro` / `has_claude_max`.
public struct PlanTag: Sendable, Equatable, Codable {
    /// The chip text: "Free", "Pro", "Max", "Team Standard", "Team Premium", "Enterprise",
    /// or — for an organisation type this build has never seen — that type's name, spaced
    /// and capitalized once, not guessed away.
    public let plan: String
    /// Raw `rate_limit_tier`, in the same `"…_<digits>x"` shape `AccountsBuilder.planLabel`
    /// already parses off the token-response tier.
    public let tier: String?
    /// Raw `organization_type`, kept for diagnostics only.
    public let organizationType: String?

    public init(plan: String, tier: String?, organizationType: String?) {
        self.plan = plan
        self.tier = tier
        self.organizationType = organizationType
    }

    /// The usage endpoint answers 403 for exactly this plan (CCM-67 [Readout]).
    public var usageBlocked: Bool { Self.isBlockedPlan(plan) }

    /// The one stored plan label whose usage the endpoint refuses.
    public static func isBlockedPlan(_ plan: String?) -> Bool { plan == "Free" }

    /// Parses an `/api/oauth/profile` response body. See `contract/API-CONTRACT.md` §9 for
    /// the captured shape. `nil` on anything that doesn't parse, or that carries no plan
    /// signal at all (an empty `{}`, say).
    public static func parse(_ body: String) -> PlanTag? {
        guard let object = try? JSONSerialization.jsonObject(with: Data(body.utf8)) else { return nil }
        let json = JSON(object)
        let org = json["organization"]
        let account = json["account"]
        let orgType = org["organization_type"].nonEmptyString
        let seatTier = org["seat_tier"].nonEmptyString
        let tier = org["rate_limit_tier"].nonEmptyString
        guard let plan = label(
            organizationType: orgType, seatTier: seatTier,
            hasMax: account["has_claude_max"].bool ?? false,
            hasPro: account["has_claude_pro"].bool ?? false)
        else { return nil }
        return PlanTag(plan: plan, tier: tier, organizationType: orgType)
    }

    /// Team seats: `"team_standard"` is the Standard seat; any other named seat on a Team
    /// organisation is treated as the premium one, because premium is the seat Anthropic
    /// sells under a changing name. A Team organisation with no seat named is plain "Team".
    static func label(
        organizationType: String?, seatTier: String?, hasMax: Bool, hasPro: Bool
    ) -> String? {
        switch organizationType {
        case "claude_free": return "Free"
        case "claude_pro": return "Pro"
        case "claude_max": return "Max"
        case "claude_enterprise": return "Enterprise"
        case "claude_team":
            if seatTier == nil { return "Team" }
            if seatTier == "team_standard" { return "Team Standard" }
            return "Team Premium"
        case nil:
            if hasMax { return "Max" }
            if hasPro { return "Pro" }
            return nil
        case .some(let other):
            // An organisation type this build has never seen: show its own name rather than
            // guess. Matches Android exactly — only the first character of the whole string
            // is capitalized, not each word ("claude_something_new" → "Something new").
            let stripped = other.hasPrefix("claude_") ? String(other.dropFirst("claude_".count)) : other
            let spaced = stripped.replacingOccurrences(of: "_", with: " ")
            guard let first = spaced.first else { return spaced }
            return first.uppercased() + spaced.dropFirst()
        }
    }

    /// CCM-76 [Accounts]: maps ChatGPT's `plan_type` onto the same plan-tag seam Claude's
    /// `/api/oauth/profile` feeds — see BEHAVIOR-SPEC's Provider column on §9.2. `tier` and
    /// `organizationType` carry no ChatGPT equivalent, so both stay nil; `nil` in, `nil` out.
    public static func fromChatGPT(planType: String?) -> PlanTag? {
        guard let planType, !planType.isEmpty else { return nil }
        let plan: String
        switch planType.lowercased() {
        case "plus": plan = "Plus"
        case "pro": plan = "Pro"
        case "team", "business": plan = "Team"
        case "free": plan = "Free"
        default:
            guard let first = planType.first else { return nil }
            plan = first.uppercased() + planType.dropFirst()
        }
        return PlanTag(plan: plan, tier: nil, organizationType: nil)
    }

    /// Whether a 403 body is the usage refusal this app parks on — `error.details.error_code
    /// == "oauth_not_allowed_for_organization"`. Not proof of Free specifically: an org admin
    /// can disable OAuth app access for any plan.
    public static func isPlanRefusal(_ body: String) -> Bool {
        guard let object = try? JSONSerialization.jsonObject(with: Data(body.utf8)) else { return false }
        let json = JSON(object)
        return json["error"]["details"]["error_code"].string == "oauth_not_allowed_for_organization"
    }
}
