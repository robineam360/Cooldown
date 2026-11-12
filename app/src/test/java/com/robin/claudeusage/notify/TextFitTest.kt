package com.robin.claudeusage.notify

import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.ui.PaceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt

/**
 * CCRM-104 (Pace Line Parity), wireframe D10 and Astra C1-3/C1-4: the width estimator the
 * notification picks pace-line and caption forms with (RemoteViews cannot measure text), checked
 * against the wireframe's §8 numbers and at the three font scales.
 */
class TextFitTest {

    private val now = Instant.parse("2026-11-02T06:00:00Z") // a Monday, 6:00 AM
    private val wed = Instant.parse("2026-11-04T12:30:00Z") // another day: Wed 12:30 PM
    private val sat = Instant.parse("2026-11-07T08:00:00Z") // another day: Sat 8:00 AM
    private val today815 = Instant.parse("2026-11-02T08:15:00Z") // later today
    private val today257 = Instant.parse("2026-11-02T14:57:00Z") // later today
    private val us = Locale.US

    private fun forms(mt: Pace.MarkTime?, r: Instant) =
        PaceText.paceLineForms(mt, r, now.toEpochMilli(), false, ZoneOffset.UTC, us)

    // The wireframe's §4 fit table (rev B, CCRM-108 (Reset Named)), its five columns.
    private val behind = forms(Pace.MarkTime.Under((6 * 1440 + 23 * 60) * 60_000L), wed) // 6d 23h behind
    private val ahead = forms(Pace.MarkTime.Over(0, (23 * 60 + 59) * 60_000L), wed) // 23h 59m ahead
    private val behindToday = forms(Pace.MarkTime.Under((5 * 60 + 45) * 60_000L), today815) // 5h 45m behind
    private val aheadToday = forms(Pace.MarkTime.Over(0, 57 * 60_000L), today257) // 57m ahead
    private val onMark = forms(Pace.MarkTime.On, sat)
    private val table = listOf(behind, ahead, behindToday, aheadToday, onMark)

    private fun dp(line: PaceText.PaceLine, sp: Float = 12f, scale: Float = 1f) =
        TextFit.widthDp(TextFit.em(line), sp, scale).roundToInt()

    @Test
    fun `the forms are the wireframe's lines - a bare clock always means today`() {
        assertEquals(
            listOf("23h 59m ahead · resets Wed 12:30 PM", "23h 59m ahead · Wed 12:30 PM", "23h 59m ahead · Wed", "23h 59m ahead"),
            ahead.map { it.text },
        )
        assertEquals(
            listOf("5h 45m behind · resets 8:15 AM", "5h 45m behind · 8:15 AM", "5h 45m behind"),
            behindToday.map { it.text },
        )
        assertEquals(
            listOf("On the mark · resets Sat 8:00 AM", "On the mark · Sat 8:00 AM", "On the mark · Sat", "On the mark"),
            onMark.map { it.text },
        )
    }

    @Test
    fun `the estimator reproduces the wireframe's widths at 12 sp`() {
        assertEquals(109, dp(behind[2])) // "6d 23h behind · Wed"
        assertEquals(117, dp(ahead[2])) // "23h 59m ahead · Wed"
        assertEquals(134, dp(behindToday[1])) // "5h 45m behind · 8:15 AM"
        assertEquals(113, dp(aheadToday[1])) // "57m ahead · 2:57 PM"
        assertEquals(138, dp(onMark[1])) // "On the mark · Sat 8:00 AM"
        assertEquals(149, dp(aheadToday[0])) // "57m ahead · resets 2:57 PM"
    }

    @Test
    fun `the tinted span is 3 percent wider, a line on the mark has none`() {
        assertEquals(TextFit.em("23h 59m ahead") * 1.03, TextFit.em(ahead.last()), 1e-9)
        assertEquals(TextFit.em("On the mark"), TextFit.em(onMark.last()), 1e-9)
        assertEquals(null, onMark.last().tinted)
    }

    @Test
    fun `signs are 0_58 em and a small percent 0_46`() {
        assertEquals(0.58, TextFit.em("+"), 1e-9)
        assertEquals(0.58, TextFit.em("−"), 1e-9)
        assertEquals(0.46, TextFit.em("%"), 1e-9)
    }

