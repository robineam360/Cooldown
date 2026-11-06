package com.robin.claudeusage.notify

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CCBG-45 (Single Panel Wording): "Weekly", never "7-day", on the one-account notification
 * too. Since CCRM-103 (Hybrid Notification) the header always names the Weekly — the 5h is
 * the ring, which carries no "5h" text — and the panel no longer has a Weekly or 5h row.
 */
class SinglePanelWordingTest {

    @Test
    fun `the header names the Weekly, never the long name`() {
        assertEquals("Work · Weekly", LeadLines.headerTitle("Work"))
    }
}
