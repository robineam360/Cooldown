package com.robin.claudeusage.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CCRM-83 (Ring Renderer): the status-bar glyph must render **byte-identically** after its
 * drawing moved into [RingRenderer]. Every state the icon draws is rendered twice — through
 * today's [UsageIcon.draw] (now a 24 dp NEEDLE call) and through [LegacyUsageIcon], the
 * frozen pre-extraction copy — at the Fold 7's density and two others, both themes, and the
 * pixels are compared.
 */
@RunWith(RobolectricTestRunner::class)
class RingRendererTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private data class IconState(
        val name: String,
        val pct: Double?,
        val elapsed: Double?,
        val fill: Int? = ACCENT,
        val showOverPace: Boolean = true,
    )

    private val states = listOf(
        IconState("no reading", null, null),
        IconState("no reading, clock known", null, 40.0),
        IconState("no usage", 0.0, 40.0),
        IconState("1% (min sweep)", 1.0, 40.0),
        IconState("under pace", 20.0, 50.0),
        IconState("on the dead zone", 53.0, 50.0),
        IconState("above pace", 62.0, 46.0),
        IconState("above pace, red off", 62.0, 46.0, showOverPace = false),
        IconState("no clock", 62.0, null),
        IconState("80+ ladder", 85.0, 60.0, fill = 0xFFFDD663.toInt()),
        IconState("99.9 truncates below 100", 99.9, 70.0, fill = 0xFFFFA726.toInt()),
        IconState("100 spent cross", 100.0, 70.0, fill = 0xFFFF5252.toInt()),
        IconState("over 100", 130.0, 100.0, fill = 0xFFFF5252.toInt()),
        IconState("elapsed at 0", 5.0, 0.0),
        IconState("elapsed at 100", 90.0, 100.0),
        IconState("null fill → white", 38.0, 46.0, fill = null),
    )

    private fun assertSame(label: String) {
        for (dark in listOf(true, false)) for (s in states) {
            val a = UsageIcon.draw(context, s.pct, false, s.elapsed, s.fill, dark, s.showOverPace)
            val b = LegacyUsageIcon.draw(context, s.pct, false, s.elapsed, s.fill, dark, s.showOverPace)
            assertEquals("$label ${s.name} dark=$dark: size", b.width, a.width)
            assertTrue("$label ${s.name} dark=$dark: pixels differ", pixels(a).contentEquals(pixels(b)))
        }
    }

    @Test
    @Config(qualifiers = "xxhdpi")
    fun iconIsByteIdentical_xxhdpi() = assertSame("xxhdpi")

    @Test
    @Config(qualifiers = "420dpi")
    fun iconIsByteIdentical_fold7() = assertSame("420dpi")

    @Test
    @Config(qualifiers = "mdpi")
    fun iconIsByteIdentical_mdpi() = assertSame("mdpi")

    @Test
    @Config(qualifiers = "420dpi")
    fun theStatesAreNotAllTheSamePicture() {
        // Guards the test itself: a renderer that drew nothing would pass the equality.
        val empty = UsageIcon.draw(context, null, false, null, ACCENT, true)
        val spent = UsageIcon.draw(context, 100.0, false, 70.0, ACCENT, true)
        val above = UsageIcon.draw(context, 62.0, false, 46.0, ACCENT, true)
        assertFalse(pixels(empty).contentEquals(pixels(spent)))
        assertFalse(pixels(spent).contentEquals(pixels(above)))
    }

    @Test
    @Config(qualifiers = "420dpi")
    fun honestyGates_holdForTheTickRingToo() {
        // no reading, no usage and no clock all draw the extent alone
        fun tick(pct: Double?, elapsed: Double?) = RingRenderer.draw(
            context, 168, 16f, pct, elapsed, ACCENT, true, true, spentCross = true,
            mark = RingRenderer.PaceMark.TICK, haloArgb = 0xFF1A1A1A.toInt(),
        )
        val bare = pixels(tick(null, null))
        assertTrue(bare.contentEquals(pixels(tick(null, 40.0))))
        assertTrue(bare.contentEquals(pixels(tick(0.0, 40.0))))
        // a reading with no clock draws the band but no tick; with a clock, the tick
        assertFalse(bare.contentEquals(pixels(tick(38.0, null))))
        assertFalse(pixels(tick(38.0, null)).contentEquals(pixels(tick(38.0, 46.0))))
        // the × only when asked
        val noX = RingRenderer.draw(
            context, 168, 16f, 100.0, 70.0, ACCENT, true, true, spentCross = false,
            mark = RingRenderer.PaceMark.TICK,
        )
        assertFalse(pixels(noX).contentEquals(pixels(tick(100.0, 70.0))))
    }

    @Test
    @Config(qualifiers = "420dpi")
    fun tickRing_staysInsideItsBox() {
        // The tick's overhang, round caps and halo must never clip: the outermost ring of
        // pixels stays empty at the tick's worst position (3 o'clock, the box's edge).
        val bmp = RingRenderer.draw(
            context, 168, 16f, 60.0, 25.0, ACCENT, true, true, spentCross = true,
            mark = RingRenderer.PaceMark.TICK, haloArgb = 0xFF1A1A1A.toInt(),
        )
        for (i in 0 until bmp.width) {
            assertEquals(0, bmp.getPixel(i, 0) ushr 24)
            assertEquals(0, bmp.getPixel(bmp.width - 1, i) ushr 24)
        }
    }

    private fun pixels(b: Bitmap): IntArray =
        IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    private companion object {
        val ACCENT = 0xFFE59980.toInt()
    }
}
