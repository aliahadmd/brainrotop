package com.aliahad.brainrotop.analytics

enum class AnalyticsEventType {
    SHORT_VIDEO_BLOCK,
    SCREEN_TIME_WARNING,
    SCREEN_TIME_BLOCK,
}

enum class ScreenSessionEndReason {
    SCREEN_OFF,
    SERVICE_STOPPED,
}

data class ScreenTimeSessionRecord(
    val startedAtWallMs: Long,
    val endedAtWallMs: Long,
    val durationMs: Long,
    val dayKey: String,
    val limitMinutes: Int,
    val endReason: String,
)

data class BlockEventRecord(
    val timestampWallMs: Long,
    val dayKey: String,
    val type: AnalyticsEventType,
    val packageName: String? = null,
    val appLabel: String? = null,
    val ruleId: String? = null,
    val reason: String? = null,
    val limitMinutes: Int? = null,
)

data class DailyAnalytics(
    val dayKey: String,
    val screenTimeMs: Long = 0L,
    val sessionCount: Int = 0,
    val warningCount: Int = 0,
    val screenTimeBlockCount: Int = 0,
    val shortVideoBlockCount: Int = 0,
) {
    val totalBlockCount: Int
        get() = screenTimeBlockCount + shortVideoBlockCount
}

data class AnalyticsSummary(
    val today: DailyAnalytics,
    val sevenDayTrend: List<DailyAnalytics>,
    val topBlockedApp: String?,
) {
    companion object {
        fun empty(nowWallTimeMs: Long = System.currentTimeMillis()): AnalyticsSummary {
            val keys = AnalyticsDateKeys.lastSevenDayKeys(nowWallTimeMs)
            val days = keys.map { DailyAnalytics(dayKey = it) }
            return AnalyticsSummary(
                today = days.last(),
                sevenDayTrend = days,
                topBlockedApp = null,
            )
        }
    }
}
