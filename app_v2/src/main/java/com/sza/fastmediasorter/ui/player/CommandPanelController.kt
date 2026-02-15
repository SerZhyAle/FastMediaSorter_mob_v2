package com.sza.fastmediasorter.ui.player

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
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
    }
    
    private val originalCommandButtonHeights = mutableMapOf<Int, Int>()
    private val originalMargins = mutableMapOf<Int, android.graphics.Rect>()
    private val originalPaddings = mutableMapOf<Int, android.graphics.Rect>()
    private val originalContainerPaddings = mutableMapOf<Int, android.graphics.Rect>()
    private var smallControlsApplied = false
    private val safeViews = PlayerBindingSafeViews(binding)
    
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
        
        // Adaptive layout based on orientation
        // Portrait: Back | Overflow(...) | Delete, Fullscreen | Prev, Next
        // Landscape: All buttons visible
        
        val showInLandscape = state.showCommandPanel && isLandscapeMode
        val showInPortrait = state.showCommandPanel && !isLandscapeMode
        
        // Overflow menu button - visible only in portrait mode
        safeViews.btnOverflowMenu.isVisible = showInPortrait
        
        // Back, Delete, Fullscreen, Previous, Next: always visible in command panel mode
        binding.btnBack.isVisible = state.showCommandPanel
        // Hide delete button if not writable or not allowed
        binding.btnDeleteCmd.isVisible = state.showCommandPanel && canWrite && state.allowDelete
        binding.btnDeleteCmd.isEnabled = canWrite && canRead && state.allowDelete
        binding.btnPreviousCmd.isVisible = state.showCommandPanel
        binding.btnNextCmd.isVisible = state.showCommandPanel
        
        // Fullscreen: visible in both modes
        binding.btnFullscreenCmd.isVisible = state.showCommandPanel && 
            (currentFile.type == MediaType.IMAGE || currentFile.type == MediaType.GIF ||
             currentFile.type == MediaType.VIDEO || 
             currentFile.type == MediaType.PDF || currentFile.type == MediaType.TEXT ||
             currentFile.type == MediaType.EPUB)
        
        // Slideshow: visible in both modes for supported types (including AUDIO for background photos)
        binding.btnSlideshowCmd.isVisible = state.showCommandPanel && 
             (currentFile.type == MediaType.IMAGE || currentFile.type == MediaType.GIF ||
              currentFile.type == MediaType.VIDEO || currentFile.type == MediaType.AUDIO)

        // Common Action Buttons (Visible in both Portrait and Landscape)
        if (state.showCommandPanel) {
            // Check enableFavorites setting (async)
            coroutineScope.launch {
                val settings = settingsRepository.getSettings().first()
                val shouldShowFavorite = settings.enableFavorites || state.resource?.id == -100L
                
                withContext(Dispatchers.Main) {
                    binding.btnFavorite.isVisible = shouldShowFavorite
                    binding.btnFavorite.setImageResource(if (currentFile.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
                }
            }
            
            binding.btnShareCmd.isVisible = true
            binding.btnInfoCmd.isVisible = true
        }
        
        // Center buttons visibility
        if (showInPortrait) {
            // Hide all center buttons in portrait (they are in overflow menu)
            getOverflowableButtons().forEach { it.isVisible = false }
        } else if (showInLandscape) {
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
            // Edit is visible for images (if writable) OR video (always, as it's controls)
            safeViews.btnEditCmd.isVisible = (isImage && canWrite) || isVideo || isPdf || isPdf
            
            // Update button contentDescription based on file type
            if (isVideo) {
                safeViews.btnEditCmd.contentDescription = binding.root.context.getString(R.string.control)
            } else {
                safeViews.btnEditCmd.contentDescription = binding.root.context.getString(R.string.edit)
            }
            
            // Update button text based on file type
            if (isVideo) {
                safeViews.btnEditCmd.contentDescription = binding.root.context.getString(R.string.control)
            } else {
                safeViews.btnEditCmd.contentDescription = binding.root.context.getString(R.string.edit)
            }
            
            // PDF Actions
            safeViews.btnGoogleLensPdfCmd.isVisible = isPdf && isLandscapeMode
            safeViews.btnOcrPdfCmd.isVisible = isPdf && isLandscapeMode
            safeViews.btnTranslatePdfCmd.isVisible = isPdf && isLandscapeMode
            safeViews.btnSearchPdfCmd.isVisible = isPdf && isLandscapeMode
            
            // Text Actions
            safeViews.btnCopyTextCmd.isVisible = isText && isLandscapeMode
            safeViews.btnEditTextCmd.isVisible = isText && isLandscapeMode && canWrite // Edit text requires write
            safeViews.btnTranslateTextCmd.isVisible = isText && isLandscapeMode
            safeViews.btnTextSettingsCmd.isVisible = isText && isLandscapeMode
            safeViews.btnSearchTextCmd.isVisible = isText && isLandscapeMode
            
            // EPUB Actions
            safeViews.btnSearchEpubCmd.isVisible = isEpub && isLandscapeMode
            safeViews.btnTranslateEpubCmd.isVisible = isEpub && isLandscapeMode
            safeViews.btnEpubTextSettingsCmd.isVisible = isEpub && isLandscapeMode
            safeViews.btnOcrEpubCmd.isVisible = isEpub && isLandscapeMode
            
            // PDF Actions
            safeViews.btnPdfTextSettingsCmd.isVisible = isPdf && isLandscapeMode
            
            // Image Actions (for IMAGE/GIF)
            // Show buttons in landscape mode based on file type and settings from state
            if (isImage && isLandscapeMode) {
                // Use settings from state (already loaded synchronously)
                safeViews.btnTranslateImageCmd.isVisible = state.enableTranslation
                safeViews.btnOcrImageCmd.isVisible = state.enableOcr
                safeViews.btnGoogleLensImageCmd.isVisible = state.enableGoogleLens
                safeViews.btnImageTextSettingsCmd.isVisible = true
                Timber.d("CommandPanelController: Image buttons IN LANDSCAPE - translate=${state.enableTranslation}, ocr=${state.enableOcr}, lens=${state.enableGoogleLens}")
                Timber.d("CommandPanelController: btnTranslateImageCmd.isVisible=${safeViews.btnTranslateImageCmd.isVisible}, visibility=${safeViews.btnTranslateImageCmd.visibility}")
            } else if (!isImage) {
                // Hide buttons if not an image
                safeViews.btnTranslateImageCmd.isVisible = false
                safeViews.btnImageTextSettingsCmd.isVisible = false
                safeViews.btnOcrImageCmd.isVisible = false
                safeViews.btnGoogleLensImageCmd.isVisible = false
            } else if (isImage && !isLandscapeMode) {
                Timber.d("CommandPanelController: Image in PORTRAIT mode - buttons should be hidden")
            }
            // In portrait mode, buttons are already hidden by getOverflowableButtons()
        } else {
            // Command panel hidden
            getOverflowableButtons().forEach { it.isVisible = false }
        }
        
        // Enable state for always-enabled buttons
        binding.btnBack.isEnabled = true
        binding.btnPreviousCmd.isEnabled = true
        binding.btnNextCmd.isEnabled = true
        binding.btnSlideshowCmd.isEnabled = true
        
        // Update slideshow button color based on active state
        updateSlideshowButtonColor(state.isSlideShowActive)
        
        // Copy/Move panels visibility based on settings AND whether there are destination buttons
        val hasCopyButtons = safeViews.copyToButtonsGrid.childCount > 0
        val hasMoveButtons = safeViews.moveToButtonsGrid.childCount > 0
        
        val copyPanelVisible = state.showCommandPanel && state.enableCopying && hasCopyButtons
        val movePanelVisible = state.showCommandPanel && state.enableMoving && hasMoveButtons && canWrite
        
        Timber.d("CommandPanelController.updateCommandAvailability: copyPanel=$copyPanelVisible (showCmd=${state.showCommandPanel}, enableCopy=${state.enableCopying}, hasCopy=$hasCopyButtons, childCount=${safeViews.copyToButtonsGrid.childCount})")
        Timber.d("CommandPanelController.updateCommandAvailability: movePanel=$movePanelVisible (showCmd=${state.showCommandPanel}, enableMove=${state.enableMoving}, hasMove=$hasMoveButtons, canWrite=$canWrite, childCount=${safeViews.moveToButtonsGrid.childCount})")
        
        safeViews.copyToPanel.isVisible = copyPanelVisible
        safeViews.moveToPanel.isVisible = movePanelVisible
        
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
        
        // DEBUG: Log actual panel state after visibility change
        safeViews.copyToPanel.post {
            val location = IntArray(2)
            safeViews.copyToPanel.getLocationOnScreen(location)
            Timber.d("CommandPanelController: ACTUAL copyToPanel state - visibility=${safeViews.copyToPanel.visibility}, isVisible=${safeViews.copyToPanel.isVisible}, width=${safeViews.copyToPanel.width}, height=${safeViews.copyToPanel.height}, Y=${location[1]}, parent=${safeViews.copyToPanel.parent?.javaClass?.simpleName}, background=${safeViews.copyToPanel.background}")
        }
        safeViews.moveToPanel.post {
            val location = IntArray(2)
            safeViews.moveToPanel.getLocationOnScreen(location)
            Timber.d("CommandPanelController: ACTUAL moveToPanel state - visibility=${safeViews.moveToPanel.visibility}, isVisible=${safeViews.moveToPanel.isVisible}, width=${safeViews.moveToPanel.width}, height=${safeViews.moveToPanel.height}, Y=${location[1]}, parent=${safeViews.moveToPanel.parent?.javaClass?.simpleName}, background=${safeViews.moveToPanel.background}")
        }
        

    }
    
    /**
     * Update slideshow button visual state (color/alpha) based on active state
     */
    fun updateSlideshowButtonColor(isActive: Boolean) {
        Timber.d("CommandPanelController.updateSlideshowButtonColor: isActive=$isActive, btn=${binding.btnSlideshowCmd}")
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
        safeViews.btnLyricsCmd
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
        
        // Update cached state with new orientation if we have state
        cachedState?.let { state ->
            updateCommandAvailability(state)
        }
    }
    
    /**
     * Show overflow popup menu
     */
    @SuppressLint("RestrictedApi")
    private fun showOverflowMenu(anchor: View) {
        val state = cachedState ?: return
        val currentFile = state.currentFile ?: return
        val context = binding.root.context
        val isReadOnly = state.resource?.isReadOnly == true
        
        val popup = PopupMenu(context, anchor)
        popup.menuInflater.inflate(R.menu.overflow_menu_player, popup.menu)
        
        popup.setForceShowIcon(true)
        
        // Apply dark tint to white icons for popup menu visibility
        val iconColor = android.graphics.Color.DKGRAY
        popup.menu.findItem(R.id.menu_google_lens)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_rename)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_edit)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_translate)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_ocr)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_search)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_text_settings)?.icon?.setTint(iconColor)
        // popup.menu.findItem(R.id.menu_info)?.icon?.setTint(iconColor) // Removed from menu
        popup.menu.findItem(R.id.menu_lyrics)?.icon?.setTint(iconColor)

        // popup.menu.findItem(R.id.menu_favorite)?.icon?.setTint(iconColor) // Removed from menu
        popup.menu.findItem(R.id.menu_copy_text)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_edit_text)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_undo)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_sleep_timer)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_reopen_encoding)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_toggle_markdown)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_reader_settings)?.icon?.setTint(iconColor)
        popup.menu.findItem(R.id.menu_read_aloud)?.icon?.setTint(iconColor)
        
        // Configure menu items visibility based on file type and state
        val isPdf = currentFile.type == MediaType.PDF
        val isText = currentFile.type == MediaType.TEXT
        val isEpub = currentFile.type == MediaType.EPUB
        val isImage = currentFile.type == MediaType.IMAGE || currentFile.type == MediaType.GIF
        val isVideo = currentFile.type == MediaType.VIDEO || currentFile.type == MediaType.AUDIO
        
        // Show/hide menu items
        popup.menu.findItem(R.id.menu_rename)?.isVisible = state.allowRename && !isReadOnly
        // popup.menu.findItem(R.id.menu_share)?.isVisible = true // Removed
        // popup.menu.findItem(R.id.menu_info)?.isVisible = true // Removed
        popup.menu.findItem(R.id.menu_lyrics)?.isVisible = currentFile.type == MediaType.AUDIO
        popup.menu.findItem(R.id.menu_edit)?.apply {
            isVisible = (isImage && !isReadOnly) || isVideo || isPdf || isPdf
            // Update title based on file type
            title = if (isVideo) {
                context.getString(R.string.control)
            } else {
                context.getString(R.string.edit)
            }
        }

        // popup.menu.findItem(R.id.menu_favorite) block removed
        popup.menu.findItem(R.id.menu_search)?.isVisible = isPdf || isText || isEpub
        popup.menu.findItem(R.id.menu_translate)?.isVisible = (isPdf || isText || isEpub || isImage) && state.enableTranslation
        popup.menu.findItem(R.id.menu_text_settings)?.isVisible = true // Always visible
        popup.menu.findItem(R.id.menu_ocr)?.isVisible = (isPdf || isImage || isEpub) && state.enableOcr
        popup.menu.findItem(R.id.menu_google_lens)?.isVisible = (isPdf || isImage) && state.enableGoogleLens
        popup.menu.findItem(R.id.menu_copy_text)?.isVisible = isText
        popup.menu.findItem(R.id.menu_edit_text)?.isVisible = isText && !isReadOnly
        popup.menu.findItem(R.id.menu_undo)?.isVisible = state.lastOperation != null && !isReadOnly
        popup.menu.findItem(R.id.menu_sleep_timer)?.isVisible = currentFile.type == MediaType.AUDIO || currentFile.type == MediaType.VIDEO
        popup.menu.findItem(R.id.menu_reopen_encoding)?.isVisible = isText
        popup.menu.findItem(R.id.menu_toggle_markdown)?.isVisible = isText && currentFile.name.endsWith(".md", ignoreCase = true)
        popup.menu.findItem(R.id.menu_reader_settings)?.isVisible = isText
        popup.menu.findItem(R.id.menu_read_aloud)?.isVisible = isText
        popup.menu.findItem(R.id.menu_pdf_scroll_mode)?.isVisible = isPdf
        popup.menu.findItem(R.id.menu_pdf_color_mode)?.isVisible = isPdf
        popup.menu.findItem(R.id.menu_pdf_thumbnails)?.isVisible = isPdf
        popup.menu.findItem(R.id.menu_epub_reader_settings)?.isVisible = isEpub
        popup.menu.findItem(R.id.menu_epub_search_all)?.isVisible = isEpub
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_rename -> callback.onRenameClicked()
                // R.id.menu_share -> callback.onShareClicked() // Removed
                // R.id.menu_info -> callback.onInfoClicked() // Removed
                R.id.menu_lyrics -> callback.onLyricsClicked()
                R.id.menu_edit -> callback.onEditClicked()
                // R.id.menu_favorite -> callback.onFavoriteClicked() // Removed
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
            }
            true
        }

        // Long click shortcut for settings dialogs
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
                    R.id.menu_ocr -> {
                        callback.onOcrSettingsClicked()
                        popup.dismiss()
                        true
                    }
                    R.id.menu_translate -> {
                        callback.onTranslationSettingsClicked()
                        popup.dismiss()
                        true
                    }
                    else -> false
                }
            }
        } catch (e: Exception) {
            Timber.w("Failed to set long click listener for menu: ${e.message}")
        }
        
        // Update translation button icon with language pair (async, then show popup)
        coroutineScope.launch {
            try {
                val settings = settingsRepository.getSettings().first()
                val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
                val targetLang = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
                val translationDrawable = LanguageBadgeDrawable(context, sourceLang, targetLang, android.graphics.Color.DKGRAY)
                
                // Update icon and show popup on Main thread
                withContext(Dispatchers.Main) {
                    popup.menu.findItem(R.id.menu_translate)?.icon = translationDrawable
                    popup.show()
                }
            } catch (e: Exception) {
                // Show popup anyway
                withContext(Dispatchers.Main) {
                    popup.show()
                }
            }
        }
    }
    
    /**
     * Get buttons that should be hidden in portrait mode (shown in overflow menu)
     */
    private fun getOverflowableButtons(): List<View> = listOf(
        safeViews.btnRenameCmd,
        safeViews.btnLyricsCmd,
        safeViews.btnEditCmd,
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
    
    /**
     * Get buttons that should always be visible (not in overflow)
     */
    private fun getAlwaysVisibleButtons(): List<View> = listOf(
        binding.btnBack,
        binding.btnDeleteCmd,
        binding.btnFullscreenCmd,
        binding.btnPreviousCmd,
        binding.btnNextCmd
    )
}
