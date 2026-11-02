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

    /** [joined] at a reported [frame] rather than its bucket's design size. */
    private fun joinedAt(frame: Frame, s: StateId): String {
        val state = FaceStates.of(forState(frame.bucket.face, s))
        val v = WidgetFace.render(context, frame.bucket.face, frame, state).apply(context, FrameLayout(context))
        return texts(v).joinToString(" | ")
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
        // CCRM-103 (Hybrid Notification)'s shape grammar (rev D §5): the 1×1 is the 5h ring
        // over a bare Weekly bar; the 2×2 the ring, the name and the Weekly row.
        assertEquals("38%", joined(Bucket.RING_1X1, StateId.S1))
        assertEquals("38% | Pro | Weekly | 20%", joined(Bucket.RING_2X2, StateId.S1))
        // At 100% the ring keeps its figure (the page's ring() draws no cross).
        assertEquals("100%", joined(Bucket.RING_1X1, StateId.S3))
        assertEquals("100% | Pro | Weekly | 20%", joined(Bucket.RING_2X2, StateId.S3))
        // B1: a weekly-only account's Weekly figure where the ring would be, and its row.
        assertEquals("31% | ChatGPT · Weekly | Weekly | 31%", joined(Bucket.RING_2X2, StateId.S4))
        // S5: rev D's 2×2 has no reset line, so the name says it.
        assertTrue(joined(Bucket.RING_2X2, StateId.S5).startsWith("— | Pro · not started | Weekly | 20%"))
        assertTrue(joined(Bucket.RING_2X2, StateId.S6).startsWith("— | Pro |"))
        // Rev H (Q4): no "(unassigned)" on the name.
        assertTrue(joined(Bucket.RING_2X2, StateId.S10).startsWith("38% | Pro | Weekly"))
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
        // The shape grammar (rev D §5): the 5h ring at the left of the Weekly name, figure and
        // bar; the 2×1 stacks the name over the figure, the 4×1 keeps "Pro · Weekly".
        assertEquals("38% | Pro | 20%", joined(Bucket.NUMBER_2X1, StateId.S1))
        assertEquals("38% | Pro · Weekly | 20% | Resets Sat 9:10 PM | as of 6:26 PM", joined(Bucket.NUMBER_4X1, StateId.S1))
        // The window chips went (both windows show); the cycler stays.
        assertEquals(
            "38% | Pro · Weekly | 20% | Resets Sat 9:10 PM | as of 6:26 PM | Pro ⇄",
            joined(Bucket.NUMBER_4X2, StateId.S1),
        )
        // B1: a weekly-only account keeps today's face, no ring — on 2×1 its name, with
        // "Weekly" as its own second line.
        assertEquals("ChatGPT | Weekly | 31%", joined(Bucket.NUMBER_2X1, StateId.S4))
        assertEquals(
            "ChatGPT · Weekly | 31% | Resets Sat 9:10 PM | as of 6:26 PM | ChatGPT ⇄",
            joined(Bucket.NUMBER_4X2, StateId.S4),
        )
        // The 5h's states land on the ring.
        assertTrue(joined(Bucket.NUMBER_4X1, StateId.S5).startsWith("— | Pro · Weekly | 20%"))
        assertTrue(joined(Bucket.NUMBER_4X1, StateId.S6).startsWith("— | Pro · Weekly | 20%"))
        assertTrue(joined(Bucket.NUMBER_4X1, StateId.S7).contains("Resets Sat 9:10 PM")) // S7 keeps it
        assertTrue(joined(Bucket.NUMBER_4X1, StateId.S8).contains("| Stale |"))
        assertEquals("Account removed · tap to choose", joined(Bucket.NUMBER_4X1, StateId.S9))
        // Rev D's 84 dp 2×1 beside the ring has no height for the pill; the tap opens the config.
        assertEquals("38% | Pro | 20%", joined(Bucket.NUMBER_2X1, StateId.S10))
        // Rev H (Q4): the stamp slot says how to choose, the name stays whole.
        assertEquals("38% | Pro · Weekly | 20% | Resets Sat 9:10 PM | tap to choose account", joined(Bucket.NUMBER_4X1, StateId.S10))
        // Left: the bare figure flips at 2×1; the LEFT caption from 4×1 up.
        assertEquals("62% | Pro | 80%", joined(Bucket.NUMBER_2X1, StateId.S11))
        assertTrue(joined(Bucket.NUMBER_4X1, StateId.S11).startsWith("62% | Pro · Weekly | 80% | LEFT"))
        assertEquals("No usage on this plan", joined(Bucket.NUMBER_4X2, StateId.S12))
        assertEquals("Update Cooldown", joined(Bucket.NUMBER_2X1, StateId.S14))
        assertEquals("Unavailable in this version · update Cooldown", joined(Bucket.NUMBER_4X1, StateId.S14))
    }

    /**
     * Fable C5: an account with only a 5h never gets a 5h bar. The Number draws the ring
     * alone, its Weekly slot (figure and bar) empty, as the Ring face leaves its Weekly row.
     */
    @Test
    fun fiveHourOnly_isTheRingAlone_neverA5hBar() {
        val fiveOnly = com.robin.claudeusage.data.UsageData(
            session = com.robin.claudeusage.data.UsageWindow(38.0, WidgetFixtures.RESET_5H, null),
            weekly = null, modelCaps = emptyList(),
        )
        for (window in FaceWindow.entries) {
            val n = FaceStates.of(WidgetFixtures.input(Face.NUMBER, listOf(WidgetFixtures.pro(fiveOnly)), window = window))
            assertEquals(FaceWindow.SESSION, n.cells.single().window)
            assertNull(n.cells.single().companion)
            for (bucket in Bucket.of(Face.NUMBER)) {
                val v = inflate(bucket, n)
                assertTrue("$bucket ring", shown(v.findViewById(com.robin.claudeusage.R.id.num_ring_box)))
                assertFalse("$bucket bar", shown(v.findViewById(com.robin.claudeusage.R.id.num_bar)))
                assertFalse("$bucket fig", shown(v.findViewById(com.robin.claudeusage.R.id.num_fig)))
                assertFalse("$bucket fig below", shown(v.findViewById(com.robin.claudeusage.R.id.num_fig_below)))
                // The 5h figure appears once — in the ring's bore, nowhere else.
                val t = texts(v)
                assertEquals("$bucket $t", 1, t.count { it == "38%" })
                assertFalse("$bucket $t", t.any { it.contains("Weekly") })
                // No face writes "5h" (rev D §5): the ring is the 5h, the label the name alone.
                assertFalse("$bucket $t", t.any { it.contains("5h") })
            }
            assertEquals("38% | Pro", texts(inflate(Bucket.NUMBER_2X1, n)).joinToString(" | "))
            assertTrue(texts(inflate(Bucket.NUMBER_4X1, n)).joinToString(" | ").startsWith("38% | Pro | Resets "))
        }
        // The Ring face already leaves its Weekly row empty.
        val r = FaceStates.of(WidgetFixtures.input(Face.RING, listOf(WidgetFixtures.pro(fiveOnly))))
        for (bucket in Bucket.of(Face.RING)) {
            val t = texts(inflate(bucket, r))
            assertFalse("$bucket $t", t.any { it.contains("Weekly") })
            assertEquals("$bucket $t", 1, t.count { it == "38%" })
        }
    }

    /** Fable C6: the Ring and Number's window is fixed, so the config screen has no Window row for them. */
    @Test
    fun configWindowRow_isHiddenForRingAndNumber() {
        assertFalse(windowRowShown(Face.RING))
        assertFalse(windowRowShown(Face.NUMBER))
        assertTrue(windowRowShown(Face.COUNTDOWN))
        assertTrue(windowRowShown(Face.STRIP))
    }

    /**
     * Fable C6: the picker previews are the §5 faces — a 5h ring, a Weekly bar, no window
     * chips — and inflate as plain RemoteViews, with no code, the way the picker inflates them.
     */
    @Test
    fun pickerPreviews_areTheShapeGrammarFaces() {
        fun preview(id: Int) = texts(
            android.widget.RemoteViews(context.packageName, id).apply(context, FrameLayout(context)),
        ).joinToString(" | ")
        assertEquals("38% | Pro | Weekly | 20%", preview(com.robin.claudeusage.R.layout.widget_preview_ring))
        assertEquals("38% | Pro · Weekly | 20% | Resets Sat 9:10 PM | as of 6:29 PM", preview(com.robin.claudeusage.R.layout.widget_preview_number))
        assertEquals("38% | 2:41:07 | at 9:10 PM | Pro", preview(com.robin.claudeusage.R.layout.widget_preview_countdown))
        assertEquals("38% | Pro | 62% | Work | 20% | ChatGPT", preview(com.robin.claudeusage.R.layout.widget_preview_strip))
    }

    /** Q "Ring widget's window choice" and rev D §5: a stored window is kept and ignored. */
    @Test
    fun theStoredWindowIsIgnored_ringIs5hNumberIsWeekly() {
        for (window in FaceWindow.entries) {
            val n = FaceStates.of(forState(Face.NUMBER, StateId.S1).copy(window = window))
            assertEquals(FaceWindow.WEEKLY, n.cells.single().window)
            assertEquals(FaceWindow.SESSION, n.cells.single().companion!!.window)
            val all = texts(inflate(Bucket.NUMBER_4X2, n)).joinToString(" | ")
            assertTrue(all, all.startsWith("38% | Pro · Weekly | 20% | Resets Sat 9:10 PM"))
            val r = FaceStates.of(forState(Face.RING, StateId.S1).copy(window = window))
            assertEquals(FaceWindow.SESSION, r.cells.single().window)
            assertEquals(FaceWindow.WEEKLY, r.cells.single().companion!!.window)
        }
        // The Countdown keeps its window.
        val cd = FaceStates.of(forState(Face.COUNTDOWN, StateId.S1).copy(window = FaceWindow.WEEKLY))
        assertEquals(FaceWindow.WEEKLY, cd.cells.single().window)
        assertNull(cd.cells.single().companion)
    }

    @Test
    fun countdown() {
        // A 5h countdown leads with the ring in place of the "5h reset" caption (rev D §5).
        assertEquals("38% | <live> | at 9:10 PM | Pro", joined(Bucket.COUNTDOWN_2X1, StateId.S1))
        // CCBG-66 (Countdown Ring Small): on the 2×2 the ring is the hero, under the count.
        assertTrue(joined(Bucket.COUNTDOWN_2X2, StateId.S1).startsWith("<live> | at 9:10 PM | 38% | Pro"))
        // The estimate only where the projection says the window runs dry first (Q2), and
        // under the hero only where the frame keeps room for it: not at rev D's 181×168.
        val oneUi = Frame.at(Face.COUNTDOWN, 156f, 237f)
        val s2 = joinedAt(oneUi, StateId.S2)
        assertTrue(s2, s2.startsWith("<live> | at 9:10 PM | 62% | ~ runs out 7:54 PM | Pro"))
        assertFalse(joined(Bucket.COUNTDOWN_2X2, StateId.S2).contains("runs out"))
        assertFalse(joinedAt(oneUi, StateId.S3).contains("runs out"))
        // A wide 2×2 keeps the ring beside the count, and its estimate.
        val wide = joinedAt(Frame.at(Face.COUNTDOWN, 510f, 237f), StateId.S2)
        assertTrue(wide, wide.startsWith("62% | <live> | at 9:10 PM | ~ runs out 7:54 PM"))
        // Weekly outside the last 24 h: absolute, no chronometer — today's face, no ring.
        assertEquals("Weekly reset | Sat 9:10 PM | ChatGPT · Weekly", joined(Bucket.COUNTDOWN_2X1, StateId.S4))
        assertEquals("— | Not started | Pro", joined(Bucket.COUNTDOWN_2X1, StateId.S5))
        assertEquals("— | Reset 6:19 PM | Pro", joined(Bucket.COUNTDOWN_2X1, StateId.S6))
        assertTrue(joined(Bucket.COUNTDOWN_2X2, StateId.S6).startsWith("Reset 6:19 PM | —"))
        // The ring took the caption, so the account line says "Stale" and "tap to choose".
        assertEquals("38% | <live> | at 9:10 PM | Pro · Stale", joined(Bucket.COUNTDOWN_2X1, StateId.S8))
        assertEquals("38% | <live> | at 9:10 PM | Pro · tap to choose", joined(Bucket.COUNTDOWN_2X1, StateId.S10))
        assertTrue(joined(Bucket.COUNTDOWN_2X2, StateId.S10).contains("Pro · tap to choose"))
        assertTrue(joined(Bucket.COUNTDOWN_2X2, StateId.S11).contains("Pro · left"))
        assertTrue(joined(Bucket.COUNTDOWN_2X1, StateId.S11).endsWith("Pro · left"))
        assertEquals("Update Cooldown", joined(Bucket.COUNTDOWN_2X1, StateId.S14))
        // A Weekly countdown keeps its caption, figure row and bar.
        val weekly = FaceStates.of(forState(Face.COUNTDOWN, StateId.S1).copy(window = FaceWindow.WEEKLY))
        assertTrue(texts(inflate(Bucket.COUNTDOWN_2X2, weekly)).joinToString(" | ").startsWith("Weekly reset | Sat 9:10 PM | 20%"))
    }

    @Test
    fun countdown_notStartedAndStale_saysBothOnce() {
        val i = forState(Face.COUNTDOWN, StateId.S5).let { x ->
            x.copy(accounts = x.accounts.mapIndexed { n, a -> if (n == 0) a.copy(fetchedAt = NOW_MS - 7 * 60 * WidgetFixtures.MIN) else a })
        }
        assertEquals(
            "— | Not started | Pro · Stale",
            texts(inflate(Bucket.COUNTDOWN_2X1, FaceStates.of(i))).joinToString(" | "),
        )
    }

    @Test
    fun bars_areDrawnAtTheirViewsSize() {
        for (bucket in listOf(Bucket.NUMBER_2X1, Bucket.NUMBER_4X1, Bucket.NUMBER_4X2, Bucket.COUNTDOWN_2X2)) {
            // A 5h Countdown has no bar (the 5h is a ring): the Weekly one has.
            val input = forState(bucket.face, StateId.S1).let { if (bucket.face == Face.COUNTDOWN) it.copy(window = FaceWindow.WEEKLY) else it }
            val v = inflate(bucket, FaceStates.of(input))
            val id = if (bucket.face == Face.NUMBER) com.robin.claudeusage.R.id.num_bar else com.robin.claudeusage.R.id.cd_bar
            val bar = v.findViewById<ImageView>(id)
            val bmp = (bar.drawable as BitmapDrawable).bitmap
            assertEquals("$bucket", bmp.width, bar.layoutParams.width)
            assertEquals("$bucket", bmp.height, bar.layoutParams.height)
            // The Number's bar spans the Weekly column beside the ring; the Countdown's the inner width.
            val width = (WidgetFace.bar(bucket)!!.width * context.resources.displayMetrics.density).toInt()
            assertTrue("$bucket ${bmp.width} vs $width", kotlin.math.abs(bmp.width - width) <= 1)
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
        // The shape grammar (rev D §5): each account a 5h ring over its Weekly bar — no Weekly
        // digits at 4×1 (Q "Strip 4×1 Weekly digits"); ChatGPT (no 5h, B1) its Weekly figure
        // where the ring would be.
        assertEquals(
            "38% | Pro | 24% | Teams | 8% | Product | 31% | ChatGPT",
            joined(Bucket.STRIP_4X1, StateId.S1),
        )
        // 4×2 adds the bar's figure; the short reset is the ring's (the Weekly's for B1).
        assertEquals(
            "38% | 20% | Pro | 9:10 PM | 24% | 20% | Teams | 9:10 PM | 8% | 20% | Product | 9:10 PM | " +
                "31% | 31% | ChatGPT | Sat 9:10 PM | as of 6:26 PM",
            joined(Bucket.STRIP_4X2, StateId.S1),
        )
        // A state lands on one ring (Teams); the others keep their own readings.
        assertTrue(joined(Bucket.STRIP_4X2, StateId.S6).contains("— | 20% | Teams | Reset 6:19 PM"))
        assertTrue(joined(Bucket.STRIP_4X2, StateId.S5).contains("— | 20% | Teams | Not started"))
        assertTrue(joined(Bucket.STRIP_4X2, StateId.S8).contains("24% | 20% | Teams | Stale"))
        assertTrue(joined(Bucket.STRIP_4X2, StateId.S12).contains("Teams | Free"))
        // At 100% the ring keeps its figure.
        assertTrue(joined(Bucket.STRIP_4X1, StateId.S3).contains("38% | Pro | 100% | Teams | 8%"))
        assertTrue(joined(Bucket.STRIP_4X1, StateId.S11).contains("Pro · left"))
        assertEquals("Open Cooldown to sign in", joined(Bucket.STRIP_4X1, StateId.S10))
        assertEquals("Unavailable in this version · update Cooldown", joined(Bucket.STRIP_4X1, StateId.S14))
        // No "Weekly" tag in a bore any more: a weekly-only account is its figure (B1).
        val full = WidgetFixtures.CHATGPT.copy(data = WidgetFixtures.weeklyOnly(100.0))
        val s = FaceStates.of(WidgetFixtures.input(Face.STRIP, listOf(WidgetFixtures.pro(), full), null))
        for (bucket in listOf(Bucket.STRIP_4X1, Bucket.STRIP_4X2)) {
            val t = texts(inflate(bucket, s))
            assertTrue("$bucket $t", "100%" in t)
            assertFalse("$bucket $t", "Weekly" in t)
        }
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
     * 2×2 pair), and the largest single frame; rev H adds the 6×2 and 4×3 pairs.
     */
    @Test
    fun r10_underBudget_atReportedFrames() {
        // Rev H (R10 at 4 MB): the worst Fold pairs — the Strip and the Ring at 6×2 and 4×3 on
        // the cover with the inner frame a placement also reports.
        val maps = listOf(
            listOf(156f to 237f, 202f to 264f), listOf(510f to 366f),
            listOf(510f to 237f, 470f to 264f), listOf(333f to 366f, 470f to 414f),
        )
        // At the Fold 7's own density every pair is sent whole.
        for (face in Face.entries) for (m in maps) {
            val bytes = WidgetFace.frameBytes(m.map { (w, h) -> Frame.at(face, w, h) }, COVER_DENSITY)
            assertTrue("$face $m: $bytes bytes", bytes < WidgetFace.BITMAP_BUDGET_BYTES)
        }
        // At the headroom density what withinBudget sends stays under, keeping the smallest.
        for (face in Face.entries) for (m in maps) {
            val sizes = m.map { (w, h) -> android.util.SizeF(w, h) }.sortedBy { it.width * it.height }
            val kept = WidgetHost.withinBudget(face, sizes, HEADROOM_DENSITY)
            assertEquals(sizes.first().width, kept.first().widthDp)
            val bytes = WidgetFace.frameBytes(kept, HEADROOM_DENSITY)
            assertTrue("$face at headroom $m: $bytes bytes", kept.size == 1 || bytes < WidgetFace.BITMAP_BUDGET_BYTES)
        }
    }

    /**
     * Rev H (CCBG-44 (Widget Fill)): the hero fills the frame One UI reports on the Fold 7's
     * six-column cover — the numbers design/2026-09-26-widgets-fill-revh.html draws.
     */
    @Test
    fun revH_theHeroFillsTheFrame() {
        fun ring(w: Float, h: Float) = WidgetFace.ring(Frame.at(Face.RING, w, h))!!.diameter
        assertEquals(68f, ring(84f, 108f))            // 1×1, its Weekly bar under it
        assertEquals(68f, ring(156f, 108f))           // 2×1, the ring over its bar
        assertEquals(129f, ring(156f, 237f))          // 2×2, name and Weekly row under it
        assertEquals(160f, ring(333f, 366f))          // 4×3, at the cap
        assertEquals(160f, ring(510f, 237f))          // 6×2, beside its lines
        // The shape grammar (rev D §5): rev H's companion ring is now the Weekly bar — on the
        // 1×1's column the bar alone, as wide as the ring; elsewhere the row.
        val one = WidgetFace.ringLayout(Bucket.RING_1X1.frame).weekly
        assertTrue(one.barOnly)
        assertEquals(52f, one.bar.width)
        assertEquals(160f, WidgetFace.ringLayout(Frame.at(Face.RING, 333f, 366f)).weekly.rowDp)
        assertEquals(150f, WidgetFace.ringLayout(Bucket.RING_2X2.frame).weekly.rowDp)
        assertTrue(WidgetFace.ringLayout(Frame.at(Face.RING, 244f, 108f)).horizontal)
        // Number, Countdown, Strip.
        assertEquals(72f, WidgetFace.numberFigSp(Frame.at(Face.NUMBER, 510f, 237f)))
        // With the 5h ring at its left (rev D §5) the 2×1's figure fits what the ring leaves.
        assertEquals(29f, WidgetFace.numberFigSp(Frame.at(Face.NUMBER, 156f, 108f)))
        assertTrue(WidgetFace.numberTall(Frame.at(Face.NUMBER, 156f, 237f)))
        assertTrue(WidgetFace.numberControls(Frame.at(Face.NUMBER, 156f, 237f)))
        assertEquals(80f, WidgetFace.countSp(Frame.at(Face.COUNTDOWN, 510f, 237f)))
        assertNotNull(WidgetFace.countdownSideDp(Frame.at(Face.COUNTDOWN, 510f, 108f)))
        assertNull(WidgetFace.countdownSideDp(Frame.at(Face.COUNTDOWN, 333f, 108f)))
        assertEquals(120f, WidgetFace.ring(Frame.at(Face.STRIP, 510f, 237f), 3)!!.diameter)
        // The Strip's row is a centred group: each cell the ring plus 40 dp, not a third.
        assertEquals(160f, WidgetFace.stripPitch(Frame.at(Face.STRIP, 510f, 237f), 3, overflow = false))
        // Rev D's own frames, the shape grammar's numbers (§5): Ring 1×1 Ø52, Number 4×1 Ø60
        // and 2×1 Ø52 (28 sp), Countdown 2×1 Ø44 (24 sp), Strip 4×1 Ø42 and 4×2 Ø64.
        assertEquals(52f, WidgetFace.ring(Bucket.RING_1X1)!!.diameter)
        assertEquals(97f, WidgetFace.ring(Bucket.RING_2X2)!!.diameter)
        assertEquals(60f, WidgetFace.ring(Bucket.NUMBER_4X1)!!.diameter)
        assertEquals(52f, WidgetFace.ring(Bucket.NUMBER_2X1)!!.diameter)
        assertEquals(28f, WidgetFace.numberFigSp(Bucket.NUMBER_2X1.frame))
        assertEquals(32f, WidgetFace.numberFigSp(Bucket.NUMBER_4X1.frame))
        assertEquals(44f, WidgetFace.ring(Bucket.COUNTDOWN_2X1)!!.diameter)
        assertEquals(24f, WidgetFace.countSp(Bucket.COUNTDOWN_2X1.frame, ring = true))
        assertEquals(26f, WidgetFace.countSp(Bucket.COUNTDOWN_2X1.frame))
        assertEquals(42f, WidgetFace.ring(Bucket.STRIP_4X1, 4)!!.diameter)
        assertEquals(64f, WidgetFace.ring(Bucket.STRIP_4X2, 4)!!.diameter)
        // The 110 dp minimum Countdown has no room for the ring: today's caption face.
        assertNull(WidgetFace.ring(Frame.at(Face.COUNTDOWN, 110f, 84f)))
    }

    /**
     * CCBG-66 (Countdown Ring Small), Robin 2026-11-02 "Scale the ring up": a 5h Countdown
     * 2×2 that is not wide draws the ring as the hero — centred under the count, sized from
     * the frame as the Ring face's is, up to its Ø160 cap.
     */
    @Test
    fun countdown2x2_theRingIsTheHeroUnderTheCount() {
        // Rev D's 181×168: Ø61 (Ø44 beside the count before), the estimate line gone.
        val design = Bucket.COUNTDOWN_2X2.frame
        assertTrue(WidgetFace.cdHero(design))
        assertEquals(61f, WidgetFace.ring(design)!!.diameter)
        assertEquals(28f, WidgetFace.countSp(design, ring = true))
        assertFalse(WidgetFace.cdHeroEstimate(design))
        // One UI's cover 2×2 (156×237): Ø108 with the estimate kept; 4×3 at the cap.
        val oneUi = Frame.at(Face.COUNTDOWN, 156f, 237f)
        assertEquals(108f, WidgetFace.ring(oneUi)!!.diameter)
        assertTrue(WidgetFace.cdHeroEstimate(oneUi))
        assertEquals(160f, WidgetFace.ring(Frame.at(Face.COUNTDOWN, 333f, 366f))!!.diameter)
        // A wide 2×2 and the 2×1 keep today's Ø44 beside the count.
        val wide = Frame.at(Face.COUNTDOWN, 510f, 237f)
        assertFalse(WidgetFace.cdHero(wide))
        assertEquals(44f, WidgetFace.ring(wide)!!.diameter)
        assertFalse(WidgetFace.cdHero(Bucket.COUNTDOWN_2X1.frame))

        // Laid out: the count's line ends above the hero, which is centred and drawn at its size.
        val d = context.resources.displayMetrics.density
        for (frame in listOf(design, oneUi)) {
            val host = FrameLayout(context)
            val state = FaceStates.of(forState(Face.COUNTDOWN, StateId.S1))
            val root = WidgetFace.render(context, Face.COUNTDOWN, frame, state).apply(context, host)
            host.addView(root)
            val w = (frame.widthDp * d).toInt()
            val h = (frame.heightDp * d).toInt()
            host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, w, h)
            val chrono = root.findViewById<Chronometer>(com.robin.claudeusage.R.id.cd_chrono)
            val box = root.findViewById<View>(com.robin.claudeusage.R.id.cd_hero_box)
            val ring = root.findViewById<ImageView>(com.robin.claudeusage.R.id.cd_hero)
            assertTrue("$frame", shown(box))
            assertFalse("$frame", shown(root.findViewById(com.robin.claudeusage.R.id.cd_ring_box)))
            assertTrue("$frame", shown(chrono))
            assertTrue("$frame count ${offset(chrono, root, true) + chrono.height} vs ring ${offset(box, root, true)}",
                offset(chrono, root, true) + chrono.height <= offset(box, root, true))
            val dia = WidgetFace.ring(frame)!!.diameter * d
            assertTrue("$frame ${ring.width} vs $dia", kotlin.math.abs(ring.width - dia) <= 1.5f)
            assertTrue("$frame ${ring.height} vs $dia", kotlin.math.abs(ring.height - dia) <= 1.5f)
            val centre = offset(box, root, false) + box.width / 2f
            assertTrue("$frame centre $centre vs ${w / 2f}", kotlin.math.abs(centre - w / 2f) <= 2f)
        }
    }

    /** [v]'s top ([vertical]) or left inside [root], in px. */
    private fun offset(v: View, root: View, vertical: Boolean): Int {
        var x = 0
        var p: View? = v
        while (p != null && p !== root) { x += if (vertical) p.top else p.left; p = p.parent as? View }
        return x
    }

    /** The shape grammar: a Ring's companion is its Weekly bar; the Number's its 5h ring. */
    @Test
    fun theCompanionIsTheOtherWindow() {
        val s = FaceStates.of(forState(Face.RING, StateId.S1))
        assertEquals(FaceWindow.WEEKLY, s.cells.single().companion!!.window)
        val v = WidgetFace.render(context, Face.RING, Frame.at(Face.RING, 333f, 366f), s).apply(context, FrameLayout(context))
        val t = texts(v).joinToString(" | ")
        assertTrue(t, t.startsWith("38% | Pro | Weekly | 20% | Resets 9:10 PM"))
        // A one-window account has no companion.
        assertNull(FaceStates.of(WidgetFixtures.input(Face.RING, listOf(WidgetFixtures.CHATGPT))).cells.single().companion)
        // The Strip: every account's ring carries its Weekly.
        val strip = FaceStates.of(forState(Face.STRIP, StateId.S1))
        assertEquals(listOf(FaceWindow.WEEKLY, FaceWindow.WEEKLY, FaceWindow.WEEKLY, null), strip.cells.map { it.companion?.window })
    }

    /** Rev H: a stacked 3×2 Number has no row for LEFT, so its label says it; a synthetic
     *  face never draws more bitmap than the estimate. */
    @Test
    fun revH_stackedLeftAndTheRibbonedEstimate() {
        val s = FaceStates.of(forState(Face.NUMBER, StateId.S11))
        val v = WidgetFace.render(context, Face.NUMBER, Frame.at(Face.NUMBER, 244f, 237f), s).apply(context, FrameLayout(context))
        // The ring's figure first, then the label.
        assertTrue(texts(v).joinToString(" | "), texts(v)[1].endsWith("· left"))
        val synth = FaceStates.of(forState(Face.RING, StateId.S13))
        val density = context.resources.displayMetrics.density
        for ((w, h) in listOf(300f to 230f, 250f to 210f, 333f to 366f, 190f to 175f, 200f to 190f, 220f to 185f)) {
            val frame = Frame.at(Face.RING, w, h)
            val drawn = bitmaps(WidgetFace.render(context, Face.RING, frame, synth).apply(context, FrameLayout(context)))
            assertTrue("$w×$h", drawn.sumOf { it.toLong() } <= WidgetFace.frameBytes(frame, density))
        }
    }

    /** Rev H: the Number 2×2 carries a cycler on its own row; the chips went (shape grammar). */
    @Test
    fun revH_theNumber2x2HasItsControls() {
        val s = FaceStates.of(forState(Face.NUMBER, StateId.S1))
        val frame = Frame.at(Face.NUMBER, 156f, 237f)
        val v = WidgetFace.render(context, Face.NUMBER, frame, s).apply(context, FrameLayout(context))
        assertTrue(shown(v.findViewById(com.robin.claudeusage.R.id.num_cycler2)))
        assertFalse(shown(v.findViewById(com.robin.claudeusage.R.id.num_cycler)))
        assertFalse(shown(v.findViewById(com.robin.claudeusage.R.id.num_ctrl_row)))
        assertEquals(com.robin.claudeusage.R.id.num_cycler2, WidgetFace.cycler(frame))
        assertTrue(shown(v.findViewById(com.robin.claudeusage.R.id.num_ring)))
    }

    @Test
    fun r10_theEstimateIsWhatRenderDraws() {
        val density = context.resources.displayMetrics.density
        assertEquals(2.625f, density)
        // Four accounts with both windows: the Strip's worst case, four rings and four bars.
        val four = listOf(WidgetFixtures.pro(), WidgetFixtures.TEAMS, WidgetFixtures.PRODUCT, WidgetFixtures.RESEARCH)
        for (bucket in Bucket.entries) {
            val state = if (bucket.face == Face.STRIP) FaceStates.of(WidgetFixtures.input(Face.STRIP, four, null))
            else FaceStates.of(forState(bucket.face, StateId.S1))
            val drawn = bitmaps(inflate(bucket, state))
            assertTrue("$bucket: at most eight bitmaps", drawn.size <= 8)
            // Exact on a short face; on a tall one the estimate also covers the ribboned draw.
            if (bucket.tall) assertTrue("$bucket", drawn.sumOf { it.toLong() } <= WidgetFace.bucketBytes(bucket, density))
            else assertEquals("$bucket", WidgetFace.bucketBytes(bucket, density), drawn.sumOf { it.toLong() })
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
