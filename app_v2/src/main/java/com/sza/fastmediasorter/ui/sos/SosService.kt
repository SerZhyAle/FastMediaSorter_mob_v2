package com.sza.fastmediasorter.ui.sos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.domain.model.sos.SosMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S3216: owns the running distress signal - siren, torch strobe, wake lock and the notification that
 * stops all three.
 *
 * A foreground service rather than work inside [SosActivity] because the signal must survive the screen
 * going off and the activity being destroyed (strategic §6, Resolved): a phone put in a pocket, dropped,
 * or left on a rock still has to be sounding when someone arrives.
 *
 * The safety timeout is not a convenience. Half an hour of continuous torch heats the LED module, and a
 * signal nobody can hear any more because the battery is flat is worse than one that stopped while there
 * was still a phone to call with (strategic §7).
 */
@AndroidEntryPoint
class SosService : Service() {

    @Inject
    lateinit var soundGenerator: SosSoundGenerator

    @Inject
    lateinit var torchManager: SosTorchManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var timeoutJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSignal()
            else -> startSignal(SosMode.fromNameOrDefault(intent?.getStringExtra(EXTRA_MODE)))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Ordered so nothing outlives the service even when it is killed rather than stopped: both
        // halves of the signal reach for device-wide resources - the alarm volume and the torch.
        soundGenerator.stop(this)
        torchManager.stop(this)
        releaseWakeLock()
        activeMode.value = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startSignal(mode: SosMode) {
        createChannel()
        activeMode.value = mode
        startForegroundCompat(mode)
        acquireWakeLock()
        // Re-entered rather than refused when the mode changed: the chips on the screen switch a
        // running signal, and each half is idempotent, so this both starts and reshapes it.
        if (mode.engagesSound) soundGenerator.start(this) else soundGenerator.stop(this)
        if (mode.engagesLight) torchManager.start(this) else torchManager.stop(this)
        scheduleSafetyTimeout()
        Timber.i("SOS signal running in mode %s", mode)
    }

    private fun stopSignal() {
        stopForegroundCompat()
        stopSelf()
    }

    /**
     * Restarted on every mode change rather than armed once, so switching mode never shortens the
     * window the owner has left - the timeout protects the battery, and the battery clock restarts
     * with the load.
     */
    private fun scheduleSafetyTimeout() {
        timeoutJob?.cancel()
        timeoutJob = serviceScope.launch {
            delay(SAFETY_TIMEOUT_MS)
            Timber.w("SOS signal stopped by the safety timeout after %d ms", SAFETY_TIMEOUT_MS)
            stopSignal()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val manager = getSystemService<PowerManager>() ?: return
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            // Bounded by the same span as the signal: an un-timed partial lock left behind by a killed
            // process is a battery drain with nothing on screen to explain it.
            acquire(SAFETY_TIMEOUT_MS + WAKE_LOCK_GRACE_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { held -> if (held.isHeld) held.release() }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService<NotificationManager>()
        // One condition rather than two guards: an absent manager and an existing channel are the same
        // answer here - nothing to create - and detekt allows this function two exits, not three.
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sos_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun startForegroundCompat(mode: SosMode) {
        val stopPending = PendingIntent.getService(
            this,
            0,
            Intent(this, SosService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openPending = PendingIntent.getActivity(
            this,
            0,
            SosActivity.createIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(getString(R.string.sos_title))
            .setContentText(getString(R.string.sos_notification_text, getString(modeLabelOf(mode))))
            .setContentIntent(openPending)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.sos_stop_action), stopPending)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
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

    companion object {

        const val ACTION_START = "com.sza.fastmediasorter.action.SOS_START"
        const val ACTION_STOP = "com.sza.fastmediasorter.action.SOS_STOP"
        const val EXTRA_MODE = "sos_mode"

        /**
         * The mode the signal is running in, or null when nothing is running.
         *
         * Exposed as state rather than queried through a binder because both readers only ever render
         * it: the screen shows which chip is active, and an incoming stop from the watch needs to know
         * whether there is anything to stop.
         */
        private val activeMode = MutableStateFlow<SosMode?>(null)

        val active: StateFlow<SosMode?> = activeMode.asStateFlow()

        /** The string naming [mode] on the screen and in the notification - one wording, one home. */
        fun modeLabelOf(mode: SosMode): Int = when (mode) {
            SosMode.ALL -> R.string.sos_mode_all
            SosMode.SOUND_ONLY -> R.string.sos_mode_sound_only
            SosMode.LIGHT_ONLY -> R.string.sos_mode_light_only
        }

        fun start(context: Context, mode: SosMode) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SosService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_MODE, mode.name),
            )
        }

        fun stop(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SosService::class.java).setAction(ACTION_STOP),
            )
        }

        private const val CHANNEL_ID = "sos_signal"
        private const val NOTIFICATION_ID = NotificationIds.SOS_SIGNAL

        /** Strategic §7: thirty minutes, the upper end of the range the ticket fixed. */
        private const val SAFETY_TIMEOUT_MS = 30L * 60L * 1_000L
        private const val WAKE_LOCK_GRACE_MS = 5L * 1_000L
        private const val WAKE_LOCK_TAG = "FastMediaSorter:sos"
    }
}