    /** One row of the wireframe's table: each column's pick at font scale 1.0, 1.3 and 2.0, "text@sp". */
    private fun row(col: Float): List<List<String>> = table.map { f ->
        listOf(1f, 1.3f, 2f).map { scale -> TextFit.paceLine(f, col, 12f, scale)!!.let { "${it.form.text}@${it.sp.toInt()}" } }
    }

    @Test
    fun `the notification columns follow the wireframe's fit table`() {
        val notification = listOf(
            listOf("6d 23h behind · Wed@12", "6d 23h behind@12", "6d 23h behind@10"),
            listOf("23h 59m ahead · Wed@12", "23h 59m ahead@12", "23h 59m ahead@10"),
            listOf("5h 45m behind · 8:15 AM@12", "5h 45m behind@12", "5h 45m behind@10"),
            listOf("57m ahead · 2:57 PM@12", "57m ahead@12", "57m ahead@12"),
            listOf("On the mark · Sat 8:00 AM@12", "On the mark · Sat@12", "On the mark@12"),
        )
        assertEquals(notification, row(TextFit.COLLAPSED_COLUMN_DP)) // collapsed single, 142
        assertEquals(notification, row(TextFit.EXPANDED_COLUMN_DP)) // expanded single, 141
        assertEquals( // Duet expanded, new (24 sp figure), 186 — CCRM-105 (Duet Stack), wireframe §4 B table
            listOf(
                listOf("6d 23h behind · Wed 12:30 PM@12", "6d 23h behind · Wed@12", "6d 23h behind@12"),
                listOf("23h 59m ahead · Wed 12:30 PM@12", "23h 59m ahead · Wed@12", "23h 59m ahead@12"),
                listOf("5h 45m behind · resets 8:15 AM@12", "5h 45m behind · 8:15 AM@12", "5h 45m behind@12"),
                listOf("57m ahead · resets 2:57 PM@12", "57m ahead · 2:57 PM@12", "57m ahead@12"),
                listOf("On the mark · resets Sat 8:00 AM@12", "On the mark · Sat 8:00 AM@12", "On the mark · Sat@12"),
            ),
            row(TextFit.DUET_EXPANDED_COLUMN_DP),
        )
        assertEquals(186f, TextFit.DUET_EXPANDED_COLUMN_DP) // 159 + 2.25 em × 12 sp
    }

    @Test
    fun `a wide column prints the full line - the reset named`() {
        // Judge A1 F3: a wide notification (328 dp) keeps every word at 1.0 and 1.3; 184 dp drops "resets", then the clock.
        assertEquals("23h 59m ahead · resets Wed 12:30 PM", TextFit.paceLine(ahead, 328f, 12f, 1f)!!.form.text)
        assertEquals("23h 59m ahead · resets Wed 12:30 PM", TextFit.paceLine(ahead, 328f, 12f, 1.3f)!!.form.text)
        assertEquals("23h 59m ahead · Wed 12:30 PM", TextFit.paceLine(ahead, 184f, 12f, 1f)!!.form.text)
        assertEquals("23h 59m ahead · Wed", TextFit.paceLine(ahead, 184f, 12f, 1.3f)!!.form.text)
    }

    @Test
    fun `a reset days away keeps its weekday on the cover - never a bare clock`() {
        // RUNBOOK A1: the collapsed line at the cover-like width shows "· Sun" for a reset days away, not "· 9:30 PM".
        val tue = Instant.parse("2026-11-10T14:00:00Z")
        val forms = PaceText.paceLineForms(
            Pace.MarkTime.Under((5 * 60 + 45) * 60_000L), Instant.parse("2026-11-15T21:30:00Z"),
            tue.toEpochMilli(), false, ZoneOffset.UTC, us,
        )
        assertEquals(
            "5h 45m behind · Sun",
            TextFit.paceLine(forms, TextFit.COLLAPSED_COLUMN_DP, 12f, 1f)!!.form.text,
        )
        // No form a reset days away prints is a bare clock.
        forms.forEach { assertTrue(it.text, it.text == "5h 45m behind" || it.text.contains("Sun")) }
    }

