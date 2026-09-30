package com.robin.claudeusage.diag

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.TransactionTooLargeException
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.ZoneId

/** CCRM-85 (Crash Capture): the files — the handler's write, the next launch's filing, dismiss and share. */
@RunWith(RobolectricTestRunner::class)
class CrashStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val meta = CrashReport.Meta("1.9", 25, "github", 36, "16", "samsung", "SM-F966B")

    @Before
    fun setUp() {
        CrashStore.dir(context).deleteRecursively()
    }

    private fun crash(at: Long) = CrashReport.javaCrash(CrashNowCanary(), "main", at, 4242, meta, ZoneId.of("UTC"))

    @Test
    fun `the handler's capture becomes one unseen report on the next launch`() {
        val now = System.currentTimeMillis()
        CrashStore.writeLast(context, crash(now))
        assertFalse(File(CrashStore.dir(context), "${CrashStore.LAST}.tmp").exists())
        CrashStore.ingestForTest(context, now)

        val unseen = CrashStore.unseen(context)
        assertEquals(1, unseen.size)
        assertEquals(CrashReport.Kind.CRASH, unseen[0].kind)
        assertEquals(now, unseen[0].at)
        assertEquals(4242, unseen[0].pid)
        assertTrue(unseen[0].hasTrace)
        assertFalse(File(CrashStore.dir(context), CrashStore.LAST).exists())
    }

    @Test
    fun `a dismissed report leaves the card but stays in Diagnostics`() {
        val now = System.currentTimeMillis()
        CrashStore.writeLast(context, crash(now))
        CrashStore.ingestForTest(context, now)
        CrashStore.dismiss(context, CrashStore.unseen(context).map { it.name })
        assertEquals(0, CrashStore.unseen(context).size)
        assertEquals(1, CrashStore.reports(context).size)
        assertTrue(CrashStore.reports(context)[0].dismissed)
    }

    @Test
    fun `the chooser callback dismisses exactly the shared reports`() {
        val now = System.currentTimeMillis()
        CrashStore.writeLast(context, crash(now - 1_000))
        CrashStore.ingestForTest(context, now)
        CrashStore.writeLast(context, crash(now))
        CrashStore.ingestForTest(context, now)
        val older = CrashStore.unseen(context).last()
        CrashShareReceiver().onReceive(
            context,
            Intent().putExtra(CrashShareReceiver.EXTRA_REPORTS, arrayOf(older.name)),
        )
        assertEquals(listOf(now), CrashStore.unseen(context).map { it.at })
    }

    @Test
    fun `the shared text is newest first and carries no canary`() {
        val now = System.currentTimeMillis()
        CrashStore.writeLast(context, crash(now - 60_000))
        CrashStore.ingestForTest(context, now)
        CrashStore.writeLast(context, crash(now))
        CrashStore.ingestForTest(context, now)
        val text = CrashStore.shareText(context, CrashStore.unseen(context))
        assertFalse(text.contains(CrashNowCanary.TOKEN))
        assertFalse(text.contains(CrashNowCanary.EMAIL))
        assertTrue(text.indexOf("At: $now") < text.indexOf("At: ${now - 60_000}"))
    }

    @Test
    fun `reports expire after 30 days and at most eight are kept`() {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        CrashStore.writeLast(context, crash(now - 31 * day))
        CrashStore.ingestForTest(context, now)
        assertEquals(0, CrashStore.reports(context).size)
        for (i in 0 until 10) {
            CrashStore.writeLast(context, crash(now - i * 1_000L))
            CrashStore.ingestForTest(context, now)
        }
        assertEquals(CrashStore.KEEP_COUNT, CrashStore.reports(context).size)
        assertEquals(now, CrashStore.reports(context).first().at)
    }

    @Test
    fun `eight full-size reports still fit one share intent`() {
        val now = System.currentTimeMillis()
        val huge = "\"main\" prio=5 tid=1 Blocked\n" + "  at a.b.C.d(C.kt:1)\n".repeat(20_000)
        val d = CrashStore.dir(context).apply { mkdirs() }
        for (i in 0 until CrashStore.KEEP_COUNT) {
            val report = CrashReport.exitReport(CrashReport.Kind.ANR, "not responding", now - i, i, huge, meta, ZoneId.of("UTC"))
            File(d, "r-${now - i}-$i-anr-t.txt").writeText(report)
        }
        assertEquals(CrashStore.KEEP_COUNT, CrashStore.unseen(context).size)
        val text = CrashStore.shareText(context, CrashStore.unseen(context))
        // UTF-16 in the parcel, and ACTION_SEND copies EXTRA_TEXT into ClipData as well.
        assertTrue("${text.length} chars", text.length * 2 * 2 < 300 * 1024)
    }

    @Test
    fun `the Application files the last crash on process start`() {
        val now = System.currentTimeMillis()
        CrashStore.writeLast(context, crash(now))
        assertTrue(CrashStore.fileLast(context))
        assertFalse(File(CrashStore.dir(context), CrashStore.LAST).exists())
        assertEquals(listOf(now), CrashStore.unseen(context).map { it.at })
        // A second crash before any screen opens is filed alongside, not over it.
        CrashStore.writeLast(context, crash(now + 1))
        CrashStore.fileLast(context)
        assertEquals(2, CrashStore.unseen(context).size)
    }

    // Astra 2026-09-30 (C4-3): dismissal is the chooser's pick callback and nothing else.
    @Test
    fun `opening, backing out of or failing a share dismisses nothing`() {
        val now = System.currentTimeMillis()
        CrashStore.writeLast(context, crash(now))
        CrashStore.ingestForTest(context, now)
        val unseen = CrashStore.unseen(context)

        // The sheet opens; backing out sends no callback.
        assertTrue(CrashStore.share(context, unseen))
        assertEquals(unseen, CrashStore.unseen(context))

        // A launch the system refuses — too large, or no activity — never crashes and keeps the card.
        for (failure in listOf(
            RuntimeException("Failure from system", TransactionTooLargeException()),
            ActivityNotFoundException(),
        )) {
            val refusing = object : ContextWrapper(context) {
                override fun startActivity(intent: Intent?) = throw failure
            }
            assertFalse(CrashStore.share(refusing, unseen))
            assertEquals(unseen, CrashStore.unseen(context))
        }

        // Only the pick callback dismisses.
        CrashShareReceiver().onReceive(context, Intent().putExtra(CrashShareReceiver.EXTRA_REPORTS, unseen.map { it.name }.toTypedArray()))
        assertEquals(0, CrashStore.unseen(context).size)
    }

    @Test
    fun `delete removes every report`() {
        val now = System.currentTimeMillis()
        CrashStore.writeLast(context, crash(now))
        CrashStore.ingestForTest(context, now)
        CrashStore.deleteAll(context)
        assertEquals(0, CrashStore.reports(context).size)
    }
}
