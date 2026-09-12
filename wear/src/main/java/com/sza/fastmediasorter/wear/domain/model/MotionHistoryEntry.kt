package com.sza.fastmediasorter.wear.domain.model

/**
 * S3014: One recorded physical activity/step measurement snapshot on the watch.
 *
 * @param id database primary key (0 for unpersisted entries).
 * @param timestampMillis epoch milliseconds when the measurement snapshot was recorded.
 * @param steps number of steps recorded in this snapshot.
 * @param durationSeconds duration of tracking session in seconds if applicable.
 * @param note optional descriptive note.
 */
data class MotionHistoryEntry(
    val id: Long = 0,
    val timestampMillis: Long,
    val steps: Long,
    val durationSeconds: Long = 0,
    val note: String = ""
)
