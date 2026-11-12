package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.data.Projection
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

/**
 * CCRM-109 (Quiet Accounts) and CCRM-111 (Map Layers): core §6.3, read from
 * `spec/core/fixtures/pace-distance/printed.json` (`quiet`, `mapLabels`, `callout`, `projectionLayer`) and the
 * mobile foot line from `spec/mobile/fixtures/map-foot/cases.json`.
 * iOS reads the same files (`PaceMapV20Tests`).
 */
class PaceMapV20Test {

    private fun load(path: String) = JSONObject(javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().readText())

    private val fx: JSONObject by lazy { load("core/fixtures/pace-distance/printed.json") }
    private val footFx: JSONObject by lazy { load("mobile/fixtures/map-foot/cases.json") }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.dbl(k: String): Double? = if (isNull(k)) null else getDouble(k)
    private fun JSONObject.lng(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)

    private fun cases(o: JSONObject, key: String): List<JSONObject> {
        val l = o.getJSONArray(key).objects()
        assertTrue("fixture section $key is empty", l.isNotEmpty())
        return l
    }

    private fun mode(s: String) = when (s) {
        "used" -> PaceText.Mode.USED
        "left" -> PaceText.Mode.LEFT
        "vs" -> PaceText.Mode.VS_PACE
        else -> error("mode $s")
    }

    private fun estimate(c: JSONObject): Projection.Estimate? =
        if (c.isNull("estimate")) null
        else c.getJSONObject("estimate").let { Projection.Estimate(0.0, it.lng("hitsLimitAtMs"), it.getDouble("pctAtReset")) }

    private fun spentMatches(c: JSONObject) =
        assertEquals("${c.getString("name")}: spent flag", c.optBoolean("spent", false), Pace.isSpent(c.dbl("usedPct")))

    // ---- CCRM-109

    @Test fun `quiet - an account leaves the map when its row prints 0 percent, every case`() {
        for (c in cases(fx, "quiet")) {
            val name = c.getString("name")
            val used = c.dbl("usedPct")
            assertEquals("$name: row prints", c.str("rowPrints"), PaceMapRules.rowPrints(used))
            val clocked = !(c.has("resetMs") && c.isNull("resetMs"))
            assertEquals("$name: plotted", c.getBoolean("plotted"), PaceMapRules.isPlotted(used, clocked))
        }
    }

    @Test fun `quiet - 0_99 prints 0 and is hidden, 1_0 prints 1 and is plotted, an unclocked reading is never plotted`() {
        assertTrue(PaceMapRules.isQuiet(0.99))
        assertFalse(PaceMapRules.isQuiet(1.0))
        assertTrue(PaceMapRules.isPlotted(1.0, clocked = true))
        assertFalse(PaceMapRules.isPlotted(52.0, clocked = false))
        assertFalse(PaceMapRules.isPlotted(null, clocked = true))
    }

    @Test fun `foot line - the count, the plotted accounts and the empty message, every case`() {
        for (c in cases(footFx, "footLine")) {
            val name = c.getString("name")
            val raw = c.getJSONArray("accounts").objects()
            val accounts = raw.map { PaceMapRules.Account(it.dbl("usedPct"), it.getBoolean("clocked")) }
            val e = c.getJSONObject("expected")
            val plotted = raw.filter { PaceMapRules.isPlotted(it.dbl("usedPct"), it.getBoolean("clocked")) }.map { it.getString("name") }
            val want = e.getJSONArray("plotted").let { a -> (0 until a.length()).map { a.getString(it) } }
            assertEquals("$name: plotted", want, plotted)
            val foot = PaceMapRules.foot(accounts)
            assertEquals("$name: left", e.getString("left"), foot.left)
            assertEquals("$name: count", e.str("count"), foot.count)
            if (e.isNull("empty")) assertNull("$name: no empty message", foot.empty)
            else {
                val m = e.getJSONObject("empty")
                assertEquals("$name: empty", PaceMapRules.Empty(m.getString("title"), m.getString("detail")), foot.empty)
            }
        }
    }

