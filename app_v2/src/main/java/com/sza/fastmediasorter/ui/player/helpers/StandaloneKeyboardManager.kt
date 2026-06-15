package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.Player
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.domain.model.MediaType

/**
 * S0393 U4/U5: shared keyboard / D-pad / TV layer for the standalone hosts. The [PlayerKeyboardHandler]
 * parser is host-agnostic; each host supplies only the routes that apply (paging for audio, pdf/epub
 * keys for documents, text-scroll for text). Seek / volume / mute operate on the active player and are
 * implemented once here. Replaces the per-host duplication (the former PhotoVideoStandaloneKeyboardManager
 * is folded in), so all standalone hosts share one keyboard layer.
 */
class StandaloneKeyboardManager(
    keyBindingManager: KeyBindingManager,
    private val getActivePlayer: () -> Player? = { null },
    private val getCurrentMediaType: () -> MediaType? = { null },
    private val onDelete: () -> Unit = {},
    private val onExit: () -> Unit = {},
    private val onShowRename: () -> Unit = {},
    private val onShowInfo: () -> Unit = {},
    private val onToggleCommandPanel: () -> Unit = {},
    private val onToggleFullscreen: () -> Unit = {},
    private val onToggleFavourite: () -> Unit = {},
    private val onNextFile: () -> Unit = {},
    private val onPreviousFile: () -> Unit = {},
    private val onToggleSlideshow: () -> Unit = {},
    private val onPdfNextPage: () -> Unit = {},
    private val onPdfPreviousPage: () -> Unit = {},
    private val onPdfHome: () -> Unit = {},
    private val onPdfEnd: () -> Unit = {},
    private val onEpubNextPage: () -> Unit = {},
    private val onEpubPreviousPage: () -> Unit = {},
    private val onEpubHome: () -> Unit = {},
    private val onEpubEnd: () -> Unit = {},
    private val onEpubScrollDelta: (Float) -> Unit = {},
    private val onTextScrollDown: () -> Unit = {},
    private val onTextScrollUp: () -> Unit = {},
    private val onTextHome: () -> Unit = {},
    private val onTextEnd: () -> Unit = {},
    private val onDocumentSearch: () -> Unit = {},
    private val onShowContextMenu: () -> Unit = {},
    private val onShowHelp: () -> Unit = {},
    private val onToggleRotationSensor: () -> Unit = {},
) {
    val handler: PlayerKeyboardHandler = PlayerKeyboardHandler(
        callback = object : PlayerKeyboardHandler.PlayerKeyboardCallback {
            override fun onDeleteFile() = onDelete()
            override fun onExitPlayer() = onExit()
            override fun onToggleSlideshow() = this@StandaloneKeyboardManager.onToggleSlideshow()
            override fun onShowRenameDialog() = onShowRename()
            override fun onShowFileInfo() = onShowInfo()
            override fun onToggleCommandPanel() = this@StandaloneKeyboardManager.onToggleCommandPanel()
            override fun onToggleCopyPanel() { /* no copy targets in standalone */ }
            override fun onToggleMovePanel() { /* no move targets in standalone */ }
            override fun onShowEditDialog() { /* not applicable */ }
            override fun getActivePlayer(): Player? = this@StandaloneKeyboardManager.getActivePlayer()
            override fun getCurrentMediaType(): MediaType? = this@StandaloneKeyboardManager.getCurrentMediaType()
            override fun onPdfNextPage() = this@StandaloneKeyboardManager.onPdfNextPage()
            override fun onPdfPreviousPage() = this@StandaloneKeyboardManager.onPdfPreviousPage()
            override fun onPdfHome() = this@StandaloneKeyboardManager.onPdfHome()
            override fun onPdfEnd() = this@StandaloneKeyboardManager.onPdfEnd()
            override fun onEpubNextPage() = this@StandaloneKeyboardManager.onEpubNextPage()
            override fun onEpubPreviousPage() = this@StandaloneKeyboardManager.onEpubPreviousPage()
            override fun onEpubHome() = this@StandaloneKeyboardManager.onEpubHome()
            override fun onEpubEnd() = this@StandaloneKeyboardManager.onEpubEnd()
            override fun onTextScrollDown() = this@StandaloneKeyboardManager.onTextScrollDown()
            override fun onTextScrollUp() = this@StandaloneKeyboardManager.onTextScrollUp()
            override fun onTextHome() = this@StandaloneKeyboardManager.onTextHome()
            override fun onTextEnd() = this@StandaloneKeyboardManager.onTextEnd()
            override fun onSeekForward(seconds: Int) {
                val p = getActivePlayer() ?: return
                p.seekTo((p.currentPosition + seconds * 1000L).coerceAtMost(p.duration))
            }
            override fun onSeekBackward(seconds: Int) {
                val p = getActivePlayer() ?: return
                p.seekTo((p.currentPosition - seconds * 1000L).coerceAtLeast(0L))
            }
            override fun onEpubScrollDelta(verticalScroll: Float) =
                this@StandaloneKeyboardManager.onEpubScrollDelta(verticalScroll)
            override fun onNavigationScroll(verticalScroll: Float) { /* single-file standalone */ }
            override fun onToggleMute() {
                val p = getActivePlayer() ?: return
                p.volume = if (p.volume > 0f) 0f else 1f
            }
            override fun onToggleFullscreen() = this@StandaloneKeyboardManager.onToggleFullscreen()
            override fun onChangeVolume(delta: Int) {
                val p = getActivePlayer() ?: return
                p.volume = (p.volume + delta * 0.1f).coerceIn(0f, 1f)
            }
            override fun onDocumentSearch() = this@StandaloneKeyboardManager.onDocumentSearch()
            override fun onSaveCurrent() { /* save frame not supported standalone */ }
            override fun onShowContextMenu() = this@StandaloneKeyboardManager.onShowContextMenu()
            override fun onShowHelp() = this@StandaloneKeyboardManager.onShowHelp()
            override fun onToggleRotationSensor() = this@StandaloneKeyboardManager.onToggleRotationSensor()
            override fun onNextFile() = this@StandaloneKeyboardManager.onNextFile()
            override fun onPreviousFile() = this@StandaloneKeyboardManager.onPreviousFile()
            override fun onToggleFavourite() = this@StandaloneKeyboardManager.onToggleFavourite()
            override fun onUndoOperation() { /* delete-undo handled by the shared coordinator */ }
        },
        keyBindingManager = keyBindingManager,
    )
}
