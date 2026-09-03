package com.sza.fastmediasorter.wear.domain.playback

/** What a player screen does with its session when its host stops. */
enum class HostStopAction {
    /** Give the session to the foreground service, which keeps the sound going. */
    HandOff,

    /** Pause the player and release the stream hold, the behaviour S0902 introduced. */
    Pause
}

/** Why a host went away, named at the call site so the rule below is not asked with a boolean. */
enum class HostTeardownReason {
    /** The owner pressed the close button: the one gesture that says "finished" (ADR-4). */
    ExplicitExit,

    /** The app went to the background with the watch face on top - the case this ticket exists for. */
    Minimized,

    /** The display timed out or the wrist dropped. */
    ScreenOff,

    /** Navigation away from the player, with the app still open. */
    LeftPlayerScreen
}

/**
 * S2166: decides whether a stopping player screen hands its session to the playback service.
 *
 * The decision lives outside the ViewModels because strategic §7 records that none of the three watch
 * players carries a unit test, so a lifecycle change across them has no regression net unless the rule
 * itself is a pure function with cases of its own.
 */
object WearBackgroundPlaybackPolicy {

    /**
     * A player that is not playing pauses whatever the setting says: there is no sound to carry over,
     * and starting a foreground service for a paused player would post a media notification over
     * silence - which is the same failure strategic §7 names for a service outliving its sound.
     *
     * Video and slide-shows never reach [HostStopAction.HandOff] (ADR-1): the minimized app has no
     * surface to show them on, so background video would be a different capability, not this one.
     */
    fun onHostStopped(
        backgroundPlaybackEnabled: Boolean,
        isAudioContent: Boolean,
        isPlaying: Boolean
    ): HostStopAction =
        if (backgroundPlaybackEnabled && isAudioContent && isPlaying) {
            HostStopAction.HandOff
        } else {
            HostStopAction.Pause
        }

    /**
     * ADR-4, taken from the owner's own contrast - "выйти из приложения, не перевыйти, а свернуть
     * его". Only the exit means finished; the other three are the gestures the sound is meant to
     * survive, and treating any of them as an ending is the behaviour this ticket removes.
     */
    fun stopsBackgroundSession(reason: HostTeardownReason): Boolean =
        reason == HostTeardownReason.ExplicitExit
}
