package com.aliahad.brainrotop.analytics

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object AnalyticsDateKeys {
    fun dayKey(
        wallTimeMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = wallTimeMs
        }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    fun lastSevenDayKeys(
        nowWallTimeMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<String> {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowWallTimeMs
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return (6 downTo 0).map { daysAgo ->
            val day = calendar.clone() as Calendar
            day.add(Calendar.DAY_OF_YEAR, -daysAgo)
            dayKey(day.timeInMillis, timeZone)
        }
    }

    fun shortLabel(dayKey: String): String =
        dayKey.takeIf { it.length >= 10 }?.substring(5)?.replace('-', '/') ?: dayKey
}
