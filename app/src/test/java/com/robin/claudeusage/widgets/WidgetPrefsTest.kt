package com.robin.claudeusage.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** CCRM-78 (Widgets Reborn): the `widget_prefs` contract (R9) and the unassigned state (R5). */
@RunWith(RobolectricTestRunner::class)
class WidgetPrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = WidgetPrefs(context)
    private val raw = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    @Test
    fun theKeysAreThePermanentContract() {
        prefs.save(7, "pro", FaceWindow.WEEKLY, FaceBackground.GRADIENT)
        assertEquals(
            mapOf("w7.account" to "pro", "w7.window" to "weekly", "w7.bg" to "gradient", "w7.v" to 1),
            raw.all,
        )
    }

    @Test
    fun anUnknownIdIsUnassignedWithDefaults() {
        val c = prefs.read(42)
        assertNull(c.accountKey)
        assertFalse(c.assigned)
        assertEquals(FaceWindow.SESSION, c.window)
        assertEquals(FaceBackground.SOLID, c.background)
        assertEquals(0, c.version)
    }

    @Test
    fun anEmptyAccountIsUnassigned() {
        prefs.save(3, null, FaceWindow.SESSION, FaceBackground.TRANSPARENT)
        assertEquals("", raw.getString("w3.account", null))
        val c = prefs.read(3)
        assertNull(c.accountKey)
        assertEquals(1, c.version)
        assertEquals(FaceBackground.TRANSPARENT, c.background)
    }

    @Test
    fun onFaceControlsWriteOneKey() {
        prefs.save(5, "pro", FaceWindow.SESSION, FaceBackground.SOLID)
        prefs.setWindow(5, FaceWindow.WEEKLY)
        prefs.setAccount(5, "teams")
        val c = prefs.read(5)
        assertEquals("teams", c.accountKey)
        assertEquals(FaceWindow.WEEKLY, c.window)
        assertEquals(FaceBackground.SOLID, c.background)
    }

    @Test
    fun deletePrunesOnlyThoseIds() {
        prefs.save(1, "pro", FaceWindow.SESSION, FaceBackground.SOLID)
        prefs.save(2, "teams", FaceWindow.WEEKLY, FaceBackground.SOLID)
        prefs.delete(intArrayOf(1))
        assertTrue(raw.all.keys.none { it.startsWith("w1.") })
        assertEquals("teams", prefs.read(2).accountKey)
    }

    @Test
    fun remapMovesKeys_evenWhenIdsOverlap() {
        prefs.save(10, "pro", FaceWindow.WEEKLY, FaceBackground.GRADIENT)
        prefs.save(11, "teams", FaceWindow.SESSION, FaceBackground.TRANSPARENT)
        // 10 → 11 and 11 → 12: a naive in-order copy would overwrite 11 before moving it.
        prefs.remap(intArrayOf(10, 11), intArrayOf(11, 12))
        assertEquals("pro", prefs.read(11).accountKey)
        assertEquals(FaceWindow.WEEKLY, prefs.read(11).window)
        assertEquals("teams", prefs.read(12).accountKey)
        assertEquals(FaceBackground.TRANSPARENT, prefs.read(12).background)
        assertNull(prefs.read(10).accountKey)
        assertEquals(0, prefs.read(10).version)
    }

    @Test
    fun aRestoredIdWithNoPrefsStaysUnassigned() {
        prefs.remap(intArrayOf(20), intArrayOf(21))
        assertFalse(prefs.read(21).assigned)
        assertEquals(0, prefs.read(21).version)
    }

    @Test
    fun unknownStoredValuesReadAsDefaults() {
        raw.edit().putString("w9.window", "monthly").putString("w9.bg", "neon").commit()
        assertEquals(FaceWindow.SESSION, prefs.read(9).window)
        assertEquals(FaceBackground.SOLID, prefs.read(9).background)
    }
}
