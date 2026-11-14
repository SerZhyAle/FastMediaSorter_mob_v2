package com.sza.fastmediasorter.ui.broadcast.helpers

import android.view.View
import android.widget.TextView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.DriveBroadcastPublishState
import com.sza.fastmediasorter.domain.model.DriveBroadcastPublishState.NotAnnounced
import timber.log.Timber

/**
 * Renders the Google Drive line of the broadcast screen beside the exchange server line (contract DEVICE-EXCHANGE
 * item BC: the control surface says where the broadcast went). The whole state is in the words of one `TextView`,
 * so TalkBack reads it as one node and nothing rests on color alone; a failure says what to try (item DA).
 */
class BroadcastDriveStatusManager {

    fun render(view: TextView, state: DriveBroadcastPublishState) {
        val line = when (state) {
            DriveBroadcastPublishState.Idle -> null
            DriveBroadcastPublishState.Announcing -> R.string.broadcast_drive_line_announcing
            DriveBroadcastPublishState.Announced -> R.string.broadcast_drive_line_announced
            is NotAnnounced -> when (state.reason) {
                NotAnnounced.Reason.NOT_SIGNED_IN -> R.string.broadcast_drive_line_sign_in
                NotAnnounced.Reason.UNREACHABLE -> R.string.broadcast_drive_line_unreachable
                NotAnnounced.Reason.GUEST -> R.string.broadcast_drive_line_guest
            }
        }
        Timber.d("S4608: Drive line state=${state::class.simpleName}")
        line?.let(view::setText)
        view.visibility = if (line == null) View.GONE else View.VISIBLE
    }
}
