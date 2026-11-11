package com.robin.claudeusage.notify

import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.ui.PaceText
import org.junit.Assert.assertEquals
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
        assertEquals( // Duet expanded today (36 sp figure), 159
            listOf(
                listOf("6d 23h behind · Wed@12", "6d 23h behind · Wed@12", "6d 23h behind@12"),
                listOf("23h 59m ahead · Wed@12", "23h 59m ahead · Wed@12", "23h 59m ahead@10"),
                listOf("5h 45m behind · 8:15 AM@12", "5h 45m behind@12", "5h 45m behind@10"),
                listOf("57m ahead · resets 2:57 PM@12", "57m ahead · 2:57 PM@12", "57m ahead@12"),
                listOf("On the mark · Sat 8:00 AM@12", "On the mark · Sat@12", "On the mark@12"),
            ),
            row(TextFit.DUET_EXPANDED_COLUMN_DP),
        )
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
}
