package com.robin.claudeusage.diag

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** CCRM-85 (Crash Capture): the card's words, state by state, as wireframe rev B draws them. */
class CrashCopyTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 9, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = at(30, 22, 0)

    @Test
    fun `when words`() {
        assertEquals("today at 9:41", CrashCopy.whenText(at(30, 9, 41), now, zone, use24h = true))
        assertEquals("today at 9:41 AM", CrashCopy.whenText(at(30, 9, 41), now, zone, use24h = false))
        assertEquals("yesterday at 21:07", CrashCopy.whenText(at(29, 21, 7), now, zone, use24h = true))
        assertEquals("on 28 Sep", CrashCopy.whenText(at(28, 12, 0), now, zone, use24h = true))
    }

    @Test
    fun `titles`() {
        assertEquals("Cooldown crashed today at 9:41", CrashCopy.title(CrashReport.Kind.CRASH, "today at 9:41"))
        assertEquals("Cooldown crashed today at 9:41", CrashCopy.title(CrashReport.Kind.NATIVE, "today at 9:41"))
        assertEquals("Cooldown stopped responding today at 9:41", CrashCopy.title(CrashReport.Kind.ANR, "today at 9:41"))
    }

    @Test
    fun `details`() {
        assertEquals("A report is saved on this phone. It goes nowhere unless you share it.", CrashCopy.detail(1, true))
        assertEquals(
            "Android kept no trace, so the report has only the time, the reason and the app version.",
            CrashCopy.detail(1, false),
        )
        assertEquals("2 reports on this phone. Share sends both, newest first.", CrashCopy.detail(2, false))
        assertEquals("3 reports on this phone. Share sends all 3, newest first.", CrashCopy.detail(3, true))
    }

    @Test
    fun `diagnostics line and subject`() {
        assertEquals(
            "2 on this phone · newest today at 9:41 · each is kept for 30 days. " +
                "A report never contains tokens, emails or exception messages.",
            CrashCopy.diagnostics(2, "today at 9:41"),
        )
        assertEquals("Cooldown crash report (v1.9)", CrashCopy.shareSubject("1.9"))
    }
}
