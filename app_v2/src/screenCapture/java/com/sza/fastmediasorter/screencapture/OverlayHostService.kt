package com.sza.fastmediasorter.screencapture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import timber.log.Timber

class OverlayHostService : Service() {

    private lateinit var overlayManager: ScreenGestureOverlayManager
    private var overlayVisible = false

    override fun onCreate() {
        super.onCreate()
        // Application-overlay strip; the gesture launches the MediaProjection consent activity. This
        // is the sole capture path on Play flavors (standard/photos, all API levels) and the API
        // 26..29 fallback on noLegal, where API 30+ uses the silent accessibility path instead.
        overlayManager = ScreenGestureOverlayManager(
            context = this,
            overlayWindowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            onGestureMatched = { launchConsentActivity() }
        )
    }

    private fun launchConsentActivity() {
        val intent = Intent(this, ScreenCaptureConsentActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopOverlayHost()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Timber.w("OverlayHostService: overlay permission missing")
            stopOverlayHost()
            return START_NOT_STICKY
        }

        try {
            if (!overlayVisible) {
                overlayManager.show()
                overlayVisible = true
            }
            startForegroundCompat()
        } catch (e: Exception) {
            Timber.e(e, "OverlayHostService: failed to start overlay host")
            stopOverlayHost()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        hideOverlay()
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun stopOverlayHost() {
        hideOverlay()
        stopForegroundCompat()
        stopSelf()
    }

    private fun hideOverlay() {
        if (!overlayVisible) return
        overlayManager.hide()
        overlayVisible = false
    }

    private fun startForegroundCompat() {
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_screen_capture)
            .setContentTitle(getString(R.string.screen_capture_service_notification_title))
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.screen_capture_service_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_START = "com.sza.fastmediasorter.action.OVERLAY_HOST_START"
        private const val ACTION_STOP = "com.sza.fastmediasorter.action.OVERLAY_HOST_STOP"
        private const val CHANNEL_ID = "screen_capture_overlay_host"
        private const val NOTIFICATION_ID = 0x4054

        fun start(context: Context) {
            val intent = Intent(context, OverlayHostService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayHostService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}
