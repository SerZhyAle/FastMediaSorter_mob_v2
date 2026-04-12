package com.sza.fastmediasorter.ui.browse.managers

import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Launches and manages all secondary observers in BrowseActivity.observeData():
 * - Inline audio player auto-scroll
 * - Favorites button visibility
 * - hideGridActionButtons setting
 * - Loading/progress state
 * - Settings changes (PDF thumbnails, icon size)
 * - Error state + empty state text
 *
 * Extracted from BrowseActivity (Wave 1.5 decomposition — IV.1).
 */
class BrowseObserverManager(
    private val lifecycleOwner: LifecycleOwner,
    private val binding: ActivityBrowseBinding,
    private val viewModel: BrowseViewModel,
    private val adapter: MediaFileAdapter,
    private val settingsRepository: SettingsRepository,
    private val onUpdateDisplayMode: suspend (DisplayMode) -> Unit,
    private val onNotifyRangeChanged: (start: Int, count: Int, payload: Any, source: String) -> Unit,
    private val getShowPdfThumbnails: () -> Boolean,
    private val setShowVideoThumbnails: (Boolean) -> Unit,
    private val setShowPdfThumbnails: (Boolean) -> Unit
) {

    fun startAll() {
        observeInlinePlayer()
        observeFavoritesSetting()
        observeHideGridActionButtons()
        observeLoadingProgress()
        observeSettings()
        observeError()
    }

    private fun observeInlinePlayer() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.inlinePlayerState.collect { state ->
                    adapter.updateInlinePlayerState(state)
                    state.playingPath?.let { path ->
                        val position = viewModel.state.value.mediaFiles.indexOfFirst { it.path == path }
                        if (position >= 0) {
                            val layoutManager = binding.rvMediaFiles.layoutManager as? LinearLayoutManager
                            layoutManager?.let { lm ->
                                val rvHeight = binding.rvMediaFiles.height
                                val itemHeight = lm.findViewByPosition(position)?.height ?: 80
                                val offset = ((rvHeight - itemHeight) / 2).coerceAtLeast(0)
                                lm.scrollToPositionWithOffset(position, offset)
                                Timber.d("InlinePlayer: auto-scrolled to position=$position offset=$offset")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeFavoritesSetting() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(settingsRepository.getSettings(), viewModel.state) { settings, state ->
                    settings.enableFavorites || state.resource?.id == -100L
                }.collect { shouldShow ->
                    adapter.setShowFavoriteButton(shouldShow)
                }
            }
        }
    }

    private fun observeHideGridActionButtons() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.getSettings().collect { settings ->
                    adapter.setHideGridActionButtons(settings.hideGridActionButtons)
                }
            }
        }
    }

    private fun observeLoadingProgress() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.loading, viewModel.state) { isLoading, state ->
                    Pair(isLoading, state)
                }.collect { (isLoading, state) ->
                    binding.layoutProgress.isVisible = isLoading
                    binding.btnStopScan.isVisible = state.isScanCancellable && isLoading

                    if (!isLoading) binding.swipeRefreshLayout.isRefreshing = false

                    binding.tvProgressMessage.text = if (state.loadingProgress > 0) {
                        binding.root.context.getString(
                            R.string.loading_with_progress,
                            binding.root.context.getString(R.string.loading),
                            state.loadingProgress
                        )
                    } else {
                        binding.root.context.getString(R.string.loading)
                    }

                    if (!isLoading) {
                        val hasError = viewModel.error.value != null
                        val actualItemCount = adapter.itemCount
                        val actualIsEmpty = actualItemCount == 0
                        val isFavorites = state.resource?.id == -100L
                        val filesStillPending = actualIsEmpty && state.mediaFiles.isNotEmpty()
                        val shouldShowEmpty = when {
                            filesStillPending -> false
                            isFavorites -> actualIsEmpty && !hasError && state.mediaFiles.isEmpty()
                            else -> actualIsEmpty && !hasError
                        }
                        binding.emptyStateView.isVisible = shouldShowEmpty
                        Timber.d("Progress observer: emptyState=$shouldShowEmpty (itemCount=$actualItemCount, hasError=$hasError, filesStillPending=$filesStillPending)")
                    }
                }
            }
        }
    }

    private fun observeSettings() {
        var lastIconSize = 96
        var lastCompactMode = false
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.getSettings().collect { settings ->
                    val pdfThumbnailsChanged = getShowPdfThumbnails() != settings.showPdfThumbnails
                    setShowVideoThumbnails(settings.showVideoThumbnails)
                    setShowPdfThumbnails(settings.showPdfThumbnails)

                    if (pdfThumbnailsChanged && adapter.itemCount > 0) {
                        onNotifyRangeChanged(0, adapter.itemCount, "LOAD_THUMBNAILS", "settings pdf toggle")
                    }

                    // Compact elements: always push to adapter (guard against redundant notify is inside)
                    adapter.setUseCompactElements(settings.useCompactElements)
                    val compactChanged = settings.useCompactElements != lastCompactMode
                    if (compactChanged) lastCompactMode = settings.useCompactElements

                    val currentResource = viewModel.state.value.resource
                    val iconSizeChanged = settings.defaultIconSize != lastIconSize
                    if (currentResource != null &&
                        currentResource.displayMode == DisplayMode.GRID &&
                        !currentResource.isAudioOnly() &&
                        (iconSizeChanged || compactChanged)
                    ) {
                        lastIconSize = settings.defaultIconSize
                        Timber.d("Grid layout update: iconSize=${settings.defaultIconSize} compact=${settings.useCompactElements}")
                        onUpdateDisplayMode(DisplayMode.GRID)
                    } else if (currentResource != null) {
                        lastIconSize = settings.defaultIconSize
                    }
                }
            }
        }
    }

    private fun observeError() {
        val ctx = binding.root.context
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.error.collect { errorMessage ->
                    val hasError = errorMessage != null
                    val isEmpty = adapter.itemCount == 0
                    val currentLoading = viewModel.loading.value
                    val currentState = viewModel.state.value

                    binding.errorStateView.isVisible = hasError && isEmpty

                    val actualIsEmpty = adapter.itemCount == 0
                    val isFavorites = currentState.resource?.id == -100L
                    val shouldShowEmpty = if (isFavorites) {
                        actualIsEmpty && !hasError && !currentLoading && currentState.mediaFiles.isEmpty()
                    } else {
                        actualIsEmpty && !hasError && !currentLoading
                    }
                    binding.emptyStateView.isVisible = shouldShowEmpty

                    if (binding.emptyStateView.isVisible) {
                        if (isFavorites) {
                            binding.tvEmptyStateMessage.text = ctx.getString(R.string.favorites_empty_title)
                            binding.tvEmptyStateHint.isVisible = true
                            binding.tvEmptyStateHint.text = ctx.getString(R.string.favorites_empty_hint)
                        } else {
                            binding.tvEmptyStateMessage.text = ctx.getString(R.string.no_files_found)
                            val resource = currentState.resource
                            if (resource != null && !resource.scanSubdirectories) {
                                binding.tvEmptyStateHint.isVisible = true
                                binding.tvEmptyStateHint.text = if (resource.subfolderCount > 0) {
                                    ctx.getString(R.string.empty_folder_with_subfolders, resource.subfolderCount)
                                } else {
                                    ctx.getString(R.string.empty_folder_hint_subdirectories)
                                }
                            } else {
                                binding.tvEmptyStateHint.isVisible = false
                            }
                        }
                    }

                    if (hasError && isEmpty) {
                        binding.tvErrorMessage.text = errorMessage
                    }
                }
            }
        }
    }

}
