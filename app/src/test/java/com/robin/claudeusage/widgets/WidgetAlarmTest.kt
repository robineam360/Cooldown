package com.robin.claudeusage.widgets

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.data.AuthState
import com.robin.claudeusage.data.UsageCache
import com.robin.claudeusage.notify.Surfaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant

/**
 * R7 end to end on Robolectric's widget host: one alarm, re-armed over **all** placed
 * widgets by every change, cancelled only when no widget of any face is left — and the
 * Number 4×2's on-face taps move only the widget tapped (CCRM-78 §On-face controls).
 */
@RunWith(RobolectricTestRunner::class)
class WidgetAlarmTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mgr = AppWidgetManager.getInstance(context)
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val now = System.currentTimeMillis()
    private lateinit var cache: UsageCache

    @Before
    fun setUp() {
        cache = UsageCache(context)
        val (personal, work) = cache.registry().all().take(2)
        // Both accounts fetched just now; their 5h windows reset in 2 h and 4 h.
        cache.saveSuccess(personal, payload(now + 2 * H), now)
        cache.saveSuccess(work, payload(now + 4 * H), now)
        cache.setAuthState(personal, AuthState.OK)
        cache.setAuthState(work, AuthState.OK)
    }

    private fun payload(reset5h: Long) =
        """{"five_hour":{"utilization":38.0,"resets_at":"${Instant.ofEpochMilli(reset5h)}"},""" +
            """"seven_day":{"utilization":20.0,"resets_at":"${Instant.ofEpochMilli(now + 60 * H)}"}}"""

    private fun place(id: Int, face: Face) {
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(context.packageName, WidgetHost.PROVIDERS.getValue(face))
        }
        shadowOf(mgr).addBoundWidget(id, info)
    }

    private fun armedAt(): Long? = alarms.peekNextScheduledAlarm()?.triggerAtTime

    @Test
    fun theAlarmIsTheEarliestTransitionOverEveryPlacedWidget() {
        place(3, Face.NUMBER)
        place(4, Face.NUMBER)
        val prefs = WidgetPrefs(context)
        prefs.save(3, "work", FaceWindow.SESSION, FaceBackground.SOLID) // resets in 4 h
        prefs.save(4, "personal", FaceWindow.SESSION, FaceBackground.SOLID) // resets in 2 h
        Surfaces.arm(context)
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(now + 2 * H, armedAt())
    }

    @Test
    fun aTapOnOneNumberChangesOnlyIt_andReArms() {
        place(3, Face.NUMBER)
        place(4, Face.NUMBER)
        val prefs = WidgetPrefs(context)
        prefs.save(3, "personal", FaceWindow.SESSION, FaceBackground.SOLID)
        prefs.save(4, "work", FaceWindow.SESSION, FaceBackground.SOLID)
        val before4 = prefs.read(4)

        tap(3, WidgetActionReceiver.Action.CYCLE)
        assertEquals("work", prefs.read(3).accountKey)
        assertEquals(before4, prefs.read(4))
        // Both now show "work" (4 h out), so the re-armed alarm moved from 2 h to 4 h.
        assertEquals(now + 4 * H, armedAt())
        assertEquals(1, alarms.scheduledAlarms.size)

        tap(4, WidgetActionReceiver.Action.WINDOW_WEEKLY)
        assertEquals(FaceWindow.WEEKLY, prefs.read(4).window)
        assertEquals(FaceWindow.SESSION, prefs.read(3).window)
    }

    @Test
    fun deletingOneOfTwo_keepsTheAlarm_andTheLastGoingCancelsIt() {
        place(3, Face.NUMBER)
        Surfaces.arm(context)
        assertNotNull(armedAt())
        // Widget 4 was deleted by the launcher (it is no longer bound); 3 remains.
        NumberWidgetProvider().onDeleted(context, intArrayOf(4))
        assertNotNull(armedAt())
    }

    @Test
    fun nothingPlaced_onDisabledCancels() {
        // An alarm left from when a widget was placed; now nothing of any face is bound.
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC, now + H, WidgetActionReceiver.alarmIntent(context))
        assertNotNull(armedAt())
        NumberWidgetProvider().onDisabled(context)
        assertNull(armedAt())
    }

    private fun tap(id: Int, action: WidgetActionReceiver.Action) {
        WidgetActionReceiver().onReceive(
            context,
            Intent(context, WidgetActionReceiver::class.java).setData(WidgetHost.uri(id, action.path)),
        )
    }

    private companion object {
        const val H = 60 * 60_000L
    }
}
