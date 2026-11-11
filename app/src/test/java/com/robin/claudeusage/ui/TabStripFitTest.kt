package com.robin.claudeusage.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCBG-81 (History Tab Wrap), option B (Robin, 2026-11-11, approved as captured in
 * design/2026-11-11-ccbg81-history-tab-strip.html): Main's and History's strips are fixed while every label fits its
 * equal share on one line, and scroll as soon as one would not. Widths in dp (the call site passes px).
 */
class TabStripFitTest {

    private val pad = TabStripFit.TAB_PADDING_DP

    @Test
    fun `three accounts on a 360 dp phone - Personal cannot fit a third, so History scrolls`() {
        // The strip is 360 − 2 × 20 dp of margin = 320; a third is 106. "Personal" with its 20 dp mark and 6 dp gap
        // is about 26 + 58 = 84, plus the tab's 32 dp padding = 116 > 106 — today's "Person / al".
        assertTrue(TabStripFit.scrolls(listOf(70, 84, 46), 320, pad))
    }

    @Test
    fun `the same three on the cover fit their thirds - the fixed row stays`() {
        assertFalse(TabStripFit.scrolls(listOf(70, 84, 46), 380, pad))
    }

    @Test
    fun `a share that exactly holds the label and its padding stays fixed`() {
        assertFalse(TabStripFit.scrolls(listOf(68, 68), 200, pad))
        assertTrue(TabStripFit.scrolls(listOf(69, 68), 200, pad))
    }

    @Test
    fun `two accounts with a long name scroll instead of wrapping`() {
        assertTrue(TabStripFit.scrolls(listOf(40, 70, 150), 320, pad))
    }

    @Test
    fun `no labels, nothing to scroll`() {
        assertFalse(TabStripFit.scrolls(emptyList(), 320, pad))
    }
}
