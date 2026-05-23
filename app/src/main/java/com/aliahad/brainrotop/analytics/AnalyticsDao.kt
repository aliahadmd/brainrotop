package com.aliahad.brainrotop.analytics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AnalyticsDao {
    @Insert
    suspend fun insertSession(session: ScreenTimeSessionEntity)

    @Insert
    suspend fun insertBlockEvent(event: BlockEventEntity)

    @Query(
        """
        SELECT * FROM screen_time_sessions
        WHERE dayKey >= :startDayKey
        ORDER BY startedAtWallMs ASC
        """,
    )
    suspend fun sessionsFromDay(startDayKey: String): List<ScreenTimeSessionEntity>

    @Query(
        """
        SELECT * FROM block_events
        WHERE dayKey >= :startDayKey
        ORDER BY timestampWallMs ASC
        """,
    )
    suspend fun blockEventsFromDay(startDayKey: String): List<BlockEventEntity>

    @Query("DELETE FROM screen_time_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM block_events")
    suspend fun clearBlockEvents()
}
