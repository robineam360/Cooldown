package com.robin.claudeusage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The JSONL line format behind [HistoryStore], exercised through its pure companion
 * functions — the class itself is only filesDir plumbing around [HistoryStore.encode]
 * and [HistoryStore.parsePoint], so no Android runtime is needed.
 *
 * CCRM-73 (Model Cap Chart) added the `mc` / `mr` maps. The two things that must stay
 * true: an old line (no maps) still parses, and a new line round-trips every cap.
 */
class HistoryStoreTest {

    private val t = 1_700_000_000_000L
    private fun window(pct: Double?, resetMs: Long?) =
        UsageWindow(pct, resetMs?.let { Instant.ofEpochMilli(it) }, null)

    // --- backwards compatibility ---

    @Test
    fun `a line written before the cap fields existed parses with empty maps`() {
        val old = """{"t":$t,"sp":14.0,"sr":${t + 1},"wp":84.0,"wr":${t + 2}}"""
        val p = HistoryStore.parsePoint(old)!!
        assertEquals(t, p.at)
        assertEquals(14.0, p.sessionPct!!, 0.0)
        assertEquals(t + 1, p.sessionResetAt)
        assertEquals(84.0, p.weeklyPct!!, 0.0)
        assertEquals(t + 2, p.weeklyResetAt)
        assertTrue(p.capPcts.isEmpty())
        assertTrue(p.capResets.isEmpty())
    }

    @Test
    fun `a torn or blank line is dropped rather than thrown`() {
        assertNull(HistoryStore.parsePoint(""))
        assertNull(HistoryStore.parsePoint("""{"t":$t,"sp":1"""))
        assertNull(HistoryStore.parsePoint("""{"sp":1.0}"""))
    }

    // --- round trip (CCRM-73 (Model Cap Chart)) ---

    @Test
    fun `every cap with a percent round-trips by name`() {
        val data = UsageData(
            session = window(14.0, t + 1),
            weekly = window(84.0, t + 2),
            modelCaps = listOf(
                ModelCap("Fable", window(61.0, t + 3)),
                ModelCap("Opus", window(9.5, t + 4)),
            ),
        )
        val p = HistoryStore.parsePoint(HistoryStore.encode(data, t))!!
        assertEquals(mapOf("Fable" to 61.0, "Opus" to 9.5), p.capPcts)
        assertEquals(mapOf("Fable" to t + 3, "Opus" to t + 4), p.capResets)
        assertEquals(84.0, p.weeklyPct!!, 0.0)
    }

    @Test
    fun `a cap without a percent is not recorded and one without a reset has no reset`() {
        val data = UsageData(
            session = null,
            weekly = window(40.0, t + 2),
            modelCaps = listOf(
                ModelCap("Fable", window(null, t + 3)),
                ModelCap("Opus", window(12.0, null)),
            ),
        )
        val line = HistoryStore.encode(data, t)
        val p = HistoryStore.parsePoint(line)!!
        assertEquals(mapOf("Opus" to 12.0), p.capPcts)
        assertTrue(p.capResets.isEmpty())
        assertTrue("mr must be omitted, not written empty", !line.contains("\"mr\""))
    }

    @Test
    fun `no caps means no cap keys at all so old and new lines look alike`() {
        val data = UsageData(session = window(3.0, t + 1), weekly = null, modelCaps = emptyList())
        val line = HistoryStore.encode(data, t)
        assertTrue(!line.contains("\"mc\""))
        assertTrue(!line.contains("\"mr\""))
        assertNull(HistoryStore.parsePoint(line)!!.weeklyPct)
    }
}
