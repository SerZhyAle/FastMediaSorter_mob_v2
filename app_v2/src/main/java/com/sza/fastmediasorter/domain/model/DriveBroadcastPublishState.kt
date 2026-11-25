package com.sza.fastmediasorter.domain.model

/**
 * Where the live broadcast stands on the person's Google Drive (contract DEVICE-EXCHANGE items BC and CL): only
 * its announcement record ever goes there, never the stream.
 */
sealed interface DriveBroadcastPublishState {

    /** Nothing is live, or the Drive channel is off: the screen shows no line. */
    data object Idle : DriveBroadcastPublishState

    /** The first record of this session is being written. */
    data object Announcing : DriveBroadcastPublishState

    /** The record stands on Drive and is renewed while the broadcast is live. */
    data object Announced : DriveBroadcastPublishState

    data class NotAnnounced(val reason: Reason) : DriveBroadcastPublishState {
        enum class Reason {
            /** The app has no Drive access: the person must sign in again. */
            NOT_SIGNED_IN,

            /** Drive did not answer; the next renewal tries again. */
            UNREACHABLE,

            /** The outgoing exchange entry is a guest one, and a guest announces nothing anywhere. */
            GUEST,
        }
    }
}
