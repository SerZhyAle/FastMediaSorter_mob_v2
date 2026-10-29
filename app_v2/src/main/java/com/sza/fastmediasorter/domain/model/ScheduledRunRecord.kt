package com.sza.fastmediasorter.domain.model

/**
 * One line of a schedule's run journal.
 *
 * @property note an observation about how the run executed that is not its result, e.g. the
 *   foreground service could not start; null when there is nothing to add.
 * @property reason the code behind [message] when the producer knew the cause; null for a run that
 *   ended without one and for a row written before the code existed.
 */
data class ScheduledRunRecord(
    val startedAt: Long,
    val finishedAt: Long,
    val outcome: ScheduledRunOutcome,
    val filesProcessed: Int,
    val message: String? = null,
    val note: String? = null,
    val reason: ScheduledRunReason? = null,
)
