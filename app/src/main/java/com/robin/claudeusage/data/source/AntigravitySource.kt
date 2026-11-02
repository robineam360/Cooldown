package com.robin.claudeusage.data.source

import com.robin.claudeusage.BuildConfig
import com.robin.claudeusage.data.Credentials
import com.robin.claudeusage.data.HttpResult
import com.robin.claudeusage.data.ModelCap
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsagePool
import com.robin.claudeusage.data.UsageWindow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Google Antigravity's Gemini windows (CCRM-55 (Antigravity Account)), read the way
 * Google's own Antigravity CLI reads them: spec/core API-CONTRACT §10. **Posture:** this
 * presents as that CLI — its public OAuth client and a versioned `antigravity/cli/<v>`
 * User-Agent token, both Google's, either of which Google can change or close.
 *
 * [Credentials.accountId] carries the Cloud project from `loadCodeAssist` (the one id the
 * quota call needs), as it carries ChatGPT's account id; refresh keeps it.
 */
object AntigravitySource : UsageSource {
    override val provider: Provider = Provider.ANTIGRAVITY

    const val CLIENT_ID = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"
    const val REDIRECT_URI = "https://antigravity.google/oauth-callback"
    const val AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    const val SCOPE = "https://www.googleapis.com/auth/cloud-platform " +
        "https://www.googleapis.com/auth/userinfo.email " +
        "https://www.googleapis.com/auth/userinfo.profile openid"
    private const val BASE = "https://daily-cloudcode-pa.googleapis.com/v1internal"
    const val LOAD_CODE_ASSIST_URL = "$BASE:loadCodeAssist"
    const val QUOTA_URL = "$BASE:retrieveUserQuotaSummary"

    /**
     * The one gate Google applies: a **versioned** `antigravity/cli/<v>` token anywhere in
     * the User-Agent returns the Gemini groups; without the version only the Claude + GPT
     * group comes back (measured 2026-11-02). One constant, tested to carry a version.
     */
    const val CLI_VERSION = "1.4.12"
    val USER_AGENT =
        "Cooldown/${BuildConfig.VERSION_NAME} antigravity/cli/$CLI_VERSION " +
            "(aidev_client; os_type=android; arch=arm64; auth_method=consumer)"

    /** The client's public installed-app secret, from build configuration (never the repo). */
    val clientSecret: String get() = BuildConfig.ANTIGRAVITY_CLIENT_SECRET

    /** Without the secret no token can be minted, so the Gemini row is hidden. */
    val available: Boolean get() = clientSecret.isNotEmpty()

    private val jsonMedia = "application/json".toMediaType()
    private val formMedia = "application/x-www-form-urlencoded".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** `loadCodeAssist`'s two answers we keep. */
    data class CodeAssist(val project: String?, val plan: String)

    override fun fetchUsage(creds: Credentials): HttpResult {
        val cached = creds.accountId
        val project = cached ?: loadCodeAssist(creds.accessToken).let { (r, ca) ->
            ca?.project ?: return r
        }
        val resp = postJson(QUOTA_URL, creds.accessToken, JSONObject().put("project", project).toString())
        // A stale project answers 4xx with a fresh token: read it again once (§10.4).
        if (cached != null && resp.code in 400..499 && !isAuthFailure(resp.code) && resp.code != 429) {
            val fresh = loadCodeAssist(creds.accessToken).second?.project ?: return resp
            return postJson(QUOTA_URL, creds.accessToken, JSONObject().put("project", fresh).toString())
        }
        return resp
    }

    /** POST `loadCodeAssist`; the parsed answer is null unless it was a 200 with a body. */
    fun loadCodeAssist(accessToken: String): Pair<HttpResult, CodeAssist?> {
        val r = postJson(LOAD_CODE_ASSIST_URL, accessToken, """{"metadata":{"ideType":"ANTIGRAVITY"}}""")
        return r to if (r.code == 200) parseCodeAssist(r.body) else null
    }

    override fun refresh(creds: Credentials): HttpResult = postForm(
        TOKEN_URL,
        "grant_type=refresh_token" +
            "&refresh_token=${enc(creds.refreshToken)}" +
            "&client_id=${enc(CLIENT_ID)}" +
            "&client_secret=${enc(clientSecret)}",
    )

    /** The authorization-code exchange (§10.1 step 3). */
    fun exchangeCode(code: String, verifier: String): HttpResult = postForm(
        TOKEN_URL,
        "grant_type=authorization_code" +
            "&code=${enc(code)}" +
            "&client_id=${enc(CLIENT_ID)}" +
            "&client_secret=${enc(clientSecret)}" +
            "&redirect_uri=${enc(REDIRECT_URI)}" +
            "&code_verifier=${enc(verifier)}",
    )

