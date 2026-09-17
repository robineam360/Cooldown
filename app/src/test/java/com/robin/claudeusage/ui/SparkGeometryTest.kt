package com.robin.claudeusage.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCRM-20 pulled the plot's coordinate system out of the `Canvas` lambda so a touch
 * could be mapped back to a sample. These tests exist because that mapping is now the
 * thing a tap depends on: if [SparkGeometry.nearestSample] picks the wrong point, the
 * callout confidently reports a percentage for the wrong moment, and nothing about the
 * chart looks wrong while it does it.
 */
class SparkGeometryTest {

    private val density = Density(2f)
    private val start = 1_000_000L
    private val end = start + 5 * 60 * 60_000L // a 5-hour window

    private fun geo(width: Float = 700f, height: Float = 400f) =
        SparkGeometry(width, height, density, start, end)

    private fun down(width: Float = 700f, height: Float = 400f) =
        SparkGeometry(width, height, density, start, end, ChartOrientation.DOWN)

    /** Samples at 0, 1, 2, 3, 4 hours into the window. */
    private val samples = (0..4).map { start + it * 60L * 60_000L to it * 10.0 }

    @Test
    fun `x maps the window onto the plot and clamps outside it`() {
        val g = geo()
        assertEquals(0f, g.x(start), 0.01f)
        assertEquals(g.plotRight, g.x(end), 0.01f)
        assertEquals(g.plotRight / 2f, g.x(start + (end - start) / 2), 0.01f)
        // A sample from a neighbouring window must not be drawn off the canvas.
        assertEquals(0f, g.x(start - 60_000L), 0.01f)
        assertEquals(g.plotRight, g.x(end + 60_000L), 0.01f)
    }

    @Test
    fun `y puts 0 percent on the axis and 100 percent at the top`() {
        val g = geo()
        assertEquals(g.plotBottom, g.y(0.0), 0.01f)
        assertEquals(g.plotTop, g.y(100.0), 0.01f)
        assertEquals((g.plotBottom + g.plotTop) / 2f, g.y(50.0), 0.01f)
        // Over-limit windows exist; they pin to the top rather than drawing above it.
        assertEquals(g.plotTop, g.y(140.0), 0.01f)
    }

    @Test
    fun `nearest snaps to the sample under the touch`() {
        val g = geo()
        for (s in samples) {
            assertEquals(s, g.nearestSample(g.x(s.first), samples))
        }
    }

    @Test
    fun `nearest rounds to the closer neighbour, never between them`() {
        val g = geo()
        val first = samples[0]
        val second = samples[1]
        val midpoint = (g.x(first.first) + g.x(second.first)) / 2f

        // Just inside the midpoint each way picks the point on that side — and the
        // returned percentage is always one that was really observed, which is the
        // whole reason we snap instead of interpolating.
        assertEquals(first, g.nearestSample(midpoint - 1f, samples))
        assertEquals(second, g.nearestSample(midpoint + 1f, samples))
        assertTrue(g.nearestSample(midpoint, samples) in samples)
    }

    @Test
    fun `a touch beyond either end still selects the end sample`() {
        val g = geo()
        assertEquals(samples.first(), g.nearestSample(-500f, samples))
        // Past the last sample there is only the projection tail, which isn't tappable —
        // so the newest real fetch is the honest answer.
        assertEquals(samples.last(), g.nearestSample(g.plotRight + 500f, samples))
    }

    @Test
    fun `nearest on an empty series is null rather than a crash`() {
        assertNull(geo().nearestSample(100f, emptyList()))
    }

    @Test
    fun `a view too small to plot into reports itself unusable`() {
        // No width at all, and shorter than the axis strip plus the top inset: both
        // were guarded inline before and are now one property.
        assertFalse(SparkGeometry(0f, 400f, density, start, end).usable)
        assertFalse(SparkGeometry(700f, 30f, density, start, end).usable)
        assertTrue(geo().usable)
    }

    // --- CCRM-74 (Chart Polish) item 1: the plot spans the full width ---

    @Test
    fun `the plot runs to the view's own edge, with no label gutter`() {
        // The whole point of the fix: the chart maps time against exactly the width
        // the usage bar above it maps elapsed against, so the now divider and the
        // bar's pace mark land on the same x. A gutter of any size breaks that.
        val g = geo(width = 700f)
        assertEquals(700f, g.plotRight, 0.01f)
        assertEquals(0f, g.plotLeft, 0.01f)
        // What a bar 70% elapsed would draw its mark at, and what the chart draws
        // "now" at, from the same width.
        assertEquals(0.70f * 700f, g.x(start + (end - start) * 7 / 10), 0.01f)
    }

    @Test
    fun `a 12dp top inset leaves the 100 percent label room above its own line`() {
        val g = geo()
        assertEquals(with(density) { 12.dp.toPx() }, g.plotTop, 0.01f)
        assertEquals(g.plotTop, g.y(100.0), 0.01f)
    }

    @Test
    fun `lines-only gives the axis strip back to the plot`() {
        // CCRM-75 (Chart Height) Small draws no time labels, so nothing is reserved
        // for them — the 72dp it is given is 72dp of chart.
        val plain = geo()
        val lines = SparkGeometry(
            700f, 400f, density, start, end, ChartOrientation.ACROSS, linesOnly = true,
        )
        assertEquals(400f, lines.plotBottom, 0.01f)
        assertTrue(lines.plotBottom > plain.plotBottom)
        assertEquals(plain.plotTop, lines.plotTop, 0.01f)
    }

