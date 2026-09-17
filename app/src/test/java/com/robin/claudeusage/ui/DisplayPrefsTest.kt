package com.robin.claudeusage.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCRM-72 (Main Screen Redesign) / CCRM-75 (Chart Height) / CCRM-77 (Transposed Chart):
 * the pure display-prefs enums Settings → Appearance and [com.robin.claudeusage.data.UsageCache]
 * read and write.
 */
class DisplayPrefsTest {

    // --- Density ---

    @Test
    fun `Density fromKey resolves valid keys`() {
        assertEquals(Density.COMFORTABLE, Density.fromKey("comfortable"))
        assertEquals(Density.COMPACT, Density.fromKey("compact"))
    }

    @Test
    fun `Density fromKey falls back to DEFAULT for an unknown or null key`() {
        assertEquals(Density.DEFAULT, Density.fromKey("bogus"))
        assertEquals(Density.DEFAULT, Density.fromKey(null))
    }

    @Test
    fun `Density default is COMFORTABLE`() {
        assertEquals(Density.COMFORTABLE, Density.DEFAULT)
    }

    // --- ChartSize ---

    @Test
    fun `ChartSize fromKey resolves valid keys`() {
        assertEquals(ChartSize.SMALL, ChartSize.fromKey("small"))
        assertEquals(ChartSize.MEDIUM, ChartSize.fromKey("medium"))
        assertEquals(ChartSize.LARGE, ChartSize.fromKey("large"))
    }

    @Test
    fun `ChartSize fromKey falls back to DEFAULT for an unknown or null key`() {
        assertEquals(ChartSize.DEFAULT, ChartSize.fromKey("bogus"))
        assertEquals(ChartSize.DEFAULT, ChartSize.fromKey(null))
    }

    @Test
    fun `ChartSize default is MEDIUM`() {
        assertEquals(ChartSize.MEDIUM, ChartSize.DEFAULT)
    }

    @Test
    fun `ChartSize heights are 72, 120 and null`() {
        assertEquals(72, ChartSize.SMALL.heightDp)
        assertEquals(120, ChartSize.MEDIUM.heightDp)
        assertNull(ChartSize.LARGE.heightDp)
    }

    @Test
    fun `ChartSize linesOnly is true only for SMALL`() {
        assertTrue(ChartSize.SMALL.linesOnly)
        assertFalse(ChartSize.MEDIUM.linesOnly)
        assertFalse(ChartSize.LARGE.linesOnly)
    }

    // --- ChartOrientation ---

    @Test
    fun `ChartOrientation fromKey resolves valid keys`() {
        assertEquals(ChartOrientation.ACROSS, ChartOrientation.fromKey("across"))
        assertEquals(ChartOrientation.DOWN, ChartOrientation.fromKey("down"))
    }

    @Test
    fun `ChartOrientation fromKey falls back to DEFAULT for an unknown or null key`() {
        assertEquals(ChartOrientation.DEFAULT, ChartOrientation.fromKey("bogus"))
        assertEquals(ChartOrientation.DEFAULT, ChartOrientation.fromKey(null))
    }

    @Test
    fun `ChartOrientation default is ACROSS`() {
        assertEquals(ChartOrientation.ACROSS, ChartOrientation.DEFAULT)
    }
}
