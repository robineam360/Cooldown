package com.robin.claudeusage.ui

import com.robin.claudeusage.ui.MapLabels.Spot
import com.robin.claudeusage.ui.PaceMapGeometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CCRM-104 (Pace Line Parity), D7 / review C1-1, and CCRM-111 (Map Layers): the Pace map's label collision rule on
 * the app's own plot (339 × 230 dp at 1 px per dp). The fixture cases are in [PaceMapV20Test]; these pin the
 * behaviours the wireframe words in the app's frame: a name sits right of its dot, then flips, a crowd takes short
 * dotted leaders that never cross another name, and with nothing free a name is not drawn.
 */
class PaceMapPlacementTest {
    private val f = PaceMapGeometry.frame(339f, 230f, 1f)
    private val g = MapLabels.Geometry.DP.scaled(1.0)
    private val area = MapLabels.Rect(
        f.left - g.margin.left, f.top - g.margin.top,
        (f.right - f.left) + g.margin.left + g.margin.right, (f.bottom - f.top) + g.margin.top + g.margin.bottom,
    )
    private val w = 60.0
    private val h = 12.0

    private fun r(x: Float, y: Float, w: Float, h: Float) = MapLabels.Rect(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble())

    private fun dot(key: String, gone: Double, used: Double) = MapLabels.Dot(key, f.x(gone).toDouble(), f.y(used).toDouble(), w, h)

    private fun place(vararg dots: MapLabels.Dot, fixed: List<MapLabels.Rect> = emptyList()) =
        MapLabels.place(dots.toList(), area, g, fixed, emptyList())

    private fun overlap(a: MapLabels.Rect, b: MapLabels.Rect) = a.x < b.right && b.x < a.right && a.y < b.bottom && b.y < a.bottom

    @Test fun `a single account's name sits right of its dot`() {
        val p = place(dot("a", 40.0, 50.0)).single()
        assertEquals(Spot.RIGHT, p.spot)
        assertNull(p.leader)
        assertEquals(f.x(40.0) + 10.0, p.box!!.x, 1e-3)
    }

    @Test fun `near the right edge the name flips left`() {
        val p = place(dot("a", 95.0, 50.0)).single()
        assertEquals(Spot.LEFT, p.spot)
        assertEquals(f.x(95.0) - 10.0, p.box!!.right, 1e-3)
    }

    @Test fun `two accounts at the same spot - the lower label goes below`() {
        val p = place(dot("a", 40.0, 60.0), dot("b", 40.0, 60.0))
        assertEquals(Spot.RIGHT, p[0].spot)
        assertEquals(Spot.BELOW, p[1].spot)
        assertFalse(overlap(p[0].box!!, p[1].box!!))
    }

    @Test fun `the higher used percent is placed first, so the lower label is the one that moves`() {
        val p = place(dot("low", 40.0, 40.0), dot("high", 40.0, 41.0)) // results come back in input order
        assertEquals(Spot.RIGHT, p[1].spot)
        assertEquals(Spot.BELOW, p[0].spot)
    }