    @Test fun `foot line - nothing plotted with an unread account shows the count and no message (as iOS)`() {
        val foot = PaceMapRules.foot(
            listOf(PaceMapRules.Account(0.0, true), PaceMapRules.Account(null, true)),
        )
        assertEquals("1 at 0% · 1 no reading", foot.count)
        assertNull(foot.empty)
        // an empty list has nothing to count and nothing to say
        assertNull(PaceMapRules.foot(emptyList()).count)
        assertNull(PaceMapRules.foot(emptyList()).empty)
    }

    // ---- CCRM-111

    @Test fun `words - the axis and the foot note`() {
        val w = fx.getJSONObject("mapLabels").getJSONObject("words")
        assertEquals(w.getString("aboveDiagonal"), PaceText.MAP_AHEAD)
        assertEquals(w.getString("belowDiagonal"), PaceText.MAP_BEHIND)
        assertEquals(w.getString("diagonal"), PaceText.MAP_EVEN)
        assertEquals(w.getString("xStart"), PaceText.MAP_WEEK_START)
        assertEquals(w.getString("xEnd"), PaceText.MAP_RESET)
        assertEquals(w.getString("footNote"), PaceText.MAP_FOOT_NOTE)
    }

    @Test fun `figures - Used, Left, Vs pace, spent, truncation, every case`() {
        for (c in cases(fx.getJSONObject("mapLabels"), "figures")) {
            val name = c.getString("name")
            spentMatches(c)
            val got = PaceMapRules.label(c.getString("account"), mode(c.getString("mode")), c.dbl("usedPct"), c.dbl("elapsedPct"))
            assertEquals("$name: $got", c.str("label"), got)
        }
    }

    @Test fun `figures - a spent account in Left mode reads 0 percent, as Robin approved`() {
        assertEquals("Teams 0%", PaceMapRules.label("Teams", PaceText.Mode.LEFT, 100.0, 70.0))
        assertEquals("Teams 100%", PaceMapRules.label("Teams", PaceText.Mode.USED, 100.0, 70.0))
    }

    @Test fun `figure tone - the severity colour for Used and Left, the side for Vs pace`() {
        assertEquals(PaceText.VsTone.USAGE, PaceMapRules.figureTone(PaceText.Mode.USED, 52.0, 39.0))
        assertEquals(PaceText.VsTone.USAGE, PaceMapRules.figureTone(PaceText.Mode.LEFT, 52.0, 39.0))
        assertEquals(PaceText.VsTone.OVER, PaceMapRules.figureTone(PaceText.Mode.VS_PACE, 52.0, 39.0))
        assertEquals(PaceText.VsTone.UNDER, PaceMapRules.figureTone(PaceText.Mode.VS_PACE, 30.0, 49.2))
        assertEquals(PaceText.VsTone.ON, PaceMapRules.figureTone(PaceText.Mode.VS_PACE, 60.5, 60.71))
        assertEquals(PaceText.VsTone.USAGE, PaceMapRules.figureTone(PaceText.Mode.VS_PACE, 100.0, 70.0)) // spent
        assertEquals(PaceText.VsTone.USAGE, PaceMapRules.figureTone(PaceText.Mode.VS_PACE, 12.0, null)) // unclocked
    }

    @Test fun `callout forecast - the third line, every case`() {
        for (c in cases(fx.getJSONObject("callout"), "forecast")) {
            val name = c.getString("name")
            spentMatches(c)
            val got = PaceMapRules.forecast(
                estimate(c), c.dbl("usedPct"), c.lng("resetMs"), c.getLong("nowMs"), c.optBoolean("hour24", false),
                ZoneOffset.UTC, Locale.US,
            )
            assertEquals("$name: $got", c.str("text"), got)
            val runsOut = c.str("text")?.startsWith("100% at ") == true
            assertEquals("$name: runs out", runsOut, PaceMapRules.runsOut(estimate(c), c.dbl("usedPct"), c.lng("resetMs")))
        }
    }

