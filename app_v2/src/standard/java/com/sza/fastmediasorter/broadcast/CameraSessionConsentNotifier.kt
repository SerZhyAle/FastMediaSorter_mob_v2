package com.sza.fastmediasorter.broadcast

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2551: asks the owner, on the phone, whether the watch may open this phone's camera.
 *
 * A notification rather than a direct launch, and the reason is the same one `OpenOnPhoneNotifier`
 * records: the request arrives in a background `WearableListenerService`, and a background process has
 * not been allowed to start an activity since Android 10. The tap is also what `research/05` needs -
 * a camera-typed foreground service cannot be created while the app is invisible, and a notification
 * the user acted on is the one exemption the platform grants.
 *
 * Reached through [CameraSessionConsentPrompt] so the policy above it can be judged without a device:
 * the boolean this class reports is the whole of what that policy branches on.
 */
@Singleton
class CameraSessionConsentNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : CameraSessionConsentPrompt {

    override fun ask(requestId: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Timber.i("Camera session: POST_NOTIFICATIONS not granted, the owner cannot be asked")
            return false
        }
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.camera_session_consent_notification_title))
            .setContentText(context.getString(R.string.camera_session_consent_notification_text))
            .setContentIntent(allowPendingIntent(requestId))
            .addAction(
                NotificationIcons.STATUS_BAR,
                context.getString(R.string.camera_session_consent_allow),
                allowPendingIntent(requestId)
            )
            .setAutoCancel(true)
            .build()
        return runCatching { manager.notify(NotificationIds.WEAR_CAMERA_SESSION_CONSENT, notification) }
            .onFailure {
                // Revoked between the check above and the post; the watch is told the same thing it
                // would have been told had the check itself failed.
                Timber.i(it, "Camera session: notification suppressed - POST_NOTIFICATIONS not granted")
            }
            .isSuccess
    }

    private fun allowPendingIntent(requestId: String): PendingIntent {
        val allow = Intent(context, CameraSessionConsentActivity::class.java).apply {
            putExtra(CameraSessionConsentActivity.EXTRA_REQUEST_ID, requestId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            // Keeps one session's pending grant from reusing the previous session's intent, which
            // would grant the wrong request id and leave the current one to expire.
            requestId.hashCode(),
            allow,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.camera_session_consent_channel_name),
                // DEFAULT rather than LOW: someone is holding the watch waiting for this phone, and a
                // silent entry in the shade would read on the watch as the request having failed.
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private companion object {
        const val CHANNEL_ID = "wear_camera_session_consent"
    }
}
