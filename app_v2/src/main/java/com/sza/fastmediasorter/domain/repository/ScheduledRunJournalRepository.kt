package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.ScheduledRunRecord

/**
 * Per-schedule run journal. The shared text log carries no schedule id and is trimmed to a fixed
 * line count, so one large run would erase every other schedule's history.
 */
interface ScheduledRunJournalRepository {
    suspend fun append(operationId: Long, record: ScheduledRunRecord)

    /** Records of one schedule, newest first. */
    suspend fun read(operationId: Long): List<ScheduledRunRecord>

    /** True once the current skip streak of the schedule has been reported to the user (S4513). */
    suspend fun isStallReported(operationId: Long): Boolean

    suspend fun setStallReported(operationId: Long, reported: Boolean)

    /** Also forgets the stall report, so a recreated id starts clean. */
    suspend fun clear(operationId: Long)

    suspend fun clearAll()
}
