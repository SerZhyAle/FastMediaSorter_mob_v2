package com.sza.fastmediasorter.domain.model

import java.util.Calendar

data class ScheduledOperation(
    val id: Long = 0,
    val isEnabled: Boolean = true,
    val sourceResourceId: Long?,          // null only for WATCH_MESSAGE, which reads no resource
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
    val lastRunAt: Long? = null,          // Unix timestamp ms - the last ATTEMPT, whatever its outcome
    val nextRunAt: Long? = null,          // Unix timestamp ms
    val lastRunStatus: String? = null,    // null | "OK" | "SKIPPED: <text>" | "ERROR: <text>"
    val workerId: String? = null,         // WorkManager unique work name = "sched_op_$id"
    val fileConditions: ScheduledFileConditions = ScheduledFileConditions(),
    // Kept apart from lastRunAt: a failed run must not hide when the operation last worked, and it
    // is the SINCE_LAST marker, so it only moves on a run that finished without a single error.
    val lastSuccessAt: Long? = null,
    // S4248: set only for WATCH_MESSAGE; the image is a persisted content:// document URI.
    val watchTitle: String? = null,
    val watchText: String? = null,
    val watchImageUri: String? = null,
) {
    /**
     * The moment [TimeFilter.SINCE_LAST] compares file dates against, or null when no run has ever
     * succeeded (first-run semantics, as for a new schedule).
     *
     * Rows written before `last_success_at` existed carry their only success in [lastRunAt] with
     * status `OK`; they keep that value as the marker. A row whose latest attempt failed never falls
     * back to [lastRunAt], because a failed attempt is exactly what must not consume the window.
     */
    val sinceLastMarkerAt: Long?
        get() = lastSuccessAt ?: lastRunAt?.takeIf { lastRunStatus == ScheduledRunStatus.OK }
}

/** What a finished run amounted to; decides the status string, the journal entry and the notification. */
enum class ScheduledRunOutcome {
    /** Every file handled, or nothing to handle. */
    OK,

    /** A side was unreachable before any file was touched - a normal state, not a fault. */
    SKIPPED,

    /** At least one file or the setup failed. */
    ERROR,
}

/** The persisted `lastRunStatus` families; the adapter and the worker read them through here. */
object ScheduledRunStatus {
    const val OK = "OK"
    const val SKIPPED_PREFIX = "SKIPPED"
    const val ERROR_PREFIX = "ERROR"

    fun format(outcome: ScheduledRunOutcome, reason: String?): String = when (outcome) {
        ScheduledRunOutcome.OK -> OK
        ScheduledRunOutcome.SKIPPED -> "$SKIPPED_PREFIX: ${reason.orEmpty()}"
        ScheduledRunOutcome.ERROR -> "$ERROR_PREFIX: ${reason.orEmpty()}"
    }

    /** True for a status the schedule card marks with the warning badge until a run succeeds. */
    fun needsAttention(status: String?): Boolean =
        status != null && (status.startsWith(ERROR_PREFIX) || status.startsWith(SKIPPED_PREFIX))
}

/** Minimum honoured gap between scheduled runs - WorkManager-friendly floor shared by every caller. */
const val MIN_SCHEDULED_INTERVAL_MS: Long = 15L * 60L * 1000L

private const val MS_PER_DAY: Long = 24L * 60L * 60L * 1000L

/** The configured gap in milliseconds, never below [MIN_SCHEDULED_INTERVAL_MS]. */
fun scheduledIntervalMs(intervalHours: Int, intervalMinutes: Int): Long =
    ((intervalHours.toLong() * 3600L + intervalMinutes.toLong() * 60L) * 1000L)
        .coerceAtLeast(MIN_SCHEDULED_INTERVAL_MS)

/**
 * Soonest future moment for a daily HH:MM anchor advanced by whole interval steps.
 *
 * Single source of truth for both the editor preview and the initial scheduling, so the
 * "next run" shown to the user is exactly the moment enqueued in WorkManager. A whole-day interval
 * steps by calendar days in the device's current time zone, so "every day at 03:00" stays at 03:00
 * across a DST change; any other interval steps by raw milliseconds.
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
    val intervalMs = scheduledIntervalMs(intervalHours, intervalMinutes)
    return when {
        anchor > now -> anchor
        intervalMs % MS_PER_DAY == 0L -> nextWholeDaySlot(anchor, startHour, startMinute, intervalMs, now)
        else -> anchor + ((now - anchor) / intervalMs + 1) * intervalMs
    }
}

/**
 * The slot that follows a run, placed on the grid of the slot that just fired.
 *
 * - A slot still in the future stays: a manual "Run now" must not move the schedule.
 * - A whole-day interval continues from the fired slot, re-anchored to the configured HH:MM, so a
 *   weekly schedule stays on its weekday however often it is run by hand.
 * - Anything else, and a schedule with no slot yet, falls back to [computeNextRunAt].
 */
fun computeNextRunAfter(operation: ScheduledOperation, now: Long): Long {
    val intervalMs = scheduledIntervalMs(operation.intervalHours, operation.intervalMinutes)
    val slot = operation.nextRunAt
    return when {
        slot == null || intervalMs % MS_PER_DAY != 0L -> computeNextRunAt(
            operation.startTimeHour,
            operation.startTimeMinute,
            operation.intervalHours,
            operation.intervalMinutes,
            now,
        )
        slot > now -> slot
        else -> nextWholeDaySlot(slot, operation.startTimeHour, operation.startTimeMinute, intervalMs, now)
    }
}

/**
 * Walks forward from the day of [fromMs] in whole-day steps of [intervalMs], pinned to the configured
 * HH:MM of the device's current zone, and returns the first moment strictly after [now].
 */
private fun nextWholeDaySlot(fromMs: Long, startHour: Int, startMinute: Int, intervalMs: Long, now: Long): Long {
    val days = (intervalMs / MS_PER_DAY).toInt().coerceAtLeast(1)
    val cursor = Calendar.getInstance().apply {
        timeInMillis = fromMs
        set(Calendar.HOUR_OF_DAY, startHour.coerceIn(0, 23))
        set(Calendar.MINUTE, startMinute.coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    do {
        cursor.add(Calendar.DAY_OF_YEAR, days)
    } while (cursor.timeInMillis <= now)
    return cursor.timeInMillis
}

/**
 * Applies a finished run to the stored operation.
 *
 * - [ScheduledOperation.lastRunAt] and the status string describe the attempt, whatever its outcome.
 * - [ScheduledOperation.lastSuccessAt] - the SINCE_LAST marker - moves only on [ScheduledRunOutcome.OK],
 *   and to the run's START: a file created while the run was copying is newer than the marker and is
 *   picked up by the next run instead of falling between two windows.
 * - A failing run keeps the marker it read, including the one a pre-`last_success_at` row derived from
 *   its `OK` status, because that status is overwritten by this very call.
 */
fun ScheduledOperation.afterRun(
    outcome: ScheduledRunOutcome,
    reason: String?,
    startedAt: Long,
    finishedAt: Long,
): ScheduledOperation = copy(
    lastRunAt = finishedAt,
    lastRunStatus = ScheduledRunStatus.format(outcome, reason),
    lastSuccessAt = if (outcome == ScheduledRunOutcome.OK) startedAt else sinceLastMarkerAt,
    nextRunAt = computeNextRunAfter(this, finishedAt),
    workerId = "sched_op_$id",
)
