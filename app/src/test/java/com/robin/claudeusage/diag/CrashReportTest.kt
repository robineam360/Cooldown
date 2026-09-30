package com.robin.claudeusage.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.ZoneId

/**
 * CCRM-85 (Crash Capture): the one scrubber, and the rule that a report keeps no exception
 * message. Canaries are planted in every field a report keeps — the thread name, the
 * device fields, a sample ANR dump and a sample native tombstone — as well as in the
 * messages a report drops.
 */
class CrashReportTest {

    private val token = "sk-ant-oat01-Zx9QwErTy0123456789abcdefGHIJKL"
    private val refresh = "sk-ant-ort01-AbCdEf0123456789ghijklmnOPQRST"
    private val bareToken = "Xk3v9QmZ2pL8wR4tY7uB1nC6dF0gH5jK" // 32, no prefix, no Bearer
    private val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJlMTIzNDU2"
    private val email = "robin.test+cooldown@example.co.uk"
    private val canaries = listOf(token, refresh, bareToken, jwt, email, "CANARY")

    private val zone = ZoneId.of("Asia/Kolkata")
    private val meta = CrashReport.Meta("1.9", 25, "github", 36, "16", "samsung", "SM-F966B")

    private fun assertClean(text: String) {
        for (c in canaries) assertFalse("leaked $c in:\n$text", text.contains(c))
    }

    // --- the scrubber ---

    @Test
    fun `strips a token after Bearer`() {
        val out = CrashReport.scrub("Authorization: Bearer $token")
        assertClean(out)
    }

    @Test
    fun `strips a token with no Bearer in front`() {
        assertClean(CrashReport.scrub("failed with $token while refreshing"))
        assertClean(CrashReport.scrub("refresh $refresh"))
        assertClean(CrashReport.scrub("opaque $bareToken here"))
        assertClean(CrashReport.scrub("jwt $jwt"))
    }

    @Test
    fun `strips keyed secrets and emails`() {
        assertClean(CrashReport.scrub("""{"access_token":"abc.def-CANARY","refresh_token": "CANARYx"}"""))
        assertClean(CrashReport.scrub("code_verifier=CANARYverifier&state=1"))
        assertClean(CrashReport.scrub("signed in as $email"))
    }

    @Test
    fun `leaves ordinary frames alone`() {
        val frame = "    at androidx.compose.runtime.ComposerImpl.rememberLauncherForActivityResult(Composer.kt:1234)"
        assertEquals(frame, CrashReport.scrub(frame))
    }

    // --- the Java handler's report ---

    private fun thrown(): Throwable {
        val cause = IllegalStateException("cause message $email CANARY")
        val top = RuntimeException("top message Bearer $token", cause)
        top.addSuppressed(IllegalArgumentException("suppressed $refresh"))
        return top
    }

    @Test
    fun `a java crash keeps classes and frames but never a message`() {
        val report = CrashReport.javaCrash(thrown(), "main", 1_000L, 42, meta, zone)
        assertClean(report)
        assertTrue(report.contains("java.lang.RuntimeException"))
        assertTrue(report.contains("Caused by: java.lang.IllegalStateException"))
        assertTrue(report.contains("Suppressed: java.lang.IllegalArgumentException"))
        assertTrue(report.contains("    at com.robin.claudeusage.diag.CrashReportTest.thrown(CrashReportTest.kt:"))
        assertTrue(report.contains("App: 1.9 (25) github"))
        assertTrue(CrashReport.hasTrace(report))
        assertEquals(1_000L, CrashReport.parseAt(report))
        assertEquals(42, CrashReport.parsePid(report))
    }

    @Test
    fun `the Crash now canary never reaches the report`() {
        val report = CrashReport.javaCrash(CrashNowCanary(), "main", 1L, 1, meta, zone)
        assertFalse(report.contains(CrashNowCanary.TOKEN))
        assertFalse(report.contains(CrashNowCanary.EMAIL))
        assertTrue(report.contains(CrashNowCanary::class.java.name))
    }

