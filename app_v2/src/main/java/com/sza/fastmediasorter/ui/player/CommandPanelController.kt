package com.sza.fastmediasorter.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.compat.MultiWindowCapabilityDetector
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.core.cast.CastController
import com.sza.fastmediasorter.ui.player.helpers.CommandPanelLayoutPlanner
import com.sza.fastmediasorter.ui.player.helpers.LanguageBadgeDrawable
import com.sza.fastmediasorter.ui.player.helpers.PlayerBigButtonsModeManager
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

// S0238: VR-entry button visibility - open for video and pixel-media (image, gif).
// Audio / docs / text / pdf / epub do not benefit from VR.
private val VR_BUTTON_MEDIA_TYPES = setOf(MediaType.VIDEO, MediaType.IMAGE, MediaType.GIF)

/** PlayerActivity command panel: button setup, availability/state updates, small-controls layout, original-height tracking, landscape/portrait adaptation. */
class CommandPanelController(
    private val binding: ActivityPlayerUnifiedBinding,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val callback: CommandPanelCallback,
    private val mediaCapabilities: MediaCapabilities,
    private val bigButtonsMode: Boolean = false,
    private val allowVrLaunch: () -> Boolean = { false },
) {

    interface CommandPanelCallback {
        fun onBackClicked()
        fun onPreviousClicked()
        fun onRandomClicked()
        fun onNextClicked()
        fun onRenameClicked()
        fun onDeleteClicked()
        fun onSendToClicked() // S0459: unified «Send to..» menu - bar press / big-buttons overflow → bottom sheet
        /**
         * S0459 ADR-2: build the «Send to..» receivers as a native nested submenu in the overflow
         * PopupMenu, at [order] (the command's priority). No-op when there is no current file.
         */
        fun onSendToOverflowSubMenuRequested(menu: android.view.Menu, order: Int)
        fun onEditClicked()
        fun onUndoClicked()
        fun onFullscreenClicked()
        fun onSlideshowClicked()
        fun onCopyPanelExpandedChanged(expanded: Boolean)
        fun onMovePanelExpandedChanged(expanded: Boolean)
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
        fun onBlackScreenClicked()
        fun onOpenInVrClicked()
        fun onOpenInSeparateWindowClicked()
        fun onCropClicked()
        fun onCropToFileClicked()
        fun onCompressCopyClicked()
        fun onDrawOverlayClicked()
        fun onRotationToggleClicked()
        // S0995: manual 90° visual frame rotation (image/video); distinct from the screen sensor toggle.
        fun onRotateContent90Clicked()

        // S1364: the counter-clockwise twin of onRotateContent90Clicked.
        fun onRotateContentCounter90Clicked()
    }

    private val originalCommandButtonHeights = mutableMapOf<Int, Int>()
    private val originalMargins = mutableMapOf<Int, android.graphics.Rect>()
    private val originalPaddings = mutableMapOf<Int, android.graphics.Rect>()
    private val originalContainerPaddings = mutableMapOf<Int, android.graphics.Rect>()
    private var smallControlsApplied = false
    private val safeViews = PlayerBindingSafeViews(binding)
    private val bigButtonsModeManager = PlayerBigButtonsModeManager(binding.root.context)

    // Adaptive portrait layout
    private val planner = CommandPanelLayoutPlanner(mediaCapabilities)
    private var latestOverflowCommands: List<CommandPanelLayoutPlanner.PlayerCommand> = emptyList()
    private var latestBigButtonsBarCommands: List<CommandPanelLayoutPlanner.PlayerCommand> = emptyList()
    private var lastKnownFavoriteVisible = true
    // S0028: cached from settings (separate-window allow flag)
    private var lastKnownAllowSeparateWindow: Boolean = false

    // Cached state for overflow menu visibility
    private var cachedState: PlayerViewModel.PlayerState? = null
    private var isLandscapeMode = true

    companion object {
        private const val SMALL_CONTROLS_SCALE = 0.5f

        // S1364: the owner's editing set - every command whose result is a changed image. RENAME and
        // UNDO are deliberately absent: undo restores a deleted file rather than editing the current
        // one, so grouping it here would misdescribe it.
        private val EDIT_SUBMENU_COMMANDS = setOf(
            CommandPanelLayoutPlanner.PlayerCommand.EDIT,
            CommandPanelLayoutPlanner.PlayerCommand.CROP,
            CommandPanelLayoutPlanner.PlayerCommand.CROP_TO_FILE,
            CommandPanelLayoutPlanner.PlayerCommand.DRAW_OVERLAY,
            CommandPanelLayoutPlanner.PlayerCommand.ROTATE_CONTENT,
            CommandPanelLayoutPlanner.PlayerCommand.ROTATE_CONTENT_CCW,
            CommandPanelLayoutPlanner.PlayerCommand.COMPRESS_COPY,
        )
    }

    private lateinit var castMediaManager: CastController

    fun bindCastManager(manager: CastController) {
        castMediaManager = manager
        coroutineScope.launch {
            manager.castAvailableState.collect {
                cachedState?.let { state -> updateCommandAvailability(state) }
            }
        }
    }

    /** Setup all command panel button click listeners */
    fun setupCommandPanelControls() {
        binding.btnBack.setOnClickListener {
            callback.onBackClicked()
        }

        // Overflow menu button for portrait mode
        safeViews.btnOverflowMenu.setOnClickListener { view ->
            showOverflowMenu(view)
        }

        binding.btnPreviousCmd.setOnClickListener {
            callback.onPreviousClicked()
        }

        binding.btnRandomCmd.setOnClickListener {
            callback.onRandomClicked()
        }

        binding.btnNextCmd.setOnClickListener {
            callback.onNextClicked()
        }

        safeViews.btnRenameCmd.setOnClickListener {
            callback.onRenameClicked()
        }

        binding.btnDeleteCmd.setOnClickListener {
            callback.onDeleteClicked()
        }

        safeViews.btnEditCmd.setOnClickListener {
            callback.onEditClicked()
        }

        safeViews.btnSaveFrameCmd.setOnClickListener {
            callback.onSaveFrameClicked()
        }

        safeViews.btnPrintCmd.setOnClickListener {
            callback.onPrintClicked()
        }

        // S0217: inline click listeners for image-edit commands (parity with overflow menu)
        safeViews.btnOpenInSeparateWindowCmd.setOnClickListener {
            callback.onOpenInSeparateWindowClicked()
        }
        safeViews.btnCropCmd.setOnClickListener {
            callback.onCropClicked()
        }
        safeViews.btnCropToFileCmd.setOnClickListener {
            callback.onCropToFileClicked()
        }
        safeViews.btnCompressCopyCmd.setOnClickListener {
            callback.onCompressCopyClicked()
        }
        safeViews.btnDrawOverlayCmd.setOnClickListener {
            callback.onDrawOverlayClicked()
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

        binding.btnSleepTimerCmd.setOnClickListener {
            callback.onSleepTimerClicked()
        }

        binding.btnFavorite.setOnClickListener {
            callback.onFavoriteClicked()
        }

        binding.btnInfoCmd.setOnClickListener {
            callback.onInfoClicked()
        }

        // Setup collapsible Copy to panel
        safeViews.copyToPanelHeader.apply {
            setExpanded(false, notify = false)
            setOnExpandedChangeListener { expanded ->
                callback.onCopyPanelExpandedChanged(expanded)
            }
        }

        // Setup collapsible Move to panel
        safeViews.moveToPanelHeader.apply {
            setExpanded(false, notify = false)
            setOnExpandedChangeListener { expanded ->
                callback.onMovePanelExpandedChanged(expanded)
            }
        }

        // S0162: Rotation toggle
        safeViews.btnRotationToggleCmd.setOnClickListener {
            callback.onRotationToggleClicked()
        }

    }

    /** S0293: re-run command availability after the host Activity entered or left a multi-window / desktop-mode container. The OR-composition inside [updateCommandAvailability] reads the runtime capability flag on every pass, so calling this from [Activity.onMultiWindowModeChanged] (and [Activity.onConfigurationChanged]) brings inline buttons into sync without a recreate. Safe before the first state arrives (`cachedState == null`): no-op. */
    fun notifyMultiWindowModeChanged() {
        val state = cachedState ?: return
        updateCommandAvailability(state)
    }

    private val availabilityUpdater: CommandPanelAvailabilityUpdater by lazy {
        CommandPanelAvailabilityUpdater(
            binding = binding,
            safeViews = safeViews,
            planner = planner,
            mediaCapabilities = mediaCapabilities,
            bigButtonsModeManager = bigButtonsModeManager,
            settingsRepository = settingsRepository,
            coroutineScope = coroutineScope,
            bigButtonsMode = bigButtonsMode,
            getIsLandscapeMode = { isLandscapeMode },
            getCastMediaManager = { if (::castMediaManager.isInitialized) castMediaManager else null },
            getAllowVrLaunch = { allowVrLaunch() },
            shouldShowRandomNavigation = ::shouldShowRandomNavigation,
            isWifiConnected = ::isWifiConnected,
            getOverflowableButtons = ::getOverflowableButtons,
            barViewForCommand = ::barViewForCommand,
            resolveAvailableCenterWidthPx = ::resolveAvailableCenterWidthPx,
            resolveBigButtonsTopPanelSlotCount = ::resolveBigButtonsTopPanelSlotCount,
            bigButtonsFixedButtons = ::bigButtonsFixedButtons,
            updateBigButtonsTopPanelContentDescriptions = ::updateBigButtonsTopPanelContentDescriptions,
            updateSlideshowButtonColor = ::updateSlideshowButtonColor,
            syncBigButtonsTopPanelLayout = ::syncBigButtonsTopPanelLayout,
            logPanelGeometrySnapshot = ::logPanelGeometrySnapshot,
            onCachedStateChange = { cachedState = it },
            getLastKnownFavoriteVisible = { lastKnownFavoriteVisible },
            setLastKnownFavoriteVisible = { lastKnownFavoriteVisible = it },
            getLastKnownAllowSeparateWindow = { lastKnownAllowSeparateWindow },
            setLastKnownAllowSeparateWindow = { lastKnownAllowSeparateWindow = it },
            setLatestBigButtonsBarCommands = { latestBigButtonsBarCommands = it },
            setLatestOverflowCommands = { latestOverflowCommands = it },
            reTriggerUpdate = { state -> updateCommandAvailability(state) },
        )
    }

    /** Update command availability based on state */
    fun updateCommandAvailability(state: PlayerViewModel.PlayerState) {
        availabilityUpdater.update(state)
    }

    /**
     * S0532: whether the MOVE operation is actually usable right now, matching the exact criterion
     * that gates [CommandPanelAvailabilityUpdater] move-panel visibility (move enabled + populated
     * destinations + write permission). The keyboard MOVE guard consults this so the shortcut cannot
     * expand a panel header that visibility logic keeps hidden on a non-writable resource.
     */
    fun isMoveAvailable(): Boolean {
        val state = cachedState ?: return false
        val currentFile = state.currentFile ?: return false
        if (!state.enableMoving) return false
        if (safeViews.moveToButtonsGrid.childCount == 0) return false
        return resolvePlayerFilePermissions(binding.root.context, state.resource, currentFile.path).canWrite
    }

    /**
     * S0533: whether the COPY operation is usable right now, matching the criterion that gates
     * [CommandPanelAvailabilityUpdater] copy-panel visibility (copy enabled + populated destinations).
     * Copy reads the source, so this intentionally omits the write-permission check used by [isMoveAvailable].
     */
    fun isCopyAvailable(): Boolean {
        val state = cachedState ?: return false
        if (state.currentFile == null) return false
        if (!state.enableCopying) return false
        return safeViews.copyToButtonsGrid.childCount > 0
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

    }

    /** Update slideshow button visual state (color/alpha) based on active state */
    fun updateSlideshowButtonColor(isActive: Boolean) {
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

    /** Apply small controls layout (50% height, margins, and paddings) if not already applied */
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
                params.height = (baseline * SMALL_CONTROLS_SCALE).roundToInt().coerceAtLeast(1)

                if (params is android.view.ViewGroup.MarginLayoutParams) {
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

    /** Restore original button heights, margins, and paddings if small controls were applied */
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

        // Post so the layout has settled to the new dimensions before the planner measures width
        cachedState?.let { state ->
            binding.topCommandPanel.post { updateCommandAvailability(state) }
        }
    }

    /** Show the overflow popup menu, ordered by command priority (highest priority first). Items are built programmatically from [latestOverflowCommands] so the display order reflects the runtime priority model, not the static XML declaration order. */
    @SuppressLint("RestrictedApi")
    private fun showOverflowMenu(anchor: View) {
        val state = cachedState ?: return
        val currentFile = state.currentFile ?: return
        val context = binding.root.context

        val commands = latestOverflowCommands
        if (commands.isEmpty()) return

        // S0158: Big Buttons Mode uses a custom ListPopupWindow with doubled row height
        if (bigButtonsMode) {
            bigButtonsModeManager.buildBigButtonsOverflowMenu(anchor, commands, bigButtonsMode = true) { cmd ->
                handleOverflowCommand(cmd)
            }
            return
        }

        val popup = PopupMenu(context, anchor)
        popup.setForceShowIcon(true)

        val iconColor = android.graphics.Color.DKGRAY
        Timber.d("S1365: overflow menu opened for type=${currentFile.type}, ${commands.size} commands")

        // S1364: count the section's members before creating it - Android does not hide an empty
        // submenu, and on a video or text file none of these commands is emitted at all. Same
        // count-then-guard order as SendToMenuManager.buildOverflowSubMenu().
        val editCommands = commands.filter { it in EDIT_SUBMENU_COMMANDS }
        Timber.d("S1364: editing section members=${editCommands.size}, autorotate=${state.playerRotationSensorEnabled}")
        val editSubMenu = if (editCommands.isEmpty()) {
            null
        } else {
            popup.menu.addSubMenu(
                android.view.Menu.NONE,
                android.view.Menu.NONE,
                editCommands.minOf { it.priority },
                context.getString(R.string.menu_edit_submenu_title)
            ).apply { clearHeader() }
        }

        // Build menu dynamically in priority order
        for (cmd in commands) {
            // S0459 ADR-2: «Send to..» renders as a native nested submenu here (not a flat item that
            // opens a sheet); the callback fills it from the gated receiver list at this priority.
            if (cmd.menuItemId == R.id.menu_send_to) {
                callback.onSendToOverflowSubMenuRequested(popup.menu, cmd.priority)
                continue
            }
            val title = if (cmd == CommandPanelLayoutPlanner.PlayerCommand.EDIT) {
                context.getString(
                    CommandPanelLayoutPlanner.PlayerCommand.editTitleResFor(currentFile.type)
                )
            } else {
                context.getString(cmd.titleResId)
            }
            val targetMenu: android.view.Menu =
                editSubMenu?.takeIf { cmd in EDIT_SUBMENU_COMMANDS } ?: popup.menu
            val item = targetMenu.add(android.view.Menu.NONE, cmd.menuItemId, cmd.priority, title)
            // Dynamic favorite icon reflects current file state
            val iconRes = if (cmd == CommandPanelLayoutPlanner.PlayerCommand.FAVORITE && currentFile.isFavorite) {
                R.drawable.ic_star_filled
            } else {
                cmd.iconResId
            }
            if (iconRes != 0) {
                val drawable = androidx.core.content.ContextCompat.getDrawable(context, iconRes)
                drawable?.setTint(iconColor)
                item.icon = drawable
            }
            // Random is only enabled when there are multiple files to navigate
            if (cmd == CommandPanelLayoutPlanner.PlayerCommand.RANDOM) {
                item.isEnabled = (cachedState?.files?.size ?: 0) > 1
            }
            // S0995: a11y description distinct from the short title (states the direction/magnitude).
            if (cmd == CommandPanelLayoutPlanner.PlayerCommand.ROTATE_CONTENT) {
                androidx.core.view.MenuItemCompat.setContentDescription(
                    item, context.getString(R.string.rotate_content_90_desc)
                )
            }
            if (cmd == CommandPanelLayoutPlanner.PlayerCommand.ROTATE_CONTENT_CCW) {
                androidx.core.view.MenuItemCompat.setContentDescription(
                    item,
                    context.getString(R.string.rotate_content_ccw_desc)
                )
            }
            // S1364: playerRotationSensorEnabled is the on/off value; showRotationToggle only decides
            // whether this item exists at all, so checking against it would always render as enabled.
            if (cmd == CommandPanelLayoutPlanner.PlayerCommand.ROTATION_TOGGLE) {
                item.isCheckable = true
                item.isChecked = state.playerRotationSensorEnabled
            }
        }

        // S0459: tint every submenu header icon to match the other (runtime-tinted) items.
        // S1364: this popup now carries two sections - «Send to..», built by the callback, and the
        // editing group built above - so the loop must keep handling any number, not just one.
        for (i in 0 until popup.menu.size()) {
            val mi = popup.menu.getItem(i)
            if (mi.hasSubMenu()) mi.icon?.let { it.setTint(iconColor); mi.icon = it }
        }

        popup.setOnMenuItemClickListener { menuItem ->
            val cmd = CommandPanelLayoutPlanner.PlayerCommand.entries.find { it.menuItemId == menuItem.itemId }
            if (cmd != null) handleOverflowCommand(cmd)
            true
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

    /** S0158: Dispatch overflow menu command from both PopupMenu and big-buttons ListPopupWindow paths. */
    private fun handleOverflowCommand(cmd: CommandPanelLayoutPlanner.PlayerCommand) {
        when (cmd.menuItemId) {
            R.id.menu_delete -> callback.onDeleteClicked()
            R.id.menu_favorite -> callback.onFavoriteClicked()
            R.id.menu_send_to -> callback.onSendToClicked()
            R.id.menu_info -> callback.onInfoClicked()
            R.id.menu_fullscreen -> callback.onFullscreenClicked()
            R.id.menu_slideshow -> callback.onSlideshowClicked()
            R.id.menu_random -> callback.onRandomClicked()
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
            R.id.menu_open_in_vr -> callback.onOpenInVrClicked()
            R.id.menu_save_frame -> callback.onSaveFrameClicked()
            R.id.menu_black_screen -> callback.onBlackScreenClicked()
            R.id.menu_open_in_separate_window -> callback.onOpenInSeparateWindowClicked()
            R.id.menu_crop -> callback.onCropClicked()
            R.id.menu_crop_to_file -> callback.onCropToFileClicked()
            R.id.menu_compress_copy -> callback.onCompressCopyClicked()
            R.id.menu_draw_overlay -> callback.onDrawOverlayClicked()
            R.id.menu_rotation_toggle -> callback.onRotationToggleClicked()
            R.id.menu_rotate_content -> callback.onRotateContent90Clicked()
            R.id.menu_rotate_content_ccw -> callback.onRotateContentCounter90Clicked()
        }
    }

    /** All adaptive center buttons that may move between bar and overflow. These are hidden at the start of every portrait-branch pass and then selectively shown by the planner result. */
    private fun getOverflowableButtons(): List<View> {
        val list = mutableListOf<View>(
            // Group 1: high-priority adaptive buttons
            binding.btnDeleteCmd,
            binding.btnFavorite,
            binding.btnShareCmd,
            binding.btnInfoCmd,
            binding.btnFullscreenCmd,
            binding.btnRandomCmd,
            // Group 2+
            binding.btnBlackScreenCmd,
            safeViews.btnRenameCmd,
            safeViews.btnLyricsCmd,
            binding.btnSleepTimerCmd,
            safeViews.btnSearchYoutubeMusicCmd,
            safeViews.btnRotationToggleCmd,
            safeViews.btnCastCmd,
            safeViews.btnEditCmd,
            safeViews.btnSaveFrameCmd,
            safeViews.btnPrintCmd,
            // S0217: image-edit inline buttons + open-in-separate-window
            safeViews.btnOpenInSeparateWindowCmd,
            safeViews.btnCropCmd,
            safeViews.btnCropToFileCmd,
            safeViews.btnCompressCopyCmd,
            safeViews.btnDrawOverlayCmd,
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
        return list
    }

    /** Return the bar [View] for [cmd], or null for overflow-only commands. */
    private fun barViewForCommand(cmd: CommandPanelLayoutPlanner.PlayerCommand): View? {
        return when (cmd) {
            CommandPanelLayoutPlanner.PlayerCommand.DELETE -> binding.btnDeleteCmd
            CommandPanelLayoutPlanner.PlayerCommand.FAVORITE -> binding.btnFavorite
            CommandPanelLayoutPlanner.PlayerCommand.SHARE -> binding.btnShareCmd
            CommandPanelLayoutPlanner.PlayerCommand.INFO -> binding.btnInfoCmd
            CommandPanelLayoutPlanner.PlayerCommand.FULLSCREEN -> binding.btnFullscreenCmd
            CommandPanelLayoutPlanner.PlayerCommand.RANDOM -> binding.btnRandomCmd
            CommandPanelLayoutPlanner.PlayerCommand.RENAME -> safeViews.btnRenameCmd
            CommandPanelLayoutPlanner.PlayerCommand.EDIT -> safeViews.btnEditCmd
            CommandPanelLayoutPlanner.PlayerCommand.SAVE_FRAME -> safeViews.btnSaveFrameCmd
            CommandPanelLayoutPlanner.PlayerCommand.UNDO -> safeViews.btnUndoCmd
            CommandPanelLayoutPlanner.PlayerCommand.CAST -> safeViews.btnCastCmd
            CommandPanelLayoutPlanner.PlayerCommand.LYRICS -> safeViews.btnLyricsCmd
            CommandPanelLayoutPlanner.PlayerCommand.BLACK_SCREEN -> binding.btnBlackScreenCmd
            CommandPanelLayoutPlanner.PlayerCommand.SLEEP_TIMER -> binding.btnSleepTimerCmd
            CommandPanelLayoutPlanner.PlayerCommand.SEARCH_YOUTUBE_MUSIC -> safeViews.btnSearchYoutubeMusicCmd
            CommandPanelLayoutPlanner.PlayerCommand.ROTATION_TOGGLE -> safeViews.btnRotationToggleCmd
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
            // S0217: image-edit commands now bar-capable; map to dedicated inline buttons.
            CommandPanelLayoutPlanner.PlayerCommand.OPEN_IN_SEPARATE_WINDOW -> safeViews.btnOpenInSeparateWindowCmd
            CommandPanelLayoutPlanner.PlayerCommand.CROP -> safeViews.btnCropCmd
            CommandPanelLayoutPlanner.PlayerCommand.CROP_TO_FILE -> safeViews.btnCropToFileCmd
            CommandPanelLayoutPlanner.PlayerCommand.COMPRESS_COPY -> safeViews.btnCompressCopyCmd
            CommandPanelLayoutPlanner.PlayerCommand.DRAW_OVERLAY -> safeViews.btnDrawOverlayCmd
            else -> null // Overflow-only commands have no bar view
        }
    }

    private fun shouldShowRandomNavigation(profile: ResourceProfile?): Boolean {
        return profile == ResourceProfile.AUDIO_LIBRARY || profile == ResourceProfile.PHOTO_STORAGE
    }

    /** Pixel width available for the center adaptive group in portrait mode. Fixed anchors: Back (left) + Previous + Next (right) = 3 × 40dp. */
    private fun resolveAvailableCenterWidthPx(): Int {
        val dm = binding.root.resources.displayMetrics
        val buttonPx = (40 * dm.density).toInt()
        val panelWidth = if (binding.topCommandPanel.width > 0) {
            binding.topCommandPanel.width
        } else {
            dm.widthPixels
        }
        val slideshowFixed = if (binding.btnSlideshowCmd.isVisible) 1 else 0
        return (panelWidth - buttonPx * (3 + slideshowFixed)).coerceAtLeast(0) // Back + Slideshow + Prev + Next
    }

    /** Big Buttons Mode total visible top-panel slot count. Formula: `(panelWidthPx / minSlotWidthPx).coerceIn(5, 9)`. `panelWidthPx` is the laid-out width of `topCommandPanel`; falls back to `displayMetrics.widthPixels` before the first layout pass. `minSlotWidthPx` is `R.dimen.player_big_button_min_slot_width`. Strategic S0208 §3.1.4 / §5.1.3. */
    private fun resolveBigButtonsTopPanelSlotCount(): Int {
        val dm = binding.root.resources.displayMetrics
        val panelWidthPx = binding.topCommandPanel.width.takeIf { it > 0 } ?: dm.widthPixels
        val minSlotWidthPx = binding.root.resources
            .getDimensionPixelSize(R.dimen.player_big_button_min_slot_width)
            .coerceAtLeast(1)
        return (panelWidthPx / minSlotWidthPx).coerceIn(5, 9)
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

    fun updateRotationToggleIcon(sensorEnabled: Boolean) {
        val iconRes = if (sensorEnabled) R.drawable.ic_rotation_unlocked
                      else R.drawable.ic_rotation_locked
        safeViews.btnRotationToggleCmd.setImageResource(iconRes)
        safeViews.btnRotationToggleCmd.contentDescription =
            binding.root.context.getString(
                if (sensorEnabled) R.string.rotation_toggle_sensor_on_desc
                else R.string.rotation_toggle_sensor_off_desc
            )
    }

    private fun bigButtonsFixedButtons(): List<View> = listOf(
        binding.btnBack,
        binding.btnSlideshowCmd,
        binding.btnPreviousCmd,
        binding.btnNextCmd
    )

    private fun syncBigButtonsTopPanelLayout() {
        binding.topCommandPanel.post {
            bigButtonsModeManager.restoreTopCommandPanel(binding.topCommandPanel)

            val orderedButtons = buildList {
                if (binding.btnBack.isVisible) add(binding.btnBack)
                latestBigButtonsBarCommands.forEach { command ->
                    barViewForCommand(command)
                        ?.takeIf { it.isVisible }
                        ?.let(::add)
                }
                if (safeViews.btnOverflowMenu.isVisible) add(safeViews.btnOverflowMenu)
                if (binding.btnSlideshowCmd.isVisible) add(binding.btnSlideshowCmd)
                if (binding.btnPreviousCmd.isVisible) add(binding.btnPreviousCmd)
                if (binding.btnNextCmd.isVisible) add(binding.btnNextCmd)
            }

            if (orderedButtons.isEmpty()) return@post

            bigButtonsModeManager.applyToTopCommandPanel(
                topCommandPanel = binding.topCommandPanel,
                visibleButtons = orderedButtons,
                overflowButton = null,
                bigButtonsMode = true
            )
        }
    }

    private fun updateBigButtonsTopPanelContentDescriptions(editLabelRes: Int) {
        val context = binding.root.context
        binding.btnBack.contentDescription = context.getString(R.string.back)
        binding.btnPreviousCmd.contentDescription = context.getString(R.string.previous)
        binding.btnNextCmd.contentDescription = context.getString(R.string.next)
        binding.btnSlideshowCmd.contentDescription = context.getString(R.string.slideshow)
        safeViews.btnOverflowMenu.contentDescription = context.getString(R.string.more_actions)

        CommandPanelLayoutPlanner.PlayerCommand.entries.forEach { command ->
            barViewForCommand(command)?.contentDescription = context.getString(
                if (command == CommandPanelLayoutPlanner.PlayerCommand.EDIT) editLabelRes
                else command.titleResId
            )
        }
    }
}
