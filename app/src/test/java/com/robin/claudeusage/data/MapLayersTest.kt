package com.robin.claudeusage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CCRM-111 (Map Layers): the Pace map's three chips are remembered in three new keys — Figures off, Trails on,
 * Projection off by default — and a v1.9 install, which never reads them, is unaffected after a downgrade.
 */
@RunWith(RobolectricTestRunner::class)
class MapLayersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `defaults - Figures off, Trails on, Projection off`() {
        assertEquals(MapLayers(figures = false, trails = true, projection = false), UsageCache(context).mapLayers())
    }

    @Test
    fun `every combination is remembered and the keys are the new ones`() {
        val cache = UsageCache(context)
        for (figures in listOf(false, true)) for (trails in listOf(false, true)) for (projection in listOf(false, true)) {
            val layers = MapLayers(figures, trails, projection)
            cache.setMapLayers(layers)
            assertEquals(layers, UsageCache(context).mapLayers())
        }
        val prefs = context.getSharedPreferences("usage_cache", Context.MODE_PRIVATE)
        assertTrue(prefs.all.keys.containsAll(setOf("paceMap.figures", "paceMap.trails", "paceMap.projection")))
        assertFalse(prefs.all.keys.any { it == "figures" || it == "trails" || it == "projection" })
    }
}
