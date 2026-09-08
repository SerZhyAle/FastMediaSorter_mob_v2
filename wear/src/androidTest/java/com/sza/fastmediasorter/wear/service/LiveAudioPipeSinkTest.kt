package com.sza.fastmediasorter.wear.service

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sza.fastmediasorter.wear.service.helpers.LiveAudioPipeSink
import com.sza.fastmediasorter.wear.service.helpers.VoiceRecordingSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * S2550 strategic §6.2, now asserted against the SHIPPED sink rather than a throwaway probe: does
 * [LiveAudioPipeSink], fed by [VoiceRecordingSessionManager]'s live configuration, deliver a live and
 * continuously readable ADTS stream, so the watch can serve its microphone over the LAN instead of
 * writing a file and sending it after the fact?
 *
 * [mpeg4IntoTheSink_isTheControlAndMustNotWalk] is a deliberate control: MPEG_4 seeks back to patch
 * its index and is expected to fail here. Without it, a pass in the ADTS case cannot be told apart
 * from a harness that would pass anything.
 *
 * The session manager is driven through its two `configure*` companion functions rather than through
 * an instance, because standing up its Hilt graph would put a repository, a publisher and a use case
 * between this test and the one property it exists to measure.
 */
@RunWith(AndroidJUnit4::class)
class LiveAudioPipeSinkTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun plantLogging() {
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
    }

    /**
     * The test grants its own microphone permission rather than relying on the host having done it.
     *
     * Measured 2026-09-05: a run that reinstalled the test APK reset the runtime grant, every case
     * fell through the assumption, and the report came back `tests="3" skipped="3"` under a green
     * BUILD SUCCESSFUL - three cases that observed nothing, reported as a pass. `UiAutomation` is
     * used instead of `GrantPermissionRule` because that rule lives in `androidx.test:rules`, which
     * this module does not depend on, and a self-grant is not worth a new dependency.
     */
    @Before
    fun grantMicrophonePermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            android.Manifest.permission.RECORD_AUDIO
        )
    }

    @Test
    fun adtsIntoTheSink_streamsWalkableFramesWhileStillRecording() {
        requireMicrophonePermission()
        val observed = captureThroughSink(MediaRecorder.OutputFormat.AAC_ADTS)

        assertNull("prepare()/start() threw for AAC_ADTS into the shipped sink", observed.startFailure)
        assertNull("stop() threw for AAC_ADTS into the shipped sink", observed.stopFailure)
        assertTrue(
            "No bytes reached the listener before stop(); the sink is not live. Observed=$observed",
            observed.bytesBeforeStop > 0
        )
        assertTrue(
            "First byte took ${observed.firstByteMillis} ms, beyond the $MAX_FIRST_BYTE_MILLIS ms " +
                "live-stream budget. Observed=$observed",
            observed.firstByteMillis in 1..MAX_FIRST_BYTE_MILLIS
        )
        assertTrue(
            "ADTS frame chain did not walk cleanly: ${observed.adtsFrames} frames over " +
                "${observed.totalBytes} bytes. Observed=$observed",
            observed.adtsFrames >= MIN_EXPECTED_FRAMES
        )
        assertEquals(
            "ADTS frame chain did not consume the whole capture, so the bytes the listener got are " +
                "not a clean frame sequence. Observed=$observed",
            observed.totalBytes,
            observed.adtsWalkedBytes
        )
        assertEquals(
            "Captured stream did not decode as AAC. Observed=$observed",
            "audio/mp4a-latm",
            extractedMimeType(observed.received)
        )
    }

    @Test
    fun mpeg4IntoTheSink_isTheControlAndMustNotWalk() {
        requireMicrophonePermission()
        val observed = captureThroughSink(MediaRecorder.OutputFormat.MPEG_4)

        val walked = observed.startFailure == null &&
            observed.stopFailure == null &&
            observed.adtsFrames >= MIN_EXPECTED_FRAMES &&
            observed.totalBytes == observed.adtsWalkedBytes
        assertTrue(
            "CONTROL FAILED: MPEG_4 into the non-seekable sink produced a clean walkable frame " +
                "chain. That is not possible for a container that patches its index at stop(), so " +
                "this harness cannot tell a working sink from a broken one and the ADTS result " +
                "above proves nothing. Observed=$observed",
            !walked
        )
    }

    /**
     * Pins the property ADR-7 rests on: a listener that walks away costs itself the rest of the
     * stream and nothing else. The bytes it already received stay a clean ADTS chain, and - unlike
     * the raw-pipe measurement that produced strategic §6.2 - `stop()` no longer throws, because the
     * sink drops the listener instead of closing the pipe behind it.
     */
    @Test
    fun sinkSurvivesTheListenerWalkingAway() {
        requireMicrophonePermission()
        val observed = captureThroughSink(
            outputFormat = MediaRecorder.OutputFormat.AAC_ADTS,
            detachAfterMillis = DETACH_AFTER_MILLIS
        )

        assertNull(
            "start() threw when the listener was going to be detached. Observed=$observed",
            observed.startFailure
        )
        assertNull(
            "stop() threw after the listener was detached. The sink is supposed to keep draining " +
                "the pipe so a departing listener cannot reach the recorder. Observed=$observed",
            observed.stopFailure
        )
        assertTrue(
            "No bytes arrived before the listener was detached. Observed=$observed",
            observed.bytesBeforeStop > 0
        )
        assertEquals(
            "Bytes delivered before the detach were not a clean ADTS chain, so a departing " +
                "listener corrupts what it already received. Observed=$observed",
            observed.totalBytes,
            observed.adtsWalkedBytes
        )
    }

    /**
     * Deliberately an assertion and not an assumption. This class exists to measure one platform
     * behaviour, so a run that cannot reach the microphone has not proved the behaviour absent - it
     * has proved nothing, and reporting that as a skip inside a green build is how the 2026-09-05
     * `skipped="3"` run happened.
     */
    private fun requireMicrophonePermission() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        assertTrue(
            "RECORD_AUDIO is not granted and the self-grant in @Before did not take, so this run " +
                "would measure nothing. Grant it by hand and re-run: " +
                "adb shell pm grant <wear-package> android.permission.RECORD_AUDIO",
            granted
        )
    }

    /**
     * Runs one capture through the shipped sink and reports what the attached listener actually saw.
     *
     * `detachAfterMillis` above zero detaches the listener mid-capture, which is the disconnect case.
     */
    @Suppress("LongMethod")
    private fun captureThroughSink(
        outputFormat: Int,
        detachAfterMillis: Long = 0L
    ): SinkObservation = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val sink = LiveAudioPipeSink()
        val received = ByteArrayOutputStream()
        val firstByteAt = AtomicLong(0L)
        val startedAt = SystemClock.elapsedRealtime()
        var listenerJob: Job? = null

        var startFailure: String? = null
        var stopFailure: String? = null
        var bytesBeforeStop = 0
        val recorder = newRecorder()
        try {
            val descriptor = sink.open(scope)
            configureFor(outputFormat, recorder, descriptor)
            listenerJob = scope.launch {
                sink.readInto(TimestampingStream(received, firstByteAt))
            }
            recorder.prepare()
            recorder.start()
        } catch (e: CancellationException) {
            // First arm on purpose: CancellationException extends IllegalStateException, so the
            // arms below would otherwise swallow the cancellation of the scope this runs in.
            throw e
        } catch (e: IOException) {
            startFailure = describe(e)
        } catch (e: IllegalStateException) {
            startFailure = describe(e)
        } catch (e: RuntimeException) {
            startFailure = describe(e)
        }

        if (startFailure == null) {
            if (detachAfterMillis > 0L) {
                SystemClock.sleep(detachAfterMillis)
                // S2509 split the old single-listener detach(): this case drives the whole session
                // away, which is now detachAll() rather than the per-listener form.
                sink.detachAll()
                SystemClock.sleep(RECORD_MILLIS - detachAfterMillis)
            } else {
                SystemClock.sleep(RECORD_MILLIS)
            }
            bytesBeforeStop = synchronized(received) { received.size() }
            try {
                recorder.stop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                stopFailure = describe(e)
            } catch (e: RuntimeException) {
                stopFailure = describe(e)
            }
        }
        recorder.release()
        // Order matters and is the invariant under test: the recorder is done before the pipe closes.
        sink.close()
        listenerJob?.cancel()
        scope.cancel()

        val captured = synchronized(received) { received.toByteArray() }
        val walk = walkAdtsFrames(captured)
        SinkObservation(
            startFailure = startFailure,
            stopFailure = stopFailure,
            firstByteMillis = if (firstByteAt.get() == 0L) -1 else firstByteAt.get() - startedAt,
            bytesBeforeStop = bytesBeforeStop,
            totalBytes = captured.size,
            adtsFrames = walk.frames,
            adtsWalkedBytes = walk.consumedBytes,
            received = captured
        )
    }

    /**
     * The live path is the shipped one verbatim. The control reuses the shipped voice-note settings
     * and only redirects them at the pipe, so the two runs differ by container alone.
     */
    private fun configureFor(
        outputFormat: Int,
        recorder: MediaRecorder,
        descriptor: android.os.ParcelFileDescriptor
    ) {
        if (outputFormat == MediaRecorder.OutputFormat.AAC_ADTS) {
            VoiceRecordingSessionManager.configureLiveStream(recorder, descriptor)
        } else {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(AUDIO_CHANNELS_MONO)
            recorder.setAudioSamplingRate(AUDIO_SAMPLING_RATE_HZ)
            recorder.setAudioEncodingBitRate(AUDIO_BIT_RATE)
            recorder.setOutputFile(descriptor.fileDescriptor)
        }
    }

    private fun describe(e: Throwable): String = "${e.javaClass.simpleName}: ${e.message}"

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        MediaRecorder()
    }

    /** `MediaExtractor` needs a path, so the bytes the listener collected are staged in the cache. */
    private fun extractedMimeType(captured: ByteArray): String? {
        if (captured.isEmpty()) {
            return null
        }
        val staged = File(context.cacheDir, "live-sink-capture.aac")
        staged.writeBytes(captured)
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(staged.absolutePath)
            if (extractor.trackCount == 0) {
                null
            } else {
                extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME)
            }
        } catch (e: IOException) {
            logProbe("MediaExtractor refused the capture: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            logProbe("MediaExtractor rejected the capture: ${e.message}")
            null
        } finally {
            extractor.release()
            staged.delete()
        }
    }

    /**
     * Walks the ADTS frame chain: each frame declares its own length, so a stream that is genuinely a
     * clean sequence of self-framing frames consumes exactly to its end. Counting bare sync words
     * would accept a corrupt stream that happens to contain the pattern.
     */
    private fun walkAdtsFrames(bytes: ByteArray): AdtsWalk {
        var offset = 0
        var frames = 0
        var walking = true
        while (walking && offset + ADTS_HEADER_BYTES <= bytes.size) {
            val frameLength = adtsFrameLengthAt(bytes, offset)
            if (frameLength == null || offset + frameLength > bytes.size) {
                walking = false
            } else {
                offset += frameLength
                frames++
            }
        }
        return AdtsWalk(frames = frames, consumedBytes = offset)
    }

    /** Null when [offset] carries no ADTS sync word, or declares a frame shorter than its header. */
    private fun adtsFrameLengthAt(bytes: ByteArray, offset: Int): Int? {
        val first = bytes[offset].toInt() and BYTE_MASK
        val second = bytes[offset + 1].toInt() and BYTE_MASK
        val isSync = first == SYNC_FIRST_BYTE && (second and SYNC_SECOND_MASK) == SYNC_SECOND_VALUE
        val declared = if (isSync) declaredFrameLength(bytes, offset) else 0
        return if (declared >= ADTS_HEADER_BYTES) declared else null
    }

    private fun declaredFrameLength(bytes: ByteArray, offset: Int): Int {
        val third = bytes[offset + LENGTH_BYTE_HIGH].toInt() and BYTE_MASK
        val fourth = bytes[offset + LENGTH_BYTE_MID].toInt() and BYTE_MASK
        val fifth = bytes[offset + LENGTH_BYTE_LOW].toInt() and BYTE_MASK
        return ((third and LENGTH_HIGH_MASK) shl LENGTH_HIGH_SHIFT) or
            (fourth shl LENGTH_MID_SHIFT) or
            (fifth shr LENGTH_LOW_SHIFT)
    }

    /**
     * The ticket id deliberately does not appear in this message. A permanent `Timber.i` carrying an
     * `Sxxxx` token is refused by `assert-no-ticket-logs.ps1`: that prefix is reserved for temporary
     * `Timber.d` probes tied to `BlockNeedUserTest`, and a permanent log wearing it would be swept
     * away with them.
     */
    private fun logProbe(message: String) {
        Timber.i("Live audio sink test: %s", message)
    }

    /** Stamps the arrival of the first byte, which is the measure of whether the sink is live. */
    private class TimestampingStream(
        private val target: ByteArrayOutputStream,
        private val firstByteAt: AtomicLong
    ) : OutputStream() {

        override fun write(b: Int) {
            firstByteAt.compareAndSet(0L, SystemClock.elapsedRealtime())
            synchronized(target) { target.write(b) }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            firstByteAt.compareAndSet(0L, SystemClock.elapsedRealtime())
            synchronized(target) { target.write(b, off, len) }
        }
    }

    private data class AdtsWalk(val frames: Int, val consumedBytes: Int)

    private data class SinkObservation(
        val startFailure: String?,
        val stopFailure: String?,
        val firstByteMillis: Long,
        val bytesBeforeStop: Int,
        val totalBytes: Int,
        val adtsFrames: Int,
        val adtsWalkedBytes: Int,
        val received: ByteArray
    ) {
        override fun toString(): String = "start=$startFailure stop=$stopFailure " +
            "firstByteMs=$firstByteMillis bytesBeforeStop=$bytesBeforeStop total=$totalBytes " +
            "adtsFrames=$adtsFrames adtsWalked=$adtsWalkedBytes"
    }

    private companion object {
        const val AUDIO_CHANNELS_MONO = 1
        const val AUDIO_SAMPLING_RATE_HZ = 44_100
        const val AUDIO_BIT_RATE = 64_000
        const val RECORD_MILLIS = 3_000L
        const val DETACH_AFTER_MILLIS = 1_000L
        const val MAX_FIRST_BYTE_MILLIS = 2_000L
        const val MIN_EXPECTED_FRAMES = 10
        const val ADTS_HEADER_BYTES = 7
        const val BYTE_MASK = 0xFF
        const val SYNC_FIRST_BYTE = 0xFF
        const val SYNC_SECOND_MASK = 0xF6
        const val SYNC_SECOND_VALUE = 0xF0
        const val LENGTH_BYTE_HIGH = 3
        const val LENGTH_BYTE_MID = 4
        const val LENGTH_BYTE_LOW = 5
        const val LENGTH_HIGH_MASK = 0x03
        const val LENGTH_HIGH_SHIFT = 11
        const val LENGTH_MID_SHIFT = 3
        const val LENGTH_LOW_SHIFT = 5
    }
}
