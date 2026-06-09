package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.Player
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.domain.model.MediaType

/**
 * S0380: builds the [PlayerKeyboardHandler] for [com.sza.fastmediasorter.ui.player.standalone.PhotoVideoStandaloneActivity].
 *
 * Only the image/gif/video-relevant routes are live (seek, mute, fullscreen, volume, favourite,
 * command-panel toggle, delete, info, rename, exit). All document (pdf/epub/text) and playlist
 * routes are inert no-ops because this trimmed surface never opens those media types.
 *
 * Extracted from the Activity to keep it free of input-routing branching (Strict Rule 3).
 */
class PhotoVideoStandaloneKeyboardManager(
    keyBindingManager: KeyBindingManager,
    private val getActivePlayer: () -> Player?,
    private val getCurrentMediaType: () -> MediaType?,
    private val onDelete: () -> Unit,
    private val onExit: () -> Unit,
    private val onShowRename: () -> Unit,
    private val onShowInfo: () -> Unit,
    private val onToggleCommandPanel: () -> Unit,
    private val onToggleFullscreen: () -> Unit,
    private val onToggleFavourite: () -> Unit,
    // S0389: folder-paging routes. No-ops at the VM level when the opened file is not in a local folder.
    private val onNextFile: () -> Unit,
    private val onPreviousFile: () -> Unit,
    private val onToggleSlideshow: () -> Unit,
) {

    val handler: PlayerKeyboardHandler = PlayerKeyboardHandler(
        callback = object : PlayerKeyboardHandler.PlayerKeyboardCallback {
            override fun onDeleteFile() = onDelete()
            override fun onExitPlayer() = onExit()
            override fun onToggleSlideshow() =
                this@PhotoVideoStandaloneKeyboardManager.onToggleSlideshow()
            override fun onShowRenameDialog() = onShowRename()
            override fun onShowFileInfo() = onShowInfo()
            override fun onToggleCommandPanel() =
                this@PhotoVideoStandaloneKeyboardManager.onToggleCommandPanel()
            override fun onToggleCopyPanel() { /* not applicable */ }
            override fun onToggleMovePanel() { /* not applicable */ }
            override fun onShowEditDialog() { /* not applicable */ }
            override fun getActivePlayer(): Player? = this@PhotoVideoStandaloneKeyboardManager.getActivePlayer()
            override fun getCurrentMediaType(): MediaType? =
                this@PhotoVideoStandaloneKeyboardManager.getCurrentMediaType()
            override fun onPdfNextPage() { /* no documents here */ }
            override fun onPdfPreviousPage() { /* no documents here */ }
            override fun onPdfHome() { /* no documents here */ }
            override fun onPdfEnd() { /* no documents here */ }
            override fun onEpubNextPage() { /* no documents here */ }
            override fun onEpubPreviousPage() { /* no documents here */ }
            override fun onEpubHome() { /* no documents here */ }
            override fun onEpubEnd() { /* no documents here */ }
            override fun onTextScrollDown() { /* no text here */ }
            override fun onTextScrollUp() { /* no text here */ }
            override fun onTextHome() { /* no text here */ }
            override fun onTextEnd() { /* no text here */ }
            override fun onSeekForward(seconds: Int) {
                val p = getActivePlayer() ?: return
                p.seekTo((p.currentPosition + seconds * 1000L).coerceAtMost(p.duration))
            }
            override fun onSeekBackward(seconds: Int) {
                val p = getActivePlayer() ?: return
                p.seekTo((p.currentPosition - seconds * 1000L).coerceAtLeast(0L))
            }
            override fun onEpubScrollDelta(verticalScroll: Float) { /* no documents here */ }
            override fun onNavigationScroll(verticalScroll: Float) { /* single-file standalone */ }
            override fun onToggleMute() {
                val p = getActivePlayer() ?: return
                p.volume = if (p.volume > 0f) 0f else 1f
            }
            override fun onToggleFullscreen() =
                this@PhotoVideoStandaloneKeyboardManager.onToggleFullscreen()
            override fun onChangeVolume(delta: Int) {
                val p = getActivePlayer() ?: return
                p.volume = (p.volume + delta * 0.1f).coerceIn(0f, 1f)
            }
            override fun onDocumentSearch() { /* no documents here */ }
            override fun onSaveCurrent() { /* save frame not supported standalone */ }
            override fun onShowContextMenu() =
                this@PhotoVideoStandaloneKeyboardManager.onToggleCommandPanel()
            override fun onNextFile() = this@PhotoVideoStandaloneKeyboardManager.onNextFile()
            override fun onPreviousFile() = this@PhotoVideoStandaloneKeyboardManager.onPreviousFile()
            override fun onToggleFavourite() =
                this@PhotoVideoStandaloneKeyboardManager.onToggleFavourite()
            override fun onUndoOperation() { /* delete-undo handled by the shared coordinator */ }
        },
        keyBindingManager = keyBindingManager,
    )
}
