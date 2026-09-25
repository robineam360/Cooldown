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
        // Number 3×1 is wider than 2×1 but not a 4×1: the 2×1 face.
        assertEquals(Bucket.NUMBER_2X1, WidgetHost.pick(Face.NUMBER, 272f, 84f))
        // The inner grid's taller cells: a 4×1 there (~472×100) is still the 4×1 face.
        assertEquals(Bucket.NUMBER_4X1, WidgetHost.pick(Face.NUMBER, 472f, 100f))
        assertEquals(Bucket.COUNTDOWN_2X1, WidgetHost.pick(Face.COUNTDOWN, 236f, 100f))
        assertEquals(Bucket.RING_2X2, WidgetHost.pick(Face.RING, 236f, 200f))
        // Smaller than every key: the smallest face, as RemoteViews(Map) itself does.
        assertEquals(Bucket.RING_1X1, WidgetHost.pick(Face.RING, 40f, 40f))
        assertEquals(Bucket.STRIP_4X1, WidgetHost.pick(Face.STRIP, 200f, 60f))
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
