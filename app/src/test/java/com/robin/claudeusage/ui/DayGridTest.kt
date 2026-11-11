package com.robin.claudeusage.ui

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * CCRM-107 (Day Grid): the grid lines, now's column and the ordinals' verdicts, read from
 * `spec/core/fixtures/pace-distance/printed-v20.json` → `dayGrid` (core §6.3 "Planned — v2.0 feedback").
 * The fixture gives the label widths (the wireframe's em model at 9.5 sp); the rule is tested with them.
 */
class DayGridTest {

    private val fx: JSONArray by lazy {
        JSONObject(
            javaClass.classLoader!!.getResourceAsStream("core/fixtures/pace-distance/printed-v20.json")!!
                .bufferedReader().readText(),
        ).getJSONArray("dayGrid")
    }

    private fun ms(local: String) = LocalDateTime.parse(local).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `every case matches the fixture`() {
        for (i in 0 until fx.length()) {
            val c = fx.getJSONObject(i)
            val name = c.getString("name")
            val n = c.getInt("columns")
            val start = ms(c.getString("startLocal"))
            val span = if (n == 7) 7 * 86_400_000L else 5 * 3_600_000L
            val now = ms(c.getString("nowLocal"))
            val e = c.getJSONObject("expected")
            assertEquals(name, n, DayGrid.columns(span))

            val vs = e.getJSONArray("verticals")
            assertEquals(name, n - 1, vs.length())
            for (j in 0 until vs.length()) {
                val v = vs.getJSONObject(j)
                assertEquals(name, ms(v.getString("atLocal")), DayGrid.verticalAt(start, span, v.getInt("k"), n))
            }
            val hs = e.getJSONArray("horizontals")
            val got = DayGrid.horizontals(n)
            for (j in 0 until hs.length()) {
                assertEquals(name, hs.getJSONObject(j).getDouble("pct"), got[j], 1e-6)
            }
            assertEquals(name, 100.0, got.last(), 1e-9) // the n-th is the 100% guide's line

            val nowCol = DayGrid.nowColumn(start, span, now, n)
            assertEquals(name, e.getInt("nowColumn"), nowCol)
            val w = c.getDouble("plotWidth").toFloat()
            val nowX = w * (now - start).toFloat() / span
            assertEquals(name, e.getDouble("nowX"), nowX.toDouble(), 1e-3)

            val widths = c.getJSONObject("widths")
            val ords = DayGrid.ordinals(
                n = n,
                plotWidth = w,
                startWidth = widths.getDouble("start").toFloat(),
                endWidth = widths.getDouble("end").toFloat(),
                ordinalWidth = { widths.getDouble("ordinal").toFloat() },
                nowX = if (c.getBoolean("nowLabel")) nowX else null,
                nowWidth = widths.getDouble("now").toFloat(),
                nowColumn = nowCol,
                nowGap = c.getDouble("nowGap").toFloat(),
                endGap = c.getDouble("endGap").toFloat(),
                dropBelowColumn = c.getDouble("dropBelowColumn").toFloat(),
            )
            val want = e.getJSONArray("ordinals")
            assertEquals(name, want.length(), ords.size)
            for (j in 0 until want.length()) {
                val o = want.getJSONObject(j)
                val g = ords[j]
                val label = "$name / ${o.getString("text")}"
                assertEquals(label, o.getString("text"), g.text)
                assertEquals(label, o.getInt("column"), g.column)
                assertEquals(label, o.getBoolean("drawn"), g.drawn)
                if (g.drawn) {
                    assertEquals(label, o.getDouble("centreX"), g.centreX.toDouble(), 1e-3)
                    assertEquals(label, o.getBoolean("bold"), g.bold)
                }
            }
        }
    }

    @Test
    fun `gaps read as the fixture's whys`() {
        // "gives way to now: gap -0.84 < 6" — an overlap is minus its length.
        assertEquals(-0.84f, DayGrid.gap(118.41f, 123.73f, 122.89f, 141.32f), 0.01f)
        // A box inside another: minus the inner box's width ("gap -5.32").
        assertEquals(-5.32f, DayGrid.gap(72.34f, 77.66f, 65.79f, 84.21f), 0.01f)
        // Apart: the space between them ("gap 0.31 < 4").
        assertEquals(0.31f, DayGrid.gap(115.2f, 120.52f, 120.83f, 150f), 0.01f)
    }

    @Test
    fun `the Pace map has seven columns and no bold ordinal`() {
        val ords = DayGrid.ordinals(7, 299f, 50f, 30f, { 6f }, null, 0f, nowColumn = 0)
        assertEquals(listOf("2", "3", "4", "5", "6"), ords.filter { it.drawn }.map { it.text })
        assertEquals(PaceText.MAP_ORDINALS, ords.map { it.text })
        assertEquals(false, ords.any { it.bold })
    }
}
