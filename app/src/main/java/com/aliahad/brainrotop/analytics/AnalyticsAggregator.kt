package com.aliahad.brainrotop.analytics

import java.util.TimeZone

object AnalyticsAggregator {
    fun summarize(
        sessions: List<ScreenTimeSessionRecord>,
        events: List<BlockEventRecord>,
        nowWallTimeMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): AnalyticsSummary {
        val dayKeys = AnalyticsDateKeys.lastSevenDayKeys(nowWallTimeMs, timeZone)

        val days = dayKeys.map { dayKey ->
            val daySessions = sessions.filter { it.dayKey == dayKey }
            val dayEvents = events.filter { it.dayKey == dayKey }

            DailyAnalytics(
                dayKey = dayKey,
                screenTimeMs = daySessions.sumOf { it.durationMs },
                sessionCount = daySessions.size,
                warningCount = dayEvents.count { it.type == AnalyticsEventType.SCREEN_TIME_WARNING },
                screenTimeBlockCount = dayEvents.count { it.type == AnalyticsEventType.SCREEN_TIME_BLOCK },
                shortVideoBlockCount = dayEvents.count { it.type == AnalyticsEventType.SHORT_VIDEO_BLOCK },
            )
        }

        val todayKey = dayKeys.last()
        val topBlockedApp = events
            .asSequence()
            .filter { it.dayKey == todayKey }
            .filter { it.type == AnalyticsEventType.SHORT_VIDEO_BLOCK }
            .mapNotNull { it.appLabel?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        return AnalyticsSummary(
            today = days.lastOrNull() ?: DailyAnalytics(AnalyticsDateKeys.dayKey(nowWallTimeMs, timeZone)),
            sevenDayTrend = days,
            topBlockedApp = topBlockedApp,
        )
    }
}
