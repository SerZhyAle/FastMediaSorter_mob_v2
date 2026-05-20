package com.sza.fastmediasorter.ui.player.helpers

import androidx.core.view.isVisible
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.model.TouchZoneHintType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Coordinates UI state rendering for PlayerActivity.
 * Keeps PlayerActivity slimmer by centralizing updateUI(state) logic.
 */
@android.annotation.SuppressLint("SetTextI18n")
class PlayerUiStateCoordinator(
    private val binding: ActivityPlayerUnifiedBinding,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val callback: Callback
) {
    private val safeViews = PlayerBindingSafeViews(binding)

    /** Counts distinct IMAGE/GIF files shown since app launch. Resets on process death. */
    private var imageViewCount = 0
    private var lastTrackedFilePath: String? = null
    private val touchZonesHelpMaxViews = 2

    /**
     * Tracks the last known pause state so we can detect the transition to paused.
     * Used to notify FilenameOverlayAutoHideManager on pause interaction.
     */
    private var previousIsPaused: Boolean = false

    /**
     * Tracks whether a file change was detected in the current updateUI() pass.
     * Set inside the currentFile?.let block and consumed after updatePanelVisibility().
     */
    private var pendingOverlayFileType: MediaType? = null

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

        fun isImageVisible(): Boolean
        fun hasImageDrawable(): Boolean

        fun isSlideshowModeRequested(): Boolean
        fun clearSlideshowModeRequested()

        fun hasShownHintType(type: TouchZoneHintType): Boolean
        fun markHintTypeShown(type: TouchZoneHintType)

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

        fun showTouchZoneHintOverlay(type: TouchZoneHintType)
        fun showSlideshowEnabledMessage()
        fun toggleSlideShow()
        fun startSlideshow(intervalSeconds: Int)
        fun getLatestState(): PlayerViewModel.PlayerState
        fun forceStateUpdate()
        fun enterAudioSlideshowPhotoModeIfNeeded()
        fun updateTouchZonesHelpButtonVisibility(visible: Boolean)

        /** Called after a new file is displayed and panel visibility is set. */
        fun onFilenameOverlayFileShown(type: MediaType)

        /** Called when the player transitions into the paused state (non-fullscreen). */
        fun onFilenameOverlayPauseInteraction(type: MediaType)
    }

    /**
     * Determine which touch zone hint type to show based on current player state.
     * Returns null for document types where touch zones are disabled.
     */
    private fun determineTouchZoneHintType(
        showCommandPanel: Boolean,
        currentFile: MediaFile?
    ): TouchZoneHintType? {
        if (currentFile == null) return null
        // Documents have touch zones disabled - no hint needed
        if (currentFile.type in listOf(MediaType.PDF, MediaType.EPUB, MediaType.TEXT)) return null
        if (showCommandPanel) return TouchZoneHintType.COMMAND_PANEL_3ZONE
        val isMedia = currentFile.type in listOf(MediaType.VIDEO, MediaType.AUDIO)
        return if (isMedia) TouchZoneHintType.MEDIA_BOTTOM_RESERVED
               else TouchZoneHintType.FULLSCREEN_9ZONE
    }

    fun updateUI(state: PlayerViewModel.PlayerState) {
        val updateId = System.currentTimeMillis()
        if (BuildConfig.DEBUG) {
            val caller = Thread.currentThread().stackTrace.getOrNull(3)?.let {
                "${it.className}.${it.methodName}():${it.lineNumber}"
            } ?: "Unknown"
            Timber.d("╔════════════════════════════════════════════════════════════════")
            Timber.d("║ PlayerUiStateCoordinator.updateUI() CALLED [ID=$updateId]")
            Timber.d("╚════════════════════════════════════════════════════════════════")
            Timber.d("updateUI[$updateId]: type=${state.currentFile?.type}")
            Timber.d("updateUI[$updateId]: caller=$caller")
        }

        if (!callback.isActivityAlive()) {
            Timber.d("PlayerUiStateCoordinator.updateUI: Activity not alive, skipping")
            return
        }


        if (state.files.isEmpty()) {
            Timber.d("PlayerUiStateCoordinator.updateUI: Files not loaded yet, skipping")
            return
        }

        // Auto-start slideshow if requested via intent (only once after files are loaded)
        Timber.d("PlayerUiStateCoordinator.updateUI: Checking slideshow auto-start - isSlideshowModeRequested=${callback.isSlideshowModeRequested()}")
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
                    Timber.d("PlayerUiStateCoordinator: Slideshow already active, skipping auto-start")
                }
            }
        } else {
            Timber.d("PlayerUiStateCoordinator.updateUI: Slideshow auto-start NOT triggered")
        }


        // Show context-aware touch zone hint if enabled and not shown yet for this mode
        val hintType = determineTouchZoneHintType(state.showCommandPanel, state.currentFile)
        if (hintType != null && !callback.hasShownHintType(hintType) && state.currentFile != null) {
            coroutineScope.launch {
                val settings = settingsRepository.getSettings().first()
                val isFirstRun = settingsRepository.isPlayerFirstRun()
                val alreadyShownPersistently = settingsRepository.isTouchZoneHintShown(hintType)

                if (settings.showPlayerHintOnFirstRun && isFirstRun && !alreadyShownPersistently) {
                    Timber.d("PlayerUiStateCoordinator.updateUI: Showing touch zone hint overlay type=$hintType")
                    delay(500)
                    callback.showTouchZoneHintOverlay(hintType)
                    settingsRepository.setTouchZoneHintShown(hintType, true)
                    // Mark 9-zone first run as done when any hint is shown (backward compat)
                    if (hintType == TouchZoneHintType.FULLSCREEN_9ZONE) {
                        settingsRepository.setPlayerFirstRun(false)
                    }
                    callback.markHintTypeShown(hintType)
                }
            }
        }

        state.currentFile?.let { file ->
            binding.toolbar.title = "${state.currentIndex + 1}/${state.files.size} - ${file.name}"
            binding.btnPrevious.isEnabled = state.hasPrevious
            binding.btnNext.isEnabled = state.hasNext
            binding.btnPreviousCmd.isEnabled = state.hasPrevious
            binding.btnPreviousCmd.isEnabled = state.hasPrevious
            binding.btnRandomCmd.isEnabled = state.files.size > 1
            binding.btnNextCmd.isEnabled = state.hasNext

            binding.btnFavorite.setImageResource(
                if (file.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )

            // For audio: show full filename with extension (no index - metadata shown in center overlay)
            // For other types: add position index for navigation context
            if (file.type == MediaType.AUDIO) {
                binding.tvFileNameOverlay.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                binding.tvFileNameOverlay.text = file.name
            } else {
                binding.tvFileNameOverlay.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                binding.tvFileNameOverlay.text = "${file.name} (${state.currentIndex + 1}/${state.files.size})"
            }

            val currentFilePath = callback.getCurrentFilePath()
            val isImageType = file.type == MediaType.IMAGE || file.type == MediaType.GIF
            val imageVisible = callback.isImageVisible()
            val hasDrawable = callback.hasImageDrawable()
            
            if (currentFilePath != file.path) {
                Timber.d("updateUI[$updateId]: 📂 FILE CHANGED - reloading media")
                callback.setCurrentFilePath(file.path)

                // Hide translation overlays when changing files
                safeViews.translationOverlay.isVisible = false
                binding.translationLensOverlay.isVisible = false

                mediaDisplayCoordinator.display(file)
                // Capture file type so we can notify the overlay manager AFTER
                // updatePanelVisibility() ensures the view is in a consistent state.
                pendingOverlayFileType = file.type            } else if (isImageType && (!imageVisible || !hasDrawable)) {
                Timber.d("updateUI[$updateId]: ⚠️ SAME file path BUT image not ready (visible=$imageVisible, hasDrawable=$hasDrawable) - forcing reload")
                // Force reload if image view is not visible or has no drawable (race condition / loading not complete)
                mediaDisplayCoordinator.display(file)
            } else {
                Timber.d("updateUI[$updateId]: 📋 SAME file path - skipping media reload (metadata update only)")
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

        // Notify overlay manager: file switch (after panel visibility is stabilised)
        pendingOverlayFileType?.let { type ->
            callback.onFilenameOverlayFileShown(type)
            pendingOverlayFileType = null
        }

        // Notify overlay manager: pause interaction (detect transition to paused)
        val currentType = state.currentFile?.type
        if (currentType != null && state.isPaused && !previousIsPaused) {
            callback.onFilenameOverlayPauseInteraction(currentType)
        }
        previousIsPaused = state.isPaused

        val shouldShowControls = !state.showCommandPanel && state.showControls && !callback.getUseTouchZones()
        binding.controlsOverlay.isVisible = shouldShowControls

        val currentFile = state.currentFile
        val settings = callback.getCurrentSettings()
        safeViews.touchZonesOverlayNew.isVisible =
            !state.showCommandPanel &&
                settings?.alwaysShowTouchZonesOverlay == true &&
                currentFile != null &&
                (currentFile.type == MediaType.IMAGE || currentFile.type == MediaType.GIF)

        callback.updatePlayPauseButton()
        callback.updateSlideShowButton()
        callback.updateVolumeButtonsVisibility()

        // Touch Zones Help button (?) - visible for first 3 IMAGE/GIF files since app launch.
        // Counter resets on process death (in-memory only).
        val isImageGif = currentFile != null &&
            (currentFile.type == MediaType.IMAGE || currentFile.type == MediaType.GIF)
        val isFullscreenTouchZone = !state.showCommandPanel && callback.getUseTouchZones()

        if (isFullscreenTouchZone && isImageGif) {
            val path = currentFile!!.path
            if (path != lastTrackedFilePath) {
                lastTrackedFilePath = path
                imageViewCount++
                Timber.d("TouchZonesHelpButton: imageViewCount=$imageViewCount (max=$touchZonesHelpMaxViews)")
            }
        }

        val showTouchZonesHelpButton = isFullscreenTouchZone && isImageGif &&
            imageViewCount <= touchZonesHelpMaxViews
        callback.updateTouchZonesHelpButtonVisibility(showTouchZonesHelpButton)

        // Note: updateAudioTouchZonesVisibility() is invoked inside updatePanelVisibility()
    }

    /**
     * Determine the current hint type based on latest state (for manual help button trigger).
     */
    fun getCurrentHintType(state: PlayerViewModel.PlayerState): TouchZoneHintType? {
        return determineTouchZoneHintType(state.showCommandPanel, state.currentFile)
    }
}
