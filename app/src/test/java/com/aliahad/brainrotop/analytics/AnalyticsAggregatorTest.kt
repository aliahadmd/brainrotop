package com.aliahad.brainrotop.analytics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AnalyticsAggregatorTest {
    private val timeZone: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
    private val now: Long = calendarMs(2026, Calendar.MAY, 23, 12, 0)

    @Test
    fun summarizeBuildsTodayRecoveryMetrics() {
        val summary = AnalyticsAggregator.summarize(
            sessions = listOf(
                session("2026-05-23", durationMs = 4 * 60_000L),
                session("2026-05-23", durationMs = 2 * 60_000L),
                session("2026-05-22", durationMs = 10 * 60_000L),
            ),
            events = listOf(
                event("2026-05-23", AnalyticsEventType.SCREEN_TIME_WARNING),
                event("2026-05-23", AnalyticsEventType.SCREEN_TIME_BLOCK),
                event("2026-05-23", AnalyticsEventType.SHORT_VIDEO_BLOCK, appLabel = "WeChat Channels"),
                event("2026-05-23", AnalyticsEventType.SHORT_VIDEO_BLOCK, appLabel = "WeChat Channels"),
                event("2026-05-23", AnalyticsEventType.SHORT_VIDEO_BLOCK, appLabel = "YouTube Shorts"),
            ),
            nowWallTimeMs = now,
            timeZone = timeZone,
        )

        assertEquals(6 * 60_000L, summary.today.screenTimeMs)
        assertEquals(2, summary.today.sessionCount)
        assertEquals(1, summary.today.warningCount)
        assertEquals(1, summary.today.screenTimeBlockCount)
        assertEquals(3, summary.today.shortVideoBlockCount)
        assertEquals("WeChat Channels", summary.topBlockedApp)
    }

    @Test
    fun summarizeFillsSevenDayTrendWithEmptyDays() {
        val summary = AnalyticsAggregator.summarize(
            sessions = emptyList(),
            events = listOf(
                event("2026-05-20", AnalyticsEventType.SHORT_VIDEO_BLOCK),
                event("2026-05-23", AnalyticsEventType.SCREEN_TIME_BLOCK),
            ),
            nowWallTimeMs = now,
            timeZone = timeZone,
        )

        assertEquals(7, summary.sevenDayTrend.size)
        assertEquals("2026-05-17", summary.sevenDayTrend.first().dayKey)
        assertEquals("2026-05-23", summary.sevenDayTrend.last().dayKey)
        assertEquals(
            listOf(0, 0, 0, 1, 0, 0, 1),
            summary.sevenDayTrend.map { it.totalBlockCount },
        )
    }

    private fun session(dayKey: String, durationMs: Long): ScreenTimeSessionRecord =
        ScreenTimeSessionRecord(
            startedAtWallMs = 0L,
            endedAtWallMs = durationMs,
            durationMs = durationMs,
            dayKey = dayKey,
            limitMinutes = 5,
            endReason = ScreenSessionEndReason.SCREEN_OFF.name,
        )

    private fun event(
        dayKey: String,
        type: AnalyticsEventType,
        appLabel: String? = null,
    ): BlockEventRecord =
        BlockEventRecord(
            timestampWallMs = 0L,
            dayKey = dayKey,
            type = type,
            appLabel = appLabel,
        )

    private fun calendarMs(
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
