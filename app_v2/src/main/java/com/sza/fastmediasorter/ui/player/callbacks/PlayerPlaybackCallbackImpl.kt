package com.sza.fastmediasorter.ui.player.callbacks

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.ImageLoadingManager
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.SlideshowController
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import com.sza.fastmediasorter.ui.player.helpers.LoadingSource
import com.sza.fastmediasorter.ui.player.helpers.PlayerSettingsManager
import com.sza.fastmediasorter.ui.player.helpers.AudioEmptyStateController
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.core.debug.MemoryEnduranceTracker
import timber.log.Timber

/**
 * Implementation of VideoPlayerManager.PlayerCallback extracted from PlayerActivity.
 * Handles events from VideoPlayerManager (ExoPlayer wrapper).
 */
class PlayerPlaybackCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel,
    private val binding: ActivityPlayerUnifiedBinding,
    private val playerSettingsManagerProvider: () -> PlayerSettingsManager,
    private val imageLoadingManagerProvider: () -> ImageLoadingManager,
    private val slideshowController: SlideshowController,
    private val sleepTimerManagerProvider: () -> com.sza.fastmediasorter.ui.player.helpers.SleepTimerManager? = { null },
    private val audioEmptyStateControllerProvider: () -> AudioEmptyStateController? = { null },
    private val mediaCapabilities: com.sza.fastmediasorter.core.capability.MediaCapabilities
) : VideoPlayerManager.PlayerCallback {

    override fun onPlaybackReady() {
        activity.slideshowResourceAvailabilityManager.onPlaybackReady()
        activity.emitPlayerReadyTraceIfNeeded()
        // S0704: playback ready - drop VIDEO_EXOPLAYER and cancel the playVideo pending show so a
        // fast-starting clip never flashes the spinner over the first frame.
        activity.loadingIndicatorCoordinator.reset(LoadingSource.VIDEO_EXOPLAYER)
        // S0685: playback started - clear any stream wait-phase label (catch-all for the stream->local path).
        binding.streamWaitLabel.isVisible = false

        // Apply player settings when ready
        playerSettingsManagerProvider().applyPlayerSettings()

        // Update track switcher buttons visibility (audio/subtitle)
        activity.updateTrackButtonsVisibility()

        // S0593: a stream URL that reached READY played OK here - record the green status in the
        // streams list. No-op for non-stream local files and http(s) media that is not a saved entry.
        val readyPath = viewModel.state.value.currentFile?.path
        if (readyPath != null && isStreamUrl(readyPath)) {
            viewModel.recordStreamPlayOk(readyPath)
        }

        // Update audio info and load cover art for audio files
        val currentFile = viewModel.state.value.currentFile
        if (currentFile?.type == MediaType.AUDIO) {
            activity.updateAudioFormatInfo()
            imageLoadingManagerProvider().loadAudioCoverArt(currentFile)
            activity.prefetchNextAudio()
            // Refresh song label in audio slideshow photo mode (covers auto-advance case)
            activity.updateAudioSlideshowCurrentSongLabel()
        }
    }
    
    override fun onPlaybackError(error: Throwable, userMessage: String?) {
        if (activity.slideshowResourceAvailabilityManager.handlePlaybackError(error, userMessage)) {
            return
        }
        // S0581: a list stream that did not respond gets a friendly retry / remove-from-list dialog
        // instead of a silent skip. The ViewModel resolves the URL against the stored streams and
        // only shows the dialog when it maps to a saved row; otherwise the generic path runs.
        val currentPath = viewModel.state.value.currentFile?.path
        if (currentPath != null && isStreamUrl(currentPath)) {
            viewModel.onStreamPlaybackFailed(currentPath)
            return
        }
        if (userMessage != null) {
            // WHY: timeout-specific feedback should replace the generic skip toast, not stack with it.
            Toast.makeText(activity, userMessage, Toast.LENGTH_LONG).show()
            activity.navigationManager.navigateNextFromControl(manual = false)
            return
        }
        activity.handleMediaLoadErrorAndSkip()
    }

    private fun isStreamUrl(path: String): Boolean {
        val lower = path.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("rtsp://")
    }
    
    override fun onBuffering(isBuffering: Boolean) {
        if (isBuffering) {
            activity.loadingIndicatorCoordinator.show(LoadingSource.VIDEO_EXOPLAYER)
        } else {
            activity.loadingIndicatorCoordinator.hide(LoadingSource.VIDEO_EXOPLAYER)
            // S0685: spinner gone -> drop the wait-phase label too (the stream listener also clears it
            // explicitly, this covers the generic non-stream buffering-off path).
            binding.streamWaitLabel.isVisible = false
        }
    }

    override fun onStreamWaitPhase(phase: VideoPlayerManager.StreamWaitPhase?) {
        val label = binding.streamWaitLabel
        if (phase == null) {
            label.isVisible = false
            return
        }
        label.setText(
            when (phase) {
                VideoPlayerManager.StreamWaitPhase.BUFFERING -> R.string.stream_wait_buffering
                VideoPlayerManager.StreamWaitPhase.RECONNECTING -> R.string.stream_wait_reconnecting
            }
        )
        label.isVisible = true
    }

    /**
     * S1158: the programme caption is deliberately not cleared where [streamWaitLabel] is - those two
     * resets fire when playback becomes ready or buffering ends, and a programme name has to survive
     * its own stream starting to play. The reset lives in `VideoPlayerManager.playVideo`.
     */
    override fun onStreamProgramName(name: String?) {
        val label = binding.streamProgramLabel
        val text = name?.trim().orEmpty()
        Timber.d("S1158: programme caption text='$text' visible=${text.isNotEmpty()}")
        if (text.isEmpty()) {
            label.isVisible = false
            return
        }
        label.text = text
        label.isVisible = true
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        val currentFile = viewModel.state.value.currentFile
        val isAudioFile = currentFile?.type == MediaType.AUDIO
        sleepTimerManagerProvider()?.updateVinylState(isPlaying, isAudioFile)
        if (isAudioFile) {
            audioEmptyStateControllerProvider()?.onIsPlayingChanged(isPlaying)
        }
    }
    
    override fun onPlaybackEnded() {
        val wasAudio = viewModel.state.value.currentFile?.type == com.sza.fastmediasorter.domain.model.MediaType.AUDIO
        // S0120: track auto-advance transitions with correct scenario name per media type
        MemoryEnduranceTracker.checkpoint("TRANSITION", if (wasAudio) "AUD-playback" else "VID-playback")
        if (viewModel.state.value.isSlideShowActive && activity.slideshowResourceAvailabilityManager.handlePlaybackEnded()) {
            return
        }
        if (viewModel.state.value.isSlideShowActive) {
            Timber.tag("TOUCH_ZONE_DEBUG").d("NEXT triggered by: Playback ended (slideshow)")
            viewModel.nextFile(skipDocuments = true)
            slideshowController.restartTimer()
            // Advance background photo on audio track auto-advance (mirrors navigateNext behaviour)
            if (wasAudio) {
                activity.advanceAudioBackgroundPhoto()
            }
        }
    }
    
    override fun onAudioFormatChanged(format: VideoPlayerManager.AudioFormat?) {
        // Not used currently
    }
    
    override fun showError(message: String) {
        activity.showError(message)
    }

    override fun showFileNotFound(fileName: String) {
        activity.showFileNotFound(fileName)
    }

    override fun isActivityDestroyed(): Boolean {
        return activity.isDestroyed || activity.isFinishing
    }
    
    override fun showUnsupportedFormatError(message: String, filePath: String, isLocalFile: Boolean) {
        activity.showUnsupportedFormatError(message, filePath, isLocalFile)
    }

    override fun onBdTsFormatError() {
        if (activity.isDestroyed || activity.isFinishing) return
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.error_bdts_format_title))
            .setMessage(activity.getString(R.string.error_bdts_format_message))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onNetworkContainerRouteError(
        path: String,
        hint: com.sza.fastmediasorter.ui.player.helpers.NetworkPlaybackContainerHint
    ) {
        if (activity.isDestroyed || activity.isFinishing) return
        Timber.w("PlayerPlaybackCallbackImpl: VOB route error, stopping on current file: $path")
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.error_vob_route_title))
            .setMessage(activity.getString(R.string.error_vob_route_message))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onBeforeVideoLoad(path: String) {
        // Keep explicit per-file behaviour predictable: every new video starts from a clean
        // detection state, but remembered VR format may still seed the effective renderer mode.
        viewModel.resetStereoModeForNewFile(path)
        // Reset flag so the 3D toast can fire once for the new file.
        stereoToastShownForCurrentFile = false
    }

    // Tracks whether the "3D detected" toast has already been shown for the current file.
    // Resets in onBeforeVideoLoad() so each new file gets one toast chance.
    private var stereoToastShownForCurrentFile = false

    override fun onDecoderCooldownReentry(path: String, remainingSec: Int) {
        // S0213 Pillar A: route manual decoder-cooldown re-entry to the snackbar (with Skip action).
        if (activity.isDestroyed || activity.isFinishing) return
        activity.dialogAndUiStateManager.showDecoderCooldownSnackbar(remainingSec) {
            viewModel.nextFile(skipDocuments = true)
        }
    }

    override fun onStereoDetected(mode: StereoMode, forFilePath: String) {
        // Pass auto-detected mode to ViewModel; it will only apply if user is still on AUTO.
        viewModel.setAutoDetectedStereoMode(mode, forFilePath)

        // S0264/S0969: VR install CTA must not appear on VR-capable builds (vr / noLegal). Gate on
        // supportsVrMediaControls (true on BOTH vr and noLegal), NOT supportsVrPlayer - the latter is
        // false on the `vr` Store flavor (S0241 gate divergence), so it let the "install the VR edition"
        // prompt fire on the VR edition itself. supportsVrMediaControls tracks actual VR capability.
        if (mediaCapabilities.supportsVrMediaControls) return

        // Show VR CTA for actual flat 3D content (SBS/OU), not for MONO/AUTO/UNKNOWN.
        val is3d = mode == StereoMode.SBS_FULL || mode == StereoMode.SBS_HALF || mode == StereoMode.OU
        if (is3d && !stereoToastShownForCurrentFile && !activity.isDestroyed && !activity.isFinishing) {
            stereoToastShownForCurrentFile = true
            // Show CTA dialog suggesting VR edition for detected 3D content.
            Timber.d("PlayerPlaybackCallbackImpl: showing VR install CTA for mode=$mode")
            viewModel.showVrInstallCta(mode)
        }
    }
}
