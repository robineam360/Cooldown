package com.robin.claudeusage.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** R2 (CCRM-78 (Widgets Reborn)): 12-hour always; the day only past 24 h. */
class WidgetClockTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 1, 1, 18, 29, 0, 0, zone) // a Thursday
    private val nowMs = now.toInstant().toEpochMilli()

    private fun at(z: ZonedDateTime, amPm: Boolean = true) = Fmt.widgetClock(z.toInstant(), nowMs, zone, amPm)

    @Test
    fun twelveHourAlways() {
        assertEquals("9:10 PM", at(now.withHour(21).withMinute(10)))
        assertEquals("12:05 AM", at(now.plusDays(1).withHour(0).withMinute(5)))
        assertEquals("12:00 PM", at(now.plusDays(1).withHour(12).withMinute(0)))
    }

    @Test
    fun theDayOnlyWhenMoreThan24hAway() {
        assertEquals("2:29 PM", at(now.plusHours(20)))
        assertEquals("6:29 PM", at(now.plusHours(24)))
        assertEquals("Fri 6:30 PM", at(now.plusHours(24).plusMinutes(1)))
        assertEquals("Sat 9:10 PM", at(now.plusDays(2).withHour(21).withMinute(10)))
        assertEquals("Tue 6:29 PM", at(now.minusDays(2))) // a stale "as of" stamp
    }

    @Test
    fun amPmDropsOnlyWhenAsked() {
        assertEquals("9:10", at(now.withHour(21).withMinute(10), amPm = false))
    }
}
