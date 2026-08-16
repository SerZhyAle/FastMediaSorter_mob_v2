package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.text.format.DateFormat
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.allowsWriteOperations
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.util.DrawingTargetPolicy
import com.sza.fastmediasorter.util.TextNoteTargetPolicy
import com.sza.fastmediasorter.util.VirtualPathUtils
import com.sza.fastmediasorter.utils.clearBadge
import com.sza.fastmediasorter.utils.setBadgeText
import java.util.Date

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
    private val onUpdateToggleViewAvailability: (Boolean) -> Unit,
    // S0374: report runtime-gated command eligibility + request an overflow recompute.
    private val setCommandEligibility: (Int, Boolean) -> Unit = { _, _ -> },
    private val onRecomputeOverflow: () -> Unit = {}
) {
    /** Cached display mode to avoid redundant updates. */
    var currentDisplayMode: DisplayMode? = null
    /** Cached audio-only mode to force layout refresh when resource changes. */
    var currentAudioOnlyMode: Boolean? = null
    /** Cached no-thumbnail flag - grid span count differs for the no-thumbnail "plank" layout (S0419). */
    private var currentDisableThumbnails: Boolean? = null

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
        // S0374: re-partition the bar after every state-driven visibility change (final step).
        onRecomputeOverflow()
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

        // S1685: `isUserFilter` already requires a non-null filter, so a second null check was dead code
        // the compiler reported on every build; carrying the value instead keeps the non-null type.
        val activeFilter = filter?.takeIf { isUserFilter }
        if (activeFilter != null) {
            // S1272: Browse spells the active filter out under the toolbar, the way Main already does.
            // The badge stays alongside it - a count at a glance, the detail underneath.
            val summary = describeFilter(activeFilter)
            binding.tvFilterWarning.text = activity.getString(R.string.filters_active, summary)
            binding.tvFilterWarning.isVisible = summary.isNotEmpty()
            binding.btnFilter.setBadgeText(activeFilter.activeFilterCount().toString())
        } else {
            binding.tvFilterWarning.isVisible = false
            binding.btnFilter.clearBadge()
        }
    }

    /**
     * Builds the strip's detail from the filter dialog's own labels rather than the hard-coded English
     * ones the Main manager uses, so the sentence stays translated on a RU or UK device.
     */
    private fun describeFilter(filter: FileFilter): String {
        val dateFormat = DateFormat.getDateFormat(activity)
        val parts = mutableListOf<String>()
        filter.nameContains?.takeIf { it.isNotBlank() }?.let { parts.add("\"$it\"") }
        filter.minDate?.let { parts.add(label(R.string.min_date, dateFormat.format(Date(it)))) }
        filter.maxDate?.let { parts.add(label(R.string.max_date, dateFormat.format(Date(it)))) }
        filter.minSizeMb?.let { parts.add(label(R.string.min_size_mb, it.toString())) }
        filter.maxSizeMb?.let { parts.add(label(R.string.max_size_mb, it.toString())) }
        filter.mediaTypes?.takeIf { it.isNotEmpty() }?.let { types ->
            val names = types.mapNotNull { type -> mediaTypeLabel(type)?.let { activity.getString(it) } }
            if (names.isNotEmpty()) parts.add(names.sorted().joinToString(", "))
        }
        return parts.joinToString(" | ")
    }

    private fun label(@StringRes titleRes: Int, value: String): String =
        activity.getString(titleRes) + ": " + value

    /**
     * Only the eight types the filter dialog can actually select carry a translated name; the binary
     * ones are not offered there, so they are omitted rather than shown as a raw enum constant.
     */
    @StringRes
    private fun mediaTypeLabel(type: MediaType): Int? = when (type) {
        MediaType.IMAGE -> R.string.media_type_image
        MediaType.VIDEO -> R.string.media_type_video
        MediaType.AUDIO -> R.string.media_type_audio
        MediaType.GIF -> R.string.media_type_gif
        MediaType.TEXT -> R.string.media_type_text
        MediaType.PDF -> R.string.media_type_pdf
        MediaType.EPUB -> R.string.media_type_epub
        MediaType.OFFICE_DOCUMENT -> R.string.media_type_office_documents
        else -> null
    }

    private fun updateSelectionPanel(state: BrowseState) {
        val hasSelection = state.selectedFiles.isNotEmpty()
        val resource = state.resource
        // S1019: write affordance from the shared policy resolver so it matches the player and the
        // operation-layer guards; clearing "read-only" now enables move/rename/delete for network resources.
        val canWrite = resource?.allowsWriteOperations() ?: false

        binding.layoutOperations.isVisible = hasSelection || state.lastOperation != null
        // Re-settle the nav-bar insets now that the bottom bar visibility is decided: which strip
        // carries the inset, and whether the list must reserve it itself.
        BrowseEdgeToEdgeHelper.applyBottomInsets(binding)

        binding.btnCopy.isVisible = hasSelection
        binding.btnMove.isVisible = hasSelection && canWrite
        binding.btnRename.isVisible = hasSelection && canWrite
        binding.btnDelete.isVisible = hasSelection && canWrite
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
        setCommandEligibility(R.id.btnPlayRandom, isSingleTypeMediaLibrary)
    }

    private suspend fun updateDisplayModeIfNeeded(state: BrowseState) {
        val shouldDisableToggle = state.resource?.isAudioOnly() == true
        val disableThumbnails = state.resource?.disableThumbnails == true
        onUpdateToggleViewAvailability(shouldDisableToggle)
        // Keep the overflow partition in sync: an audio-only library hides the toggle (GONE), so it
        // must also be ineligible for the command bar - otherwise the adaptive bar re-shows it dimmed.
        setCommandEligibility(R.id.btnToggleView, !shouldDisableToggle)

        if (state.displayMode != currentDisplayMode ||
            shouldDisableToggle != currentAudioOnlyMode ||
            disableThumbnails != currentDisableThumbnails
        ) {
            currentAudioOnlyMode = shouldDisableToggle
            currentDisableThumbnails = disableThumbnails
            currentDisplayMode = state.displayMode
            onUpdateDisplayMode(state.displayMode)
        }
    }

    private fun applySmallControls(state: BrowseState) {
        if (state.useCompactElements) {
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
        setCommandEligibility(R.id.btnCreateFolder, canCreateFolder)

        // S0189: virtual "All Documents" writes new notes to the public Documents folder.
        val canCreateTextNote = TextNoteTargetPolicy.canCreateTextNote(resource)
        binding.btnCreateTextFile?.isVisible = canCreateTextNote
        setCommandEligibility(R.id.btnCreateTextFile, canCreateTextNote)

        // S0363: drawing allowed on real image folders + the virtual "all images" / "camera" resources.
        val canCreateDrawing = DrawingTargetPolicy.canCreateDrawing(resource)
        binding.btnCreateDrawing?.isVisible = canCreateDrawing
        setCommandEligibility(R.id.btnCreateDrawing, canCreateDrawing)
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
            // S0371 follow-up: the camera command is in-app photo only, so it requires an
            // image-capable resource. Video-only resources expose the dedicated record-video command
            // instead - routing a photo there would save a .jpg the resource filter never shows.
            val supportsImage = resource.allFiles ||
                resource.supportedMediaTypes.any {
                    it == MediaType.IMAGE || it == MediaType.GIF
                }
            if (!supportsImage) return false
            val path = resource.path
            return !VirtualPathUtils.isVirtualPath(path) ||
                path == LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES ||
                path == LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS
        }

        /**
         * S0371: video-recording command visibility. Mirrors [isCameraCaptureVisible] but is
         * media-type-driven on VIDEO (Strict Rule 15 - no BuildConfig flavor gate): the resource must
         * accept video (or be in all-files mode) and resolve to a writable destination. Virtual
         * aggregates are limited to "All video" and the camera resource, since those route to a real
         * folder; "All images" is excluded as it never holds video.
         */
        internal fun isVideoCaptureVisible(state: BrowseState, settings: AppSettings): Boolean {
            if (settings.disableVideoCapture) return false
            val resource = state.resource ?: return false
            val supportsVideo = resource.allFiles ||
                resource.supportedMediaTypes.any { it == MediaType.VIDEO }
            if (!supportsVideo) return false
            val path = resource.path
            return !VirtualPathUtils.isVirtualPath(path) ||
                path == LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO ||
                path == LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS
        }
    }
}
