package com.sza.fastmediasorter.ui.player.helpers

import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Coordinates UI state rendering for PlayerActivity.
 * Keeps PlayerActivity slimmer by centralizing updateUI(state) logic.
 */
class PlayerUiStateCoordinator(
    private val binding: ActivityPlayerUnifiedBinding,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val callback: Callback
) {
    private val safeViews = PlayerBindingSafeViews(binding)

    private val mediaDisplayCoordinator = MediaDisplayCoordinator(
        callback = object : MediaDisplayCoordinator.Callback {
            override fun displayImage(path: String) = callback.displayImage(path)
            override fun playVideo(path: String) = callback.playVideo(path)
            override fun displayText(file: MediaFile) = callback.displayText(file)
            override fun displayPdf(file: MediaFile) = callback.displayPdf(file)
            override fun displayEpub(file: MediaFile) = callback.displayEpub(file)
        }
    )

    interface Callback {
        fun isActivityAlive(): Boolean

        fun getCurrentSettings(): AppSettings?
        fun setCurrentSettings(settings: AppSettings?)

        fun getCurrentFilePath(): String?
        fun setCurrentFilePath(path: String?)

        fun isSlideshowModeRequested(): Boolean
        fun clearSlideshowModeRequested()

        fun hasShownFirstRunHint(): Boolean
        fun markFirstRunHintShown()

        fun getUseTouchZones(): Boolean

        fun displayImage(path: String)
        fun playVideo(path: String)
        fun displayText(file: MediaFile)
        fun displayPdf(file: MediaFile)
        fun displayEpub(file: MediaFile)

        fun adjustTouchZonesForVideo(isVideo: Boolean)
        fun updatePanelVisibility(showCommandPanel: Boolean)
        fun updateCommandAvailability(state: PlayerViewModel.PlayerState)

        fun updatePlayPauseButton()
        fun updateSlideShowButton()
        fun updateVolumeButtonsVisibility()

        fun showFirstRunHintOverlay()
        fun showSlideshowEnabledMessage()
        fun toggleSlideShow()
        fun startSlideshow(intervalSeconds: Int)
        fun getLatestState(): PlayerViewModel.PlayerState
        fun forceStateUpdate()
        fun enterAudioSlideshowPhotoModeIfNeeded()
    }

    fun updateUI(state: PlayerViewModel.PlayerState) {
        val updateId = System.currentTimeMillis()
        val caller = Thread.currentThread().stackTrace.getOrNull(3)?.let {
            "${it.className}.${it.methodName}():${it.lineNumber}"
        } ?: "Unknown"
        
        Timber.w("╔════════════════════════════════════════════════════════════════")
        Timber.w("║ PlayerUiStateCoordinator.updateUI() CALLED [ID=$updateId]")
        Timber.w("╚════════════════════════════════════════════════════════════════")
        Timber.w("updateUI[$updateId]: currentFile=${state.currentFile?.name}, type=${state.currentFile?.type}")
        Timber.w("updateUI[$updateId]: caller=$caller")

        if (!callback.isActivityAlive()) {
            Timber.d("PlayerUiStateCoordinator.updateUI: Activity not alive, skipping")
            return
        }


        if (state.files.isEmpty()) {
            Timber.d("PlayerUiStateCoordinator.updateUI: Files not loaded yet, skipping")
            return
        }

        // Auto-start slideshow if requested via intent (only once after files are loaded)
        Timber.d("PlayerUiStateCoordinator.updateUI: Checking slideshow auto-start - isSlideshowModeRequested=${callback.isSlideshowModeRequested()}, currentFile=${state.currentFile?.name}")
        if (callback.isSlideshowModeRequested() && state.currentFile != null) {
            callback.clearSlideshowModeRequested()
            Timber.d("PlayerUiStateCoordinator.updateUI: Auto-starting slideshow (requested via intent)")

            coroutineScope.launch {
                delay(300)
                if (!callback.getLatestState().isSlideShowActive) {
                    val intervalSeconds = (callback.getLatestState().slideShowInterval / 1000).toInt()
                    Timber.d("PlayerUiStateCoordinator: Calling toggleSlideShow() and startSlideshow($intervalSeconds)")
                    callback.toggleSlideShow()
                    callback.startSlideshow(intervalSeconds)
                    callback.showSlideshowEnabledMessage()
                    callback.updateSlideShowButton()
                    
                    // Force re-emit state to trigger AudioBackgroundPhotos check (if audio file loaded)
                    callback.forceStateUpdate()
                    
                    // Check if we need to enter audio slideshow photo mode
                    callback.enterAudioSlideshowPhotoModeIfNeeded()
                    
                    Timber.d("PlayerUiStateCoordinator: Slideshow auto-start COMPLETE")
                } else {
                    Timber.w("PlayerUiStateCoordinator: Slideshow already active, skipping auto-start")
                }
            }
        } else {
            Timber.d("PlayerUiStateCoordinator.updateUI: Slideshow auto-start NOT triggered")
        }


        // Show first-run hint if enabled and not shown yet (only in fullscreen mode without command panel)
        if (!callback.hasShownFirstRunHint() && state.currentFile != null && !state.showCommandPanel) {
            coroutineScope.launch {
                val settings = settingsRepository.getSettings().first()
                val isFirstRun = settingsRepository.isPlayerFirstRun()

                if (settings.showPlayerHintOnFirstRun && isFirstRun) {
                    Timber.d("PlayerUiStateCoordinator.updateUI: Showing first-run hint overlay (fullscreen mode)")
                    delay(500)
                    callback.showFirstRunHintOverlay()
                    settingsRepository.setPlayerFirstRun(false)
                    callback.markFirstRunHintShown()
                }
            }
        }

        state.currentFile?.let { file ->
            binding.toolbar.title = "${state.currentIndex + 1}/${state.files.size} - ${file.name}"
            binding.btnPrevious.isEnabled = state.hasPrevious
            binding.btnNext.isEnabled = state.hasNext
            binding.btnPreviousCmd.isEnabled = state.hasPrevious
            binding.btnPreviousCmd.isEnabled = state.hasPrevious
            binding.btnNextCmd.isEnabled = state.hasNext

            binding.btnFavorite.setImageResource(
                if (file.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )

            binding.tvFileNameOverlay?.text = "${file.name} (${state.currentIndex + 1}/${state.files.size})"

            val currentFilePath = callback.getCurrentFilePath()
            if (currentFilePath != file.path) {
                Timber.w(
                    "updateUI[$updateId]: 📂 FILE CHANGED from '$currentFilePath' to '${file.path}' - reloading media"
                )
                callback.setCurrentFilePath(file.path)

                // Hide translation overlays when changing files
                safeViews.translationOverlay.isVisible = false
                binding.translationLensOverlay.isVisible = false

                mediaDisplayCoordinator.display(file)
            } else {
                Timber.w("updateUI[$updateId]: 📋 SAME file path - skipping media reload (metadata update only)")
            }

            // Determine if file should disable touch zones
            // Touch zones DISABLED for:
            // - AUDIO: has ExoPlayer controls for Previous/Next
            // - VIDEO: has ExoPlayer controls for Previous/Next
            // - EPUB/PDF/TEXT: have document-specific controls for navigation
            // Touch zones ENABLED for:
            // - GIF: treat as image - use touch zones for Previous/Next navigation
            // - IMAGE: use touch zones for Previous/Next navigation
            val isGif = file.type == MediaType.GIF
            val isDocument = file.type == MediaType.EPUB || file.type == MediaType.PDF || file.type == MediaType.TEXT
            val isVideoOrAudio = file.type == MediaType.AUDIO || (file.type == MediaType.VIDEO && !isGif)
            val shouldDisableTouchZones = isVideoOrAudio || isDocument
            callback.adjustTouchZonesForVideo(shouldDisableTouchZones)
        }

        callback.updatePanelVisibility(state.showCommandPanel)
        callback.updateCommandAvailability(state)

        val shouldShowControls = !state.showCommandPanel && state.showControls && !callback.getUseTouchZones()
        binding.controlsOverlay.isVisible = shouldShowControls

        val currentFile = state.currentFile
        val settings = callback.getCurrentSettings()
        binding.touchZonesOverlayNew.isVisible =
            !state.showCommandPanel &&
                settings?.alwaysShowTouchZonesOverlay == true &&
                currentFile != null &&
                (currentFile.type == MediaType.IMAGE || currentFile.type == MediaType.GIF)

        callback.updatePlayPauseButton()
        callback.updateSlideShowButton()
        callback.updateVolumeButtonsVisibility()

        // Note: updateAudioTouchZonesVisibility() is invoked inside updatePanelVisibility()
    }
}
