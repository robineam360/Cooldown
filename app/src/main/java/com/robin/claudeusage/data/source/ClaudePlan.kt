package com.robin.claudeusage.data.source

import org.json.JSONObject

/**
 * CCRM-64 (Claude Plan Tag): what `/api/oauth/profile` says about the plan, reduced to
 * the label the account card wears. Pure — no Android — so `ClaudePlanTest` pins the
 * three shapes seen on the Fold 7 on 2026-09-11.
 *
 * The plan used to come from a pasted desktop token's `subscriptionType`; a phone
 * sign-in's token response carries no plan at all, which is why Claude accounts lost
 * their chip when CCRM-63 (Token Import Removal) took the paste path away. The profile
 * endpoint is the replacement, and it says more: a Team seat's tier, and — the reason
 * CCBG-27 (Free Plan 403) needs it — whether the organisation is Free, which is the one
 * plan the usage endpoint refuses.
 *
 * Observed values: `organization_type` "claude_free" (rate_limit_tier "default_claude_ai",
 * seat_tier null, subscription_status "canceled"), "claude_team" with seat_tier
 * "team_standard" (rate_limit_tier "default_raven") and "team_tier_1" (rate_limit_tier
 * "default_claude_max_5x" — the premium seat, which is what carries Claude Code). Pro
 * and Max are inferred by analogy ("claude_pro" / "claude_max") and cross-checked
 * against `account.has_claude_pro` / `has_claude_max`, which the same payload carries.
 */
object ClaudePlan {

    data class Info(
        /** The chip text: "Free", "Pro", "Max", "Team Standard", "Team Premium", "Enterprise". */
        val plan: String,
        /** Raw `rate_limit_tier`, for `Fmt.tierMultiplier` ("default_claude_max_5x" → "5x"). */
        val tier: String?,
        /** Raw `organization_type`, kept for diagnostics. */
        val organizationType: String?,
    ) {
        /** The usage endpoint answers 403 for exactly this plan. */
        val usageBlocked: Boolean get() = isBlockedPlan(plan)
    }

    /** The one stored plan label whose usage the endpoint refuses. */
    fun isBlockedPlan(plan: String?): Boolean = plan == "Free"

    fun parse(body: String): Info? = try {
        val root = JSONObject(body)
        val org = root.optJSONObject("organization")
        val account = root.optJSONObject("account")
        val orgType = org?.optString("organization_type")?.ifEmpty { null }
        val seat = org?.optString("seat_tier")?.ifEmpty { null }
        val tier = org?.optString("rate_limit_tier")?.ifEmpty { null }
        val plan = label(
            orgType, seat,
            hasMax = account?.optBoolean("has_claude_max", false) == true,
            hasPro = account?.optBoolean("has_claude_pro", false) == true,
        ) ?: return null
        Info(plan, tier, orgType)
    } catch (_: Exception) {
        null
    }

    /**
     * Team seats: "team_standard" is the Standard seat; any other named seat on a Team
     * organisation is treated as the premium one ("team_tier_1" is what a premium seat
     * reported), because premium is the seat Anthropic sells by a changing name. A Team
     * organisation with no seat named is plain "Team".
     */
    fun label(orgType: String?, seatTier: String?, hasMax: Boolean, hasPro: Boolean): String? =
        when (orgType) {
            "claude_free" -> "Free"
            "claude_pro" -> "Pro"
            "claude_max" -> "Max"
            "claude_enterprise" -> "Enterprise"
            "claude_team" -> when {
                seatTier == null -> "Team"
                seatTier == "team_standard" -> "Team Standard"
                else -> "Team Premium"
            }
            null -> when {
                hasMax -> "Max"
                hasPro -> "Pro"
                else -> null
            }
            // An organisation type this build has never seen: show its own name rather
            // than guess ("claude_something" → "Something").
            else -> orgType.removePrefix("claude_").replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
        }

    /** Whether a 403 body is the Free-plan refusal rather than some other forbidden. */
    fun isPlanRefusal(body: String): Boolean = try {
        JSONObject(body).optJSONObject("error")?.optJSONObject("details")
            ?.optString("error_code") == "oauth_not_allowed_for_organization"
    } catch (_: Exception) {
        false
    }
}