    @Test
    fun `CCBG-78 the collapsed column fits the Fold 7 cover's 147 dp`() {
        // Measured on the Fold 7 cover (One UI 8): the collapsed sub line is 385 px at 2.625, about 147 dp.
        assertTrue(TextFit.COLLAPSED_COLUMN_DP <= 147f * 0.97f) // room for the estimator's ~2% error
    }

    @Test
    fun `a floor that fits at no size stays the floor at 10 sp for the view to ellipsize`() {
        val p = TextFit.paceLine(ahead, 60f, 12f, 2f)!!
        assertEquals("23h 59m ahead", p.form.text)
        assertEquals(10f, p.sp, 0f)
    }

    @Test
    fun `per string, not per size class - the shorter line keeps more`() {
        // "6d 23h behind · Wed 12:30 PM" is narrower than "23h 59m ahead · Wed 12:30 PM".
        assertEquals("6d 23h behind · Wed 12:30 PM", TextFit.paceLine(behind, 165f, 12f, 1f)!!.form.text)
        assertEquals("23h 59m ahead · Wed", TextFit.paceLine(ahead, 165f, 12f, 1f)!!.form.text)
    }

    // ---- the caption (C1-3) ----

    private fun cap(mode: PaceText.Mode, used: Double, elapsed: Double, avail: Float, scale: Float = 1f): Pair<String, Float> {
        val p = TextFit.caption(PaceText.captionForms(mode, used, elapsed), avail, 12f, scale)!!
        return p.form to p.sp
    }

    @Test
    fun `the caption keeps its words at 184 and falls back to the distance clause at 141`() {
        // Left, 61% pace mark, 13 to spare: about 154 dp at 12 sp against a 141 dp column (C1-3).
        assertEquals("61% pace mark · 13 to spare" to 12f, cap(PaceText.Mode.LEFT, 26.0, 39.0, 184f))
        assertEquals("13 to spare" to 12f, cap(PaceText.Mode.LEFT, 26.0, 39.0, 141f))
        assertEquals("39% pace mark · 13 over" to 12f, cap(PaceText.Mode.USED, 52.0, 39.0, 184f))
        assertEquals("13 over" to 12f, cap(PaceText.Mode.USED, 52.0, 39.0, 120f))
        assertEquals("13 under" to 12f, cap(PaceText.Mode.USED, 26.0, 39.0, 100f))
        assertEquals("On the mark" to 12f, cap(PaceText.Mode.USED, 39.0, 39.0, 80f))
    }

    @Test
    fun `Vs pace caption falls back to the usage alone`() {
        assertEquals("52% used · 39% pace mark" to 12f, cap(PaceText.Mode.VS_PACE, 52.0, 39.0, 184f))
        assertEquals("52% used" to 12f, cap(PaceText.Mode.VS_PACE, 52.0, 39.0, 120f))
    }

    @Test
    fun `the caption steps down to 10 sp at large text, wording unchanged`() {
        assertEquals("13 over" to 10f, cap(PaceText.Mode.USED, 52.0, 39.0, 60f, scale = 2f))
    }

    @Test
    fun `no caption forms, no pick`() {
        assertEquals(null, TextFit.caption(PaceText.captionForms(PaceText.Mode.USED, 100.0, 80.0), 141f, 12f, 1f))
    }

    // --- CCBG-80 (Fixed Pace Columns): the column derived from the screen, addendum §N1 F2 ----------

    /** AOSP's FontScaleConverterFactory (Android 14+), the table the page's F2 arithmetic uses, as the system would. */
    private val nonLinear = mapOf(
        1f to floatArrayOf(8f, 10f, 12f, 14f, 18f, 20f, 24f, 30f, 100f),
        1.3f to floatArrayOf(10.4f, 13f, 15.6f, 18.8f, 21.6f, 23.6f, 26.4f, 30f, 100f),
        2f to floatArrayOf(16f, 20f, 24f, 26f, 30f, 34f, 36f, 38f, 100f),
    )

    private fun aosp(scale: Float) = TextFit.SpToDp { sp ->
        val from = nonLinear.getValue(1f)
        val to = nonLinear.getValue(scale)
        var dp = sp
        for (i in 1 until from.size) if (sp <= from[i]) {
            dp = to[i - 1] + (to[i] - to[i - 1]) * (sp - from[i - 1]) / (from[i] - from[i - 1]); break
        }
        dp
    }