    @Test
    fun `every kept field is scrubbed — thread name and device fields`() {
        val dirty = meta.copy(model = "Pixel $token", manufacturer = email, release = "16 $jwt")
        val report = CrashReport.javaCrash(thrown(), "OkHttp $email $bareToken", 1L, 1, dirty, zone)
        assertClean(report)
    }

    @Test
    fun `frames are capped and so is the report`() {
        fun deep(n: Int): Nothing = if (n == 0) throw IllegalStateException() else deep(n - 1)
        val t = try { deep(200); error("unreachable") } catch (e: IllegalStateException) { e }
        val report = CrashReport.javaCrash(t, "main", 1L, 1, meta, zone)
        assertEquals(CrashReport.MAX_FRAMES, report.lines().count { it.startsWith("    at ") })
        assertTrue(report.contains(" more"))
        assertTrue(CrashReport.cap("x".repeat(100_000)).length < CrashReport.MAX_CHARS + 100)
    }

    @Test
    fun `a report with no trace says so`() {
        val report = CrashReport.exitReport(CrashReport.Kind.CRASH, "crash", 5L, 7, null, meta, zone)
        assertFalse(CrashReport.hasTrace(report))
        assertTrue(report.contains("Trace: ${CrashReport.NO_TRACE}"))
    }

    // --- an ANR dump ---

    private val anrDump = """
        ----- pid 4242 at 2026-09-30 09:41:07.123 -----
        Cmd line: com.robin.claudeusage
        Build fingerprint: 'samsung/q7qxeea/q7q:16/BP2A.250605.031/F966BXXU3AYH1:user/release-keys'

        "OkHttp https://api.anthropic.com/?key=$bareToken" daemon prio=5 tid=21 Waiting
          | group="main" sCount=1 ucsCount=0 flags=1 obj=0x13b40000 self=0xb400007b2c4a1e30
          at java.lang.Object.wait(Native method)

        "main" prio=5 tid=1 Blocked
          | group="main" sCount=1 ucsCount=0 flags=1 obj=0x72a4b3c8 self=0xb400007c1d2a3b40
          at com.robin.claudeusage.data.UsageRepository.refresh(UsageRepository.kt:377)
          - waiting to lock <0x0e1f2a3b> (a java.lang.Object) held by thread 21 "Bearer $token"
          at com.robin.claudeusage.MainActivity.onCreate(MainActivity.kt:140)

        "Signal Catcher" daemon prio=10 tid=4 Runnable
          native: #00 pc 000000000009e2c8  /apex/com.android.runtime/lib64/bionic/libc.so (syscall+24) $email
    """.trimIndent()

    @Test
    fun `an ANR dump puts the main thread first and is scrubbed`() {
        val trace = CrashReport.anrTrace(anrDump)
        assertTrue(trace.startsWith("\"main\" prio=5 tid=1 Blocked"))
        val report = CrashReport.exitReport(CrashReport.Kind.ANR, "not responding", 9L, 4242, trace, meta, zone)
        assertClean(report)
        assertTrue(report.contains("UsageRepository.refresh(UsageRepository.kt:377)"))
        assertTrue(report.contains("Signal Catcher"))
    }

    @Test
    fun `a secret crossing the read cap leaves no fragment`() {
        // What a 1 MB read can end on: part of an email and part of a token, too short for
        // the scrubber to recognise once cut.
        val raw = anrDump + "\n  held by robin.fragment@exam"
        val cut = CrashReport.dropPartialLine(raw, cut = true)
        val report = CrashReport.exitReport(CrashReport.Kind.ANR, "not responding", 9L, 1, CrashReport.anrTrace(cut), meta, zone)
        assertFalse(report.contains("robin.fragment"))
        assertTrue(report.contains("trace cut at the read limit"))
        val token = CrashReport.dropPartialLine(anrDump + "\n  Bearer sk-FRAG", cut = true)
        assertFalse(token.contains("sk-FRAG"))
        assertEquals("an uncut trace is untouched", anrDump, CrashReport.dropPartialLine(anrDump, cut = false))
    }

