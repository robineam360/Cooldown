package com.robin.claudeusage.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [compactLine] — the one line a Compact card shows in place of its folded chart
 * (CCRM-72 (Main Screen Redesign)), per `design/2026-09-17-main-screen-redesign.html`
 * §3, §4 and §7.
 */
class CompactLineCopyTest {

    @Test
    fun `below pace reads the wireframe line`() {
        val line = compactLine("in 2h 41m", -32.0)
        assertEquals("Resets in 2h 41m · 32% below pace", line.text)
        assertNull("nothing is emphasised below pace", line.boldClause)
    }

    @Test
    fun `above pace emphasises the pace clause alone`() {
        val line = compactLine("in 3d 2h", 12.0)
        assertEquals("Resets in 3d 2h · 12% above even pace", line.text)
        assertEquals("12% above even pace", line.boldClause)
    }

    @Test
    fun `inside the dead zone reads on even pace`() {
        val line = compactLine("in 10h 1m", 2.0)
        assertEquals("Resets in 10h 1m · on even pace", line.text)
        assertNull(line.boldClause)
    }

    @Test
    fun `the dead zone boundary is not above pace`() {
        assertEquals("on even pace", compactPaceClause(PACE_DEAD_ZONE))
        assertEquals("on even pace", compactPaceClause(-PACE_DEAD_ZONE))
    }

    @Test
    fun `an unstarted window keeps the idle copy`() {
        // CCBG-30: the same words ResetRow uses when there is no reset time yet.
        val line = compactLine(null, null)
        assertEquals("Starts when a message is sent", line.text)
        assertNull(line.boldClause)
    }

    @Test
    fun `no pace to read leaves the reset clause alone`() {
        val line = compactLine("in 45m", null)
        assertEquals("Resets in 45m", line.text)
        assertNull(line.boldClause)
    }

    @Test
    fun `the pace clause rounds rather than truncates`() {
        assertEquals("18% below pace", compactPaceClause(-17.6))
        assertEquals("13% above even pace", compactPaceClause(12.5))
    }
}
