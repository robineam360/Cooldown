package com.robin.claudeusage.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.robin.claudeusage.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.ZoneId

/**
 * CCRM-85 (Crash Capture): the reports on disk, in private no-backup storage — nothing
 * here leaves the phone unless the user shares it.
 *
 * - The Java handler writes `last-crash.txt` ([writeLast]); the next launch's [ingest]
 *   files it as a report, then adds whatever [ExitReasons] finds.
 * - A report is a file `r-<at>-<pid>-<kind>-<t|n>.txt` (`t` when it carries a trace).
 *   **Seen** is the watermark: an exit the app has processed is never looked at again.
 *   **Dismissed** is the user's: Not now, or a share that reached an app, renames the
 *   file to `d-…`, so it leaves the card but stays in Diagnostics until it expires.
 * - Reports expire after [KEEP_DAYS]; at most [KEEP_COUNT] are kept.
 */
object CrashStore {

    private const val DIR = "crash"
    internal const val LAST = "last-crash.txt"
    private const val WATERMARK = "watermark"
    internal const val KEEP_DAYS = 30L
    internal const val KEEP_COUNT = 8
    private const val MAX_TRACE_BYTES = 1 shl 20
    /** A backstop over KEEP_COUNT × CrashReport.MAX_CHARS; see there for the binder budget. */
    private const val MAX_SHARE_CHARS = 70_000
    private const val SEPARATOR = "\n\n----------------------------------------\n\n"

    data class Report(
        val name: String,
        val at: Long,
        val pid: Int,
        val kind: CrashReport.Kind,
        val hasTrace: Boolean,
        val dismissed: Boolean,
    )

    private val NAME = Regex("""([rd])-(\d+)-(\d+)-([a-z]+)-([tn])\.txt""")

    private val _changes = MutableStateFlow(0)
    /** Bumped on every change, so the cards re-read without polling. */
    val changes: StateFlow<Int> = _changes

    private val lock = Any()
    private var ingested = false

    fun dir(context: Context): File = File(context.noBackupFilesDir, DIR)

    fun meta(context: Context): CrashReport.Meta = CrashReport.Meta(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        flavor = BuildConfig.FLAVOR,
        sdk = Build.VERSION.SDK_INT,
        release = Build.VERSION.RELEASE ?: "?",
        manufacturer = Build.MANUFACTURER ?: "?",
        model = Build.MODEL ?: "?",
    )

    /**
     * The Java handler's one write, at crash time: no lock, no log, no parsing — a temp
     * file renamed over `last-crash.txt`, so a half-written report is never read.
     */
    fun writeLast(context: Context, text: String) {
        val d = dir(context)
        d.mkdirs()
        val tmp = File(d, "$LAST.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(File(d, LAST))) tmp.delete()
    }

    internal fun parse(name: String): Report? {
        val m = NAME.matchEntire(name) ?: return null
        val (state, at, pid, kind, trace) = m.destructured
        return Report(
            name, at.toLongOrNull() ?: return null, pid.toIntOrNull() ?: return null,
            CrashReport.Kind.fromKey(kind) ?: return null, trace == "t", state == "d",
        )
    }

    private fun fileName(at: Long, pid: Int, kind: CrashReport.Kind, hasTrace: Boolean) =
        "r-$at-$pid-${kind.key}-${if (hasTrace) "t" else "n"}.txt"

    /** Newest first. */
    fun reports(context: Context): List<Report> =
        (dir(context).list() ?: emptyArray()).mapNotNull { parse(it) }.sortedByDescending { it.at }

    fun unseen(context: Context): List<Report> = reports(context).filterNot { it.dismissed }

    /** Once per process: the first screen that needs the reports files them. */
    fun ingestOnce(context: Context) {
        synchronized(lock) {
            if (ingested) return
            ingested = true
            ingest(context.applicationContext, System.currentTimeMillis())
        }
    }

    /**
     * Files `last-crash.txt` as a report: one small read and a rename. The Application
     * calls it on every process start, so a background crash loop keeps every Java trace
     * instead of each crash overwriting the last one before a screen ever opens.
     */
    fun fileLast(context: Context): Boolean {
        val d = dir(context)
        val last = File(d, LAST)
        if (!last.exists()) return false
        return try {
            val text = last.readText()
            val at = CrashReport.parseAt(text) ?: last.lastModified()
            val pid = CrashReport.parsePid(text) ?: 0
            val target = File(d, fileName(at, pid, CrashReport.Kind.CRASH, CrashReport.hasTrace(text)))
            last.renameTo(target) || last.delete()
        } catch (_: Exception) {
            last.delete()
            false
        }
    }

    /** Tests only: [ingestOnce] without the once-per-process guard. */
    internal fun ingestForTest(context: Context, now: Long) = synchronized(lock) { ingest(context, now) }

    private fun ingest(context: Context, now: Long) {
        val d = dir(context)
        d.mkdirs()
        val meta = meta(context)
        val zone = ZoneId.systemDefault()

        // 1. The Java handler's capture from the last process, if the Application has not
        //    filed it already.
        if (fileLast(context)) {
            AppLog.log(context, AppLog.Level.INFO, "crash", event = "captured an uncaught exception")
        }

        // 2. The exit reasons since the watermark.
        try {
            val am = context.getSystemService(ActivityManager::class.java)
            val infos = am?.getHistoricalProcessExitReasons(context.packageName, 0, 0).orEmpty()
            val stored = readWatermark(context)
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            val watermark = ExitReasons.initialWatermark(stored, installed)
            if (stored == null) writeWatermark(context, watermark)
            val byEntry = infos.associateBy { ExitReasons.Entry(it.timestamp, it.pid, it.reason) }
            val captured = reports(context)
                .filter { it.kind == CrashReport.Kind.CRASH && it.hasTrace }
                .map { ExitReasons.Captured(it.at, it.pid) }
            val actions = ExitReasons.plan(byEntry.keys.toList(), watermark, captured)
            ExitReasons.drain(
                actions, watermark,
                process = { a ->
                    val e = a.entry
                    when (a) {
                        is ExitReasons.Action.Report -> {
                            val trace = byEntry[e]?.let { readTrace(it, a.kind) }
                            val text = CrashReport.exitReport(
                                a.kind, ExitReasons.reasonName(e.reason), e.timestamp, e.pid, trace, meta, zone,
                            )
                            writeAtomic(File(d, fileName(e.timestamp, e.pid, a.kind, trace != null)), text)
                            AppLog.log(context, AppLog.Level.INFO, "crash", event = "exit: ${ExitReasons.reasonName(e.reason)} → report")
                        }
                        is ExitReasons.Action.Duplicate -> Unit
                        is ExitReasons.Action.LogOnly ->
                            AppLog.log(context, AppLog.Level.INFO, "crash", event = "exit: ${ExitReasons.reasonName(e.reason)}")
                    }
                },
                save = { writeWatermark(context, it) },
            )
        } catch (e: Exception) {
            AppLog.log(context, AppLog.Level.WARN, "crash", event = "exit reasons unreadable (${e.javaClass.simpleName})")
        }

        // 3. Retention.
        val all = reports(context)
        all.forEachIndexed { i, r ->
            if (i >= KEEP_COUNT || now - r.at > KEEP_DAYS * 86_400_000L) File(d, r.name).delete()
        }
        _changes.value++
    }

    /** A missing or unreadable trace is tolerated: null, and the report says none was kept. */
    private fun readTrace(info: ApplicationExitInfo, kind: CrashReport.Kind): String? = try {
        info.traceInputStream?.use { input ->
            // InputStream.readNBytes is API 33; this app runs from 31.
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (out.size() < MAX_TRACE_BYTES) {
                val n = input.read(buf, 0, minOf(buf.size, MAX_TRACE_BYTES - out.size()))
                if (n < 0) break
                out.write(buf, 0, n)
            }
            val bytes = out.toByteArray()
            val cut = bytes.size >= MAX_TRACE_BYTES
            when (kind) {
                CrashReport.Kind.ANR -> CrashReport.anrTrace(CrashReport.dropPartialLine(bytes.toString(Charsets.UTF_8), cut))
                CrashReport.Kind.NATIVE -> CrashReport.nativeTrace(bytes)
                CrashReport.Kind.CRASH -> null
            }
        }?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun readWatermark(context: Context): Long? =
        try { File(dir(context), WATERMARK).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() } catch (_: Exception) { null }

    private fun writeWatermark(context: Context, value: Long) = writeAtomic(File(dir(context), WATERMARK), value.toString())

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw java.io.IOException("rename")
        }
    }

