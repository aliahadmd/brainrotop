package com.aliahad.brainrotop.analytics

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screen_time_sessions")
data class ScreenTimeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startedAtWallMs: Long,
    val endedAtWallMs: Long,
    val durationMs: Long,
    val dayKey: String,
    val limitMinutes: Int,
    val endReason: String,
)

@Entity(tableName = "block_events")
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampWallMs: Long,
    val dayKey: String,
    val type: String,
    val packageName: String?,
    val appLabel: String?,
    val ruleId: String?,
    val reason: String?,
    val limitMinutes: Int?,
)

fun ScreenTimeSessionEntity.toRecord(): ScreenTimeSessionRecord =
    ScreenTimeSessionRecord(
        startedAtWallMs = startedAtWallMs,
        endedAtWallMs = endedAtWallMs,
        durationMs = durationMs,
        dayKey = dayKey,
        limitMinutes = limitMinutes,
        endReason = endReason,
    )

fun ScreenTimeSessionRecord.toEntity(): ScreenTimeSessionEntity =
    ScreenTimeSessionEntity(
        startedAtWallMs = startedAtWallMs,
        endedAtWallMs = endedAtWallMs,
        durationMs = durationMs,
        dayKey = dayKey,
        limitMinutes = limitMinutes,
        endReason = endReason,
    )

fun BlockEventEntity.toRecord(): BlockEventRecord? {
    val eventType = runCatching { AnalyticsEventType.valueOf(type) }.getOrNull() ?: return null
    return BlockEventRecord(
        timestampWallMs = timestampWallMs,
        dayKey = dayKey,
        type = eventType,
        packageName = packageName,
        appLabel = appLabel,
        ruleId = ruleId,
        reason = reason,
        limitMinutes = limitMinutes,
    )
}

fun BlockEventRecord.toEntity(): BlockEventEntity =
    BlockEventEntity(
        timestampWallMs = timestampWallMs,
        dayKey = dayKey,
        type = type.name,
        packageName = packageName,
        appLabel = appLabel,
        ruleId = ruleId,
        reason = reason,
        limitMinutes = limitMinutes,
    )
