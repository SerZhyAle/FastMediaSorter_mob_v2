package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing playback positions of audio/video files.
 * Enables "audiobook mode" - resume playback from last position.
 */
@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey
    val filePath: String,                           // Unique file path
    val position: Long,                              // Position in milliseconds
    val duration: Long,                              // File duration in milliseconds
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false                 // File watched/listened to >95%
)
