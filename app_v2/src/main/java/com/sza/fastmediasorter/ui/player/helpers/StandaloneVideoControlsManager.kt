package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import android.widget.ImageButton
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * Sets up the unified Control button in the ExoPlayer controller overlay for
 * StandalonePlayerActivity. Mirrors the main player by routing video settings
 * through a single tabbed dialog instead of split popup/actions.
 */
class StandaloneVideoControlsManager(
    private val playerView: PlayerView,
    private val callback: StandaloneVideoControlsCallback
) {

    interface StandaloneVideoControlsCallback {
        fun showPlaybackControlDialog()
        // S1114: launch VR-immersive for the current standalone video.
        fun onVrLaunchClicked()
        // S1114: true when VR entry is available now (XR device + 3D/VR master toggle).
        fun isVrEntryAvailable(): Boolean
    }

    fun setupVideoControls() {
        playerView.findViewById<ImageButton>(R.id.btnPlaybackControl)?.apply {
            setOnClickListener {
                callback.showPlaybackControlDialog()
            }
            contentDescription = playerView.context.getString(R.string.control)
        }

        // S1114: VR entry from the transport row - the standalone host has no VR badge, so this is its
        // only VR entry point. Reachable in fullscreen video via the tap-shown controller.
        playerView.findViewById<ImageButton>(R.id.btnVrLaunch)?.setOnClickListener {
            callback.onVrLaunchClicked()
        }
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (visibility == View.VISIBLE) updateVrEntryButtonVisibility()
            }
        )
        updateVrEntryButtonVisibility()

        Timber.d("StandaloneVideoControlsManager: video controls wired")
    }

    /** S1114: show the transport-row VR button only when VR entry is available now. */
    private fun updateVrEntryButtonVisibility() {
        val available = callback.isVrEntryAvailable()
        playerView.findViewById<View>(R.id.btnVrLaunch)?.visibility =
            if (available) View.VISIBLE else View.GONE
    }

    fun updateTrackButtonsVisibility(hasMultipleAudio: Boolean, hasSubtitles: Boolean) {
        Timber.d("StandaloneVideoControlsManager: track buttons - audio=$hasMultipleAudio, subtitles=$hasSubtitles")
    }
}