    // --- a native tombstone (protobuf) ---

    private class Pb {
        val out = ByteArrayOutputStream()
        private fun varint(v: Long) {
            var x = v
            while (true) {
                if (x and 0x7fL.inv() == 0L) { out.write(x.toInt()); return }
                out.write(((x and 0x7f) or 0x80).toInt())
                x = x ushr 7
            }
        }
        fun int(field: Int, v: Long) = apply { varint((field shl 3).toLong()); varint(v) }
        fun bytes(field: Int, b: ByteArray) = apply { varint(((field shl 3) or 2).toLong()); varint(b.size.toLong()); out.write(b) }
        fun str(field: Int, s: String) = bytes(field, s.toByteArray())
        fun msg(field: Int, m: Pb) = bytes(field, m.out.toByteArray())
        fun fixed64(field: Int) = apply { varint(((field shl 3) or 1).toLong()); repeat(8) { out.write(0) } }
    }

    private fun frame(pc: Long, file: String, fn: String) =
        Pb().int(1, pc).int(2, pc + 0x1000).str(4, fn).int(5, 24).str(6, file).str(8, "0123456789abcdef0123456789abcdef")

    private fun tombstone(): ByteArray {
        val crashing = Pb().int(1, 4243).str(2, "RenderThread $email")
            .msg(3, Pb().str(1, "x0").int(2, 0xdead))
            .msg(4, frame(0x4f2a8, "/system/lib64/libc.so", "abort"))
            .msg(4, frame(0x1234, "/system/lib64/libhwui.so", "android::uirenderer::renderthread::RenderThread::threadLoop()"))
            .msg(5, Pb().str(1, "mapping").bytes(3, "memory $token".toByteArray()))
        val other = Pb().int(1, 4242).str(2, "main").msg(4, frame(0x10, "/apex/libart.so", "art_quick_invoke_stub"))
        return Pb()
            .int(1, 3).str(2, "samsung/q7q:16/BP2A/F966B:user/release-keys").int(5, 4242).int(6, 4243)
            .msg(10, Pb().int(1, 6).str(2, "SIGABRT").int(3, -1).str(4, "SI_TKILL").fixed64(9))
            .str(14, "abort message: Bearer $token $email CANARY")
            .msg(16, Pb().int(1, 4242).msg(2, other))
            .msg(16, Pb().int(1, 4243).msg(2, crashing))
            .msg(18, Pb().str(1, "main").msg(2, Pb().str(7, "logcat line $refresh $email")))
            .out.toByteArray()
    }

    @Test
    fun `a native tombstone keeps the signal and the crashing thread only`() {
        val trace = CrashReport.nativeTrace(tombstone())
        assertNotNull(trace)
        val report = CrashReport.exitReport(CrashReport.Kind.NATIVE, "native crash", 9L, 4242, trace, meta, zone)
        assertClean(report)
        assertTrue(report.contains("Signal: SIGABRT (SI_TKILL)"))
        assertTrue(report.contains("/system/lib64/libc.so (abort+24)"))
        assertTrue(report.contains("RenderThread::threadLoop()"))
        assertFalse("not the main thread's frames", report.contains("art_quick_invoke_stub"))
        assertFalse("no abort message", report.contains("abort message"))
    }

    @Test
    fun `a tombstone cut at the read cap still gives the signal and the crashing thread`() {
        val cut = tombstone() + Pb().str(17, "x".repeat(4_000)).out.toByteArray().copyOf(2_000)
        val trace = CrashReport.nativeTrace(cut)
        assertNotNull(trace)
        assertTrue(trace!!.contains("Signal: SIGABRT"))
        assertTrue(trace.contains("threadLoop()"))
    }

    @Test
    fun `an unreadable tombstone is tolerated`() {
        assertNull(CrashReport.nativeTrace(byteArrayOf(0x0a, 0x7f, 0x01)))
        assertNull(CrashReport.nativeTrace(ByteArray(0)))
    }
}
