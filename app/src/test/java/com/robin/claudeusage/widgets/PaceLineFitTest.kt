package com.robin.claudeusage.widgets

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.R
import com.robin.claudeusage.data.Pace
import com.robin.claudeusage.ui.PaceText
import com.robin.claudeusage.widgets.WidgetFixtures.forState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

/**
 * CCRM-104 (Pace Line Parity), Android widgets: the estimator that picks a pace line's form
 * by column width and font scale (wireframe §8's em classes, Astra C1-2 and C1-4), and the
 * faces that use it. The worst-case line is the wireframe's "23h 59m ahead · resets Fri 12:30 PM" (CCRM-108
 * (Reset Named): the reset is named, then bare, then the weekday alone — a bare clock always means today).
 */
class PaceLineFitTest {
    private val h = 3_600_000L
    private val now = Instant.parse("2026-11-04T14:00:00Z").toEpochMilli()
    private val scales = listOf(1.0f, 1.3f, 2.0f)

    /** The worst case: full 197 dp, without "resets" 161 dp, the weekday alone 109 dp, floor 85 dp at 12 sp and font scale 1.0. */
    private val worst = PaceLineFit.forms(
        Pace.MarkTime.Over(now + 23 * h + 59 * 60_000L, 23 * h + 59 * 60_000L),
        Instant.parse("2026-11-06T12:30:00Z"), now, ZoneOffset.UTC,
    )

    private fun texts(forms: List<PaceText.PaceLine>) = forms.map { it.text }

    @Test fun formsFollowTheDropOrder_andTheWidgetsOwnClockRule() {
        assertEquals(
            listOf("23h 59m ahead · resets Fri 12:30 PM", "23h 59m ahead · Fri 12:30 PM", "23h 59m ahead · Fri", "23h 59m ahead"),
            texts(worst),
        )
        assertEquals("23h 59m ahead", worst.last().tinted)
        // Later today the clock has no weekday: a bare clock means today, so there is no weekday form.
        val soon = PaceLineFit.forms(Pace.MarkTime.Under(2 * h), Instant.ofEpochMilli(now + 5 * h), now, ZoneOffset.UTC)
        assertEquals(listOf("2h 0m behind · resets 7:00 PM", "2h 0m behind · 7:00 PM", "2h 0m behind"), texts(soon))
        // On the mark: the floor is "On the mark".
        val on = PaceLineFit.forms(Pace.MarkTime.On, Instant.ofEpochMilli(now + 5 * h), now, ZoneOffset.UTC)
        assertEquals(listOf("On the mark · resets 7:00 PM", "On the mark · 7:00 PM", "On the mark"), texts(on))
        assertTrue(PaceLineFit.forms(null, Instant.ofEpochMilli(now + 5 * h), now, ZoneOffset.UTC).isEmpty())
    }

    @Test fun theEstimatorMatchesTheWireframeMeasurements() {
        fun w(i: Int, sp: Float) = PaceLineFit.widthDp(worst[i], sp, 1f)
        assertEquals(197f, w(0, 12f), 1f)
        assertEquals(161f, w(1, 12f), 1f)
        assertEquals(109f, w(2, 12f), 1f)
        assertEquals(85f, w(3, 12f), 1f)
        // The estimate grows with the font scale and the size.
        assertEquals(w(0, 12f) * 1.3f, PaceLineFit.widthDp(worst[0], 12f, 1.3f), 0.01f)
        assertEquals(66f, PaceLineFit.stampDp(1f), 0.01f)
    }

    // ---- the wireframe's own columns (§6, §8): Number 4×1 drawn at 333 and 244 dp ---------

    @Test fun numberAtTheWireframesColumns_weekdayDropsBesideTheStamp_andTheStampYieldsAt244() {
        // 333 dp: a 221 dp column; beside the 66 dp stamp 155 dp — the line ends on the weekday.
        val at333 = PaceLineFit.pick(worst, 221f, hasStamp = true, sp = 12f, fontScale = 1f)!!
        assertEquals("23h 59m ahead · Fri", at333.choice.line.text)
        assertTrue(at333.stamp)
        // 244 dp: a 132 dp column; even the floor leaves no room beside the stamp (66 dp), so
        // the stamp yields (Astra C1-2) and the drop order runs on the full column: the weekday
        // (the wireframe's fit table, "Widget Number 4×1 at 244 dp": −clock).
        val at244 = PaceLineFit.pick(worst, 132f, hasStamp = true, sp = 12f, fontScale = 1f)!!
        assertEquals("23h 59m ahead · Fri", at244.choice.line.text)
        assertFalse(at244.stamp)
        // No stamp on the row: the whole column.
        assertEquals("23h 59m ahead · resets Fri 12:30 PM", PaceLineFit.pick(worst, 221f, false, 12f, 1f)!!.choice.line.text)
    }

