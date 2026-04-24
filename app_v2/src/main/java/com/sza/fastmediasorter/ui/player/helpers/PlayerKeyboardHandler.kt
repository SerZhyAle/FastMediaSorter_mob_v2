package com.sza.fastmediasorter.ui.player.helpers

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.media3.common.Player
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.common.MouseEventHandler
import com.sza.fastmediasorter.ui.common.input.InputAction
import com.sza.fastmediasorter.ui.common.input.InputSurface
import com.sza.fastmediasorter.util.KeyboardShortcutHandler
import com.sza.fastmediasorter.utils.UserActionLogger
import timber.log.Timber

/**
 * Keyboard and mouse-wheel handler for [PlayerActivity] and [StandalonePlayerActivity].
 * Single host-agnostic parser — each host supplies its own [PlayerKeyboardCallback].
 *
 * Flow: raw [KeyEvent] → [KeyboardShortcutHandler] (PLAYER profile) → [InputAction] →
 * [PlayerKeyboardCallback] → existing manager / ViewModel call.
 * Legacy raw-key handling (media-button debouncing, scan-code fixup) stays in the
 * secondary switch below.
 */
class PlayerKeyboardHandler(
    // Exposed so gamepad dispatch in PlayerActivity can reuse the same callback routes.
    internal val callback: PlayerKeyboardCallback,
) {
    /** Debounce window for media button events to prevent double-trigger (Activity + MediaSession). */
    private var lastMediaButtonTimeMs = 0L

    private companion object {
        const val MEDIA_BUTTON_DEBOUNCE_MS = 300L
        const val SEEK_INCREMENT_SECONDS = 10
    }

    interface PlayerKeyboardCallback {
        // ── file operations ──────────────────────────────────────────────────
        fun onDeleteFile()
        fun onExitPlayer()
        fun onToggleSlideshow()
        fun onShowRenameDialog()
        fun onShowFileInfo()
        fun onToggleCommandPanel()
        fun onToggleCopyPanel()
        fun onToggleMovePanel()
        fun onShowEditDialog()
        // ── playback / media ─────────────────────────────────────────────────
        /** Returns the currently active player: service MediaController or Activity ExoPlayer. */
        fun getActivePlayer(): Player?
        fun getCurrentMediaType(): MediaType?
        fun onNextFile() {}
        fun onPreviousFile() {}
        fun onSeekForward(seconds: Int)
        fun onSeekBackward(seconds: Int)
        // ── document viewers ─────────────────────────────────────────────────
        fun onPdfNextPage()
        fun onPdfPreviousPage()
        fun onPdfHome()
        fun onPdfEnd()
        fun onEpubNextPage()
        fun onEpubPreviousPage()
        fun onEpubHome()
        fun onEpubEnd()
        fun onTextScrollDown()
        fun onTextScrollUp()
        fun onTextHome()
        fun onTextEnd()
        /** Mouse-wheel scroll on EPUB — positive = scroll up, negative = scroll down. */
        fun onEpubScrollDelta(verticalScroll: Float)
        /** Mouse-wheel scroll on non-document media — delegate to navigation manager. */
        fun onNavigationScroll(verticalScroll: Float)
        // ── Phase 2 additions ────────────────────────────────────────────────
        fun onToggleMute() {}
        fun onToggleFullscreen() {}
        fun onChangeVolume(delta: Int) {}
        fun onShowHelp() {}
        /** Invoke document search UI if the current surface supports it (PDF / EPUB / TXT). */
        fun onDocumentSearch() {}
        fun onSaveCurrent() {}
        fun onShowContextMenu() {}
        fun onToggleFavourite() {}
        fun onUndoOperation() {}
        fun canCopyCurrent(): Boolean = false
        fun canMoveCurrent(): Boolean = false
    }

    // ── shared keyboard parser ────────────────────────────────────────────────

    private val shortcutHandler = KeyboardShortcutHandler(
        surface = InputSurface.PLAYER,
        dispatcher = KeyboardShortcutHandler.ActionDispatcher { action -> dispatchAction(action) },
    )

    private val mouseHandler = MouseEventHandler(
        callbacks = object : MouseEventHandler.MouseEventCallbacks {
            override fun onRightClick(view: View, x: Float, y: Float) {
                callback.onShowContextMenu()
            }

            override fun onMiddleClick(view: View) {
                callback.onToggleFavourite()
            }

            override fun onScrollWheel(
                view: View,
                deltaY: Float,
                deltaX: Float,
                withShift: Boolean,
                withCtrl: Boolean,
            ) {
                handleWheelScroll(deltaY)
            }

            override fun onNavigateBack(view: View) {
                callback.onPreviousFile()
            }

            override fun onNavigateForward(view: View) {
                callback.onNextFile()
            }
        }
    )

    private fun dispatchAction(action: InputAction): Boolean {
        return when (action) {
            InputAction.ShowHelp -> { callback.onShowHelp(); true }
            InputAction.ExitSurface -> { callback.onExitPlayer(); true }
            InputAction.RenameSelection -> { callback.onShowRenameDialog(); true }
            InputAction.ShowInfo -> { callback.onShowFileInfo(); true }
            InputAction.DeleteSelection -> { callback.onDeleteFile(); true }
            InputAction.CopySelection -> if (callback.canCopyCurrent()) {
                callback.onToggleCopyPanel(); true
            } else {
                false
            }
            InputAction.MoveSelection -> if (callback.canMoveCurrent()) {
                callback.onToggleMovePanel(); true
            } else {
                false
            }
            InputAction.EditCurrent -> { callback.onShowEditDialog(); true }
            InputAction.ShowContextMenu -> { callback.onShowContextMenu(); true }
            InputAction.SaveCurrent -> { callback.onSaveCurrent(); true }
            InputAction.ToggleFavourite -> { callback.onToggleFavourite(); true }
            InputAction.PlayPause -> { handlePlayPause(); true }
            InputAction.ToggleMute -> { callback.onToggleMute(); true }
            InputAction.ToggleFullscreen -> { callback.onToggleFullscreen(); true }
            InputAction.ShowPlaybackControls -> { callback.onToggleCommandPanel(); true }
            is InputAction.ChangeVolume -> { callback.onChangeVolume(action.delta); true }
            is InputAction.SeekBy -> {
                if (action.seconds >= 0) callback.onSeekForward(action.seconds)
                else callback.onSeekBackward(-action.seconds)
                true
            }
            InputAction.NextTrack -> { callback.onNextFile(); true }
            InputAction.PreviousTrack -> { callback.onPreviousFile(); true }
            InputAction.RefreshCurrent -> true  // no-op in player
            InputAction.UndoRequested -> { callback.onUndoOperation(); true }
            InputAction.SearchRequested -> if (supportsDocumentSearch(callback.getCurrentMediaType())) {
                callback.onDocumentSearch(); true
            } else {
                false
            }
            else -> false
        }
    }

    // ── entry point ───────────────────────────────────────────────────────────

    /**
     * Process keyboard event. Returns true if handled.
     */
    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val currentType = callback.getCurrentMediaType()

        // Scan-code fixup for external keyboards that send KEYCODE_UNKNOWN.
        val effectiveKeyCode = if (keyCode == KeyEvent.KEYCODE_UNKNOWN && event != null) {
            when (event.scanCode) {
                104 -> KeyEvent.KEYCODE_PAGE_UP
                109 -> KeyEvent.KEYCODE_PAGE_DOWN
                102 -> KeyEvent.KEYCODE_MOVE_HOME
                107 -> KeyEvent.KEYCODE_MOVE_END
                else -> keyCode
            }
        } else {
            keyCode
        }

        UserActionLogger.logKey(
            effectiveKeyCode, event?.action ?: KeyEvent.ACTION_DOWN,
            KeyEvent.keyCodeToString(effectiveKeyCode), "PlayerKeyboardHandler (type=$currentType)"
        )

        // Hardware media buttons can arrive twice (Activity + MediaSession). Debounce them
        // before semantic parsing so mapped shortcuts do not bypass the legacy guard.
        if (event != null && needsMediaButtonDebounce(effectiveKeyCode) && isMediaButtonDebounced(event)) {
            return true
        }

        // Shared semantic parser first (handles F-keys, letter shortcuts, color keys, …).
        if (event != null && shortcutHandler.handleKeyEvent(effectiveKeyCode, event)) return true

        // Legacy raw-key path retained for media-hardware-button debouncing and
        // document-viewer page navigation that depends on media type at call time.
        when (effectiveKeyCode) {
            KeyEvent.KEYCODE_PAGE_UP -> {
                when (currentType) {
                    MediaType.PDF -> { callback.onPdfPreviousPage(); return true }
                    MediaType.TEXT, MediaType.EPUB -> return false
                    else -> { callback.onPreviousFile(); return true }
                }
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                when (currentType) {
                    MediaType.PDF -> { callback.onPdfNextPage(); return true }
                    MediaType.TEXT, MediaType.EPUB -> return false
                    else -> { callback.onNextFile(); return true }
                }
            }
            KeyEvent.KEYCODE_MOVE_HOME -> {
                when (currentType) {
                    MediaType.PDF -> { callback.onPdfHome(); return true }
                    MediaType.EPUB -> { callback.onEpubHome(); return true }
                    MediaType.TEXT -> { callback.onTextHome(); return true }
                    else -> {}
                }
            }
            KeyEvent.KEYCODE_MOVE_END -> {
                when (currentType) {
                    MediaType.PDF -> { callback.onPdfEnd(); return true }
                    MediaType.EPUB -> { callback.onEpubEnd(); return true }
                    MediaType.TEXT -> { callback.onTextEnd(); return true }
                    else -> {}
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                Timber.d("PlayerKeyboardHandler: Left — previous file")
                callback.onPreviousFile()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                Timber.d("PlayerKeyboardHandler: Right — next file")
                callback.onNextFile()
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (isMediaButtonDebounced(event)) return true
                handlePlayPause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (isMediaButtonDebounced(event)) return true
                handlePlay()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (isMediaButtonDebounced(event)) return true
                handlePause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (isMediaButtonDebounced(event)) return true
                callback.onNextFile()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (isMediaButtonDebounced(event)) return true
                callback.onPreviousFile()
                return true
            }
            KeyEvent.KEYCODE_SPACE -> { handlePlayPause(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER -> { handlePlayPause(); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (isMediaButtonDebounced(event)) return true
                handleSeekForward()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (isMediaButtonDebounced(event)) return true
                handleSeekBackward()
                return true
            }
            KeyEvent.KEYCODE_RIGHT_BRACKET -> { handleSeekForward(); return true }
            KeyEvent.KEYCODE_LEFT_BRACKET -> { handleSeekBackward(); return true }
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                if (isMediaButtonDebounced(event)) return true
                callback.onNextFile()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                if (isMediaButtonDebounced(event)) return true
                callback.onPreviousFile()
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> { callback.onNextFile(); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { callback.onPreviousFile(); return true }
            KeyEvent.KEYCODE_BOOKMARK -> { callback.onToggleFavourite(); return true }
            KeyEvent.KEYCODE_Z -> {
                if (event?.isCtrlPressed == true) {
                    callback.onUndoOperation()
                    return true
                }
            }
        }

        return false
    }

    // ── pointer / wheel input ────────────────────────────────────────────────

    fun handlePointerEvent(view: View, event: MotionEvent?): Boolean {
        if (event == null) return false
        if (mouseHandler.handleMotionEvent(view, event)) return true
        if (mouseHandler.handleGenericMotionEvent(view, event)) return true
        return false
    }

    private fun handleWheelScroll(scrollY: Float) {
        if (scrollY == 0f) return
        when (callback.getCurrentMediaType()) {
            MediaType.PDF -> {
                if (scrollY > 0) callback.onPdfPreviousPage() else callback.onPdfNextPage()
                return
            }
            MediaType.TEXT -> {
                if (scrollY > 0) callback.onTextScrollUp() else callback.onTextScrollDown()
                return
            }
            MediaType.EPUB -> {
                callback.onEpubScrollDelta(scrollY)
                return
            }
            else -> {
                callback.onNavigationScroll(scrollY)
                return
            }
        }
    }

    private fun supportsDocumentSearch(mediaType: MediaType?): Boolean = when (mediaType) {
        MediaType.PDF, MediaType.TEXT, MediaType.EPUB -> true
        else -> false
    }

    // ── media button helpers ──────────────────────────────────────────────────

    private fun needsMediaButtonDebounce(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> true
        else -> false
    }

    private fun isMediaButtonDebounced(event: KeyEvent?): Boolean {
        if (event != null && event.repeatCount > 0) {
            Timber.d("PlayerKeyboardHandler: ignoring media button repeat (count=${event.repeatCount})")
            return true
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastMediaButtonTimeMs < MEDIA_BUTTON_DEBOUNCE_MS) {
            Timber.d("PlayerKeyboardHandler: debouncing media button (${now - lastMediaButtonTimeMs}ms)")
            return true
        }
        lastMediaButtonTimeMs = now
        return false
    }

    private fun handlePlayPause() {
        val currentType = callback.getCurrentMediaType()
        if (currentType == MediaType.VIDEO || currentType == MediaType.AUDIO) {
            callback.getActivePlayer()?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
                Timber.d("PlayerKeyboardHandler: play/pause toggled")
            }
        }
    }

    private fun handlePlay() {
        val currentType = callback.getCurrentMediaType()
        if (currentType == MediaType.VIDEO || currentType == MediaType.AUDIO) {
            callback.getActivePlayer()?.let { if (!it.isPlaying) it.play() }
        }
    }

    private fun handlePause() {
        val currentType = callback.getCurrentMediaType()
        if (currentType == MediaType.VIDEO || currentType == MediaType.AUDIO) {
            callback.getActivePlayer()?.let { if (it.isPlaying) it.pause() }
        }
    }

    private fun handleSeekForward() {
        if (callback.getCurrentMediaType().let { it == MediaType.VIDEO || it == MediaType.AUDIO }) {
            callback.onSeekForward(SEEK_INCREMENT_SECONDS)
        }
    }

    private fun handleSeekBackward() {
        if (callback.getCurrentMediaType().let { it == MediaType.VIDEO || it == MediaType.AUDIO }) {
            callback.onSeekBackward(SEEK_INCREMENT_SECONDS)
        }
    }
}
