package com.robin.claudeusage.data

import com.robin.claudeusage.ui.PaceText
import com.robin.claudeusage.ui.UsageEndMark
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/**
 * RUNBOOK-v2.0 Step 6d, CCRM-103 (Hybrid Notification): core §6.3's `markTime`, its
 * readout words and the status-bar glyph, read straight from `spec/core/fixtures/pace-distance/`
 * and `spec/mobile/fixtures/{display,status-icon}/`.
 */
class ShapeGrammarFixturesTest {

    private fun load(path: String): JSONObject =
        JSONObject(javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().readText())

    private fun JSONObject.dbl(name: String): Double? = if (isNull(name)) null else getDouble(name)
    private fun JSONObject.lng(name: String): Long? = if (isNull(name)) null else getLong(name)
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun markTime(c: JSONObject): Pace.MarkTime? =
        Pace.markTime(c.dbl("usedPct"), c.lng("msUntilReset"), c.getLong("windowLengthMs"), c.getLong("nowMs"))

    @Test
    fun `markTime matches every core case`() {
        for (c in load("core/fixtures/pace-distance/cases.json").getJSONArray("markTime").objects()) {
            val name = c.getString("name")
            val got = markTime(c)
            if (c.isNull("expected")) {
                assertNull(name, got)
                continue
            }
            val e = c.getJSONObject("expected")
            val want = when (e.getString("side")) {
                "on" -> Pace.MarkTime.On
                "under" -> Pace.MarkTime.Under(e.getLong("markWasAtUsedAgoMs"))
                else -> Pace.MarkTime.Over(e.getLong("markReachesUsedAtMs"), e.getLong("inMs"))
            }
            assertEquals(name, want, got)
        }
    }

    @Test
    fun `readout words match every mobile case`() {
        for (c in load("mobile/fixtures/display/cases.json").getJSONArray("markReadout").objects()) {
            val exp = if (c.isNull("expected")) null else c.getString("expected")
            val got = PaceText.markReadout(markTime(c), c.getBoolean("use24h"), ZoneId.of(c.getString("zone")), Locale.US)
            assertEquals(c.getString("name"), exp, got)
        }
    }

    @Test
    fun `durations round every part down`() {
        assertEquals("0m", PaceText.behind(59_999L))
        assertEquals("59m", PaceText.behind(3_599_999L))
        assertEquals("1h 0m", PaceText.behind(3_600_000L))
        assertEquals("23h 59m", PaceText.behind(86_399_999L))
        assertEquals("1d 0h", PaceText.behind(86_400_000L))
        assertEquals("6d 23h", PaceText.behind(604_799_999L))
    }

    @Test
    fun `status glyph matches every mobile case`() {
        val glyphs = mapOf(
            "ring" to StatusGlyph.RING, "greyedRing" to StatusGlyph.GREYED_RING,
            "cross" to StatusGlyph.CROSS, "weeklyBar" to StatusGlyph.WEEKLY_BAR,
        )
        for (c in load("mobile/fixtures/status-icon/cases.json").getJSONArray("cases").objects()) {
            val got = StatusIconRule.glyph(
                StatusIconChoice.fromKey(c.getString("choice")), c.getBoolean("hasSession"),
                c.dbl("sessionPct"), c.dbl("weeklyPct"),
            )
            assertEquals(c.getString("name"), glyphs.getValue(c.getString("expected")), got)
        }
    }

    @Test
    fun `new prefs read unknown and absent keys as their defaults`() {
        assertEquals(UsageEndMark.LINE, UsageEndMark.fromKey(null))
        assertEquals(UsageEndMark.LINE, UsageEndMark.fromKey("bead"))
        assertEquals(UsageEndMark.ROUND, UsageEndMark.fromKey("round"))
        assertEquals(UsageEndMark.PACE, UsageEndMark.fromKey("pace"))
        assertEquals(StatusIconChoice.RING, StatusIconChoice.fromKey(null))
        assertEquals(StatusIconChoice.RING, StatusIconChoice.fromKey("dots"))
        assertEquals(StatusIconChoice.WEEKLY_BAR, StatusIconChoice.fromKey("weeklyBar"))
    }
}
