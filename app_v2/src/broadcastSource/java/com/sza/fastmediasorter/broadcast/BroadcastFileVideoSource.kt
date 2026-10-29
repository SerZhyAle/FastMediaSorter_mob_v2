package com.sza.fastmediasorter.broadcast

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import com.pedro.encoder.input.sources.OrientationConfig
import com.pedro.encoder.input.sources.OrientationForced
import com.pedro.encoder.input.sources.video.VideoSource
import timber.log.Timber

/**
 * Decodes the first video track of a picked file onto the stream's surface, one frame at its own
 * presentation time. The library's file decoder stamps every sample with the moment it was read,
 * in decode order; a file with B-frames comes out of the codec in display order carrying those
 * stamps backwards and in bursts, and the GL fps limiter drops most of them before the encoder
 * (S4300 measured a 25 fps film reaching the receiver at 10). Here each sample keeps its own time and each
 * output buffer is held until that time is due, so the surface sees the film's own cadence.
 *
 * [open] reads the track before the stream is prepared, because the encoder needs its size and
 * rate; the library then calls [create], [start] with its surface texture, and [stop]/[release].
 * The pump runs on its own thread; a finished track and a decoder that dies both reach [onFinished].
 */
class BroadcastFileVideoSource(
    context: Context,
    uri: Uri,
    private val onFinished: () -> Unit,
) : VideoSource() {

    private val appContext = context.applicationContext
    private val fileUri = uri
    private val extractor = MediaExtractor()
    private var trackFormat: MediaFormat? = null

    @Volatile
    private var running = false
    private var pump: Thread? = null
    private var surface: Surface? = null

    var trackWidth: Int = 0
        private set
    var trackHeight: Int = 0
        private set
    var trackFps: Int = DEFAULT_FPS
        private set

    /** False when the file carries no video track, or cannot be read at all. */
    @Suppress("TooGenericExceptionCaught")
    fun open(): Boolean {
        return try {
            extractor.setDataSource(appContext, fileUri, null)
            val index = videoTrackIndex() ?: return false
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            trackWidth = format.getInteger(MediaFormat.KEY_WIDTH)
            trackHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
            trackFps = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                format.getInteger(MediaFormat.KEY_FRAME_RATE).takeIf { it > 0 } ?: DEFAULT_FPS
            } else {
                DEFAULT_FPS
            }
            trackFormat = format
            true
        } catch (e: Exception) {
            Timber.w(e, "BroadcastFileVideoSource: no decodable video track in %s", fileUri)
            false
        }
    }

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean = trackFormat != null

    // A file has no sensor: the frame is drawn as stored, like the library's own file source.
    override fun getOrientationConfig(): OrientationConfig =
        OrientationConfig(forced = OrientationForced.LANDSCAPE)

    override fun start(surfaceTexture: SurfaceTexture) {
        this.surfaceTexture = surfaceTexture
        val format = trackFormat
        val mime = format?.getString(MediaFormat.KEY_MIME)
        if (running || format == null || mime == null) return
        val output = Surface(surfaceTexture)
        val decoder = startDecoder(format, mime, output)
        if (decoder == null) {
            output.release()
        } else {
            surface = output
            running = true
            pump = Thread({ runPump(decoder) }, "BroadcastFileVideoPump").also { it.start() }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun startDecoder(format: MediaFormat, mime: String, output: Surface): MediaCodec? = try {
        MediaCodec.createDecoderByType(mime).also {
            it.configure(format, output, null, 0)
            it.start()
        }
    } catch (e: Exception) {
        Timber.w(e, "BroadcastFileVideoSource: codec for %s did not start", mime)
        null
    }

    override fun stop() {
        running = false
        val thread = pump
        pump = null
        if (thread != null && thread !== Thread.currentThread()) thread.join(STOP_JOIN_MS)
        surface?.release()
        surface = null
    }

    @Suppress("TooGenericExceptionCaught")
    override fun release() {
        stop()
        try {
            extractor.release()
        } catch (e: Exception) {
            Timber.d("BroadcastFileVideoSource: extractor already released - ${e.message}")
        }
    }

    override fun isRunning(): Boolean = running

    /**
     * Feeds samples and releases due frames until the track ends, the codec throws or [stop] lands. The codec is
     * owned by this thread alone, so it is stopped and released here and nowhere else.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun runPump(decoder: MediaCodec) {
        val pacer = FramePacer()
        val info = MediaCodec.BufferInfo()
        var inputEosSent = false
        var reachedEnd = false
        try {
            while (running && !reachedEnd) {
                if (!inputEosSent) inputEosSent = feedInput(decoder)
                val outIndex = decoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                if (outIndex >= 0) {
                    reachedEnd = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val render = info.size > 0 && pacer.awaitDue(info.presentationTimeUs) { running }
                    decoder.releaseOutputBuffer(outIndex, render)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "BroadcastFileVideoSource: decoder stopped mid-session")
        } finally {
            releaseCodec(decoder)
        }
        // stop() clears running before it joins this thread, so a flag still set here means the
        // pump ended on its own - a track end or a codec throw - and the session must end with it.
        if (reachedEnd || running) {
            running = false
            onFinished()
        }
    }

    /** Queues one encoded sample at its own time; true once the end-of-stream flag is queued. */
    private fun feedInput(decoder: MediaCodec): Boolean {
        val inIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        val buffer = if (inIndex >= 0) decoder.getInputBuffer(inIndex) else null
        val size = if (buffer != null) extractor.readSampleData(buffer, 0) else NO_BUFFER
        val endOfTrack = buffer != null && size < 0
        if (endOfTrack) {
            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        } else if (buffer != null) {
            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
        return endOfTrack
    }

    @Suppress("TooGenericExceptionCaught")
    private fun releaseCodec(decoder: MediaCodec) {
        try {
            decoder.stop()
        } catch (e: Exception) {
            Timber.d("BroadcastFileVideoSource: decoder already stopped - ${e.message}")
        }
        decoder.release()
    }

    private fun videoTrackIndex(): Int? {
        var index = 0
        while (index < extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) return index
            index++
        }
        return null
    }

    /** Anchors the first frame to the wall clock and holds every later one until its offset is due. */
    private class FramePacer {
        private var firstPtsUs = NO_PTS_US
        private var startedAtElapsedMs = 0L

        /** False when [isActive] turned false during the wait. */
        fun awaitDue(ptsUs: Long, isActive: () -> Boolean): Boolean {
            if (firstPtsUs == NO_PTS_US) {
                firstPtsUs = ptsUs
                startedAtElapsedMs = SystemClock.elapsedRealtime()
                return true
            }
            val dueMs = startedAtElapsedMs + (ptsUs - firstPtsUs) / US_PER_MS
            var due = false
            while (isActive() && !due) {
                val waitMs = dueMs - SystemClock.elapsedRealtime()
                due = waitMs <= 0
                if (!due) SystemClock.sleep(waitMs.coerceAtMost(PACING_SLICE_MS))
            }
            return due
        }
    }

    private companion object {
        const val DEFAULT_FPS = 30
        const val NO_PTS_US = Long.MIN_VALUE
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val PACING_SLICE_MS = 100L
        const val US_PER_MS = 1000L
        const val STOP_JOIN_MS = 500L

        /** A sample size no read returns, standing for "no input buffer this round". */
        const val NO_BUFFER = 0
    }
}
