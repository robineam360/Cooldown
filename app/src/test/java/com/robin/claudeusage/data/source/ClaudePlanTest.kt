package com.robin.claudeusage.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCRM-64 (Claude Plan Tag) / CCBG-27 (Free Plan 403): the three `/api/oauth/profile`
 * shapes probed on the Fold 7 on 2026-09-11, with identifiers replaced, plus the 403 body
 * the usage endpoint returned for the Free one.
 */
class ClaudePlanTest {

    private fun profile(orgType: String, seat: String?, tier: String, pro: Boolean = false, max: Boolean = false): String {
        val seatJson = if (seat == null) "null" else "\"$seat\""
        return """{"account":{"uuid":"a","full_name":"R","display_name":"R","email":"r@example.com",
            "has_claude_max":$max,"has_claude_pro":$pro,"created_at":"2026-04-12T11:12:43Z"},
            "organization":{"uuid":"o","name":"Org","organization_type":"$orgType",
            "billing_type":"none","rate_limit_tier":"$tier","seat_tier":$seatJson,
            "has_extra_usage_enabled":false,"subscription_status":"active"},
            "application":{"uuid":"9d1c250a","name":"Claude Code","slug":"claude-code"},
            "enabled_plugins":[]}"""
    }

    @Test
    fun `a free organisation is Free and blocked`() {
        val info = ClaudePlan.parse(profile("claude_free", null, "default_claude_ai"))!!
        assertEquals("Free", info.plan)
        assertEquals("default_claude_ai", info.tier)
        assertTrue(info.usageBlocked)
    }

    @Test
    fun `team seats read Standard and Premium`() {
        val std = ClaudePlan.parse(profile("claude_team", "team_standard", "default_raven"))!!
        assertEquals("Team Standard", std.plan)
        assertFalse(std.usageBlocked)
        val prem = ClaudePlan.parse(profile("claude_team", "team_tier_1", "default_claude_max_5x"))!!
        assertEquals("Team Premium", prem.plan)
        assertEquals("default_claude_max_5x", prem.tier)
        assertEquals("Team", ClaudePlan.label("claude_team", null, hasMax = false, hasPro = false))
    }

    @Test
    fun `pro and max by organisation type, then by the account flags`() {
        assertEquals("Pro", ClaudePlan.parse(profile("claude_pro", null, "default_claude_ai", pro = true))!!.plan)
        assertEquals("Max", ClaudePlan.parse(profile("claude_max", null, "default_claude_max_20x", max = true))!!.plan)
        assertEquals("Max", ClaudePlan.label(null, null, hasMax = true, hasPro = true))
        assertEquals("Pro", ClaudePlan.label(null, null, hasMax = false, hasPro = true))
        assertNull(ClaudePlan.label(null, null, hasMax = false, hasPro = false))
        assertEquals("Enterprise", ClaudePlan.label("claude_enterprise", null, false, false))
        assertEquals("Something new", ClaudePlan.label("claude_something_new", null, false, false))
    }

    @Test
    fun `garbage parses to null`() {
        assertNull(ClaudePlan.parse("not json"))
        assertNull(ClaudePlan.parse("{}"))
    }

    @Test
    fun `the usage endpoint's refusal is recognised by its error code`() {
        val body = """{"type":"error","error":{"type":"permission_error","message":"OAuth authentication is currently not allowed for this organization.","details":{"error_visibility":"user_facing","error_code":"oauth_not_allowed_for_organization"}},"request_id":"req_x"}"""
        assertTrue(ClaudePlan.isPlanRefusal(body))
        assertFalse(ClaudePlan.isPlanRefusal("""{"type":"error","error":{"type":"permission_error","message":"nope"}}"""))
        assertFalse(ClaudePlan.isPlanRefusal("<html>403</html>"))
    }
}
