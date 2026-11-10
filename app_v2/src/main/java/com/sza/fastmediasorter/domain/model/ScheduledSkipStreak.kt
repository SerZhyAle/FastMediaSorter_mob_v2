package com.sza.fastmediasorter.domain.model

import java.util.concurrent.TimeUnit

/**
 * A skipped run is a normal state (an offline server), but a schedule that keeps skipping is a schedule
 * that does nothing, and a month of such runs once passed with only a badge on a screen nobody opened.
 * This decides when a run of skips has become a stall the user must be told about.
 */
object ScheduledSkipStreak {

    private const val STALL_AFTER_DAYS = 3L

    /** A streak this long in time is a stall whatever the interval. */
    val STALL_AFTER_MS: Long = TimeUnit.DAYS.toMillis(STALL_AFTER_DAYS)

    /** Fewer skips than this are never a stall: one weekend with the PC off is not news. */
    const val MIN_SKIPS = 3

    /**
     * A streak this long in runs is a stall even inside [STALL_AFTER_MS], for short intervals. It must stay
     * below the journal's capacity, or the oldest skip is trimmed before the count is ever reached.
     */
    const val STALL_AFTER_SKIPS = 24

    /** The leading records of [newestFirst] that were skipped; empty when the newest run was not. */
    fun streak(newestFirst: List<ScheduledRunRecord>): List<ScheduledRunRecord> =
        newestFirst.takeWhile { it.outcome == ScheduledRunOutcome.SKIPPED }

    /** Start of the oldest journalled skip of the current streak, or null when the newest run was not skipped. */
    fun since(newestFirst: List<ScheduledRunRecord>): Long? = streak(newestFirst).lastOrNull()?.startedAt

    fun isStalled(newestFirst: List<ScheduledRunRecord>): Boolean {
        val skips = streak(newestFirst)
        if (skips.size < MIN_SKIPS) return false
        val span = skips.first().startedAt - skips.last().startedAt
        return span >= STALL_AFTER_MS || skips.size >= STALL_AFTER_SKIPS
    }
}
