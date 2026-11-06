package com.robin.claudeusage.ui

import com.robin.claudeusage.ui.PaceMapGeometry.LabelReq
import com.robin.claudeusage.ui.PaceMapGeometry.Rect
import com.robin.claudeusage.ui.PaceMapGeometry.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CCRM-104 (Pace Line Parity), D7 / review C1-1: the Pace map's label collision rule. */
class PaceMapPlacementTest {
    // the wireframe's plot: 339 x 230 at 1 px per dp
    private val f = PaceMapGeometry.frame(339f, 230f, 1f)
    private val w = 60f
    private val h = 12f

    private fun req(gone: Double, used: Double) = LabelReq(f.x(gone), f.y(used), w, h)
    private fun place(vararg r: LabelReq, fixed: List<Rect> = emptyList()) = PaceMapGeometry.placeLabels(f, 1f, r.toList(), fixed)

    private fun overlap(a: Rect, b: Rect) = a.x < b.right && b.x < a.right && a.y < b.bottom && b.y < a.bottom

    @Test fun `a single account's name sits right of its dot`() {
        val p = place(req(40.0, 50.0)).single()
        assertEquals(Side.RIGHT, p.side)
        assertNull(p.leader)
        assertEquals(f.x(40.0) + 10f, p.rect.x, 1e-3f)
    }

    @Test fun `near the right edge the name flips left`() {
        val p = place(req(95.0, 50.0)).single()
        assertEquals(Side.LEFT, p.side)
        assertEquals(f.x(95.0) - 10f, p.rect.right, 1e-3f)
    }

    @Test fun `two accounts at the same spot - the lower label goes below`() {
        val a = req(40.0, 60.0)
        val b = req(40.0, 60.0)
        val p = place(a, b)
        assertEquals(Side.RIGHT, p[0].side)
        assertEquals(Side.BELOW, p[1].side)
        assertFalse(overlap(p[0].rect, p[1].rect))
    }

    @Test fun `the higher used percent is placed first, so the lower label is the one that moves`() {
        val low = req(40.0, 40.0)
        val high = req(40.0, 41.0)
        val p = place(low, high) // inputs in the other order; results come back in input order
        assertEquals(Side.RIGHT, p[1].side)
        assertEquals(Side.BELOW, p[0].side)
    }

    @Test fun `five accounts with a cluster - the third takes a leader line at the right edge`() {
        // three stacked near week start: left and above-left leave the plot
        val c = req(2.0, 70.0)
        val p = place(req(60.0, 20.0), c, c, c, req(80.0, 90.0))
        assertEquals(Side.RIGHT, p[1].side)
        assertEquals(Side.BELOW, p[2].side)
        assertEquals(Side.LEADER, p[3].side)
        val lead = p[3].leader
        assertNotNull(lead)
        lead!!
        assertEquals(f.right + 10f - 6f, p[3].rect.right, 1e-3f)
        assertTrue(lead.toX <= p[3].rect.x)
        // nothing overlaps anything
        for (i in p.indices) for (j in i + 1 until p.size) assertFalse("$i vs $j", overlap(p[i].rect, p[j].rect))
    }

    @Test fun `a label never overlaps a fixed label or a dot`() {
        val fixed = Rect(f.x(40.0) + 8f, f.y(50.0) - 6f, 50f, 12f) // right of the dot is taken
        val p = place(req(40.0, 50.0), fixed = listOf(fixed)).single()
        assertTrue(p.side != Side.RIGHT)
        assertFalse(overlap(p.rect, fixed))
    }

    @Test fun `every label stays inside the plot`() {
        val reqs = listOf(0.0, 50.0, 100.0).flatMap { g -> listOf(0.0, 50.0, 100.0).map { u -> req(g, u) } }
        for (p in PaceMapGeometry.placeLabels(f, 1f, reqs, emptyList())) {
            assertTrue(p.rect.x >= f.left - 2f && p.rect.right <= f.right + 10f - 2f)
            assertTrue(p.rect.y >= f.top - 6f && p.rect.bottom <= f.bottom + 6f)
        }
    }

    @Test fun `even pace slides along the diagonal off a dot`() {
        val onDefault = LabelReq(f.x(62.0), f.y(62.0), 0f, 0f)
        val free = PaceMapGeometry.evenPace(f, 1f, 40f, 12f, listOf(Rect(f.x(10.0), f.y(90.0), 14f, 14f)), emptyList())
        assertEquals(62.0, free.atPct, 1e-9)
        val moved = PaceMapGeometry.evenPace(f, 1f, 40f, 12f, listOf(Rect(onDefault.cx - 7f, onDefault.cy - 7f, 14f, 14f)), emptyList())
        assertTrue(moved.atPct != 62.0)
        assertFalse(overlap(moved.rect, Rect(onDefault.cx - 7f, onDefault.cy - 7f, 14f, 14f)))
    }
}
