package com.aliahad.brainrotop.analytics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AnalyticsDateKeysTest {
    @Test
    fun dayKeyUsesProvidedTimeZone() {
        val utc = TimeZone.getTimeZone("UTC")
        val shanghai = TimeZone.getTimeZone("Asia/Shanghai")
        val wallTime = calendarMs(utc, 2026, Calendar.MAY, 22, 17, 30)

        assertEquals("2026-05-22", AnalyticsDateKeys.dayKey(wallTime, utc))
        assertEquals("2026-05-23", AnalyticsDateKeys.dayKey(wallTime, shanghai))
    }

    @Test
    fun lastSevenDayKeysEndsWithToday() {
        val timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        val now = calendarMs(timeZone, 2026, Calendar.MAY, 23, 9, 0)

        assertEquals(
            listOf(
                "2026-05-17",
                "2026-05-18",
                "2026-05-19",
                "2026-05-20",
                "2026-05-21",
                "2026-05-22",
                "2026-05-23",
            ),
            AnalyticsDateKeys.lastSevenDayKeys(now, timeZone),
        )
    }

    private fun calendarMs(
        timeZone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long =
        Calendar.getInstance(timeZone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
