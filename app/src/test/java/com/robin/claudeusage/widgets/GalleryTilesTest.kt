package com.robin.claudeusage.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CCRM-84 (Faces Gallery): [GalleryTiles.build] must cover every face's own buckets ×
 * every [StateId] exactly once, and each caption's KB figure must be
 * [WidgetFace.bitmapBytes] for that one bucket, in KB, rounded to the nearest whole KB
 * ([Math.round] — round-half-up).
 *
 * Robolectric only because [WidgetFace.bitmapBytes] shares geometry helpers
 * ([com.robin.claudeusage.ui.BarRenderer]) with the render path the other widget tests
 * already run under it (`WidgetFaceTest`, `WidgetFitTest`); [GalleryTiles.build] itself
 * takes no `Context`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "420dpi")
class GalleryTilesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val density = context.resources.displayMetrics.density

    @Test
    fun everyFaceBucketAndStateIsCoveredExactlyOnce() {
        val expected = Face.entries.flatMap { face ->
            Bucket.of(face).flatMap { bucket -> StateId.entries.map { Triple(face, bucket, it) } }
        }
        val actual = GalleryTiles.build(dark = true, density = density)
            .map { Triple(it.face, it.bucket, it.stateId) }
        assertEquals(expected.toSet(), actual.toSet())
        assertEquals("no duplicate tiles", expected.size, actual.size)
    }

    @Test
    fun faceFilterKeepsOnlyThatFacesOwnBuckets() {
        for (face in Face.entries) {
            val tiles = GalleryTiles.build(dark = false, density = density, faces = listOf(face))
            assertTrue(tiles.all { it.face == face })
            assertEquals(Bucket.of(face).size * StateId.entries.size, tiles.size)
        }
    }

    @Test
    fun captionKbIsBitmapBytesForThatBucketAloneRoundedToNearestKb() {
        for (tile in GalleryTiles.build(dark = true, density = density)) {
            val expectedKb = Math.round(WidgetFace.bitmapBytes(tile.face, listOf(tile.bucket), density) / 1024.0)
            assertTrue(
                "'${tile.caption}' should end with '· $expectedKb KB'",
                tile.caption.endsWith("· $expectedKb KB"),
            )
        }
    }

    @Test
    fun captionNamesTheFaceBucketAndState() {
        val tile = GalleryTiles.build(dark = true, density = density, faces = listOf(Face.RING))
            .first { it.bucket == Bucket.RING_2X2 && it.stateId == StateId.S3 }
        assertTrue(tile.caption.startsWith("Ring 2×2 · S3 (100%) ·"))
    }
}
