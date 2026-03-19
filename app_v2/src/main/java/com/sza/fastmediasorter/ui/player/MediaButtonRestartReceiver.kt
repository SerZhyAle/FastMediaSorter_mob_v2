package com.sza.fastmediasorter.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Phase 5: Hardware media button handler for cold service restart.
 *
 * When AudioPlaybackService is alive, MediaSession handles button events directly.
 * This receiver handles the case where the app was killed (car/headphone scenario)
 * and the user presses Play on an external media controller — restarting the service
 * quietly without requiring user to return to the app.
 *
 * Enabled/disabled via PackageManager.setComponentEnabledSetting() controlled by
 * the "Use as primary media player" toggle in Playback Settings.
 */
class MediaButtonRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        val keyEvent: KeyEvent? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

        if (keyEvent?.action != KeyEvent.ACTION_DOWN) return

        // Restart on PLAY / PAUSE / PLAY_PAUSE / NEXT / PREV — any media control key that
        // implies the user expects the app to be active (e.g. car stereo, Bluetooth headphones).
        val isMediaKey = keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
            || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
            || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
            || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS

        if (!isMediaKey) return

        if (AudioPlaybackService.isRunning) {
            Timber.d("MediaButtonRestartReceiver: service already running, skipping restart")
            return
        }

        Timber.d("MediaButtonRestartReceiver: media key pressed while service dead — restarting AudioPlaybackService")
        // Android 8+ requires a foreground service to post a notification within 5 s.
        // The "quiet restart" guarantee is: no Activity is launched, no toast is shown —
        // only the standard media notification (expected in car / headphone mode).
        val serviceIntent = Intent(context, AudioPlaybackService::class.java)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Timber.e(e, "MediaButtonRestartReceiver: failed to start AudioPlaybackService")
        }
    }
}
