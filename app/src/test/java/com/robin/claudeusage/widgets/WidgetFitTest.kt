package com.robin.claudeusage.widgets

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every face, state and bucket, laid out at exactly its cover size (Fold 7, 420 dpi) with
 * native font metrics: no visible text may be squeezed below the height it asks for, or
 * pushed past the face's edge. [WidgetFaceTest] checks what a face *says*; this checks it
 * *fits* — found at RUNBOOK.md Step 4, where the Number 4×1's sub-line was cut to 9 dp on
 * the emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "420dpi")
class WidgetFitTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun shown(v: View): Boolean {
        var p: View? = v
        while (p != null) {
            if (p.visibility != View.VISIBLE) return false
            p = p.parent as? View
        }
        return true
    }

    private fun problems(bucket: Bucket, s: StateId, label: String, state: FaceState): List<String> =
        problems(bucket.frame, s, label, state)

    private fun problems(frame: Frame, s: StateId, label: String, state: FaceState): List<String> {
        val d = context.resources.displayMetrics.density
        val bucket = "${frame.bucket}@${frame.widthDp.toInt()}x${frame.heightDp.toInt()}"
        val host = FrameLayout(context)
        val root = WidgetFace.render(context, frame.bucket.face, frame, state).apply(context, host)
        host.addView(root)
        val w = (frame.widthDp * d).toInt()
        val h = (frame.heightDp * d).toInt()
        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, w, h)
        val out = mutableListOf<String>()
        fun walk(v: View, top: Int) {
            if (!shown(v)) return
            val y = top + v.top
            if (v is TextView && !v.text.isNullOrEmpty()) {
                val name = runCatching { context.resources.getResourceEntryName(v.id) }.getOrDefault("?")
                v.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val wants = v.measuredHeight
                val got = v.height
                // Text pushed past the right edge, not just below the bottom (rev F's narrow frames).
                val x = left(v, root)
                if (v.maxLines == 1 && x + v.width > w + 1) out += "$bucket $label ${s.name} $name '${v.text}' ends ${(x + v.width) / d}dp > ${frame.widthDp}dp"
                if (got + 1 < wants) out += "$bucket $label ${s.name} $name '${v.text}' ${got / d}dp < ${wants / d}dp"
                if (y + got > h + 1) out += "$bucket $label ${s.name} $name '${v.text}' ends ${(y + got) / d}dp > ${frame.heightDp}dp"
            }
            // A shape background (chip, pill, tag) that collapsed to a sliver: FrameLayout
            // only stretches match_parent children of a wrap_content frame when it has two.
            if (v is android.widget.ImageView && v.drawable != null && (v.width < 2 * d || v.height < 2 * d)) {
                val name = runCatching { context.resources.getResourceEntryName(v.id) }.getOrDefault("?")
                out += "$bucket $label ${s.name} $name collapsed to ${v.width / d}×${v.height / d}dp"
            }
            // Rev H: a ring or bar bitmap pushed past the frame's edge, or squeezed below its size.
            if (v is android.widget.ImageView && v.drawable is android.graphics.drawable.BitmapDrawable) {
                val name = runCatching { context.resources.getResourceEntryName(v.id) }.getOrDefault("?")
                val x = left(v, root)
                if (x + v.width > w + 1 || y + v.height > h + 1) {
                    out += "$bucket $label ${s.name} $name bitmap ends ${(x + v.width) / d}×${(y + v.height) / d}dp past the frame"
                }
                val lp = v.layoutParams
                if (lp != null && lp.height > 0 && v.height + 1 < lp.height) {
                    out += "$bucket $label ${s.name} $name bitmap ${v.height / d}dp < ${lp.height / d}dp"
                }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), y)
        }
        walk(root, 0)
        return out
    }

    private fun left(v: View, root: View): Int {
        var x = 0
        var p: View? = v
        while (p != null && p !== root) { x += p.left; p = p.parent as? View }
        return x
    }

    @Test
    fun everyFaceStateAndBucketFitsItsCoverCell() {
        val all = mutableListOf<String>()
        for (bucket in Bucket.entries) for (s in StateId.entries) for (dark in listOf(true, false)) {
            val state = FaceStates.of(WidgetFixtures.forState(bucket.face, s, dark))
            all += problems(bucket, s, if (dark) "dark" else "light", state)
        }
        assertEquals(all.distinct().joinToString("\n"), 0, all.size)
    }

    /**
     * Rev F (CCBG-38 (Cover Buckets)): the frames One UI reported on the Fold 7 at the
     * Step 7 device pass — cover and inner, Robin's grid — each drawn at exactly that size
     * with the layout the class rule picks.
     */
    @Test
    fun everyFaceStateFitsTheFramesOneUiReports() {
        val cover = listOf(84f to 108f, 156f to 108f, 156f to 237f, 244f to 108f, 244f to 237f,
            244f to 366f, 333f to 108f, 333f to 237f, 510f to 108f, 510f to 237f)
        val inner = listOf(202f to 114f, 202f to 264f, 470f to 114f, 470f to 264f, 470f to 414f)
        // Other launchers' grids (rev F.1): a Pixel-style 3-column 84 dp row, a five-column
        // launcher's narrow 2×1, and every no-size-yet key frame.
        val other = listOf(244f to 84f, 272f to 84f, 140f to 84f, 84f to 84f, 140f to 140f,
            240f to 84f, 240f to 150f, 140f to 150f, 244f to 150f)
        // Rev H (CCBG-44 (Widget Fill)): the tiers' edges — the 4×3 and 6×3, the plan's inner
        // frames, Pixel-style 2×2s between 180 and 240 dp, wide 84–90 dp rows (the Countdown's
        // side column), and frames where the synthetic ribbon moves a tier.
        val revH = listOf(333f to 366f, 510f to 366f, 211f to 126f, 211f to 291f, 467f to 126f,
            467f to 291f, 180f to 180f, 200f to 190f, 180f to 200f, 220f to 185f, 239f to 180f,
            239f to 230f, 318f to 84f, 320f to 84f, 363f to 84f, 400f to 84f, 330f to 90f,
            300f to 230f, 250f to 210f, 156f to 205f, 156f to 229f, 110f to 84f)
        val all = mutableListOf<String>()
        for (face in Face.entries) for ((w, h) in cover + inner + other + revH) {
            // The info files' minResizeWidth: the Strip is never under 250 dp wide, the
            // Number and the Countdown never under 110 dp.
            if (face == Face.STRIP && w < 244f) continue
            if ((face == Face.NUMBER || face == Face.COUNTDOWN) && w < 110f) continue
            val frame = Frame.at(face, w, h)
            for (s in StateId.entries) for (dark in listOf(true, false)) {
                val state = FaceStates.of(WidgetFixtures.forState(face, s, dark))
                all += problems(frame, s, if (dark) "dark" else "light", state)
            }
        }
        assertEquals(all.distinct().joinToString("\n"), 0, all.size)
    }
}
