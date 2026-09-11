package com.robin.claudeusage.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Fmt.ago] and [Fmt.dhm] with the injected `nowMs` (CCRM-65 (Accounts Redesign)):
 * both took a direct `System.currentTimeMillis()` read before, which made them
 * untestable without a real clock. The ladders themselves are unchanged.
 */
class RelativeAgoTest {

    private val now = 1_757_000_000_000L

    @Test
    fun `ago at the same instant is just now`() {
        assertEquals("just now", Fmt.ago(now, now))
    }

    @Test
    fun `ago just under a minute is still just now`() {
        assertEquals("just now", Fmt.ago(now - 59_999L, now))
    }

    @Test
    fun `ago at exactly a minute rolls to 1m`() {
        assertEquals("1m ago", Fmt.ago(now - 60_000L, now))
    }

    @Test
    fun `ago hours and minutes`() {
        val threeH38m = (3 * 60 + 38) * 60_000L
        assertEquals("3h 38m ago", Fmt.ago(now - threeH38m, now))
    }

    @Test
    fun `ago rolls into days past 24h`() {
        assertEquals("1d 2h ago", Fmt.ago(now - 26 * 60 * 60_000L, now))
    }

    @Test
    fun `ago of zero is never`() {
        assertEquals("never", Fmt.ago(0L, now))
    }

    @Test
    fun `dhm hours and minutes`() {
        val oneH9m = (60 + 9) * 60_000L
        assertEquals("1h 9m", Fmt.dhm(now + oneH9m, now))
    }

    @Test
    fun `dhm days and hours`() {
        val twoD14h = (2 * 24 * 60 + 14 * 60) * 60_000L
        assertEquals("2d 14h 0m", Fmt.dhm(now + twoD14h, now))
    }

    @Test
    fun `dhm at or past the target reads now`() {
        assertEquals("now", Fmt.dhm(now, now))
        assertEquals("now", Fmt.dhm(now - 60_000L, now))
    }
}
