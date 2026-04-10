package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import android.widget.ImageButton
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * Sets up speed/audio/subtitle control buttons in the ExoPlayer controller overlay
 * for StandalonePlayerActivity. Mirrors ExoPlayerControlsManager but without
 * file navigation (no prev/next — standalone is single-file only).
 */
class StandaloneVideoControlsManager(
    private val playerView: PlayerView,
    private val callback: StandaloneVideoControlsCallback
) {

    interface StandaloneVideoControlsCallback {
        fun showPlaybackSpeedDialog()
        fun showAudioTrackDialog()
        fun showSubtitleTrackDialog()
    }

    fun setupVideoControls() {
        playerView.findViewById<ImageButton>(R.id.exo_speed)?.apply {
            setOnClickListener { callback.showPlaybackSpeedDialog() }
            contentDescription = playerView.context.getString(R.string.cd_playback_speed)
        }

        playerView.findViewById<ImageButton>(R.id.btnAudioTrack)?.apply {
            setOnClickListener { callback.showAudioTrackDialog() }
            contentDescription = playerView.context.getString(R.string.cd_audio_track)
        }

        playerView.findViewById<ImageButton>(R.id.btnSubtitleTrack)?.apply {
            setOnClickListener { callback.showSubtitleTrackDialog() }
            contentDescription = playerView.context.getString(R.string.cd_subtitle_track)
        }

        Timber.d("StandaloneVideoControlsManager: video controls wired")
    }

    fun updateTrackButtonsVisibility(hasMultipleAudio: Boolean, hasSubtitles: Boolean) {
        val btnAudioTrack = playerView.findViewById<ImageButton>(R.id.btnAudioTrack)
        val btnSubtitleTrack = playerView.findViewById<ImageButton>(R.id.btnSubtitleTrack)

        btnAudioTrack?.visibility = if (hasMultipleAudio) View.VISIBLE else View.GONE
        btnSubtitleTrack?.visibility = if (hasSubtitles) View.VISIBLE else View.GONE

        Timber.d("StandaloneVideoControlsManager: track buttons — audio=$hasMultipleAudio, subtitles=$hasSubtitles")
    }
}