    @Test fun `projection layer - the tail's end and the ring, every case`() {
        for (c in cases(fx, "projectionLayer")) {
            val name = c.getString("name")
            spentMatches(c)
            val got = PaceMapRules.projection(estimate(c), c.dbl("usedPct"), c.lng("resetMs"), Projection.WEEKLY_MS)
            if (c.isNull("layer")) {
                assertNull("$name: expected no tail", got)
                continue
            }
            val layer = c.getJSONObject("layer")
            assertNotNull("$name: expected a tail", got)
            got!!
            assertEquals("$name: x", layer.getDouble("endWeekGonePct"), got.weekGonePct, 1e-6)
            assertEquals("$name: y", layer.getDouble("endUsedPct"), got.usedPct, 1e-6)
            assertEquals("$name: ring", layer.getString("ring") == "red", got.runsOut)
            assertEquals("$name: tail end", layer.getString("tailEnd") == "red", got.runsOut)
        }
    }

    // ---- the placer, in the fixture's dp

    private fun spotName(s: MapLabels.Spot) = if (s == MapLabels.Spot.ABOVE_LEFT) "aboveLeft" else s.name.lowercase()

    private fun rect(a: JSONArray?) = a?.let { MapLabels.Rect(it.getDouble(0), it.getDouble(1), it.getDouble(2), it.getDouble(3)) }

    private fun geometry(c: JSONObject): MapLabels.Geometry {
        val g = c.getJSONObject("geometry")
        val m = g.getJSONObject("nameAreaMargin")
        val s = g.getJSONObject("spots")
        val d = g.getJSONArray("leaderDistances")
        return MapLabels.Geometry(
            g.getDouble("dotHalf"), g.getDouble("nameGap"), g.getDouble("fixedGap"),
            MapLabels.Margin(m.getDouble("left"), m.getDouble("top"), m.getDouble("right"), m.getDouble("bottom")),
            s.getDouble("rightDx"), s.getDouble("belowDx"), s.getDouble("belowDy"), s.getDouble("leftDx"), s.getDouble("aboveDy"),
            (0 until d.length()).map { d.getDouble(it) },
        )
    }

    @Test fun `placement - the app's offsets are the fixture's`() {
        for (c in cases(fx.getJSONObject("mapLabels"), "placement")) {
            assertEquals(c.getString("name"), MapLabels.Geometry.DP, geometry(c))
        }
    }

    @Test fun `placement - the dense snapshot map and the projection case, spot, box, leader and reasons`() {
        for (c in cases(fx.getJSONObject("mapLabels"), "placement")) {
            val name = c.getString("name")
            val plot = c.getJSONObject("plot")
            val w = plot.getDouble("width")
            val h = plot.getDouble("height")
            val g = geometry(c)
            val area = MapLabels.Rect(-g.margin.left, -g.margin.top, w + g.margin.left + g.margin.right, h + g.margin.top + g.margin.bottom)
            val fixed = if (c.has("fixed")) c.getJSONArray("fixed").objects().mapNotNull { rect(it.getJSONArray("box")) } else emptyList()
            val rings = if (c.has("rings")) c.getJSONArray("rings").objects().mapNotNull { rect(it.getJSONArray("box")) } else emptyList()
            val dots = c.getJSONArray("dots").objects().map { d ->
                MapLabels.Dot(
                    d.getString("name"), d.getDouble("weekGonePct") / 100 * w, (100 - d.getDouble("usedPct")) / 100 * h,
                    d.getDouble("nameWidth"), d.getDouble("nameHeight"),
                    if (d.has("tail")) d.getJSONObject("tail").let { t -> t.getDouble("toWeekGonePct") / 100 * w to (100 - t.getDouble("toUsedPct")) / 100 * h } else null,
                )
            }
            val expected = c.getJSONObject("expected")
            val order = expected.getJSONArray("order").let { a -> (0 until a.length()).map { a.getString(it) } }
            assertEquals("$name: order", order, MapLabels.order(dots).map { it.key })
            val got = MapLabels.place(dots, area, g, fixed, rings)
            for (e in expected.getJSONArray("names").objects()) {
                val who = e.getString("name")
                val p = got.first { it.key == who }
                assertEquals("$name: $who spot", e.getString("spot"), spotName(p.spot))
                val box = if (e.isNull("box")) null else rect(e.getJSONArray("box"))
                if (box != null) {
                    val b = p.box
                    assertNotNull("$name: $who expected a box", b)
                    b!!
                    assertEquals("$name: $who x", box.x, b.x, 1e-3)
                    assertEquals("$name: $who y", box.y, b.y, 1e-3)
                    assertEquals("$name: $who w", box.w, b.w, 1e-3)
                    assertEquals("$name: $who h", box.h, b.h, 1e-3)
                } else assertNull("$name: $who expected no box", p.box)
                if (e.isNull("leader")) assertNull("$name: $who expected no leader", p.leader)
                else {
                    val l = e.getJSONObject("leader")
                    val got = p.leader
                    assertNotNull("$name: $who expected a leader", got)
                    got!!
                    assertEquals("$name: $who distance", l.getDouble("distance"), got.distance, 1e-9)
                    assertEquals("$name: $who direction", l.getString("direction"), got.direction.name)
                    val end = l.getJSONArray("end")
                    assertEquals("$name: $who end x", end.getDouble(0), got.endX, 1e-3)
                    assertEquals("$name: $who end y", end.getDouble(1), got.endY, 1e-3)
                }
                val reasons = e.getJSONArray("refusedFor").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                assertEquals("$name: $who reasons ${p.refusedFor.sorted()}", reasons, p.refusedFor)
            }
        }
    }

