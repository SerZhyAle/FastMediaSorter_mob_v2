package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.xr.StartVrPlaybackUseCase
import com.sza.fastmediasorter.core.xr.VrLaunchInput
import com.sza.fastmediasorter.core.xr.VrLaunchMode
import com.sza.fastmediasorter.core.xr.VrLaunchPoint
import com.sza.fastmediasorter.core.xr.VrLaunchResult
import com.sza.fastmediasorter.core.xr.VrLaunchUnavailableReason
import com.sza.fastmediasorter.core.xr.VrMediaType
import com.sza.fastmediasorter.core.xr.VrPanelReturnTarget
import com.sza.fastmediasorter.core.xr.XrDetectionFacade
import com.sza.fastmediasorter.core.xr.XrDetectionState
import com.sza.fastmediasorter.core.xr.readSerializableExtraCompat
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class PlayerVrLaunchManager(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel,
    private val settingsRepository: SettingsRepository,
    private val detectionFacade: XrDetectionFacade,
    private val startVrPlaybackUseCase: StartVrPlaybackUseCase,
) {
    private val safeViews = PlayerBindingSafeViews(activity.activityBinding)

    private var latestSurfaceState = SurfaceState()
    private var transientBadgeState: TransientBadgeState? = null
    private var launchJob: Job? = null
    private var pendingReturnTarget: VrPanelReturnTarget.Player? = null
    private var pendingReturnResult: VrLaunchResult? = null

    fun bind() {
        safeViews.vrLaunchBadgeContainer.setOnClickListener { launchFromBadge() }
        safeViews.btnVrLaunchPromptOpenSettings.setOnClickListener { dismissPromptAndOpenSettings() }
        safeViews.btnVrLaunchPromptDismiss.setOnClickListener { dismissPrompt() }

        consumeReturnIntent()
        render()

        activity.lifecycleScope.launch {
            activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.state,
                    detectionFacade.state(),
                    viewModel.settings,
                ) { playerState, detectionState, settings ->
                    buildSurfaceState(playerState, detectionState, settings.vrPlayerEntryPromptDismissed)
                }.collect { surfaceState ->
                    val overflowChanged = latestSurfaceState.overflowEntryVisible != surfaceState.overflowEntryVisible
                    val fileChanged = latestSurfaceState.filePath != surfaceState.filePath
                    latestSurfaceState = surfaceState
                    if (fileChanged) {
                        transientBadgeState = null
                    }
                    render()
                    if (overflowChanged) {
                        activity.commandPanelController.updateCommandAvailability(viewModel.state.value)
                    }
                    applyPendingReturnIfReady()
                }
            }
        }
    }

    fun isOverflowEntryVisible(): Boolean = latestSurfaceState.overflowEntryVisible

    fun overlayViewsForAutoHide(): List<View> = buildList {
        val shouldMirrorBadge = latestSurfaceState.badgeVisible || transientBadgeState != null
        if (shouldMirrorBadge) add(safeViews.vrLaunchBadgeContainer)
        if (latestSurfaceState.showPrompt) add(safeViews.vrLaunchPromptCard)
    }

    fun launchFromBadge() {
        launch(VrLaunchPoint.PLAYER_BADGE)
    }

    fun launchFromOverflow() {
        launch(VrLaunchPoint.OVERFLOW_MENU)
    }

    private fun launch(source: VrLaunchPoint) {
        if (launchJob?.isActive == true) return
        val currentFile = viewModel.state.value.currentFile ?: return
        if (currentFile.type !in VR_ENTRY_MEDIA_TYPES) return

        launchJob = activity.lifecycleScope.launch {
            transientBadgeState = TransientBadgeState.Preparing
            render()

            val snapshot = captureSnapshot(currentFile)
            val request = try {
                buildStartRequest(currentFile, snapshot, source)
            } catch (error: Throwable) {
                Timber.e(error, "PlayerVrLaunchManager: failed to prepare launch request")
                transientBadgeState = TransientBadgeState.Error(R.string.player_vr_badge_error_retry)
                render()
                return@launch
            }

            val returnTarget = buildPlayerReturnTarget(currentFile, snapshot)
            when (val result = startVrPlaybackUseCase(request, returnTarget)) {
                StartVrPlaybackUseCase.DispatchResult.Started -> {
                    markPromptDismissed()
                    activity.finishAndRemoveTask()
                }
                is StartVrPlaybackUseCase.DispatchResult.Unavailable -> {
                    transientBadgeState = TransientBadgeState.Error(
                        badgeErrorMessageFor(result.reason)
                    )
                    render()
                }
                is StartVrPlaybackUseCase.DispatchResult.Failed -> {
                    transientBadgeState = TransientBadgeState.Error(R.string.player_vr_badge_error_retry)
                    render()
                }
            }
        }
    }

    private suspend fun buildStartRequest(
        currentFile: MediaFile,
        snapshot: com.sza.fastmediasorter.core.xr.PlayerStateSnapshot,
        source: VrLaunchPoint,
    ): com.sza.fastmediasorter.core.xr.StartVrPlaybackRequest {
        val mediaType = currentFile.type.toVrMediaType()
        val fileUriString = when (mediaType) {
            VrMediaType.IMAGE -> {
                val localFile = withContext(Dispatchers.IO) {
                    activity.networkFileManager.prepareFileForRead(currentFile)
                }
                Uri.fromFile(localFile).toString()
            }
            VrMediaType.VIDEO,
            VrMediaType.GIF -> currentFile.toLaunchUriString()
        }
        return com.sza.fastmediasorter.core.xr.StartVrPlaybackRequest(
            launchMode = VrLaunchMode.FILE_URI,
            fileUriString = fileUriString,
            mediaType = mediaType,
            source = source,
            snapshot = snapshot,
        )
    }

    private fun buildPlayerReturnTarget(
        currentFile: MediaFile,
        snapshot: com.sza.fastmediasorter.core.xr.PlayerStateSnapshot,
    ): VrPanelReturnTarget.Player {
        return VrPanelReturnTarget.Player(
            resourceId = viewModel.state.value.resourceId,
            windowId = activity.windowId,
            sourceFilePath = currentFile.path,
            playlistIndex = viewModel.state.value.currentIndex,
            resumeIsPlaying = !viewModel.state.value.isPaused,
            resumeSlideshowEnabled = viewModel.state.value.isSlideShowActive,
            detectedStereoModeName = viewModel.detectedStereoMode.value.name,
            snapshot = snapshot,
        )
    }

    private fun captureSnapshot(
        currentFile: MediaFile,
    ): com.sza.fastmediasorter.core.xr.PlayerStateSnapshot {
        val photoRect = activity.activityBinding.photoView.displayRect
        val videoPlayer = activity._videoPlayerManager?.getPlayer()
        return com.sza.fastmediasorter.core.xr.PlayerStateSnapshot(
            fileUriString = currentFile.path,
            playlistIndex = viewModel.state.value.currentIndex,
            videoPositionMs = videoPlayer?.currentPosition ?: 0L,
            videoPlaybackSpeed = videoPlayer?.playbackParameters?.speed ?: 1.0f,
            photoZoom = activity.activityBinding.photoView.scale,
            photoPanX = photoRect?.centerX() ?: 0f,
            photoPanY = photoRect?.centerY() ?: 0f,
            commandPanelVisible = activity.activityBinding.topCommandPanel.isVisible,
            fullscreen = !activity.activityBinding.topCommandPanel.isVisible,
            slideshowState = viewModel.state.value.isSlideShowActive.toString(),
            videoIsPlaying = videoPlayer?.isPlaying == true,
            videoVolume = videoPlayer?.volume ?: 1.0f,
        )
    }

    private fun consumeReturnIntent() {
        pendingReturnResult = activity.intent.readSerializableExtraCompat(VrLaunchInput.EXTRA_LAUNCH_RESULT)
        pendingReturnTarget = activity.intent
            .readSerializableExtraCompat<VrPanelReturnTarget>(VrLaunchInput.EXTRA_RETURN_TARGET)
            as? VrPanelReturnTarget.Player
    }

    private fun applyPendingReturnIfReady() {
        val target = pendingReturnTarget ?: return
        val result = pendingReturnResult ?: return
        val currentFilePath = viewModel.state.value.currentFile?.path ?: return
        if (currentFilePath != target.sourceFilePath) return

        if (target.snapshot.commandPanelVisible) {
            viewModel.enterCommandPanelMode()
            activity.isExplicitFullscreenMode = false
        } else {
            viewModel.enterFullscreenMode()
            activity.isExplicitFullscreenMode = target.snapshot.fullscreen
        }

        if (result == VrLaunchResult.CompletedNormally || result == VrLaunchResult.CancelledByUser) {
            val videoPlayer = activity._videoPlayerManager?.getPlayer()
            if (videoPlayer != null) {
                videoPlayer.seekTo(target.snapshot.videoPositionMs)
                videoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(target.snapshot.videoPlaybackSpeed)
                videoPlayer.volume = target.snapshot.videoVolume
                if (target.snapshot.videoIsPlaying) {
                    videoPlayer.play()
                } else {
                    videoPlayer.pause()
                }
            }
        }

        when (result) {
            is VrLaunchResult.Crashed -> showReturnSnackbar(R.string.player_vr_return_crashed)
            is VrLaunchResult.Unavailable -> showReturnSnackbar(R.string.player_vr_return_unavailable)
            VrLaunchResult.CancelledByUser,
            VrLaunchResult.CompletedNormally -> Unit
        }

        activity.intent.removeExtra(VrLaunchInput.EXTRA_LAUNCH_RESULT)
        activity.intent.removeExtra(VrLaunchInput.EXTRA_RETURN_TARGET)
        pendingReturnTarget = null
        pendingReturnResult = null
    }

    private fun showReturnSnackbar(messageRes: Int) {
        Snackbar.make(activity.activityBinding.root, messageRes, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_retry) { launchFromBadge() }
            .show()
    }

    private fun render() {
        val showPrompt = latestSurfaceState.showPrompt && shouldShowBadgeChrome()
        val chromeVisible = shouldShowBadgeChrome()
        val showBadge = when (val transientState = transientBadgeState) {
            null -> latestSurfaceState.badgeVisible && chromeVisible && !showPrompt
            TransientBadgeState.Preparing -> true
            is TransientBadgeState.Error -> latestSurfaceState.eligibleMedia && !showPrompt && chromeVisible
        }

        safeViews.vrLaunchPromptCard.isVisible = showPrompt
        safeViews.vrLaunchPromptCard.isFocusable = showPrompt
        safeViews.vrLaunchPromptCard.isClickable = showPrompt
        safeViews.btnVrLaunchPromptOpenSettings.isFocusable = showPrompt
        safeViews.btnVrLaunchPromptDismiss.isFocusable = showPrompt

        safeViews.vrLaunchBadgeContainer.isVisible = showBadge
        safeViews.vrLaunchBadgeContainer.isFocusable = showBadge
        safeViews.vrLaunchBadgeContainer.isClickable = showBadge
        safeViews.progressVrLaunchBadge.isVisible = transientBadgeState == TransientBadgeState.Preparing
        safeViews.ivVrLaunchBadgeIcon.isVisible = transientBadgeState != TransientBadgeState.Preparing
        safeViews.tvVrLaunchBadgeText.text = when (val transientState = transientBadgeState) {
            null -> activity.getString(R.string.player_vr_badge_label)
            TransientBadgeState.Preparing -> activity.getString(R.string.player_vr_badge_loading)
            is TransientBadgeState.Error -> activity.getString(transientState.messageRes)
        }
        safeViews.vrLaunchBadgeContainer.contentDescription = when (transientBadgeState) {
            TransientBadgeState.Preparing -> activity.getString(R.string.player_vr_badge_loading)
            else -> activity.getString(R.string.player_vr_overflow_open)
        }

        updateOverlayMargins()
    }

    private fun updateOverlayMargins() {
        activity.activityBinding.root.post {
            val defaultTop = activity.resources.getDimensionPixelSize(R.dimen.player_epub_button_margin_top)
            val defaultEnd = activity.resources.getDimensionPixelSize(R.dimen.player_epub_button_margin_end)
            val spacing = activity.resources.getDimensionPixelSize(R.dimen.margin_small)
            val anchors = listOfNotNull(
                activity.activityBinding.btnTouchZonesHelp?.takeIf { it.isVisible },
                activity.activityBinding.tvAnimatedBadge.takeIf { it.isVisible },
                activity.activityBinding.sleepTimerBadge.takeIf { it.isVisible },
                activity.activityBinding.tvPlayerFpsOverlay.takeIf { it.isVisible },
                activity.activityBinding.btnExitEpubFullscreen?.takeIf { it.isVisible },
            )
            val top = anchors.fold(defaultTop) { currentTop, view ->
                maxOf(currentTop, view.top + view.height + spacing)
            }
            updateLayoutParams(safeViews.vrLaunchBadgeContainer, top, defaultEnd)
            updateLayoutParams(safeViews.vrLaunchPromptCard, top, defaultEnd)
        }
    }

    private fun updateLayoutParams(view: View, topMargin: Int, endMargin: Int) {
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.topMargin == topMargin && params.marginEnd == endMargin) return
        params.topMargin = topMargin
        params.marginEnd = endMargin
        view.layoutParams = params
    }

    private fun shouldShowBadgeChrome(): Boolean {
        return activity.activityBinding.topCommandPanel.isVisible ||
            activity.activityBinding.controlsOverlay.isVisible ||
            activity.activityBinding.tvFileNameOverlay.isVisible ||
            (activity.activityBinding.btnTouchZonesHelp?.isVisible == true)
    }

    private fun buildSurfaceState(
        playerState: PlayerViewModel.PlayerState,
        detectionState: XrDetectionState,
        promptDismissed: Boolean,
    ): SurfaceState {
        val currentFile = playerState.currentFile
        val eligibleMedia = currentFile?.type in VR_ENTRY_MEDIA_TYPES
        return SurfaceState(
            filePath = currentFile?.path,
            eligibleMedia = eligibleMedia,
            badgeVisible = eligibleMedia && detectionState == XrDetectionState.AVAILABLE_ENABLED,
            overflowEntryVisible = eligibleMedia && detectionState == XrDetectionState.AVAILABLE_ENABLED,
            showPrompt = eligibleMedia &&
                detectionState == XrDetectionState.AVAILABLE_DISABLED_BY_USER &&
                !promptDismissed,
        )
    }

    private suspend fun markPromptDismissed() {
        val settings = viewModel.settings.value
        if (settings.vrPlayerEntryPromptDismissed) return
        settingsRepository.updateSettings(settings.copy(vrPlayerEntryPromptDismissed = true))
    }

    private fun dismissPrompt() {
        activity.lifecycleScope.launch {
            markPromptDismissed()
        }
    }

    private fun dismissPromptAndOpenSettings() {
        activity.lifecycleScope.launch {
            markPromptDismissed()
            activity.startActivity(
                MainActivity.createReturnToSettingsIntent(activity, 1)
            )
        }
    }

    private fun badgeErrorMessageFor(reason: VrLaunchUnavailableReason): Int {
        return when (reason) {
            VrLaunchUnavailableReason.NotYetSupported -> R.string.player_vr_badge_error_not_supported
            VrLaunchUnavailableReason.DisabledByUser -> R.string.player_vr_prompt_message
            VrLaunchUnavailableReason.NoRuntime,
            VrLaunchUnavailableReason.RuntimeDied,
            VrLaunchUnavailableReason.InvalidUri,
            VrLaunchUnavailableReason.DecoderFailed -> R.string.player_vr_badge_error_retry
        }
    }

    private fun MediaType.toVrMediaType(): VrMediaType = when (this) {
        MediaType.IMAGE -> VrMediaType.IMAGE
        MediaType.VIDEO -> VrMediaType.VIDEO
        MediaType.GIF -> VrMediaType.GIF
        else -> error("Unsupported VR launch media type: $this")
    }

    private fun MediaFile.toLaunchUriString(): String {
        return when {
            path.startsWith("file://") || path.startsWith("content://") -> path
            path.startsWith("/") -> Uri.fromFile(File(path)).toString()
            else -> path
        }
    }

    private data class SurfaceState(
        val filePath: String? = null,
        val eligibleMedia: Boolean = false,
        val badgeVisible: Boolean = false,
        val overflowEntryVisible: Boolean = false,
        val showPrompt: Boolean = false,
    )

    private sealed interface TransientBadgeState {
        data object Preparing : TransientBadgeState
        data class Error(val messageRes: Int) : TransientBadgeState
    }

    private companion object {
        val VR_ENTRY_MEDIA_TYPES = setOf(MediaType.VIDEO, MediaType.IMAGE, MediaType.GIF)
    }
}
