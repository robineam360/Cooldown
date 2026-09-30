package com.robin.claudeusage

import android.app.Application
import android.content.Context
import com.robin.claudeusage.diag.CrashHandler
import com.robin.claudeusage.diag.CrashStore

/**
 * The app's Application class exists for CCRM-85 (Crash Capture): the uncaught-exception
 * handler goes in before anything else runs, ContentProviders included, and each process
 * start files the last process's crash so a background crash loop loses no trace.
 */
class CooldownApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        try {
            CrashHandler.install(this)
        } catch (_: Exception) {
            // Without the handler the app still runs exactly as it did before v1.9.
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            CrashStore.fileLast(this)
        } catch (_: Exception) {
            // Never a launch crash from the crash reporter.
        }
    }
}
