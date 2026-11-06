package com.robin.claudeusage

import com.robin.claudeusage.data.Provider

/**
 * CCRM-55 (Antigravity Account): the copy and the two small rules of the Gemini sign-in
 * on Android, kept out of the composables so a unit test can pin them
 * (design/2026-11-02-antigravity-account.html rev B, §2 F1–F7 and §5).
 */
object GeminiSignIn {
    const val ROW_SUBTITLE = "Signs in with Google in your browser, then paste the code back."
    const val NOT_SIGNED_IN_BUTTON = "Sign in with Google"
    const val NOT_SIGNED_IN_HELP = "Opens Google’s sign-in in your browser — no computer needed."
    const val PASTE_TITLE = "Finish signing in"
    const val PASTE_BODY = "Sign in on the page that opened, then copy the code it shows and paste it here."
    const val PASTE_LABEL = "Paste the code from the sign-in page"
    const val PASTE_HELPER = "The code starts with 4/. You can also paste the whole address from the page."
    const val START_AGAIN = "Start again"

    fun successLine(label: String) = "$label signed in — usage fetched, polling started."

    /** The Add-account sheet's Gemini row: shown only when the build carries the client secret. */
    fun addRowVisible(secretAvailable: Boolean): Boolean = secretAvailable

    /** The providers the Add-account sheet lists, in order. */
    fun addRowProviders(secretAvailable: Boolean): List<Provider> =
        if (addRowVisible(secretAvailable)) listOf(Provider.CLAUDE, Provider.CHATGPT, Provider.ANTIGRAVITY)
        else listOf(Provider.CLAUDE, Provider.CHATGPT)

    /** Continue is on with text in the field, off while busy or while an error shows. */
    fun continueEnabled(input: String, busy: Boolean, errorShowing: Boolean): Boolean =
        !busy && !errorShowing && input.isNotBlank()
}
