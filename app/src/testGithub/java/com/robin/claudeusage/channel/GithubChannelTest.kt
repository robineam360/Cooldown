package com.robin.claudeusage.channel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.notify.Conditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CCRM-87 (Update Channel), github flavor: the update strip is exactly the v1.8 one —
 * present while the last release check saw a newer version, gone once it is installed
 * or skipped.
 */
@RunWith(RobolectricTestRunner::class)
class GithubChannelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var cache: UsageCache
    private lateinit var installed: String

    @Before
    fun setUp() {
        cache = UsageCache(context)
        cache.clearUpdateState()
        installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName!!
    }

    @Test
    fun `no check yet means no strip`() {
        assertNull(Channel.updateCondition(context, cache))
    }

    @Test
    fun `a newer release shows the strip`() {
        cache.recordUpdateCheckSuccess(1L, "v99.0 available", "99.0")
        val strip: Conditions.Condition? = Channel.updateCondition(context, cache)
        assertEquals("Update available — v99.0", strip?.title)
    }

    @Test
    fun `the installed version shows no strip`() {
        cache.recordUpdateCheckSuccess(1L, "up to date (v$installed)", installed)
        assertNull(Channel.updateCondition(context, cache))
    }

    @Test
    fun `a skipped version shows no strip`() {
        cache.recordUpdateCheckSuccess(1L, "v99.0 available", "v99.0")
        cache.setDismissedUpdateVersion("99.0")
        assertNull(Channel.updateCondition(context, cache))
    }
}
