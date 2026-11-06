package com.robin.claudeusage.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.robin.claudeusage.data.source.AntigravitySource
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * CCRM-55 (Antigravity Account): Google sign-in the way Antigravity's headless CLI does it
 * (spec/core API-CONTRACT §10.1) — a Custom Tab on Google's page, which ends on
 * `antigravity.google/oauth-callback` showing a `4/…` code; the user pastes the code or
 * the whole callback address. The PKCE material survives process death while the tab is up.
 */
object AntigravitySignIn {

    data class Pending(val verifier: String, val state: String, val profileKey: String)

    /** What a paste resolves to: a code to exchange, or why not. */
    sealed interface Paste {
        data class Code(val code: String) : Paste
        /** Nothing that looks like a Google code. */
        data object Unreadable : Paste
        /** A callback address from another attempt: never exchanged. */
        data object OtherSignIn : Paste
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("oauth_pending_antigravity", Context.MODE_PRIVATE)

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    internal fun challengeFor(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    /** The authorize URL for a Custom Tab; persists the verifier and state for the return trip. */
    fun begin(context: Context, profile: Profile): String {
        val verifier = randomUrlSafe(32)
        val state = randomUrlSafe(16)
        prefs(context).edit()
            .putString("verifier", verifier).putString("state", state).putString("profile", profile.key)
            .apply()
        return authorizeUrl(challengeFor(verifier), state)
    }

    internal fun authorizeUrl(challenge: String, state: String): String =
        AntigravitySource.AUTHORIZE_URL + "?" +
            "client_id=" + enc(AntigravitySource.CLIENT_ID) +
            "&redirect_uri=" + enc(AntigravitySource.REDIRECT_URI) +
            "&response_type=code" +
            "&scope=" + enc(AntigravitySource.SCOPE) +
            "&code_challenge=" + enc(challenge) +
            "&code_challenge_method=S256" +
            "&access_type=offline" +
            "&prompt=consent" +
            "&state=" + enc(state)

    fun pending(context: Context): Pending? {
        val p = prefs(context)
        return Pending(
            p.getString("verifier", null) ?: return null,
            p.getString("state", null) ?: return null,
            p.getString("profile", null) ?: return null,
        )
    }

    fun clearPending(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /**
     * The bare `4/…` code, or a whole callback address whose `state` (when it carries one)
     * must equal [expectedState]. Whitespace and line breaks from a copy are dropped.
     */
    fun parsePaste(input: String, expectedState: String): Paste {
        val text = input.filterNot { it.isWhitespace() }
        if (text.isEmpty()) return Paste.Unreadable
        if (text.startsWith("http://") || text.startsWith("https://")) {
            val query = runCatching { URI(text).rawQuery }.getOrNull() ?: return Paste.Unreadable
            val params = query.split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
            }.toMap()
            val state = params["state"]
            if (state != null && state != expectedState) return Paste.OtherSignIn
            val code = params["code"] ?: return Paste.Unreadable
            return if (code.startsWith("4/")) Paste.Code(code) else Paste.Unreadable
        }
        return if (text.startsWith("4/") && text.length > 10) Paste.Code(text) else Paste.Unreadable
    }
}
