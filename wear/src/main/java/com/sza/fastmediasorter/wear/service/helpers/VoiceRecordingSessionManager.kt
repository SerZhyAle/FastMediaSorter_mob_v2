package com.sza.fastmediasorter.wear.service.helpers

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.sza.fastmediasorter.wear.data.recorder.VoiceNoteFileFactory
import com.sza.fastmediasorter.wear.data.recorder.VoiceNotePublisher
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendPolicy
import com.sza.fastmediasorter.wear.domain.recorder.VoiceRecordingErrorReason
import com.sza.fastmediasorter.wear.domain.recorder.VoiceRecordingState
import com.sza.fastmediasorter.wear.domain.recorder.VoiceRecordingStateHolder
import com.sza.fastmediasorter.wear.domain.repository.VoiceNoteRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.SendVoiceNoteUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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

/**
 * S2430: the microphone session itself, extracted from `VoiceRecordingService` (CLAUDE.md rule 3).
 *
 * Owns the recorder, the error classification around opening and closing it, the decision to publish
 * and to send, and the elapsed ticker. What stayed in the service is the Android lifecycle and the
 * ongoing notification; the two points where this sequence has to reach back into it - raising the
 * sending notification, ending the service - are [Callbacks].
 *
 * Unscoped, so one instance belongs to one service instance and the session fields below need no
 * ownership rules beyond the dispatcher the service attaches.
 */
class VoiceRecordingSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VoiceNoteRepository,
    private val publisher: VoiceNotePublisher,
    private val fileFactory: VoiceNoteFileFactory,
    private val stateHolder: VoiceRecordingStateHolder,
    private val preferences: WearPreferencesRepository,
    private val sendVoiceNoteUseCase: SendVoiceNoteUseCase,
    /**
     * S2550: exposed because the LAN server serving the stream needs THIS session's pipe, and an
     * unscoped injection at the server's own call site would hand it a different, unopened one.
     */
    val liveSink: LiveAudioPipeSink
) {

    /** What this manager cannot do itself: it holds no `Service` and posts no notification. */
    interface Callbacks {
        /** The recording is over and the automatic transfer has begun. */
        fun onSendingStarted()

        /** The session ended, on every path including failure - nothing more will happen. */
        fun onSessionFinished()
    }

    /** S2550: which of the two things the shipped microphone session can be doing. */
    enum class Mode {
        /** The shipped voice note: MPEG_4 into a real file, published to MediaStore and sent. */
        VOICE_NOTE,

        /** ADR-3: live ADTS onto [liveSink]. Nothing is stored, published or sent. */
        LIVE_STREAM
    }

    private var scope: CoroutineScope? = null
    private var callbacks: Callbacks? = null
    private var recorder: MediaRecorder? = null
    private var targetFile: File? = null
    private var mode = Mode.VOICE_NOTE
    private var startedAtMillis = 0L
    private var startedAtElapsed = 0L
    private var tickerJob: Job? = null

    /** True while a microphone session is open; a second start over one of those is ignored. */
    val isSessionOpen: Boolean
        get() = recorder != null

    /**
     * The scope belongs to the service, not to this manager: the ticker must die with the service, and
     * the service is what the platform tears down.
     */
    fun attach(scope: CoroutineScope, callbacks: Callbacks) {
        this.scope = scope
        this.callbacks = callbacks
    }

    suspend fun begin(mode: Mode) {
        this.mode = mode
        when (mode) {
            Mode.VOICE_NOTE -> beginVoiceNote()
            Mode.LIVE_STREAM -> beginLiveStream()
        }
    }

    private suspend fun beginVoiceNote() {
        if (!repository.hasRoomToRecord()) {
            fail(VoiceRecordingErrorReason.NO_FREE_SPACE, cause = null)
            return
        }
        val file = withContext(Dispatchers.IO) { fileFactory.newFile() }
        if (openRecorder { configureVoiceNote(it, file) }) {
            targetFile = file
            markStarted()
        }
    }

    /**
     * No free-space check and no file: a live session writes nothing to disk. The pipe is opened
     * inside the recorder's own try, so a pipe the kernel refuses fails the start the same way an
     * unavailable recorder does rather than by a separate path with its own error state.
     */
    private suspend fun beginLiveStream() {
        val pumpScope = requireNotNull(scope) { "begin() ran before attach()" }
        val opened = openRecorder { recorder ->
            configureLiveStream(recorder, liveSink.open(pumpScope))
        }
        if (opened) {
            markStarted()
        }
    }

    suspend fun stop() {
        val active = recorder
        if (active == null) {
            Timber.i("Ignoring a stop: no microphone session is open")
            callbacks?.onSessionFinished()
            return
        }
        stateHolder.publish(VoiceRecordingState.Finishing)
        tickerJob?.cancel()
        tickerJob = null
        val durationMillis = SystemClock.elapsedRealtime() - startedAtElapsed
        val file = targetFile
        targetFile = null
        val closedCleanly = closeRecorder(active)
        when (mode) {
            Mode.VOICE_NOTE -> finishVoiceNote(closedCleanly, file, durationMillis)
            // A live session has nothing to keep or to discard, and a stop() that threw because the
            // listener had already gone (ADR-7) is the expected end rather than a lost recording.
            Mode.LIVE_STREAM -> stateHolder.publish(VoiceRecordingState.Idle)
        }
        // Last, and on every path: the service exists only for the duration of one session.
        callbacks?.onSessionFinished()
    }

    private suspend fun finishVoiceNote(closedCleanly: Boolean, file: File?, durationMillis: Long) {
        if (closedCleanly && file != null) {
            storeNote(file, durationMillis)
        } else {
            discard(file)
        }
    }

    private fun markStarted() {
        startedAtMillis = System.currentTimeMillis()
        startedAtElapsed = SystemClock.elapsedRealtime()
        stateHolder.publish(VoiceRecordingState.Recording(startedAtMillis, elapsedMillis = 0L))
        startTicker()
    }

    /** Called from the service's `onDestroy`, after its scope is cancelled. */
    fun release() {
        releaseRecorder()
    }

    /**
     * The recorder is published to the field BEFORE it is configured: `prepare` and `start` are the
     * two calls that throw, and a recorder still held only by a local would leak its native session
     * when they do. The field itself is only ever touched on the service's own dispatcher, while
     * the two blocking calls run on IO - `prepare` creates and opens the output file.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun openRecorder(configure: (MediaRecorder) -> Unit): Boolean = try {
        val created = newRecorder()
        recorder = created
        withContext(Dispatchers.IO) {
            configure(created)
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
     * The insert is uncancellable: the service stops itself right after this returns and cancels the
     * attached scope, and a cancelled insert would lose exactly the note ADR-3 promises to keep.
     *
     * The note is stored before it is offered to the transport, and the automatic policy stores it as
     * PENDING rather than sending from a state that claims nothing is owed: if the process dies
     * mid-transfer, the drain finds the note and finishes the job. The send is awaited here, so the
     * service is still alive around it - the microphone is already released by this point, so what
     * stays up is a foreground service holding no hardware.
     */
    private suspend fun storeNote(file: File, durationMillis: Long) {
        val policy = readSendPolicy()
        val initialState = when (policy) {
            VoiceNoteSendPolicy.AUTOMATIC -> VoiceNoteDeliveryState.PENDING
            VoiceNoteSendPolicy.MANUAL -> VoiceNoteDeliveryState.LOCAL_ONLY
        }
        val note = withContext(NonCancellable) {
            val registered = repository.register(file, durationMillis, initialState)
            val publishedUri = publisher.publish(file)
            if (publishedUri != null) {
                repository.updatePublishedAddress(registered.id, publishedUri.toString())
                if (file.exists() && !file.delete()) {
                    Timber.w("Failed to delete working copy %s after publishing", file.name)
                }
            }
            registered
        }
        stateHolder.publish(VoiceRecordingState.Idle)
        if (policy == VoiceNoteSendPolicy.AUTOMATIC) {
            callbacks?.onSendingStarted()
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
        tickerJob = scope?.launch {
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
        callbacks?.onSessionFinished()
    }

    /**
     * The pipe is closed after the recorder and never before it: closing the read end under a
     * running capture is the measured case that makes `stop()` throw (strategic §6.2). Every caller
     * of this function has already finished with the recorder, so the order holds on every path.
     */
    private fun releaseRecorder() {
        recorder?.release()
        recorder = null
        liveSink.close()
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

    /**
     * Both configurations are stateless, so they sit here rather than on an instance: the
     * instrumentation test that proves the live container actually streams drives the shipped
     * settings directly, without standing up this manager's dependency graph.
     */
    companion object {

        /** The shipped voice note. MPEG_4 patches its index at `stop()`, so it needs a real file. */
        fun configureVoiceNote(target: MediaRecorder, file: File) {
            target.setAudioSource(MediaRecorder.AudioSource.MIC)
            target.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            configureEncoder(target)
            target.setOutputFile(file.absolutePath)
        }

        /**
         * ADR-4: self-delimiting ADTS frames, which is what lets a non-seekable sink carry them and
         * what `AacExtractor` on the phone already reads. S3049: uses VOICE_RECOGNITION audio source
         * to enable Wear OS platform voice gain boost and AGC for live streams.
         */
        fun configureLiveStream(target: MediaRecorder, sink: ParcelFileDescriptor) {
            target.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            target.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            configureEncoder(target)
            target.setOutputFile(sink.fileDescriptor)
            Timber.d("S3049: wear live stream configured with VOICE_RECOGNITION audio source")
        }

        /**
         * Called after `setOutputFormat` on both paths, which the recorder's state machine requires
         * and which is also what makes the two modes differ by container alone - the property the
         * test's MPEG_4 control rests on.
         */
        private fun configureEncoder(target: MediaRecorder) {
            target.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            target.setAudioChannels(AUDIO_CHANNELS_MONO)
            target.setAudioSamplingRate(AUDIO_SAMPLING_RATE_HZ)
            target.setAudioEncodingBitRate(AUDIO_BIT_RATE)
        }
    }
}
