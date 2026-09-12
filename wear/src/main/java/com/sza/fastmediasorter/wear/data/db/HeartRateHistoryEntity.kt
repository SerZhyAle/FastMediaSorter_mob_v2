package com.sza.fastmediasorter.wear.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sza.fastmediasorter.wear.domain.model.HeartRateHistoryEntry

/**
 * S2808: the stored shape of one completed heart-rate measurement.
 *
 * One row per measurement session: the last BPM reading captured before the session ended,
 * with the wall-clock timestamp of the save. No enum or type converter is needed because
 * the only stored value is an integer BPM.
 */
@Entity(tableName = "heart_rate_history")
data class HeartRateHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bpm: Int,
    val timestampMillis: Long
)

fun HeartRateHistoryEntity.toDomain(): HeartRateHistoryEntry = HeartRateHistoryEntry(
    id = id,
    bpm = bpm,
    timestampMillis = timestampMillis
)
