package com.sza.fastmediasorter.ui.player.helpers

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.CommandPanelController
import com.sza.fastmediasorter.ui.player.DestinationButtonsManager
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerDialogHelper
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Manager for dialog operations and UI state coordination in PlayerActivity.
 * 
 * Centralizes:
 * - Dialog display with business logic validation (read-only checks, file existence)
 * - Panel visibility management (command panel, copy/move panels, controls)
 * - Volume and audio touch zones visibility coordination
 * - Integration with PlayerDialogHelper for actual dialog implementation
 * 
 * Implements coordinator pattern to reduce PlayerActivity complexity.
 */
class PlayerDialogAndUiStateManager(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel,
    private val binding: ActivityPlayerUnifiedBinding,
    private val dialogHelper: PlayerDialogHelper,
    private val destinationButtonsManager: DestinationButtonsManager,
    private val commandPanelController: CommandPanelController,
    private val textViewerManager: TextViewerManager,
    private val mediaLoaderManager: PlayerMediaLoaderManager,
    private val networkFileManager: NetworkFileManager,
    private val imageLoadingManager: com.sza.fastmediasorter.ui.player.ImageLoadingManager,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private val safeViews = PlayerBindingSafeViews(binding)
    
    /**
     * Flag indicating audio slideshow photo mode is active.
     * When true, audio files should NOT force command panel / system bars visible.
     */
    var isAudioSlideshowPhotoMode: Boolean = false

    /**
     * Reference to AudioSlideshowPhotoModeManager, set after both managers are initialized.
     * Required for updateBackgroundMusicTrackDisplay() delegation.
     */
    var audioSlideshowPhotoModeManager: AudioSlideshowPhotoModeManager? = null
    
    // ========================================
    // Dialog Operations with Business Logic
    // ========================================
    
    /**
     * Show rename dialog with read-only validation.
     * Checks if current resource is read-only and shows error toast if so.
     */
    fun showRenameDialog() {
        Timber.d("PlayerDialogAndUiStateManager: showRenameDialog()")
        
        val resource = viewModel.state.value.resource
        if (resource?.isReadOnly == true) {
            Toast.makeText(activity, activity.getString(R.string.error_read_only), Toast.LENGTH_SHORT).show()
            return
        }
        
        val currentFile = viewModel.state.value.currentFile
        if (currentFile == null) {
            Toast.makeText(activity, activity.getString(R.string.msg_no_file_to_edit), Toast.LENGTH_SHORT).show()
            return
        }
        
        dialogHelper.showRenameDialog(currentFile)
    }
    
    /**
     * Show image edit dialog with read-only and file validation.
     * Handles IMAGE and GIF types.
     */
    fun showImageEditDialog() {
        Timber.d("PlayerDialogAndUiStateManager: showImageEditDialog()")
        
        // Check for Read-only mode
        val resource = viewModel.state.value.resource
        if (resource?.isReadOnly == true) {
            Toast.makeText(activity, activity.getString(R.string.error_read_only), Toast.LENGTH_SHORT).show()
            return
        }
        
        val currentFile = viewModel.state.value.currentFile
        if (currentFile == null) {
            Toast.makeText(activity, activity.getString(R.string.msg_no_file_to_edit), Toast.LENGTH_SHORT).show()
            return
        }
        
        dialogHelper.showImageEditDialog(currentFile)
    }
    
    /**
     * Show GIF edit dialog with file validation.
     * Allows speed adjustment and frame extraction.
     */
    fun showGifEditDialog() {
        Timber.d("PlayerDialogAndUiStateManager: showGifEditDialog()")
        
        val currentFile = viewModel.state.value.currentFile
        if (currentFile == null) {
            Toast.makeText(activity, activity.getString(R.string.msg_no_file_to_edit), Toast.LENGTH_SHORT).show()
            return
        }
        
        dialogHelper.showGifEditDialog(currentFile)
    }
    
    /**
     * Show PDF edit dialog with export options.
     * Creates AlertDialog with available PDF operations.
     */
    fun showPdfEditDialog() {
        Timber.d("PlayerDialogAndUiStateManager: showPdfEditDialog()")
        
        val options = arrayOf(
            activity.getString(R.string.pdf_export_to_jpg)
        )
        
        AlertDialog.Builder(activity)
            .setTitle(R.string.pdf_edit_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportPdfToJpg()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * Export PDF pages to JPG images.
     * Handles network file download and conversion.
     */
    private fun exportPdfToJpg() {
        Timber.d("PlayerDialogAndUiStateManager: exportPdfToJpg()")
        
        val currentFile = viewModel.state.value.currentFile ?: return
        
        lifecycleScope.launch {
            // Show loading
            binding.progressBar.isVisible = true
            Toast.makeText(activity, R.string.pdf_exporting_started, Toast.LENGTH_SHORT).show()
            
            try {
                // Determine file to export
                val fileToExport = withContext(Dispatchers.IO) {
                    if (currentFile.path.contains("://")) {
                        // Network file - ensure it's cached/downloaded
                        networkFileManager.prepareFileForRead(currentFile)
                    } else {
                        File(currentFile.path)
                    }
                }
                
                val result = com.sza.fastmediasorter.utils.PdfExportHelper.exportPdfPagesToJpg(
                    activity,
                    fileToExport
                )
                
                withContext(Dispatchers.Main) {
                    result.onSuccess { count ->
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.pdf_export_success, count),
                            Toast.LENGTH_LONG
                        ).show()
                    }.onFailure { e ->
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.pdf_export_failed, e.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "PDF export failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        activity,
                        "Export failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                }
            }
        }
    }
    
    /**
     * Show copy dialog with file validation.
     */
    fun showCopyDialog() {
        Timber.d("PlayerDialogAndUiStateManager: showCopyDialog()")
        
        val currentFile = viewModel.state.value.currentFile ?: return
        val resourceId = activity.intent.getLongExtra("resourceId", -1)
        dialogHelper.showCopyDialog(currentFile, resourceId)
    }
    
    /**
     * Show move dialog with read-only validation.
     */
    fun showMoveDialog() {
        Timber.d("PlayerDialogAndUiStateManager: showMoveDialog()")
        
        val resource = viewModel.state.value.resource
        if (resource?.isReadOnly == true) {
            Toast.makeText(activity, activity.getString(R.string.error_read_only), Toast.LENGTH_SHORT).show()
            return
        }
        
        val currentFile = viewModel.state.value.currentFile ?: return
        val resourceId = activity.intent.getLongExtra("resourceId", -1)
        dialogHelper.showMoveDialog(currentFile, resourceId)
    }
    
    // ========================================
    // UI State Management
    // ========================================
    
    /**
     * Update panel visibility based on command panel mode.
     * Coordinates visibility of:
     * - Top command panel and filename overlay
     * - Copy/Move panels
     * - Touch zones overlays
     * - Controls overlay
     * - Small controls application
     * 
     * IMPORTANT: For AUDIO files, command panel is ALWAYS visible (ignores showCommandPanel flag).
     * Audio playback requires persistent access to Previous/Next/Favorite commands.
     */
    fun updatePanelVisibility(showCommandPanel: Boolean) {
        val state = viewModel.state.value
        val isAudioFile = state.currentFile?.type == MediaType.AUDIO
        val isSlideshowActive = state.isSlideShowActive
        
        // OVERRIDE: Audio files ALWAYS show command panel (except in audio slideshow photo mode)
        val forceShowPanel = showCommandPanel || (isAudioFile && !isAudioSlideshowPhotoMode)
        
        // CRITICAL: Hide topCommandPanel during slideshow mode for fullscreen experience
        val shouldHideForSlideshow = isSlideshowActive && !isAudioFile
        
        Timber.d("PlayerDialogAndUiStateManager: updatePanelVisibility(showCommandPanel=$showCommandPanel, isAudio=$isAudioFile, slideshow=$isSlideshowActive, RESULT=${forceShowPanel && !shouldHideForSlideshow})")
        
        if (forceShowPanel && !shouldHideForSlideshow) {
            // Command panel mode
            binding.topCommandPanel.isVisible = true
            // Show full filename overlay for all file types (not just audio) so it's
            // always visible regardless of content background (dark or light).
            val showFileNameOverlay = !isAudioSlideshowPhotoMode
            binding.tvFileNameOverlay?.isVisible = showFileNameOverlay

            // Restore default top margin from resources. topCommandPanel is outside the FrameLayout
            // containing tvFileNameOverlay, so adding panel.height here shifts the filename too low.
            binding.tvFileNameOverlay?.let { overlay ->
                val lp = overlay.layoutParams as? android.widget.FrameLayout.LayoutParams
                if (lp != null) {
                    lp.topMargin = activity.resources.getDimensionPixelSize(
                        R.dimen.activity_player_unified_tvFileNameOverlay_layout_marginTop
                    )
                    overlay.layoutParams = lp
                }
            }
            
            // DEBUG: Log actual view state after setting
            binding.topCommandPanel.post {
                Timber.d("PlayerDialogAndUiStateManager: topCommandPanel ACTUAL state - visibility=${binding.topCommandPanel.visibility}, " +
                    "width=${binding.topCommandPanel.width}, height=${binding.topCommandPanel.height}, " +
                    "x=${binding.topCommandPanel.x}, y=${binding.topCommandPanel.y}, " +
                    "alpha=${binding.topCommandPanel.alpha}, parent=${binding.topCommandPanel.parent?.javaClass?.simpleName}")
            }
            
            // Touch zones visibility will be managed by displayImage() based on loadFullSizeImages setting
            // (standard 2-zone overlay vs 3-zone overlay with gesture area)
            // Copy/Move panel visibility is controlled by updateCommandAvailability()
            binding.controlsOverlay.isVisible = false
            
            // Populate destination buttons (handles state restoration internally)
            activity.populateDestinationButtons()
            
            // Apply small controls setting if enabled
            if (state.showSmallControls) {
                applySmallControlsIfNeeded()
            } else {
                restoreCommandButtonHeightsIfNeeded()
            }
        } else {
            // Fullscreen mode or slideshow mode
            binding.topCommandPanel.isVisible = false
            binding.tvFileNameOverlay?.isVisible = false
            // View-based overlays always hidden - TouchZoneGestureManager handles zones
            safeViews.touchZones3Overlay.isVisible = false
            safeViews.touchZonesOverlay.isVisible = false
            safeViews.copyToPanel.isVisible = false
            safeViews.moveToPanel.isVisible = false
            // controlsOverlay visibility is controlled in updateUI based on showControls
            
            if (!state.showSmallControls) {
                restoreCommandButtonHeightsIfNeeded()
            }
        }
        
        // CRITICAL: Re-evaluate image scale type when panel visibility changes
        // This ensures the image immediately scales up/down when entering/exiting fullscreen
        imageLoadingManager.reEvaluateScaleTypeOnRotation()
        
        // Update audio touch zones overlay whenever panel visibility changes
        updateAudioTouchZonesVisibility()
        
        // Update text viewer close button visibility (User request: hide when command panel visible)
        textViewerManager.updateCloseButtonVisibility(forceShowPanel)
    }
    
    /**
     * Apply small controls sizing to command buttons.
     */
    private fun applySmallControlsIfNeeded() {
        commandPanelController.applySmallControlsIfNeeded()
    }
    
    /**
     * Restore normal command button heights.
     */
    private fun restoreCommandButtonHeightsIfNeeded() {
        commandPanelController.restoreCommandButtonHeightsIfNeeded()
    }
    
    /**
     * Toggle Copy to panel collapsed/expanded state.
     */
    fun toggleCopyPanel() {
        Timber.d("PlayerDialogAndUiStateManager: toggleCopyPanel()")
        destinationButtonsManager.toggleCopyPanel()
    }
    
    /**
     * Toggle Move to panel collapsed/expanded state with read-only check.
     */
    fun toggleMovePanel() {
        Timber.d("PlayerDialogAndUiStateManager: toggleMovePanel()")
        
        val resource = viewModel.state.value.resource
        if (resource?.isReadOnly == true) {
            return
        }
        destinationButtonsManager.toggleMovePanel()
    }
    
    /**
     * Update volume buttons visibility - show for audio and video files.
     * Delegates to PlayerMediaLoaderManager.
     */
    fun updateVolumeButtonsVisibility() {
        mediaLoaderManager.updateVolumeButtonsVisibility()
    }
    
    /**
     * Update audio touch zones overlay visibility based on:
     * - Current file is audio
     * - Fullscreen mode (not showing command panel or controls overlay)
     * - Touch zones are enabled
     * 
     * Delegates to PlayerMediaLoaderManager.
     */
    fun updateAudioTouchZonesVisibility() {
        mediaLoaderManager.updateAudioTouchZonesVisibility()
    }

    // ========================================
    // Slideshow / Countdown / Music UI State
    // ========================================

    /**
     * Update slideshow button appearance based on active state.
     * Alpha + color reflect whether slideshow is running.
     * Also forwards state to commandPanelController button.
     * Extracted from PlayerActivity.updateSlideShowButton().
     */
    fun updateSlideShowButton() {
        val isActive = viewModel.state.value.isSlideShowActive
        Timber.d("updateSlideShowButton: START - isActive=$isActive")
        binding.btnSlideShow.alpha = if (isActive) 1.0f else 0.5f
        if (isActive) {
            binding.btnSlideShow.setTextColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED))
            binding.btnSlideShow.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#33FF0000"))
        } else {
            binding.btnSlideShow.setTextColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE))
            binding.btnSlideShow.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        }
        commandPanelController.updateSlideshowButtonColor(isActive)
        Timber.d("updateSlideShowButton: END")
    }

    /**
     * Update countdown display for slideshow (called by NavigationManager).
     * Hides countdown in audio slideshow photo mode.
     * Extracted from PlayerActivity.updateCountdownDisplay().
     */
    fun updateCountdownDisplay(seconds: Int) {
        if (activity.isFinishing || activity.isDestroyed) {
            Timber.w("updateCountdownDisplay: Activity finishing/destroyed, skipping UI update")
            return
        }

        if (isAudioSlideshowPhotoMode) {
            safeViews.tvCountdown.isVisible = false
            return
        }

        safeViews.tvCountdown.text = "$seconds.."
        safeViews.tvCountdown.isVisible = true
        if (seconds == 0) {
            safeViews.tvCountdown.isVisible = false
        }
    }

    /**
     * Update background music track name display.
     * Shows track name in bottom-left corner during slideshow with music.
     * In audio slideshow photo mode, delegates to audioSlideshowPhotoModeManager.
     * Extracted from PlayerActivity.updateBackgroundMusicTrackDisplay().
     *
     * @param trackName track name without extension, or null to hide
     */
    fun updateBackgroundMusicTrackDisplay(trackName: String?) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        if (isAudioSlideshowPhotoMode) {
            audioSlideshowPhotoModeManager?.updateCurrentSongLabel()
            return
        }

        if (trackName != null) {
            safeViews.tvBackgroundMusicTrack.text = "♪ $trackName"
            safeViews.tvBackgroundMusicTrack.isVisible = true
            Timber.d("BackgroundMusic: Showing track name: $trackName")
        } else {
            safeViews.tvBackgroundMusicTrack.isVisible = false
            Timber.d("BackgroundMusic: Hiding track name")
        }
    }
}
