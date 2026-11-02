package com.robin.claudeusage.data

import com.robin.claudeusage.ui.PaceText
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RUNBOOK-v2.0 Step 3: the v2.0 logic against the spec's cases, read straight from
 * `spec/core/fixtures/pace/` and `spec/mobile/fixtures/{headline,summary,display}/` —
 * CCRM-96 (Weekly First), CCRM-97 (Pace Number), CCRM-98 (Summary Tab), CCRM-100
 * (Vs Pace Display).
 */
class PaceFixturesTest {

    private fun load(path: String): JSONObject =
        JSONObject(javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().readText())

    private fun JSONObject.dbl(name: String): Double? = if (isNull(name)) null else getDouble(name)
    private fun JSONObject.lng(name: String): Long? = if (isNull(name)) null else getLong(name)
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private val bandKeys = mapOf(
        "spent" to Pace.Band.SPENT, "onPace" to Pace.Band.ON_PACE,
        "roomToSpare" to Pace.Band.ROOM_TO_SPARE, "useBeforeReset" to Pace.Band.USE_BEFORE_RESET,
        "slowDown" to Pace.Band.SLOW_DOWN, "nearlyOut" to Pace.Band.NEARLY_OUT,
    )

    @Test
    fun `pace and band match every core case`() {
        val cases = load("core/fixtures/pace/cases.json").getJSONArray("pace").objects()
        assertTrue(cases.size >= 20)
        for (c in cases) {
            val u = c.getDouble("usedPct")
            val g = c.getDouble("elapsedPct")
            val exp = c.getJSONObject("expected")
            assertEquals(c.getString("name"), exp.getDouble("pace"), Pace.pace(u, g), 0.01)
            assertEquals(c.getString("name"), bandKeys.getValue(exp.getString("band")), Pace.band(u, g))
        }
    }

    @Test
    fun `allowance matches every core case`() {
        for (c in load("core/fixtures/pace/cases.json").getJSONArray("allowance").objects()) {
            val got = Pace.allowance(c.getDouble("usedPct"), c.getLong("msUntilReset"))
            val name = c.getString("name")
            if (c.isNull("expected")) {
                assertNull(name, got)
                continue
            }
            val exp = c.getJSONObject("expected")
            when (exp.getString("mode")) {
                "perDay" -> assertEquals(name, exp.getDouble("pctPerDay"), (got as Pace.Allowance.PerDay).pctPerDay, 0.01)
                "hours" -> {
                    got as Pace.Allowance.Hours
                    assertEquals(name, exp.getDouble("pctLeft"), got.pctLeft, 0.01)
                    assertEquals(name, exp.getLong("msLeft"), got.msLeft)
                }
                else -> error("unknown mode in $name")
            }
        }
    }

    @Test
    fun `sessionBinding matches every core case`() {
        for (c in load("core/fixtures/pace/cases.json").getJSONArray("sessionBinding").objects()) {
            assertEquals(
                c.getString("name"), c.getBoolean("expected"),
                Pace.sessionBinding(c.dbl("sessionPct"), c.dbl("weeklyPct"), c.getBoolean("sessionHitsLimitBeforeReset")),
            )
        }
    }

    @Test
    fun `headline resolution matches every mobile case`() {
        val surfaces = mapOf(
            "notification" to HeadlineSurface.NOTIFICATION,
            "statusGauge" to HeadlineSurface.STATUS_GAUGE,
            "newWidget" to HeadlineSurface.NEW_WIDGET,
        )
        for (c in load("mobile/fixtures/headline/cases.json").getJSONArray("cases").objects()) {
            val got = HeadlineRule.lead(
                Headline.fromKey(c.getString("setting")),
                surfaces.getValue(c.getString("surface")),
                c.getBoolean("hasSession"),
                c.getBoolean("sessionBinding"),
            )
            val exp = if (c.getString("expected") == "session") WindowKind.SESSION else WindowKind.WEEKLY
            assertEquals(c.getString("name"), exp, got)
        }
    }

    @Test
    fun `card order follows the 5-hour Headline only`() {
        assertEquals(WindowKind.SESSION, HeadlineRule.lead(Headline.FIVE_HOUR, HeadlineSurface.CARD_ORDER, true, false))
        assertEquals(WindowKind.WEEKLY, HeadlineRule.lead(Headline.AUTO, HeadlineSurface.CARD_ORDER, true, true))
        assertEquals(WindowKind.WEEKLY, HeadlineRule.lead(Headline.FIVE_HOUR, HeadlineSurface.CARD_ORDER, false, false))
        assertEquals(Headline.WEEKLY, Headline.fromKey(null))
        assertEquals(Headline.WEEKLY, Headline.fromKey("bogus"))
    }

    @Test
    fun `summary ranking matches every mobile case`() {
        val groups = mapOf(
            "eligible" to SummaryRank.Group.ELIGIBLE, "held" to SummaryRank.Group.HELD,
            "spent" to SummaryRank.Group.SPENT, "noReading" to SummaryRank.Group.NO_READING,
        )
        val fx = load("mobile/fixtures/summary/cases.json")
        for (c in fx.getJSONArray("ranking").objects()) {
            val entries = c.getJSONArray("accounts").objects().map { a ->
                SummaryRank.Entry(
                    key = a.getString("name"),
                    usedPct = a.dbl("usedPct"),
                    elapsedPct = a.dbl("elapsedPct"),
                    msUntilReset = a.lng("msUntilReset"),
                    sessionBinding = a.getBoolean("sessionBinding"),
                    sessionMsUntilReset = a.lng("sessionMsUntilReset"),
                )
            }
            val got = SummaryRank.rank(entries).map { it.entry.key to it.group }
            val exp = c.getJSONArray("expected").objects().map { it.getString("name") to groups.getValue(it.getString("group")) }
            assertEquals(c.getString("name"), exp, got)
        }
        for (c in fx.getJSONArray("tab").objects()) {
            val n = c.getInt("accountCount")
            assertEquals(c.getString("name"), c.getBoolean("expectedTabShown"), SummaryRank.tabShown(n))
            assertEquals(c.getString("name"), c.getBoolean("expectedToggleShown"), SummaryRank.tabShown(n))
            assertEquals(
                c.getString("name"), c.getString("expectedOpensOn") == "summary",
                SummaryRank.opensOnSummary(n, c.getBoolean("openOnSummary")),
            )
        }
    }

