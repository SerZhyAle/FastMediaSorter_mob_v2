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
                                Triple(it.currentIndex, it.currentFile?.path, it.isSlideShowActive),
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
                        activity.pipManager?.setupPipButton(settings.enablePictureInPicture)

                        settings.enableFavorites || state.resource?.id == -100L
                    }.collect { shouldShow ->
                        activity.activityBinding.btnFavorite.isVisible = shouldShow
                    }
                }
            }
        }
    }

    fun updateUI(state: PlayerViewModel.PlayerState) {
        activity.uiStateCoordinator.updateUI(state)
        if (state.currentFile?.type != MediaType.AUDIO) {
            activity.sleepTimerManager?.stopVinylAnimation()
        }
        if (activity.audioSlideshowPhotoModeManager.isActive) {
            activity.audioSlideshowPhotoModeManager.enforceUI()
        }
    }
}
