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
    }

    fun setupVideoControls() {
        playerView.findViewById<ImageButton>(R.id.btnPlaybackControl)?.apply {
            setOnClickListener {
                callback.showPlaybackControlDialog()
            }
            contentDescription = playerView.context.getString(R.string.control)
        }

        Timber.d("StandaloneVideoControlsManager: video controls wired")
    }

    fun updateTrackButtonsVisibility(hasMultipleAudio: Boolean, hasSubtitles: Boolean) {
        Timber.d("StandaloneVideoControlsManager: track buttons - audio=$hasMultipleAudio, subtitles=$hasSubtitles")
    }
}
