package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.PhoneCameraFailure
import com.sza.fastmediasorter.wear.domain.model.PhoneCameraSessionState
import com.sza.fastmediasorter.wear.domain.repository.PhoneCameraSessionHolder
import javax.inject.Inject

/**
 * S3212: reads a dead stream as the end of the phone's camera session.
 *
 * The phone answers `STOPPED` only to a stop the watch itself sent, so a broadcast its owner ends on
 * the phone reaches this watch as nothing but a refused socket. The address is what ties the two
 * together: a player that fails on the very url the live session is being served at has lost that
 * session and no other.
 */
class EndPhoneCameraSessionOnStreamErrorUseCase @Inject constructor(
    private val holder: PhoneCameraSessionHolder
) {

    /**
     * Ends the live session when [streamUri] is the address it was served at.
     *
     * @return true when the session was the phone's camera and is now marked as ended, so the caller
     * can leave the player instead of stating an error the owner cannot act on.
     */
    operator fun invoke(streamUri: String?): Boolean {
        val live = holder.state.value as? PhoneCameraSessionState.Live
        val ended = live != null && !streamUri.isNullOrBlank() && streamUri == live.url
        if (ended) {
            holder.markRefused(PhoneCameraFailure.ENDED)
        }
        return ended
    }
}
