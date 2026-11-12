package com.sza.fastmediasorter.ui.scheduledops

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
import com.sza.fastmediasorter.domain.model.ScheduledRunReason

/**
 * The sentence the run history shows for a run's [ScheduledRunReason] (S4478). A run recorded without a
 * code - one written before the code existed, or one whose producer could not name a cause - gets a
 * neutral sentence for its outcome, never the English message.
 */
object ScheduledRunReasonText {

    @StringRes
    fun of(reason: ScheduledRunReason?, outcome: ScheduledRunOutcome): Int = when {
        reason != null -> textOf(reason)
        outcome == ScheduledRunOutcome.SKIPPED -> R.string.scheduled_op_reason_skipped_other
        else -> R.string.scheduled_op_reason_error_other
    }

    @StringRes
    private fun textOf(reason: ScheduledRunReason): Int = when (reason) {
        ScheduledRunReason.SOURCE_UNREACHABLE -> R.string.scheduled_op_reason_source_unreachable
        ScheduledRunReason.TARGET_UNREACHABLE -> R.string.scheduled_op_reason_target_unreachable
        ScheduledRunReason.SOURCE_READ_ONLY -> R.string.scheduled_op_reason_source_read_only
        ScheduledRunReason.TARGET_READ_ONLY -> R.string.scheduled_op_reason_target_read_only
        ScheduledRunReason.SOURCE_MISSING -> R.string.scheduled_op_reason_source_missing
        ScheduledRunReason.TARGET_UNAVAILABLE -> R.string.scheduled_op_reason_target_unavailable
        ScheduledRunReason.WATCH_UNAVAILABLE -> R.string.scheduled_op_reason_watch_unavailable
        ScheduledRunReason.WATCH_UNREACHABLE -> R.string.scheduled_op_reason_watch_unreachable
        ScheduledRunReason.WATCH_REFUSED -> R.string.scheduled_op_reason_watch_refused
        ScheduledRunReason.MESSAGE_TOO_LONG -> R.string.scheduled_op_reason_message_too_long
        ScheduledRunReason.PIN_REFUSED -> R.string.scheduled_op_reason_pin_refused
        ScheduledRunReason.PERMISSION_REQUIRED -> R.string.scheduled_op_reason_permission_required
        ScheduledRunReason.SOURCE_DELETED -> R.string.scheduled_op_reason_source_deleted
        ScheduledRunReason.TARGET_DELETED -> R.string.scheduled_op_reason_target_deleted
        ScheduledRunReason.FILL_BUSY,
        ScheduledRunReason.FILL_NOT_LOCAL,
        ScheduledRunReason.FILL_STORAGE_UNAVAILABLE,
        ScheduledRunReason.FILL_MEASUREMENT_UNAVAILABLE,
        ScheduledRunReason.FILL_NO_FREE_SPACE,
        ScheduledRunReason.FILL_WRITE_FAILED,
        ScheduledRunReason.FILL_CLEANUP_PENDING -> fillTextOf(reason)
    }

    // S4522: kept apart so neither mapping outgrows the complexity ceiling; each stays exhaustive.
    @StringRes
    private fun fillTextOf(reason: ScheduledRunReason): Int = when (reason) {
        ScheduledRunReason.FILL_BUSY -> R.string.scheduled_op_reason_fill_busy
        ScheduledRunReason.FILL_NOT_LOCAL -> R.string.scheduled_op_reason_fill_not_local
        ScheduledRunReason.FILL_STORAGE_UNAVAILABLE -> R.string.scheduled_op_reason_fill_storage_unavailable
        ScheduledRunReason.FILL_MEASUREMENT_UNAVAILABLE -> R.string.scheduled_op_reason_fill_measurement_unavailable
        ScheduledRunReason.FILL_NO_FREE_SPACE -> R.string.scheduled_op_reason_fill_no_free_space
        ScheduledRunReason.FILL_WRITE_FAILED -> R.string.scheduled_op_reason_fill_write_failed
        else -> R.string.scheduled_op_reason_fill_cleanup_pending
    }
}
