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
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.core.notification.WearNotificationIds
import com.sza.fastmediasorter.wear.data.recorder.VoiceNoteFileFactory
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendPolicy
import com.sza.fastmediasorter.wear.domain.recorder.VoiceRecordingErrorReason
import com.sza.fastmediasorter.wear.domain.recorder.VoiceRecordingState
import com.sza.fastmediasorter.wear.domain.recorder.VoiceRecordingStateHolder
import com.sza.fastmediasorter.wear.domain.repository.VoiceNoteRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.SendVoiceNoteUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Mono: a note is speech into a watch, and a second channel would only double the bytes to send. */
private const val AUDIO_CHANNELS_MONO = 1

/** Speech-grade AAC. Section 5.4 pins the container and the codec; these two follow from that. */
private const val AUDIO_SAMPLING_RATE_HZ = 44_100
private const val AUDIO_BIT_RATE = 64_000

/** How often the elapsed figure the screen shows is refreshed while the session is open. */
private const val ELAPSED_TICK_MS = 500L

/** The typed startForeground overload does not exist below API 29; there the type is simply absent. */
private const val FOREGROUND_SERVICE_TYPE_NONE = 0

/**
 * S1862: owner of the microphone session (ADR-4).
 *
 * The screen starts and stops this service by intent and reads its state through an
 * application-scoped flow; it never binds, so a screen going dark cannot end a recording that is
 * still being spoken into. The note is stored the moment the session closes and before any transfer
 * is attempted (ADR-3) - speech is not reproducible, so a lost file is a lost note.
 */
@AndroidEntryPoint
class VoiceRecordingService : Service() {

    @Inject
    lateinit var repository: VoiceNoteRepository

    @Inject
    lateinit var fileFactory: VoiceNoteFileFactory

    @Inject
    lateinit var stateHolder: VoiceRecordingStateHolder

    @Inject
    lateinit var preferences: WearPreferencesRepository

    @Inject
    lateinit var sendVoiceNoteUseCase: SendVoiceNoteUseCase

