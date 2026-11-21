package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandLogEntry

/**
 * One line of the on-device command log in the person's words. A verb or outcome this build does not know is
 * named generically, never printed raw: the log holds text a controller chose, and only the sender's id of an
 * opened box is shown beside its name.
 */
internal object RemoteCommandLogText {

    private const val SHORT_ID_LENGTH = 12
    private const val TIME_FLAGS =
        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH

    fun line(context: Context, entry: RemoteCommandLogEntry): String {
        val time = DateUtils.formatDateTime(context, entry.timeMillis, TIME_FLAGS)
        val controller = when {
            !entry.verified -> context.getString(R.string.remote_control_log_controller_unverified)
            else -> entry.controllerName ?: entry.deviceId.take(SHORT_ID_LENGTH)
        }
        val outcome = context.getString(outcomeRes(entry.status, entry.reason))
        val verb = entry.verb
        return if (verb == null) {
            context.getString(R.string.remote_control_log_line_no_verb, time, controller, outcome)
        } else {
            val verbText = context.getString(verbRes(verb))
            context.getString(R.string.remote_control_log_line, time, controller, verbText, outcome)
        }
    }

    @StringRes
    private fun verbRes(verb: String): Int = when (verb) {
        "commands" -> R.string.remote_control_log_verb_commands
        "version" -> R.string.remote_control_log_verb_version
        "status" -> R.string.remote_control_log_verb_status
        "quit" -> R.string.remote_control_log_verb_quit
        "playback-stop" -> R.string.remote_control_log_verb_playback_stop
        "cast-stop" -> R.string.remote_control_log_verb_cast_stop
        "broadcast-stop" -> R.string.remote_control_log_verb_broadcast_stop
        "broadcast-start" -> R.string.remote_control_log_verb_broadcast_start
        else -> R.string.remote_control_log_verb_unknown
    }

    @StringRes
    private fun outcomeRes(status: String, reason: String?): Int = when (status) {
        RemoteCommandLogEntry.STATUS_SEALED_FAILED -> R.string.remote_control_outcome_unverified
        RemoteCommandAnswer.STATUS_DONE -> R.string.remote_control_outcome_done
        RemoteCommandAnswer.STATUS_ACCEPTED -> R.string.remote_control_outcome_started
        RemoteCommandAnswer.STATUS_BUSY -> R.string.remote_control_outcome_busy
        RemoteCommandAnswer.STATUS_FAILED ->
            if (reason == RemoteCommandAnswer.REASON_TARGET_MISSING) {
                R.string.remote_control_outcome_target_missing
            } else {
                R.string.remote_control_outcome_failed
            }
        else -> refusalRes(reason)
    }

    @StringRes
    private fun refusalRes(reason: String?): Int = when (reason) {
        RemoteCommandAnswer.REASON_NOT_PERMITTED -> R.string.remote_control_outcome_not_permitted
        RemoteCommandAnswer.REASON_DECLINED -> R.string.remote_control_outcome_declined
        RemoteCommandAnswer.REASON_EXPIRED -> R.string.remote_control_outcome_expired
        RemoteCommandAnswer.REASON_UNKNOWN_VERB -> R.string.remote_control_outcome_unknown_command
        RemoteCommandAnswer.REASON_BAD_ARGUMENT -> R.string.remote_control_outcome_bad_argument
        RemoteCommandAnswer.REASON_SCHEMA -> R.string.remote_control_outcome_newer_format
        else -> R.string.remote_control_outcome_refused
    }
}
