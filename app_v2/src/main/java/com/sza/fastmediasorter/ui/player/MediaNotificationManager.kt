package com.sza.fastmediasorter.ui.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import timber.log.Timber

/**
 * Manages media notification for audio background playback.
 *
 * Uses Media3 DefaultMediaNotificationProvider with custom notification channel.
 * Handles notification channel creation for Android 8+ (API 26+).
 *
 * Media3 MediaSessionService automatically:
 * - Posts foreground notification when playback starts
 * - Updates notification with play/pause, skip controls
 * - Shows lock screen controls via MediaSession
 * - Handles Bluetooth/headset button events
 */
@UnstableApi
object MediaNotificationManager {

    const val NOTIFICATION_CHANNEL_ID = "fastmediasorter_audio_playback"
    const val NOTIFICATION_ID = NotificationIds.MEDIA_PLAYBACK

    /**
     * Creates the notification channel for audio playback (Android 8+).
     * Must be called before any notification is posted.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_channel_audio_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_audio_description)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Timber.d("MediaNotificationManager: notification channel created")
        }
    }

    /**
     * Creates a configured MediaNotification.Provider for the MediaSessionService.
     * Customizes the default provider with our notification channel.
     */
    fun createNotificationProvider(context: Context): MediaNotification.Provider {
        val provider = DefaultMediaNotificationProvider.Builder(context)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setNotificationId(NOTIFICATION_ID)
            .build()

        provider.setSmallIcon(NotificationIcons.STATUS_BAR)
        Timber.d("S1399: Media3 playback notification provider configured with the branded status-bar icon")

        return provider
    }
}
