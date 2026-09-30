package com.robin.claudeusage.diag

import android.content.Context
import android.os.Process
import java.time.ZoneId
import kotlin.system.exitProcess

/**
 * CCRM-85 (Crash Capture): the default uncaught-exception handler. It writes a scrubbed
 * report as a bounded, best-effort step — the report is capped, and nothing here takes
 * the AppLog lock another thread may be holding — then hands the crash to the handler
 * that was there before, **in `finally`**, so the system dialog and the process death
 * are exactly what they were without it.
 */
class CrashHandler(
    private val capture: (Thread, Throwable) -> Unit,
    private val previous: Thread.UncaughtExceptionHandler?,
    /** Only when there was no previous handler: what the runtime would have done. */
    private val fallback: (Thread, Throwable) -> Unit = { _, _ ->
        Process.killProcess(Process.myPid())
        exitProcess(10)
    },
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            capture(thread, error)
        } catch (_: Throwable) {
            // A report that cannot be written must never change how the app dies.
        } finally {
            previous?.uncaughtException(thread, error) ?: fallback(thread, error)
        }
    }

    companion object {
        /**
         * Installed once, from `Application.attachBaseContext` — before any ContentProvider
         * (androidx.startup, WorkManager) initialises — so [app] is the Application itself:
         * its `applicationContext` is not set yet. A second call is a no-op.
         */
        fun install(app: Context) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            if (previous is CrashHandler) return
            // Worked out now, not at crash time.
            val meta = CrashStore.meta(app)
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(
                    capture = { t, e ->
                        CrashStore.writeLast(
                            app,
                            CrashReport.javaCrash(e, t.name, System.currentTimeMillis(), Process.myPid(), meta, ZoneId.systemDefault()),
                        )
                    },
                    previous = previous,
                ),
            )
        }
    }
}
