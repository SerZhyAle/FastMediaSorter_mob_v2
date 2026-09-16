package com.sza.fastmediasorter.ui.broadcast.helpers

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastFailure

/**
 * One home for the localized wording of a broadcast failure. Every entry surface (main screen,
 * control screen, and whatever routes through them) must say the same thing for the same failure -
 * a second copy of this mapping is how the control screen ended up silently swallowing
 * NETWORK_UNAVAILABLE while the main screen reported it (S3054).
 */
object BroadcastFailureMessage {

    @StringRes
    fun resFor(failure: BroadcastFailure): Int = when (failure) {
        BroadcastFailure.MICROPHONE_PERMISSION -> R.string.broadcast_failed_microphone_permission
        BroadcastFailure.NETWORK_UNAVAILABLE -> R.string.broadcast_failed_network
        BroadcastFailure.ENCODER_UNAVAILABLE -> R.string.broadcast_failed_encoder
        BroadcastFailure.CAPTURE_ERROR -> R.string.broadcast_failed_capture
    }
}
