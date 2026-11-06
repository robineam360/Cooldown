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

    private val now = Instant.parse("2026-11-02T14:00:00Z") // a Monday
    private val reset = Instant.parse("2026-11-04T12:30:00Z") // Wed 12:30 PM
    private val us = Locale.US

    private fun forms(mt: Pace.MarkTime?, r: Instant = reset) =
        PaceText.paceLineForms(mt, r, now.toEpochMilli(), false, ZoneOffset.UTC, us)

    private val ahead = forms(Pace.MarkTime.Over(0, (23 * 60 + 59) * 60_000L)) // 23h 59m ahead
    private val behind = forms(Pace.MarkTime.Under((6 * 1440 + 23 * 60) * 60_000L)) // 6d 23h behind
    private val onMark = forms(Pace.MarkTime.On)

    private fun dp(line: PaceText.PaceLine, sp: Float = 12f, scale: Float = 1f) =
        TextFit.widthDp(TextFit.em(line), sp, scale).roundToInt()

    @Test
    fun `the forms are the wireframe's lines`() {
        assertEquals(
            listOf("23h 59m ahead · Wed 12:30 PM", "23h 59m ahead · 12:30 PM", "23h 59m ahead"),
            ahead.map { it.text },
        )
    }

    @Test
    fun `the estimator reproduces the wireframe's section 8 numbers at 12 sp`() {
        val all = listOf(ahead, behind, onMark)
        // What the wireframe's own §8 script prints for its five lines at 12 sp: widest full line
        // 169 dp ("23h 59m ahead · Wed 12:30 PM"), without the weekday 144, the floor 85. (The
        // build brief rounded these to 172 / 146 / 85; the script's output is what is pinned.)
        assertEquals(169, all.maxOf { dp(it[0]) })
        assertEquals(144, all.maxOf { dp(it[1]) })
        assertEquals(85, all.maxOf { dp(it.last()) })
        assertEquals(162, dp(behind[0])) // "6d 23h behind · Wed 12:30 PM"
        assertEquals(150, dp(onMark[0])) // "On the mark · Wed 12:30 PM", no bold span
    }

    @Test
    fun `the tinted span is 3 percent wider, a line on the mark has none`() {
        assertEquals(TextFit.em("23h 59m ahead") * 1.03, TextFit.em(ahead.last()), 1e-9)
        assertEquals(TextFit.em("On the mark"), TextFit.em(onMark.last()), 1e-9)
    }

    @Test
    fun `signs are 0_58 em and a small percent 0_46`() {
        assertEquals(0.58, TextFit.em("+"), 1e-9)
        assertEquals(0.58, TextFit.em("−"), 1e-9)
        assertEquals(0.46, TextFit.em("%"), 1e-9)
    }

    private fun texts(avail: Float, scale: Float, line: List<PaceText.PaceLine> = ahead): Pair<String, Float> {
        val p = TextFit.paceLine(line, avail, 12f, scale)!!
        return p.form.text to p.sp
    }

    @Test
    fun `form choice at font scale 1_0`() {
        assertEquals("23h 59m ahead" to 12f, texts(141f, 1f)) // expanded single column: weekday and clock drop
        assertEquals("23h 59m ahead · 12:30 PM" to 12f, texts(159f, 1f)) // Duet expanded header: the weekday drops
        assertEquals("23h 59m ahead · Wed 12:30 PM" to 12f, texts(184f, 1f)) // collapsed column
        assertEquals("23h 59m ahead · Wed 12:30 PM" to 12f, texts(328f, 1f))
    }

    @Test
    fun `larger text drops earlier, at 1_3`() {
        assertEquals("23h 59m ahead" to 12f, texts(141f, 1.3f))
        assertEquals("23h 59m ahead" to 12f, texts(159f, 1.3f))
        assertEquals("23h 59m ahead" to 12f, texts(184f, 1.3f)) // 224 and 190 do not fit 184
        assertEquals("23h 59m ahead · Wed 12:30 PM" to 12f, texts(328f, 1.3f))
    }

    @Test
    fun `at 2_0 the floor steps down to 10 sp before it would clip, and never changes wording`() {
        // Floor 170 dp at 12 sp x2: too wide for 141 and 159, fits 184 at 12 sp.
        assertEquals("23h 59m ahead" to 10f, texts(141f, 2f))
        assertEquals("23h 59m ahead" to 10f, texts(159f, 2f))
        assertEquals("23h 59m ahead" to 12f, texts(184f, 2f))
        assertEquals("23h 59m ahead · 12:30 PM" to 12f, texts(328f, 2f)) // 292 fits, the full 344 does not
    }

    @Test
    fun `a floor that fits at no size stays the floor at 10 sp for the view to ellipsize`() {
        val p = TextFit.paceLine(ahead, 60f, 12f, 2f)!!
        assertEquals("23h 59m ahead", p.form.text)
        assertEquals(10f, p.sp, 0f)
    }

    @Test
    fun `per string, not per size class - the shorter clock keeps itself`() {
        // "6d 23h behind · Wed 12:30 PM" is narrower than "23h 59m ahead · ...": both fit 184 at 1.0.
        assertEquals("6d 23h behind · Wed 12:30 PM" to 12f, texts(184f, 1f, behind))
        assertTrue(dp(behind[0]) <= dp(ahead[0]))
    }

    @Test
    fun `a reset later today has no weekday to drop, on the mark has no tinted span`() {
        val later = forms(Pace.MarkTime.Over(0, 3_600_000L), Instant.parse("2026-11-02T23:00:00Z"))
        assertEquals(listOf("1h 0m ahead · 11:00 PM", "1h 0m ahead"), later.map { it.text })
        assertEquals(null, onMark.last().tinted)
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