    /**
     * Main, deliberately: every field below is session state that `onStartCommand` and `onDestroy`
     * also touch, and both of those are delivered on the main thread. One dispatcher for all of them
     * means the state needs no synchronisation; each blocking call inside hops to IO on its own.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: MediaRecorder? = null
    private var targetFile: File? = null
    private var startedAtMillis = 0L
    private var startedAtElapsed = 0L
    private var tickerJob: Job? = null

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
            ACTION_STOP -> serviceScope.launch { handleStop() }
            else -> Timber.w("VoiceRecordingService started with an unknown action: %s", intent?.action)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // The ticker dies before the recorder, so nothing publishes a Recording state over a session
        // that no longer exists.
        serviceScope.cancel()
        releaseRecorder()
        super.onDestroy()
    }

    private fun handleStart() {
        Timber.d("S1862: recording start requested")
        if (recorder != null) {
            Timber.i("Ignoring a start: the microphone session is already open")
            return
        }
        val notification = buildNotification(R.string.wear_voice_recorder_notification_title)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType())
        serviceScope.launch { beginSession() }
    }

    private suspend fun beginSession() {
        if (!repository.hasRoomToRecord()) {
            fail(VoiceRecordingErrorReason.NO_FREE_SPACE, cause = null)
            return
        }
        val file = withContext(Dispatchers.IO) { fileFactory.newFile() }
        if (openRecorder(file)) {
            targetFile = file
            startedAtMillis = System.currentTimeMillis()
            startedAtElapsed = SystemClock.elapsedRealtime()
            stateHolder.publish(VoiceRecordingState.Recording(startedAtMillis, elapsedMillis = 0L))
            startTicker()
        }
    }

    /**
     * The recorder is published to the field BEFORE it is configured: `prepare` and `start` are the
     * two calls that throw, and a recorder still held only by a local would leak its native session
     * when they do. The field itself is only ever touched on this service's own dispatcher, while
     * the two blocking calls run on IO - `prepare` creates and opens the output file.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun openRecorder(file: File): Boolean = try {
        val created = newRecorder()
        recorder = created
        withContext(Dispatchers.IO) {
            configure(created, file)
            created.prepare()
            created.start()
        }
        true
    } catch (e: IOException) {
        fail(VoiceRecordingErrorReason.RECORDER_UNAVAILABLE, e)
        false
    } catch (e: SecurityException) {
        fail(VoiceRecordingErrorReason.PERMISSION_DENIED, e)
        false
    } catch (e: RuntimeException) {
        // start() reports a busy microphone as a bare RuntimeException("start failed") and an
        // out-of-order call as IllegalStateException. Neither is distinguishable to the user, and
        // both leave a half-open recorder that fail() releases.
        fail(VoiceRecordingErrorReason.RECORDER_UNAVAILABLE, e)
        false
    }

    private fun configure(target: MediaRecorder, file: File) {
        target.setAudioSource(MediaRecorder.AudioSource.MIC)
        target.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        target.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        target.setAudioChannels(AUDIO_CHANNELS_MONO)
        target.setAudioSamplingRate(AUDIO_SAMPLING_RATE_HZ)
        target.setAudioEncodingBitRate(AUDIO_BIT_RATE)
        target.setOutputFile(file.absolutePath)
    }

    private suspend fun handleStop() {
        val active = recorder
        if (active == null) {
            Timber.i("Ignoring a stop: no microphone session is open")
            stopForegroundAndSelf()
            return
        }
        stateHolder.publish(VoiceRecordingState.Finishing)
        tickerJob?.cancel()
        tickerJob = null
        val durationMillis = SystemClock.elapsedRealtime() - startedAtElapsed
        val file = targetFile
        targetFile = null
        if (closeRecorder(active) && file != null) {
            storeNote(file, durationMillis)
        } else {
            discard(file)
        }
        // Last, and on every path: the service exists only for the duration of one session.
        stopForegroundAndSelf()
    }

    /**
     * `stop` flushes the container to disk, so it is uncancellable as well as off the main thread -
     * a cancelled flush leaves a file that no player can open.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun closeRecorder(active: MediaRecorder): Boolean = try {
        withContext(NonCancellable + Dispatchers.IO) { active.stop() }
        true
    } catch (e: RuntimeException) {
        // stop() throws when the session captured no valid audio - a tap that opened and closed the
        // recorder inside one frame. What it left on disk is an unplayable stub, not a note.
        Timber.i(e, "The microphone session produced no usable audio")
        false
    } finally {
        releaseRecorder()
    }

    /**
     * The insert is uncancellable: [stopForegroundAndSelf] follows it and cancels [serviceScope], and
     * a cancelled insert would lose exactly the note ADR-3 promises to keep.
     *
     * The note is stored before it is offered to the transport, and the automatic policy stores it as
     * PENDING rather than sending from a state that claims nothing is owed: if the process dies
     * mid-transfer, the drain finds the note and finishes the job. The send is awaited here, so the
     * service is still alive around it - the microphone is already released by this point, so what
     * stays up is a foreground service holding no hardware.
     */
    private suspend fun storeNote(file: File, durationMillis: Long) {
        val policy = readSendPolicy()
        Timber.d("S1862: note stored, send policy is %s", policy)
        val initialState = when (policy) {
            VoiceNoteSendPolicy.AUTOMATIC -> VoiceNoteDeliveryState.PENDING
            VoiceNoteSendPolicy.MANUAL -> VoiceNoteDeliveryState.LOCAL_ONLY
        }
        val note = withContext(NonCancellable) {
            repository.register(file, durationMillis, initialState)
        }
        stateHolder.publish(VoiceRecordingState.Idle)
        if (policy == VoiceNoteSendPolicy.AUTOMATIC) {
            showSendingNotification()
            val result = sendVoiceNoteUseCase(note.id)
            Timber.i("Voice note %s ended as %s", note.fileName, result)
        }
    }

    /**
     * A store that cannot be read must not cost the note. The shipped default is what an untouched
     * watch would have used anyway, so falling back to it changes nothing for anyone but the one
     * user whose preferences file is broken - and they keep their recording.
     */
    private suspend fun readSendPolicy(): VoiceNoteSendPolicy = try {
        preferences.voiceNoteSendPolicy.first()
    } catch (e: IOException) {
        Timber.w(e, "Could not read the voice-note send policy; using the default")
        VoiceNoteSendPolicy.AUTOMATIC
    }

    private suspend fun discard(file: File?) {
        val removed = withContext(NonCancellable + Dispatchers.IO) {
            file == null || !file.exists() || file.delete()
        }
        if (!removed) {
            Timber.w("Failed to remove the unusable recording %s", file?.name)
        }
        stateHolder.publish(VoiceRecordingState.Error(VoiceRecordingErrorReason.NOTHING_RECORDED))
    }

    private fun startTicker() {
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(ELAPSED_TICK_MS)
                val elapsed = SystemClock.elapsedRealtime() - startedAtElapsed
                stateHolder.publish(VoiceRecordingState.Recording(startedAtMillis, elapsed))
            }
        }
    }

    private fun fail(reason: VoiceRecordingErrorReason, cause: Throwable?) {
        Timber.w(cause, "Voice recording could not start: %s", reason)
        releaseRecorder()
        targetFile = null
        stateHolder.publish(VoiceRecordingState.Error(reason))
        stopForegroundAndSelf()
    }

    private fun releaseRecorder() {
        recorder?.release()
        recorder = null
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
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
            .setSmallIcon(R.drawable.ic_voice_note)
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
