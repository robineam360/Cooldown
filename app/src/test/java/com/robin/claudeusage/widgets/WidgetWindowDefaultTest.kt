package com.robin.claudeusage.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.data.Headline
import com.robin.claudeusage.data.UsageCache
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CCRM-96 (Weekly First), RUNBOOK-v2.0 Step 4: a new widget's window starts on its account's
 * Headline; a widget placed before v2.0 keeps its window; the first draw pins it.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetWindowDefaultTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var cache: UsageCache
    private lateinit var prefs: WidgetPrefs
    private val body =
        """{"five_hour":{"utilization":12.0,"resets_at":"2099-01-01T00:00:00Z"},""" +
            """"seven_day":{"utilization":5.0,"resets_at":"2099-01-05T00:00:00Z"}}"""

    @Before
    fun setUp() {
        cache = UsageCache(context)
        prefs = WidgetPrefs(context)
        cache.saveSuccess(cache.registry().first(), body, System.currentTimeMillis())
    }

    @Test
    fun `a new widget starts on Weekly by default`() {
        assertEquals(FaceWindow.WEEKLY, WidgetHost.config(context, cache, 101).window)
    }

    @Test
    fun `an unassigned new widget follows the first account's 5-hour Headline`() {
        cache.setHeadline(cache.registry().first(), Headline.FIVE_HOUR)
        assertEquals(FaceWindow.SESSION, WidgetHost.config(context, cache, 102).window)
    }

    @Test
    fun `Auto starts a new widget on Weekly`() {
        cache.setHeadline(cache.registry().first(), Headline.AUTO)
        assertEquals(FaceWindow.WEEKLY, WidgetHost.config(context, cache, 103).window)
    }

    @Test
    fun `a widget drawn before v2 without a stored window keeps 5h`() {
        prefs.markDrawn(104)
        assertEquals(FaceWindow.SESSION, WidgetHost.config(context, cache, 104).window)
    }

    @Test
    fun `the first draw pins the window against later Headline changes`() {
        val window = WidgetHost.config(context, cache, 105).window
        prefs.pinWindowIfAbsent(105, window)
        prefs.markDrawn(105)
        cache.setHeadline(cache.registry().first(), Headline.FIVE_HOUR)
        assertEquals(FaceWindow.WEEKLY, WidgetHost.config(context, cache, 105).window)
    }

    @Test
    fun `each Strip ring follows its account's Headline live`() {
        val first = cache.registry().first()
        assertEquals(FaceWindow.WEEKLY, WidgetHost.accounts(context, cache, dark = true, withEstimates = false).first().stripWindow)
        cache.setHeadline(first, Headline.FIVE_HOUR)
        assertEquals(FaceWindow.SESSION, WidgetHost.accounts(context, cache, dark = true, withEstimates = false).first().stripWindow)
    }

    @Test
    fun `a saved window always wins`() {
        prefs.save(106, null, FaceWindow.SESSION, FaceBackground.SOLID)
        assertEquals(FaceWindow.SESSION, WidgetHost.config(context, cache, 106).window)
    }
}
