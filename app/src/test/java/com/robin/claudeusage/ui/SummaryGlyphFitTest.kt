package com.robin.claudeusage.ui

import com.robin.claudeusage.data.AccountTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCRM-115 (Row Tag Glyph): the glyph column's fit rule, pure (addendum 2026-11-11, §Row glyph, table "Where the glyph
 * drops out"): the name ellipsizes first down to its floor (64 dp at its own text scale, or its own width if
 * shorter), then the column leaves every row at once; a one-line bar keeps 40 dp.
 */
class SummaryGlyphFitTest {

    private fun full(name: Float, pill: Float?, figure: Float) = SummaryRows.FullMeasure(name, pill, figure)

    @Test fun `a full row keeps the glyph exactly while the text column holds 121 + pill + figure`() {
        // The addendum's arithmetic for a name at its floor: 18 glyph + 18 mark + 7 + 64 + 7 + pill + 7 + figure = 121 + pill + figure.
        val row = full(name = 80f, pill = 34f, figure = 36f)
        val need = 121f + 34f + 7f * 0 + 36f
        assertTrue(SummaryRows.fullKeepsGlyph(need + 7f, listOf(row), 1f)) // 7 dp pill gap sits inside the 121
        assertTrue(SummaryRows.fullKeepsGlyph(need, listOf(row), 1f))
        assertFalse(SummaryRows.fullKeepsGlyph(need - 1f, listOf(row), 1f))
    }

    @Test fun `no plan pill - one gap fewer`() {
        val row = full(name = 80f, pill = null, figure = 36f)
        // 18 + 18 + 7 + 64 + 7 + 36 = 150
        assertTrue(SummaryRows.fullKeepsGlyph(150f, listOf(row), 1f))
        assertFalse(SummaryRows.fullKeepsGlyph(149.9f, listOf(row), 1f))
    }

    @Test fun `a name shorter than its floor keeps only its own width`() {
        val short = full(name = 30f, pill = 30f, figure = 30f)
        val long = full(name = 90f, pill = 30f, figure = 30f)
        // short: 18 + 18 + 7 + 30 + 7 + 30 + 7 + 30 = 147 ; long: 181
        assertTrue(SummaryRows.fullKeepsGlyph(147f, listOf(short), 1f))
        assertFalse(SummaryRows.fullKeepsGlyph(147f, listOf(long), 1f))
    }

    @Test fun `the column is one decision for the list - the tightest row takes it from every row`() {
        val roomy = full(40f, 30f, 36f)
        val tight = full(120f, 70f, 70f) // "Enterprise", "72% left"
        assertTrue(SummaryRows.fullKeepsGlyph(279f, listOf(roomy, tight), 1f)) // the cover: 339 - 60
        assertFalse(SummaryRows.fullKeepsGlyph(228f, listOf(roomy, tight), 1f)) // a 360 dp phone: 288 - 60
        assertTrue(SummaryRows.fullKeepsGlyph(228f, listOf(roomy), 1f)) // without the tight row, it stays
    }

    @Test fun `the name floor grows with the text scale - large text drops the column sooner`() {
        val row = full(name = 120f, pill = 34f, figure = 36f)
        val at1 = SummaryRows.fullKeepsGlyph(240f, listOf(row), 1f)
        val at2 = SummaryRows.fullKeepsGlyph(240f, listOf(row), 2f)
        assertTrue(at1)
        assertFalse(at2)
    }

    @Test fun `one-line rows - the bar pays 16 dp and keeps 40, so a list under 295 dp drops the glyph`() {
        assertEquals(239f, SummaryRows.ONE_LINE_FIXED_DP, 0f)
        assertTrue(SummaryRows.oneLineKeepsGlyph(295f))
        assertFalse(SummaryRows.oneLineKeepsGlyph(294.9f))
        // The addendum's rows: 360 dp phone list 288 (drops), cover 339, inner 317 (both keep).
        assertFalse(SummaryRows.oneLineKeepsGlyph(288f))
        assertTrue(SummaryRows.oneLineKeepsGlyph(339f))
        assertTrue(SummaryRows.oneLineKeepsGlyph(317f))
    }

    @Test fun `the row's name reads its tag whether or not the glyph is drawn`() {
        assertEquals("Teams, Work account", SummaryRows.nameDescription("Teams", AccountTag.WORK))
        assertEquals("Lab, Personal account", SummaryRows.nameDescription("Lab", AccountTag.PERSONAL))
    }

    @Test fun `the Settings chip drops its word, not its glyph, when the name would fall under its floor`() {
        // available 200: name 80 (floor 64) + others 60 + chip fixed 26 + word 50 = 200 → keeps; one dp less → drops.
        assertTrue(TagChipFit.showsWord(200f, 80f, 1f, 60f, 50f))
        assertFalse(TagChipFit.showsWord(199f, 80f, 1f, 60f, 50f))
        // a short name keeps its own width as the floor
        assertTrue(TagChipFit.showsWord(176f, 40f, 1f, 60f, 50f))
        assertFalse(TagChipFit.showsWord(175f, 40f, 1f, 60f, 50f))
    }

    @Test fun `the Add sheet's glyphs leave both segments together when Personal and its glyph no longer fit half the row`() {
        assertTrue(AddSheetFit.keepsGlyphs(320f, 52f)) // font 1.0 on a 360 dp phone: 160 of 160 needed 102
        assertTrue(AddSheetFit.keepsGlyphs(320f, 110f)) // 110 + 50 = 160 of 160: still fits
        assertFalse(AddSheetFit.keepsGlyphs(320f, 111f)) // one dp more: both glyphs leave
    }
}
