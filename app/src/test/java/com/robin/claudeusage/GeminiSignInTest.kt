package com.robin.claudeusage

import com.robin.claudeusage.data.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CCRM-55 (Antigravity Account): the Gemini sign-in's pure rules and copy. */
class GeminiSignInTest {

    @Test
    fun `Add account lists Gemini only when the build has the secret`() {
        assertEquals(
            listOf(Provider.CLAUDE, Provider.CHATGPT),
            GeminiSignIn.addRowProviders(secretAvailable = false),
        )
        assertEquals(
            listOf(Provider.CLAUDE, Provider.CHATGPT, Provider.ANTIGRAVITY),
            GeminiSignIn.addRowProviders(secretAvailable = true),
        )
    }

    @Test
    fun `Continue needs text and is off while busy or while an error shows`() {
        assertTrue(GeminiSignIn.continueEnabled("4/0Abc", busy = false, errorShowing = false))
        assertFalse(GeminiSignIn.continueEnabled("   ", busy = false, errorShowing = false))
        assertFalse(GeminiSignIn.continueEnabled("", busy = false, errorShowing = false))
        assertFalse(GeminiSignIn.continueEnabled("4/0Abc", busy = true, errorShowing = false))
        assertFalse(GeminiSignIn.continueEnabled("4/0Abc", busy = false, errorShowing = true))
    }

    @Test
    fun `the success line names the account`() {
        assertEquals("Gemini signed in — usage fetched, polling started.", GeminiSignIn.successLine("Gemini"))
    }
}
