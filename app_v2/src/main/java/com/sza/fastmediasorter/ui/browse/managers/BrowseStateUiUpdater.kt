package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.util.DrawingTargetPolicy
import com.sza.fastmediasorter.util.TextNoteTargetPolicy
import com.sza.fastmediasorter.util.VirtualPathUtils
import com.sza.fastmediasorter.utils.clearBadge
import com.sza.fastmediasorter.utils.setBadgeText
import timber.log.Timber

/**
 * Applies BrowseState changes to the UI: filter badge, selection panel, display mode,
 * breadcrumb, and resource action button.
 *
 * Extracted from BrowseActivity.observeData() state collector (Wave 1.5 decomposition - IV.1).
 */
class BrowseStateUiUpdater(
    private val activity: Activity,
    private val binding: ActivityBrowseBinding,
    private val adapter: MediaFileAdapter,
    private val viewModel: BrowseViewModel,
    private val passwordManager: ResourcePasswordManager,
    private val smallControlsManager: BrowseSmallControlsManager,
    private val onUpdateDisplayMode: suspend (DisplayMode) -> Unit,
    private val onUpdateBreadcrumb: (BrowseState) -> Unit,
    private val onBuildResourceInfo: (BrowseState) -> String,
    private val onLaunchEditResource: (Long) -> Unit,
    private val onUpdateToggleViewAvailability: (Boolean) -> Unit
) {
    /** Cached display mode to avoid redundant updates. */
    var currentDisplayMode: DisplayMode? = null
    /** Cached audio-only mode to force layout refresh when resource changes. */
    var currentAudioOnlyMode: Boolean? = null

    /**
     * Apply all UI changes derived from the current [state].
     * Called from the state collector inside observeData().
     */
    suspend fun onStateChanged(state: BrowseState) {
        updateFilterBadge(state)
        updateSelectionPanel(state)
        updateDisplayModeIfNeeded(state)
        updatePlayRandomButtonVisibility(state)
        adapter.setUseCompactElements(state.useCompactElements)
        applySmallControls(state)
        onUpdateBreadcrumb(state)
        updateCreateFolderButtonVisibility(state)
        updateResourceActionButton(state)
    }

    private fun updateFilterBadge(state: BrowseState) {
        val filter = state.filter
        val resource = state.resource

        val isUserFilter = filter != null && !filter.isEmpty() && (
            !filter.nameContains.isNullOrBlank() ||
            filter.minDate != null ||
            filter.maxDate != null ||
            filter.minSizeMb != null ||
            filter.maxSizeMb != null ||
            (filter.mediaTypes != null && filter.mediaTypes != resource?.supportedMediaTypes)
        )

        binding.tvFilterWarning.isVisible = false

        if (isUserFilter) {
            val filterCount = state.filter?.activeFilterCount() ?: 0
            binding.btnFilter.setBadgeText(filterCount.toString())
        } else {
            binding.btnFilter.clearBadge()
        }
    }

    private fun updateSelectionPanel(state: BrowseState) {
        val hasSelection = state.selectedFiles.isNotEmpty()
        val resource = state.resource
        val isWritable = (resource?.isWritable ?: false) && (resource?.isReadOnly != true)

        binding.layoutOperations.isVisible = hasSelection || state.lastOperation != null

        binding.btnCopy.isVisible = hasSelection
        binding.btnMove.isVisible = hasSelection && isWritable
        binding.btnRename.isVisible = hasSelection && isWritable
        binding.btnDelete.isVisible = hasSelection && isWritable
        binding.btnUndo.isVisible = state.lastOperation != null
        binding.btnShare.isVisible = hasSelection
        val isLocalResource = resource?.type == ResourceType.LOCAL
        binding.btnArchive?.isVisible = hasSelection && isLocalResource
    }

    /**
     * "Play Random" button appears only for single-type libraries (audio / image / video).
     * Hidden for mixed, allFiles, or document-only resources - random play has no meaningful
     * semantics there from a UX perspective.
     */
    private fun updatePlayRandomButtonVisibility(state: BrowseState) {
        val resource = state.resource
        val isSingleTypeMediaLibrary = resource != null &&
            (resource.isAudioOnly() || resource.isOnlyImage() || resource.isVideoOnly())
        binding.btnPlayRandom?.isVisible = isSingleTypeMediaLibrary
    }

    private suspend fun updateDisplayModeIfNeeded(state: BrowseState) {
        val shouldDisableToggle = state.resource?.isAudioOnly() == true
        onUpdateToggleViewAvailability(shouldDisableToggle)

        if (state.displayMode != currentDisplayMode || shouldDisableToggle != currentAudioOnlyMode) {
            currentAudioOnlyMode = shouldDisableToggle
            currentDisplayMode = state.displayMode
            onUpdateDisplayMode(state.displayMode)
        }
    }

    private fun applySmallControls(state: BrowseState) {
        if (state.showSmallControls || state.useCompactElements) {
            smallControlsManager.applySmallControlsIfNeeded()
        } else {
            smallControlsManager.restoreCommandButtonHeightsIfNeeded()
        }
    }

    private fun updateCreateFolderButtonVisibility(state: BrowseState) {
        val resource = state.resource
        val canCreateFolder = resource != null
                && resource.showSubfoldersAsItems
                && !resource.isReadOnly
                && !VirtualPathUtils.isVirtualPath(resource.path)
        binding.btnCreateFolder?.isVisible = canCreateFolder

        // S0189: virtual "All Documents" writes new notes to the public Documents folder.
        binding.btnCreateTextFile?.isVisible = TextNoteTargetPolicy.canCreateTextNote(resource)

        // S0363: drawing allowed on real image folders + the virtual "all images" / "camera" resources.
        binding.btnCreateDrawing?.isVisible = DrawingTargetPolicy.canCreateDrawing(resource)
    }

    private fun updateResourceActionButton(state: BrowseState) {
        val stateResource = state.resource ?: return
        binding.tvResourceInfo.text = onBuildResourceInfo(state)

        // Apply compact scaling to resource info text
        if (state.useCompactElements) {
            binding.tvResourceInfo.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
        } else {
            binding.tvResourceInfo.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
        }

        val isSubfolder = state.isSubfolderMode && state.currentPath != null && state.currentPath != stateResource.path
        if (isSubfolder) {
            binding.btnResourceAction.setImageResource(R.drawable.ic_folder_24)
            binding.btnResourceAction.isClickable = false
            binding.btnResourceAction.isFocusable = false
            binding.btnResourceAction.visibility = View.VISIBLE
        } else {
            binding.btnResourceAction.setImageResource(R.drawable.ic_edit_20)
            binding.btnResourceAction.isClickable = true
            binding.btnResourceAction.isFocusable = true
            binding.btnResourceAction.visibility = View.VISIBLE
            binding.btnResourceAction.setOnClickListener {
                if (!stateResource.accessPin.isNullOrBlank()) {
                    passwordManager.checkResourcePin(stateResource) {
                        onLaunchEditResource(stateResource.id)
                    }
                } else {
                    onLaunchEditResource(stateResource.id)
                }
            }
        }
    }

    companion object {
        internal fun isCameraCaptureVisible(state: BrowseState, settings: AppSettings): Boolean {
            if (settings.disableCameraCapture) return false
            val resource = state.resource ?: return false
            val supportsImageOrVideo = resource.allFiles ||
                resource.supportedMediaTypes.any {
                    it == MediaType.IMAGE || it == MediaType.VIDEO || it == MediaType.GIF
                }
            if (!supportsImageOrVideo) return false
            val path = resource.path
            return !VirtualPathUtils.isVirtualPath(path) ||
                path == LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO ||
                path == LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES ||
                path == LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS
        }
    }
}
