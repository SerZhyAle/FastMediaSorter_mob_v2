package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.Player
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior

/** The action a screen must take when the user leaves while background audio may be active. */
enum class AudioExitAction {
    /** Finish without stopping the service (lets background audio continue). */
    FINISH,

    /** Stop the service player, then finish. */
    STOP_AND_FINISH,

    /** Ask the user via the exit dialog. */
    ASK,
}

/**
 * Activity-agnostic decision for the background-audio exit flow, shared by the player and the
 * streams screen so the rule lives in one place (S0577 §5.3).
 */
object AudioExitBehaviorResolver {

    /**
     * @param serviceAudioActive whether audio is currently owned by the background service.
     * @param player the background-service player, used to detect a user-paused track.
     * @param behavior the persisted user preference.
     */
    fun resolve(
        serviceAudioActive: Boolean,
        player: Player?,
        behavior: BackgroundAudioExitBehavior,
    ): AudioExitAction {
        if (!serviceAudioActive) return AudioExitAction.FINISH

        // A still-connecting track (BUFFERING) is honoured per the saved preference; only a
        // user-paused track (READY/ENDED but not playing) exits as an implicit stop.
        val isConnecting = player?.playbackState == Player.STATE_BUFFERING
        if (player != null && !player.isPlaying && !isConnecting) {
            return AudioExitAction.STOP_AND_FINISH
        }

        return when (behavior) {
            BackgroundAudioExitBehavior.ALWAYS_STOP -> AudioExitAction.STOP_AND_FINISH
            BackgroundAudioExitBehavior.ALWAYS_CONTINUE -> AudioExitAction.FINISH
            BackgroundAudioExitBehavior.ASK -> AudioExitAction.ASK
        }
    }
}
