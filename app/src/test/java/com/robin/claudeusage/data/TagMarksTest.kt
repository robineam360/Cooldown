package com.robin.claudeusage.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCRM-118 (Header Glyphs), "Tag marks": every `marks` case of `spec/mobile/fixtures/tags/cases.json` — the glyph
 * column, each row's slot (glyph or blank) and the snapshot's column. iOS reads the same file.
 */
class TagMarksTest {

    private val fx: JSONObject by lazy {
        JSONObject(javaClass.classLoader!!.getResourceAsStream("mobile/fixtures/tags/cases.json")!!.bufferedReader().readText())
    }

    @Test
    fun `the marks cases of the shared fixture`() {
        val cases = fx.getJSONArray("marks")
        assertTrue(cases.length() > 0)
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val name = c.getString("name")
            val rows = (0 until c.getJSONArray("rows").length()).map { AccountTag.read(c.getJSONArray("rows").getString(it)) }
            val sw = c.getJSONObject("tagMarks")
            val marks = mapOf(AccountTag.PERSONAL to sw.getBoolean("personal"), AccountTag.WORK to sw.getBoolean("work"))
            val e = c.getJSONObject("expected")
            assertEquals("$name: column", e.getBoolean("column"), TagMarks.column(rows, marks))
            val slots = TagMarks.slots(rows, marks)
            if (e.isNull("slots")) {
                assertEquals("$name: slots", null, slots)
            } else {
                val want = (0 until e.getJSONArray("slots").length()).map { e.getJSONArray("slots").getString(it) == "glyph" }
                assertEquals("$name: slots", want, slots)
            }
            assertEquals("$name: snapshotColumn", e.getBoolean("snapshotColumn"), TagMarks.snapshotColumn(rows, marks))
        }
    }

    @Test
    fun `absent keys read as on and a change is stored per tag`() {
        assertTrue(TagMarks.ALL_ON.values.all { it })
        assertFalse(TagMarks.column(emptyList(), TagMarks.ALL_ON))
        assertEquals("tagMarks.work", TagMarks.key(AccountTag.WORK))
        assertEquals("tagMarks.personal", TagMarks.key(AccountTag.PERSONAL))
    }

    @Test
    fun `the filter segment reads its count for TalkBack`() {
        assertEquals("Personal, 3 accounts", SummaryFilters.description("Personal", 3))
        assertEquals("Work, 1 account", SummaryFilters.description("Work", 1))
        assertEquals("Work", SummaryFilters.description("Work", 0))
    }
}
