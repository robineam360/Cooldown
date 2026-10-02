package com.robin.claudeusage.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.robin.claudeusage.data.UsageCache

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
 * restriction — WorkManager's executor isn't a BOOT_COMPLETED broadcast context.
 *
 * The one-shot is **expedited** (CCBG-52 (Boot Pin Delay)). A plain request with
 * a short initial delay sat in One UI's JobScheduler for 5 min 45 s after a
 * reboot on the Fold 7, every constraint met. An expedited job is run as soon
 * as the scheduler can; expedited work cannot carry a delay, so there is none,
 * and out of expedited quota it falls back to an ordinary request.
 */
class PinnedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!UsageCache(context).pinnedEnabled()) return

        WorkManager.getInstance(context).enqueue(bootRequest())
    }

    companion object {
        fun bootRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<PinBootWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
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
