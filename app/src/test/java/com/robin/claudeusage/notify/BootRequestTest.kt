package com.robin.claudeusage.notify

import android.annotation.SuppressLint
import androidx.work.OutOfQuotaPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CCBG-52 (Boot Pin Delay): the post-boot restart is expedited, undelayed, and degrades to plain work. */
@SuppressLint("RestrictedApi")
class BootRequestTest {

    @Test
    fun bootRequest_isExpeditedWithNoDelayAndFallsBackOutOfQuota() {
        val spec = PinnedBootReceiver.bootRequest().workSpec
        assertTrue(spec.expedited)
        assertEquals(0L, spec.initialDelay)
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, spec.outOfQuotaPolicy)
        assertEquals(PinBootWorker::class.java.name, spec.workerClassName)
    }
}
