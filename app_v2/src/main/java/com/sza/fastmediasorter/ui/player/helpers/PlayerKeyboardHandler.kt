package com.sza.fastmediasorter.ui.player.helpers

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.media3.common.Player
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.domain.input.CommandId
import com.sza.fastmediasorter.domain.input.InputTrigger
import com.sza.fastmediasorter.domain.input.fromKeyEvent
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.common.MouseEventHandler
import com.sza.fastmediasorter.utils.UserActionLogger
import timber.log.Timber
import com.sza.fastmediasorter.domain.input.InputSurface as DomainSurface

/** Zoom actions shared by the still and video surfaces (MEDIA-VIEWER rules 3.1-3.2). */
enum class ZoomAction { IN, OUT, FIT, ACTUAL_SIZE }

/**
 * Keyboard and mouse-wheel handler for [PlayerActivity] and [StandalonePlayerActivity].
 * Single host-agnostic parser - each host supplies its own [PlayerKeyboardCallback].
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
    /** True while an editable field owns focus: no key may then reach a viewer action (VIEWER-KEYBOARD). */
    var textInputFocused: () -> Boolean = { false },
) {
    /** Debounce window for media button events to prevent double-trigger (Activity + MediaSession). */
    private var lastMediaButtonTimeMs = 0L

    private companion object {
        const val MEDIA_BUTTON_DEBOUNCE_MS = 300L
        const val SEEK_INCREMENT_SECONDS = 10
        const val SHORT_SEEK_SECONDS = 3
        const val STEP_TEN = 10
        const val STEP_HUNDRED = 100

        val KEYBOARD_ARROW_COMMANDS = setOf(
            CommandId.NEXT_OR_SEEK_FORWARD,
            CommandId.PREVIOUS_OR_SEEK_BACKWARD,
            CommandId.STEP_FORWARD_TEN,
            CommandId.STEP_BACK_TEN,
        )
        val DPAD_DIRECTIONS = setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
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
        /** Mouse-wheel scroll on EPUB - positive = scroll up, negative = scroll down. */
        fun onEpubScrollDelta(verticalScroll: Float)
        /** Mouse-wheel scroll on non-document media - delegate to navigation manager. */
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

        /** Move to global destination [slot] (1..10, digits); false when the host has no route. */
        fun onMoveToSlot(slot: Int): Boolean = false

        /** Copy to global destination [slot] (Shift+digits); false when the host has no route. */
        fun onCopyToSlot(slot: Int): Boolean = false

        /** Apply a zoom action on the current still or video (MEDIA-VIEWER rules 3.1-3.2). */
        fun onZoom(action: ZoomAction): Boolean = false

        /** Ctrl+wheel zoom by [factor] around ([x], [y]); consuming the event means it never pages. */
        fun onZoomAt(factor: Float, x: Float, y: Float): Boolean = false
        fun onToggleBlackScreen() {}
        // S0162: toggle player-level rotation sensor (no-op when the player follows the OS - guard in ViewModel)
        fun onToggleRotationSensor() {}
        // ── VIEWER-KEYBOARD actions (INPUT-PARITY 0.4); false = this host has no route ──

        fun onStepFiles(offset: Int): Boolean = false
        fun onEdgeFile(last: Boolean): Boolean = false
        fun onRandomFile(): Boolean = false
        fun onRotateContent(clockwise: Boolean): Boolean = false
        fun onChangeSpeed(faster: Boolean): Boolean = false
        fun onCycleAudioTrack(): Boolean = false
        fun onStartSlideshow(random: Boolean): Boolean = false
        fun onShowFileGrid(): Boolean = false
        fun onShowFileList(): Boolean = false
        fun onShowFileNumber(): Boolean = false
        fun hasModalInput(): Boolean = false
        fun onFrameStep(forward: Boolean): Boolean = false
        fun onToggleAbLoop(): Boolean = false
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
        if (textInputFocused() || callback.hasModalInput()) return false

        // Primary path: resolve trigger → commandId → handleCommand.
        val commandId = keyBindingManager.resolve(InputTrigger.fromKeyEvent(event), DomainSurface.PLAYER)
        if (commandId != null) {
            Timber.v("PlayerKeyboardHandler: commandId=%s type=%s", commandId, currentType)
            return dispatchResolved(commandId, effectiveKeyCode, event, currentType)
        }

        // Scan-code fallback: KEYCODE_UNKNOWN events have effectiveKeyCode from scan-code table.
        // The resolver cannot find them because event.keyCode is still KEYCODE_UNKNOWN.
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN && effectiveKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
            val fixedCommandId = scanCodeToCommandId(effectiveKeyCode) ?: return false
            return handleCommand(fixedCommandId)
        }

        // A bare D-pad direction resolves to no player command, so it falls through here and the
        // platform performs focus traversal among the visible transport controls.
        return false
    }

    /**
     * Dispatches a resolved [CommandId] to the appropriate callback, applying
     * media-type-aware routing for navigation commands (PDF / EPUB / TEXT).
     *
     * The seek_*_5s and seek_*_30s ids are frozen storage keys for stored remaps;
     * their steps are 10 s and 60 s (VIEWER-KEYBOARD media keys and Ctrl+arrows).
     */
    // Digit keys address global destination slots (MEDIA-VIEWER rule 4.1): op slots move, copy slots copy.
    private val moveSlotCommands: Map<String, Int> = mapOf(
        CommandId.OPERATION_SLOT_1 to 1,
        CommandId.OPERATION_SLOT_2 to 2,
        CommandId.OPERATION_SLOT_3 to 3,
        CommandId.OPERATION_SLOT_4 to 4,
        CommandId.OPERATION_SLOT_5 to 5,
        CommandId.OPERATION_SLOT_6 to 6,
        CommandId.OPERATION_SLOT_7 to 7,
        CommandId.OPERATION_SLOT_8 to 8,
        CommandId.OPERATION_SLOT_9 to 9,
        CommandId.OPERATION_SLOT_10 to 10,
    )

    private val copySlotCommands: Map<String, Int> = mapOf(
        CommandId.COPY_SLOT_1 to 1,
        CommandId.COPY_SLOT_2 to 2,
        CommandId.COPY_SLOT_3 to 3,
        CommandId.COPY_SLOT_4 to 4,
        CommandId.COPY_SLOT_5 to 5,
        CommandId.COPY_SLOT_6 to 6,
        CommandId.COPY_SLOT_7 to 7,
        CommandId.COPY_SLOT_8 to 8,
        CommandId.COPY_SLOT_9 to 9,
        CommandId.COPY_SLOT_10 to 10,
    )

    private fun dispatchResolved(commandId: String, keyCode: Int, event: KeyEvent, type: MediaType?): Boolean = when {
        // A TV remote sends the same D-pad codes as keyboard arrows; it keeps focus traversal.
        commandId in KEYBOARD_ARROW_COMMANDS && keyCode in DPAD_DIRECTIONS && !isFromAlphabeticKeyboard(event) -> false
        // VIEWER-KEYBOARD: Space advances where the view is not playable; K and the media key do not.
        commandId == CommandId.PAUSE_PLAY && keyCode == KeyEvent.KEYCODE_SPACE && !isPlayable(type) ->
            handleCommand(CommandId.NEXT_FILE)
        else -> handleCommand(commandId)
    }

    // An event with no device (injected by a test harness) counts as a keyboard.
    private fun isFromAlphabeticKeyboard(event: KeyEvent): Boolean =
        event.device?.keyboardType?.let { it == InputDevice.KEYBOARD_TYPE_ALPHABETIC } ?: true

    private fun isPlayable(type: MediaType?) = type == MediaType.VIDEO || type == MediaType.AUDIO

    private fun isDocument(type: MediaType?) = when (type) {
        MediaType.PDF, MediaType.TEXT, MediaType.EPUB, MediaType.OFFICE_DOCUMENT -> true
        else -> false
    }

    /** VIEWER-KEYBOARD commands (INPUT-PARITY 0.4); null = not one of them. Navigation half here, view half below. */
    private fun handleViewerKeyCommand(commandId: String, type: MediaType?): Boolean? = when (commandId) {
        CommandId.NEXT_OR_SEEK_FORWARD ->
            if (isPlayable(type)) seek(SEEK_INCREMENT_SECONDS) else handleCommand(CommandId.NEXT_FILE)
        CommandId.PREVIOUS_OR_SEEK_BACKWARD ->
            if (isPlayable(type)) seek(-SEEK_INCREMENT_SECONDS) else handleCommand(CommandId.PREVIOUS_FILE)
        CommandId.SEEK_FORWARD_3S -> seek(SHORT_SEEK_SECONDS)
        CommandId.SEEK_BACKWARD_3S -> seek(-SHORT_SEEK_SECONDS)
        CommandId.STEP_FORWARD_TEN -> !isDocument(type) && callback.onStepFiles(STEP_TEN)
        CommandId.STEP_BACK_TEN -> !isDocument(type) && callback.onStepFiles(-STEP_TEN)
        CommandId.STEP_FORWARD_HUNDRED -> !isDocument(type) && callback.onStepFiles(STEP_HUNDRED)
        CommandId.STEP_BACK_HUNDRED -> !isDocument(type) && callback.onStepFiles(-STEP_HUNDRED)
        CommandId.RANDOM_FILE -> !isDocument(type) && callback.onRandomFile()

        else -> handleViewerViewCommand(commandId, type)
    }

    private fun handleViewerViewCommand(commandId: String, type: MediaType?): Boolean? = when (commandId) {
        CommandId.FILE_GRID -> callback.onShowFileGrid()
        CommandId.FILE_LIST -> callback.onShowFileList()
        CommandId.FILE_NUMBER -> callback.onShowFileNumber()
        CommandId.ROTATE -> callback.onRotateContent(clockwise = true)
        CommandId.ROTATE_COUNTER -> callback.onRotateContent(clockwise = false)
        CommandId.SPEED_UP -> isPlayable(type) && callback.onChangeSpeed(faster = true)
        CommandId.SPEED_DOWN -> isPlayable(type) && callback.onChangeSpeed(faster = false)
        CommandId.CYCLE_TRACK -> type == MediaType.VIDEO && callback.onCycleAudioTrack()
        CommandId.SLIDESHOW_START -> callback.onStartSlideshow(random = false)
        CommandId.SLIDESHOW_RANDOM -> callback.onStartSlideshow(random = true)
        else -> handleVideoCommand(commandId, type)
    }

    private fun handleVideoCommand(commandId: String, type: MediaType?): Boolean? = when (commandId) {
        CommandId.FRAME_STEP_FORWARD -> type == MediaType.VIDEO && callback.onFrameStep(forward = true)
        CommandId.FRAME_STEP_BACKWARD -> type == MediaType.VIDEO && callback.onFrameStep(forward = false)
        CommandId.AB_LOOP -> type == MediaType.VIDEO && callback.onToggleAbLoop()
        else -> null
    }

    private fun seek(seconds: Int): Boolean {
        if (seconds >= 0) callback.onSeekForward(seconds) else callback.onSeekBackward(-seconds)
        return true
    }

    internal fun handleCommand(commandId: String): Boolean {
        if (callback.hasModalInput()) return false
        val currentType = callback.getCurrentMediaType()
        return handleViewerKeyCommand(commandId, currentType) ?: when (commandId) {
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
                MediaType.OFFICE_DOCUMENT -> false
                else -> callback.onEdgeFile(last = false)
            }
            CommandId.JUMP_END -> when (currentType) {
                MediaType.PDF -> { callback.onPdfEnd(); true }
                MediaType.EPUB -> { callback.onEpubEnd(); true }
                MediaType.TEXT -> { callback.onTextEnd(); true }
                MediaType.OFFICE_DOCUMENT -> false
                else -> callback.onEdgeFile(last = true)
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
            CommandId.ZOOM_IN -> zoomCommand(ZoomAction.IN, currentType)
            CommandId.ZOOM_OUT -> zoomCommand(ZoomAction.OUT, currentType)
            CommandId.FIT_SCREEN, CommandId.ZOOM_RESET -> zoomCommand(ZoomAction.FIT, currentType)
            CommandId.ZOOM_ACTUAL_SIZE -> zoomCommand(ZoomAction.ACTUAL_SIZE, currentType)
            else -> slotCommand(commandId)
        }
    }

    // MV-3.1/3.2: zoom keys exist for stills and video; documents keep paging (strategic non-goals).
    private fun zoomCommand(action: ZoomAction, type: MediaType?): Boolean =
        if (supportsZoom(type)) callback.onZoom(action) else false

    /** Slots need the operation to be available on the current resource, mirroring the Move/Copy panel shortcuts. */
    private fun slotCommand(commandId: String): Boolean =
        moveSlotCommands[commandId]?.let { callback.canMoveCurrent() && callback.onMoveToSlot(it) }
            ?: copySlotCommands[commandId]?.let { callback.canCopyCurrent() && callback.onCopyToSlot(it) }
            ?: false

    // ── pointer / wheel input ────────────────────────────────────────────────

    fun handlePointerEvent(view: View, event: MotionEvent?): Boolean {
        if (event == null) return false
        // MV-3.7: with Ctrl held, the wheel zooms the still or video and is consumed here, so the
        // activity level can never page with it; without Ctrl the mouse handler pages as before.
        val isZoomWheel = event.actionMasked == MotionEvent.ACTION_SCROLL &&
            isCtrlPressed(event) &&
            supportsZoom(callback.getCurrentMediaType())
        if (isZoomWheel) {
            val scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (scroll != 0f) {
                val factor = if (scroll > 0f) ViewerZoomMath.STEP else 1f / ViewerZoomMath.STEP
                return callback.onZoomAt(factor, event.x, event.y)
            }
        }
        if (mouseHandler.handleMotionEvent(view, event)) return true
        if (mouseHandler.handleGenericMotionEvent(view, event)) return true
        return false
    }

    private fun isCtrlPressed(event: MotionEvent): Boolean =
        event.metaState and KeyEvent.META_CTRL_ON != 0

    private fun supportsZoom(type: MediaType?): Boolean =
        type == MediaType.IMAGE || type == MediaType.GIF || type == MediaType.VIDEO

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
            MediaType.OFFICE_DOCUMENT -> {
                // S0301 Phase 05: the embedded Office WebView consumes wheel scrolling itself, so
                // the player must not hijack it for file navigation.
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
