package com.robin.claudeusage.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.data.MapLayers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * CCBG-105 (Corner Label Collision): the reported Summary (Fold 7 inner screen, Figures on) built by the screen's own
 * scene. A dot late in its week no longer sits on "behind pace"; the word yields along its line. With SHOT_DIR set,
 * the map is also drawn to `ccbg105-<name>.png` there (the before/after captures).
 */
@RunWith(RobolectricTestRunner::class)
class PaceMapCornerWordsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val d = 2.625f
    private val ink = Color(0xFFE6E1E5)
    private val card = Color(0xFF2B2930)

    private fun entry(name: String, gone: Double, used: Double, color: Long) =
        PaceMapEntry(name, name, Color(color), gone, used, false, emptyList())

    /** The accounts as captured on 2026-11-18 (`release/play/screenshots-foldable/fold-01-inner-summary.png`). */
    private val reported = listOf(
        entry("ChatGPT", 28.6, 21.0, 0xFF19C39C),
        entry("Work", 39.0, 17.0, 0xFFE8957A),
        entry("Claude", 86.0, 9.0, 0xFFE8957A),
        entry("Product", 98.0, 58.0, 0xFFE8957A),
    )

    private fun scene(entries: List<PaceMapEntry>, wDp: Float, hDp: Float): PaceMapScene {
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(d, 1f), LayoutDirection.Ltr)
        return PaceMapScene.build(measurer, d, wDp * d, hDp * d, entries, MapLayers(figures = true, trails = false, projection = false),
            PaceText.Mode.USED, null, ink, true)
    }

    private fun save(name: String, s: PaceMapScene) {
        val dir = System.getenv("SHOT_DIR") ?: return
        val image = ImageBitmap(s.width.toInt(), s.height.toInt())
        CanvasDrawScope().draw(Density(d, 1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(s.width, s.height)) {
            drawRect(card)
            drawPaceMap(s, MapLayers(figures = true, trails = false, projection = false), PaceText.Mode.USED, null, null, ink, card, true)
        }
        File(dir).mkdirs()
        File(dir, "ccbg105-$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun word(s: PaceMapScene, text: String) = s.words.firstOrNull { it.layout.layoutInput.text.text == text }

    private fun overlapsADot(s: PaceMapScene, w: PaceMapScene.Word): Boolean {
        val h = MapLabels.Geometry.DP.dotHalf.toFloat() * d
        return s.items.any { i ->
            w.at.x < i.point.x + h && i.point.x - h < w.at.x + w.layout.size.width &&
                w.at.y < i.point.y + h && i.point.y - h < w.at.y + w.layout.size.height
        }
    }

    @Test fun `CCBG-105 - the reported map, inner screen portrait and landscape - no dot on a wedge word`() {
        for ((name, size) in listOf("inner-portrait" to (311f to 311f), "inner-landscape" to (420f to 300f), "cover" to (320f to 230f))) {
            val s = scene(reported, size.first, size.second)
            save(name, s)
            val behind = word(s, PaceText.MAP_BEHIND)
            val ahead = word(s, PaceText.MAP_AHEAD)
            assertNotNull("$name: behind pace is drawn", behind)
            assertNotNull("$name: ahead of pace is drawn", ahead)
            assertFalse("$name: a dot sits on behind pace", overlapsADot(s, behind!!))
            assertFalse("$name: a dot sits on ahead of pace", overlapsADot(s, ahead!!))
        }
    }

    @Test fun `CCBG-105 - with no dot on it the word stays in its corner`() {
        val s = scene(reported.filter { it.key != "Claude" }, 311f, 311f)
        val behind = word(s, PaceText.MAP_BEHIND)!!
        val corner = s.frame.x(97.0) - behind.layout.size.width
        assertTrue("stays at ${behind.at.x} vs $corner", kotlin.math.abs(behind.at.x - corner) < 0.5f)
    }
}
