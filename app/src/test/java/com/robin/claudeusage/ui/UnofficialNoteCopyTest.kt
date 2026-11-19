package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Provider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * CCRM-120 (Unofficial Note): the note's words against `spec/mobile/fixtures/unofficial-note/cases.json`,
 * the same file the iOS tests read, so the two platforms cannot drift.
 */
class UnofficialNoteCopyTest {

    private val fx: JSONObject by lazy {
        JSONObject(javaClass.classLoader!!.getResourceAsStream("mobile/fixtures/unofficial-note/cases.json")!!
            .bufferedReader().readText())
    }

    @Test
    fun `title and body match the shared copy for every provider`() {
        assertEquals(fx.getString("title"), UnofficialNoteCopy.TITLE)
        val companies = fx.getJSONObject("companies")
        for (p in Provider.entries) {
            val company = companies.getString(p.key)
            assertEquals(p.name, company, UnofficialNoteCopy.company(p))
            assertEquals(p.name, fx.getString("bodyTemplate").replace("{company}", company), UnofficialNoteCopy.body(p))
        }
    }

    @Test
    fun `the add footnote matches`() {
        assertEquals(fx.getString("androidAddFootnote"), UnofficialNoteCopy.ADD_FOOTNOTE)
    }

    @Test
    fun `chatgpt shows the note on starting and waiting only`() {
        val stages = fx.getJSONObject("chatgptStages")
        for (s in DeviceCodeStage.entries) {
            assertEquals(s.name, stages.getBoolean(s.name.lowercase()), UnofficialNoteCopy.showsOn(s))
        }
    }

    @Test
    fun `never uses the ruled-out words`() {
        val never = fx.getJSONObject("_meta").getJSONArray("neverWords")
        val all = (Provider.entries.map { UnofficialNoteCopy.body(it) } + UnofficialNoteCopy.TITLE + UnofficialNoteCopy.ADD_FOOTNOTE)
            .joinToString(" ").lowercase()
        val words = Regex("[a-z]+").findAll(all).map { it.value }.toSet()
        for (i in 0 until never.length()) assertFalse(never.getString(i), never.getString(i) in words)
    }
}