    @Test fun `a stack of dots at one point - two names fit, the rest are not drawn, and no leader crosses a name or a dot`() {
        // CCBG-79 (Leader Reads as Trail) symptom 2: five dots at one point, as the synthetic series draws them. A leader
        // starts inside every other dot's hit box, so the rule refuses it: the extra names are not drawn (Astra C1-2).
        val crowd = (1..5).map { dot("c$it", 2.0, 70.0) } + dot("other", 60.0, 20.0)
        val got = MapLabels.place(crowd, area, g, emptyList(), emptyList())
        val longest = g.leaderDistances.max()
        val boxes = got.mapNotNull { it.box }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) assertFalse("$i vs $j", overlap(boxes[i], boxes[j]))
        for (p in got) {
            val l = p.leader ?: continue
            assertTrue(l.distance <= longest)
            // the leader's own path crosses no other placed name
            for (q in got) if (q.key != p.key) {
                val b = q.box ?: continue
                val d = crowd.first { it.key == p.key }
                assertFalse("${p.key} crosses ${q.key}", MapLabels.segmentHits(d.x to d.y, l.endX to l.endY, b))
            }
        }
        assertEquals(Spot.RIGHT, got[0].spot)
        assertEquals(Spot.BELOW, got[1].spot)
        assertTrue(got.drop(2).take(3).all { it.spot == Spot.NONE && it.box == null })
        assertEquals(Spot.RIGHT, got[5].spot)
    }

    @Test fun `the origin dot gets no leader to the far edge`() {
        // Gemini's week has not started: its dot sits at the origin (CCBG-79 symptom 1).
        val p = place(dot("Gemini", 0.0, 0.0)).single()
        val box = p.box
        if (p.spot == Spot.LEADER) assertTrue(p.leader!!.distance <= 48.0)
        if (box != null) assertTrue(box.right < f.right - 40.0)
    }

    @Test fun `nothing free - the name is not drawn and takes no room`() {
        val p = place(dot("a", 40.0, 50.0), fixed = listOf(area)).single()
        assertEquals(Spot.NONE, p.spot)
        assertNull(p.box)
        assertNull(p.leader)
    }

    @Test fun `a label never overlaps a fixed word or a dot`() {
        val fixed = r(f.x(40.0) + 8f, f.y(50.0) - 6f, 50f, 12f) // right of the dot is taken
        val p = place(dot("a", 40.0, 50.0), fixed = listOf(fixed)).single()
        assertTrue(p.spot != Spot.RIGHT)
        assertFalse(overlap(p.box!!, fixed))
    }

    @Test fun `with Projection on a name does not sit on its own tail or on a ring`() {
        val a = f.x(40.0).toDouble() to f.y(50.0).toDouble()
        val tailEnd = f.x(100.0).toDouble() to f.y(70.0).toDouble()
        val own = MapLabels.Dot("a", a.first, a.second, w, h, tailEnd)
        val p = MapLabels.place(listOf(own), area, g, emptyList(), emptyList()).single()
        assertTrue("the tail runs right, so the name cannot", p.spot != Spot.RIGHT)
        assertTrue(p.refusedFor.contains("sits on its own tail"))
        val ring = MapLabels.Rect(tailEnd.first - 3.5, tailEnd.second - 3.5, 7.0, 7.0)
        val q = MapLabels.place(listOf(dot("b", 96.0, 70.0)), area, g, emptyList(), listOf(ring)).single()
        val qb = q.box
        if (qb != null) assertFalse(overlap(qb, ring))
    }

    @Test fun `every label stays inside the plot`() {
        val reqs = listOf(0.0, 50.0, 100.0).flatMap { gone -> listOf(0.0, 50.0, 100.0).map { u -> dot("$gone/$u", gone, u) } }
        for (p in MapLabels.place(reqs, area, g, emptyList(), emptyList())) {
            val b = p.box ?: continue
            assertTrue(b.x >= area.x && b.right <= area.right && b.y >= area.y && b.bottom <= area.bottom)
        }
    }

    @Test fun `even pace slides along the diagonal off a dot`() {
        val onDefault = PaceMapGeometry.dotBox(f.x(62.0), f.y(62.0), 1f)
        val free = PaceMapGeometry.evenPace(f, 1f, 40f, 12f, listOf(Rect(f.x(10.0), f.y(90.0), 14f, 14f)), emptyList())
        assertEquals(62.0, free.atPct, 1e-9)
        val moved = PaceMapGeometry.evenPace(f, 1f, 40f, 12f, listOf(onDefault), emptyList())
        assertTrue(moved.atPct != 62.0)
        assertFalse(moved.rect.x < onDefault.right && onDefault.x < moved.rect.right && moved.rect.y < onDefault.bottom && onDefault.y < moved.rect.bottom)
    }
}
