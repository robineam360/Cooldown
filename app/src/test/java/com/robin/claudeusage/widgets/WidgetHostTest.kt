package com.robin.claudeusage.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The size map's keys and the bucket picker (R10's fallback draws what the map would),
 * the permanent R9 names, and the per-widget PendingIntent identity (CCRM-78 §On-face
 * controls).
 */
@RunWith(RobolectricTestRunner::class)
class WidgetHostTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun r9_providerClassNamesArePermanent() {
        assertEquals(
            mapOf(
                Face.RING to "com.robin.claudeusage.widgets.RingWidgetProvider",
                Face.NUMBER to "com.robin.claudeusage.widgets.NumberWidgetProvider",
                Face.COUNTDOWN to "com.robin.claudeusage.widgets.CountdownWidgetProvider",
                Face.STRIP to "com.robin.claudeusage.widgets.StripWidgetProvider",
            ),
            WidgetHost.PROVIDERS,
        )
    }

    @Test
    fun everyFaceHasAtMostThreeBuckets() {
        for (face in Face.entries) assertTrue(face.name, Bucket.of(face).size in 1..3)
    }

    @Test
    fun pick_coverCellsGetTheirOwnBucket() {
        // Exactly at cover size, and a few dp under it (the launcher's rounding).
        for (b in Bucket.entries) {
            assertEquals(b, WidgetHost.pick(b.face, b.widthDp, b.heightDp))
            assertEquals(b, WidgetHost.pick(b.face, b.widthDp - 4f, b.heightDp - 4f))
        }
    }

    @Test
    fun pick_inBetweenAndTooSmall() {
        // Rev F.1: a 3-column frame (240 dp or more) has the width for the 4×1 face and its
        // sub-line; under that it is the 2×1 face.
        assertEquals(Bucket.NUMBER_4X1, WidgetHost.pick(Face.NUMBER, 272f, 84f))
        assertEquals(Bucket.NUMBER_2X1, WidgetHost.pick(Face.NUMBER, 236f, 84f))
        // The inner grid's taller cells: a 4×1 there (~472×100) is still the 4×1 face.
        assertEquals(Bucket.NUMBER_4X1, WidgetHost.pick(Face.NUMBER, 472f, 100f))
        assertEquals(Bucket.COUNTDOWN_2X1, WidgetHost.pick(Face.COUNTDOWN, 236f, 100f))
        assertEquals(Bucket.RING_2X2, WidgetHost.pick(Face.RING, 236f, 200f))
        // Smaller than every key: the smallest face, as RemoteViews(Map) itself does.
        assertEquals(Bucket.RING_1X1, WidgetHost.pick(Face.RING, 40f, 40f))
        assertEquals(Bucket.STRIP_4X1, WidgetHost.pick(Face.STRIP, 200f, 60f))
    }

    /**
     * CCBG-38 (Cover Buckets): the frames One UI reported on the Fold 7 cover pick the
     * layout the device pass expected — rev D's keys sent all of them to the smallest.
     */
    @Test
    fun pick_oneUiCoverFrames() {
        assertEquals(Bucket.RING_2X2, WidgetHost.pick(Face.RING, 155.8f, 237f))
        assertEquals(Bucket.RING_1X1, WidgetHost.pick(Face.RING, 84.2f, 107.8f))
        assertEquals(Bucket.RING_1X1, WidgetHost.pick(Face.RING, 155.8f, 107.8f))
        assertEquals(Bucket.NUMBER_2X1, WidgetHost.pick(Face.NUMBER, 155.8f, 107.8f))
        assertEquals(Bucket.NUMBER_4X1, WidgetHost.pick(Face.NUMBER, 333f, 107.8f))
        assertEquals(Bucket.NUMBER_4X2, WidgetHost.pick(Face.NUMBER, 333f, 237f))
        assertEquals(Bucket.COUNTDOWN_2X1, WidgetHost.pick(Face.COUNTDOWN, 155.8f, 107.8f))
        assertEquals(Bucket.COUNTDOWN_2X2, WidgetHost.pick(Face.COUNTDOWN, 155.8f, 237f))
        assertEquals(Bucket.STRIP_4X1, WidgetHost.pick(Face.STRIP, 333f, 107.8f))
        assertEquals(Bucket.STRIP_4X2, WidgetHost.pick(Face.STRIP, 333f, 237f))
    }

    /** The no-sizes-yet keys agree with the class rule: each key picks its own bucket. */
    @Test
    fun key_isTheSmallestFrameOfItsClass() {
        for (b in Bucket.entries) {
            val k = WidgetHost.key(b)
            assertEquals(b, WidgetHost.pick(b.face, k.width, k.height))
        }
        for (face in Face.entries) {
            val keys = Bucket.of(face).map { WidgetHost.key(it) }
            assertEquals(face.name, keys.size, keys.distinct().size)
        }
    }

    @Test
    fun reportedSizes_smallestFirstDistinctAndCapped() {
        assertTrue(WidgetHost.reportedSizes(null).isEmpty())
        val options = android.os.Bundle().apply {
            putParcelableArrayList(
                android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(android.util.SizeF(155.8f, 237f), android.util.SizeF(202.3f, 264.4f),
                    android.util.SizeF(155.8f, 237f), android.util.SizeF(0f, 10f)),
            )
        }
        assertEquals(
            listOf(android.util.SizeF(155.8f, 237f), android.util.SizeF(202.3f, 264.4f)),
            WidgetHost.reportedSizes(options),
        )
        // Over budget, the largest go: the smallest frame is always kept.
        val huge = List(4) { android.util.SizeF(470f + it, 414f) }
        val kept = WidgetHost.withinBudget(Face.RING, listOf(android.util.SizeF(155.8f, 237f)) + huge, 3.5f)
        assertEquals(155.8f, kept.first().widthDp)
        assertTrue(kept.size < 5)
    }

    @Test
    fun currentSize_nullWithoutOptions() {
        assertNull(WidgetHost.currentSize(null))
        assertNull(WidgetHost.currentSize(android.os.Bundle()))
    }

    @Test
    fun onFaceIntents_areUniquePerWidgetAndAction() {
        val ids = listOf(3, 4, 11)
        val identities = ids.flatMap { id ->
            WidgetActionReceiver.Action.entries.map { a ->
                WidgetHost.uri(id, a.path).toString() to WidgetHost.requestCode(id, a.code)
            }
        }
        assertEquals(identities.size, identities.map { it.first }.toSet().size)
        assertEquals(identities.size, identities.map { it.second }.toSet().size)
        // And the PendingIntents themselves differ between two widgets' cyclers.
        assertNotEquals(
            WidgetActionReceiver.intent(context, 3, WidgetActionReceiver.Action.CYCLE),
            WidgetActionReceiver.intent(context, 4, WidgetActionReceiver.Action.CYCLE),
        )
        assertNotEquals(
            WidgetActionReceiver.intent(context, 3, WidgetActionReceiver.Action.WINDOW_5H),
            WidgetActionReceiver.intent(context, 3, WidgetActionReceiver.Action.WINDOW_WEEKLY),
        )
    }
}
