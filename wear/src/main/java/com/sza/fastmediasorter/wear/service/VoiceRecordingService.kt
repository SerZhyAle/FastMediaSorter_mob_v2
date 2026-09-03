package com.sza.fastmediasorter.wear.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.core.notification.NotificationIcons
import com.sza.fastmediasorter.wear.core.notification.WearNotificationIds
import com.sza.fastmediasorter.wear.service.helpers.VoiceRecordingSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** The typed startForeground overload does not exist below API 29; there the type is simply absent. */
private const val FOREGROUND_SERVICE_TYPE_NONE = 0

/**
 * S1862 / S2161: owner of the microphone session (ADR-4).
 *
 * The screen starts and stops this service by intent and reads its state through an
 * application-scoped flow; it never binds, so a screen going dark cannot end a recording that is
 * still being spoken into. The note is stored the moment the session closes and before any transfer
 * is attempted (ADR-3) - speech is not reproducible, so a lost file is a lost note. S2161 publishes
 * the finished note into MediaStore.Audio before send.
 *
 * S2430: the session itself lives in [VoiceRecordingSessionManager]. What is left here is the part
 * that needs a `Service` - the platform callbacks, the foreground notification and the scope the
 * session runs in.
 */
@AndroidEntryPoint
class VoiceRecordingService : Service() {

    @Inject
    lateinit var sessionManager: VoiceRecordingSessionManager

    /**
     * Main, deliberately: the session state the manager holds is also touched by `onStartCommand` and
     * `onDestroy`, and both of those are delivered on the main thread. One dispatcher for all of them
     * means the state needs no synchronisation; each blocking call inside hops to IO on its own.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val sessionCallbacks = object : VoiceRecordingSessionManager.Callbacks {
        override fun onSendingStarted() = showSendingNotification()

        override fun onSessionFinished() = stopForegroundAndSelf()
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager.attach(serviceScope, sessionCallbacks)
    }

    /** Never bound - see the class KDoc. A binder would put the session back under the screen. */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * START_NOT_STICKY: a service the platform restarts arrives with a null intent and no microphone
     * session, so there is nothing to resume. Restarting it would only raise a recording
     * notification over a recorder that is not recording.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> serviceScope.launch { sessionManager.stop() }
            else -> Timber.w("VoiceRecordingService started with an unknown action: %s", intent?.action)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // The ticker dies before the recorder, so nothing publishes a Recording state over a session
        // that no longer exists.
        serviceScope.cancel()
        sessionManager.release()
        super.onDestroy()
    }

    private fun handleStart() {
        if (sessionManager.isSessionOpen) {
            Timber.i("Ignoring a start: the microphone session is already open")
            return
        }
        Timber.d("S2161: voice recording started")
        val notification = buildNotification(R.string.wear_voice_recorder_notification_title)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType())
        serviceScope.launch { sessionManager.begin() }
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            FOREGROUND_SERVICE_TYPE_NONE
        }

    /**
     * S1862 phase 03: the ongoing notification stays up for the automatic transfer that follows a
     * recording, and the microphone is already released by then - leaving the recording title there
     * would tell the user the watch is still listening while it is only sending. A denied
     * POST_NOTIFICATIONS makes this a no-op; the transfer is unaffected either way.
     */
    private fun showSendingNotification() {
        if (!canPostNotification()) {
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(R.string.wear_voice_recorder_notification_sending_title)
        )
    }

    private fun canPostNotification(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildNotification(@StringRes titleRes: Int): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.wear_voice_recorder_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(titleRes))
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.sza.fastmediasorter.wear.action.START_VOICE_RECORDING"
        const val ACTION_STOP = "com.sza.fastmediasorter.wear.action.STOP_VOICE_RECORDING"

        /**
         * S1961: moved into [WearNotificationIds] once the watch gained a second notification.
         *
         * This used to be a literal justified by the watch shipping nothing else to collide with.
         * That stopped being true when the phone-initiated open began posting its own, and a number
         * repeated in two files is a collision nobody can see until both notifications are live.
         */
        private const val NOTIFICATION_ID = WearNotificationIds.VOICE_RECORDING
        private const val CHANNEL_ID = "wear_voice_recording"

        fun startIntent(context: Context): Intent =
            Intent(context, VoiceRecordingService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, VoiceRecordingService::class.java).setAction(ACTION_STOP)
    }
}
