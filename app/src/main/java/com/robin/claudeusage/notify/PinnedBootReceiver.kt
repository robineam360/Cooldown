package com.robin.claudeusage.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.robin.claudeusage.data.UsageCache
import java.util.concurrent.TimeUnit

/**
 * Restarts the pinned notification's foreground service after a reboot, if the
 * pin was on before it (CCRM-67 (Pin Service)) — otherwise the pin would stay
 * dark until the user next opened the app themselves.
 *
 * This deliberately does **not** call [PinnedService.ensureRunning] straight
 * from [onReceive]. Android 15 (API 35) narrowed which foreground-service types
 * a BOOT_COMPLETED receiver is allowed to start while the app is otherwise in
 * the background, to a short, specific list (shortService, systemExempted, and
 * a few hardware-tied types) — and `specialUse` is not on it. Calling
 * `startForegroundService` for a `specialUse` service straight out of this
 * receiver would throw `ForegroundServiceStartNotAllowedException` on a fresh
 * boot on API 35/36, the two platform versions this app actually ships on.
 * Handing the restart to a WorkManager one-shot instead sidesteps that specific
 * restriction — WorkManager's executor isn't a BOOT_COMPLETED broadcast context
 * — at the cost of the service coming up a handful of seconds after boot rather
 * than immediately, which is a trade this feature can afford.
 */
class PinnedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!UsageCache(context).pinnedEnabled()) return

        val request = OneTimeWorkRequestBuilder<PinBootWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}

/** The actual restart, off a WorkManager thread rather than the boot broadcast — see [PinnedBootReceiver]. */
class PinBootWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (UsageCache(applicationContext).pinnedEnabled()) {
            PinnedService.ensureRunning(applicationContext)
        }
        return Result.success()
    }
}
