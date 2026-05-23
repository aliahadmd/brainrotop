package com.aliahad.brainrotop.analytics

import java.util.TimeZone

class ScreenSessionTracker {
    private var activeSession: ActiveSession? = null

    fun onScreenOn(
        startedAtWallMs: Long,
        startedAtElapsedMs: Long,
        limitMinutes: Int,
    ) {
        if (activeSession != null) return

        activeSession = ActiveSession(
            startedAtWallMs = startedAtWallMs,
            startedAtElapsedMs = startedAtElapsedMs,
            limitMinutes = limitMinutes,
        )
    }

    fun onScreenOff(
        endedAtWallMs: Long,
        endedAtElapsedMs: Long,
        endReason: ScreenSessionEndReason,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): ScreenTimeSessionRecord? {
        val active = activeSession ?: return null
        activeSession = null

        val durationMs = (endedAtElapsedMs - active.startedAtElapsedMs).coerceAtLeast(0L)
        return ScreenTimeSessionRecord(
            startedAtWallMs = active.startedAtWallMs,
            endedAtWallMs = endedAtWallMs,
            durationMs = durationMs,
            dayKey = AnalyticsDateKeys.dayKey(active.startedAtWallMs, timeZone),
            limitMinutes = active.limitMinutes,
            endReason = endReason.name,
        )
    }

    private data class ActiveSession(
        val startedAtWallMs: Long,
        val startedAtElapsedMs: Long,
        val limitMinutes: Int,
    )
}
