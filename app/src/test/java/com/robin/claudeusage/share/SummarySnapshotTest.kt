package com.robin.claudeusage.share

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.SummaryRowModel
import com.robin.claudeusage.data.AccountTag
import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.MapLayers
import com.robin.claudeusage.data.Profile
import com.robin.claudeusage.data.Projection
import com.robin.claudeusage.data.Provider
import com.robin.claudeusage.data.SummaryFilter
import com.robin.claudeusage.data.SummaryOrder
import com.robin.claudeusage.data.SummaryRank
import com.robin.claudeusage.data.UsageData
import com.robin.claudeusage.data.UsageWindow
import com.robin.claudeusage.ui.PaceText
import com.robin.claudeusage.widgets.AccountInput
import com.robin.claudeusage.widgets.GalleryFixtures
import com.robin.claudeusage.widgets.GalleryFixtures.MIN
import com.robin.claudeusage.widgets.GalleryFixtures.NOW_MS
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
 * CCRM-112 (Summary Snapshot) and the snapshot half of CCRM-115 (Row Tag Glyph): what the card says (the filter's
 * header, names only, the glyph column only while the rows carry both tags, one line each from six rows) and that it
 * renders at 4× for every state the wireframes draw, ending exactly where [SummarySnapshot.heightPx] says.
 *
 * Set `SUMMARY_SNAPSHOT_DUMP=<dir>` to write each rendered card as a PNG for a visual check.
 */
