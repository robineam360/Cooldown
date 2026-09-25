package com.robin.claudeusage.widgets

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CCBG-36 (Widget Reapply Residue): the launcher reapplies an update onto the old view
 * tree when the layout id is unchanged, so a view one state turned VISIBLE must not
 * survive into a state that never mentions it. [WidgetHost.single] re-adds the face into
 * a fresh frame on every update; this reapplies S13 (synthetic) → S1 the way the launcher
 * does and checks the marker is gone.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetReapplyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun visibility(root: View, id: Int): Int =
        root.findViewById<View>(id)?.visibility ?: View.GONE

    @Test
    fun theSyntheticMarkerDoesNotSurviveAReapply() {
        for (bucket in Bucket.entries) {
            val marker = if (bucket.tall) R.id.w_ribbon else R.id.w_synth_dot
            val on = FaceStates.of(WidgetFixtures.forState(bucket.face, StateId.S13))
            val off = FaceStates.of(WidgetFixtures.forState(bucket.face, StateId.S1))
            val host = FrameLayout(context)
            val root = WidgetHost.single(context, 1, bucket, on).apply(context, host)
            assertEquals("${bucket.name} marker on", View.VISIBLE, visibility(root, marker))
            WidgetHost.single(context, 1, bucket, off).reapply(context, root)
            assertEquals("${bucket.name} marker after reapply", View.GONE, visibility(root, marker))
        }
    }
}
