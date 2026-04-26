package com.sza.fastmediasorter.ui.player

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.helpers.CommandPanelLayoutPlanner
import com.sza.fastmediasorter.ui.player.helpers.LanguageBadgeDrawable
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.roundToInt

/**
 * Manages command panel in PlayerActivity:
 * - Setup button click listeners
 * - Update button availability based on state
 * - Apply/restore small controls layout
 * - Track original button heights
 * - Adaptive layout based on orientation (landscape vs portrait)
 */
class CommandPanelController(
    private val binding: ActivityPlayerUnifiedBinding,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val callback: CommandPanelCallback
) {
    
    interface CommandPanelCallback {
        fun onBackClicked()
        fun onPreviousClicked()
        fun onRandomClicked()
        fun onNextClicked()
        fun onRenameClicked()
        fun onDeleteClicked()
        fun onShareClicked()
        fun onEditClicked()
        fun onUndoClicked()
        fun onFullscreenClicked()
        fun onSlideshowClicked()
        fun onCopyPanelHeaderClicked()
        fun onMovePanelHeaderClicked()
        fun onInfoClicked()
        fun onLyricsClicked()
        fun onSearchYoutubeMusicClicked()
        fun onCastClicked()
        fun onFavoriteClicked()
        fun onSearchClicked()
        fun onTranslateClicked()
        fun onOcrClicked()
        fun onGoogleLensClicked()
        fun onCopyTextClicked()
        fun onEditTextClicked()
        fun onOcrSettingsClicked()
        fun onTranslationSettingsClicked()
        fun onSleepTimerClicked()
        fun onReopenEncodingClicked()
        fun onToggleMarkdownClicked()
        fun onReaderSettingsClicked()
        fun onReadAloudClicked()
        fun onPdfScrollModeClicked()
        fun onPdfColorModeClicked()
        fun onPdfThumbnailsClicked()
        fun onEpubReaderSettingsClicked()
        fun onEpubSearchAllClicked()
        fun onPrintClicked()
        fun onSaveFrameClicked()
        fun on3dVrToggleClicked()
    }
    
    private val originalCommandButtonHeights = mutableMapOf<Int, Int>()
    private val originalMargins = mutableMapOf<Int, android.graphics.Rect>()
    private val originalPaddings = mutableMapOf<Int, android.graphics.Rect>()
    private val originalContainerPaddings = mutableMapOf<Int, android.graphics.Rect>()
    private var smallControlsApplied = false
    private val safeViews = PlayerBindingSafeViews(binding)

    // Adaptive portrait layout
    private val planner = CommandPanelLayoutPlanner()
    private var latestOverflowCommands: List<CommandPanelLayoutPlanner.PlayerCommand> = emptyList()
    private var lastKnownFavoriteVisible = true

    // Cached state for overflow menu visibility
    private var cachedState: PlayerViewModel.PlayerState? = null
    private var isLandscapeMode = true
    
    companion object {
        private const val SMALL_CONTROLS_SCALE = 0.5f
    }
    
    /**
     * Setup all command panel button click listeners
     */
    fun setupCommandPanelControls() {
        binding.btnBack.setOnClickListener {
            callback.onBackClicked()
        }
        
        // Overflow menu button for portrait mode
        safeViews.btnOverflowMenu.setOnClickListener { view ->
            showOverflowMenu(view)
        }

        binding.btnPreviousCmd.setOnClickListener {
            Timber.d("CommandPanelController: btnPreviousCmd clicked")
            callback.onPreviousClicked()
        }

        binding.btnRandomCmd.setOnClickListener {
            Timber.d("CommandPanelController: btnRandomCmd clicked")
            callback.onRandomClicked()
        }

        binding.btnNextCmd.setOnClickListener {
            Timber.d("CommandPanelController: btnNextCmd clicked")
            callback.onNextClicked()
        }

        safeViews.btnRenameCmd.setOnClickListener {
            callback.onRenameClicked()
        }

        binding.btnDeleteCmd.setOnClickListener {
            callback.onDeleteClicked()
        }
        
        binding.btnShareCmd.setOnClickListener {
            callback.onShareClicked()
        }
        
        safeViews.btnEditCmd.setOnClickListener {
            callback.onEditClicked()
        }

        safeViews.btnSaveFrameCmd.setOnClickListener {
            callback.onSaveFrameClicked()
        }

        if (BuildConfig.SUPPORT_VR_PLAYER) {
            safeViews.btn3dVrCmd.setOnClickListener {
                callback.on3dVrToggleClicked()
            }
        }

        safeViews.btnPrintCmd.setOnClickListener {
            callback.onPrintClicked()
        }
        
        safeViews.btnUndoCmd.setOnClickListener {
            callback.onUndoClicked()
        }

        binding.btnFullscreenCmd.setOnClickListener {
            callback.onFullscreenClicked()
        }

        binding.btnSlideshowCmd.setOnClickListener {
            callback.onSlideshowClicked()
        }

        binding.btnFavorite.setOnClickListener {
            callback.onFavoriteClicked()
        }

        binding.btnInfoCmd.setOnClickListener {
            callback.onInfoClicked()
        }
        
        // Setup collapsible Copy to panel
        safeViews.copyToPanelHeader.apply {
            setOnClickListener { _ ->
                Timber.d("CommandPanelController: copyToPanelHeader clicked - toggling Copy panel")
                callback.onCopyPanelHeaderClicked()
            }
            // Prevent click propagation to underlying PlayerView
            isClickable = true
            isFocusable = true
        }
        
        // Setup collapsible Move to panel
        safeViews.moveToPanelHeader.apply {
            setOnClickListener { _ ->
                Timber.d("CommandPanelController: moveToPanelHeader clicked - toggling Move panel")
                callback.onMovePanelHeaderClicked()
            }
            // Prevent click propagation to underlying PlayerView
            isClickable = true
            isFocusable = true
        }
    }
    
    /**
     * Update command availability based on state
     */
    fun updateCommandAvailability(state: PlayerViewModel.PlayerState) {
        // Cache state for overflow menu
        cachedState = state
        
        val currentFile = state.currentFile ?: return
        val resource = state.resource
        
        // Check if resource is read-only
        val isReadOnly = resource?.isReadOnly == true
        
        // For network resources, assume permissions based on resource type
        val isNetworkResource = resource != null && 
            (resource.type == ResourceType.SMB || 
             resource.type == ResourceType.SFTP || 
             resource.type == ResourceType.FTP)
        
        var canWrite: Boolean
        val canRead: Boolean
        
        if (isNetworkResource) {
            // Network resources: assume read/write based on resource configuration
            canWrite = true // Network resources typically allow operations
            canRead = true
        } else if (currentFile.path.startsWith("content://")) {
            // SAF resources: check DocumentFile permissions and resource.isWritable
            canWrite = resource?.isWritable ?: false
            canRead = try {
                val uri = Uri.parse(currentFile.path)
                val docFile = DocumentFile.fromSingleUri(binding.root.context, uri)
                docFile?.canRead() ?: false
            } catch (e: Exception) {
                Timber.e(e, "CommandPanelController: Error checking SAF URI read permission")
                false
            }
        } else {
            // Regular file system: check actual file permissions
            val file = File(currentFile.path)
            // Fix: Use resource writable state if available, otherwise fall back to file system check
            // On modern Android, File.canWrite() may return false even if we have access via other means
            canWrite = resource?.isWritable ?: file.canWrite()
            canRead = file.canRead()
        }
        
        // Enforce Read-Only mode
        if (isReadOnly) {
            canWrite = false
        }
        
        // AUDIO override: for audio files the command panel is always force-shown by
        // updatePanelVisibility() regardless of state.showCommandPanel. We must mirror
        // that logic here so buttons/panels are consistent with what the user sees.
        val isAudioFile = currentFile.type == MediaType.AUDIO
        val effectiveShowCommandPanel = state.showCommandPanel || isAudioFile
        
        // Adaptive layout based on orientation
        // Portrait: Back | Overflow(...) | Delete, Fullscreen | Prev, Next
        // Landscape: All buttons visible
        
        val showInLandscape = effectiveShowCommandPanel && isLandscapeMode
        val showInPortrait = effectiveShowCommandPanel && !isLandscapeMode
        
        // Random stays limited to library-like profiles where stochastic browsing makes sense.
        val showRandomNavigation = shouldShowRandomNavigation(resource?.profile)

        // Back, Delete, Fullscreen, Previous, Next: always visible in command panel mode
        binding.btnBack.isVisible = effectiveShowCommandPanel
        // Hide delete button if not writable or not allowed
        binding.btnDeleteCmd.isVisible = effectiveShowCommandPanel && canWrite && state.allowDelete
        binding.btnDeleteCmd.isEnabled = canWrite && canRead && state.allowDelete
        binding.btnPreviousCmd.isVisible = effectiveShowCommandPanel
        binding.btnRandomCmd.isVisible = effectiveShowCommandPanel && showRandomNavigation
        binding.btnNextCmd.isVisible = effectiveShowCommandPanel
        
        // Fullscreen: not applicable for audio; visible in both modes for image/video/document
        binding.btnFullscreenCmd.isVisible = effectiveShowCommandPanel && 
            (currentFile.type == MediaType.IMAGE || currentFile.type == MediaType.GIF ||
             currentFile.type == MediaType.VIDEO || 
             currentFile.type == MediaType.PDF || currentFile.type == MediaType.TEXT ||
             currentFile.type == MediaType.EPUB)
        
        // Slideshow: visible in both modes for supported types (including AUDIO for background photos)
        binding.btnSlideshowCmd.isVisible = effectiveShowCommandPanel && 
             (currentFile.type == MediaType.IMAGE || currentFile.type == MediaType.GIF ||
              currentFile.type == MediaType.VIDEO || currentFile.type == MediaType.AUDIO)

        // Common Action Buttons (Visible in both Portrait and Landscape)
        if (effectiveShowCommandPanel) {
            // Check enableFavorites setting (async)
            coroutineScope.launch {
                val settings = settingsRepository.getSettings().first()
                val shouldShowFavorite = settings.enableFavorites || state.resource?.id == -100L
                
                withContext(Dispatchers.Main) {
                    lastKnownFavoriteVisible = shouldShowFavorite
                    binding.btnFavorite.isVisible = shouldShowFavorite
                    binding.btnFavorite.setImageResource(if (currentFile.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
                }
            }
            
            binding.btnShareCmd.isVisible = true
            binding.btnInfoCmd.isVisible = true
        }
        
        // Center buttons visibility
        if (showInPortrait) {
            // Adaptive portrait layout: planner decides which commands fit on bar vs overflow
            val activeCommands = planner.buildActiveCommands(
                state, canWrite, canRead, isWifiConnected(binding.root.context)
            )
            val availablePx = resolveAvailableCenterWidthPx(state, canWrite)
            val density = binding.root.resources.displayMetrics.density
            val buttonPx = (40 * density).toInt()
            val overflowPx = (40 * density).toInt()
            val result = planner.planLayout(activeCommands, availablePx, buttonPx, overflowPx)
            latestOverflowCommands = result.overflowCommands

            // Hide all adaptive center buttons; planner result shows only bar commands
            getOverflowableButtons().forEach { it.isVisible = false }
            result.barCommands.forEach { cmd -> barViewForCommand(cmd)?.isVisible = true }

            // RENAME isEnabled mirrors landscape rule (requires canRead)
            safeViews.btnRenameCmd.isEnabled = canWrite && canRead && state.allowRename

            // Overflow button: only when the planner says some commands are in overflow
            safeViews.btnOverflowMenu.isVisible = result.showOverflowButton

        } else if (showInLandscape) {
            safeViews.btnOverflowMenu.isVisible = false
            latestOverflowCommands = emptyList()
            // Show center buttons based on logic
            val isImage = currentFile.type == MediaType.IMAGE || currentFile.type == MediaType.GIF
            val isVideo = currentFile.type == MediaType.VIDEO || currentFile.type == MediaType.AUDIO
            val isPdf = currentFile.type == MediaType.PDF
            val isText = currentFile.type == MediaType.TEXT
            val isEpub = currentFile.type == MediaType.EPUB

            // Common actions
            safeViews.btnRenameCmd.isEnabled = canWrite && canRead && state.allowRename
            // Hide rename if not writable or not allowed
            safeViews.btnRenameCmd.isVisible = canWrite && state.allowRename
            
            safeViews.btnUndoCmd.isVisible = state.lastOperation != null && canWrite
            safeViews.btnLyricsCmd.isVisible = isVideo && currentFile.type == MediaType.AUDIO
            safeViews.btnSearchYoutubeMusicCmd.isVisible = isVideo && currentFile.type == MediaType.AUDIO
            safeViews.btnCastCmd.isVisible = (isImage || isVideo) && isWifiConnected(binding.root.context)
            // Edit is visible for images (if writable) OR video (always, as it's controls)
            safeViews.btnEditCmd.isVisible = (isImage && canWrite) || isVideo || isPdf
            // Save Frame is a direct video-only command in the command panel.
            safeViews.btnSaveFrameCmd.isVisible = currentFile.type == MediaType.VIDEO
            // Immersive toggle: VR flavor only, visible for all video files including flat 2D.
            if (BuildConfig.SUPPORT_VR_PLAYER) {
                safeViews.btn3dVrCmd.isVisible = currentFile.type == MediaType.VIDEO
            }

            // Update button contentDescription based on file type
            if (isVideo) {
                safeViews.btnEditCmd.contentDescription = binding.root.context.getString(R.string.control)
            } else {
                safeViews.btnEditCmd.contentDescription = binding.root.context.getString(R.string.edit)
            }
            
            // PDF Actions
            safeViews.btnGoogleLensPdfCmd.isVisible = isPdf
            safeViews.btnOcrPdfCmd.isVisible = isPdf
            safeViews.btnTranslatePdfCmd.isVisible = isPdf
            safeViews.btnSearchPdfCmd.isVisible = isPdf
            safeViews.btnPdfThumbnailsCmd.isVisible = isPdf
            
            // Text Actions
            safeViews.btnCopyTextCmd.isVisible = (isText || isPdf)
            safeViews.btnEditTextCmd.isVisible = isText && canWrite // Edit text requires write
            safeViews.btnTranslateTextCmd.isVisible = isText
            safeViews.btnTextSettingsCmd.isVisible = isText
            safeViews.btnSearchTextCmd.isVisible = isText
            
            // EPUB Actions
            safeViews.btnSearchEpubCmd.isVisible = isEpub
            safeViews.btnTranslateEpubCmd.isVisible = isEpub
            safeViews.btnEpubTextSettingsCmd.isVisible = isEpub
            safeViews.btnOcrEpubCmd.isVisible = isEpub
            
            // PDF Actions
            safeViews.btnPdfTextSettingsCmd.isVisible = isPdf
            
            // Image Actions (for IMAGE/GIF)
            if (isImage) {
                safeViews.btnTranslateImageCmd.isVisible = state.enableTranslation
                safeViews.btnOcrImageCmd.isVisible = state.enableOcr
                safeViews.btnGoogleLensImageCmd.isVisible = state.enableGoogleLens
                safeViews.btnImageTextSettingsCmd.isVisible = true
                Timber.d("CommandPanelController: Image buttons IN LANDSCAPE - translate=${state.enableTranslation}, ocr=${state.enableOcr}, lens=${state.enableGoogleLens}")
            } else {
                safeViews.btnTranslateImageCmd.isVisible = false
                safeViews.btnImageTextSettingsCmd.isVisible = false
                safeViews.btnOcrImageCmd.isVisible = false
                safeViews.btnGoogleLensImageCmd.isVisible = false
            }

            // Print: visible for PDF, TEXT, and IMAGE/GIF; not for video/audio/epub
            safeViews.btnPrintCmd.isVisible = isPdf || isText || isImage
        } else {
            // Command panel hidden
            safeViews.btnOverflowMenu.isVisible = false
            latestOverflowCommands = emptyList()
            getOverflowableButtons().forEach { it.isVisible = false }
        }
        
        // Enable state for always-enabled buttons
        binding.btnBack.isEnabled = true
        binding.btnPreviousCmd.isEnabled = true
        binding.btnRandomCmd.isEnabled = state.files.size > 1
        binding.btnNextCmd.isEnabled = true
        binding.btnSlideshowCmd.isEnabled = true
        
        // Update slideshow button color based on active state
        updateSlideshowButtonColor(state.isSlideShowActive)
        
        // Copy/Move panels visibility based on settings AND whether there are destination buttons
        val hasCopyButtons = safeViews.copyToButtonsGrid.childCount > 0
        val hasMoveButtons = safeViews.moveToButtonsGrid.childCount > 0
        
        val copyPanelVisible = effectiveShowCommandPanel && state.enableCopying && hasCopyButtons
        val movePanelVisible = effectiveShowCommandPanel && state.enableMoving && hasMoveButtons && canWrite
        
        Timber.v("CommandPanelController.updateCommandAvailability: copyPanel=$copyPanelVisible (showCmd=${state.showCommandPanel}, enableCopy=${state.enableCopying}, hasCopy=$hasCopyButtons, childCount=${safeViews.copyToButtonsGrid.childCount})")
        Timber.v("CommandPanelController.updateCommandAvailability: movePanel=$movePanelVisible (showCmd=${state.showCommandPanel}, enableMove=${state.enableMoving}, hasMove=$hasMoveButtons, canWrite=$canWrite, childCount=${safeViews.moveToButtonsGrid.childCount})")
        
        safeViews.copyToPanel.isVisible = copyPanelVisible
        safeViews.moveToPanel.isVisible = movePanelVisible

        logPanelGeometrySnapshot("decision")
        
        // CRITICAL FIX: Force layout recalculation when panel visibility changes
        // Problem: mediaContentArea has layout_weight=1 and takes all available space
        // When panels change from gone->visible, LinearLayout doesn't recalculate automatically
        // Result: panels are pushed off-screen (Y > screen height)
        // Solution: Force complete layout recalculation via requestLayout() + requestApplyInsets()
        // This is the same approach used in updateSystemBarsForPlayer after exitFullscreen
        if (copyPanelVisible || movePanelVisible) {
            binding.root.post {
                // Force immediate layout recalculation
                binding.mediaContentArea.requestLayout()
                safeViews.copyToPanel.requestLayout()
                safeViews.moveToPanel.requestLayout()
                binding.root.requestLayout()
                
                // Also request insets reapply for proper system bars handling
                binding.root.requestApplyInsets()
                Timber.d("CommandPanelController: Requested full layout recalculation and insets reapply")
            }
        }
        
        // DEBUG: Log actual panel state after visibility change (single + delayed snapshot)
        safeViews.copyToPanel.post {
            logPanelGeometrySnapshot("post")
            binding.root.post {
                logPanelGeometrySnapshot("post+1")
            }
        }
        

    }

    private fun logPanelGeometrySnapshot(stage: String) {
        val visibleFrame = Rect()
        binding.root.getWindowVisibleDisplayFrame(visibleFrame)

        val rootLoc = IntArray(2)
        val mediaLoc = IntArray(2)
        val bottomLoc = IntArray(2)
        val copyLoc = IntArray(2)
        val moveLoc = IntArray(2)

        binding.root.getLocationOnScreen(rootLoc)
        binding.mediaContentArea.getLocationOnScreen(mediaLoc)
        safeViews.bottomPanelsContainer.getLocationOnScreen(bottomLoc)
        safeViews.copyToPanel.getLocationOnScreen(copyLoc)
        safeViews.moveToPanel.getLocationOnScreen(moveLoc)

        val copyGlobalRect = Rect()
        val moveGlobalRect = Rect()
        val copyLocalRect = Rect()
        val moveLocalRect = Rect()

        val copyGlobalVisible = safeViews.copyToPanel.getGlobalVisibleRect(copyGlobalRect)
        val moveGlobalVisible = safeViews.moveToPanel.getGlobalVisibleRect(moveGlobalRect)
        val copyLocalVisible = safeViews.copyToPanel.getLocalVisibleRect(copyLocalRect)
        val moveLocalVisible = safeViews.moveToPanel.getLocalVisibleRect(moveLocalRect)

        Timber.v(
            "PanelGeom[$stage]: visibleFrame=[${visibleFrame.left},${visibleFrame.top}..${visibleFrame.right},${visibleFrame.bottom}] h=${visibleFrame.height()} | root=(x=${rootLoc[0]},y=${rootLoc[1]},w=${binding.root.width},h=${binding.root.height}) | media=(x=${mediaLoc[0]},y=${mediaLoc[1]},w=${binding.mediaContentArea.width},h=${binding.mediaContentArea.height}) | bottom=(x=${bottomLoc[0]},y=${bottomLoc[1]},w=${safeViews.bottomPanelsContainer.width},h=${safeViews.bottomPanelsContainer.height},vis=${safeViews.bottomPanelsContainer.visibility})"
        )

        Timber.v(
            "PanelGeom[$stage]: copy=(vis=${safeViews.copyToPanel.visibility},isVisible=${safeViews.copyToPanel.isVisible},x=${copyLoc[0]},y=${copyLoc[1]},w=${safeViews.copyToPanel.width},h=${safeViews.copyToPanel.height},globalVisible=$copyGlobalVisible,globalRect=$copyGlobalRect,localVisible=$copyLocalVisible,localRect=$copyLocalRect,childRows=${safeViews.copyToButtonsGrid.childCount})"
        )

        Timber.v(
            "PanelGeom[$stage]: move=(vis=${safeViews.moveToPanel.visibility},isVisible=${safeViews.moveToPanel.isVisible},x=${moveLoc[0]},y=${moveLoc[1]},w=${safeViews.moveToPanel.width},h=${safeViews.moveToPanel.height},globalVisible=$moveGlobalVisible,globalRect=$moveGlobalRect,localVisible=$moveLocalVisible,localRect=$moveLocalRect,childRows=${safeViews.moveToButtonsGrid.childCount})"
        )
    }
    
    /**
     * Update slideshow button visual state (color/alpha) based on active state
     */
    fun updateSlideshowButtonColor(isActive: Boolean) {
        Timber.v("CommandPanelController.updateSlideshowButtonColor: isActive=$isActive, btn=${binding.btnSlideshowCmd}")
        binding.btnSlideshowCmd.alpha = if (isActive) 1.0f else 0.5f
        // ImageButton uses imageTintList instead of setTextColor
        if (isActive) {
            binding.btnSlideshowCmd.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
            binding.btnSlideshowCmd.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#33FF0000"))
        } else {
            binding.btnSlideshowCmd.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            binding.btnSlideshowCmd.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        }
    }
    
    /**
     * Apply small controls layout (50% height, margins, and paddings) if not already applied
     */
    fun applySmallControlsIfNeeded() {
        if (smallControlsApplied) return

        commandPanelButtons().forEach { button ->
            // Scale button height
            val baseline = originalCommandButtonHeights.getOrPut(button.id) {
                resolveOriginalButtonHeight(button)
            }

            if (baseline <= 0) {
                Timber.w("CommandPanelController.applySmallControlsIfNeeded: Skipping button ${button.id} with baseline=$baseline")
                return@forEach
            }

            val params = button.layoutParams
            if (params != null) {
                // Save and scale height
                params.height = (baseline * SMALL_CONTROLS_SCALE).roundToInt().coerceAtLeast(1)
                
                // Save and scale margins
                if (params is android.view.ViewGroup.MarginLayoutParams) {
                    // Save original margins
                    originalMargins.putIfAbsent(
                        button.id,
                        android.graphics.Rect(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin)
                    )
                    
                    params.setMargins(
                        (params.leftMargin * SMALL_CONTROLS_SCALE).roundToInt(),
                        (params.topMargin * SMALL_CONTROLS_SCALE).roundToInt(),
                        (params.rightMargin * SMALL_CONTROLS_SCALE).roundToInt(),
                        (params.bottomMargin * SMALL_CONTROLS_SCALE).roundToInt()
                    )
                }
                
                button.layoutParams = params
            }
            
            // Save and scale paddings
            originalPaddings.putIfAbsent(
                button.id,
                android.graphics.Rect(button.paddingLeft, button.paddingTop, button.paddingRight, button.paddingBottom)
            )
            
            button.setPadding(
                (button.paddingLeft * SMALL_CONTROLS_SCALE).roundToInt(),
                (button.paddingTop * SMALL_CONTROLS_SCALE).roundToInt(),
                (button.paddingRight * SMALL_CONTROLS_SCALE).roundToInt(),
                (button.paddingBottom * SMALL_CONTROLS_SCALE).roundToInt()
            )
        }

        // Scale command panel container paddings
        val containers = listOf(
            binding.topCommandPanel,
            safeViews.copyToPanel,
            safeViews.moveToPanel,
            safeViews.copyToButtonsGrid,
            safeViews.moveToButtonsGrid
        )
        
        containers.forEach { container ->
            // Save original padding
            originalContainerPaddings.putIfAbsent(
                container.id,
                android.graphics.Rect(container.paddingLeft, container.paddingTop, container.paddingRight, container.paddingBottom)
            )
            
            container.setPadding(
                (container.paddingLeft * SMALL_CONTROLS_SCALE).roundToInt(),
                (container.paddingTop * SMALL_CONTROLS_SCALE).roundToInt(),
                (container.paddingRight * SMALL_CONTROLS_SCALE).roundToInt(),
                (container.paddingBottom * SMALL_CONTROLS_SCALE).roundToInt()
            )
        }

        smallControlsApplied = true
    }

    /**
     * Restore original button heights, margins, and paddings if small controls were applied
     */
    fun restoreCommandButtonHeightsIfNeeded() {
        if (!smallControlsApplied) return

        commandPanelButtons().forEach { button ->
            // Restore height
            val baseline = originalCommandButtonHeights[button.id] ?: return@forEach
            val params = button.layoutParams ?: return@forEach
            params.height = baseline
            
            // Restore margins
            if (params is android.view.ViewGroup.MarginLayoutParams) {
                val originalMargin = originalMargins[button.id]
                if (originalMargin != null) {
                    params.setMargins(
                        originalMargin.left,
                        originalMargin.top,
                        originalMargin.right,
                        originalMargin.bottom
                    )
                }
            }
            
            button.layoutParams = params
            
            // Restore padding
            val originalPadding = originalPaddings[button.id]
            if (originalPadding != null) {
                button.setPadding(
                    originalPadding.left,
                    originalPadding.top,
                    originalPadding.right,
                    originalPadding.bottom
                )
            }
        }
        
        // Restore container paddings
        val containers = listOf(
            binding.topCommandPanel,
            safeViews.copyToPanel,
            safeViews.moveToPanel,
            safeViews.copyToButtonsGrid,
            safeViews.moveToButtonsGrid
        )
        
        containers.forEach { container ->
            val originalPadding = originalContainerPaddings[container.id]
            if (originalPadding != null) {
                container.setPadding(
                    originalPadding.left,
                    originalPadding.top,
                    originalPadding.right,
                    originalPadding.bottom
                )
            }
        }

        smallControlsApplied = false
    }

    private fun commandPanelButtons(): List<View> = listOfNotNull(
        // Navigation
        binding.btnBack,
        binding.btnPreviousCmd,
        binding.btnRandomCmd,
        binding.btnNextCmd,
        // File operations
        safeViews.btnRenameCmd,
        binding.btnDeleteCmd,
        binding.btnShareCmd,
        binding.btnInfoCmd,
        safeViews.btnEditCmd,
        safeViews.btnUndoCmd,
        binding.btnFullscreenCmd,
        binding.btnSlideshowCmd,
        binding.btnFavorite,
        // Overflow menu
        safeViews.btnOverflowMenu,
        // Text commands
        safeViews.btnSearchTextCmd,
        safeViews.btnTranslateTextCmd,
        safeViews.btnTextSettingsCmd,
        safeViews.btnCopyTextCmd,
        safeViews.btnEditTextCmd,
        // PDF commands
        safeViews.btnSearchPdfCmd,
        safeViews.btnTranslatePdfCmd,
        safeViews.btnPdfTextSettingsCmd,
        safeViews.btnOcrPdfCmd,
        safeViews.btnGoogleLensPdfCmd,
        // EPUB commands
        safeViews.btnSearchEpubCmd,
        safeViews.btnTranslateEpubCmd,
        safeViews.btnEpubTextSettingsCmd,
        safeViews.btnOcrEpubCmd,
        // Image commands
        safeViews.btnTranslateImageCmd,
        safeViews.btnImageTextSettingsCmd,
        safeViews.btnOcrImageCmd,
        safeViews.btnGoogleLensImageCmd,
        // Audio commands
        safeViews.btnLyricsCmd,
        safeViews.btnSearchYoutubeMusicCmd,
        // Cast
        safeViews.btnCastCmd
    )

    private fun resolveOriginalButtonHeight(button: View): Int {
        val paramsHeight = button.layoutParams?.height ?: 0
        return when {
            paramsHeight > 0 -> paramsHeight
            button.height > 0 -> button.height
            button.measuredHeight > 0 -> button.measuredHeight
            else -> 0
        }
    }
    
    /**
     * Update layout based on orientation change
     * @param configuration Current configuration
     */
    fun updateOrientation(configuration: Configuration) {
        isLandscapeMode = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        Timber.d("CommandPanelController.updateOrientation: isLandscape=$isLandscapeMode")
        
        // Post so the layout has settled to the new dimensions before the planner measures width
        cachedState?.let { state ->
            binding.topCommandPanel.post { updateCommandAvailability(state) }
        }
    }
    
    /**
     * Show the overflow popup menu, ordered by command priority (highest priority first).
     *
     * Items are built programmatically from [latestOverflowCommands] so the display order
     * reflects the runtime priority model, not the static XML declaration order.
     */
    @SuppressLint("RestrictedApi")
    private fun showOverflowMenu(anchor: View) {
        val state = cachedState ?: return
        val currentFile = state.currentFile ?: return
        val context = binding.root.context

        val commands = latestOverflowCommands
        if (commands.isEmpty()) return

        val popup = PopupMenu(context, anchor)
        popup.setForceShowIcon(true)

        val isVideo = currentFile.type == MediaType.VIDEO || currentFile.type == MediaType.AUDIO
        val iconColor = android.graphics.Color.DKGRAY

        // Build menu dynamically in priority order
        for (cmd in commands) {
            val title = if (cmd == CommandPanelLayoutPlanner.PlayerCommand.EDIT && isVideo) {
                context.getString(R.string.control)
            } else {
                context.getString(cmd.titleResId)
            }
            val item = popup.menu.add(android.view.Menu.NONE, cmd.menuItemId, cmd.priority, title)
            if (cmd.iconResId != 0) {
                val drawable = androidx.core.content.ContextCompat.getDrawable(context, cmd.iconResId)
                drawable?.setTint(iconColor)
                item.icon = drawable
            }
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_rename -> callback.onRenameClicked()
                R.id.menu_lyrics -> callback.onLyricsClicked()
                R.id.menu_search_youtube_music -> callback.onSearchYoutubeMusicClicked()
                R.id.menu_cast -> callback.onCastClicked()
                R.id.menu_edit -> callback.onEditClicked()
                R.id.menu_search -> callback.onSearchClicked()
                R.id.menu_translate -> callback.onTranslateClicked()
                R.id.menu_text_settings -> callback.onTranslationSettingsClicked()
                R.id.menu_ocr -> callback.onOcrClicked()
                R.id.menu_google_lens -> callback.onGoogleLensClicked()
                R.id.menu_copy_text -> callback.onCopyTextClicked()
                R.id.menu_edit_text -> callback.onEditTextClicked()
                R.id.menu_undo -> callback.onUndoClicked()
                R.id.menu_sleep_timer -> callback.onSleepTimerClicked()
                R.id.menu_reopen_encoding -> callback.onReopenEncodingClicked()
                R.id.menu_toggle_markdown -> callback.onToggleMarkdownClicked()
                R.id.menu_reader_settings -> callback.onReaderSettingsClicked()
                R.id.menu_read_aloud -> callback.onReadAloudClicked()
                R.id.menu_pdf_scroll_mode -> callback.onPdfScrollModeClicked()
                R.id.menu_pdf_color_mode -> callback.onPdfColorModeClicked()
                R.id.menu_pdf_thumbnails -> callback.onPdfThumbnailsClicked()
                R.id.menu_epub_reader_settings -> callback.onEpubReaderSettingsClicked()
                R.id.menu_epub_search_all -> callback.onEpubSearchAllClicked()
                R.id.menu_print -> callback.onPrintClicked()
                R.id.menu_save_frame -> callback.onSaveFrameClicked()
                R.id.menu_3d_vr -> callback.on3dVrToggleClicked()
            }
            true
        }

        // Long-click shortcut: OCR settings / translation settings
        try {
            val listView = (popup.menu as? androidx.appcompat.view.menu.MenuBuilder)?.let { _ ->
                popup.javaClass.getDeclaredField("mPopup")
                    .apply { isAccessible = true }
                    .get(popup)
                    ?.javaClass
                    ?.getDeclaredMethod("getListView")
                    ?.invoke(
                        popup.javaClass.getDeclaredField("mPopup")
                            .apply { isAccessible = true }
                            .get(popup)
                    ) as? android.widget.ListView
            }
            listView?.setOnItemLongClickListener { _, _, position, _ ->
                val menuItem = popup.menu.getItem(position)
                when (menuItem.itemId) {
                    R.id.menu_ocr -> { callback.onOcrSettingsClicked(); popup.dismiss(); true }
                    R.id.menu_translate -> { callback.onTranslationSettingsClicked(); popup.dismiss(); true }
                    else -> false
                }
            }
        } catch (e: Exception) {
            Timber.w("Failed to set long click listener for menu: ${e.message}")
        }

        // Async translate icon: update language-pair badge then show popup
        val hasTranslate = commands.any {
            it.menuItemId == R.id.menu_translate
        }
        if (hasTranslate) {
            coroutineScope.launch {
                try {
                    val settings = settingsRepository.getSettings().first()
                    val src = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
                    val tgt = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
                    val badge = LanguageBadgeDrawable(context, src, tgt, iconColor)
                    withContext(Dispatchers.Main) {
                        popup.menu.findItem(R.id.menu_translate)?.icon = badge
                        popup.show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { popup.show() }
                }
            }
        } else {
            popup.show()
        }
    }
    
    /**
     * All adaptive center buttons that may move between bar and overflow.
     * These are hidden at the start of every portrait-branch pass and then
     * selectively shown by the planner result.
     */
    private fun getOverflowableButtons(): List<View> {
        val list = mutableListOf<View>(
            safeViews.btnRenameCmd,
            safeViews.btnLyricsCmd,
            safeViews.btnSearchYoutubeMusicCmd,
            safeViews.btnCastCmd,
            safeViews.btnEditCmd,
            safeViews.btnSaveFrameCmd,
            safeViews.btnPrintCmd,
            safeViews.btnUndoCmd,
            safeViews.btnGoogleLensPdfCmd,
            safeViews.btnOcrPdfCmd,
            safeViews.btnTranslatePdfCmd,
            safeViews.btnSearchPdfCmd,
            safeViews.btnSearchTextCmd,
            safeViews.btnEditTextCmd,
            safeViews.btnTranslateTextCmd,
            safeViews.btnTextSettingsCmd,
            safeViews.btnCopyTextCmd,
            safeViews.btnSearchEpubCmd,
            safeViews.btnTranslateEpubCmd,
            safeViews.btnEpubTextSettingsCmd,
            safeViews.btnOcrEpubCmd,
            safeViews.btnPdfTextSettingsCmd,
            safeViews.btnTranslateImageCmd,
            safeViews.btnImageTextSettingsCmd,
            safeViews.btnOcrImageCmd,
            safeViews.btnGoogleLensImageCmd
        )
        if (BuildConfig.SUPPORT_VR_PLAYER) list.add(safeViews.btn3dVrCmd)
        return list
    }

    /**
     * Return the bar [View] for [cmd], or null for overflow-only commands.
     */
    private fun barViewForCommand(cmd: CommandPanelLayoutPlanner.PlayerCommand): View? {
        return when (cmd) {
            CommandPanelLayoutPlanner.PlayerCommand.RENAME -> safeViews.btnRenameCmd
            CommandPanelLayoutPlanner.PlayerCommand.EDIT -> safeViews.btnEditCmd
            CommandPanelLayoutPlanner.PlayerCommand.SAVE_FRAME -> safeViews.btnSaveFrameCmd
            CommandPanelLayoutPlanner.PlayerCommand.UNDO -> safeViews.btnUndoCmd
            CommandPanelLayoutPlanner.PlayerCommand.CAST -> safeViews.btnCastCmd
            CommandPanelLayoutPlanner.PlayerCommand.LYRICS -> safeViews.btnLyricsCmd
            CommandPanelLayoutPlanner.PlayerCommand.SEARCH_YOUTUBE_MUSIC -> safeViews.btnSearchYoutubeMusicCmd
            CommandPanelLayoutPlanner.PlayerCommand.SEARCH_PDF -> safeViews.btnSearchPdfCmd
            CommandPanelLayoutPlanner.PlayerCommand.TRANSLATE_PDF -> safeViews.btnTranslatePdfCmd
            CommandPanelLayoutPlanner.PlayerCommand.PDF_TEXT_SETTINGS -> safeViews.btnPdfTextSettingsCmd
            CommandPanelLayoutPlanner.PlayerCommand.OCR_PDF -> safeViews.btnOcrPdfCmd
            CommandPanelLayoutPlanner.PlayerCommand.GOOGLE_LENS_PDF -> safeViews.btnGoogleLensPdfCmd
            CommandPanelLayoutPlanner.PlayerCommand.SEARCH_TEXT -> safeViews.btnSearchTextCmd
            CommandPanelLayoutPlanner.PlayerCommand.EDIT_TEXT -> safeViews.btnEditTextCmd
            CommandPanelLayoutPlanner.PlayerCommand.TRANSLATE_TEXT -> safeViews.btnTranslateTextCmd
            CommandPanelLayoutPlanner.PlayerCommand.TEXT_SETTINGS -> safeViews.btnTextSettingsCmd
            CommandPanelLayoutPlanner.PlayerCommand.COPY_TEXT -> safeViews.btnCopyTextCmd
            CommandPanelLayoutPlanner.PlayerCommand.SEARCH_EPUB -> safeViews.btnSearchEpubCmd
            CommandPanelLayoutPlanner.PlayerCommand.TRANSLATE_EPUB -> safeViews.btnTranslateEpubCmd
            CommandPanelLayoutPlanner.PlayerCommand.EPUB_TEXT_SETTINGS -> safeViews.btnEpubTextSettingsCmd
            CommandPanelLayoutPlanner.PlayerCommand.OCR_EPUB -> safeViews.btnOcrEpubCmd
            CommandPanelLayoutPlanner.PlayerCommand.TRANSLATE_IMAGE -> safeViews.btnTranslateImageCmd
            CommandPanelLayoutPlanner.PlayerCommand.IMAGE_TEXT_SETTINGS -> safeViews.btnImageTextSettingsCmd
            CommandPanelLayoutPlanner.PlayerCommand.OCR_IMAGE -> safeViews.btnOcrImageCmd
            CommandPanelLayoutPlanner.PlayerCommand.GOOGLE_LENS_IMAGE -> safeViews.btnGoogleLensImageCmd
            CommandPanelLayoutPlanner.PlayerCommand.PRINT -> safeViews.btnPrintCmd
            CommandPanelLayoutPlanner.PlayerCommand.VR_3D ->
                if (BuildConfig.SUPPORT_VR_PLAYER) safeViews.btn3dVrCmd else null
            else -> null // Overflow-only commands have no bar view
        }
    }

    /**
     * Count how many fixed right-group buttons will be visible for the given [state].
     * Used to calculate the space left for the adaptive center group in portrait mode.
     *
     * Counts: Previous, Random, Next (fixed nav group) + Delete, Fullscreen, Slideshow, Share, Info,
     * Favorite (conditional). Favorite uses [lastKnownFavoriteVisible] because its
     * visibility is resolved asynchronously.
     */
    private fun countVisibleRightGroupButtons(
        state: PlayerViewModel.PlayerState,
        canWrite: Boolean
    ): Int {
        val file = state.currentFile ?: return 2 // at least Previous + Next
        var count = 2 // btnPreviousCmd + btnNextCmd always
        if (shouldShowRandomNavigation(state.resource?.profile)) count++
        if (canWrite && state.allowDelete) count++
        val type = file.type
        if (type == MediaType.IMAGE || type == MediaType.GIF || type == MediaType.VIDEO ||
            type == MediaType.PDF || type == MediaType.TEXT || type == MediaType.EPUB
        ) count++ // btnFullscreenCmd
        if (type == MediaType.IMAGE || type == MediaType.GIF ||
            type == MediaType.VIDEO || type == MediaType.AUDIO
        ) count++ // btnSlideshowCmd
        count++ // btnShareCmd (always visible when panel shown)
        count++ // btnInfoCmd (always visible when panel shown)
        if (lastKnownFavoriteVisible) count++ // btnFavorite (async; use last known state)
        return count
    }

    private fun shouldShowRandomNavigation(profile: ResourceProfile?): Boolean {
        return profile == ResourceProfile.AUDIO_LIBRARY || profile == ResourceProfile.PHOTO_STORAGE
    }

    /**
     * Estimate the pixel width available for the center button group in portrait mode.
     *
     * Uses [binding.topCommandPanel.width] when the view is already laid out (after a
     * [post] call from [updateOrientation]), or falls back to display width otherwise.
     */
    private fun resolveAvailableCenterWidthPx(
        state: PlayerViewModel.PlayerState,
        canWrite: Boolean
    ): Int {
        val dm = binding.root.resources.displayMetrics
        val buttonPx = (40 * dm.density).toInt()
        val panelWidth = if (binding.topCommandPanel.width > 0) {
            binding.topCommandPanel.width
        } else {
            dm.widthPixels
        }
        val leftGroupPx = buttonPx  // btnBack only
        val rightGroupPx = buttonPx * countVisibleRightGroupButtons(state, canWrite)
        return (panelWidth - leftGroupPx - rightGroupPx).coerceAtLeast(0)
    }

    private fun isWifiConnected(context: android.content.Context): Boolean {
        return try {
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }
}
