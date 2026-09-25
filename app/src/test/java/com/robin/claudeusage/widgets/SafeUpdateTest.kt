package com.robin.claudeusage.widgets

import android.content.Context
import android.os.DeadObjectException
import android.os.TransactionTooLargeException
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.robin.claudeusage.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * R10's fallback, exercised through [SafeUpdate] itself with an injected updater that
 * throws each of the three shapes, then one that throws on both attempts.
 */
@RunWith(RobolectricTestRunner::class)
class SafeUpdateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val full = RemoteViews(context.packageName, R.layout.widget_ring)
    private val one = RemoteViews(context.packageName, R.layout.widget_ring)

    /** Throws [first] on the full face, [second] (if any) on the fallback; records what landed. */
    private class Host(val first: Exception?, val second: Exception? = null) : SafeUpdate.Updater {
        val landed = mutableListOf<RemoteViews>()
        var calls = 0
        override fun update(appWidgetId: Int, views: RemoteViews) {
            calls++
            val e = if (calls == 1) first else second
            if (e != null) throw e
            landed += views
        }
    }

    private fun run(host: Host, logs: MutableList<String> = mutableListOf()) =
        SafeUpdate.update(host, 7, { full }, { one }, { logs += it })

    @Test
    fun aGoodUpdateLandsTheSizeMap() {
        val host = Host(null)
        assertEquals(SafeUpdate.Outcome.UPDATED, run(host))
        assertSame(full, host.landed.single())
    }

    @Test
    fun eachOfTheThreeShapesFallsBackToOneSize() {
        val shapes = listOf(
            IllegalArgumentException("RemoteViews for widget update exceeds maximum bitmap memory usage"),
            TransactionTooLargeException("data parcel size 1200000 bytes"),
            RuntimeException("system server dead?", DeadObjectException()),
            RuntimeException("wrapped twice", IllegalStateException(TransactionTooLargeException())),
        )
        for (e in shapes) {
            val host = Host(e)
            val logs = mutableListOf<String>()
            assertEquals(e.toString(), SafeUpdate.Outcome.FELL_BACK, run(host, logs))
            assertSame(one, host.landed.single())
            assertEquals(1, logs.size)
        }
    }

    @Test
    fun bothAttemptsFailing_isContainedAndLoggedTwice_noRetryLoop() {
        val host = Host(IllegalArgumentException("cap"), TransactionTooLargeException("still too big"))
        val logs = mutableListOf<String>()
        assertEquals(SafeUpdate.Outcome.FAILED, run(host, logs))
        assertEquals(2, host.calls)
        assertEquals(emptyList<RemoteViews>(), host.landed)
        assertEquals(2, logs.size)
    }

    @Test
    fun anUnrelatedFailure_isContainedWithoutAFallback() {
        val host = Host(IllegalStateException("not a size problem"))
        assertEquals(SafeUpdate.Outcome.FAILED, run(host))
        assertEquals(1, host.calls)
        // A RuntimeException with no Binder cause is not a host limit either.
        assertEquals(false, SafeUpdate.isHostLimit(RuntimeException(IllegalStateException())))
    }

    @Test
    fun aFaceThatFailsToBuild_isContainedToo() {
        val logs = mutableListOf<String>()
        val out = SafeUpdate.update(Host(null), 7, { error("render blew up") }, { one }, { logs += it })
        assertEquals(SafeUpdate.Outcome.FAILED, out)
        assertEquals(1, logs.size)
    }
}
