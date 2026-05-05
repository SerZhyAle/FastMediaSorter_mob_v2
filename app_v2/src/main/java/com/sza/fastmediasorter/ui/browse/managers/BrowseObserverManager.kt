package com.sza.fastmediasorter.ui.browse.managers

import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import kotlinx.coroutines.flow.combine
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
        lifecycleOwner.collectOnLifecycle(viewModel.inlinePlayerState) { state ->
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

    private fun observeFavoritesSetting() {
        lifecycleOwner.collectOnLifecycle(
            combine(settingsRepository.getSettings(), viewModel.state) { settings, state ->
                settings.enableFavorites || state.resource?.id == -100L
            }
        ) { shouldShow ->
            adapter.setShowFavoriteButton(shouldShow)
        }
    }

    private fun observeHideGridActionButtons() {
        lifecycleOwner.collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            adapter.setHideGridActionButtons(settings.hideGridActionButtons)
        }
    }

    private fun observeLoadingProgress() {
        lifecycleOwner.collectOnLifecycle(
            combine(viewModel.loading, viewModel.state) { isLoading, state -> Pair(isLoading, state) }
        ) { (isLoading, state) ->
            binding.layoutProgress.isVisible = isLoading
            binding.btnStopScan.isVisible = state.isScanCancellable && isLoading

            if (!isLoading) binding.swipeRefreshLayout.isRefreshing = false

            val progressMessage = when {
                state.isSorting -> binding.root.context.getString(R.string.sorting_in_progress)
                state.resource?.type == ResourceType.SMB && state.loadingProgress > 0 -> {
                    val estimatedCount = state.totalFileCount?.takeIf { it > state.loadingProgress }
                    val scanCounter = estimatedCount?.let { "${state.loadingProgress} / ~$it" }
                        ?: state.loadingProgress.toString()
                    binding.root.context.getString(R.string.scanning_progress, scanCounter)
                }
                else -> binding.root.context.getString(R.string.loading)
            }
            binding.tvProgressMessage.text = progressMessage
            binding.tvProgressMessage.contentDescription = progressMessage
            binding.layoutProgress.contentDescription = progressMessage

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

    private fun observeSettings() {
        var lastIconSize = 96
        var lastCompactMode = false
        lifecycleOwner.collectOnLifecycle(settingsRepository.getSettings()) { settings ->
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

    private fun observeError() {
        val ctx = binding.root.context
        lifecycleOwner.collectOnLifecycle(viewModel.error) { errorMessage ->
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