    // ---- the shipped geometry, at each cover width and font scale -----------------------

    private fun numberPick(w: Float, fs: Float) =
        PaceLineFit.pick(worst, WidgetFace.numberColDp(Frame(Bucket.NUMBER_4X1, w, 84f)), true, 12f, fs)!!

    private fun ringLineDp(w: Float): Float {
        val f = Frame(Bucket.RING_2X2, w, 237f)
        val l = WidgetFace.ringLayout(f)
        return if (l.horizontal) f.innerWidthDp - l.main.diameter - l.gapDp else f.innerWidthDp
    }

    private fun ringPick(w: Float, fs: Float) =
        PaceLineFit.choose(worst, ringLineDp(w), 13f, fs)!!

    @Test fun ringTwoByTwo_formByWidthAndFontScale() {
        // 156: a 132 dp column → the weekday; 244: 216 dp → full; 333: the ring beside, 129 dp → the weekday.
        assertEquals("23h 59m ahead · Fri", ringPick(156f, 1f).line.text)
        assertEquals("23h 59m ahead · resets Fri 12:30 PM", ringPick(244f, 1f).line.text)
        assertEquals("23h 59m ahead · Fri", ringPick(333f, 1f).line.text)
        assertEquals("23h 59m ahead · resets Fri 12:30 PM", ringPick(510f, 1f).line.text)
        for (w in listOf(156f, 244f, 333f, 510f)) assertEquals(13f, ringPick(w, 1f).sp, 0f)
    }

    @Test fun ringTwoByTwo_largerTextDropsEarlier() {
        // 244 dp at 1.3×: the full line (277 dp) and the bare reset (227) no longer fit 216: the weekday alone.
        assertEquals("23h 59m ahead · Fri", ringPick(244f, 1.3f).line.text)
        // At 2.0× only the floor (183 dp) fits 216 dp; at 156 dp it fits nothing and steps to 10 sp.
        assertEquals("23h 59m ahead", ringPick(244f, 2f).line.text)
        assertEquals(13f, ringPick(244f, 2f).sp, 0f)
        val tight = ringPick(156f, 2f)
        assertEquals("23h 59m ahead", tight.line.text)
        assertEquals(PaceLineFit.MIN_SP, tight.sp, 0f)
        // Never a wrapped or reworded line: the floor is the last form at every scale.
        for (fs in scales) for (w in listOf(84f, 156f, 244f, 333f, 510f)) {
            assertTrue(ringPick(w, fs).line.text in texts(worst))
        }
    }

    @Test fun numberFourByOne_formByWidthAndFontScale() {
        // Shipped geometry: the ring is Ø60, so the column is 237 dp at 333 and 148 dp at 244.
        // 333: the bare reset fits beside the stamp (171 dp); 510: the full line.
        assertEquals("23h 59m ahead · Fri 12:30 PM", numberPick(333f, 1f).choice.line.text)
        assertTrue(numberPick(333f, 1f).stamp)
        assertEquals("23h 59m ahead · resets Fri 12:30 PM", numberPick(510f, 1f).choice.line.text)
        // 244: the floor does not fit beside the stamp (82 dp < 85) → the stamp yields (C1-2), the
        // line ending on the weekday takes the 148 dp column.
        val at244 = numberPick(244f, 1f)
        assertFalse(at244.stamp)
        assertEquals("23h 59m ahead · Fri", at244.choice.line.text)
        // 156: a 68 dp column holds not even the floor: no stamp, 10 sp.
        val at156 = numberPick(156f, 1f)
        assertFalse(at156.stamp)
        assertEquals("23h 59m ahead", at156.choice.line.text)
        assertEquals(PaceLineFit.MIN_SP, at156.choice.sp, 0f)
    }

