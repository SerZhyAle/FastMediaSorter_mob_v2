package com.sza.fastmediasorter.domain.model

/**
 * One line of a schedule's run journal.
 *
 * @property note an observation about how the run executed that is not its result, e.g. the
 *   foreground service could not start; null when there is nothing to add.
 */
data class ScheduledRunRecord(
    val startedAt: Long,
    val finishedAt: Long,
    val outcome: ScheduledRunOutcome,
    val filesProcessed: Int,
    val message: String? = null,
    val note: String? = null,
)