    /** Google's refresh reply carries no new refresh token: the previous one and the project stay. */
    override fun parseTokenResponse(body: String, previous: Credentials?): TokenGrant? = try {
        val o = JSONObject(body)
        val access = o.optString("access_token")
        if (access.isEmpty()) null
        else {
            val refresh = o.optString("refresh_token").ifEmpty { previous?.refreshToken.orEmpty() }
            val expiresIn = o.optLong("expires_in", 0L)
            val email = o.optString("id_token").takeIf { it.isNotEmpty() }
                ?.let { ChatGptSource.jwtPayload(it)?.optString("email") }
                ?.takeIf { it.isNotEmpty() }
            TokenGrant(
                creds = Credentials(
                    access, refresh,
                    if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L,
                    previous?.accountId,
                ),
                plan = null,
                tier = null,
                email = email,
            )
        }
    } catch (_: Exception) {
        null
    }

    override fun parseUsage(body: String): UsageData? = AntigravityUsageParser.parse(body)

    override fun parseUsage(body: String, fetchedAtMs: Long): UsageData? =
        AntigravityUsageParser.parse(body, fetchedAtMs)

    override fun isAuthFailure(status: Int): Boolean = status == 401

    /** `paidTier.id` names the plan; `currentTier` reads free on a Pro account, so never it. */
    fun parseCodeAssist(body: String): CodeAssist? = try {
        val o = JSONObject(body)
        // API-CONTRACT §11's labels (Pro, Ultra 5x, Ultra 20x, else the tier's own name).
        val paid = o.optJSONObject("paidTier")
        val plan = com.robin.claudeusage.data.PlanNames.gemini(
            paid?.optString("id")?.ifEmpty { null }, paid?.optString("name")?.ifEmpty { null },
        )
        CodeAssist(o.optString("cloudaicompanionProject").ifEmpty { null }, plan)
    } catch (_: Exception) {
        null
    }

    private fun enc(v: String): String = java.net.URLEncoder.encode(v, "UTF-8")

    private fun postJson(url: String, token: String, json: String): HttpResult {
        val request = Request.Builder().url(url)
            .post(json.toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { return HttpResult(it.code, it.body?.string() ?: "") }
    }

    private fun postForm(url: String, form: String): HttpResult {
        val request = Request.Builder().url(url)
            .post(form.toRequestBody(formMedia))
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { return HttpResult(it.code, it.body?.string() ?: "") }
    }
}

/**
 * `retrieveUserQuotaSummary` → the shared model (spec/core BEHAVIOR-SPEC §1.1, Antigravity
 * rows; cases `spec/core/fixtures/antigravity/cases.json`). Null when the reply carries no
 * Gemini group — the User-Agent was rejected or Google changed the reply — so the fetch path
 * keeps the last good reading and never shows a confident 0% or 100%.
 */
object AntigravityUsageParser {

    const val POOL_NAME = "Claude + GPT in Antigravity"

    /** Google slides an untouched window's reset to "now + window"; this close counts as untouched. */
    private const val SLIDING_SLACK_MS = 120_000L

    fun parse(raw: String, nowMs: Long = System.currentTimeMillis()): UsageData? = try {
        val buckets = HashMap<String, JSONObject>()
        val groups = JSONObject(raw).optJSONArray("groups")
        if (groups != null) for (i in 0 until groups.length()) {
            val list = groups.optJSONObject(i)?.optJSONArray("buckets") ?: continue
            for (j in 0 until list.length()) {
                val b = list.optJSONObject(j) ?: continue
                val id = b.optString("bucketId")
                if (id.isNotEmpty()) buckets[id] = b
            }
        }
        if ("gemini-5h" !in buckets && "gemini-weekly" !in buckets) null
        else {
            val pool5 = buckets["3p-5h"]?.let { window(it, Projection.SESSION_MS, nowMs) }
            val poolW = buckets["3p-weekly"]?.let { window(it, Projection.WEEKLY_MS, nowMs) }
            UsageData(
                session = buckets["gemini-5h"]?.let { window(it, Projection.SESSION_MS, nowMs) },
                weekly = buckets["gemini-weekly"]?.let { window(it, Projection.WEEKLY_MS, nowMs) },
                modelCaps = listOfNotNull(
                    buckets["gemini-argon-wtu-weekly"]?.let { ModelCap("Argon", window(it, Projection.WEEKLY_MS, nowMs)) },
                ),
                pools = if (pool5 == null && poolW == null) emptyList()
                else listOf(UsagePool(POOL_NAME, pool5, poolW)),
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun window(b: JSONObject, lengthMs: Long, nowMs: Long): UsageWindow {
        val fraction = b.optDouble("remainingFraction", Double.NaN)
        val used = if (fraction.isNaN()) null else (1.0 - fraction) * 100.0
        val reset = b.optString("resetTime").takeIf { it.isNotEmpty() }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val untouched = fraction >= 1.0 && reset != null &&
            abs(reset.toEpochMilli() - (nowMs + lengthMs)) <= SLIDING_SLACK_MS
        return UsageWindow(used, if (untouched) null else reset, null)
    }
}
