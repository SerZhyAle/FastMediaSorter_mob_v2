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
    }
    
    private val originalCommandButtonHeights = mutableMapOf<Int, Int>()
    private val originalMargins = mutableMapOf<Int, android.graphics.Rect>()
    private val originalPaddings = mutableMapOf<Int, android.graphics.Rect>()
    private val originalContainerPaddings = mutableMapOf<Int, android.graphics.Rect>()
    private var smallControlsApplied = false
    
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
        binding.btnOverflowMenu.setOnClickListener { view ->
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

        binding.btnRenameCmd.setOnClickListener {
            callback.onRenameClicked()
        }

        binding.btnDeleteCmd.setOnClickListener {
            callback.onDeleteClicked()
        }
        
        binding.btnShareCmd.setOnClickListener {
            callback.onShareClicked()
        }
        
        binding.btnEditCmd.setOnClickListener {
            callback.onEditClicked()
        }
        
        binding.btnUndoCmd.setOnClickListener {
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
        binding.copyToPanelHeader.apply {
            setOnClickListener { _ ->
                Timber.d("CommandPanelController: copyToPanelHeader clicked - toggling Copy panel")
                callback.onCopyPanelHeaderClicked()
            }
            // Prevent click propagation to underlying PlayerView
            isClickable = true
            isFocusable = true
        }
        
        // Setup collapsible Move to panel
        binding.moveToPanelHeader.apply {
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
        binding.btnOverflowMenu.isVisible = showInPortrait
        
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
            binding.btnRenameCmd.isEnabled = canWrite && canRead && state.allowRename
            // Hide rename if not writable or not allowed
            binding.btnRenameCmd.isVisible = canWrite && state.allowRename
            
            binding.btnUndoCmd.isVisible = state.lastOperation != null && canWrite
            binding.btnLyricsCmd.isVisible = isVideo && currentFile.type == MediaType.AUDIO
            // Edit is visible for images (if writable) OR video (always, as it's controls)
            binding.btnEditCmd.isVisible = (isImage && canWrite) || isVideo || isPdf || isPdf
            
            // Update button contentDescription based on file type
            if (isVideo) {
                binding.btnEditCmd.contentDescription = binding.root.context.getString(R.string.control)
            } else {
                binding.btnEditCmd.contentDescription = binding.root.context.getString(R.string.edit)
            }
            
            // Update button text based on file type
            if (isVideo) {
                binding.btnEditCmd.contentDescription = binding.root.context.getString(R.string.control)
            } else {
                binding.btnEditCmd.contentDescription = binding.root.context.getString(R.string.edit)
            }
            
            // PDF Actions
            binding.btnGoogleLensPdfCmd.isVisible = isPdf && isLandscapeMode
            binding.btnOcrPdfCmd.isVisible = isPdf && isLandscapeMode
            binding.btnTranslatePdfCmd.isVisible = isPdf && isLandscapeMode
            binding.btnSearchPdfCmd.isVisible = isPdf && isLandscapeMode
            
            // Text Actions
            binding.btnCopyTextCmd.isVisible = isText && isLandscapeMode
            binding.btnEditTextCmd.isVisible = isText && isLandscapeMode && canWrite // Edit text requires write
            binding.btnTranslateTextCmd.isVisible = isText && isLandscapeMode
            binding.btnTextSettingsCmd.isVisible = isText && isLandscapeMode
            binding.btnSearchTextCmd.isVisible = isText && isLandscapeMode
            
            // EPUB Actions
            binding.btnSearchEpubCmd.isVisible = isEpub && isLandscapeMode
            binding.btnTranslateEpubCmd.isVisible = isEpub && isLandscapeMode
            binding.btnEpubTextSettingsCmd.isVisible = isEpub && isLandscapeMode
            binding.btnOcrEpubCmd.isVisible = isEpub && isLandscapeMode
            
            // PDF Actions
            binding.btnPdfTextSettingsCmd.isVisible = isPdf && isLandscapeMode
            
            // Image Actions (for IMAGE/GIF)
            // Show buttons in landscape mode based on file type and settings from state
            if (isImage && isLandscapeMode) {
                // Use settings from state (already loaded synchronously)
                binding.btnTranslateImageCmd.isVisible = state.enableTranslation
                binding.btnOcrImageCmd.isVisible = state.enableOcr
                binding.btnGoogleLensImageCmd.isVisible = state.enableGoogleLens
                binding.btnImageTextSettingsCmd.isVisible = true
                Timber.d("CommandPanelController: Image buttons IN LANDSCAPE - translate=${state.enableTranslation}, ocr=${state.enableOcr}, lens=${state.enableGoogleLens}")
                Timber.d("CommandPanelController: btnTranslateImageCmd.isVisible=${binding.btnTranslateImageCmd.isVisible}, visibility=${binding.btnTranslateImageCmd.visibility}")
            } else if (!isImage) {
                // Hide buttons if not an image
                binding.btnTranslateImageCmd.isVisible = false
                binding.btnImageTextSettingsCmd.isVisible = false
                binding.btnOcrImageCmd.isVisible = false
                binding.btnGoogleLensImageCmd.isVisible = false
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
        val hasCopyButtons = (binding.copyToButtonsGrid as? android.view.ViewGroup)?.childCount ?: 0 > 0
        val hasMoveButtons = (binding.moveToButtonsGrid as? android.view.ViewGroup)?.childCount ?: 0 > 0
        
        Timber.d("CommandPanelController: hasCopyButtons=$hasCopyButtons (childCount=${binding.copyToButtonsGrid.childCount}), hasMoveButtons=$hasMoveButtons (childCount=${binding.moveToButtonsGrid.childCount})")
        
        binding.copyToPanel.isVisible = state.showCommandPanel && state.enableCopying && hasCopyButtons
        binding.moveToPanel.isVisible = state.showCommandPanel && state.enableMoving && hasMoveButtons && canWrite // Move requires write
        

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
            binding.copyToPanel,
            binding.moveToPanel,
            binding.copyToButtonsGrid,
            binding.moveToButtonsGrid
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
            binding.copyToPanel,
            binding.moveToPanel,
            binding.copyToButtonsGrid,
            binding.moveToButtonsGrid
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
        binding.btnRenameCmd,
        binding.btnDeleteCmd,
        binding.btnShareCmd,
        binding.btnInfoCmd,
        binding.btnEditCmd,
        binding.btnUndoCmd,
        binding.btnFullscreenCmd,
        binding.btnSlideshowCmd,
        binding.btnFavorite,
        // Overflow menu
        binding.btnOverflowMenu,
        // Text commands
        binding.btnSearchTextCmd,
        binding.btnTranslateTextCmd,
        binding.btnTextSettingsCmd,
        binding.btnCopyTextCmd,
        binding.btnEditTextCmd,
        // PDF commands
        binding.btnSearchPdfCmd,
        binding.btnTranslatePdfCmd,
        binding.btnPdfTextSettingsCmd,
        binding.btnOcrPdfCmd,
        binding.btnGoogleLensPdfCmd,
        // EPUB commands
        binding.btnSearchEpubCmd,
        binding.btnTranslateEpubCmd,
        binding.btnEpubTextSettingsCmd,
        binding.btnOcrEpubCmd,
        // Image commands
        binding.btnTranslateImageCmd,
        binding.btnImageTextSettingsCmd,
        binding.btnOcrImageCmd,
        binding.btnGoogleLensImageCmd,
        // Audio commands
        binding.btnLyricsCmd
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
        
        // Force show icons in popup menu
        try {
            val menuHelper = popup.javaClass.getDeclaredField("mPopup")
            menuHelper.isAccessible = true
            val helper = menuHelper.get(popup)
            helper.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                .invoke(helper, true)
        } catch (e: Exception) {
            Timber.w("Failed to force show menu icons: ${e.message}")
        }
        
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
            }
            true
        }
        
        // Long click handler for settings dialogs
        try {
            val listView = (popup.menu as? androidx.appcompat.view.menu.MenuBuilder)?.let { _ ->
                popup.javaClass.getDeclaredField("mPopup")
                    .apply { isAccessible = true }
                    .get(popup)
                    ?.javaClass
                    ?.getDeclaredMethod("getListView")
                    ?.invoke(popup.javaClass.getDeclaredField("mPopup")
                        .apply { isAccessible = true }
                        .get(popup)) as? android.widget.ListView
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
        binding.btnRenameCmd,
        binding.btnLyricsCmd,
        binding.btnEditCmd,

        binding.btnUndoCmd,
        binding.btnGoogleLensPdfCmd,
        binding.btnOcrPdfCmd,
        binding.btnTranslatePdfCmd,
        binding.btnSearchPdfCmd,
        binding.btnSearchTextCmd,
        binding.btnEditTextCmd,
        binding.btnTranslateTextCmd,
        binding.btnTextSettingsCmd,
        binding.btnCopyTextCmd,
        binding.btnSearchEpubCmd,
        binding.btnTranslateEpubCmd,
        binding.btnEpubTextSettingsCmd,
        binding.btnOcrEpubCmd,
        binding.btnPdfTextSettingsCmd,
        binding.btnTranslateImageCmd,
        binding.btnImageTextSettingsCmd,
        binding.btnOcrImageCmd,
        binding.btnGoogleLensImageCmd
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
