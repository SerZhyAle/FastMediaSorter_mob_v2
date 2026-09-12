package com.sza.fastmediasorter.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.screencapture.gesture.DeviceActionHandler
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * S2776: where a tap on the shade shortcut lands.
 *
 * A receiver rather than the [com.sza.fastmediasorter.ui.flashlight.FlashlightToggleActivity]
 * trampoline the other surfaces use (strategic ADR-2): a notification needs no window, and the same
 * pass that flips the torch can repaint the entry.
 */
@AndroidEntryPoint
class FlashlightShortcutReceiver : BroadcastReceiver() {

    @Inject
    lateinit var deviceActionHandler: DeviceActionHandler

    @Inject
    lateinit var notifier: FlashlightShortcutNotifier

    override fun onReceive(context: Context, intent: Intent) {
        // No super.onReceive: BroadcastReceiver declares it abstract, and the Hilt Gradle plugin's
        // bytecode transform is what injects the fields below - the same shape every other
        // @AndroidEntryPoint receiver in this module uses.
        if (intent.action != ACTION_TOGGLE) {
            return
        }
        deviceActionHandler.setTorch(context, !deviceActionHandler.isTorchOn)
        // Re-drawn whatever the hardware answered, so a device that refused shows the dark state
        // rather than a lit icon over an unlit flash.
        notifier.refresh()
    }

    companion object {
        private const val ACTION_TOGGLE = "com.sza.fastmediasorter.action.FLASHLIGHT_SHORTCUT_TOGGLE"

        /** The explicit intent the notification's tap target carries. */
        fun toggleIntent(context: Context): Intent =
            Intent(context, FlashlightShortcutReceiver::class.java).setAction(ACTION_TOGGLE)
    }
}
