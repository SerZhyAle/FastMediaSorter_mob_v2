package com.sza.fastmediasorter.ui.player.helpers

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.media3.common.Player
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import timber.log.Timber
import com.sza.fastmediasorter.utils.UserActionLogger

/**
 * Handles keyboard input for PlayerActivity:
 * - Navigation keys (Left/Right, Page Up/Down)
 * - Delete key (Del, Forward Del)
 * - Media controls (Enter, Media Play/Pause)
 * - Function keys (F1-F7) for various actions
 * - Escape key to exit player
 */
class PlayerKeyboardHandler(
    private val viewModel: PlayerViewModel,
    private val callback: PlayerKeyboardCallback
) {
    /** Debounce window for media button events to prevent double-trigger (Activity + MediaSession) */
    private var lastMediaButtonTimeMs = 0L
    private companion object {
        const val MEDIA_BUTTON_DEBOUNCE_MS = 300L
        const val SEEK_INCREMENT_SECONDS = 10
    }
    
    interface PlayerKeyboardCallback {
        fun onDeleteFile()
        fun onExitPlayer()
        fun onToggleSlideshow()
        fun onShowRenameDialog()
        fun onShowFileInfo()
        fun onToggleCommandPanel()
        fun onToggleCopyPanel()
        fun onToggleMovePanel()
        fun onShowEditDialog()
        /** Returns the currently active player: service MediaController (background audio) or Activity ExoPlayer. */
        fun getActivePlayer(): Player?
        fun getCurrentMediaType(): MediaType?
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
        fun onSeekForward(seconds: Int)
        fun onSeekBackward(seconds: Int)
    }
    
    /**
     * Process keyboard event and return true if handled
     */
    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val currentType = callback.getCurrentMediaType()
        
        // Handle KEYCODE_UNKNOWN by checking scanCode (for external keyboards)
        // Standard scan codes: PageUp=104, PageDown=109, Home=102, End=107
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
        
        UserActionLogger.logKey(effectiveKeyCode, event?.action ?: KeyEvent.ACTION_DOWN, KeyEvent.keyCodeToString(effectiveKeyCode), "PlayerKeyboardHandler (type=$currentType)")
        
        when (effectiveKeyCode) {
            // PageUp: PDF/TXT/EPUB - previous page/chapter/scroll, others - previous file
            KeyEvent.KEYCODE_PAGE_UP -> {
                when (currentType) {
                    MediaType.PDF -> {
                        callback.onPdfPreviousPage()
                        return true
                    }
                    MediaType.TEXT, MediaType.EPUB -> {
                        // Let ScrollView/WebView handle scrolling natively
                        return false
                    }
                    else -> {
                        Timber.d("PlayerKeyboardHandler: PageUp key - navigating to previous file")
                        viewModel.previousFile()
                        return true
                    }
                }
            }
            
            // PageDown: PDF/TXT/EPUB - next page/chapter/scroll, others - next file
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                when (currentType) {
                    MediaType.PDF -> {
                        callback.onPdfNextPage()
                        return true
                    }
                    MediaType.TEXT, MediaType.EPUB -> {
                        // Let ScrollView/WebView handle scrolling natively
                        return false
                    }
                    else -> {
                        Timber.d("PlayerKeyboardHandler: PageDown key - navigating to next file")
                        viewModel.nextFile()
                        return true
                    }
                }
            }

            // Home: Go to beginning of document
            KeyEvent.KEYCODE_MOVE_HOME -> {
                when (currentType) {
                    MediaType.PDF -> {
                        callback.onPdfHome()
                        return true
                    }
                    MediaType.EPUB -> {
                        callback.onEpubHome()
                        return true
                    }
                    MediaType.TEXT -> {
                        callback.onTextHome()
                        return true
                    }
                    else -> {}
                }
            }

            // End: Go to end of document
            KeyEvent.KEYCODE_MOVE_END -> {
                when (currentType) {
                    MediaType.PDF -> {
                        callback.onPdfEnd()
                        return true
                    }
                    MediaType.EPUB -> {
                        callback.onEpubEnd()
                        return true
                    }
                    MediaType.TEXT -> {
                        callback.onTextEnd()
                        return true
                    }
                    else -> {}
                }
            }
            
            // Arrow keys: always navigate files
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                Timber.d("PlayerKeyboardHandler: Left arrow key - navigating to previous file")
                viewModel.previousFile()
                return true
            }
            
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                Timber.d("PlayerKeyboardHandler: Right arrow key - navigating to next file")
                viewModel.nextFile()
                return true
            }
            
            // Delete current file
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                callback.onDeleteFile()
                return true
            }
            
            // Media hardware buttons: Play/Pause (headset hook sends HEADSETHOOK instead of MEDIA_PLAY_PAUSE)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (isMediaButtonDebounced(event)) return true
                handlePlayPause()
                return true
            }
            
            // Media hardware button: Play only
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (isMediaButtonDebounced(event)) return true
                handlePlay()
                return true
            }
            
            // Media hardware button: Pause / Stop
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (isMediaButtonDebounced(event)) return true
                handlePause()
                return true
            }
            
            // Media hardware button: Next track
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (isMediaButtonDebounced(event)) return true
                Timber.d("PlayerKeyboardHandler: MEDIA_NEXT - navigating to next file")
                viewModel.nextFile()
                return true
            }
            
            // Media hardware button: Previous track
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (isMediaButtonDebounced(event)) return true
                Timber.d("PlayerKeyboardHandler: MEDIA_PREVIOUS - navigating to previous file")
                viewModel.previousFile()
                return true
            }
            
            // Space bar: universal play/pause toggle (VLC/YouTube convention)
            KeyEvent.KEYCODE_SPACE -> {
                handlePlayPause()
                return true
            }
            
            // D-pad center: play/pause for Android TV / D-pad remotes
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                handlePlayPause()
                return true
            }
            
            // Media Fast Forward / Rewind: seek ±10s (Bluetooth remotes, car head units)
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
            
            // Bracket keys: seek ±10s (MPV/VLC keyboard convention)
            KeyEvent.KEYCODE_RIGHT_BRACKET -> {
                handleSeekForward()
                return true
            }
            
            KeyEvent.KEYCODE_LEFT_BRACKET -> {
                handleSeekBackward()
                return true
            }
            
            // Media Skip Forward/Backward (API 23+ — distinct from MEDIA_NEXT/PREVIOUS on some remotes)
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                if (isMediaButtonDebounced(event)) return true
                Timber.d("PlayerKeyboardHandler: MEDIA_SKIP_FORWARD - navigating to next file")
                viewModel.nextFile()
                return true
            }
            
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                if (isMediaButtonDebounced(event)) return true
                Timber.d("PlayerKeyboardHandler: MEDIA_SKIP_BACKWARD - navigating to previous file")
                viewModel.previousFile()
                return true
            }
            
            // Channel Up/Down: next/previous file (TV remotes)
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                Timber.d("PlayerKeyboardHandler: CHANNEL_UP - navigating to next file")
                viewModel.nextFile()
                return true
            }
            
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                Timber.d("PlayerKeyboardHandler: CHANNEL_DOWN - navigating to previous file")
                viewModel.previousFile()
                return true
            }
            
            // Bookmark key: toggle favorite (some remotes/keyboards)
            KeyEvent.KEYCODE_BOOKMARK -> {
                Timber.d("PlayerKeyboardHandler: BOOKMARK - toggling favorite")
                viewModel.toggleFavorite()
                return true
            }
            
            // Ctrl+Z: undo last operation
            KeyEvent.KEYCODE_Z -> {
                if (event?.isCtrlPressed == true) {
                    Timber.d("PlayerKeyboardHandler: Ctrl+Z - undo last operation")
                    viewModel.undoLastOperation()
                    return true
                }
            }
            
            // Exit player (back to browse)
            KeyEvent.KEYCODE_ESCAPE -> {
                callback.onExitPlayer()
                return true
            }
            
            // F1: Start/stop slideshow
            KeyEvent.KEYCODE_F1 -> {
                callback.onToggleSlideshow()
                return true
            }
            
            // F2: Rename file
            KeyEvent.KEYCODE_F2 -> {
                callback.onShowRenameDialog()
                return true
            }
            
            // F3: Show file info
            KeyEvent.KEYCODE_F3 -> {
                callback.onShowFileInfo()
                return true
            }
            
            // F4: Toggle fullscreen/command panel mode
            KeyEvent.KEYCODE_F4 -> {
                callback.onToggleCommandPanel()
                return true
            }
            
            // F5: Toggle Copy To panel
            KeyEvent.KEYCODE_F5 -> {
                callback.onToggleCopyPanel()
                return true
            }
            
            // F6: Toggle Move To panel
            KeyEvent.KEYCODE_F6 -> {
                callback.onToggleMovePanel()
                return true
            }
            
            // F7: Image edit dialog / Player settings for video
            KeyEvent.KEYCODE_F7 -> {
                callback.onShowEditDialog()
                return true
            }
        }
        
        return false // Not handled
    }
    
    /**
     * Check if a media button event should be ignored due to debounce or long-press repeat.
     * Returns true if the event should be skipped.
     */
    private fun isMediaButtonDebounced(event: KeyEvent?): Boolean {
        // Ignore long-press repeats for media buttons
        if (event != null && event.repeatCount > 0) {
            Timber.d("PlayerKeyboardHandler: Ignoring media button repeat (count=${event.repeatCount})")
            return true
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastMediaButtonTimeMs < MEDIA_BUTTON_DEBOUNCE_MS) {
            Timber.d("PlayerKeyboardHandler: Debouncing media button (${now - lastMediaButtonTimeMs}ms < ${MEDIA_BUTTON_DEBOUNCE_MS}ms)")
            return true
        }
        lastMediaButtonTimeMs = now
        return false
    }
    
    /**
     * Handle Enter/Media Play/Pause for video/audio playback
     */
    private fun handlePlayPause() {
        val currentType = callback.getCurrentMediaType()
        if (currentType == MediaType.VIDEO || currentType == MediaType.AUDIO) {
            callback.getActivePlayer()?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    Timber.d("PlayerKeyboardHandler: Paused playback")
                } else {
                    player.play()
                    Timber.d("PlayerKeyboardHandler: Resumed playback")
                }
            }
        }
    }
    
    /**
     * Handle Media Play button — start playback only (no toggle).
     */
    private fun handlePlay() {
        val currentType = callback.getCurrentMediaType()
        if (currentType == MediaType.VIDEO || currentType == MediaType.AUDIO) {
            callback.getActivePlayer()?.let { player ->
                if (!player.isPlaying) {
                    player.play()
                    Timber.d("PlayerKeyboardHandler: Started playback via MEDIA_PLAY")
                }
            }
        }
    }
    
    /**
     * Handle Media Pause/Stop button — pause playback only (no toggle).
     */
    private fun handlePause() {
        val currentType = callback.getCurrentMediaType()
        if (currentType == MediaType.VIDEO || currentType == MediaType.AUDIO) {
            callback.getActivePlayer()?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    Timber.d("PlayerKeyboardHandler: Paused playback via MEDIA_PAUSE/STOP")
                }
            }
        }
    }
    
    /**
     * Handle seek forward for video/audio (delegates to VideoPlayerManager via callback).
     */
    private fun handleSeekForward() {
        val currentType = callback.getCurrentMediaType()
        if (currentType == MediaType.VIDEO || currentType == MediaType.AUDIO) {
            callback.onSeekForward(SEEK_INCREMENT_SECONDS)
            Timber.d("PlayerKeyboardHandler: Seek forward ${SEEK_INCREMENT_SECONDS}s")
        }
    }
    
    /**
     * Handle seek backward for video/audio (delegates to VideoPlayerManager via callback).
     */
    private fun handleSeekBackward() {
        val currentType = callback.getCurrentMediaType()
        if (currentType == MediaType.VIDEO || currentType == MediaType.AUDIO) {
            callback.onSeekBackward(SEEK_INCREMENT_SECONDS)
            Timber.d("PlayerKeyboardHandler: Seek backward ${SEEK_INCREMENT_SECONDS}s")
        }
    }
    
    /**
     * Handle mouse scroll events for PDF/TXT page navigation
     */
    fun handleGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event?.action != MotionEvent.ACTION_SCROLL) return false
        
        val currentType = callback.getCurrentMediaType()
        val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        
        when (currentType) {
            MediaType.PDF -> {
                if (scrollY > 0) {
                    callback.onPdfPreviousPage()
                } else if (scrollY < 0) {
                    callback.onPdfNextPage()
                }
                return true
            }
            MediaType.TEXT -> {
                if (scrollY > 0) {
                    callback.onTextScrollUp()
                } else if (scrollY < 0) {
                    callback.onTextScrollDown()
                }
                return true
            }
            else -> return false
        }
    }
}