    @Test fun numberFourByOne_largerTextDropsEarlierAndTheStampYieldsFirst() {
        // 333 at 1.3×: 237 − 83 (stamp) = 154 dp beside it: the bare reset (210) does not fit, the
        // weekday alone (141) does. The drop order runs before the stamp yields.
        val a = numberPick(333f, 1.3f)
        assertEquals("23h 59m ahead · Fri", a.choice.line.text)
        assertTrue(a.stamp)
        // 333 at 2.0×: 237 − 124 = 113 dp beside the stamp holds nothing (floor 169): the stamp
        // yields, and the full 237 dp column holds the weekday line (217).
        val b = numberPick(333f, 2f)
        assertFalse(b.stamp)
        assertEquals("23h 59m ahead · Fri", b.choice.line.text)
        assertEquals(12f, b.choice.sp, 0f)
        // 510 at 2.0×: 414 − 124 = 290 dp: the weekday line (217) fits beside the stamp.
        val c = numberPick(510f, 2f)
        assertTrue(c.stamp)
        assertEquals("23h 59m ahead · Fri", c.choice.line.text)
    }

    @Test fun pickWithNoFormsIsNull() {
        assertEquals(null, PaceLineFit.pick(emptyList(), 200f, true, 12f, 1f))
        assertEquals(null, PaceLineFit.choose(emptyList(), 200f, 12f, 1f))
    }
}