    /** Not now, or a share that reached an app: off the card, still in Diagnostics. */
    fun dismiss(context: Context, names: Collection<String>) {
        val d = dir(context)
        for (n in names) if (n.startsWith("r-")) File(d, n).renameTo(File(d, "d-" + n.removePrefix("r-")))
        _changes.value++
    }

    fun deleteAll(context: Context) {
        for (r in reports(context)) File(dir(context), r.name).delete()
        _changes.value++
    }

    /** Every report in [reports], newest first, through the one scrubber again. */
    fun shareText(context: Context, reports: List<Report>): String {
        val d = dir(context)
        val body = reports.sortedByDescending { it.at }.mapNotNull { r ->
            try { CrashReport.scrub(File(d, r.name).readText()) } catch (_: Exception) { null }
        }.joinToString(SEPARATOR)
        return if (body.length <= MAX_SHARE_CHARS) body else body.take(MAX_SHARE_CHARS) + "\n… (cut)\n"
    }

    /**
     * Opens the share sheet for [reports]. Never dismisses anything itself and never throws:
     * a launch that fails (a TransactionTooLargeException, no activity) returns false and
     * leaves every report on the card.
     */
    fun share(context: Context, reports: List<Report>): Boolean = try {
        val chooser = shareIntent(context, reports)
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
    } catch (_: RuntimeException) {
        false
    }

    /**
     * The share sheet, plus the chooser's callback. **Dismissal is that callback and nothing
     * else** (Astra 2026-09-30, C4-3; wireframe rev B: "The card goes away only once you pick
     * an app in the sheet"). Android reports the pick, not whether the receiving app then
     * delivered anything, so "shared" in this app means "an app was picked". Backing out of
     * the sheet sends no callback, so the card stays.
     */
    fun shareIntent(context: Context, reports: List<Report>): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, CrashCopy.shareSubject(BuildConfig.VERSION_NAME))
            .putExtra(Intent.EXTRA_TEXT, shareText(context, reports))
        val picked = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, CrashShareReceiver::class.java)
                .putExtra(CrashShareReceiver.EXTRA_REPORTS, reports.map { it.name }.toTypedArray()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Intent.createChooser(send, "Share crash report", picked.intentSender)
    }
}

/** The chooser's callback: an app was picked, so the shared reports leave the card. */
class CrashShareReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val names = intent.getStringArrayExtra(EXTRA_REPORTS) ?: return
        CrashStore.dismiss(context, names.toList())
    }

    companion object {
        const val EXTRA_REPORTS = "reports"
    }
}