    private enum class Layout { SC, SE, DE }

    /** One cell of the page's derived-column table: layout, smallest width, font, the column, the form it takes. */
    private data class Col(val layout: Layout, val sw: Int, val scale: Float, val column: Float, val sp: Float, val form: String)

    // The page's table (F2), beside a signed two-digit figure ("−13%"), the ring's time line "in 2h 20m" in dp,
    // and "23h 59m ahead · resets Wed 12:30 PM": 360 · 411 · 420 · 750 dp at font 1.0 / 1.3 / 2.0, both layouts.
    private val columns = listOf(
        Col(Layout.SC, 360, 1f, 82.53f, 10f, "23h 59m ahead"),
        Col(Layout.SC, 360, 1.3f, 82.53f, 10f, "23h 59m ahead"),
        Col(Layout.SC, 360, 2f, 66.63f, 10f, "23h 59m ahead"),
        Col(Layout.SC, 411, 1f, 133.53f, 12f, "23h 59m ahead · Wed"),
        Col(Layout.SC, 411, 1.3f, 133.53f, 12f, "23h 59m ahead"),
        Col(Layout.SC, 411, 2f, 117.63f, 10f, "23h 59m ahead"),
        Col(Layout.SC, 420, 1f, 142.53f, 12f, "23h 59m ahead · Wed"),
        Col(Layout.SC, 420, 1.3f, 142.53f, 12f, "23h 59m ahead"),
        Col(Layout.SC, 420, 2f, 126.63f, 10f, "23h 59m ahead"),
        Col(Layout.SC, 750, 1f, 214.53f, 12f, "23h 59m ahead · resets Wed 12:30 PM"),
        Col(Layout.SC, 750, 1.3f, 214.53f, 12f, "23h 59m ahead · Wed"),
        Col(Layout.SC, 750, 2f, 198.63f, 12f, "23h 59m ahead"),
        Col(Layout.SE, 360, 1f, 77.36f, 10f, "23h 59m ahead"),
        Col(Layout.SE, 360, 1.3f, 77.36f, 10f, "23h 59m ahead"),
        Col(Layout.SE, 360, 2f, 64.26f, 10f, "23h 59m ahead"),
        Col(Layout.SE, 411, 1f, 128.36f, 12f, "23h 59m ahead · Wed"),
        Col(Layout.SE, 411, 1.3f, 128.36f, 12f, "23h 59m ahead"),
        Col(Layout.SE, 411, 2f, 115.26f, 10f, "23h 59m ahead"),
        Col(Layout.SE, 420, 1f, 137.36f, 12f, "23h 59m ahead · Wed"),
        Col(Layout.SE, 420, 1.3f, 137.36f, 12f, "23h 59m ahead"),
        Col(Layout.SE, 420, 2f, 124.26f, 10f, "23h 59m ahead"),
        Col(Layout.SE, 750, 1f, 209.36f, 12f, "23h 59m ahead · Wed 12:30 PM"),
        Col(Layout.SE, 750, 1.3f, 209.36f, 12f, "23h 59m ahead · Wed"),
        Col(Layout.SE, 750, 2f, 196.26f, 12f, "23h 59m ahead"),
        Col(Layout.DE, 360, 1f, 118.28f, 12f, "23h 59m ahead"),
        Col(Layout.DE, 360, 1.3f, 113.37f, 10f, "23h 59m ahead"),
        Col(Layout.DE, 360, 2f, 93.72f, 10f, "23h 59m ahead"),
        Col(Layout.DE, 411, 1f, 169.28f, 12f, "23h 59m ahead · Wed"),
        Col(Layout.DE, 411, 1.3f, 164.37f, 12f, "23h 59m ahead · Wed"),
        Col(Layout.DE, 411, 2f, 144.72f, 10f, "23h 59m ahead"),
        Col(Layout.DE, 420, 1f, 178.28f, 12f, "23h 59m ahead · Wed 12:30 PM"),
        Col(Layout.DE, 420, 1.3f, 173.37f, 12f, "23h 59m ahead · Wed"),
        Col(Layout.DE, 420, 2f, 153.72f, 10f, "23h 59m ahead"),
        Col(Layout.DE, 750, 1f, 250.28f, 12f, "23h 59m ahead · resets Wed 12:30 PM"),
        Col(Layout.DE, 750, 1.3f, 245.37f, 12f, "23h 59m ahead · Wed 12:30 PM"),
        Col(Layout.DE, 750, 2f, 225.72f, 12f, "23h 59m ahead"),
    )

