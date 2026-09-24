package com.sza.fastmediasorter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerController
import com.sza.fastmediasorter.domain.model.SftpServerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * and when Android ends a data-sync foreground service at its daily limit.
 */
@AndroidEntryPoint
class SftpServerService : Service() {

    @Inject
    lateinit var controller: SftpServerController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("S3041: service command %s", intent?.action)
        when (intent?.action) {
            ACTION_STOP -> stopServer()
            else -> startServer()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopServer()
        super.onTaskRemoved(rootIntent)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopServer()
    }

    override fun onDestroy() {
        controller.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startServer() {
        createChannel()
        startForegroundCompat(buildNotification(getString(R.string.sftp_server_notification_starting)))
        serviceScope.launch {
            when (val result = controller.start()) {
                is SftpServerState.Running -> notify(buildNotification(runningText(result)))
                else -> stopServer()
            }
        }
    }

    /**
     * Every entry point arrives through [Context.startForegroundService], the stop one included, so the
     * foreground promise is paid before the service withdraws - the same race `SosService` documents.
     */
    private fun stopServer() {
        createChannel()
        startForegroundCompat(buildNotification(getString(R.string.sftp_server_notification_starting)))
        controller.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
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

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SftpServerService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SftpServerService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
