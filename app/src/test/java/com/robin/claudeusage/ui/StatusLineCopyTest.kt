package com.robin.claudeusage.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [statusLine] — the main screen's one status row (CCRM-72 (Main Screen Redesign)), per
 * `design/2026-09-17-main-screen-redesign.html` §2.
 */
class StatusLineCopyTest {

    private val now = 1_757_000_000_000L
    private fun minutes(n: Long) = n * 60_000L

    @Test
    fun `never succeeded reads not checked yet`() {
        assertEquals(
            "Not checked yet",
            statusLine(fetchedAt = 0L, lastAttemptAt = 0L, lastOk = false, now = now),
        )
    }

    @Test
    fun `a failing account that never succeeded still reads not checked yet`() {
        // The ErrorNotice directly beneath already spells the failure out; a "Tried"
        // clause here would only repeat it.
        assertEquals(
            "Not checked yet",
            statusLine(
                fetchedAt = 0L,
                lastAttemptAt = now - minutes(2),
                lastOk = false,
                now = now,
            ),
        )
    }

    @Test
    fun `a fresh success reads just now`() {
        assertEquals(
            "Checked just now",
            statusLine(fetchedAt = now - 30_000L, lastAttemptAt = now - 30_000L, lastOk = true, now = now),
        )
    }

    @Test
    fun `an older success carries its age`() {
        assertEquals(
            "Checked 14m ago",
            statusLine(
                fetchedAt = now - minutes(14),
                lastAttemptAt = now - minutes(14),
                lastOk = true,
                now = now,
            ),
        )
    }

    @Test
    fun `a failed attempt after the last success adds the tried clause`() {
        assertEquals(
            "Checked 14m ago · Tried 2m ago",
            statusLine(
                fetchedAt = now - minutes(14),
                lastAttemptAt = now - minutes(2),
                lastOk = false,
                now = now,
            ),
        )
    }

    @Test
    fun `a healthy account never shows two timestamps for one fact`() {
        // The successful attempt *is* the success — one clause, not two.
        assertEquals(
            "Checked 2m ago",
            statusLine(
                fetchedAt = now - minutes(2),
                lastAttemptAt = now - minutes(2),
                lastOk = true,
                now = now,
            ),
        )
    }

    @Test
    fun `a failure older than the last success is not mentioned`() {
        assertEquals(
            "Checked 2m ago",
            statusLine(
                fetchedAt = now - minutes(2),
                lastAttemptAt = now - minutes(40),
                lastOk = false,
                now = now,
            ),
        )
    }
}
