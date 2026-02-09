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
    private val lifecycleScope: LifecycleCoroutineScope
) {
    
    /**
     * Flag indicating audio slideshow photo mode is active.
     * When true, audio files should NOT force command panel / system bars visible.
     */
    var isAudioSlideshowPhotoMode: Boolean = false
    
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
        
        // OVERRIDE: Audio files ALWAYS show command panel (except in audio slideshow photo mode)
        val forceShowPanel = showCommandPanel || (isAudioFile && !isAudioSlideshowPhotoMode)
        
        Timber.d("PlayerDialogAndUiStateManager: updatePanelVisibility(showCommandPanel=$showCommandPanel, isAudio=$isAudioFile, RESULT=$forceShowPanel)")
        
        if (forceShowPanel) {
            // Command panel mode
            binding.topCommandPanel.isVisible = true
            binding.tvFileNameOverlay?.isVisible = true
            
            // CRITICAL: Update insets after visibility change.
            // Using post() is ESSENTIAL here because:
            // 1. The view was just set to VISIBLE, needs to be attached/measured for insets.
            // 2. We just exited fullscreen, system bars might still be animating/stabilizing.
            binding.topCommandPanel.post { 
                binding.topCommandPanel.requestApplyInsets()
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
            // Fullscreen mode
            binding.topCommandPanel.isVisible = false
            binding.tvFileNameOverlay?.isVisible = false
            // View-based overlays always hidden - TouchZoneGestureManager handles zones
            binding.touchZones3Overlay.isVisible = false
            binding.touchZonesOverlay.isVisible = false
            binding.copyToPanel.isVisible = false
            binding.moveToPanel.isVisible = false
            // controlsOverlay visibility is controlled in updateUI based on showControls
            
            if (!state.showSmallControls) {
                restoreCommandButtonHeightsIfNeeded()
            }
        }
        
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
}
