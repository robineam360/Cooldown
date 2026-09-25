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

    private fun problems(bucket: Bucket, s: StateId, label: String, state: FaceState): List<String> {
        val d = context.resources.displayMetrics.density
        val host = FrameLayout(context)
        val root = WidgetFace.render(context, bucket.face, bucket, state).apply(context, host)
        host.addView(root)
        val w = (bucket.widthDp * d).toInt()
        val h = (bucket.heightDp * d).toInt()
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
                if (got + 1 < wants) out += "$bucket $label ${s.name} $name '${v.text}' ${got / d}dp < ${wants / d}dp"
                if (y + got > h + 1) out += "$bucket $label ${s.name} $name '${v.text}' ends ${(y + got) / d}dp > ${bucket.heightDp}dp"
            }
            // A shape background (chip, pill, tag) that collapsed to a sliver: FrameLayout
            // only stretches match_parent children of a wrap_content frame when it has two.
            if (v is android.widget.ImageView && v.drawable != null && (v.width < 2 * d || v.height < 2 * d)) {
                val name = runCatching { context.resources.getResourceEntryName(v.id) }.getOrDefault("?")
                out += "$bucket $label ${s.name} $name collapsed to ${v.width / d}×${v.height / d}dp"
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), y)
        }
        walk(root, 0)
        return out
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
}