    private fun derived(c: Col): Float = when (c.layout) {
        Layout.SC -> TextFit.collapsedColumnDp(c.sw, hasRing = true, figure = "−13%", spToDp = aosp(c.scale))
        Layout.SE -> TextFit.expandedColumnDp(c.sw, true, "in 2h 20m", "−13%", TextFit.SINGLE_EXPANDED_FIGURE_SP, aosp(c.scale))
        Layout.DE -> TextFit.expandedColumnDp(c.sw, true, "in 2h 20m", "−13%", TextFit.DUET_EXPANDED_FIGURE_SP, aosp(c.scale))
    }

    @Test
    fun `the derived columns are the page's table`() {
        assertEquals(36, columns.size)
        for (c in columns) assertEquals("${c.layout} ${c.sw} dp @ ${c.scale}", c.column, derived(c), 0.02f)
    }

    @Test
    fun `in its derived column the pace line takes the page's form - the weekday drops where the column is short`() {
        for (c in columns) {
            val pick = TextFit.paceLine(ahead, derived(c) * TextFit.FIT_FACTOR, 12f, c.scale)!!
            val at = "${c.layout} ${c.sw} dp @ ${c.scale}"
            assertEquals(at, c.form, pick.form.text)
            assertEquals(at, c.sp, pick.sp)
        }
    }

    @Test
    fun `the column is never wider than today's constants until Step 3 measures the Fold 7`() {
        val wide = columns.filter { it.sw == 750 && it.scale == 1f }
        assertEquals(TextFit.COLLAPSED_COLUMN_DP, TextFit.fitDp(derived(wide.first { it.layout == Layout.SC }), TextFit.COLLAPSED_COLUMN_DP))
        assertEquals(TextFit.EXPANDED_COLUMN_DP, TextFit.fitDp(derived(wide.first { it.layout == Layout.SE }), TextFit.EXPANDED_COLUMN_DP))
        assertEquals(TextFit.DUET_EXPANDED_COLUMN_DP, TextFit.fitDp(derived(wide.first { it.layout == Layout.DE }), TextFit.DUET_EXPANDED_COLUMN_DP))
        // A 360 dp phone's Duet header beside "−13%": 118 dp derived (One UI measured 134 beside "−1%",
        // CCBG-91 (One UI Expanded Column)), so the line picked for 186 is gone.
        val narrow = derived(columns.first { it.layout == Layout.DE && it.sw == 360 && it.scale == 1f })
        assertEquals(118.28f * TextFit.FIT_FACTOR, TextFit.fitDp(narrow, TextFit.DUET_EXPANDED_COLUMN_DP), 0.05f)
    }

    @Test
    fun `the column moves with the figure and the ring`() {
        val one = aosp(1f)
        // F2: 82 dp beside "−13%", 102 beside "52%" (the single collapsed row on a 360 dp phone).
        assertEquals(82.5f, TextFit.collapsedColumnDp(360, true, "−13%", one), 0.1f)
        assertEquals(TextFit.collapsedColumnDp(360, true, "52%", one) - TextFit.collapsedColumnDp(360, true, "−13%", one),
            ((TextFit.figureEm("−13%") - TextFit.figureEm("52%")) * 32).toFloat(), 0.01f)
        // No 5h: no ring box and one gap fewer (the column gains 58 dp).
        assertEquals(58f, TextFit.collapsedColumnDp(360, false, "52%", one) - TextFit.collapsedColumnDp(360, true, "52%", one), 0.01f)
        // A time line wider than the ring widens its box: "in 2h 20m" at 11 dp is 48.6 dp.
        assertEquals(48.62f, (TextFit.em("in 2h 20m") * TextFit.TIME_LINE_DP).toFloat(), 0.01f)
        assertEquals("—", "—"); assertEquals(1.0, TextFit.figureEm("—"), 0.0)
    }