/** The faces: the signed Weekly figure, and the pace line wired to the estimator. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "420dpi", sdk = [36])
class PaceLineFaceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun shown(v: View): Boolean {
        var p: View? = v
        while (p != null) {
            if (p.visibility != View.VISIBLE) return false
            p = p.parent as? View
        }
        return true
    }

    private fun render(frame: Frame, state: FaceState): View =
        WidgetFace.render(context, frame.bucket.face, frame, state).apply(context, FrameLayout(context))

    private fun texts(root: View): List<String> = buildList {
        fun walk(v: View) {
            if (!shown(v)) return
            if (v is TextView) v.text?.toString()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
    }

    private fun vsPace(face: Face, s: StateId = StateId.S1) =
        FaceStates.of(forState(face, s).copy(usageVsPace = true))

    @Test fun theWeeklyFigureIsSigned_ringsAreNot() {
        val number = vsPace(Face.NUMBER)
        val cell = number.cells.single()
        // The Fixture's week: 20% used at a 69% mark, so 49 under — green, "+49%".
        assertEquals("+49%", cell.textFigure)
        assertEquals("49%", cell.figure)
        val n = render(Bucket.NUMBER_4X1.frame, number)
        assertEquals("+49%", n.findViewById<TextView>(R.id.num_fig).text.toString())
        // The 5h ring inside it stays unsigned.
        assertFalse(n.findViewById<TextView>(R.id.num_ring_fig).text.toString().let { it.startsWith("+") || it.startsWith("−") })
        // Ring: the Weekly row signed, the ring's bore not.
        val r = render(Bucket.RING_2X2.frame, vsPace(Face.RING))
        assertEquals("+49%", r.findViewById<TextView>(R.id.ring_wk_fig).text.toString())
        assertFalse(r.findViewById<TextView>(R.id.ring_fig).text.toString().let { it.startsWith("+") || it.startsWith("−") })
        // Countdown 2×2 (Weekly): the figure row.
        val cdState = FaceStates.of(forState(Face.COUNTDOWN, StateId.S1).copy(usageVsPace = true, window = FaceWindow.WEEKLY))
        val cd = render(Bucket.COUNTDOWN_2X2.frame, cdState)
        assertEquals("+49%", cd.findViewById<TextView>(R.id.cd_fig).text.toString())
        // Strip 4×2: every ring's Weekly figure is signed.
        val strip = render(Bucket.STRIP_4X2.frame, vsPace(Face.STRIP))
        assertTrue(texts(strip).joinToString(" | "), texts(strip).any { Regex("^[+−]\\d+%$").matches(it) })
        // Used mode: nothing signed.
        val used = render(Bucket.NUMBER_4X1.frame, FaceStates.of(forState(Face.NUMBER, StateId.S1)))
        assertEquals("20%", used.findViewById<TextView>(R.id.num_fig).text.toString())
    }

    @Test fun onlyPlusHundredTakesAFifthPlace() {
        // "+19%" is as wide as "100%" (tabular digits, the small "%"); "+100%" is the one longer form.
        assertEquals(4, PaceText.signed(19).length)
        assertEquals(4, PaceText.signed(-19).length)
        assertEquals(5, PaceText.signed(100).length)
    }

    private fun numberAt(w: Float, state: FaceState = FaceStates.of(forState(Face.NUMBER, StateId.S1))) =
        render(Frame(Bucket.NUMBER_4X1, w, 84f), state)

    @Test fun theNumberSubLineIsThePaceLine_andTheStampYieldsWhenItDoesNotFit() {
        for (fs in listOf(1.0f, 1.3f, 2.0f)) {
            RuntimeEnvironment.setFontScale(fs)
            for (w in listOf(156f, 244f, 333f, 510f)) {
                val state = FaceStates.of(forState(Face.NUMBER, StateId.S1))
                val c = state.cells.single()
                assertTrue(c.paceForms.isNotEmpty())
                val frame = Frame(Bucket.NUMBER_4X1, w, 84f)
                val want = PaceLineFit.pick(c.paceForms, WidgetFace.numberColDp(frame), true, 12f, fs)!!
                val v = render(frame, state)
                val sub = v.findViewById<TextView>(R.id.num_sub)
                assertEquals("fs=$fs w=$w", want.choice.line.text, sub.text.toString())
                assertEquals("fs=$fs w=$w", want.stamp, shown(v.findViewById(R.id.num_stamp)))
                assertEquals("fs=$fs w=$w", want.choice.sp, sub.textSize / (context.resources.displayMetrics.scaledDensity), 0.01f)
            }
        }
        RuntimeEnvironment.setFontScale(1.0f)
    }

    @Test fun theNumberKeepsItsHeldAndStateLines() {
        // Stale, signed-out, not started, no reading: the lines the states already had.
        val s8 = texts(numberAt(363f, FaceStates.of(forState(Face.NUMBER, StateId.S8)))).joinToString(" | ")
        assertTrue(s8, s8.contains("| Stale |"))
        val s7 = texts(numberAt(363f, FaceStates.of(forState(Face.NUMBER, StateId.S7)))).joinToString(" | ")
        assertTrue(s7, s7.contains("Resets Sat 9:10 PM"))
    }

    @Test fun theRingsResetLineIsTheWeeklyPaceLine_atTheFloorWhenNarrow() {
        val state = FaceStates.of(forState(Face.RING, StateId.S1))
        val wk = state.cells.single().weeklyBar!!
        for (fs in listOf(1.0f, 2.0f)) {
            RuntimeEnvironment.setFontScale(fs)
            for (w in listOf(156f, 244f, 333f)) {
                val frame = Frame(Bucket.RING_2X2, w, 237f)
                val l = WidgetFace.ringLayout(frame)
                val lineDp = if (l.horizontal) frame.innerWidthDp - l.main.diameter - l.gapDp else frame.innerWidthDp
                val want = PaceLineFit.choose(wk.paceForms, lineDp, if (frame.roomy) 13f else 12f, fs)!!
                val v = render(frame, state)
                val reset = v.findViewById<TextView>(if (l.horizontal) R.id.ring_reset_h else R.id.ring_reset)
                if (!shown(reset)) continue
                assertEquals("fs=$fs w=$w", want.line.text, reset.text.toString())
            }
        }
        RuntimeEnvironment.setFontScale(1.0f)
        assertNotNull(wk)
    }

    @Test fun aWeeklyOnlyRingStillShowsThePaceLine() {
        // Judge F1: a Ring whose own cell is the Weekly (no 5h companion) takes the pace line too.
        val state = FaceStates.of(forState(Face.RING, StateId.S4)) // B1: ChatGPT, no 5h
        val c = state.cells.single()
        assertEquals(FaceWindow.WEEKLY, c.window)
        val frame = Frame(Bucket.RING_2X2, 244f, 237f)
        val l = WidgetFace.ringLayout(frame)
        val v = render(frame, state)
        val reset = v.findViewById<TextView>(if (l.horizontal) R.id.ring_reset_h else R.id.ring_reset)
        assertTrue(reset.text.toString(), shown(reset))
        assertFalse(reset.text.toString(), reset.text.toString().startsWith("Resets "))
        assertTrue(reset.text.toString(), Regex("(behind|ahead|On the mark)").containsMatchIn(reset.text))
    }

    @Test fun talkBackReadsThePaceLine() {
        // Judge F3: the description carries the drawn pace line, not the old "Resets …" sub.
        for (face in listOf(Face.NUMBER, Face.RING)) {
            val state = FaceStates.of(forState(face, StateId.S1))
            val said = WidgetFace.describe(state)
            assertTrue(said, Regex("(behind|ahead|On the mark)").containsMatchIn(said))
        }
    }
}
