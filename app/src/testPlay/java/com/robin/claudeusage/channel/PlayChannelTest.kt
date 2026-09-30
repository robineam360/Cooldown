package com.robin.claudeusage.channel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.data.UsageCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CCRM-87 (Update Channel), play flavor: an install that came from the GitHub APK
 * inherits its update prefs. A stale `latestKnownVersion` must never become an
 * "Update available" strip Play will not honour, and the first poll drops it.
 */
@RunWith(RobolectricTestRunner::class)
class PlayChannelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var cache: UsageCache

    /** What a github build leaves behind after it has seen v99.0 and the user skipped v98.0. */
    @Before
    fun inheritGithubState() {
        cache = UsageCache(context)
        cache.recordUpdateCheckSuccess(1_700_000_000_000L, "v99.0 available", "99.0")
        cache.recordUpdateCheckFailure(1_700_000_100_000L, "couldn't reach GitHub")
        cache.setDismissedUpdateVersion("98.0")
        cache.setAutoCheckUpdates(false)
    }

    @Test
    fun `a stale latestKnownVersion never shows a strip`() {
        assertEquals("99.0", cache.latestKnownVersion())
        assertNull(Channel.updateCondition(context, cache))
    }

    @Test
    fun `the poll clears the inherited update state`() {
        Channel.autoCheck(context, cache)
        assertNull(cache.latestKnownVersion())
        assertNull(cache.lastUpdateCheckOutcome())
        assertNull(cache.dismissedUpdateVersion())
        assertNull(cache.lastUpdateFailReason())
        assertEquals(0L, cache.lastUpdateCheckAt())
        assertEquals(0L, cache.lastUpdateFailAt())
        assertNull(Channel.updateCondition(context, cache))
    }

    @Test
    fun `the auto-check toggle is a preference and survives`() {
        Channel.autoCheck(context, cache)
        assertFalse(cache.autoCheckUpdates())
    }

    @Test
    fun `a clean install stays clean`() {
        cache.clearUpdateState()
        Channel.autoCheck(context, cache)
        assertNull(cache.latestKnownVersion())
        assertEquals(0L, cache.lastUpdateCheckAt())
    }
}
