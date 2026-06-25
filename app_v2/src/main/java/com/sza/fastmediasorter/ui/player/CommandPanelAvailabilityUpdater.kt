package com.sza.fastmediasorter.ui.player

import android.app.Activity
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.compat.MultiWindowCapabilityDetector
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.helpers.CastMediaManager
import com.sza.fastmediasorter.ui.player.helpers.CommandPanelLayoutPlanner
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Computes per-state permissions + adaptive button visibility for [CommandPanelController]. Extracted from `CommandPanelController.updateCommandAvailability` to keep the host class under the 1000-LOC budget. */
internal class CommandPanelAvailabilityUpdater(
    private val binding: ActivityPlayerUnifiedBinding,
    private val safeViews: PlayerBindingSafeViews,
    private val planner: CommandPanelLayoutPlanner,
    private val mediaCapabilities: MediaCapabilities,
    private val bigButtonsModeManager: com.sza.fastmediasorter.ui.player.helpers.PlayerBigButtonsModeManager,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val bigButtonsMode: Boolean,
    private val getIsLandscapeMode: () -> Boolean,
    private val getCastMediaManager: () -> CastMediaManager?,
    private val getAllowVrLaunch: () -> Boolean,
    private val shouldShowRandomNavigation: (com.sza.fastmediasorter.domain.model.ResourceProfile?) -> Boolean,
    private val isWifiConnected: (android.content.Context) -> Boolean,
    private val getOverflowableButtons: () -> List<android.view.View>,
    private val barViewForCommand: (CommandPanelLayoutPlanner.PlayerCommand) -> android.view.View?,
    private val resolveAvailableCenterWidthPx: () -> Int,
    private val resolveBigButtonsTopPanelSlotCount: () -> Int,
    private val bigButtonsFixedButtons: () -> List<android.view.View>,
    private val updateBigButtonsTopPanelContentDescriptions: (Int) -> Unit,
    private val updateSlideshowButtonColor: (Boolean) -> Unit,
    private val syncBigButtonsTopPanelLayout: () -> Unit,
    private val logPanelGeometrySnapshot: (String) -> Unit,
    private val onCachedStateChange: (PlayerViewModel.PlayerState) -> Unit,
    private val getLastKnownFavoriteVisible: () -> Boolean,
    private val setLastKnownFavoriteVisible: (Boolean) -> Unit,
    private val getLastKnownAllowSeparateWindow: () -> Boolean,
    private val setLastKnownAllowSeparateWindow: (Boolean) -> Unit,
    private val setLatestBigButtonsBarCommands: (List<CommandPanelLayoutPlanner.PlayerCommand>) -> Unit,
    private val setLatestOverflowCommands: (List<CommandPanelLayoutPlanner.PlayerCommand>) -> Unit,
    private val reTriggerUpdate: (PlayerViewModel.PlayerState) -> Unit,
) {

    fun update(state: PlayerViewModel.PlayerState) {
        onCachedStateChange(state)
        val currentFile = state.currentFile ?: return
        val resource = state.resource
        val isReadOnly = resource?.isReadOnly == true
        val permissions = resolvePlayerFilePermissions(binding.root.context, resource, currentFile.path)
        val canWrite = permissions.canWrite
        val canRead = permissions.canRead

        // Audio files force-show the command panel regardless of state.showCommandPanel.
        val isAudioFile = currentFile.type == MediaType.AUDIO
        val effectiveShowCommandPanel = state.showCommandPanel || isAudioFile
        val isLandscapeMode = getIsLandscapeMode()
        val showInLandscape = effectiveShowCommandPanel && isLandscapeMode
        val showInPortrait = effectiveShowCommandPanel && !isLandscapeMode
        val showRandomNavigation = shouldShowRandomNavigation(resource?.profile)
        val isImage = currentFile.type == MediaType.IMAGE || currentFile.type == MediaType.GIF
        val isVideo = currentFile.type == MediaType.VIDEO || currentFile.type == MediaType.AUDIO
        val isPdf = currentFile.type == MediaType.PDF
        val isText = currentFile.type == MediaType.TEXT
        val isEpub = currentFile.type == MediaType.EPUB
        val isAudio = currentFile.type == MediaType.AUDIO
        // S0631: a live video stream has no slideshow - suppress it in portrait and big-buttons modes
        // (landscape is handled by the dedicated stream branch in applyLandscapeLayout).
        val isStreamVideo = state.isLiveVideoStream
        val showSlideshow = (isImage || isVideo) && !isStreamVideo

        binding.btnBack.isVisible = effectiveShowCommandPanel
        binding.btnPreviousCmd.isVisible = effectiveShowCommandPanel
        binding.btnNextCmd.isVisible = effectiveShowCommandPanel
        if (effectiveShowCommandPanel) {
            binding.btnFavorite.setImageResource(
                if (currentFile.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
        }

        if (effectiveShowCommandPanel) {
            coroutineScope.launch {
                val settings = settingsRepository.getSettings().first()
                // S0631: a live video stream is not a managed file - favorite never applies, so it must
                // not re-appear via this async pass after the layout hid it.
                val shouldShowFavorite =
                    (settings.enableFavorites || state.resource?.id == -100L) && !state.isLiveVideoStream
                val activity = binding.root.context as? Activity
                val runtimeMultiWindow = activity?.let { MultiWindowCapabilityDetector.isMultiWindowActiveNow(it) } ?: false
                val shouldAllowSeparateWindow = settings.allowSeparateWindow || runtimeMultiWindow
                withContext(Dispatchers.Main) {
                    val favoriteChanged = getLastKnownFavoriteVisible() != shouldShowFavorite
                    val separateChanged = getLastKnownAllowSeparateWindow() != shouldAllowSeparateWindow
                    setLastKnownFavoriteVisible(shouldShowFavorite)
                    setLastKnownAllowSeparateWindow(shouldAllowSeparateWindow)
                    if (favoriteChanged || separateChanged) {
                        reTriggerUpdate(state)
                    } else if (isLandscapeMode) {
                        binding.btnFavorite.isVisible = shouldShowFavorite
                    }
                }
            }
        }

        if (bigButtonsMode && effectiveShowCommandPanel) {
            applyBigButtonsLayout(state, canWrite, canRead, isVideo, showSlideshow, showRandomNavigation)
        } else if (showInPortrait) {
            applyPortraitLayout(state, canWrite, canRead, showSlideshow, showRandomNavigation)
        } else if (showInLandscape) {
            applyLandscapeLayout(state, canWrite, canRead, isReadOnly, isImage, isVideo, isPdf, isText, isEpub, isAudio, showRandomNavigation, currentFile)
        } else {
            setLatestBigButtonsBarCommands(emptyList())
            safeViews.btnOverflowMenu.isVisible = false
            setLatestOverflowCommands(emptyList())
            getOverflowableButtons().forEach { it.isVisible = false }
        }

        binding.btnBack.isEnabled = true
        binding.btnPreviousCmd.isEnabled = true
        binding.btnNextCmd.isEnabled = true
        binding.btnRandomCmd.isEnabled = state.files.size > 1
        binding.btnDeleteCmd.isEnabled = canWrite && canRead && state.allowDelete
        binding.btnSlideshowCmd.isEnabled = true
        updateSlideshowButtonColor(state.isSlideShowActive)

        val hasCopyButtons = safeViews.copyToButtonsGrid.childCount > 0
        val hasMoveButtons = safeViews.moveToButtonsGrid.childCount > 0
        // S0642: copy-to / move-to destination groups are file operations - inapplicable to a live
        // video stream (it is not a managed file). They sit on their own panel, outside the command
        // profile filter (S0631), so gate them here too.
        val copyPanelVisible = effectiveShowCommandPanel && state.enableCopying && hasCopyButtons &&
            !state.isLiveVideoStream
        val movePanelVisible = effectiveShowCommandPanel && state.enableMoving && hasMoveButtons &&
            canWrite && !state.isLiveVideoStream
        safeViews.copyToPanel.isVisible = copyPanelVisible
        safeViews.moveToPanel.isVisible = movePanelVisible
        logPanelGeometrySnapshot("decision")

        // Force layout recalc when panels appear - mediaContentArea weight=1 takes all space, LinearLayout doesn't recalc on its own (same fix as updateSystemBarsForPlayer post-exitFullscreen).
        if (copyPanelVisible || movePanelVisible) {
            binding.root.post {
                binding.mediaContentArea.requestLayout()
                safeViews.copyToPanel.requestLayout()
                safeViews.moveToPanel.requestLayout()
                binding.root.requestLayout()
                binding.root.requestApplyInsets()
            }
        }
        safeViews.copyToPanel.post {
            logPanelGeometrySnapshot("post")
            binding.root.post { logPanelGeometrySnapshot("post+1") }
        }
        if (bigButtonsMode) syncBigButtonsTopPanelLayout()
    }

    private fun applyBigButtonsLayout(
        state: PlayerViewModel.PlayerState,
        canWrite: Boolean,
        canRead: Boolean,
        isVideo: Boolean,
        showSlideshow: Boolean,
        showRandomNavigation: Boolean,
    ) {
        binding.btnSlideshowCmd.isVisible = showSlideshow
        val activeCommands = planner.buildActiveCommands(
            state, canWrite, canRead, isWifiConnected(binding.root.context),
            showFavorite = getLastKnownFavoriteVisible(),
            showRandom = showRandomNavigation,
            allowSeparateWindow = getLastKnownAllowSeparateWindow(),
            allowVrLaunch = getAllowVrLaunch(),
        )
        val editLabel = if (isVideo) R.string.control else R.string.edit
        safeViews.btnEditCmd.contentDescription = binding.root.context.getString(editLabel)
        updateBigButtonsTopPanelContentDescriptions(editLabel)
        val totalSlots = resolveBigButtonsTopPanelSlotCount()
        val commandSlots = (totalSlots - bigButtonsFixedButtons().count { it.isVisible }).coerceAtLeast(0)
        val result = planner.planBigButtonsLayout(activeCommands, commandSlots)
        setLatestBigButtonsBarCommands(result.barCommands)
        setLatestOverflowCommands(result.overflowCommands)
        getOverflowableButtons().forEach { it.isVisible = false }
        result.barCommands.forEach { cmd -> barViewForCommand(cmd)?.isVisible = true }
        // S0293 (ADR-2): pin "Open in new window" inline in big-buttons mode (planner may keep it in overflow due to priority 610; inline override wins).
        // S0631: not part of the stream control set - keep it hidden for a live video stream.
        safeViews.btnOpenInSeparateWindowCmd.isVisible =
            getLastKnownAllowSeparateWindow() && !state.isLiveVideoStream
        safeViews.btnRenameCmd.isEnabled = canWrite && canRead && state.allowRename
        safeViews.btnOverflowMenu.isVisible = result.showOverflowButton
    }

    private fun applyPortraitLayout(
        state: PlayerViewModel.PlayerState,
        canWrite: Boolean,
        canRead: Boolean,
        showSlideshow: Boolean,
        showRandomNavigation: Boolean,
    ) {
        setLatestBigButtonsBarCommands(emptyList())
        binding.btnSlideshowCmd.isVisible = showSlideshow
        val activeCommands = planner.buildActiveCommands(
            state, canWrite, canRead, isWifiConnected(binding.root.context),
            showFavorite = getLastKnownFavoriteVisible(),
            showRandom = showRandomNavigation,
            allowSeparateWindow = getLastKnownAllowSeparateWindow(),
            allowVrLaunch = getAllowVrLaunch(),
        )
        val availablePx = resolveAvailableCenterWidthPx()
        val density = binding.root.resources.displayMetrics.density
        val buttonPx = (40 * density).toInt()
        val overflowPx = (40 * density).toInt()
        val result = planner.planLayout(activeCommands, availablePx, buttonPx, overflowPx)
        setLatestOverflowCommands(result.overflowCommands)
        getOverflowableButtons().forEach { it.isVisible = false }
        result.barCommands.forEach { cmd -> barViewForCommand(cmd)?.isVisible = true }
        // S0631: not part of the stream control set - keep it hidden for a live video stream.
        safeViews.btnOpenInSeparateWindowCmd.isVisible =
            getLastKnownAllowSeparateWindow() && !state.isLiveVideoStream
        safeViews.btnRenameCmd.isEnabled = canWrite && canRead && state.allowRename
        safeViews.btnOverflowMenu.isVisible = result.showOverflowButton
    }

    private fun applyLandscapeLayout(
        state: PlayerViewModel.PlayerState,
        canWrite: Boolean,
        canRead: Boolean,
        isReadOnly: Boolean,
        isImage: Boolean,
        isVideo: Boolean,
        isPdf: Boolean,
        isText: Boolean,
        isEpub: Boolean,
        isAudio: Boolean,
        showRandomNavigation: Boolean,
        currentFile: com.sza.fastmediasorter.domain.model.MediaFile,
    ) {
        setLatestBigButtonsBarCommands(emptyList())

        // S0631: live video stream profile. Hide the entire non-stream control set, then re-enable only
        // the owner-approved subset so portrait and landscape show the same controls (criterion §11.5).
        // SEND_TO is overflow-only (barCapable=false) and surfaces via the ⋯ button as the share-link entry.
        if (state.isLiveVideoStream) {
            getOverflowableButtons().forEach { it.isVisible = false }
            binding.btnSlideshowCmd.isVisible = false
            binding.btnInfoCmd.isVisible = true
            binding.btnFullscreenCmd.isVisible = true
            safeViews.btnSaveFrameCmd.isVisible = true
            safeViews.btnEditCmd.isVisible = true
            safeViews.btnEditCmd.contentDescription = binding.root.context.getString(R.string.control)
            safeViews.btnRotationToggleCmd.isVisible = state.showRotationToggle
            safeViews.btnCastCmd.isVisible =
                mediaCapabilities.supportsCast &&
                (getCastMediaManager()?.isCastAvailable == true) &&
                isWifiConnected(binding.root.context)
            val streamOverflowCmds = planner.buildActiveCommands(
                state, canWrite, canRead, isWifiConnected(binding.root.context),
                showFavorite = getLastKnownFavoriteVisible(),
                showRandom = showRandomNavigation,
                allowSeparateWindow = getLastKnownAllowSeparateWindow(),
                allowVrLaunch = getAllowVrLaunch(),
            ).filter { !it.barCapable }
            setLatestOverflowCommands(streamOverflowCmds)
            safeViews.btnOverflowMenu.isVisible = streamOverflowCmds.isNotEmpty()
            return
        }

        binding.btnRandomCmd.isVisible = showRandomNavigation
        binding.btnDeleteCmd.isVisible = canWrite && state.allowDelete
        binding.btnFavorite.isVisible = getLastKnownFavoriteVisible()
        // S0459: the ad-hoc Share bar button is gone - outbound is the unified «Send to..» (SEND_TO) overflow command.
        binding.btnShareCmd.isVisible = false
        binding.btnInfoCmd.isVisible = true
        // S0301 Phase 05: the embedded Office viewer is a read-only document surface, so it gets
        // the same fullscreen affordance as PDF/EPUB. Edit stays hidden (see btnEditCmd below).
        val isOffice = currentFile.type == MediaType.OFFICE_DOCUMENT
        binding.btnFullscreenCmd.isVisible = !isAudio && (isImage || isVideo || isPdf || isText || isEpub || isOffice)
        binding.btnSlideshowCmd.isVisible = isImage || isVideo
        binding.btnBlackScreenCmd.isVisible = (isAudio || isVideo) && state.showBlackScreenButton

        safeViews.btnRenameCmd.isEnabled = canWrite && canRead && state.allowRename
        safeViews.btnRenameCmd.isVisible = canWrite && state.allowRename
        safeViews.btnUndoCmd.isVisible = state.lastOperation != null && canWrite
        safeViews.btnLyricsCmd.isVisible = isVideo && currentFile.type == MediaType.AUDIO
        safeViews.btnSearchYoutubeMusicCmd.isVisible = isVideo && currentFile.type == MediaType.AUDIO
        safeViews.btnCastCmd.isVisible =
            mediaCapabilities.supportsCast &&
            (getCastMediaManager()?.isCastAvailable == true) &&
            (isImage || isVideo) &&
            isWifiConnected(binding.root.context)
        safeViews.btnEditCmd.isVisible = (isImage && canWrite) || (isVideo && !isAudio) || isPdf
        safeViews.btnSaveFrameCmd.isVisible = currentFile.type == MediaType.VIDEO
        safeViews.btnEditCmd.contentDescription = binding.root.context.getString(
            if (isVideo) R.string.control else R.string.edit
        )
        safeViews.btnGoogleLensPdfCmd.isVisible = isPdf
        safeViews.btnOcrPdfCmd.isVisible = isPdf
        safeViews.btnTranslatePdfCmd.isVisible = isPdf
        safeViews.btnSearchPdfCmd.isVisible = isPdf
        safeViews.btnPdfThumbnailsCmd.isVisible = isPdf
        safeViews.btnCopyTextCmd.isVisible = isText || isPdf
        safeViews.btnEditTextCmd.isVisible = isText && canWrite
        safeViews.btnTranslateTextCmd.isVisible = isText
        safeViews.btnTextSettingsCmd.isVisible = isText
        safeViews.btnSearchTextCmd.isVisible = isText
        safeViews.btnSearchEpubCmd.isVisible = isEpub
        safeViews.btnTranslateEpubCmd.isVisible = isEpub
        safeViews.btnEpubTextSettingsCmd.isVisible = isEpub
        safeViews.btnOcrEpubCmd.isVisible = isEpub
        safeViews.btnPdfTextSettingsCmd.isVisible = isPdf
        // S0459: image Google Lens is now a unified-menu receiver, not a panel button - kept hidden here.
        safeViews.btnGoogleLensImageCmd.isVisible = false
        if (isImage) {
            safeViews.btnTranslateImageCmd.isVisible = state.enableTranslation
            safeViews.btnOcrImageCmd.isVisible = state.enableOcr
            safeViews.btnImageTextSettingsCmd.isVisible = true
        } else {
            safeViews.btnTranslateImageCmd.isVisible = false
            safeViews.btnImageTextSettingsCmd.isVisible = false
            safeViews.btnOcrImageCmd.isVisible = false
        }
        // S0459: Print is now a «Send to..» receiver (settings-gated), not a panel button - kept hidden here.
        safeViews.btnPrintCmd.isVisible = false
        val isStaticBitmap = isImage &&
            !currentFile.name.lowercase().endsWith(".gif") &&
            !currentFile.name.lowercase().endsWith(".apng")
        safeViews.btnOpenInSeparateWindowCmd.isVisible = getLastKnownAllowSeparateWindow()
        safeViews.btnCropCmd.isVisible = isStaticBitmap && canWrite && !isReadOnly
        safeViews.btnCropToFileCmd.isVisible = isStaticBitmap
        safeViews.btnCompressCopyCmd.isVisible = isStaticBitmap
        safeViews.btnDrawOverlayCmd.isVisible = isStaticBitmap
        // S0129: overflow-only commands (barCapable=false) exposed in landscape via ⋯ button.
        val landscapeOverflowCmds = planner.buildActiveCommands(
            state, canWrite, canRead, isWifiConnected(binding.root.context),
            showFavorite = getLastKnownFavoriteVisible(),
            showRandom = showRandomNavigation,
            allowSeparateWindow = getLastKnownAllowSeparateWindow(),
            allowVrLaunch = getAllowVrLaunch(),
        ).filter { !it.barCapable }
        setLatestOverflowCommands(landscapeOverflowCmds)
        safeViews.btnOverflowMenu.isVisible = landscapeOverflowCmds.isNotEmpty()
    }
}
