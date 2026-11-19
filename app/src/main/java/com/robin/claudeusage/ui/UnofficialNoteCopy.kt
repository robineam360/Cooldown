package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Provider

/**
 * CCRM-120 (Unofficial Note): the grey "Unofficial integration" box on every sign-in, approved
 * 2026-11-19 as placement A (`design/2026-11-19-unofficial-integration-note.html`). The words are
 * pinned to `spec/mobile/fixtures/unofficial-note/cases.json`, which the iOS tests read too.
 */
object UnofficialNoteCopy {
    const val TITLE = "Unofficial integration"

    /** The Add account sheet's one line, under the divider and above "Try with sample data". */
    const val ADD_FOOTNOTE = "Unofficial integration. Not affiliated with Anthropic, OpenAI or Google."

    /** The company named in the body: Anthropic, OpenAI or Google. */
    fun company(provider: Provider): String = provider.vendor

    fun body(provider: Provider): String {
        val c = company(provider)
        return "Cooldown isn't made by or affiliated with $c. It reads your usage through an unofficial " +
            "connection that $c may change or stop at any time. Cooldown only reads usage: it never " +
            "sends prompts or uses your quota."
    }

    /** ChatGPT's sheet: Starting and Waiting only; the error stages keep their own boxes. */
    fun showsOn(stage: DeviceCodeStage): Boolean =
        stage == DeviceCodeStage.STARTING || stage == DeviceCodeStage.WAITING
}
