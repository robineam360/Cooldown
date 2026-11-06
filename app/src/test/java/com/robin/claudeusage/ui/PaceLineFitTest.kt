package com.robin.claudeusage.ui

import com.robin.claudeusage.data.Pace
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * CCRM-104 (Pace Line Parity), `design/2026-11-06-mobile-pace-line-wireframe.html` D10 and Astra
 * C1-3/C1-4: one drop order on every narrow surface — weekday, then clock; the duration and its word
 * are the floor — and the notification caption's own fallback to its distance clause.
 */
class PaceLineFitTest {

    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-04T14:00:00Z").toEpochMilli() // Wed 2:00 PM
    private val resetWed = Instant.parse("2026-11-11T12:30:00Z") // next Wed 12:30 PM
    private val resetToday = Instant.parse("2026-11-04T23:00:00Z")
    private val over = Pace.MarkTime.Over(0L, 86_399_999L) // 23h 59m ahead

    private fun forms(mt: Pace.MarkTime?, reset: Instant) =
        PaceText.paceLineForms(mt, reset, now, false, zone, Locale.US).map { it.text }

    @Test
    fun `three forms, longest first`() {
        assertEquals(
            listOf("23h 59m ahead · Wed 12:30 PM", "23h 59m ahead · 12:30 PM", "23h 59m ahead"),
            forms(over, resetWed),
        )
        assertEquals(
            listOf("On the mark · Wed 12:30 PM", "On the mark · 12:30 PM", "On the mark"),
            forms(Pace.MarkTime.On, resetWed),
        )
    }

    @Test
    fun `a reset later today has no weekday form to drop`() {
        assertEquals(listOf("23h 59m ahead · 11:00 PM", "23h 59m ahead"), forms(over, resetToday))
    }

    @Test
    fun `no readout, no forms`() {
        assertEquals(emptyList<String>(), forms(null, resetWed))
    }

    @Test
    fun `fit takes the first form that fits, else the floor`() {
        val f = PaceText.paceLineForms(over, resetWed, now, false, zone, Locale.US)
        assertEquals("23h 59m ahead · Wed 12:30 PM", PaceText.fit(f) { true }?.text)
        assertEquals("23h 59m ahead · 12:30 PM", PaceText.fit(f) { it.length <= 24 }?.text)
        assertEquals("23h 59m ahead", PaceText.fit(f) { it.length <= 13 }?.text)
        // Nothing fits (large font scale): the floor still shows, never a cut phrase.
        assertEquals("23h 59m ahead", PaceText.fit(f) { false }?.text)
        assertEquals("23h 59m ahead", PaceText.fit(f) { false }?.tinted)
    }

    @Test
    fun `caption forms fall back to the distance clause`() {
        assertEquals(
            listOf("39% pace mark · 13 over", "13 over"),
            PaceText.captionForms(PaceText.Mode.USED, 52.0, 38.9),
        )
        assertEquals(
            listOf("40% pace mark · 10 to spare", "10 to spare"),
            PaceText.captionForms(PaceText.Mode.LEFT, 50.0, 60.0),
        )
        assertEquals(
            listOf("60% pace mark · on it", "On the mark"),
            PaceText.captionForms(PaceText.Mode.USED, 60.2, 60.0),
        )
        assertEquals(
            listOf("52% used · 39% pace mark", "52% used"),
            PaceText.captionForms(PaceText.Mode.VS_PACE, 52.0, 38.9),
        )
        assertEquals(emptyList<String>(), PaceText.captionForms(PaceText.Mode.USED, 100.0, 80.0))
    }

    @Test
    fun `vs pace caption takes core's words`() {
        assertEquals("52% used · 39% pace mark", PaceText.caption(PaceText.Mode.VS_PACE, 52.0, 38.9))
    }
}
