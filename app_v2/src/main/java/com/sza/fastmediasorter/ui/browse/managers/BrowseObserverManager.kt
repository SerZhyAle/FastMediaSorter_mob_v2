package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.core.compat.MultiWindowCapabilityDetector
import com.sza.fastmediasorter.core.ui.UiState
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
 * Extracted from BrowseActivity (Wave 1.5 decomposition - IV.1).
 */
class BrowseObserverManager(
    private val lifecycleOwner: LifecycleOwner,
    private val activity: Activity,
    private val binding: ActivityBrowseBinding,
    private val viewModel: BrowseViewModel,
    private val adapter: MediaFileAdapter,
    private val onUpdateDisplayMode: suspend (DisplayMode) -> Unit,
    private val onNotifyRangeChanged: (start: Int, count: Int, payload: Any, source: String) -> Unit,
    private val getShowPdfThumbnails: () -> Boolean,
    private val setShowVideoThumbnails: (Boolean) -> Unit,
    private val setShowPdfThumbnails: (Boolean) -> Unit
) {

    // S0293: ticker that re-fires the `fileOpsInOverflowMenu` observer when the host Activity
    // enters or leaves a multi-window / DeX container. The runtime capability is not a Flow,
    // so we bump this counter from `notifyMultiWindowModeChanged()` to force `combine` to
    // recompute the OR-composed value against the latest `isMultiWindowActiveNow(activity)`.
    private val multiWindowTick = MutableStateFlow(0)

    fun startAll() {
        observeInlinePlayer()
        observeFavoritesSetting()
        observeHideGridActionButtons()
        observeFileOpsOverflowMenu()
        observeFileListUiState()
        observeSettings()
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
        // S0730: map to the single consumed field + distinctUntilChanged so an unrelated settings
        // change does not re-run this observer (the shared StateFlow emits the whole AppSettings).
        lifecycleOwner.collectOnLifecycle(
            combine(
                viewModel.settings.map { it.enableFavorites }.distinctUntilChanged(),
                viewModel.state
            ) { enableFavorites, state ->
                enableFavorites || state.resource?.id == -100L
            }
        ) { shouldShow ->
            adapter.setShowFavoriteButton(shouldShow)
        }
    }

    private fun observeHideGridActionButtons() {
        lifecycleOwner.collectOnLifecycle(
            viewModel.settings.map { it.hideGridActionButtons }.distinctUntilChanged()
        ) { hide ->
            adapter.setHideGridActionButtons(hide)
        }
    }

    private fun observeFileOpsOverflowMenu() {
        // S0293: OR-compose persistent preference with runtime multi-window capability so that
        // entering a DeX / desktop container on a phone exposes the per-row `⋮` overflow button
        // (and thereby the per-file "Open in new window" action) without requiring the user to
        // flip `Settings → Playback → "File operations in ⋮ menu"`. Leaving the container hides
        // the button again unless the user enabled it explicitly. The OR-composition is computed
        // here in the observer (single feed point); downstream `MediaFileAdapter` consumes a
        // single Boolean and stays unchanged.
        lifecycleOwner.collectOnLifecycle(
            combine(
                viewModel.settings.map { it.fileOpsInOverflowMenu }.distinctUntilChanged(),
                multiWindowTick
            ) { fileOpsInMenu, _ ->
                fileOpsInMenu || MultiWindowCapabilityDetector.isMultiWindowActiveNow(activity)
            }
        ) { effective ->
            adapter.setFileOpsInOverflowMenu(effective)
        }
    }

    /**
     * S0293: bump the ticker so [observeFileOpsOverflowMenu] re-evaluates the OR-composed
     * overflow-menu flag against the current Activity multi-window state. Called from
     * `BrowseActivity.onMultiWindowModeChanged` / `onConfigurationChanged` via
     * `BrowseManagerInitializer.notifyMultiWindowModeChanged()` after the system has finished
     * the mode transition.
     */
    fun notifyMultiWindowModeChanged() {
        multiWindowTick.value = multiWindowTick.value + 1
    }

    private fun observeFileListUiState() {
        val ctx = binding.root.context
        lifecycleOwner.collectOnLifecycle(
            combine(viewModel.fileListUiState, viewModel.state) { uiState, state -> uiState to state }
        ) { (uiState, state) ->
            val isLoading = uiState == UiState.Loading ||
                (uiState is UiState.Content && uiState.isRefreshing)
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

            val shouldShowError = uiState is UiState.Error && adapter.itemCount == 0
            binding.errorStateView.isVisible = shouldShowError

            val shouldShowEmpty = shouldShowEmptyState(uiState, state)
            binding.emptyStateView.isVisible = shouldShowEmpty

            if (shouldShowEmpty) {
                renderEmptyState(ctx, state)
            }

            if (uiState is UiState.Error && adapter.itemCount == 0) {
                binding.tvErrorMessage.text = uiState.message
            }

            if (!isLoading) {
                val actualItemCount = adapter.itemCount
                val filesStillPending = actualItemCount == 0 && state.mediaFiles.isNotEmpty()
                Timber.d(
                    "File-list uiState=${uiState::class.simpleName} emptyState=$shouldShowEmpty " +
                        "(itemCount=$actualItemCount, filesStillPending=$filesStillPending)"
                )
            }
        }
    }

    private fun shouldShowEmptyState(uiState: UiState<BrowseState>, state: BrowseState): Boolean {
        val actualIsEmpty = adapter.itemCount == 0
        val filesStillPending = actualIsEmpty && state.mediaFiles.isNotEmpty()
        return uiState === UiState.Empty && actualIsEmpty && !filesStillPending
    }

    private fun renderEmptyState(ctx: android.content.Context, state: BrowseState) {
        val resource = state.resource
        val isFavorites = resource?.id == -100L
        if (isFavorites) {
            binding.tvEmptyStateMessage.text = ctx.getString(R.string.favorites_empty_title)
            binding.tvEmptyStateHint.isVisible = true
            binding.tvEmptyStateHint.text = ctx.getString(R.string.favorites_empty_hint)
            return
        }

        binding.tvEmptyStateMessage.text = ctx.getString(R.string.no_files_found)
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

    private fun observeSettings() {
        var lastIconSize = 96
        var lastCompactMode = false
        // S0730: multi-field observer keeps the whole AppSettings; the shared StateFlow already
        // dedups identical values, so an unrelated write does not re-run this body.
        lifecycleOwner.collectOnLifecycle(viewModel.settings) { settings ->
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
