package com.sza.fastmediasorter.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerController
import com.sza.fastmediasorter.domain.model.SftpServerPauseReason
import com.sza.fastmediasorter.domain.model.SftpServerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Keeps the embedded SFTP server alive while the app is in the background, and gives the user a
 * one-tap way to stop it from the shade (strategic spec goal 3): a listening server nobody remembers
 * is both a battery drain and an open door on the network.
 *
 * The server stops with the service - on the stop action, when the app is swiped away from recents,
 * and when Android ends a data-sync foreground service at its daily limit. Only the stop action is the
 * person's "off"; the other two leave the share to resume at the next foreground (S4225,
 * `SftpServerResumeCoordinator`). While it runs, a default-network callback keeps the advertised
 * address current, and the notification follows the controller's state.
 */
@AndroidEntryPoint
class SftpServerService : Service() {

    @Inject
    lateinit var controller: SftpServerController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var stateJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopServer(byUser = true)
            else -> startServer()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopServer()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * The data-sync daily limit. The service is already in the foreground here and a new `startForeground`
     * after the limit is refused, so this path skips the promise [stopServer] pays.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Timber.d("S4225: data-sync daily limit reached, pausing the share")
        endWatching()
        controller.stop(SftpServerPauseReason.TIME_LIMIT)
        removeForeground()
        notify(buildPausedNotification())
        stopSelf()
    }

    override fun onDestroy() {
        endWatching()
        controller.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startServer() {
        createChannel()
        if (hasDataSyncPermission(this)) {
            startForegroundCompat(buildNotification(getString(R.string.sftp_server_notification_starting)))
        } else {
            notify(buildNotification(getString(R.string.sftp_server_notification_starting)))
        }
        watchState()
        watchNetwork()
        serviceScope.launch {
            if (controller.start() !is SftpServerState.Running) stopServer()
        }
    }

    /**
     * When [hasDataSyncPermission] holds, entry points arrive through [Context.startForegroundService]
     * so the foreground promise is paid before the service withdraws. Otherwise it runs as a standard
     * service while the app or launcher is in the foreground (S4263).
     */
    private fun stopServer(byUser: Boolean = false) {
        createChannel()
        if (hasDataSyncPermission(this)) {
            startForegroundCompat(buildNotification(getString(R.string.sftp_server_notification_starting)))
        }
        endWatching()
        if (byUser) controller.stopByUser() else controller.stop()
        if (hasDataSyncPermission(this)) {
            removeForeground()
        } else {
            getSystemService<NotificationManager>()?.cancel(NOTIFICATION_ID)
        }
        stopSelf()
    }

    private fun removeForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    /** A changed or lost address reaches the shade; cancelled before any stop so it cannot re-post a removed one. */
    private fun watchState() {
        if (stateJob != null) return
        stateJob = serviceScope.launch {
            controller.state.collect { state ->
                if (state is SftpServerState.Running) notify(buildNotification(runningText(state)))
            }
        }
    }

    /** The listener binds every interface, so a network change needs a fresh advertisement, never a re-bind. */
    private fun watchNetwork() {
        if (networkCallback != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService<ConnectivityManager>() ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = controller.refreshAddresses()

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                controller.refreshAddresses()

            override fun onLost(network: Network) = controller.refreshAddresses()
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: SecurityException) {
            // A missing network-state grant: the share still runs, its address only goes stale.
            Timber.w(e, "SftpServerService: network changes not followed")
        }
    }

    private fun endWatching() {
        stateJob?.cancel()
        stateJob = null
        val callback = networkCallback ?: return
        networkCallback = null
        try {
            getSystemService<ConnectivityManager>()?.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "SftpServerService: network callback was not registered")
        }
    }

    private fun runningText(state: SftpServerState.Running): String {
        val address = state.addresses.firstOrNull()
            ?: return getString(R.string.sftp_server_notification_no_network)
        return getString(R.string.sftp_server_notification_address, "$address:${state.port}")
    }

    private fun buildNotification(text: String): Notification {
        val stopPending = PendingIntent.getService(
            this,
            0,
            Intent(this, SftpServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(getString(R.string.sftp_server_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.sftp_server_action_stop), stopPending)
            .build()
    }

    /** One ordinary notification: the person can dismiss it, and a tap opens the app, which resumes the share. */
    private fun buildPausedNotification(): Notification {
        val text = getString(R.string.sftp_server_notification_paused_text)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(getString(R.string.sftp_server_notification_paused_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setSilent(true)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return builder.build()
    }

    private fun notify(notification: Notification) {
        getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService<NotificationManager>()
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sftp_server_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val ACTION_START = "com.sza.fastmediasorter.action.SFTP_SERVER_START"
        private const val ACTION_STOP = "com.sza.fastmediasorter.action.SFTP_SERVER_STOP"
        private const val CHANNEL_ID = "sftp_server"
        private const val NOTIFICATION_ID = NotificationIds.SFTP_SERVER

        fun hasDataSyncPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, SftpServerService::class.java).setAction(ACTION_START)
            if (hasDataSyncPermission(context)) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                try {
                    context.startService(intent)
                } catch (e: IllegalStateException) {
                    Timber.w(e, "SftpServerService: startService background limit reached")
                } catch (e: SecurityException) {
                    Timber.w(e, "SftpServerService: startService security error")
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SftpServerService::class.java).setAction(ACTION_STOP)
            if (hasDataSyncPermission(context)) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                try {
                    context.startService(intent)
                } catch (e: IllegalStateException) {
                    Timber.w(e, "SftpServerService: stopService intent limit reached")
                } catch (e: SecurityException) {
                    Timber.w(e, "SftpServerService: stopService security error")
                }
            }
        }
    }
}
