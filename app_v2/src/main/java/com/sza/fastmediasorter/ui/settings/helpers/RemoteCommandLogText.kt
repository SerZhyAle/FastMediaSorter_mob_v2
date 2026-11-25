package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.text.BidiFormatter
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandLogEntry
import com.sza.fastmediasorter.ui.browse.AdapterFileInfoFormatter

internal object RemoteCommandLogText {

    fun line(context: Context, entry: RemoteCommandLogEntry): String {
        val time = AdapterFileInfoFormatter.formatTimestamp(context, entry.timeMillis)
        val outcome = context.getString(outcomeRes(entry.status, entry.reason))
        val deviceName = BidiFormatter.getInstance().unicodeWrap(entry.controllerName ?: entry.deviceId)
        if (!entry.verified) {
            return context.getString(R.string.external_control_log_sealed_line, time, deviceName, outcome)
        }
        val verb = summary(context, entry.verb, context.getString(R.string.remote_control_log_verb_unknown))
        return context.getString(R.string.external_control_log_line, time, deviceName, verb, outcome)
    }

    fun summary(context: Context, verb: String?, fallback: String): String =
        verbRes(verb)?.let(context::getString) ?: fallback

    @StringRes
    private fun verbRes(verb: String?): Int? = when (verb) {
        "commands" -> R.string.remote_command_verb_commands
        "version" -> R.string.remote_command_verb_version
        "status" -> R.string.remote_command_verb_status
        "quit" -> R.string.remote_command_verb_quit
        "playback-stop" -> R.string.remote_command_verb_playback_stop
        "cast-stop" -> R.string.remote_command_verb_cast_stop
        "broadcast-stop" -> R.string.remote_command_verb_broadcast_stop
        "broadcast-start" -> R.string.remote_control_log_verb_broadcast_start
        else -> null
    }

    @StringRes
    private fun outcomeRes(status: String, reason: String?): Int = when (status) {
        RemoteCommandLogEntry.STATUS_SEALED_FAILED -> R.string.external_control_outcome_sealed_failed
        RemoteCommandAnswer.STATUS_DONE -> R.string.external_control_outcome_done
        RemoteCommandAnswer.STATUS_ACCEPTED -> R.string.external_control_outcome_accepted
        RemoteCommandAnswer.STATUS_BUSY -> R.string.external_control_outcome_busy
        RemoteCommandAnswer.STATUS_FAILED -> R.string.external_control_outcome_failed
        else -> when (reason) {
            RemoteCommandAnswer.REASON_NOT_PERMITTED -> R.string.external_control_outcome_not_permitted
            RemoteCommandAnswer.REASON_DECLINED -> R.string.external_control_outcome_declined
            RemoteCommandAnswer.REASON_EXPIRED -> R.string.external_control_outcome_expired
            RemoteCommandAnswer.REASON_UNKNOWN_VERB -> R.string.external_control_outcome_unknown_verb
            RemoteCommandAnswer.REASON_BAD_ARGUMENT -> R.string.external_control_outcome_bad_argument
            else -> R.string.remote_control_outcome_refused
        }
    }
}
