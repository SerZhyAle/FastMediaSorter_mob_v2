package com.sza.fastmediasorter.wear.ui.listen

/**
 * S3164: what the request screen shows, which is one state more than the session has.
 *
 * `ListenSessionState.Idle` means "no session" and the screen renders it as the incoming request,
 * with no action because the auto-start answers it within a frame. The capture service publishes that
 * same `Idle` when a session that already ran ends, so a finished session rendered as an unanswered
 * request: the caption asked for a confirmation that had already been given, no chip offered a way
 * out, and the system Back was the only exit. Separating the two is a screen decision and lives here.
 */
sealed interface ListenRequestUiState {

    /** The request has arrived and the microphone has not been asked for yet. */
    data object Requesting : ListenRequestUiState

    /** The confirmation is given and the microphone is opening. */
    data object Starting : ListenRequestUiState

    /** The microphone is live and the phone can hear it. */
    data object Live : ListenRequestUiState

    /** The microphone or the server refused to start. */
    data object Failed : ListenRequestUiState

    /** The session this screen started is over - by a stop, a dropped peer or the watch's own timer. */
    data object Ended : ListenRequestUiState
}
