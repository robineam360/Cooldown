package com.robin.claudeusage.diag

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.IdentityHashMap

/**
 * CCRM-85 (Crash Capture): the text of one crash report, built without Android so every
 * rule here is pinned by `CrashReportTest`.
 *
 * A report keeps the exception's class, its frames up to a cap, the cause classes, and
 * the app version, Android version and phone model. **Exception messages are left out**,
 * always: an OAuth token needs no `Bearer` in front of it to turn up in free text. Every
 * field that is kept still goes through [scrub], the one scrubber, and so does every
 * share ([com.robin.claudeusage.diag.CrashStore.shareText]).
 */
object CrashReport {

    enum class Kind(val key: String) {
        /** The Java handler's own capture, or `REASON_CRASH` from the exit reasons. */
        CRASH("crash"),
        ANR("anr"),
        NATIVE("native");

        companion object {
            fun fromKey(key: String): Kind? = entries.firstOrNull { it.key == key }
        }
    }

    data class Meta(
        val versionName: String,
        val versionCode: Long,
        val flavor: String,
        val sdk: Int,
        val release: String,
        val manufacturer: String,
        val model: String,
    )

    internal const val MAX_FRAMES = 32
    internal const val MAX_CAUSE_FRAMES = 12
    internal const val MAX_CAUSES = 6
    internal const val MAX_NATIVE_FRAMES = 32
    /**
     * Eight of these (CrashStore.KEEP_COUNT) must fit one share intent. EXTRA_TEXT is UTF-16
     * in the parcel and ACTION_SEND copies it into ClipData too, so 8 × 8,000 chars is about
     * 256 KB — well under the binder limit that a TransactionTooLargeException enforces.
     * The ANR main thread, a native backtrace and a Java trace with its causes all fit.
     */
    internal const val MAX_CHARS = 8_000

    const val NO_TRACE = "none kept"

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx")

    /** The Java handler's report: the class and frames of [t] and its causes, never a message. */
    fun javaCrash(t: Throwable, threadName: String, at: Long, pid: Int, meta: Meta, zone: ZoneId): String =
        build(Kind.CRASH, "uncaught exception", at, pid, meta, zone, threadName, throwableTrace(t))

    /** A report made from an exit reason on the next launch. [trace] null means Android kept none. */
    fun exitReport(kind: Kind, reason: String, at: Long, pid: Int, trace: String?, meta: Meta, zone: ZoneId): String =
        build(kind, reason, at, pid, meta, zone, null, trace)

    private fun build(
        kind: Kind, reason: String, at: Long, pid: Int, meta: Meta, zone: ZoneId,
        thread: String?, trace: String?,
    ): String {
        val text = buildString {
            append("Cooldown crash report\n")
            append("Kind: ").append(kind.key).append('\n')
            append("Reason: ").append(reason).append('\n')
            append("When: ").append(stamp.format(Instant.ofEpochMilli(at).atZone(zone))).append('\n')
            append("At: ").append(at).append('\n')
            append("Pid: ").append(pid).append('\n')
            append("App: ").append(meta.versionName).append(" (").append(meta.versionCode).append(") ")
                .append(meta.flavor).append('\n')
            append("Android: ").append(meta.release).append(" (SDK ").append(meta.sdk).append(")\n")
            append("Device: ").append(meta.manufacturer).append(' ').append(meta.model).append('\n')
            if (thread != null) append("Thread: ").append(thread).append('\n')
            append("Trace: ").append(if (trace.isNullOrBlank()) NO_TRACE else "\n$trace").append('\n')
        }
        return cap(scrub(text))
    }

    /** Reads back the `At:` line, so a report file can be renamed after its own time. */
    fun parseAt(report: String): Long? =
        Regex("""(?m)^At: (\d+)$""").find(report)?.groupValues?.get(1)?.toLongOrNull()

    fun parsePid(report: String): Int? =
        Regex("""(?m)^Pid: (\d+)$""").find(report)?.groupValues?.get(1)?.toIntOrNull()

    fun hasTrace(report: String): Boolean = !report.contains("\nTrace: $NO_TRACE")

    internal fun cap(text: String): String =
        if (text.length <= MAX_CHARS) text else text.take(MAX_CHARS) + "\n… (report cut at $MAX_CHARS characters)\n"

