package com.robin.claudeusage.ui

import android.text.Spanned
import android.text.style.RelativeSizeSpan
import com.robin.claudeusage.notify.Duet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CCRM-103 (Hybrid Notification): the shape grammar's numbers, as the frozen page
 * (`design/2026-11-02-shape-grammar.html` rev D, `ring()` and `endCol()`) computes them.
 */
@RunWith(RobolectricTestRunner::class)
class ShapeGrammarTest {

    @Test
    fun `band is 0_13 of the ring, 0_10 at 48 and under, never under 3`() {
        assertEquals(9.36f, GrammarRing.bandDp(72f), 1e-4f)
        assertEquals(4.8f, GrammarRing.bandDp(48f), 1e-4f)
        assertEquals(4.4f, GrammarRing.bandDp(44f), 1e-4f)
        assertEquals(3f, GrammarRing.bandDp(28f), 1e-4f)
        assertEquals(0.39f * 72f - 4.68f, GrammarRing.boreDp(72f), 1e-4f)
    }

    @Test
    fun `figure sizes and the fit to the bore`() {
        assertEquals(18f, GrammarRing.figureDp(72f, "34%"), 1e-4f)
        assertEquals(15.12f, GrammarRing.figureDp(72f, "100%"), 1e-4f)
        assertEquals(12.76f, GrammarRing.figureDp(44f, "34%"), 1e-4f)
        assertEquals(10.56f, GrammarRing.figureDp(44f, "100%"), 1e-4f)
        // A figure that clears the bore keeps its size; a wide one shrinks to 1.5 dp inside it.
        assertEquals(18f, GrammarRing.fitFigure(72f, 18f, 20f), 1e-4f)
        val ri = GrammarRing.boreDp(44f)
        val fitted = GrammarRing.fitFigure(44f, 10.56f, 30f)
        assertTrue(fitted < 10.56f)
        val d = kotlin.math.hypot(30f * fitted / 10.56f / 2f, 0.36f * fitted)
        assertTrue(d <= ri - 1.5f + 0.6f)
        assertTrue(GrammarRing.loud(90.5))
        assertFalse(GrammarRing.loud(90.0))
        assertFalse(GrammarRing.loud(null))
    }

    @Test
    fun `end mark colours follow E1 and E2b`() {
        val fill = androidx.compose.ui.graphics.Color(0xFFE59980)
        assertNull(Grammar.endMarkColor(UsageEndMark.ROUND, 64.0, 52.0, fill, true))
        assertEquals(fill, Grammar.endMarkColor(UsageEndMark.LINE, 64.0, 52.0, fill, true))
        // E2b: "near" is the ±5 on-pace band of the band words.
        assertEquals(Grammar.ink(true), Grammar.endMarkColor(UsageEndMark.PACE, 50.0, 52.0, fill, true))
        assertEquals(Grammar.vsUnder(true), Grammar.endMarkColor(UsageEndMark.PACE, 34.0, 53.3, fill, true))
        assertEquals(Grammar.vsOver(false), Grammar.endMarkColor(UsageEndMark.PACE, 64.0, 52.0, fill, false))
    }

    @Test
    fun `the percent sign is drawn at 58 percent, nothing else is`() {
        val s = Grammar.smallPercent("34%") as Spanned
        val spans = s.getSpans(0, s.length, RelativeSizeSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(0.58f, spans[0].sizeChange, 1e-6f)
        assertEquals(2, s.getSpanStart(spans[0]))
        assertFalse(Grammar.smallPercent("—") is Spanned)
        assertFalse(Grammar.smallPercent("%") is Spanned)
    }

    @Test
    fun `a large screen's shade takes the bare Duet ring`() {
        assertEquals(Duet.HalfRing.FULL, Duet.halfRing(true, Duet.halfDp(411)))
        assertEquals(Duet.HalfRing.BARE, Duet.halfRing(true, Duet.halfDp(673)))
        assertEquals(Duet.HalfRing.NONE, Duet.halfRing(false, Duet.halfDp(673)))
    }
}
