package com.robin.claudeusage.notify

import org.junit.Assert.assertEquals
import org.junit.Test

/** CCBG-45 (Single Panel Wording): "5h" and "Weekly" on the one-account notification too. */
class SinglePanelWordingTest {

    @Test
    fun `the headline window is 5h or Weekly, never the long names`() {
        assertEquals("5h", PinnedNotification.windowName(headlineWeekly = false))
        assertEquals("Weekly", PinnedNotification.windowName(headlineWeekly = true))
    }

    @Test
    fun `the weekly row is bare with one account and prefixed with two`() {
        assertEquals("Weekly", PinnedNotification.weeklyLabel(null))
        assertEquals("Work · Weekly", PinnedNotification.weeklyLabel("Work"))
    }
}