    /** Classes and frames only — `Throwable.toString()` would bring the message along. */
    internal fun throwableTrace(t: Throwable): String = buildString {
        val seen = IdentityHashMap<Throwable, Unit>()
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < MAX_CAUSES && seen.put(cur, Unit) == null) {
            if (depth > 0) append("Caused by: ")
            append(cur.javaClass.name).append('\n')
            val cap = if (depth == 0) MAX_FRAMES else MAX_CAUSE_FRAMES
            val frames = cur.stackTrace
            for (f in frames.take(cap)) {
                append("    at ").append(f.className).append('.').append(f.methodName)
                    .append('(').append(f.fileName ?: "Unknown Source")
                if (f.lineNumber >= 0) append(':').append(f.lineNumber)
                append(")\n")
            }
            if (frames.size > cap) append("    … ").append(frames.size - cap).append(" more\n")
            for (s in cur.suppressed.take(3)) append("    Suppressed: ").append(s.javaClass.name).append('\n')
            cur = cur.cause
            depth++
        }
    }

    /**
     * A trace read up to a byte cap may end part-way through a line, and so part-way through
     * a secret the scrubber would no longer recognise (Astra 2026-09-30, C4-2). A cut trace
     * therefore loses its last, partial line before anything scrubs it.
     */
    fun dropPartialLine(text: String, cut: Boolean): String {
        if (!cut) return text
        val nl = text.lastIndexOf('\n')
        return (if (nl >= 0) text.substring(0, nl) else "") + "\n… (trace cut at the read limit)"
    }

    /**
     * An ANR dump is every thread in the process; the main thread is the one that
     * matters, so it goes first and the rest follows until the report's cap.
     */
    fun anrTrace(dump: String): String {
        val lines = dump.lines()
        val start = lines.indexOfFirst { it.startsWith("\"main\"") }
        if (start < 0) return dump
        val end = (start + 1 until lines.size).firstOrNull { lines[it].isBlank() } ?: lines.size
        val main = lines.subList(start, end).joinToString("\n")
        val rest = (lines.subList(0, start) + lines.subList(end, lines.size)).joinToString("\n")
        return "$main\n\n--- the rest of the dump ---\n$rest"
    }

    /**
     * A native crash's tombstone arrives as protobuf (`tombstone.proto`). Only the signal
     * and the crashing thread's frames are read out; the abort message, the log buffers,
     * memory and registers are skipped on purpose — they are the native side's free text.
     * Anything unreadable gives null, and the report says no trace was kept.
     */
    fun nativeTrace(tombstone: ByteArray): String? = try {
        Tombstone.read(tombstone)
    } catch (_: Exception) {
        null
    }

    // --- the one scrubber ---

    private const val TOKEN = "[token]"

    private val KEYED = Regex(
        """(?i)\b(access_token|refresh_token|id_token|code_verifier|authorization|api[_-]?key|password|secret|token)(\s*["']?\s*[:=]\s*["']?)[^\s"'&,;}\]]+""",
    )
    private val BEARER = Regex("""(?i)\bbearer\s+[^\s"',;]+""")
    private val SK = Regex("""\bsk-[A-Za-z0-9_\-]{8,}""")
    private val JWT = Regex("""\beyJ[A-Za-z0-9_\-]{8,}(\.[A-Za-z0-9_\-]+){1,2}""")
    private val EMAIL = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")
    /** A 32+ run of token alphabet with at least one digit and one letter — no class name looks like that. */
    private val LONG_RUN = Regex(
        """(?<![A-Za-z0-9_\-])(?=[A-Za-z0-9_\-]*\d)(?=[A-Za-z0-9_\-]*[A-Za-z])[A-Za-z0-9_\-]{32,}(?![A-Za-z0-9_\-])""",
    )

    /** Strips tokens (with or without `Bearer`), token-shaped runs and email addresses. */
    fun scrub(text: String): String {
        var s = KEYED.replace(text) { "${it.groupValues[1]}${it.groupValues[2]}[redacted]" }
        s = BEARER.replace(s, "Bearer $TOKEN")
        s = SK.replace(s, TOKEN)
        s = JWT.replace(s, TOKEN)
        s = EMAIL.replace(s, "[email]")
        s = LONG_RUN.replace(s, TOKEN)
        return s
    }

    /** The few `tombstone.proto` fields a report keeps, read with a minimal wire-format reader. */
    private object Tombstone {
        private const val F_TID = 6
        private const val F_SIGNAL = 10
        private const val F_THREADS = 16
        private const val SIG_NAME = 2
        private const val SIG_CODE_NAME = 4
        private const val THREAD_NAME = 2
        private const val THREAD_BACKTRACE = 4
        private const val FRAME_REL_PC = 1
        private const val FRAME_FUNCTION = 4
        private const val FRAME_OFFSET = 5
        private const val FRAME_FILE = 6

        private class Field(val number: Int, val varint: Long, val bytes: ByteArray?)

        fun read(b: ByteArray): String? {
            // Lenient at the top level only: a tombstone cut at the read cap still has the
            // signal and the threads, which come before the memory maps and log buffers.
            val top = fields(b, lenient = true)
            val tid = top.firstOrNull { it.number == F_TID }?.varint
            val signal = top.firstOrNull { it.number == F_SIGNAL }?.bytes?.let { fields(it) }
            val threads = top.filter { it.number == F_THREADS }.mapNotNull { it.bytes }.map { fields(it) }
            // map<uint32, Thread>: each entry is {1: key, 2: Thread}.
            val crashing = threads.firstOrNull { e -> e.firstOrNull { it.number == 1 }?.varint == tid }
                ?: threads.firstOrNull()
            val thread = crashing?.firstOrNull { it.number == 2 }?.bytes?.let { fields(it) }
            if (signal == null && thread == null) return null
            return buildString {
                if (signal != null) {
                    append("Signal: ").append(string(signal, SIG_NAME) ?: "?")
                    string(signal, SIG_CODE_NAME)?.let { append(" (").append(it).append(')') }
                    append('\n')
                }
                if (thread != null) {
                    string(thread, THREAD_NAME)?.let { append("Thread: ").append(it).append('\n') }
                    val frames = thread.filter { it.number == THREAD_BACKTRACE }.mapNotNull { it.bytes }
                    frames.take(MAX_NATIVE_FRAMES).forEachIndexed { i, fb ->
                        val f = fields(fb)
                        val pc = f.firstOrNull { it.number == FRAME_REL_PC }?.varint ?: 0L
                        append("#").append(i.toString().padStart(2, '0'))
                            .append(" pc ").append(java.lang.Long.toHexString(pc).padStart(16, '0'))
                            .append("  ").append(string(f, FRAME_FILE) ?: "?")
                        string(f, FRAME_FUNCTION)?.takeIf { it.isNotEmpty() }?.let { fn ->
                            val off = f.firstOrNull { it.number == FRAME_OFFSET }?.varint ?: 0L
                            append(" (").append(fn).append('+').append(off).append(')')
                        }
                        append('\n')
                    }
                    if (frames.size > MAX_NATIVE_FRAMES) append("… ").append(frames.size - MAX_NATIVE_FRAMES).append(" more\n")
                }
            }
        }

        /** One field at [at]: the field (null for a skipped fixed-width one) and where the next begins. */
        private fun readOne(b: ByteArray, at: Int): Pair<Field?, Int> {
            var pos = at
            fun varint(): Long {
                var shift = 0
                var result = 0L
                while (true) {
                    require(pos < b.size && shift < 64)
                    val byte = b[pos++].toInt()
                    result = result or ((byte and 0x7f).toLong() shl shift)
                    if (byte and 0x80 == 0) return result
                    shift += 7
                }
            }
            val key = varint()
            val number = (key ushr 3).toInt()
            return when ((key and 7).toInt()) {
                0 -> Field(number, varint(), null) to pos
                1 -> { require(pos + 8 <= b.size); null to pos + 8 }
                2 -> {
                    val len = varint().toInt()
                    require(len >= 0 && pos + len <= b.size)
                    Field(number, 0L, b.copyOfRange(pos, pos + len)) to pos + len
                }
                5 -> { require(pos + 4 <= b.size); null to pos + 4 }
                else -> throw IllegalArgumentException("wire type")
            }
        }

        private fun string(fields: List<Field>, n: Int): String? =
            fields.firstOrNull { it.number == n }?.bytes?.toString(Charsets.UTF_8)

        private fun fields(b: ByteArray, lenient: Boolean = false): List<Field> {
            val out = mutableListOf<Field>()
            var pos = 0
            while (pos < b.size) {
                val (f, next) = try {
                    readOne(b, pos)
                } catch (e: IllegalArgumentException) {
                    if (lenient) break else throw e
                }
                f?.let { out += it }
                pos = next
            }
            return out
        }
    }
}

/**
 * Thrown by the Debug "Crash now" card. Its message carries a fake token and an email on
 * purpose: the Step 7 device pass checks that neither reaches the saved or shared report.
 */
class CrashNowCanary : RuntimeException("Crash now canary: Bearer $TOKEN and $EMAIL") {
    companion object {
        const val TOKEN = "sk-ant-oat01-CANARYcanary0123456789abcdefghijklmnop"
        const val EMAIL = "canary.tester@example.com"
    }
}
