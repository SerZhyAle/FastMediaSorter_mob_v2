package com.sza.fastmediasorter.core.save

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.domain.model.SaveFallbackReason
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single user-notification surface for an "unavailable destination -> saved to a local default
 * folder" fallback. Foreground flows (in-screen save actions) get a Toast; background flows
 * (gesture screenshot, worker-driven download) get a system notification. Silent for
 * [SaveFallbackReason.NoResourceConfigured] - the user never chose a destination, so a redirect
 * needs no explanation.
 */
@Singleton
class SaveFallbackNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationId = AtomicInteger(BASE_NOTIFICATION_ID)

    /** Routes to foreground/background by [background]; no-op for the silent reason. */
    fun notify(
        reason: SaveFallbackReason,
        folderLabel: String,
        resourceName: String,
        background: Boolean,
    ) {
        if (reason == SaveFallbackReason.NoResourceConfigured) return
        if (background) {
            notifyBackground(folderLabel, resourceName)
        } else {
            notifyForeground(folderLabel, resourceName)
        }
    }

    fun notifyForeground(folderLabel: String, resourceName: String) {
        val text = context.getString(R.string.save_fallback_resource_unavailable, folderLabel, resourceName)
        mainHandler.post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    fun notifyBackground(folderLabel: String, resourceName: String) {
        val text = context.getString(R.string.save_fallback_resource_unavailable, folderLabel, resourceName)
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId.incrementAndGet(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted (API 33+): the file is already saved, so the missing
            // notice is a degraded-but-safe outcome, not an error.
            Timber.i(e, "SaveFallbackNotifier: notification suppressed - POST_NOTIFICATIONS not granted")
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.save_fallback_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private companion object {
        const val CHANNEL_ID = "save_fallback"
        const val BASE_NOTIFICATION_ID = NotificationIds.SAVE_FALLBACK_BASE
    }
}