    @Test fun `leaders stay short - the placer never goes past the longest distance`() {
        var leaders = 0
        for (c in cases(fx.getJSONObject("mapLabels"), "placement")) {
            val g = geometry(c)
            val plot = c.getJSONObject("plot")
            val w = plot.getDouble("width")
            val h = plot.getDouble("height")
            val area = MapLabels.Rect(-g.margin.left, -g.margin.top, w + g.margin.left + g.margin.right, h + g.margin.top + g.margin.bottom)
            val dots = c.getJSONArray("dots").objects().map { d ->
                MapLabels.Dot(
                    d.getString("name"), d.getDouble("weekGonePct") / 100 * w, (100 - d.getDouble("usedPct")) / 100 * h,
                    d.getDouble("nameWidth"), d.getDouble("nameHeight"),
                )
            }
            for (p in MapLabels.place(dots, area, g, emptyList(), emptyList())) {
                val l = p.leader ?: continue
                leaders++
                assertTrue(l.distance <= g.leaderDistances.max())
            }
        }
        assertTrue("the dense case takes leaders", leaders > 0)
    }

    // ---- the callout, the pin, the trail and the tap

    @Test fun `tap - the empty plot clears, the pinned account opens, another account moves the pin`() {
        assertEquals(PaceMapRules.Tap(null, null), PaceMapRules.tap(null, null))
        assertEquals(PaceMapRules.Tap(null, null), PaceMapRules.tap("a", null))
        assertEquals(PaceMapRules.Tap("a", null), PaceMapRules.tap(null, "a"))
        assertEquals(PaceMapRules.Tap("b", null), PaceMapRules.tap("a", "b"))
        assertEquals(PaceMapRules.Tap("a", "a"), PaceMapRules.tap("a", "a"))
    }

    @Test fun `the pin is dropped when its account leaves the map`() {
        assertEquals("a", PaceMapRules.keepPin("a", listOf("a", "b")))
        assertNull(PaceMapRules.keepPin("a", listOf("b")))
        assertNull(PaceMapRules.keepPin(null, listOf("a")))
    }

    @Test fun `hit - the nearest dot wins within 48 dp, a name opens its dot, the empty plot is null`() {
        val d = 2f
        // two dots 11 dp apart: each wins its own side of the midpoint
        val a = PaceMapRules.Target("a", 100f * d, 100f * d, null)
        val b = PaceMapRules.Target("b", 111f * d, 100f * d, null)
        assertEquals("a", PaceMapRules.hit(listOf(a, b), 103f * d, 100f * d, d))
        assertEquals("b", PaceMapRules.hit(listOf(a, b), 108f * d, 100f * d, d))
        // the target is 48 dp square: 24 dp reaches, 25 dp does not
        assertEquals("a", PaceMapRules.hit(listOf(a), 124f * d, 100f * d, d))
        assertNull(PaceMapRules.hit(listOf(a), 125f * d, 100f * d, d))
        // a name 13 dp high takes a 48 dp high target; a tap on it picks its dot
        val named = PaceMapRules.Target("n", 20f * d, 20f * d, MapLabels.Rect(150.0 * d, 14.0 * d, 40.0 * d, 13.0 * d))
        assertEquals("n", PaceMapRules.hit(listOf(named), 170f * d, 14f * d - 15f * d, d)) // above the 13 dp box, inside 48 dp
        assertNull(PaceMapRules.hit(listOf(named), 170f * d, 14f * d - 20f * d, d))
        assertNull(PaceMapRules.hit(emptyList(), 0f, 0f, d))
    }

