package com.robin.claudeusage.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CCRM-75 (Chart Height): the Small / Medium / Large chip in Appearance, as arithmetic.
 *
 * Dp maths is plain JVM maths — [BarGeometryTest] already leans on that — so the one
 * thing worth pinning is that Large still behaves exactly as `chartHeight` did before the
 * setting existed, and that Small and Medium ignore every input but themselves. A Medium
 * chart that quietly grew on a Fold's inner screen would be the setting failing silently.
 */
class ChartHeightTest {

    private fun px(width: Int, window: Int, size: ChartSize) =
        chartHeight(width.dp, window.dp, size).value

    @Test
    fun `Large is still width times 0-35, clamped to 180-300dp`() {
        // A phone: 328dp of card × 0.35 = 115dp, so the 180dp floor binds.
        assertEquals(180f, px(328, 2000, ChartSize.LARGE), 0.01f)
        // A Fold's inner screen: 678 × 0.35 = 237.3dp, inside the clamp — the figure
        // §11 of the wireframe publishes for the inner-screen card.
        assertEquals(237.3f, px(678, 2000, ChartSize.LARGE), 0.01f)
        // Absurdly wide windows stop at the ceiling rather than filling the screen.
        assertEquals(300f, px(1400, 2000, ChartSize.LARGE), 0.01f)
    }

    @Test
    fun `Large never outgrows a short window`() {
        // A phone in landscape: 400dp tall, so 45% of it wins over the width figure.
        assertEquals(180f, px(678, 400, ChartSize.LARGE), 0.01f)
        // Nothing measured yet: trust the width rather than collapse to nothing.
        assertEquals(237.3f, px(678, 0, ChartSize.LARGE), 0.01f)
    }

    @Test
    fun `Medium and Small are flat, whatever room they are given`() {
        for (window in listOf(0, 400, 2000)) {
            for (width in listOf(328, 678, 1400)) {
                assertEquals(120f, px(width, window, ChartSize.MEDIUM), 0.01f)
                assertEquals(72f, px(width, window, ChartSize.SMALL), 0.01f)
            }
        }
    }

    @Test
    fun `the default is Medium, and the two-argument call gets it`() {
        assertEquals(ChartSize.MEDIUM, ChartSize.DEFAULT)
        // The call sites that predate the setting keep compiling and land on the
        // default — Robin's 2026-09-17 call, made knowing it shortens today's chart.
        assertEquals(chartHeight(678.dp, 2000.dp).value, px(678, 2000, ChartSize.MEDIUM), 0.01f)
    }

    @Test
    fun `only Small drops the labels`() {
        // The height and the detail level travel together, so a chart that is 72dp tall
        // is never also asked to fit an axis strip.
        assertEquals(true, ChartSize.SMALL.linesOnly)
        assertEquals(false, ChartSize.MEDIUM.linesOnly)
        assertEquals(false, ChartSize.LARGE.linesOnly)
    }
}
