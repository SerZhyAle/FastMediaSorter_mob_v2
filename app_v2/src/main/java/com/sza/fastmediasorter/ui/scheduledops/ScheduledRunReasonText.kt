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
    fun of(reason: ScheduledRunReason?, outcome: ScheduledRunOutcome): Int = when (reason) {
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
        null -> if (outcome == ScheduledRunOutcome.SKIPPED) {
            R.string.scheduled_op_reason_skipped_other
        } else {
            R.string.scheduled_op_reason_error_other
        }
    }
}
