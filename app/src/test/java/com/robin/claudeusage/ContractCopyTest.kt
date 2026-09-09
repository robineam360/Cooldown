package com.robin.claudeusage

import com.robin.claudeusage.data.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The copy-drift half of CCRM-37 (Contract Tests), built to the slice CCRM-57
 * (Provider Plumbing) asks for: **three provider names, three vendors, and the About
 * disclaimer's trademark lines** — down to two marks/two owners as of 2026-09-08, when
 * CCRM-55 (Antigravity Account) was dropped and its Google/Gemini/Antigravity lines came
 * out of the disclaimer, the Add-account sheet and the "Per provider" swatch. `Provider`
 * itself still lists all three (dropped IDs are never renumbered), so the first two tests
 * below are unchanged. CCRM-37 itself stays Planned — its registry-contract grep and
 * visual-parity assertions are not here.
 *
 * Two of the three subjects are ordinary values, so they are asserted directly. The
 * fourth — the About card's disclaimer — is a Compose string literal with no
 * Robolectric to render it, so this reads the **raw source**, which is the method
 * CCRM-37 describes (OpenQuota's `uiLanguage.test.ts` does the same). Coarse, but it
 * fails the moment someone drops a trademark while editing the sentence around it,
 * which is the whole job.
 *
 * The README's notice still names Anthropic alone and is deliberately not asserted
 * here: it is rewritten in the v1.5 release step, and a test that fails until then
 * would be noise rather than a guard.
 */
class ContractCopyTest {

    /** Gradle runs unit tests from the module dir; be indifferent to which. */
    private fun source(relative: String): String {
        val candidates = listOf(File("app/$relative"), File(relative))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("couldn't find $relative from ${File(".").absolutePath}")
        return file.readText()
    }

    private val about by lazy { source("src/main/java/com/robin/claudeusage/SettingsScreen.kt") }

    /**
     * Just the disclaimer's string literal, not the whole file — [about] also contains
     * doc comments that name Antigravity when explaining why it *isn't* in the
     * disclaimer any more, which would defeat a plain `about.contains` check.
     */
    private val disclaimer by lazy {
        val start = about.indexOf("\"Unofficial.")
        val end = about.indexOf("style = MaterialTheme.typography.labelSmall", start)
        about.substring(start, end)
    }

    // --- the three names ---

    @Test
    fun `the app tracks exactly three services, named as their users name them`() {
        assertEquals(
            listOf("Claude", "ChatGPT", "Gemini"),
            Provider.entries.map { it.displayName },
        )
        // The key is the persisted value; it never follows a rename of the name.
        assertEquals(
            listOf("claude", "chatgpt", "antigravity"),
            Provider.entries.map { it.key },
        )
    }

    // --- the three vendors ---

    @Test
    fun `each service names the company whose server the app talks to`() {
        assertEquals(
            listOf("Anthropic", "OpenAI", "Google"),
            Provider.entries.map { it.vendor },
        )
    }

    @Test
    fun `a display name is never used where the vendor is meant`() {
        // "Couldn't reach Claude" would name the model, not the company that's down.
        for (provider in Provider.entries) {
            assertTrue(
                "${provider.vendor} must not be the display name",
                provider.vendor != provider.displayName,
            )
        }
    }

    // --- the trademark lines: two marks, two owners, since CCRM-55's drop ---

    @Test
    fun `the About disclaimer names both marks and both owners`() {
        for (claim in listOf(
            "Not affiliated with, endorsed by, or supported by Anthropic or OpenAI.",
            "\\\"Claude\\\" is a trademark of Anthropic, PBC.",
            "\\\"ChatGPT\\\" is a trademark of OpenAI.",
        )) {
            assertTrue("About disclaimer lost: $claim", about.contains(claim))
        }
    }

    /**
     * Google dropped clean: CCRM-55 (Antigravity Account) is dropped, not deferred, so
     * the disclaimer no longer names a provider the app doesn't track. Guards against
     * the Google/Gemini/Antigravity line drifting back in during a future copy edit.
     */
    @Test
    fun `the disclaimer no longer names Google, Gemini or Antigravity`() {
        for (dropped in listOf("Google", "Gemini", "Antigravity")) {
            assertTrue(
                "disclaimer should not mention $dropped",
                !disclaimer.contains(dropped),
            )
        }
    }

    /**
     * The mark drawables are hand-traced from each company's public artwork, so each
     * file has to say whose it is — the header is the only place the provenance
     * lives once the paths are in the repo.
     */
    @Test
    fun `every provider mark drawable credits its owner`() {
        val owners = mapOf(
            "ic_provider_claude.xml" to "Anthropic",
            "ic_provider_chatgpt.xml" to "OpenAI",
            "ic_provider_gemini.xml" to "Google",
        )
        for ((file, owner) in owners) {
            val xml = source("src/main/res/drawable/$file")
            assertTrue("$file doesn't credit $owner", xml.contains(owner))
            assertTrue("$file doesn't say trademark", xml.contains("trademark"))
        }
    }
}
