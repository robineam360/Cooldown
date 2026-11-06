import Foundation

/// spec/core API-CONTRACT §11 (Robin, 2026-11-02): the label shown for each provider's plan code, so the two client
/// families agree. Cases: `spec/core/fixtures/plans/cases.json`. No multiplier is ever appended ("Max 5x" / "Max 20x"
/// are gone since Anthropic's 2026-10-30 rename).
public enum PlanNames {

    /// Claude: §9's label, with a 20x `rate_limit_tier` on a Max plan read as "Ultra".
    public static func claude(_ label: String, rateLimitTier: String?) -> String {
        label == "Max" && (rateLimitTier?.contains("max_20x") ?? false) ? "Ultra" : label
    }

    /// ChatGPT `plan_type`, in any case (a label an older build stored re-maps). `prolite` is confirmed as Pro 100 by
    /// Robin's account; `pro` / `promax` as Pro 200 / Pro 500 are inferred from the codes' order.
    public static func chatgpt(_ planType: String) -> String {
        let code = planType.trimmingCharacters(in: .whitespaces).lowercased()
        switch code {
        case "free": return "Free"
        case "go": return "Go"
        case "plus": return "Plus"
        case "prolite": return "Pro 100"
        case "pro": return "Pro 200"
        case "promax": return "Pro 500"
        case "team": return "Team"
        case "edu": return "Edu"
        case "edu_plus": return "Edu Plus"
        case "edu_pro": return "Edu Pro"
        default:
            if code == "business" || code.hasPrefix("self_serve_business") { return "Business" }
            if code == "enterprise" || code == "ent26" || code.hasPrefix("enterprise_cbp") { return "Enterprise" }
            return titleWords(code)
        }
    }

    /// Gemini `loadCodeAssist` `paidTier`: `g1-ultra-lite-tier` is "Ultra 5x" (inferred), `g1-ultra-tier` "Ultra 20x"
    /// (unverified); any other id its name less "Google AI ", else its words; none at all, Free.
    public static func gemini(paidTierId: String?, paidTierName: String?) -> String {
        let named = paidTierName.map { $0.hasPrefix("Google AI ") ? String($0.dropFirst("Google AI ".count)) : $0 }
            .map { $0.trimmingCharacters(in: .whitespaces) }.flatMap { $0.isEmpty ? nil : $0 }
        switch paidTierId {
        case "g1-pro-tier": return "Pro"
        case "g1-ultra-lite-tier": return "Ultra 5x"
        case "g1-ultra-tier": return "Ultra 20x"
        case nil: return named ?? "Free"
        case let id?: return named ?? titleWords(id)
        }
    }

    /// The label for a stored plan: Claude and ChatGPT map at display (an older build stored "Prolite"); Gemini's is
    /// final.
    public static func display(_ provider: Provider, _ plan: String, tier: String? = nil) -> String {
        switch provider {
        case .claude: claude(plan, rateLimitTier: tier)
        case .chatgpt: chatgpt(plan)
        case .antigravity: plan
        }
    }

    /// "sci_plus" → "Sci Plus", "pro-lite" → "Pro Lite": every word capitalised, never glued.
    static func titleWords(_ code: String) -> String {
        code.split(whereSeparator: { $0 == "_" || $0 == "-" || $0 == " " })
            .map { $0.prefix(1).uppercased() + $0.dropFirst().lowercased() }
            .joined(separator: " ")
    }
}
