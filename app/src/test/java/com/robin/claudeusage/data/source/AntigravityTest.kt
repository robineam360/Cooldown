package com.robin.claudeusage.data.source

import com.robin.claudeusage.BuildConfig
import com.robin.claudeusage.data.AntigravitySignIn
import com.robin.claudeusage.data.Credentials
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * CCRM-55 (Antigravity Account): the parser on every `spec/core/fixtures/antigravity` case,
 * the plan from `loadCodeAssist`, the User-Agent's version token, the secret's wiring and
 * the paste rules (spec/core API-CONTRACT §10, BEHAVIOR-SPEC §1.1).
 */
class AntigravityTest {

    private fun text(path: String): String =
        javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().readText()

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun assertWindow(name: String, exp: JSONObject?, got: com.robin.claudeusage.data.UsageWindow?) {
        if (exp == null) {
            assertNull(name, got)
            return
        }
        assertNotNull(name, got)
        assertEquals(name, exp.getDouble("usedPct"), got!!.percent!!, 1e-6)
        val reset = if (exp.isNull("resetsAt")) null else Instant.parse(exp.getString("resetsAt"))
        assertEquals(name, reset, got.resetsAt)
    }

    @Test
    fun `parser matches every core case`() {
        val cases = JSONObject(text("core/fixtures/antigravity/cases.json")).getJSONArray("parse").objects()
        assertTrue(cases.size >= 7)
        for (c in cases) {
            val file = c.getString("file")
            val exp = c.getJSONObject("expected")
            val got = AntigravityUsageParser.parse(text("core/fixtures/antigravity/$file"), c.getLong("nowMs"))
            if (exp.getString("status") != "ok") {
                assertNull(file, got)
                continue
            }
            assertNotNull(file, got)
            got!!
            assertWindow("$file session", exp.optJSONObject("session"), got.session)
            assertWindow("$file weekly", exp.optJSONObject("weekly"), got.weekly)
            val caps = exp.getJSONArray("modelCaps").objects()
            assertEquals(file, caps.map { it.getString("name") }, got.modelCaps.map { it.modelName })
            caps.forEachIndexed { i, cap -> assertWindow("$file cap", cap, got.modelCaps[i].window) }
            val pools = exp.getJSONArray("pools").objects()
            assertEquals(file, pools.size, got.pools.size)
            pools.forEachIndexed { i, p ->
                assertEquals(file, p.getString("name"), got.pools[i].name)
                assertEquals("$file pool shown", p.getBoolean("shown"), got.pools[i].shown)
                assertWindow("$file pool session", p.optJSONObject("session"), got.pools[i].session)
                assertWindow("$file pool weekly", p.optJSONObject("weekly"), got.pools[i].weekly)
            }
        }
    }

    @Test
    fun `plan comes from paidTier, never currentTier`() {
        for (c in JSONObject(text("core/fixtures/antigravity/cases.json")).getJSONArray("plan").objects()) {
            val ca = AntigravitySource.parseCodeAssist(text("core/fixtures/antigravity/${c.getString("file")}"))!!
            val exp = c.getJSONObject("expected")
            assertEquals(exp.getString("plan"), ca.plan)
            assertEquals(exp.getString("project"), ca.project)
        }
    }

    @Test
    fun `the User-Agent carries a versioned antigravity cli token`() {
        assertTrue(AntigravitySource.USER_AGENT, Regex("antigravity/cli/\\d").containsMatchIn(AntigravitySource.USER_AGENT))
        assertTrue(AntigravitySource.USER_AGENT.startsWith("Cooldown/"))
    }

    @Test
    fun `the client secret is wired through BuildConfig, not the source`() {
        // The field exists and is what the source reads; its value is never asserted.
        assertEquals(BuildConfig.ANTIGRAVITY_CLIENT_SECRET, AntigravitySource.clientSecret)
        assertEquals(BuildConfig.ANTIGRAVITY_CLIENT_SECRET.isNotEmpty(), AntigravitySource.available)
    }

    @Test
    fun `a refresh keeps the refresh token and the project`() {
        val prev = Credentials("old", "refresh-1", 0L, "project-1")
        val grant = AntigravitySource.parseTokenResponse("""{"access_token":"new","expires_in":3599}""", prev)!!
        assertEquals("new", grant.creds.accessToken)
        assertEquals("refresh-1", grant.creds.refreshToken)
        assertEquals("project-1", grant.creds.accountId)
        assertNull(AntigravitySource.parseTokenResponse("""{"error":"invalid_grant"}""", prev))
    }

    @Test
    fun `paste accepts the bare code or the callback address`() {
        val code = "4/0AVMBt8kQ2xLpR7wZexample"
        assertEquals(AntigravitySignIn.Paste.Code(code), AntigravitySignIn.parsePaste("  $code\n", "s1"))
        assertEquals(
            AntigravitySignIn.Paste.Code(code),
            AntigravitySignIn.parsePaste("https://antigravity.google/oauth-callback?state=s1&code=4%2F0AVMBt8kQ2xLpR7wZexample", "s1"),
        )
        assertEquals(
            AntigravitySignIn.Paste.OtherSignIn,
            AntigravitySignIn.parsePaste("https://antigravity.google/oauth-callback?state=old&code=$code", "s1"),
        )
        assertEquals(AntigravitySignIn.Paste.Unreadable, AntigravitySignIn.parsePaste("hello", "s1"))
        assertEquals(AntigravitySignIn.Paste.Unreadable, AntigravitySignIn.parsePaste("", "s1"))
    }

    @Test
    fun `authorize url carries the client, the hosted redirect and offline consent`() {
        val url = AntigravitySignIn.authorizeUrl("challenge", "state1")
        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        for (part in listOf(
            "client_id=1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com",
            "redirect_uri=https%3A%2F%2Fantigravity.google%2Foauth-callback",
            "code_challenge_method=S256", "access_type=offline", "prompt=consent", "state=state1",
            "response_type=code",
        )) assertTrue(part, url.contains(part))
    }
}