    // --- CCRM-77 (Transposed Chart): the same mapping, turned a quarter turn ---

    @Test
    fun `transposed, usage runs across the full width`() {
        val g = down()
        assertEquals(0f, g.y(0.0), 0.01f)
        assertEquals(700f, g.y(100.0), 0.01f)
        assertEquals(350f, g.y(50.0), 0.01f)
        // Same clamp as upright: an over-limit window pins to the edge.
        assertEquals(700f, g.y(140.0), 0.01f)
    }

    @Test
    fun `transposed, time runs down from the window start to the reset`() {
        val g = down()
        // Nothing is reserved top or bottom — the time labels sit inside the plot.
        assertEquals(0f, g.plotTop, 0.01f)
        assertEquals(400f, g.plotBottom, 0.01f)
        assertEquals(g.plotTop, g.x(start), 0.01f)
        assertEquals(g.plotBottom, g.x(end), 0.01f)
        assertEquals(200f, g.x(start + (end - start) / 2), 0.01f)
    }

    @Test
    fun `transposed, a sample is the same reading at a quarter turn`() {
        val g = down()
        // Window start, nothing used: the top-left corner. Reset at 100%: bottom-right.
        assertEquals(Offset(0f, 0f), g.point(start, 0.0))
        assertEquals(Offset(700f, 400f), g.point(end, 100.0))
        // The even-pace diagonal therefore runs top-left to bottom-right, and the
        // above-pace corner is the top *right* one.
        assertEquals(Offset(700f, 0f), g.point(start, 100.0))
    }

    @Test
    fun `the guides are horizontal upright and vertical transposed`() {
        val up = geo()
        val (a, b) = up.valueLine(90.0)
        assertEquals(a.y, b.y, 0.01f)
        assertEquals(0f, a.x, 0.01f)
        assertEquals(700f, b.x, 0.01f)

        val (c, d) = down().valueLine(90.0)
        assertEquals(c.x, d.x, 0.01f)
        assertEquals(0.90f * 700f, c.x, 0.01f)
        assertEquals(0f, c.y, 0.01f)
        assertEquals(400f, d.y, 0.01f)
    }

    @Test
    fun `the now divider crosses the plot on the other axis each way`() {
        val (a, b) = geo().timeLine(start + (end - start) / 2)
        assertEquals(a.x, b.x, 0.01f)   // vertical
        val (c, d) = down().timeLine(start + (end - start) / 2)
        assertEquals(c.y, d.y, 0.01f)   // horizontal
        assertEquals(200f, c.y, 0.01f)
    }

    @Test
    fun `transposed, a touch is resolved against the vertical axis`() {
        val g = down()
        // The gesture handler hands nearestSample the coordinate x() returns — y here.
        for (s in samples) assertEquals(s, g.nearestSample(g.x(s.first), samples))
    }

    @Test
    fun `pace is the diagonal from zero at the start to a hundred at the reset`() {
        val g = geo()
        assertEquals(0.0, g.paceAt(start), 0.001)
        assertEquals(100.0, g.paceAt(end), 0.001)
        assertEquals(50.0, g.paceAt(start + (end - start) / 2), 0.001)
    }

    @Test
    fun `a zero-length window does not divide by zero`() {
        val g = SparkGeometry(700f, 400f, density, start, start)
        assertEquals(0f, g.x(start), 0.01f)
        assertEquals(0.0, g.paceAt(start), 0.001)
    }

    // --- CCBG-21 (Zero-Point Shading) ---

    @Test
    fun `a fresh window at zero percent draws no wash`() {
        // The reported defect: one sample at the start of the window, observed 0%,
        // pace 0% — the caption says "On even pace" and the wash must agree.
        assertFalse(abovePaceWash(0.0, 0.0))
    }

    @Test
    fun `the wash gate is the dead zone itself, strictly past it`() {
        // Asserted through PACE_DEAD_ZONE rather than a literal, the way
        // BarGeometryTest and RingGeometryTest do, so the three surfaces cannot drift.
        assertFalse(abovePaceWash(70.0 + PACE_DEAD_ZONE, 70.0))
        assertTrue(abovePaceWash(70.0 + PACE_DEAD_ZONE + 0.01, 70.0))
    }

    @Test
    fun `below the line never washes`() {
        assertFalse(abovePaceWash(40.0, 70.0))
        assertFalse(abovePaceWash(70.0 - PACE_DEAD_ZONE, 70.0))
    }

    // --- CCRM-74 (Chart Polish) item 4: "points" is gone, "%" is not ---

    @Test
    fun `the chart's short pace label signs the number and says percent`() {
        assertEquals("+12% vs pace", paceLabel(12.4))
        assertEquals("-12% vs pace", paceLabel(-12.4))
        assertEquals("on pace", paceLabel(0.0))
        // The dead zone is the same gate the wash uses, so the label agrees with it.
        assertEquals("on pace", paceLabel(PACE_DEAD_ZONE))
        assertEquals("on pace", paceLabel(-PACE_DEAD_ZONE))
    }

    @Test
    fun `the spoken pace phrase says percent, not points`() {
        assertEquals("12% above even pace", pacePhrase(12.4))
        assertEquals("12% below even pace", pacePhrase(-12.4))
        assertEquals("On even pace", pacePhrase(0.0))
        assertEquals("On even pace", pacePhrase(PACE_DEAD_ZONE))
    }
}