    @Test
    fun `display figures, captions and caption side match every mobile case`() {
        val fx = load("mobile/fixtures/display/cases.json")
        for (c in fx.getJSONArray("vsPaceFigure").objects()) {
            val g = if (c.getBoolean("hasResetClock")) c.dbl("elapsedPct") else null
            assertEquals(c.getString("name"), c.getString("expected"), PaceText.vsPaceFigure(c.dbl("usedPct"), g))
        }
        for (c in fx.getJSONArray("caption").objects()) {
            val g = if (c.getBoolean("hasResetClock")) c.dbl("elapsedPct") else null
            val exp = if (c.isNull("expected")) null else c.getString("expected")
            assertEquals(c.getString("name"), exp, PaceText.caption(PaceText.Mode.fromKey(c.getString("mode")), c.dbl("usedPct"), g))
        }
        for (c in fx.getJSONArray("captionSide").objects()) {
            assertEquals(
                c.getString("name"), c.getString("expectedSide") == "right",
                PaceText.captionRightOfMark(
                    c.getDouble("barWidthDp").toFloat(), c.getDouble("markPct"), c.getDouble("captionWidthDp").toFloat(),
                ),
            )
        }
    }

    @Test
    fun `core distance numbers match every case`() {
        val fx = load("core/fixtures/pace-distance/cases.json")
        for (c in fx.getJSONArray("vsPace").objects()) {
            val g = if (c.getBoolean("hasResetClock")) c.dbl("elapsedPct") else null
            val exp = if (c.isNull("expected")) null else c.getInt("expected")
            assertEquals(c.getString("name"), exp, Pace.vsPace(c.dbl("usedPct"), g))
        }
        for (c in fx.getJSONArray("markDistance").objects()) {
            val u = c.dbl("usedPct")
            val g = if (c.getBoolean("hasResetClock")) c.dbl("elapsedPct") else null
            val cap = PaceText.caption(PaceText.Mode.USED, u, g)
            if (c.isNull("expected")) {
                assertNull(c.getString("name"), cap)
                continue
            }
            val e = c.getJSONObject("expected")
            val words = when (e.getString("side")) {
                "on" -> "on it"
                "under" -> "${e.getInt("points")} under"
                else -> "${e.getInt("points")} over"
            }
            assertEquals(c.getString("name"), "${e.getInt("markPct")}% pace mark · $words", cap)
        }
    }

    @Test
    fun `mobile ranking cases are the core ranking cases`() {
        val core = load("core/fixtures/ranking/cases.json").getJSONArray("ranking").toString()
        val mobile = load("mobile/fixtures/summary/cases.json").getJSONArray("ranking").toString()
        assertEquals(core, mobile)
    }

    @Test
    fun `pace words and allowance copy`() {
        assertEquals("Slow down · ≈11%/day", PaceText.tight(Pace.Band.SLOW_DOWN, Pace.Allowance.PerDay(11.22)))
        assertEquals("<1%/day", PaceText.allowance(Pace.Allowance.PerDay(0.6)))
        assertEquals("40% left, 6h", PaceText.allowance(Pace.Allowance.Hours(40.0, 21_600_000L)))
        assertNull(PaceText.tight(Pace.Band.SPENT, null))
        assertEquals("10 under the pace mark (60%)", PaceText.notificationDistance(PaceText.Mode.USED, 50.0, 60.0))
        assertEquals("52% used (mark 39%)", PaceText.notificationDistance(PaceText.Mode.VS_PACE, 52.4, 39.0))
        assertEquals("10 to spare · pace mark 40%", PaceText.notificationDistance(PaceText.Mode.LEFT, 50.0, 60.0))
        assertEquals("20 over · pace mark 40%", PaceText.notificationDistance(PaceText.Mode.LEFT, 80.0, 60.0))
        assertEquals("On the pace mark (60%)", PaceText.notificationDistance(PaceText.Mode.USED, 59.6, 60.0))
        assertEquals("On the pace mark (40%)", PaceText.notificationDistance(PaceText.Mode.LEFT, 59.6, 60.0))
        assertEquals("40% left, <1h", PaceText.allowance(Pace.Allowance.Hours(40.0, 20 * 60_000L)))
        assertEquals("40% left, 1h", PaceText.allowance(Pace.Allowance.Hours(40.0, 40 * 60_000L)))
        assertNull(Pace.bandOrNull(50.0, null))
        assertNull(Pace.bandOrNull(null, 50.0))
    }

    @Test
    fun `weekly-first notice is owed only to installs that had accounts`() {
        assertEquals(WeeklyFirstNotice.PENDING, WeeklyFirstNotice.settle(true))
        assertEquals(WeeklyFirstNotice.DONE, WeeklyFirstNotice.settle(false))
    }
}
