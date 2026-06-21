package com.sza.fastmediasorter.domain.model

import java.util.Calendar

data class ScheduledOperation(
    val id: Long = 0,
    val isEnabled: Boolean = true,
    val sourceResourceId: Long,
    val operationType: ScheduledOpType,
    val targetResourceId: Long?,          // null when operationType == DELETE
    val fileTypeMask: Int = FileTypeFlags.DEFAULT, // bitmask of FileTypeFlags constants
    val timeFilter: TimeFilter,
    val startTimeHour: Int,               // 0..23
    val startTimeMinute: Int,             // 0..59
    val intervalHours: Int,               // 0..1440
    val intervalMinutes: Int,             // 0..59; if hours==0 then min >= 15
    val overwrite: Boolean = false,          // overwrite existing files on COPY/MOVE
    val silentMode: Boolean = false,
    val lastRunAt: Long? = null,          // Unix timestamp ms
    val nextRunAt: Long? = null,          // Unix timestamp ms
    val lastRunStatus: String? = null,    // null | "OK" | "ERROR: <text>"
    val workerId: String? = null          // WorkManager unique work name = "sched_op_$id"
)

/** Minimum honoured gap between scheduled runs - WorkManager-friendly floor shared by every caller. */
const val MIN_SCHEDULED_INTERVAL_MS: Long = 15L * 60L * 1000L

/**
 * Soonest future moment for a daily HH:MM anchor advanced by whole interval steps.
 *
 * Single source of truth for both the editor preview and the initial scheduling, so the
 * "next run" shown to the user is exactly the moment enqueued in WorkManager. Steps by raw
 * milliseconds (DST-agnostic), matching the worker's recurring cadence.
 */
fun computeNextRunAt(
    startHour: Int,
    startMinute: Int,
    intervalHours: Int,
    intervalMinutes: Int,
    now: Long,
): Long {
    val anchor = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, startHour.coerceIn(0, 23))
        set(Calendar.MINUTE, startMinute.coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    if (anchor > now) return anchor
    val intervalMs = ((intervalHours.toLong() * 3600L + intervalMinutes.toLong() * 60L) * 1000L)
        .coerceAtLeast(MIN_SCHEDULED_INTERVAL_MS)
    val stepsBehind = (now - anchor) / intervalMs
    return anchor + (stepsBehind + 1) * intervalMs
}