    @Test
    fun `Weekly gives before the name when the column cannot hold both`() {
        val one = aosp(1f)
        val two = aosp(2f)
        val col360 = TextFit.expandedColumnDp(360, true, "in 2h 20m", "−13%", TextFit.DUET_EXPANDED_FIGURE_SP, one)
        // CCBG-91: One UI's 360 dp header beside a two-digit signed figure keeps "· Weekly" after a short name only.
        assertTrue(TextFit.titleKeepsWindow("Pro · Weekly", col360, one.dp(TextFit.EXPANDED_TITLE_SP)))
        assertFalse(TextFit.titleKeepsWindow("Claude · Weekly", col360, one.dp(TextFit.EXPANDED_TITLE_SP)))
        assertFalse(TextFit.titleKeepsWindow("Personal Max 20x · Weekly", col360, one.dp(TextFit.EXPANDED_TITLE_SP)))
        val col360x2 = TextFit.expandedColumnDp(360, true, "in 2h 20m", "−13%", TextFit.DUET_EXPANDED_FIGURE_SP, two)
        assertFalse(TextFit.titleKeepsWindow("Teams · Weekly", col360x2, two.dp(TextFit.EXPANDED_TITLE_SP)))
        val col750 = TextFit.expandedColumnDp(750, true, "in 2h 20m", "−13%", TextFit.DUET_EXPANDED_FIGURE_SP, one)
        assertTrue(TextFit.titleKeepsWindow("Personal Max 20x · Weekly", col750, one.dp(TextFit.EXPANDED_TITLE_SP)))
    }

    @Test
    fun `on a wide shade Weekly gives against the Duet label's own 142 dp bound, not only the column (judge F2)`() {
        val one = aosp(1f)
        val label = one.dp(TextFit.EXPANDED_TITLE_SP)
        for (sw in listOf(411, 600)) {
            val col = TextFit.expandedColumnDp(sw, true, "in 2h 20m", "−13%", TextFit.DUET_EXPANDED_FIGURE_SP, one)
            assertTrue("column alone at $sw", TextFit.titleKeepsWindow("Personal Max 20x · Weekly", col, label) || sw == 411)
            assertFalse("bounded at $sw", TextFit.titleKeepsWindow("Personal Max 20x · Weekly", col, label, TextFit.duetLabelMaxDp(true)))
            assertTrue("short name at $sw", TextFit.titleKeepsWindow("Teams · Weekly", col, label, TextFit.duetLabelMaxDp(true)))
        }
        assertEquals(142f, TextFit.duetLabelMaxDp(true))
        assertEquals(200f, TextFit.duetLabelMaxDp(false))
    }

    @Test
    fun `CCBG-91 - the fitted Duet header never exceeds One UI's measured column on the Fold 7`() {
        // RUNBOOK Step 3, Fold 7 (One UI 8): the expanded Duet header column beside "−1%" and "in 3h 8m" measured
        // 134 dp at 360 dp (display size forced) and 183 dp on the 411 dp cover; the inner shade measured 265 dp.
        val one = aosp(1f)
        for ((sw, measured) in listOf(360 to 134f, 411 to 183f, 750 to 265f)) {
            val col = TextFit.expandedColumnDp(sw, true, "in 3h 8m", "−1%", TextFit.DUET_EXPANDED_FIGURE_SP, one)
            assertTrue("$sw dp: fitted ${TextFit.fitDp(col, TextFit.DUET_EXPANDED_COLUMN_DP)} > measured $measured",
                TextFit.fitDp(col, TextFit.DUET_EXPANDED_COLUMN_DP) <= measured)
        }
        // The line that was cut at 360 ("2h 4m ahead · Fri 12:30 …") now drops its clock.
        val at360 = TextFit.fitDp(TextFit.expandedColumnDp(360, true, "in 3h 8m", "−1%", TextFit.DUET_EXPANDED_FIGURE_SP, one), TextFit.DUET_EXPANDED_COLUMN_DP)
        assertTrue(at360 < 134f)
    }
}
