package com.sza.fastmediasorter.ui.player.helpers

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.media3.common.Player
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.domain.input.CommandId
import com.sza.fastmediasorter.domain.input.InputSurface as DomainSurface
import com.sza.fastmediasorter.domain.input.InputTrigger
import com.sza.fastmediasorter.domain.input.fromKeyEvent
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.common.MouseEventHandler
import com.sza.fastmediasorter.ui.common.input.InputSurface
import com.sza.fastmediasorter.utils.UserActionLogger
import timber.log.Timber

/**
 * Keyboard and mouse-wheel handler for [PlayerActivity] and [StandalonePlayerActivity].
 * Single host-agnostic parser — each host supplies its own [PlayerKeyboardCallback].
 *
 * Flow: raw [KeyEvent] → [KeyBindingManager] resolver → [CommandId] →
 * [handleCommand] (media-type-aware) → [PlayerKeyboardCallback] → ViewModel call.
 * Legacy raw-key handling (media-button debouncing, scan-code fixup) stays in the
 * preamble of [handleKeyDown].
 */
class PlayerKeyboardHandler(
    // Exposed so gamepad dispatch in PlayerActivity can reuse the same callback routes.
    internal val callback: PlayerKeyboardCallback,
    private val keyBindingManager: KeyBindingManager,
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
        fun onToggleBlackScreen() {}
        // S0162: toggle player-level rotation sensor (no-op when followSystemRotation=true — guard in ViewModel)
        fun onToggleRotationSensor() {}
    }

    // ── pointer / wheel input ────────────────────────────────────────────────

    private val mouseHandler = MouseEventHandler(
        keyBindingManager = keyBindingManager,
        surface = DomainSurface.PLAYER,
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

    // ── entry point ───────────────────────────────────────────────────────────

    /**
     * Process keyboard event. Returns true if handled.
     *
     * Flow: scan-code fixup → debounce → resolver → [handleCommand].
     * Scan-code-fixed keys that produce KEYCODE_UNKNOWN in the event object
     * are dispatched via [scanCodeToCommandId] after a resolver miss.
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

        if (event == null) return false

        // Primary path: resolve trigger → commandId → handleCommand.
        val commandId = keyBindingManager.resolve(InputTrigger.fromKeyEvent(event), DomainSurface.PLAYER)
        if (commandId != null) {
            Timber.v("PlayerKeyboardHandler: commandId=%s type=%s", commandId, currentType)
            return handleCommand(commandId)
        }

        // Scan-code fallback: KEYCODE_UNKNOWN events have effectiveKeyCode from scan-code table.
        // The resolver cannot find them because event.keyCode is still KEYCODE_UNKNOWN.
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN && effectiveKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
            val fixedCommandId = scanCodeToCommandId(effectiveKeyCode) ?: return false
            return handleCommand(fixedCommandId)
        }

        return false
    }

    /**
     * Dispatches a resolved [CommandId] to the appropriate callback, applying
     * media-type-aware routing for navigation commands (PDF / EPUB / TEXT).
     *
     * Seek steps preserve pre-migration values: seek_forward_5s → 10 s,
     * seek_forward_30s → 60 s (legacy step preserved for Phase 03).
     */
    internal fun handleCommand(commandId: String): Boolean {
        val currentType = callback.getCurrentMediaType()
        return when (commandId) {
            CommandId.PAUSE_PLAY, CommandId.PLAY, CommandId.PAUSE -> { handlePlayPause(); true }
            CommandId.STOP -> {
                val t = callback.getCurrentMediaType()
                if (t == MediaType.VIDEO || t == MediaType.AUDIO) {
                    callback.getActivePlayer()?.let { if (it.isPlaying) it.pause() }
                }
                true
            }
            CommandId.SEEK_FORWARD_5S -> { callback.onSeekForward(SEEK_INCREMENT_SECONDS); true }
            CommandId.SEEK_BACKWARD_5S -> { callback.onSeekBackward(SEEK_INCREMENT_SECONDS); true }
            CommandId.SEEK_FORWARD_30S -> { callback.onSeekForward(60); true }
            CommandId.SEEK_BACKWARD_30S -> { callback.onSeekBackward(60); true }
            CommandId.FRAME_STEP_FORWARD, CommandId.FRAME_STEP_BACKWARD -> false
            CommandId.NEXT_FILE -> when (currentType) {
                MediaType.PDF -> { callback.onPdfNextPage(); true }
                MediaType.TEXT, MediaType.EPUB -> false
                else -> { callback.onNextFile(); true }
            }
            CommandId.PREVIOUS_FILE -> when (currentType) {
                MediaType.PDF -> { callback.onPdfPreviousPage(); true }
                MediaType.TEXT, MediaType.EPUB -> false
                else -> { callback.onPreviousFile(); true }
            }
            CommandId.JUMP_START -> when (currentType) {
                MediaType.PDF -> { callback.onPdfHome(); true }
                MediaType.EPUB -> { callback.onEpubHome(); true }
                MediaType.TEXT -> { callback.onTextHome(); true }
                else -> false
            }
            CommandId.JUMP_END -> when (currentType) {
                MediaType.PDF -> { callback.onPdfEnd(); true }
                MediaType.EPUB -> { callback.onEpubEnd(); true }
                MediaType.TEXT -> { callback.onTextEnd(); true }
                else -> false
            }
            CommandId.FULLSCREEN -> { callback.onToggleFullscreen(); true }
            CommandId.VOLUME_UP -> { callback.onChangeVolume(+1); true }
            CommandId.VOLUME_DOWN -> { callback.onChangeVolume(-1); true }
            CommandId.MUTE -> { callback.onToggleMute(); true }
            CommandId.TOGGLE_CONTROLS -> { callback.onToggleCommandPanel(); true }
            CommandId.TOGGLE_INFO -> { callback.onShowFileInfo(); true }
            CommandId.EXIT -> { callback.onExitPlayer(); true }
            CommandId.SHOW_HELP -> { callback.onShowHelp(); true }
            CommandId.SEARCH -> if (supportsDocumentSearch(currentType)) {
                callback.onDocumentSearch(); true
            } else {
                false
            }
            CommandId.UNDO -> { callback.onUndoOperation(); true }
            CommandId.REDO -> false   // no redo in player
            CommandId.MOVE -> if (callback.canMoveCurrent()) {
                callback.onToggleMovePanel(); true
            } else false
            CommandId.COPY -> if (callback.canCopyCurrent()) {
                callback.onToggleCopyPanel(); true
            } else false
            CommandId.DELETE -> { callback.onDeleteFile(); true }
            CommandId.RENAME -> { callback.onShowRenameDialog(); true }
            CommandId.FAVOURITE -> { callback.onToggleFavourite(); true }
            CommandId.FILE_OPS -> { callback.onShowContextMenu(); true }
            CommandId.SAVE -> { callback.onSaveCurrent(); true }
            CommandId.BLACK_SCREEN -> { callback.onToggleBlackScreen(); true }
            CommandId.ROTATION_TOGGLE -> { callback.onToggleRotationSensor(); true }
            else -> false
        }
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

    // ── scan-code table (KEYCODE_UNKNOWN fixup) ──────────────────────────────

    private fun scanCodeToCommandId(effectiveKeyCode: Int): String? = when (effectiveKeyCode) {
        KeyEvent.KEYCODE_PAGE_UP -> CommandId.PREVIOUS_FILE
        KeyEvent.KEYCODE_PAGE_DOWN -> CommandId.NEXT_FILE
        KeyEvent.KEYCODE_MOVE_HOME -> CommandId.JUMP_START
        KeyEvent.KEYCODE_MOVE_END -> CommandId.JUMP_END
        else -> null
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
}
