package com.aliahad.brainrotop.analytics

import android.content.Context
import com.aliahad.brainrotop.detector.DetectionResult
import com.aliahad.brainrotop.screentime.ScreenTimeConfig

class AnalyticsRepository private constructor(
    private val dao: AnalyticsDao,
) {
    suspend fun recordSession(session: ScreenTimeSessionRecord) {
        dao.insertSession(session.toEntity())
    }

    suspend fun recordShortVideoBlock(
        result: DetectionResult,
        wallTimeMs: Long = System.currentTimeMillis(),
    ) {
        dao.insertBlockEvent(
            BlockEventRecord(
                timestampWallMs = wallTimeMs,
                dayKey = AnalyticsDateKeys.dayKey(wallTimeMs),
                type = AnalyticsEventType.SHORT_VIDEO_BLOCK,
                packageName = result.packageName,
                appLabel = result.appLabel,
                ruleId = result.ruleId,
                reason = result.reason,
            ).toEntity(),
        )
    }

    suspend fun recordScreenTimeWarning(
        config: ScreenTimeConfig,
        wallTimeMs: Long = System.currentTimeMillis(),
    ) {
        recordScreenTimeEvent(
            type = AnalyticsEventType.SCREEN_TIME_WARNING,
            config = config,
            wallTimeMs = wallTimeMs,
        )
    }

    suspend fun recordScreenTimeBlock(
        config: ScreenTimeConfig,
        wallTimeMs: Long = System.currentTimeMillis(),
    ) {
        recordScreenTimeEvent(
            type = AnalyticsEventType.SCREEN_TIME_BLOCK,
            config = config,
            wallTimeMs = wallTimeMs,
        )
    }

    suspend fun loadSummary(
        nowWallTimeMs: Long = System.currentTimeMillis(),
    ): AnalyticsSummary {
        val firstDayKey = AnalyticsDateKeys.lastSevenDayKeys(nowWallTimeMs).first()
        val sessions = dao.sessionsFromDay(firstDayKey).map(ScreenTimeSessionEntity::toRecord)
        val events = dao.blockEventsFromDay(firstDayKey).mapNotNull(BlockEventEntity::toRecord)
        return AnalyticsAggregator.summarize(
            sessions = sessions,
            events = events,
            nowWallTimeMs = nowWallTimeMs,
        )
    }

    suspend fun reset() {
        dao.clearBlockEvents()
        dao.clearSessions()
    }

    private suspend fun recordScreenTimeEvent(
        type: AnalyticsEventType,
        config: ScreenTimeConfig,
        wallTimeMs: Long,
    ) {
        dao.insertBlockEvent(
            BlockEventRecord(
                timestampWallMs = wallTimeMs,
                dayKey = AnalyticsDateKeys.dayKey(wallTimeMs),
                type = type,
                reason = when (type) {
                    AnalyticsEventType.SCREEN_TIME_WARNING -> "1 minute left before screen-time block"
                    AnalyticsEventType.SCREEN_TIME_BLOCK -> "Screen-time limit reached"
                    AnalyticsEventType.SHORT_VIDEO_BLOCK -> "Short-video block"
                },
                limitMinutes = config.limitMinutes,
            ).toEntity(),
        )
    }

    companion object {
        @Volatile
        private var instance: AnalyticsRepository? = null

        fun get(context: Context): AnalyticsRepository =
            instance ?: synchronized(this) {
                instance ?: AnalyticsRepository(
                    BrainrotopDatabase.get(context).analyticsDao(),
                ).also { instance = it }
            }
    }
}
