package com.robin.claudeusage.widgets

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.widgets.WidgetFixtures.NOW_MS
import com.robin.claudeusage.widgets.WidgetFixtures.forState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CCRM-78 (Widgets Reborn): every state S1–S14 on every bucket of every face, classified
 * by the pure table and then rendered and inflated the way a launcher would — plus R10's
 * bitmap budget at the Fold 7's density. The copy each state must show is the approved
 * wireframe's (design/2026-09-25-widgets-reborn.html, rev D).
 */
@RunWith(RobolectricTestRunner::class)
// API 31 is minSdk: every RemoteViews call must be remotable there, not only on 36.
@Config(qualifiers = "420dpi", sdk = [31, 36])
class WidgetFaceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun inflate(bucket: Bucket, state: FaceState): View =
        WidgetFace.render(context, bucket.face, bucket, state).apply(context, FrameLayout(context))

    private fun shown(v: View): Boolean {
        var p: View? = v
        while (p != null) {
            if (p.visibility != View.VISIBLE) return false
            p = p.parent as? View
        }
        return true
    }

    /** Every visible text run, in layout order. Chronometers count as their live form. */
    private fun texts(root: View): List<String> = buildList {
        fun walk(v: View) {
            if (!shown(v)) return
            if (v is Chronometer) add("<live>")
            else if (v is TextView) v.text?.toString()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
    }

    private fun joined(bucket: Bucket, s: StateId, dark: Boolean = true, bg: FaceBackground = FaceBackground.SOLID): String {
        val state = FaceStates.of(forState(bucket.face, s, dark, bg))
        return texts(inflate(bucket, state)).joinToString(" | ")
    }

    // ---- the table: every state × bucket ----------------------------------------------

    @Test
    fun everyStateOnEveryBucket_classifiesAndRenders() {
        for (bucket in Bucket.entries) for (s in StateId.entries) {
            for (bg in FaceBackground.entries) for (dark in listOf(true, false)) {
                val state = FaceStates.of(forState(bucket.face, s, dark, bg))
                // A removed account simply leaves the Strip; it never shows S9 there.
                if (bucket.face == Face.STRIP && s == StateId.S9) {
                    assertFalse(StateId.S9 in state.states)
                    assertEquals(3, state.cells.size)
                } else {
                    assertTrue("$bucket $s: got ${state.states}", s in state.states)
                }
                val all = texts(inflate(bucket, state)).joinToString(" | ")
                // R4: no static relative time on any face, ever.
                assertFalse("$bucket $s relative time: $all", RELATIVE.containsMatchIn(all))
                // R2: 12-hour clock times only.
                assertFalse("$bucket $s 24h time: $all", H24.containsMatchIn(all))
                // R1: never the long window names.
                assertFalse("$bucket $s long wording: $all", all.contains("5-hour") || all.contains("7-day"))
            }
        }
    }

    @Test
    fun theSyntheticMarker_isTheRibbonOnTallFacesAndTheDotOnShortOnes() {
        for (bucket in Bucket.entries) {
            val v = inflate(bucket, FaceStates.of(forState(bucket.face, StateId.S13)))
            val ribbon = v.findViewById<View>(com.robin.claudeusage.R.id.w_ribbon)
            val dot = v.findViewById<View>(com.robin.claudeusage.R.id.w_synth_dot)
            assertEquals("$bucket ribbon", bucket.tall, shown(ribbon))
            assertEquals("$bucket dot", !bucket.tall, shown(dot))
        }
        val plain = inflate(Bucket.RING_2X2, FaceStates.of(forState(Face.RING, StateId.S1)))
        assertFalse(shown(plain.findViewById(com.robin.claudeusage.R.id.w_ribbon)))
    }

    @Test
    fun backgrounds_solidGradientTransparent() {
        for (bg in FaceBackground.entries) {
            val v = inflate(Bucket.NUMBER_4X1, FaceStates.of(forState(Face.NUMBER, StateId.S1, bg = bg)))
            assertEquals(bg != FaceBackground.TRANSPARENT, shown(v.findViewById(com.robin.claudeusage.R.id.w_bg)))
            assertEquals(bg == FaceBackground.GRADIENT, shown(v.findViewById(com.robin.claudeusage.R.id.w_wash)))
            // Transparent text carries the shadow in the opposite tone.
            val label = v.findViewById<TextView>(com.robin.claudeusage.R.id.num_label)
            assertEquals(bg == FaceBackground.TRANSPARENT, label.shadowRadius > 0f)
        }
    }

    // ---- the copy each face shows, state by state ----------------------------------

    @Test
    fun ring() {
        assertEquals("38%", joined(Bucket.RING_1X1, StateId.S1))
        assertEquals("38% | Pro | · as of 6:26 PM", joined(Bucket.RING_2X2, StateId.S1))
        // At 100% the × replaces the figure, on both sizes (Q10).
        assertEquals("", joined(Bucket.RING_1X1, StateId.S3))
        assertEquals("Pro | · as of 6:26 PM", joined(Bucket.RING_2X2, StateId.S3))
        assertTrue(joined(Bucket.RING_2X2, StateId.S4).startsWith("31% | ChatGPT · Weekly"))
        assertTrue(joined(Bucket.RING_2X2, StateId.S5).startsWith("— | Pro · not started"))
        assertTrue(joined(Bucket.RING_2X2, StateId.S6).startsWith("— | Pro |"))
        assertTrue(joined(Bucket.RING_2X2, StateId.S10).startsWith("38% | Pro (unassigned)"))
        assertTrue(joined(Bucket.RING_2X2, StateId.S11).startsWith("62% | Pro · left"))
        assertEquals("Account removed\ntap to choose", joined(Bucket.RING_1X1, StateId.S9))
        assertEquals("No usage\non this plan", joined(Bucket.RING_2X2, StateId.S12))
        assertEquals("Update\nCooldown", joined(Bucket.RING_1X1, StateId.S14))
        assertEquals("Unavailable in this version\nupdate Cooldown", joined(Bucket.RING_2X2, StateId.S14))
        // S7's dot: the 1×1's top-right inset; 2×2's after the label.
        val v = inflate(Bucket.RING_1X1, FaceStates.of(forState(Face.RING, StateId.S7)))
        assertTrue(shown(v.findViewById(com.robin.claudeusage.R.id.ring_corner_dot)))
        val v2 = inflate(Bucket.RING_2X2, FaceStates.of(forState(Face.RING, StateId.S7)))
        assertTrue(shown(v2.findViewById(com.robin.claudeusage.R.id.ring_dot)))
        // R6: the figure at half alpha, the ring at 0.45 — through the colour and
        // setImageAlpha, since View.setAlpha is not remotable on every supported API.
        assertEquals(127, v2.findViewById<TextView>(com.robin.claudeusage.R.id.ring_fig).currentTextColor ushr 24)
        assertEquals(114, v2.findViewById<ImageView>(com.robin.claudeusage.R.id.ring).imageAlpha)
    }

    @Test
    fun number() {
        assertEquals("Pro · 5h | 38%", joined(Bucket.NUMBER_2X1, StateId.S1))
        assertEquals("Pro · 5h | 38% | Resets 9:10 PM | as of 6:26 PM", joined(Bucket.NUMBER_4X1, StateId.S1))
        assertEquals(
            "Pro · 5h | 38% | Resets 9:10 PM | as of 6:26 PM | 5h | Weekly | Pro ⇄",
            joined(Bucket.NUMBER_4X2, StateId.S1),
        )
        // 2×1: a weekly-only account's name, with "Weekly" as its own second line.
        assertEquals("ChatGPT | Weekly | 31%", joined(Bucket.NUMBER_2X1, StateId.S4))
        assertEquals(
            "ChatGPT · Weekly | 31% | Resets Sat 9:10 PM | as of 6:26 PM | Weekly | ChatGPT ⇄",
            joined(Bucket.NUMBER_4X2, StateId.S4),
        )
        assertTrue(joined(Bucket.NUMBER_4X1, StateId.S5).contains("Starts when a message is sent"))
        assertTrue(joined(Bucket.NUMBER_4X1, StateId.S6).contains("— | Reset 6:19 PM"))
        assertTrue(joined(Bucket.NUMBER_4X1, StateId.S7).contains("Resets 9:10 PM")) // S7 keeps it
        assertTrue(joined(Bucket.NUMBER_4X1, StateId.S8).contains("| Stale |"))
        assertEquals("Account removed · tap to choose", joined(Bucket.NUMBER_4X1, StateId.S9))
        assertEquals("UNASSIGNED | Pro | 38%", joined(Bucket.NUMBER_2X1, StateId.S10))
        assertTrue(joined(Bucket.NUMBER_4X1, StateId.S10).startsWith("Pro (unassigned) · 5h | 38%"))
        // Left: the bare figure flips at 2×1; the LEFT caption from 4×1 up.
        assertEquals("Pro · 5h | 62%", joined(Bucket.NUMBER_2X1, StateId.S11))
        assertTrue(joined(Bucket.NUMBER_4X1, StateId.S11).startsWith("Pro · 5h | 62% | LEFT"))
        assertEquals("No usage on this plan", joined(Bucket.NUMBER_4X2, StateId.S12))
        assertEquals("Update Cooldown", joined(Bucket.NUMBER_2X1, StateId.S14))
        assertEquals("Unavailable in this version · update Cooldown", joined(Bucket.NUMBER_4X1, StateId.S14))
    }

    @Test
    fun number4x2_chipsFollowTheWindowAndTheCyclerNamesTheAccount() {
        val weekly = FaceStates.of(forState(Face.NUMBER, StateId.S1).copy(window = FaceWindow.WEEKLY))
        val all = texts(inflate(Bucket.NUMBER_4X2, weekly)).joinToString(" | ")
        assertTrue(all, all.startsWith("Pro · Weekly | 20% | Resets Sat 9:10 PM"))
        assertEquals(FaceWindow.WEEKLY, weekly.cells.single().window)
    }

    @Test
    fun countdown() {
        assertEquals("5h reset | <live> | at 9:10 PM | Pro · 5h", joined(Bucket.COUNTDOWN_2X1, StateId.S1))
        assertEquals(
            "5h reset | <live> | at 9:10 PM | 38% | Pro · 5h | as of 6:26 PM",
            joined(Bucket.COUNTDOWN_2X2, StateId.S1),
        )
        // The estimate only where the projection says the window runs dry first (Q2).
        assertTrue(joined(Bucket.COUNTDOWN_2X2, StateId.S2).contains("62% | ~ runs out 7:54 PM"))
        assertFalse(joined(Bucket.COUNTDOWN_2X2, StateId.S3).contains("runs out"))
        // Weekly outside the last 24 h: absolute, no chronometer.
        assertEquals("Weekly reset | Sat 9:10 PM | ChatGPT · Weekly", joined(Bucket.COUNTDOWN_2X1, StateId.S4))
        assertEquals("5h reset | Starts when a message is sent | Pro · 5h", joined(Bucket.COUNTDOWN_2X1, StateId.S5))
        assertEquals("5h reset | Reset 6:19 PM | Pro · 5h", joined(Bucket.COUNTDOWN_2X1, StateId.S6))
        assertTrue(joined(Bucket.COUNTDOWN_2X2, StateId.S6).contains("Reset 6:19 PM | — |"))
        assertTrue(joined(Bucket.COUNTDOWN_2X1, StateId.S8).startsWith("Stale | <live>"))
        assertTrue(joined(Bucket.COUNTDOWN_2X1, StateId.S10).endsWith("Pro (unassigned) · 5h"))
        assertTrue(joined(Bucket.COUNTDOWN_2X2, StateId.S11).contains("62% | LEFT"))
        assertTrue(joined(Bucket.COUNTDOWN_2X1, StateId.S11).endsWith("Pro · 5h · left"))
        assertEquals("Update Cooldown", joined(Bucket.COUNTDOWN_2X1, StateId.S14))
    }

    @Test
    fun countdown_notStartedAndStale_saysBothOnce() {
        val i = forState(Face.COUNTDOWN, StateId.S5).let { x ->
            x.copy(accounts = x.accounts.mapIndexed { n, a -> if (n == 0) a.copy(fetchedAt = NOW_MS - 7 * 60 * WidgetFixtures.MIN) else a })
        }
        assertEquals(
            "Stale | Starts when a message is sent | Pro · 5h",
            texts(inflate(Bucket.COUNTDOWN_2X1, FaceStates.of(i))).joinToString(" | "),
        )
    }

    @Test
    fun bars_areDrawnAtTheirViewsSize() {
        for (bucket in listOf(Bucket.NUMBER_2X1, Bucket.NUMBER_4X1, Bucket.NUMBER_4X2, Bucket.COUNTDOWN_2X2)) {
            val v = inflate(bucket, FaceStates.of(forState(bucket.face, StateId.S1)))
            val id = if (bucket.face == Face.NUMBER) com.robin.claudeusage.R.id.num_bar else com.robin.claudeusage.R.id.cd_bar
            val bar = v.findViewById<ImageView>(id)
            val bmp = (bar.drawable as BitmapDrawable).bitmap
            assertEquals("$bucket", bmp.width, bar.layoutParams.width)
            assertEquals("$bucket", bmp.height, bar.layoutParams.height)
            val inner = (bucket.innerWidthDp * context.resources.displayMetrics.density).toInt()
            assertTrue("$bucket ${bmp.width} vs $inner", kotlin.math.abs(bmp.width - inner) <= 1)
        }
    }

    @Test
    fun countdown_weeklyInsideTheLast24h_ticksLiveAndDropsTheDay() {
        val soon = NOW_MS + 20 * 60 * WidgetFixtures.MIN
        val a = WidgetFixtures.CHATGPT.copy(
            data = com.robin.claudeusage.data.UsageData(
                null, com.robin.claudeusage.data.UsageWindow(31.0, java.time.Instant.ofEpochMilli(soon), null),
                emptyList(),
            ),
        )
        val s = FaceStates.of(WidgetFixtures.input(Face.COUNTDOWN, listOf(a)))
        assertEquals(CountForm.LIVE, s.cells.single().countForm)
        assertEquals(
            "Weekly reset | <live> | at 2:29 PM | ChatGPT · Weekly",
            texts(inflate(Bucket.COUNTDOWN_2X1, s)).joinToString(" | "),
        )
        val chrono = inflate(Bucket.COUNTDOWN_2X1, s)
            .findViewById<Chronometer>(com.robin.claudeusage.R.id.cd_chrono)
        assertTrue(chrono.isCountDown)
    }

    @Test
    fun strip() {
        assertEquals(
            "38% | Pro | 24% | Teams | 8% | Product | 31% | Weekly | ChatGPT",
            joined(Bucket.STRIP_4X1, StateId.S1),
        )
        assertEquals(
            "38% | Pro | 9:10 PM | 24% | Teams | 9:10 PM | 8% | Product | 9:10 PM | " +
                "31% | Weekly | ChatGPT | Sat 9:10 PM | as of 6:26 PM",
            joined(Bucket.STRIP_4X2, StateId.S1),
        )
        // A state lands on one ring (Teams); the others keep their own readings.
        assertTrue(joined(Bucket.STRIP_4X2, StateId.S6).contains("— | Teams | Reset 6:19 PM"))
        assertTrue(joined(Bucket.STRIP_4X2, StateId.S5).contains("— | Teams | Not started"))
        assertTrue(joined(Bucket.STRIP_4X2, StateId.S8).contains("24% | Teams | Stale"))
        assertTrue(joined(Bucket.STRIP_4X2, StateId.S12).contains("Teams | Free"))
        // At 100% the × replaces the figure.
        assertTrue(joined(Bucket.STRIP_4X1, StateId.S3).contains("38% | Pro | Teams | 8%"))
        assertTrue(joined(Bucket.STRIP_4X1, StateId.S11).contains("Pro · left"))
        assertEquals("Open Cooldown to sign in", joined(Bucket.STRIP_4X1, StateId.S10))
        assertEquals("Unavailable in this version · update Cooldown", joined(Bucket.STRIP_4X1, StateId.S14))
        // Weekly under the × at 100% on 4×2 only.
        val full = WidgetFixtures.CHATGPT.copy(data = WidgetFixtures.weeklyOnly(100.0))
        val s = FaceStates.of(WidgetFixtures.input(Face.STRIP, listOf(WidgetFixtures.pro(), full), null))
        assertTrue(texts(inflate(Bucket.STRIP_4X2, s)).contains("Weekly"))
        assertFalse(texts(inflate(Bucket.STRIP_4X1, s)).contains("Weekly"))
    }

    @Test
    fun strip_moreThanFour_showsTheFirstFourAndPlusN() {
        val five = listOf(WidgetFixtures.pro(), WidgetFixtures.TEAMS, WidgetFixtures.PRODUCT, WidgetFixtures.CHATGPT, WidgetFixtures.RESEARCH)
        val s = FaceStates.of(WidgetFixtures.input(Face.STRIP, five, null))
        assertEquals(4, s.cells.size)
        assertEquals(1, s.overflow)
        assertEquals("+1", texts(inflate(Bucket.STRIP_4X1, s)).last())
        // One account: centred, one cell.
        val one = FaceStates.of(WidgetFixtures.input(Face.STRIP, listOf(WidgetFixtures.pro()), null))
        assertEquals(1, one.cells.size)
    }

    // ---- the state table's edges ------------------------------------------------------

    @Test
    fun stateTable_edges() {
        // Unassigned draws the first account in registry order, and says so.
        val u = FaceStates.of(forState(Face.NUMBER, StateId.S10))
        assertEquals("pro", u.cells.single().key)
        assertTrue(u.cells.single().unassigned)
        // An empty registry is the no-accounts face, whatever the stored key.
        val none = FaceStates.of(WidgetFixtures.input(Face.RING, emptyList(), "pro"))
        assertEquals(FaceMessage.SIGN_IN, none.message)
        // S6 needs the fetch to predate the reset; a fetch after it is a real reading.
        val fresh = forState(Face.NUMBER, StateId.S6).let { i ->
            i.copy(accounts = i.accounts.mapIndexed { n, a -> if (n == 0) a.copy(fetchedAt = NOW_MS - WidgetFixtures.MIN) else a })
        }
        assertFalse(StateId.S6 in FaceStates.of(fresh).states)
        // No reading is never 0%.
        assertNull(FaceStates.of(forState(Face.RING, StateId.S5)).cells.single().pct)
        assertEquals("—", FaceStates.of(forState(Face.RING, StateId.S6)).cells.single().figure)
        // Nothing fetched yet on a signed-in account.
        val never = FaceStates.of(WidgetFixtures.input(Face.NUMBER, listOf(WidgetFixtures.pro(null).copy(fetchedAt = 0))))
        assertEquals(FaceStates.NO_READING, never.cells.single().sub)
        assertNull(never.asOf)
        // Never signed in.
        val out = FaceStates.of(
            WidgetFixtures.input(Face.NUMBER, listOf(WidgetFixtures.pro(null).copy(authState = com.robin.claudeusage.data.AuthState.NO_CREDENTIALS))),
        )
        assertEquals(FaceMessage.SIGN_IN, out.message)
        // Asking a weekly-only account for 5h shows its Weekly headline, tagged (S4).
        val w = FaceStates.of(WidgetFixtures.input(Face.RING, listOf(WidgetFixtures.CHATGPT)))
        assertEquals(FaceWindow.WEEKLY, w.cells.single().window)
    }

    // ---- R10 --------------------------------------------------------------------------

    @Test
    fun r10_everyFaceUnderBudget_atBothFold7Densities() {
        for (face in Face.entries) for (density in listOf(COVER_DENSITY, INNER_DENSITY, HEADROOM_DENSITY)) {
            val bytes = WidgetFace.bitmapBytes(face, Bucket.of(face), density)
            assertTrue("$face at $density: $bytes bytes", bytes < WidgetFace.BITMAP_BUDGET_BYTES)
            assertTrue(Bucket.of(face).size <= 3)
        }
    }

    /**
     * Rev F: a Fold reporting its cover and inner frames for one placement (the measured
     * 2×2 pair), and the largest single frame at the Ø150 ring cap.
     */
    @Test
    fun r10_underBudget_atReportedFrames() {
        val maps = listOf(listOf(156f to 237f, 202f to 264f), listOf(510f to 366f))
        for (face in Face.entries) for (density in listOf(COVER_DENSITY, HEADROOM_DENSITY)) for (m in maps) {
            val bytes = WidgetFace.frameBytes(m.map { (w, h) -> Frame.at(face, w, h) }, density)
            assertTrue("$face at $density $m: $bytes bytes", bytes < WidgetFace.BITMAP_BUDGET_BYTES)
        }
    }

    /** Rev F's geometry reproduces rev D exactly at rev D's own frames. */
    @Test
    fun revF_revDFramesKeepRevDGeometry() {
        assertEquals(WidgetFace.RingDp(64f, 6f), WidgetFace.ring(Bucket.RING_1X1))
        assertEquals(WidgetFace.RingDp(110f, 9f), WidgetFace.ring(Bucket.RING_2X2))
        assertEquals(16f, WidgetFace.ringFigureSp(Bucket.RING_1X1.frame))
        assertEquals(26f, WidgetFace.ringFigureSp(Bucket.RING_2X2.frame))
        assertEquals(11f, WidgetFace.stripLabelSp(Bucket.STRIP_4X2.frame))
        for (b in Bucket.entries) assertEquals(false, WidgetFace.numberStacked(b.frame))
        // …and grows the Ring to the cover's 2×2: Ø131 across the 155.8 dp frame.
        assertEquals(131f, WidgetFace.ring(Frame.at(Face.RING, 155.8f, 237f))!!.diameter)
        assertEquals(true, WidgetFace.numberStacked(Frame.at(Face.NUMBER, 155.8f, 107.8f)))
    }

    @Test
    fun r10_theEstimateIsWhatRenderDraws() {
        val density = context.resources.displayMetrics.density
        assertEquals(2.625f, density)
        val four = listOf(WidgetFixtures.pro(), WidgetFixtures.TEAMS, WidgetFixtures.PRODUCT, WidgetFixtures.CHATGPT)
        for (bucket in Bucket.entries) {
            val state = if (bucket.face == Face.STRIP) FaceStates.of(WidgetFixtures.input(Face.STRIP, four, null))
            else FaceStates.of(forState(bucket.face, StateId.S1))
            val drawn = bitmaps(inflate(bucket, state))
            assertTrue("$bucket: at most five bitmaps", drawn.size <= 5)
            assertEquals("$bucket", WidgetFace.bucketBytes(bucket, density), drawn.sumOf { it.toLong() })
        }
    }

    private fun bitmaps(root: View): List<Int> = buildList {
        fun walk(v: View) {
            if (v is ImageView) (v.drawable as? BitmapDrawable)?.bitmap?.let { add(it.allocationByteCount) }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
    }

    @Test
    fun renderRefusesAnotherFacesBucket() {
        val s = FaceStates.of(forState(Face.RING, StateId.S1))
        val failed = runCatching { WidgetFace.render(context, Face.RING, Bucket.NUMBER_2X1, s) }
        assertNotNull(failed.exceptionOrNull())
    }

    private companion object {
        /** Both Fold 7 screens run at 420 dpi (2.625×), measured 2026-08-21. */
        const val COVER_DENSITY = 2.625f
        const val INNER_DENSITY = 2.625f
        /** A raised Display size, for headroom. */
        const val HEADROOM_DENSITY = 3.5f
        val RELATIVE = Regex("""\bin \d+[hmd]\b|\d+[dh] \d+[hm]\b|\bago\b|Updated""")
        val H24 = Regex("""\b(1[3-9]|2[0-3]):[0-5]\d\b""")
    }
}
