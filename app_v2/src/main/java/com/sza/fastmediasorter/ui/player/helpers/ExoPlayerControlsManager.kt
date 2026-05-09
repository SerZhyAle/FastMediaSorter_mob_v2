package com.sza.fastmediasorter.ui.player.helpers

import android.widget.ImageButton
import androidx.media3.common.Player
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber
import com.sza.fastmediasorter.utils.UserActionLogger

/**
 * Manages ExoPlayer custom controls setup and state for PlayerActivity.
 * 
 * Responsibilities:
 * - Setup custom navigation buttons (previous/next file)
 * - Setup repeat mode button with icon updates
 * - Setup unified playback control button
 * - Setup rewind/forward buttons for audiobook mode
 * - Update repeat button icon based on player state
 * 
 * Custom controls in ExoPlayer controller overlay:
 * - exo_prev_file: Navigate to previous file
 * - exo_next_file: Navigate to next file
 * - exo_repeat: Toggle repeat mode (OFF -> ONE -> OFF)
 * - btnPlaybackControl: Open unified Control dialog
 * - btnRewind10: Seek backward 10 seconds (audiobook)
 * - btnForward30: Seek forward 30 seconds (audiobook)
 */
class ExoPlayerControlsManager(
    private val binding: ActivityPlayerUnifiedBinding,
    private val videoPlayerManager: VideoPlayerManager,
    private val callback: ExoPlayerControlsCallback
) {
    
    interface ExoPlayerControlsCallback {
        fun onPreviousFile()
        fun onNextFile()
        fun showPlaybackControlDialog()
        fun onSeekForward(seconds: Int)
        fun onSeekBackward(seconds: Int)
    }
    
    /**
     * Setup all custom ExoPlayer navigation buttons and controls.
     * Called once during PlayerActivity initialization.
     */
    fun setupExoPlayerNavigationButtons() {
        // Set PlayerView for VideoPlayerManager (required for video rendering)
        videoPlayerManager.setPlayerView(binding.playerView)
        
        // Find custom navigation buttons in PlayerView's controller
        binding.playerView.findViewById<ImageButton>(R.id.exo_prev_file)?.setOnClickListener {
            UserActionLogger.logButtonClick("ExoPrevFile", "ExoPlayerControlsManager")
            // S0120: track manual video navigation; always VID-playback per spec matrix
            MemoryEnduranceTracker.checkpoint("TRANSITION", "VID-playback")
            callback.onPreviousFile()
        }
        binding.playerView.findViewById<ImageButton>(R.id.exo_next_file)?.setOnClickListener {
            UserActionLogger.logButtonClick("ExoNextFile", "ExoPlayerControlsManager")
            // S0120: track manual video navigation; always VID-playback per spec matrix
            MemoryEnduranceTracker.checkpoint("TRANSITION", "VID-playback")
            callback.onNextFile()
        }
        
        // Setup repeat mode button
        val repeatButton = binding.playerView.findViewById<ImageButton>(R.id.exo_repeat)
        repeatButton?.setOnClickListener {
            videoPlayerManager.getPlayer()?.let { player ->
                val newRepeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                    else -> Player.REPEAT_MODE_OFF
                }
                videoPlayerManager.setRepeatMode(newRepeatMode)
                updateRepeatButtonIcon()
            }
        }
        
        // Consolidate speed/audio/subtitle actions behind a single bottom-bar entry point.
        binding.playerView.findViewById<ImageButton>(R.id.btnPlaybackControl)?.setOnClickListener {
            UserActionLogger.logButtonClick("PlaybackControl", "ExoPlayerControlsManager")
            callback.showPlaybackControlDialog()
        }
        
        // Setup rewind/forward buttons for audiobook mode
        val btnRewind = binding.playerView.findViewById<ImageButton>(R.id.btnRewind10)
        val btnForward = binding.playerView.findViewById<ImageButton>(R.id.btnForward30)
        
        Timber.d("ExoPlayerControlsManager: btnRewind10 = ${btnRewind}, btnForward30 = ${btnForward}")
        
        btnRewind?.setOnClickListener {
            UserActionLogger.logButtonClick("Rewind10", "ExoPlayerControlsManager")
            callback.onSeekBackward(10)
        }

        btnForward?.setOnClickListener {
            UserActionLogger.logButtonClick("Forward10", "ExoPlayerControlsManager")
            callback.onSeekForward(10)
        }
        
        // Initial repeat button state
        updateRepeatButtonIcon()
    }
    
    /**
     * Update repeat button icon based on current player repeat mode.
     * 
     * Icon states:
     * - REPEAT_MODE_OFF: ic_repeat with 50% alpha (dimmed)
     * - REPEAT_MODE_ONE: ic_repeat_one with 100% alpha
     * 
     * Call this after:
     * - Player initialization
     * - Repeat mode change
     * - Loading new video/audio file
     */
    fun updateRepeatButtonIcon() {
        val repeatButton = binding.playerView.findViewById<ImageButton>(R.id.exo_repeat)
        videoPlayerManager.getPlayer()?.let { player ->
            val iconRes = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                else -> R.drawable.ic_repeat
            }
            repeatButton?.setImageResource(iconRes)
            
            // Visual feedback: dim icon when repeat is OFF
            val alpha = if (player.repeatMode == Player.REPEAT_MODE_OFF) 0.5f else 1.0f
            repeatButton?.alpha = alpha
        }
    }

    /**
     * Keep the bottom Control entry point visible for both video and audio playback.
     * The dialog itself narrows audio down to music-relevant controls only.
     */
    fun updateTrackButtonsVisibility(isPlaybackControlSupported: Boolean) {
        binding.playerView.findViewById<ImageButton>(R.id.btnPlaybackControl)?.visibility =
            if (isPlaybackControlSupported) android.view.View.VISIBLE else android.view.View.GONE
        Timber.d("ExoPlayerControlsManager: Control button visibility updated — supported=$isPlaybackControlSupported")
    }
}
