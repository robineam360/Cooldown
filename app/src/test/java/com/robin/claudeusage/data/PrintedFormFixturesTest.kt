package com.robin.claudeusage.data

import com.robin.claudeusage.ui.PaceText
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * CCRM-104 (Pace Line Parity): core §6.3's printed form — the signed Vs pace figure, durations,
 * the reset's day and clock, the short pace line, the pace-mark caption and the Pace map's words —
 * read straight from `spec/core/fixtures/pace-distance/printed.json`, the strings the Mac prints too.
 */
class PrintedFormFixturesTest {

    private val fx: JSONObject by lazy {
        JSONObject(javaClass.classLoader!!.getResourceAsStream("core/fixtures/pace-distance/printed.json")!!.bufferedReader().readText())
    }

    private fun JSONObject.str(name: String): String? = if (isNull(name)) null else getString(name)
    private fun JSONObject.lng(name: String): Long? = if (isNull(name)) null else getLong(name)
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun tone(t: PaceText.VsTone?): String? = when (t) {
        null -> null
        PaceText.VsTone.UNDER -> "under"
        PaceText.VsTone.OVER -> "over"
        PaceText.VsTone.ON -> "ink"
        PaceText.VsTone.USAGE -> "usage"
    }

    @Test
    fun `figure is signed, ring figure unsigned`() {
        for (c in fx.getJSONArray("figure").objects()) {
            val name = c.getString("name")
            val d: Int? = if (c.isNull("vsPace")) null else c.getInt("vsPace")
            assertEquals(name, c.str("text"), d?.let { PaceText.signed(it) })
            assertEquals(name, c.str("ringText"), d?.let { PaceText.unsigned(it) })
            assertEquals(name, c.getString("tone"), tone(PaceText.toneOf(d)))
        }
    }

    @Test
    fun `duration takes the two largest units`() {
        for (c in fx.getJSONArray("duration").objects()) {
            assertEquals(c.getLong("ms").toString(), c.getString("text"), PaceText.duration(c.getLong("ms")))
        }
    }

    @Test
    fun `reset is weekday and clock, weekday dropped later today`() {
        val zone = ZoneOffset.UTC
        for (c in fx.getJSONArray("dayClock").objects()) {
            val now = LocalDateTime.parse(c.getString("nowLocal")).toInstant(zone).toEpochMilli()
            val reset = LocalDateTime.parse(c.getString("resetLocal")).toInstant(zone)
            assertEquals(c.getString("name"), c.getString("text"), PaceText.dayClock(reset, now, c.getBoolean("hour24"), zone, Locale.US))
        }
    }

    @Test
    fun `pace line words and tint`() {
        for (c in fx.getJSONArray("paceLine").objects()) {
            val name = c.getString("name")
            val mt: Pace.MarkTime? = when (c.str("side")) {
                null -> null
                "on" -> Pace.MarkTime.On
                "under" -> Pace.MarkTime.Under(c.getLong("durationMs"))
                else -> Pace.MarkTime.Over(0L, c.getLong("durationMs"))
            }
            val got = PaceText.paceLine(mt, c.getString("reset"))
            assertEquals(name, c.str("text"), got?.text)
            assertEquals(name, c.str("tinted"), got?.tinted)
            assertEquals(name, c.str("tone"), got?.let { tone(it.tone) })
        }
    }

    @Test
    fun `end to end from the numbers`() {
        val zone = ZoneId.of("UTC")
        for (c in fx.getJSONObject("endToEnd").getJSONArray("cases").objects()) {
            val name = c.getString("name")
            val used = c.getDouble("usedPct")
            val now = c.getLong("nowMs")
            val resetMs = c.getLong("resetMs")
            val len = c.getLong("windowLengthMs")
            val untilReset = resetMs - now
            val elapsed = (len - untilReset).toDouble() / len * 100.0
            assertEquals(name, c.getString("figure"), PaceText.vsPaceSigned(used, elapsed))
            val mt = Pace.markTime(used, untilReset, len, now)
            val reset = PaceText.dayClock(java.time.Instant.ofEpochMilli(resetMs), now, false, zone, Locale.US)
            assertEquals(name, c.getString("line"), PaceText.paceLine(mt, reset)?.text)
        }
    }

    @Test
    fun `caption words`() {
        for (c in fx.getJSONArray("caption").objects()) {
            val name = c.getString("name")
            val got = when (c.getString("mode")) {
                "vsPace" -> PaceText.captionVsPace(c.getInt("usedFigure"), c.getInt("mark"))
                "left" -> PaceText.captionLeft(c.getInt("mark"), c.getInt("distance"), c.getString("side"))
                else -> PaceText.captionUsed(c.getInt("mark"), c.getInt("distance"), c.getString("side"))
            }
            assertEquals(name, c.getString("text"), got)
        }
    }

    @Test
    fun `pace map words`() {
        val m = fx.getJSONObject("mapLabels")
        assertEquals(m.getString("aboveDiagonal"), PaceText.MAP_AHEAD)
        assertEquals(m.getString("belowDiagonal"), PaceText.MAP_BEHIND)
        assertEquals(m.getString("diagonal"), PaceText.MAP_EVEN)
        assertEquals(m.getString("xStart"), PaceText.MAP_WEEK_START)
        assertEquals(m.getString("xEnd"), PaceText.MAP_RESET)
    }
}
