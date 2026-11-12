package com.robin.claudeusage.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.data.Headline
import com.robin.claudeusage.data.HistoryPoint
import com.robin.claudeusage.data.WindowKind
import com.robin.claudeusage.ui.PaceText
import com.robin.claudeusage.widgets.FaceWindow
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

    /**
     * The earlier part of the week, for a Weekly-led card's trend: [n] readings from a day
     * into the window up to the 5h window, climbing to [to].
     */
    private fun week(to: Double, n: Int = 7): List<HistoryPoint> {
        val start = RESET_WEEKLY.toEpochMilli() - 7 * 24 * 60 * MIN + 24 * 60 * MIN
        val end = RESET_5H.toEpochMilli() - 5 * 60 * MIN - 60 * MIN
        return (0 until n).map { i ->
            HistoryPoint(
                at = start + (end - start) * i / (n - 1), sessionPct = null, sessionResetAt = 0L,
                weeklyPct = to * (i + 1) / (n + 1), weeklyResetAt = RESET_WEEKLY.toEpochMilli(),
            )
        }
    }

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
        headline: Headline = Headline.DEFAULT,
        binding: Boolean = false,
    ) = ShareCard.model(
        account, history, dark, left, showOverPace = true, synthetic = synthetic,
        headline = headline, sessionBinding = binding, nowMs = NOW_MS, zone = ZONE,
    )

    /** CCRM-24 (Share Card), Weekly first: the five states of parity rev B §6, by name. */
    private fun weeklyFirstStates(dark: Boolean = true) = linkedMapOf(
        "5h-leads" to model(headline = Headline.FIVE_HOUR, dark = dark),
        "weekly-leads" to model(history = week(20.0) + history().map { it.copy(weeklyPct = 22.0) }, dark = dark),
        "weekly-spent" to model(
            GalleryFixtures.pro(GalleryFixtures.both(34.0, 100.0)), week(96.0) + history(to = 34.0).map { it.copy(weeklyPct = 100.0) },
            dark = dark, headline = Headline.FIVE_HOUR,
        ),
        "weekly-only" to model(GalleryFixtures.CHATGPT, history = emptyList(), dark = dark),
        "no-reading" to model(GalleryFixtures.account("pro", "Pro", null), dark = dark),
    )

    @Test
    fun `the card carries the label, the snapshot's own stamp and both windows`() {
        val m = model(headline = Headline.FIVE_HOUR)
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
        val m = model(left = true, headline = Headline.FIVE_HOUR)
        assertEquals("5h left", m.rows[0].name)
        assertEquals("38%", m.rows[0].figure)
        assertTrue(m.rows[0].pace!!.endsWith("above even pace"))
    }

    @Test
    fun `never fetched has no stamp, and a not-started window has no trend`() {
        val m = model(
            GalleryFixtures.account("pro", "Pro", GalleryFixtures.both(null, sessionReset = null), fetchedAt = 0),
            headline = Headline.FIVE_HOUR,
        )
        assertNull(m.asOf)
        assertEquals("—", m.headline.figure)
        assertNull(m.trend)
    }

    @Test
    fun `the order follows the Headline rule, a spent week always leads, no 5h is one row`() {
        fun o(h: Headline, session: Boolean = true, binding: Boolean = false, spent: Boolean = false) =
            ShareCard.order(h, hasSession = session, sessionBinding = binding, weeklySpent = spent)
        val ws = listOf(WindowKind.WEEKLY, WindowKind.SESSION)
        val sw = listOf(WindowKind.SESSION, WindowKind.WEEKLY)
        assertEquals(ws, o(Headline.WEEKLY))
        assertEquals(ws, o(Headline.AUTO))
        assertEquals(sw, o(Headline.AUTO, binding = true))
        assertEquals(sw, o(Headline.FIVE_HOUR))
        assertEquals(ws, o(Headline.FIVE_HOUR, spent = true))
        assertEquals(ws, o(Headline.AUTO, binding = true, spent = true))
        assertEquals(listOf(WindowKind.WEEKLY), o(Headline.FIVE_HOUR, session = false))
    }

    @Test
    fun `5h leads under Headline 5-hour and under Auto while the 5h binds - as today`() {
        for (m in listOf(model(headline = Headline.FIVE_HOUR), model(headline = Headline.AUTO, binding = true))) {
            assertEquals(listOf("5h", "Weekly"), m.rows.map { it.name })
            assertEquals(listOf(true, false), m.rows.map { it.lead })
            assertEquals("5h trend", m.trend!!.title)
            assertEquals(FaceWindow.SESSION, m.headline.window)
            assertFalse(m.ringDimmed)
            assertTrue(m.rows.none { it.dimmed || it.alarm })
        }
    }

    @Test
    fun `Weekly leads by default with its trend, and the ring stays the 5h gauge`() {
        for (m in listOf(model(), model(headline = Headline.AUTO))) {
            assertEquals(listOf("Weekly", "5h"), m.rows.map { it.name })
            assertEquals(listOf(true, false), m.rows.map { it.lead })
            assertEquals("22%", m.rows[0].figure)
            assertEquals("Weekly trend", m.trend!!.title)
            // The header ring never swaps: it is the 5h (62%) under a Weekly lead.
            assertEquals(FaceWindow.SESSION, m.headline.window)
            assertEquals("62%", m.headline.figure)
            assertTrue(m.rows.none { it.dimmed || it.alarm })
        }
    }

    // CCRM-104 (Pace Line Parity) D9: the Weekly row's left slot is the tinted span, the reset keeps the right.
    @Test
    fun `the weekly pace slot is the tinted span alone`() {
        val now = 1_800_000_000_000L
        val week = 7 * 24 * 60 * 60_000L
        val resets = java.time.Instant.ofEpochMilli(now + 3 * 24 * 60 * 60_000L) // 4/7 of the week gone
        // Over the mark: the mark reaches 80% 1d 14h from now; under: it passed 20% 2d 14h ago.
        assertEquals("1d 14h ahead" to PaceText.VsTone.OVER, ShareCard.weeklyPace(80.0, resets, week, now))
        assertEquals("2d 14h behind" to PaceText.VsTone.UNDER, ShareCard.weeklyPace(20.0, resets, week, now))
        assertEquals("On the mark" to PaceText.VsTone.ON, ShareCard.weeklyPace(57.0, resets, week, now))
        assertNull(ShareCard.weeklyPace(100.0, resets, week, now))
        assertNull(ShareCard.weeklyPace(null, resets, week, now))
        assertNull(ShareCard.weeklyPace(40.0, null, week, now))
    }

    @Test
    fun `the weekly row carries the span and keeps its reset, the 5h row its own phrase`() {
        val m = model(headline = Headline.FIVE_HOUR)
        val week = m.rows[1]
        assertTrue(week.pace!!, week.pace!!.matches(Regex("""(\d+d \d+h|\d+h \d+m|\d+m) (ahead|behind)|On the mark""")))
        assertNotNull(week.paceTone)
        assertEquals("Resets Sat 9:10 PM", week.reset)
        assertTrue(m.rows[0].pace!!.endsWith("above even pace"))
        assertNull(m.rows[0].paceTone)
    }

    @Test
    fun `a spent week leads whatever the Headline, red, out until its reset, and dims the 5h row and the ring`() {
        val m = weeklyFirstStates().getValue("weekly-spent")
        assertEquals(listOf("Weekly", "5h"), m.rows.map { it.name })
        val (week, five) = m.rows
        assertTrue(week.lead && week.alarm && !week.dimmed)
        assertEquals("100%", week.figure)
        assertEquals("Out until Sat 9:10 PM", week.pace)
        assertEquals("Resets Sat 9:10 PM", week.reset)
        assertTrue(five.dimmed && !five.lead && !five.alarm)
        assertNull(five.pace)
        assertEquals("Resets 9:10 PM", five.reset)
        assertTrue(m.ringDimmed)
        assertEquals("Weekly trend", m.trend!!.title)
    }

    @Test
    fun `under a spent week a stale 5h still says Stale and a not-started 5h says so`() {
        val stale = model(
            GalleryFixtures.pro(GalleryFixtures.both(34.0, 100.0)).copy(fetchedAt = NOW_MS - 7 * 60 * MIN),
            headline = Headline.FIVE_HOUR,
        )
        assertEquals("Stale", stale.rows.single { it.name == "5h" }.reset)
        val idle = model(GalleryFixtures.pro(GalleryFixtures.both(null, 100.0, sessionReset = null)))
        val five = idle.rows.single { it.name == "5h" }
        assertTrue(five.dimmed)
        assertEquals("Starts when a message is sent", five.reset)
    }

    @Test
    fun `a weekly-only plan has the one row, which leads, and the ring is the Weekly`() {
        val m = weeklyFirstStates().getValue("weekly-only")
        assertEquals(listOf("Weekly"), m.rows.map { it.name })
        assertTrue(m.rows[0].lead)
        assertEquals(FaceWindow.WEEKLY, m.headline.window)
        assertFalse(m.ringDimmed)
    }

    @Test
    fun `no reading draws the ring's dash and nothing else`() {
        val m = weeklyFirstStates().getValue("no-reading")
        assertEquals("—", m.headline.figure)
        assertTrue(m.rows.isEmpty())
        assertNull(m.trend)
    }

    @Test
    fun `the five Weekly-first states render in both themes`() {
        val dump = System.getenv("SHARE_CARD_DUMP")?.let { File(it).apply { mkdirs() } }
        for (dark in listOf(true, false)) {
            for ((name, card) in weeklyFirstStates(dark)) {
                val (bmp, end) = ShareCard.draw(context, card)
                assertEquals(name, 1440, bmp.width)
                assertEquals(name, ShareCard.heightPx(card), bmp.height)
                // What render drew ends where heightPx says: nothing clipped, no empty tail.
                assertEquals(name, ShareCard.heightPx(card).toFloat(), end, 1f)
                dump?.let { d ->
                    File(d, "$name-${if (dark) "dark" else "light"}.png").outputStream()
                        .use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
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

    @Test
    fun `CCBG-95 - the projection label yields to the now label when their boxes meet`() {
        val now = android.graphics.RectF(100f, 40f, 140f, 56f)
        assertTrue(ChartBitmap.projectionYields(android.graphics.RectF(110f, 50f, 150f, 64f), now, 4f))
        assertTrue(ChartBitmap.projectionYields(android.graphics.RectF(142f, 40f, 180f, 56f), now, 4f)) // 2 px apart, inside the pad
        org.junit.Assert.assertFalse(ChartBitmap.projectionYields(android.graphics.RectF(160f, 70f, 200f, 86f), now, 4f))
    }
}
