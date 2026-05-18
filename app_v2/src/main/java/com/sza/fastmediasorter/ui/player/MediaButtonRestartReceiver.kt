package com.sza.fastmediasorter.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.domain.input.InputSurface
import com.sza.fastmediasorter.domain.input.InputTrigger
import com.sza.fastmediasorter.domain.input.fromKeyEvent
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Phase 5: Hardware media button handler for cold service restart.
 *
 * When AudioPlaybackService is alive, MediaSession handles button events directly.
 * This receiver handles the case where the app was killed (car/headphone scenario)
 * and the user presses Play on an external media controller - restarting the service
 * quietly without requiring user to return to the app.
 *
 * Enabled/disabled via PackageManager.setComponentEnabledSetting() controlled by
 * the "Use as primary media player" toggle in Playback Settings.
 *
 * R1 migration: instead of a hardcoded KEYCODE_MEDIA_* whitelist, the receiver
 * resolves the key event through [KeyBindingManager]. Any command in the
 * playback or navigation group triggers a service restart - user remapped media
 * buttons are respected automatically.
 */
@AndroidEntryPoint
class MediaButtonRestartReceiver : BroadcastReceiver() {

    @Inject lateinit var keyBindingManager: KeyBindingManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        val keyEvent: KeyEvent? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

        if (keyEvent?.action != KeyEvent.ACTION_DOWN) return

        val trigger = InputTrigger.fromKeyEvent(keyEvent)
        val commandId = keyBindingManager.resolve(trigger, InputSurface.PLAYER)

        // Restart on any command that implies the user expects active playback -
        // playback.* covers play/pause/stop and navigation.* covers next/prev/seek.
        val isPlaybackOrNavigation = commandId != null && (
            commandId.startsWith("playback.") || commandId.startsWith("navigation.")
        )
        if (!isPlaybackOrNavigation) return

        if (AudioPlaybackService.isRunning) {
            Timber.d("MediaButtonRestartReceiver: service already running, skipping restart")
            return
        }

        Timber.d("MediaButtonRestartReceiver: command=%s while service dead - restarting AudioPlaybackService", commandId)
        // Android 8+ requires a foreground service to post a notification within 5 s.
        // The "quiet restart" guarantee: no Activity launched, no toast - standard media notification only.
        val serviceIntent = Intent(context, AudioPlaybackService::class.java)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Timber.e(e, "MediaButtonRestartReceiver: failed to start AudioPlaybackService")
        }
    }
}
