package com.sza.fastmediasorter.screencapture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.core.screencapture.ScreenshotGestureActionDispatcher
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class OverlayHostService : Service() {

    @Inject
    lateinit var actionDispatcher: Lazy<ScreenshotGestureActionDispatcher>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var overlayManager: ScreenGestureOverlayManager
    private var overlayVisible = false

    override fun onCreate() {
        super.onCreate()
        // Application-overlay strip; the gesture launches the MediaProjection consent activity. This
        // overlay launcher is shared by the standard MediaProjection consent path (S0621) and the
        // noLegal MediaProjection fallback. On noLegal API 30+ the silent accessibility path hosts the
        // strip instead through its own service; that accessibility path stays a noLegal exclusive.
        overlayManager = ScreenGestureOverlayManager(
            context = this,
            overlayWindowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            onGestureMatched = { zone, direction -> launchConsentActivity(zone, direction) }
        )
    }

    private fun launchConsentActivity(
        zone: ScreenshotGestureZone,
        direction: ScreenshotGestureDirection
    ) {
        serviceScope.launch {
            val action = actionDispatcher.get().actionFor(zone, direction)
            if (actionDispatcher.get().handlePreCaptureAction(this@OverlayHostService, action, zone, direction)) {
                // Pre-capture action (DO_NOT_USE disabled, or OPEN_APP launched the app): skip consent + capture.
                return@launch
            }
            val intent = Intent(this@OverlayHostService, ScreenCaptureConsentActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(ScreenCaptureConsentActivity.EXTRA_GESTURE_DIRECTION, direction.name)
                putExtra(ScreenCaptureConsentActivity.EXTRA_GESTURE_ZONE, zone.name)
            }
            startActivity(intent)
        }
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

        startForegroundCompat()
        // S0847/S1008: rebuild the enabled bands on every (re-)start so a zone toggle or strip-visibility
        // change is reflected. Enabled + strip-visible zones are read off the persisted settings; the whole
        // build stays on Main.
        serviceScope.launch {
            try {
                val enabledZones = actionDispatcher.get().enabledZones()
                val stripVisibleZones = actionDispatcher.get().stripVisibleZones()
                // S1162: resolve the slot actions here, where suspending is allowed - the hint has to
                // render synchronously on touch-down.
                val zoneActions = actionDispatcher.get().actionsForZones(enabledZones)
                overlayManager.hide()
                overlayManager.show(stripVisibleZones, enabledZones, zoneActions)
                overlayVisible = true
                Timber.d("S1162: overlay host resolved actions for ${zoneActions.size} zone(s)")
            } catch (e: Exception) {
                Timber.e(e, "OverlayHostService: failed to start overlay host")
                stopOverlayHost()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        hideOverlay()
        stopForegroundCompat()
        serviceScope.cancel()
        super.onDestroy()
    }

    // S1048: a Service still receives onConfigurationChanged on rotation even without a manifest
    // configChanges declaration (that flag only matters for Activities); without this override the
    // bands kept their pre-rotation WindowManager coordinates, drifting off the physical edge.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!overlayVisible) return
        overlayManager.relayout()
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
        private const val NOTIFICATION_ID = NotificationIds.GESTURE_OVERLAY_HOST

        // S1008: enabled + strip-visible zones are resolved inside onStartCommand off the persisted
        // settings, so a plain (re-)start refreshes both the band set and the per-zone strip colour.
        fun start(context: Context) {
            val intent = Intent(context, OverlayHostService::class.java).apply {
                action = ACTION_START
            }
            // Invariant: the sole caller runs in a foreground context (ScreenGestureOverlayStartupCoordinator
            // .restoreIfNeeded via ProcessLifecycleOwner.onStart), so the Android-15 visible-overlay FGS-start
            // rule holds. The catch is a defensive backstop only, in case a background caller is ever added.
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                Timber.w("OverlayHostService: FGS start not allowed (no visible overlay / background) - skipping")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayHostService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}
