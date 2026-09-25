package com.robin.claudeusage.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.widgets.WidgetActionReceiver.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Number 4×2's chips and cycler (CCRM-80 (Number Face), Q5): one tap moves one widget. */
@RunWith(RobolectricTestRunner::class)
class WidgetActionReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: WidgetPrefs
    private val registry = listOf("personal", "work", "gpt")

    @Before
    fun setUp() {
        context.getSharedPreferences(WidgetPrefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = WidgetPrefs(context)
    }

    @Test
    fun cycler_stepsThroughRegistryOrderAndWraps() {
        assertEquals("work", WidgetActionReceiver.nextAccount(registry, "personal"))
        assertEquals("gpt", WidgetActionReceiver.nextAccount(registry, "work"))
        assertEquals("personal", WidgetActionReceiver.nextAccount(registry, "gpt"))
        // Unassigned draws the first account, so the first tap goes to the second.
        assertEquals("work", WidgetActionReceiver.nextAccount(registry, null))
        assertNull(WidgetActionReceiver.nextAccount(emptyList(), "personal"))
    }

    @Test
    fun twoNumber4x2sOnDifferentAccounts_aTapOnOneNeverMovesTheOther() {
        prefs.save(3, "personal", FaceWindow.SESSION, FaceBackground.SOLID)
        prefs.save(4, "gpt", FaceWindow.SESSION, FaceBackground.GRADIENT)
        val before4 = prefs.read(4)

        WidgetActionReceiver.apply(prefs, registry, 3, Action.CYCLE)
        WidgetActionReceiver.apply(prefs, registry, 3, Action.WINDOW_WEEKLY)
        assertEquals("work", prefs.read(3).accountKey)
        assertEquals(FaceWindow.WEEKLY, prefs.read(3).window)
        assertEquals(before4, prefs.read(4))

        WidgetActionReceiver.apply(prefs, registry, 4, Action.CYCLE)
        assertEquals("personal", prefs.read(4).accountKey)
        assertEquals("work", prefs.read(3).accountKey)
    }

    @Test
    fun theCyclerStoresTheKey_soAReorderNeverRepointsIt() {
        prefs.save(3, "personal", FaceWindow.SESSION, FaceBackground.SOLID)
        WidgetActionReceiver.apply(prefs, registry, 3, Action.CYCLE)
        assertEquals("work", prefs.read(3).accountKey)
        // The registry reorders; the widget still shows "work", and steps on from it.
        val reordered = listOf("work", "gpt", "personal")
        assertEquals("work", prefs.read(3).accountKey)
        WidgetActionReceiver.apply(prefs, reordered, 3, Action.CYCLE)
        assertEquals("gpt", prefs.read(3).accountKey)
    }
}
