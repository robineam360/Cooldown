package com.robin.claudeusage.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CCRM-14 (Clear History): the two stores `UsageRepository.clearHistory` empties clear
 * one account and leave every other account's history exactly as it was.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryClearTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun clearingOneAccountLeavesTheOtherAlone() {
        val registry = ProfileRegistry(context)
        val a = registry.first()
        val b = registry.add("Work")
        val history = HistoryStore(context)
        val log = SessionLog(context)
        val now = System.currentTimeMillis()
        val data = UsageData(UsageWindow(40.0, null, null), UsageWindow(20.0, null, null), emptyList())
        for (p in listOf(a, b)) {
            history.record(p, data, now)
            log.record(p, SessionLog.SESSION, now, 80.0, hitLimit = false)
        }

        history.clear(a)
        log.clear(a)

        assertTrue(history.points(a).isEmpty())
        assertTrue(log.records(a).isEmpty())
        assertEquals(1, history.points(b).size)
        assertEquals(1, log.records(b).size)
    }
}