    @Test fun `lastDay - the trail's last 24 h start where the line crosses a seventh back`() {
        val seventh = 100.0 / 7
        val gone = 60.0
        val pts = listOf(0.0 to 0.0, 30.0 to 10.0, 50.0 to 20.0, 60.0 to 30.0)
        val recent = PaceMapRules.lastDay(pts, gone)
        assertEquals(gone - seventh, recent.first().first, 1e-9)
        // between (50, 20) and the first point at or after the line? the line is at 45.71, between 30 and 50
        val t = ((gone - seventh) - 30.0) / (50.0 - 30.0)
        assertEquals(10.0 + t * 10.0, recent.first().second, 1e-9)
        assertEquals(listOf(50.0 to 20.0, 60.0 to 30.0), recent.drop(1))
        // every point recent: the whole trail
        assertEquals(pts, PaceMapRules.lastDay(pts, 10.0))
        // none recent
        assertTrue(PaceMapRules.lastDay(listOf(0.0 to 0.0, 5.0 to 1.0), 60.0).isEmpty())
    }

    @Test fun `an account with no history, or one reading, has no trail`() {
        // iOS judge F1: the trail ends on the live point, so a line needs two samples behind it.
        val week = 1_000_000L
        val none = PaceMapGeometry.trail(emptyList(), week, week, 40.0, 30.0)
        assertFalse(PaceMapRules.hasTrail(none))
        val one = PaceMapGeometry.trail(listOf(100_000L to 5.0), week, week, 40.0, 30.0)
        assertEquals(2, one.size)
        assertFalse(PaceMapRules.hasTrail(one))
        val two = PaceMapGeometry.trail(listOf(100_000L to 5.0, 200_000L to 12.0), week, week, 40.0, 30.0)
        assertTrue(PaceMapRules.hasTrail(two))
    }

    @Test fun `callout origin - above-right of the dot, flipped left at the right edge and below at the top, clamped`() {
        val d = 1f
        val (x, y) = PaceMapRules.calloutOrigin(100f, 100f, 80f, 54f, 30f, 12f, 329f, 204f, d)
        assertEquals(110f, x, 1e-3f)
        assertEquals(100f - 54f - 8f, y, 1e-3f)
        // right edge: flips left
        val (fx2, _) = PaceMapRules.calloutOrigin(300f, 100f, 80f, 54f, 30f, 12f, 329f, 204f, d)
        assertEquals(300f - 10f - 80f, fx2, 1e-3f)
        // top: below the dot
        val (_, fy) = PaceMapRules.calloutOrigin(100f, 20f, 80f, 54f, 30f, 12f, 329f, 204f, d)
        assertEquals(30f, fy, 1e-3f)
        // clamped to the plot's bottom
        val (_, by) = PaceMapRules.calloutOrigin(100f, 20f, 80f, 300f, 30f, 12f, 329f, 204f, d)
        assertEquals(12f - 6f, by, 1e-3f)
    }

    @Test fun `spoken - name, figure in words, pace line and forecast`() {
        assertEquals(
            "Teams, minus 13 percent, 13h 59m ahead · resets Thu 6:15 PM, ~74% at reset",
            PaceMapRules.spoken("Teams", "−13%", "13h 59m ahead · resets Thu 6:15 PM", "~74% at reset"),
        )
        assertEquals("Personal, 48 percent left", PaceMapRules.spoken("Personal", "48% left", null, null))
        assertEquals("Lab, plus 19 percent", PaceMapRules.spoken("Lab", "+19%", null, null))
        assertEquals("Lab", PaceMapRules.spoken("Lab", null, null, null))
    }
}