@RunWith(RobolectricTestRunner::class)
class SummarySnapshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun week(used: Double?, reset: java.time.Instant? = RESET_WEEKLY) =
        UsageData(session = UsageWindow(40.0, GalleryFixtures.RESET_5H, null), weekly = UsageWindow(used, reset, null), modelCaps = emptyList())

    private fun elapsed(reset: java.time.Instant?): Double? =
        reset?.let { 100.0 * (1.0 - (it.toEpochMilli() - NOW_MS).toDouble() / Projection.WEEKLY_MS) }

    private class Spec(val key: String, val label: String, val tag: AccountTag, val data: UsageData?, val provider: Provider = Provider.CLAUDE,
                       val auth: AuthState = AuthState.OK, val group: SummaryRank.Group = SummaryRank.Group.ELIGIBLE, val fetched: Long = NOW_MS - 3 * MIN)

    private fun specs(n: Int, tags: List<AccountTag>): List<Spec> {
        val names = listOf("Teams", "Personal", "Lab", "Atlas", "Ops", "Research", "Side", "Design")
        return (0 until n).map { i ->
            Spec("a$i", names[i], tags[i % tags.size], week(20.0 + 9 * i), if (i % 3 == 1) Provider.CHATGPT else Provider.CLAUDE)
        }
    }

    private fun model(
        specs: List<Spec>, filter: SummaryFilter = SummaryFilter.ALL, dark: Boolean = false,
        layers: MapLayers = MapLayers(figures = false, trails = true, projection = false), synthetic: Boolean = false,
    ): SummarySnapshot.Model {
        val inputs = specs.map {
            AccountInput(it.key, it.label, it.provider, GalleryFixtures.PRO_ACCENT, it.data, it.fetched, it.auth)
        }
        val rows = specs.mapIndexed { i, s ->
            SummaryRowModel(Profile(s.key, i, s.label, s.provider), s.label, s.data, elapsed(s.data?.weekly?.resetsAt), s.group, emptyList())
        }
        return SummarySnapshot.model(
            accounts = inputs, rows = rows, tags = specs.associate { it.key to it.tag }, filter = filter,
            order = SummaryOrder.YOURS, layers = layers, mode = PaceText.Mode.USED, use24h = false, dark = dark,
            showOverPace = true, synthetic = synthetic, nowMs = NOW_MS, zone = ZONE,
            accentOf = { Color(GalleryFixtures.PRO_ACCENT) },
        )
    }

    @Test fun `the header names the filter and the rows are the account names only`() {
        for ((f, title) in listOf(SummaryFilter.ALL to "All accounts", SummaryFilter.PERSONAL to "Personal accounts", SummaryFilter.WORK to "Work accounts")) {
            val m = model(specs(3, listOf(AccountTag.WORK, AccountTag.PERSONAL)), f)
            assertEquals(title, m.title)
            assertEquals("your order", m.orderName)
            assertEquals(listOf("Teams", "Personal", "Lab"), m.rows.map { it.name })
        }
        assertNotNull(model(specs(2, listOf(AccountTag.PERSONAL))).asOf)
    }

    @Test fun `the glyph column rides only while the rows on the card carry both tags`() {
        assertTrue(SummarySnapshot.glyphColumn(listOf(AccountTag.WORK, AccountTag.PERSONAL)))
        assertFalse(SummarySnapshot.glyphColumn(listOf(AccountTag.PERSONAL, AccountTag.PERSONAL)))
        assertFalse(SummarySnapshot.glyphColumn(listOf(AccountTag.WORK, AccountTag.WORK)))
        assertFalse(SummarySnapshot.glyphColumn(emptyList()))
        // Under All with both tags: drawn; an upgraded install (all Personal): the card is rev B's; under Work: header says it.
        assertTrue(model(specs(3, listOf(AccountTag.WORK, AccountTag.PERSONAL))).glyphColumn)
        assertFalse(model(specs(3, listOf(AccountTag.PERSONAL))).glyphColumn)
        val work = specs(3, listOf(AccountTag.WORK, AccountTag.PERSONAL)).filter { it.tag == AccountTag.WORK }
        assertFalse(model(work, SummaryFilter.WORK).glyphColumn)
    }

    @Test fun `one line each from six rows shown`() {
        assertFalse(model(specs(5, listOf(AccountTag.WORK))).oneLine)
        assertTrue(model(specs(6, listOf(AccountTag.WORK))).oneLine)
        assertTrue(SummarySnapshot.heightPx(model(specs(6, listOf(AccountTag.WORK)))) < SummarySnapshot.heightPx(model(specs(5, listOf(AccountTag.WORK)))) + 100)
    }

    @Test fun `the entry exists only while some account shown has a reading`() {
        val none = specs(2, listOf(AccountTag.WORK)).map { Spec(it.key, it.label, it.tag, null) }
        fun rows(s: List<Spec>) = s.mapIndexed { i, x -> SummaryRowModel(Profile(x.key, i, x.label), x.label, x.data, null, SummaryRank.Group.NO_READING, emptyList()) }
        assertFalse(SummarySnapshot.hasReading(rows(none)))
        assertTrue(SummarySnapshot.hasReading(rows(specs(2, listOf(AccountTag.WORK)))))
    }

    @Test fun `a spent week, an unread row, a signed-out row and a stale row carry their words`() {
        val s = listOf(
            Spec("a0", "Atlas", AccountTag.WORK, week(100.0), group = SummaryRank.Group.SPENT),
            Spec("a1", "Product", AccountTag.PERSONAL, null, auth = AuthState.REAUTH_NEEDED, group = SummaryRank.Group.NO_READING),
            Spec("a2", "Notes", AccountTag.WORK, null, group = SummaryRank.Group.NO_READING),
            Spec("a3", "Studio", AccountTag.PERSONAL, week(44.0), fetched = NOW_MS - 3 * 24 * 60 * MIN),
        )
        val rows = model(s).rows
        assertTrue(rows[0].alarm); assertTrue(rows[0].pace!!.startsWith("Out until"))
        assertTrue(rows[1].faded); assertEquals("Sign in", rows[1].pace); assertEquals("—", rows[1].figure); assertNull(rows[1].pct)
        assertTrue(rows[2].faded); assertEquals("No reading", rows[2].pace)
        assertEquals("Stale", rows[3].pace); assertNull(rows[3].reset)
    }

    @Test fun `every card renders at 4x and ends where heightPx says, light and dark`() {
        val dump = System.getenv("SUMMARY_SNAPSHOT_DUMP")?.let { File(it).also { d -> d.mkdirs() } }
        val both = listOf(AccountTag.WORK, AccountTag.PERSONAL)
        val cases = listOf(
            "all-both" to model(specs(4, both)),
            "all-personal" to model(specs(4, listOf(AccountTag.PERSONAL))),
            "work-figures-projection" to model(specs(3, listOf(AccountTag.WORK)), SummaryFilter.WORK, layers = MapLayers(true, true, true)),
            "one-line" to model(specs(7, both)),
            "synthetic" to model(specs(3, both), synthetic = true),
        )
        for ((name, light) in cases) for (dark in listOf(false, true)) {
            val m = light.copy(dark = dark)
            val (bmp, end) = SummarySnapshot.draw(context, m)
            assertEquals(name, ShareCard.WIDTH_PX, bmp.width)
            assertEquals(name, SummarySnapshot.heightPx(m), bmp.height)
            // What render drew ends where heightPx says: nothing clipped, no empty tail.
            assertEquals(name, SummarySnapshot.heightPx(m).toFloat(), end, 1f)
            dump?.let { d -> File(d, "$name-${if (dark) "dark" else "light"}.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        }
    }
}
