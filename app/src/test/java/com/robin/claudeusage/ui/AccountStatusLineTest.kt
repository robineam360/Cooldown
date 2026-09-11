package com.robin.claudeusage.ui

import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.SignInExpiry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [AccountStatusLine] — the account card's status wording (CCRM-65 (Accounts
 * Redesign)), per design/accounts-redesign-wireframe.html rev D.
 */
class AccountStatusLineTest {

    private val now = 1_757_000_000_000L

    // --- claude ---

    @Test
    fun `claude busy shows only the checking clause`() {
        assertEquals(
            "Checking…",
            AccountStatusLine.claude(
                lastAttemptAt = now - 1000,
                tokenExpiresAt = now + 1000,
                authState = AuthState.OK,
                busy = true,
                now = now,
            ),
        )
    }

    @Test
    fun `claude never checked`() {
        assertEquals(
            "Not checked yet",
            AccountStatusLine.claude(
                lastAttemptAt = 0L,
                tokenExpiresAt = 0L,
                authState = AuthState.OK,
                busy = false,
                now = now,
            ),
        )
    }

    @Test
    fun `claude checked with no reported expiry has no clause`() {
        assertEquals(
            "Checked 1m ago",
            AccountStatusLine.claude(
                lastAttemptAt = now - 60_000L,
                tokenExpiresAt = 0L,
                authState = AuthState.OK,
                busy = false,
                now = now,
            ),
        )
    }

    @Test
    fun `claude checked with a future token expiry shows renews-in`() {
        assertEquals(
            "Checked 1m ago · renews in 2h 4m",
            AccountStatusLine.claude(
                lastAttemptAt = now - 60_000L,
                tokenExpiresAt = now + (2 * 60 + 4) * 60_000L,
                authState = AuthState.OK,
                busy = false,
                now = now,
            ),
        )
    }

    @Test
    fun `claude checked with a past token expiry shows renewal due`() {
        assertEquals(
            "Checked 1m ago · renewal due at next check",
            AccountStatusLine.claude(
                lastAttemptAt = now - 60_000L,
                tokenExpiresAt = now - 5_000L,
                authState = AuthState.OK,
                busy = false,
                now = now,
            ),
        )
    }

    @Test
    fun `claude checked with token expiry exactly at now shows renewal due`() {
        assertEquals(
            "Checked 1m ago · renewal due at next check",
            AccountStatusLine.claude(
                lastAttemptAt = now - 60_000L,
                tokenExpiresAt = now,
                authState = AuthState.OK,
                busy = false,
                now = now,
            ),
        )
    }

    @Test
    fun `claude reauth needed suppresses the renewal clause regardless of token expiry`() {
        assertEquals(
            "Checked 1m ago",
            AccountStatusLine.claude(
                lastAttemptAt = now - 60_000L,
                tokenExpiresAt = now + 60_000L,
                authState = AuthState.REAUTH_NEEDED,
                busy = false,
                now = now,
            ),
        )
    }

    // --- chatGpt ---

    @Test
    fun `chatGpt busy shows only the checking clause`() {
        assertEquals("Checking…", AccountStatusLine.chatGpt(lastAttemptAt = now - 1000, busy = true, now = now))
    }

    @Test
    fun `chatGpt never checked`() {
        assertEquals("Not checked yet", AccountStatusLine.chatGpt(lastAttemptAt = 0L, busy = false, now = now))
    }

    @Test
    fun `chatGpt checked has no renewal clause`() {
        assertEquals(
            "Checked 1m ago",
            AccountStatusLine.chatGpt(lastAttemptAt = now - 60_000L, busy = false, now = now),
        )
    }

    // --- expirySoon ---

    @Test
    fun `expirySoon is null when not soon`() {
        val line = SignInExpiry.Line.Estimated(now + SignInExpiry.SOON_MS + 60_000L)
        assertNull(AccountStatusLine.expirySoon(line, now))
    }

    @Test
    fun `expirySoon warns for an estimated expiry`() {
        val expiresAt = now + (2 * 60 + 4) * 60_000L
        val line = SignInExpiry.Line.Estimated(expiresAt)
        assertEquals(
            "Sign-in expires in 2h 4m — re-sign in to keep polling going.",
            AccountStatusLine.expirySoon(line, now),
        )
    }

    @Test
    fun `expirySoon warns for an exact expiry`() {
        val expiresAt = now + (2 * 60 + 4) * 60_000L
        val line = SignInExpiry.Line.Exact(expiresAt)
        assertEquals(
            "Sign-in expires in 2h 4m — re-sign in to keep polling going.",
            AccountStatusLine.expirySoon(line, now),
        )
    }

    @Test
    fun `expirySoon is null for renewal-dead`() {
        assertNull(AccountStatusLine.expirySoon(SignInExpiry.Line.RenewalDead(9L, true), now))
    }

    @Test
    fun `expirySoon is null for none`() {
        assertNull(AccountStatusLine.expirySoon(SignInExpiry.Line.None, now))
    }

    // --- freePlan ---

    @Test
    fun `freePlan capitalizes the given plan name`() {
        assertEquals(
            "Free plan: Claude doesn't report usage — upgrade to Pro, Max or Team to see numbers.",
            AccountStatusLine.freePlan("free"),
        )
    }

    @Test
    fun `freePlan defaults to Free when no plan is known`() {
        assertEquals(
            "Free plan: Claude doesn't report usage — upgrade to Pro, Max or Team to see numbers.",
            AccountStatusLine.freePlan(null),
        )
    }
}
