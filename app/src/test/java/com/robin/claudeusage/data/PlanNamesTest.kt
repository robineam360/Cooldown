package com.robin.claudeusage.data

import com.robin.claudeusage.data.source.ClaudePlan
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** spec/core API-CONTRACT §11 (Robin, 2026-11-02): every case in `spec/core/fixtures/plans/cases.json`. */
class PlanNamesTest {

    private val fx = JSONObject(javaClass.classLoader!!.getResourceAsStream("core/fixtures/plans/cases.json")!!.bufferedReader().readText())
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)

    @Test
    fun `claude labels match every core case`() {
        for (c in fx.getJSONArray("claude").objects()) {
            val label = ClaudePlan.label(c.str("organizationType"), c.str("seatTier"), c.getBoolean("hasMax"), c.getBoolean("hasPro"))
                ?.let { PlanNames.claude(it, c.str("rateLimitTier")) }
            assertEquals(c.toString(), c.str("expected"), label)
        }
    }

    @Test
    fun `chatgpt labels match every core case`() {
        for (c in fx.getJSONArray("chatgpt").objects()) {
            assertEquals(c.getString("planType"), c.getString("expected"), PlanNames.chatgpt(c.getString("planType")))
        }
    }

    @Test
    fun `gemini labels match every core case`() {
        for (c in fx.getJSONArray("gemini").objects()) {
            assertEquals(c.toString(), c.getString("expected"), PlanNames.gemini(c.str("paidTierId"), c.str("paidTierName")))
        }
    }

    @Test
    fun `display maps stored plans per provider, never with a multiplier`() {
        assertEquals("Pro 100", PlanNames.display(Provider.CHATGPT, "prolite"))
        assertEquals("Pro", PlanNames.display(Provider.CLAUDE, "Pro"))
        assertEquals("Ultra", PlanNames.display(Provider.CLAUDE, "Max", "default_claude_max_20x"))
        assertEquals("Max", PlanNames.display(Provider.CLAUDE, "Max", "default_claude_max_5x"))
        assertEquals("Team Premium", PlanNames.display(Provider.CLAUDE, "Team Premium", "default_claude_max_5x"))
        assertEquals("Ultra 5x", PlanNames.display(Provider.ANTIGRAVITY, "Ultra 5x"))
    }
}
