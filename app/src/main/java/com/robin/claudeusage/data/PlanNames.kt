package com.robin.claudeusage.data

/**
 * spec/core API-CONTRACT §11 (Robin, 2026-11-02): the label shown for each provider's plan code, so two
 * installs — and the two client families — agree. Cases: `spec/core/fixtures/plans/cases.json`. No
 * multiplier is ever appended ("Max 5x" / "Max 20x" are gone since Anthropic's 2026-10-30 rename).
 */
object PlanNames {

    /** Claude: §9's label, with a 20x `rate_limit_tier` on a Max plan read as "Ultra". */
    fun claude(label: String, rateLimitTier: String?): String =
        if (label == "Max" && rateLimitTier?.contains("max_20x") == true) "Ultra" else label

    /**
     * ChatGPT `plan_type`, in any case (a label an older build stored re-maps). `prolite` is confirmed as
     * Pro 100 by Robin's account; `pro` / `promax` as Pro 200 / Pro 500 are inferred from the codes' order.
     */
    fun chatgpt(planType: String): String {
        val code = planType.trim().lowercase()
        return when {
            code == "free" -> "Free"
            code == "go" -> "Go"
            code == "plus" -> "Plus"
            code == "prolite" -> "Pro 100"
            code == "pro" -> "Pro 200"
            code == "promax" -> "Pro 500"
            code == "team" -> "Team"
            code == "business" || code.startsWith("self_serve_business") -> "Business"
            code == "enterprise" || code == "ent26" || code.startsWith("enterprise_cbp") -> "Enterprise"
            code == "edu" -> "Edu"
            code == "edu_plus" -> "Edu Plus"
            code == "edu_pro" -> "Edu Pro"
            else -> titleWords(code)
        }
    }

    /**
     * Gemini `loadCodeAssist` `paidTier`: `g1-ultra-lite-tier` is "Ultra 5x" (inferred), `g1-ultra-tier`
     * "Ultra 20x" (unverified); any other id its name less "Google AI ", else its words; none at all, Free.
     */
    fun gemini(paidTierId: String?, paidTierName: String?): String = when (paidTierId) {
        "g1-pro-tier" -> "Pro"
        "g1-ultra-lite-tier" -> "Ultra 5x"
        "g1-ultra-tier" -> "Ultra 20x"
        null -> paidTierName?.removePrefix("Google AI ")?.trim()?.ifEmpty { null } ?: "Free"
        else -> paidTierName?.removePrefix("Google AI ")?.trim()?.ifEmpty { null } ?: titleWords(paidTierId)
    }

    /** The label for a stored plan: Claude and ChatGPT map at display (older stores hold codes); Gemini's is final. */
    fun display(provider: Provider, plan: String, tier: String? = null): String = when (provider) {
        Provider.CLAUDE -> claude(plan, tier)
        Provider.CHATGPT -> chatgpt(plan)
        Provider.ANTIGRAVITY -> plan
    }

    /** "sci_plus" → "Sci Plus", "pro-lite" → "Pro Lite": every word capitalised, never glued. */
    private fun titleWords(code: String): String =
        code.split('_', '-', ' ').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
}
