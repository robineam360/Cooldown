package com.robin.claudeusage.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.data.HistoryPoint
import com.robin.claudeusage.widgets.GalleryFixtures
import com.robin.claudeusage.widgets.GalleryFixtures.MIN
import com.robin.claudeusage.widgets.GalleryFixtures.NOW_MS
import com.robin.claudeusage.widgets.GalleryFixtures.RESET_5H
import com.robin.claudeusage.widgets.GalleryFixtures.RESET_WEEKLY
import com.robin.claudeusage.widgets.GalleryFixtures.ZONE
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CCRM-24 (Share Card): what the card says, that it renders at 4× for every reading it
 * can be asked to show, and that the file never leaves `cacheDir/share/`.
 *
 * Set `SHARE_CARD_DUMP=<dir>` in the environment to write each rendered card as a PNG for a visual check.
 */
@RunWith(RobolectricTestRunner::class)
class ShareCardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun history(n: Int = 8, from: Double = 10.0, to: Double = 62.0): List<HistoryPoint> {
        val start = RESET_5H.toEpochMilli() - 5 * 60 * MIN
        val span = NOW_MS - 3 * MIN - start
        return (0 until n).map { i ->
            HistoryPoint(
                at = start + span * i / (n - 1),
                sessionPct = from + (to - from) * i / (n - 1),
                sessionResetAt = RESET_5H.toEpochMilli(),
                weeklyPct = 20.0 + i,
                weeklyResetAt = RESET_WEEKLY.toEpochMilli(),
            )
        }
    }

    private fun model(
        account: com.robin.claudeusage.widgets.AccountInput = GalleryFixtures.pro(GalleryFixtures.both(62.0, 22.0)),
        history: List<HistoryPoint> = history(),
        dark: Boolean = true,
        left: Boolean = false,
        synthetic: Boolean = false,
    ) = ShareCard.model(account, history, dark, left, showOverPace = true, synthetic = synthetic, nowMs = NOW_MS, zone = ZONE)

    @Test
    fun `the card carries the label, the snapshot's own stamp and both windows`() {
        val m = model()
        assertEquals("Pro", m.label)
        // Fetched 3 minutes before the fixture's 6:29 PM, Thu Jan 1.
        assertEquals("as of 6:26 PM, Thu Jan 1", m.asOf)
        assertEquals(listOf("5h", "Weekly"), m.rows.map { it.name })
        assertEquals("62%", m.rows[0].figure)
        assertEquals("Resets 9:10 PM", m.rows[0].reset)
        assertEquals("Resets Sat 9:10 PM", m.rows[1].reset)
        assertTrue(m.rows[0].pace!!.endsWith("above even pace"))
        assertEquals("5h trend", m.trend!!.title)
        assertEquals(8, m.trend!!.samples.size)
    }

    @Test
    fun `a weekly-only account shows one row and charts its weekly window`() {
        val m = model(GalleryFixtures.CHATGPT, history = emptyList())
        assertEquals(listOf("Weekly"), m.rows.map { it.name })
        assertEquals("Weekly trend", m.trend!!.title)
    }

    @Test
    fun `left mode flips the figures and says so, pace still reads usage`() {
        val m = model(left = true)
        assertEquals("5h left", m.rows[0].name)
        assertEquals("38%", m.rows[0].figure)
        assertTrue(m.rows[0].pace!!.endsWith("above even pace"))
    }

    @Test
    fun `never fetched has no stamp, and a not-started window has no trend`() {
        val m = model(GalleryFixtures.account("pro", "Pro", GalleryFixtures.both(null, sessionReset = null), fetchedAt = 0))
        assertNull(m.asOf)
        assertEquals("—", m.headline.figure)
        assertNull(m.trend)
    }

    @Test
    fun `renders 1440 px wide at the height the layout computes, for every state`() {
        val cases = mapOf(
            "under-pace-dark" to model(GalleryFixtures.pro(GalleryFixtures.both(30.0, 22.0)), history(to = 30.0)),
            "above-pace-dark" to model(),
            "above-pace-light" to model(dark = false),
            "full-dark" to model(GalleryFixtures.pro(GalleryFixtures.both(100.0, 64.0)), history(to = 100.0)),
            "weekly-only-dark" to model(GalleryFixtures.CHATGPT, history = emptyList()),
            "no-history-light" to model(history = emptyList(), dark = false),
            "not-started-dark" to model(GalleryFixtures.account("pro", "Pro", GalleryFixtures.both(null, sessionReset = null))),
            "synthetic-dark" to model(synthetic = true),
            "long-label-dark" to model(GalleryFixtures.account("pro", "A very long account label that has to truncate somewhere", GalleryFixtures.both(62.0))),
        )
        val dump = System.getenv("SHARE_CARD_DUMP")?.let { File(it).apply { mkdirs() } }
        for ((name, m) in cases) {
            val bmp = ShareCard.render(context, m)
            assertEquals(name, 1440, bmp.width)
            assertEquals(name, ShareCard.heightPx(m), bmp.height)
            // Full-bleed: every corner is the card colour, never transparent.
            val card = if (m.dark) ShareCard.CARD_DARK else ShareCard.CARD_LIGHT
            assertEquals(name, card, bmp.getPixel(0, bmp.height - 1))
            dump?.let { d -> File(d, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        }
        assertTrue(ShareCard.heightPx(model(synthetic = true)) > ShareCard.heightPx(model()))
    }

    @Test
    fun `the file lives in cacheDir share only, one at a time, and the chooser grants read`() {
        val bmp = ShareCard.render(context, model())
        val first = ShareCard.Files.write(context, bmp)
        val second = ShareCard.Files.write(context, bmp)
        val dir = File(context.cacheDir, "share")
        assertEquals(1, dir.listFiles()!!.size)
        assertEquals("content", second.scheme)
        assertEquals("${context.packageName}.share", second.authority)
        assertFalse(first == second && dir.listFiles()!!.size > 1)

        val chooser = ShareCard.Files.chooser(second)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(send)
        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals("image/png", send.type)
        @Suppress("DEPRECATION")
        assertEquals(second, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

        ShareCard.Files.clear(context)
        assertEquals(0, dir.listFiles()!!.size)
    }
}
