package com.robin.claudeusage.diag

import android.app.ApplicationExitInfo.REASON_ANR
import android.app.ApplicationExitInfo.REASON_CRASH
import android.app.ApplicationExitInfo.REASON_CRASH_NATIVE
import android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED
import android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
import android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
import android.app.ApplicationExitInfo.REASON_LOW_MEMORY
import android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE
import android.app.ApplicationExitInfo.REASON_SIGNALED
import android.app.ApplicationExitInfo.REASON_USER_REQUESTED
import android.app.ApplicationExitInfo.REASON_USER_STOPPED
import com.robin.claudeusage.diag.ExitReasons.Action
import com.robin.claudeusage.diag.ExitReasons.Captured
import com.robin.claudeusage.diag.ExitReasons.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CCRM-85 (Crash Capture): the whitelist, the duplicate rule and the watermark. */
class ExitReasonsTest {

    @Test
    fun `only crash, native crash and ANR make a report`() {
        assertEquals(CrashReport.Kind.CRASH, ExitReasons.kindOf(REASON_CRASH))
        assertEquals(CrashReport.Kind.NATIVE, ExitReasons.kindOf(REASON_CRASH_NATIVE))
        assertEquals(CrashReport.Kind.ANR, ExitReasons.kindOf(REASON_ANR))
        for (r in listOf(
            REASON_LOW_MEMORY, REASON_USER_STOPPED, REASON_USER_REQUESTED, REASON_EXCESSIVE_RESOURCE_USAGE,
            REASON_INITIALIZATION_FAILURE, REASON_SIGNALED, REASON_PERMISSION_CHANGE, REASON_DEPENDENCY_DIED,
        )) {
            assertEquals(null, ExitReasons.kindOf(r))
            assertTrue(ExitReasons.plan(listOf(Entry(10, 1, r)), 0, emptyList()).single() is Action.LogOnly)
        }
    }

    @Test
    fun `the watermark starts at the install or upgrade time`() {
        assertEquals(500L, ExitReasons.initialWatermark(null, 500L))
        assertEquals(900L, ExitReasons.initialWatermark(900L, 500L))
        // A v1.8 crash from before the upgrade is not reported.
        val plan = ExitReasons.plan(listOf(Entry(400, 1, REASON_CRASH), Entry(600, 2, REASON_ANR)), 500, emptyList())
        assertEquals(listOf(600L), plan.map { it.entry.timestamp })
    }

    @Test
    fun `entries go oldest first`() {
        val plan = ExitReasons.plan(listOf(Entry(30, 3, REASON_ANR), Entry(10, 1, REASON_ANR), Entry(20, 2, REASON_ANR)), 0, emptyList())
        assertEquals(listOf(10L, 20L, 30L), plan.map { it.entry.timestamp })
    }

    @Test
    fun `a crash the Java handler caught is a duplicate`() {
        val captured = listOf(Captured(at = 1_000, pid = 77))
        val plan = ExitReasons.plan(
            listOf(Entry(1_050, 77, REASON_CRASH), Entry(1_060, 78, REASON_CRASH), Entry(9_000_000, 77, REASON_CRASH)),
            0, captured,
        )
        assertTrue(plan[0] is Action.Duplicate)
        assertTrue("another process", plan[1] is Action.Report)
        assertTrue("same pid, much later: a reused pid", plan[2] is Action.Report)
        // An ANR in the same process is never a duplicate of a Java capture.
        assertTrue(ExitReasons.plan(listOf(Entry(1_050, 77, REASON_ANR)), 0, captured).single() is Action.Report)
    }

    @Test
    fun `the watermark moves only past processed entries`() {
        val plan = ExitReasons.plan(listOf(Entry(10, 1, REASON_ANR), Entry(20, 2, REASON_ANR), Entry(30, 3, REASON_ANR)), 0, emptyList())
        val saved = mutableListOf<Long>()
        val end = ExitReasons.drain(plan, 0, process = { if (it.entry.timestamp == 20L) error("disk") }, save = { saved += it })
        assertEquals(10L, end)
        assertEquals(listOf(10L), saved)
        // The next launch picks up from there.
        assertEquals(listOf(20L, 30L), ExitReasons.plan(plan.map { it.entry }, end, emptyList()).map { it.entry.timestamp })
    }

    @Test
    fun `a clean drain ends on the newest entry`() {
        val plan = ExitReasons.plan(listOf(Entry(10, 1, REASON_LOW_MEMORY), Entry(20, 2, REASON_ANR)), 0, emptyList())
        assertEquals(20L, ExitReasons.drain(plan, 0, process = {}, save = {}))
    }
}
