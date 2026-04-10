package com.sza.fastmediasorter.domain.model

/**
 * Determines what happens when the user presses Back in the Player while audio
 * is playing via the background AudioPlaybackService.
 *
 * Persisted as a String value in DataStore (name of the enum constant).
 */
enum class BackgroundAudioExitBehavior {
    /** Show a dialog asking the user each time (default). */
    ASK,
    /** Stop the background audio service without showing a dialog. */
    ALWAYS_STOP,
    /** Leave background audio running without showing a dialog. */
    ALWAYS_CONTINUE
}
