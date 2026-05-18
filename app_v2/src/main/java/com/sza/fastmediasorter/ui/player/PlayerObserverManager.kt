package com.sza.fastmediasorter.ui.player

import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages all ViewModel state observation for PlayerActivity.
 * Extracted from PlayerActivity.observeViewModel() + updateUI() to keep the activity slim.
 *
 * Call [startObserving] once from PlayerActivity.observeData().
 */
internal class PlayerObserverManager(
    private val activity: PlayerActivity,
    private val settingsRepository: SettingsRepository
) {
    fun startObserving() {
        val viewModel = activity.viewModel

        activity.lifecycleScope.launch {
            activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state
                        .distinctUntilChangedBy {
                            Triple(
                                // isPaused must participate in UI observation because pause/resume
                                // drives filename overlay re-show logic and several playback affordances.
                                Triple(it.currentIndex, it.currentFile?.path, Pair(it.isSlideShowActive, it.isPaused)),
                                it.showCommandPanel,
                                it.currentFile?.isFavorite
                            )
                        }
                        .collect { state ->
                            updateUI(state)
                            activity.backgroundMusicManager.updateState(state)
                            activity.audioBackgroundPhotosManager.updateState(state)
                        }
                }

                launch {
                    viewModel.loading.collect { isLoading ->
                        val currentType = viewModel.state.value.currentFile?.type
                        if (currentType != MediaType.PDF && currentType != MediaType.EPUB) {
                            activity.activityBinding.progressBar.isVisible = isLoading
                        }
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        activity.eventHandler.handleEvent(event)
                    }
                }

                launch {
                    combine(
                        settingsRepository.getSettings().distinctUntilChanged(),
                        viewModel.state.distinctUntilChangedBy { it.resource?.id }
                    ) { settings, state ->
                        activity.currentSettings = settings
                        activity.loadFullSizeImages = settings.loadFullSizeImages

                        activity.imageLoadingManager.setDynamicBackgroundEnabled(settings.dynamicBackgroundExtension)
                        // currentFile may be null at resource-switch time; updateUI() will correct
                        // PiP visibility once the file type is known.
                        val isAudio = state.currentFile?.type == MediaType.AUDIO
                        activity.pipManager?.setupPipButton(settings.enablePictureInPicture, isAudio)

                        // S0162: re-apply orientation whenever followSystemRotation changes
                        activity.screenRotationManager.apply(
                            activity,
                            settings.followSystemRotation,
                            settings.playerRotationSensorEnabled,
                            activity.hasAccelerometer
                        )
                        activity.commandPanelController.updateRotationToggleIcon(settings.playerRotationSensorEnabled)

                        settings.enableFavorites || state.resource?.id == -100L
                    }.collect { shouldShow ->
                        activity.activityBinding.btnFavorite.isVisible = shouldShow
                    }
                }

                launch {
                    viewModel.state
                        .distinctUntilChangedBy { it.imageEditMode }
                        .collect { state ->
                            activity.immersiveModeManager.apply(state.imageEditMode)
                        }
                }
            }
        }
    }

    fun updateUI(state: PlayerViewModel.PlayerState) {
        activity.uiStateCoordinator.updateUI(state)
        val isAudio = state.currentFile?.type == MediaType.AUDIO
        if (!isAudio) {
            activity.sleepTimerManager?.stopVinylAnimation()
        }
        // Re-evaluate PiP button on every file change - audio files must not show PiP.
        activity.pipManager?.setupPipButton(
            activity.currentSettings?.enablePictureInPicture == true,
            isAudio
        )
        if (activity.audioSlideshowPhotoModeManager.isActive) {
            activity.audioSlideshowPhotoModeManager.enforceUI()
        }
    }
}
