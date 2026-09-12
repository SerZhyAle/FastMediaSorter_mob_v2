package com.sza.fastmediasorter.wear.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sza.fastmediasorter.wear.domain.model.MotionHistoryEntry

/**
 * S3014: Stored shape of one physical activity/step measurement snapshot.
 */
@Entity(tableName = "motion_history")
data class MotionHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestampMillis: Long,
    val steps: Long,
    val durationSeconds: Long = 0L,
    val note: String = ""
)

fun MotionHistoryEntity.toDomain(): MotionHistoryEntry = MotionHistoryEntry(
    id = id,
    timestampMillis = timestampMillis,
    steps = steps,
    durationSeconds = durationSeconds,
    note = note
)

fun MotionHistoryEntry.toEntity(): MotionHistoryEntity = MotionHistoryEntity(
    id = id,
    timestampMillis = timestampMillis,
    steps = steps,
    durationSeconds = durationSeconds,
    note = note
)
