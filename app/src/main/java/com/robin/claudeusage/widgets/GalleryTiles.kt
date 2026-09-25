package com.robin.claudeusage.widgets

/**
 * CCRM-84 (Faces Gallery): the pure half — every tile the gallery screen shows, built
 * without a `Context` so the coverage (every face × its buckets × every [StateId]) and
 * each caption's `bitmapBytes` estimate are unit-testable without Robolectric.
 * [FacesGalleryActivity] does the Android half: inflating [input] through
 * `FaceStates.of` and `WidgetFace.render`.
 */
data class GalleryTile(
    val face: Face,
    val bucket: Bucket,
    val stateId: StateId,
    val caption: String,
    val input: FaceInput,
)

object GalleryTiles {

    fun faceLabel(face: Face): String = when (face) {
        Face.RING -> "Ring"
        Face.NUMBER -> "Number"
        Face.COUNTDOWN -> "Countdown"
        Face.STRIP -> "Accounts Strip"
    }

    /** [WidgetFace.bitmapBytes] for one bucket alone, in KB, rounded to the nearest whole KB. */
    fun kb(face: Face, bucket: Bucket, density: Float): Long =
        Math.round(WidgetFace.bitmapBytes(face, listOf(bucket), density) / 1024.0)

    fun caption(face: Face, bucket: Bucket, stateId: StateId, density: Float): String =
        "${faceLabel(face)} ${bucket.label} · ${stateId.name} (${stateId.label}) · ${kb(face, bucket, density)} KB"

    /** Every tile for [faces] (default: all four), at [dark] and [density]. */
    fun build(dark: Boolean, density: Float, faces: List<Face> = Face.entries): List<GalleryTile> =
        faces.flatMap { face ->
            Bucket.of(face).flatMap { bucket ->
                StateId.entries.map { stateId ->
                    GalleryTile(
                        face = face,
                        bucket = bucket,
                        stateId = stateId,
                        caption = caption(face, bucket, stateId, density),
                        input = GalleryFixtures.forState(face, stateId, dark),
                    )
                }
            }
        }
}
