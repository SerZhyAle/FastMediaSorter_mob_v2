package com.sza.fastmediasorter.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.screencapture.gesture.DeviceActionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2776: owns the permanent shade entry that lights and extinguishes the camera flash.
 *
 * Two duties only - show the shortcut in a given state, and take it away. Which of the two is called
 * is decided by [FlashlightShortcutCoordinator] from the user's setting; this class never reads a
 * setting itself.
 *
 * The torch subscription is registered with the notification and unregistered with it, so it is a
 * balanced listener rather than the process-lifetime one [DeviceActionHandler] declined to keep. It
 * is what makes the rendered state honest when the flash is switched from the quick-settings tile or
 * by another app.
 */
@Singleton
class FlashlightShortcutNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val deviceActionHandler: DeviceActionHandler,
) {

    private val cameraManager: CameraManager?
        get() = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    /** The torch subscription is live. Says nothing about whether a notification actually got posted. */
    @Volatile
    private var shown = false

    /** A notification is really on screen. False while notifications are switched off for this app. */
    @Volatile
    private var posted = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (shown) {
                render(enabled)
            }
        }
    }

    /** True while the shade entry is really on screen; the caller uses it to tell a teardown from a no-op. */
    val isShown: Boolean get() = posted

    /** False on a device with no flash unit, where the shortcut would toggle nothing. */
    fun hasFlashUnit(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    @Synchronized
    fun show() {
        if (!shown) {
            ensureChannel()
            shown = true
            // An explicit main looper: show() is called from a coroutine, and a null handler would
            // bind the callback to whichever thread happened to make the call.
            cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        }
        render(deviceActionHandler.isTorchOn)
    }

    @Synchronized
    fun hide() {
        if (shown) {
            shown = false
            cameraManager?.unregisterTorchCallback(torchCallback)
        }
        // Cancelled unconditionally: a notification outlives the process that posted it, so a fresh
        // process that finds the setting off has one to take down and no memory of posting it.
        posted = false
        NotificationManagerCompat.from(context).cancel(NotificationIds.FLASHLIGHT_SHORTCUT)
    }

    /** Re-draws the entry in the state the caller believes the torch is in. */
    fun refresh() {
        if (shown) {
            render(deviceActionHandler.isTorchOn)
        }
    }

    private fun render(lit: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            posted = false
            Timber.i("FlashlightShortcutNotifier: shortcut suppressed - notifications are off")
            return
        }
        val bodyRes = if (lit) {
            R.string.flashlight_shortcut_state_on
        } else {
            R.string.flashlight_shortcut_state_off
        }
        val iconRes = if (lit) {
            R.drawable.ic_flashlight_shortcut_on
        } else {
            R.drawable.ic_flashlight_shortcut_off
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.physical_flashlight_title))
            .setContentText(context.getString(bodyRes))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(tapIntent())
        ContextCompat.getDrawable(context, iconRes)?.let {
            builder.setLargeIcon(it.toBitmap(LARGE_ICON_PX, LARGE_ICON_PX))
        }
        try {
            manager.notify(NotificationIds.FLASHLIGHT_SHORTCUT, builder.build())
            posted = true
        } catch (e: SecurityException) {
            posted = false
            // POST_NOTIFICATIONS revoked between the check above and the post: the setting is still
            // on, so the next sync re-posts; nothing here can recover it and nothing is lost.
            Timber.i(e, "FlashlightShortcutNotifier: post refused - POST_NOTIFICATIONS not granted")
        }
    }

    private fun tapIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        NotificationIds.FLASHLIGHT_SHORTCUT,
        FlashlightShortcutReceiver.toggleIntent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.flashlight_shortcut_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private companion object {
        const val CHANNEL_ID = "flashlight_shortcut"

        /** The vector is 24dp; the shade scales its large icon down, never up, so rasterise larger. */
        const val LARGE_ICON_PX = 128
    }
}
