package com.sza.fastmediasorter.core.screencapture.gesture

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import timber.log.Timber
import javax.inject.Inject

/**
 * S1038: runs the volume + media-transport edge-gesture actions (DEVICE picker group) through the
 * system [AudioManager]. Stateless - the dispatcher passes the capture Service context per call and
 * [handle] returns false for actions it does not own so the dispatcher can try the next handler.
 */
class MediaActionHandler @Inject constructor() {

    /** Returns true when [action] is a volume/media action this handler owns (performed or degraded). */
    fun handle(context: Context, action: ScreenshotGestureAction): Boolean {
        if (!isMediaAction(action)) return false
        Timber.d("S1038: media action %s", action)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            Timber.w("MediaActionHandler: AudioManager unavailable, ignoring %s", action)
            return true
        }
        when (action) {
            ScreenshotGestureAction.VOLUME_UP -> adjustVolume(audioManager, AudioManager.ADJUST_RAISE)
            ScreenshotGestureAction.VOLUME_DOWN -> adjustVolume(audioManager, AudioManager.ADJUST_LOWER)
            ScreenshotGestureAction.VOLUME_MUTE -> adjustVolume(audioManager, AudioManager.ADJUST_TOGGLE_MUTE)
            ScreenshotGestureAction.MEDIA_PLAY_PAUSE -> dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ScreenshotGestureAction.MEDIA_NEXT -> dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT)
            ScreenshotGestureAction.MEDIA_PREV -> dispatchMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            else -> Unit
        }
        return true
    }

    // FLAG_SHOW_UI surfaces the system volume slider so the change is visible (there is no app UI here).
    private fun adjustVolume(audioManager: AudioManager, direction: Int) {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    // A media key needs a matching down + up pair to register as a full press with the active session.
    private fun dispatchMediaKey(audioManager: AudioManager, keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun isMediaAction(action: ScreenshotGestureAction): Boolean = when (action) {
        ScreenshotGestureAction.VOLUME_UP,
        ScreenshotGestureAction.VOLUME_DOWN,
        ScreenshotGestureAction.VOLUME_MUTE,
        ScreenshotGestureAction.MEDIA_PLAY_PAUSE,
        ScreenshotGestureAction.MEDIA_NEXT,
        ScreenshotGestureAction.MEDIA_PREV -> true
        else -> false
    }
}
