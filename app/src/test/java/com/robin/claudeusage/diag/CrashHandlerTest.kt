package com.robin.claudeusage.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** CCRM-85 (Crash Capture): whatever the capture does, the crash always goes on to the previous handler. */
class CrashHandlerTest {

    private val calls = mutableListOf<String>()
    private var chained: Throwable? = null
    private val previous = Thread.UncaughtExceptionHandler { _, e -> calls += "previous"; chained = e }
    private val boom = IllegalStateException("x")

    @Test
    fun `chains after a capture that works`() {
        CrashHandler({ _, _ -> calls += "capture" }, previous).uncaughtException(Thread.currentThread(), boom)
        assertEquals(listOf("capture", "previous"), calls)
        assertSame(boom, chained)
    }

    @Test
    fun `chains after a capture that throws`() {
        CrashHandler({ _, _ -> calls += "capture"; throw java.io.IOException("disk full") }, previous)
            .uncaughtException(Thread.currentThread(), boom)
        assertEquals(listOf("capture", "previous"), calls)
        assertSame(boom, chained)
    }

    @Test
    fun `chains after a capture that runs out of memory`() {
        CrashHandler({ _, _ -> throw OutOfMemoryError() }, previous).uncaughtException(Thread.currentThread(), boom)
        assertEquals(listOf("previous"), calls)
    }

    @Test
    fun `with no previous handler it does what the runtime would`() {
        CrashHandler({ _, _ -> throw RuntimeException() }, null, fallback = { _, e -> calls += "fallback"; chained = e })
            .uncaughtException(Thread.currentThread(), boom)
        assertEquals(listOf("fallback"), calls)
        assertSame(boom, chained)
    }
}
