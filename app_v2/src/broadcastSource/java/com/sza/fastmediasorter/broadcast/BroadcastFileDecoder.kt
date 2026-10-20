package com.sza.fastmediasorter.broadcast

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import timber.log.Timber

/**
 * Decodes the first audio track of a picked file to 16-bit PCM and hands it to the sink in real
 * time. A live broadcast has one present: a decoder running flat out would push the whole file
 * through the encoder in seconds while every listener sat at the live edge of a stream that had
 * already ended, so each output buffer is held until its presentation time is due.
 *
 * One instance serves one session, on one thread: [open] reads the track's metadata (the session
 * config needs its rate before the server and the encoder can start), [start] brings the codec up
 * once the sink exists, then [readNext] until it reports false - the end of the file, or [isActive]
 * having turned false - then [release]. The paced waits wake in short slices, so a stop lands
 * within a slice rather than after the file.
 */
class BroadcastFileDecoder(
    context: Context,
    uri: Uri,
) {

    private val appContext = context.applicationContext
    private val fileUri = uri
    private val extractor = MediaExtractor()
    private var codec: MediaCodec? = null
    private var onPcm: ((ByteArray, Int, Long) -> Unit)? = null
    private val outInfo = MediaCodec.BufferInfo()
    private var inputEosSent = false
    private var firstPtsUs = NO_PTS_US
    private var startedAtElapsedMs = 0L

    var sampleRateHz: Int = 0
        private set
    var channelCount: Int = 0
        private set

    private var trackMime: String? = null
    private var trackIndex = -1

    /** False when the file carries no audio track, or cannot be read at all. */
    @Suppress("TooGenericExceptionCaught")
    fun open(): Boolean {
        return try {
            extractor.setDataSource(appContext, fileUri, null)
            val index = audioTrackIndex() ?: return false
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            sampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            trackMime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            trackIndex = index
            true
        } catch (e: Exception) {
            Timber.w(e, "BroadcastFileDecoder: no decodable audio track in %s", fileUri)
            false
        }
    }

    /** False when the device has no decoder for the track the open found. */
    @Suppress("TooGenericExceptionCaught")
    fun start(sink: (ByteArray, Int, Long) -> Unit): Boolean {
        val mime = trackMime
        if (mime == null || trackIndex < 0) return false
        return try {
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(extractor.getTrackFormat(trackIndex), null, null, 0)
            decoder.start()
            codec = decoder
            onPcm = sink
            true
        } catch (e: Exception) {
            Timber.w(e, "BroadcastFileDecoder: codec for %s did not start", mime)
            false
        }
    }

    /**
     * Delivers the next due chunk of PCM to the [start] sink. False means the session should stop
     * serving: the end of the file was reached, or [isActive] returned false first.
     *
     * The returns are the codec pump's own shape - one per dequeue outcome plus the two stop
     * edges - and flattening them into a flag would hide which edge ended the session.
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    fun readNext(isActive: () -> Boolean): Boolean {
        val decoder = codec ?: return false
        val sink = onPcm ?: return false
        feedInput(decoder, isActive)
        while (isActive()) {
            val outIndex = try {
                decoder.dequeueOutputBuffer(outInfo, DEQUEUE_TIMEOUT_US)
            } catch (e: Exception) {
                Timber.w(e, "BroadcastFileDecoder: decoder stopped mid-session")
                return false
            }
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return true
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outIndex >= 0 -> {
                    val eos = outInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (outInfo.size > 0) {
                        if (!awaitDue(outInfo.presentationTimeUs, isActive)) return false
                        val buffer = decoder.getOutputBuffer(outIndex)
                        if (buffer != null) {
                            buffer.position(outInfo.offset)
                            buffer.limit(outInfo.offset + outInfo.size)
                            val pcm = ByteArray(outInfo.size)
                            buffer.get(pcm)
                            sink(pcm, pcm.size, outInfo.presentationTimeUs)
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (eos) return false
                    return true
                }
            }
        }
        return false
    }

    @Suppress("TooGenericExceptionCaught")
    fun release() {
        try {
            codec?.stop()
        } catch (e: Exception) {
            Timber.d("BroadcastFileDecoder: decoder already stopped - ${e.message}")
        }
        codec?.release()
        codec = null
        try {
            extractor.release()
        } catch (e: Exception) {
            Timber.d("BroadcastFileDecoder: extractor already released - ${e.message}")
        }
    }

    private fun audioTrackIndex(): Int? {
        var index = 0
        while (index < extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return index
            index++
        }
        return null
    }

    /** Queues one encoded sample, or the end-of-stream flag once the extractor runs out. */
    private fun feedInput(decoder: MediaCodec, isActive: () -> Boolean) {
        if (inputEosSent || !isActive()) return
        val inIndex = dequeueInputIndex(decoder)
        val buffer = if (inIndex >= 0) decoder.getInputBuffer(inIndex) else null
        if (buffer == null) return
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputEosSent = true
        } else {
            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun dequeueInputIndex(decoder: MediaCodec): Int = try {
        decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
    } catch (e: Exception) {
        Timber.w(e, "BroadcastFileDecoder: decoder stopped mid-session")
        CODEC_GONE
    }

    /**
     * Holds the decoded buffer until its presentation time is due on the wall clock. False when
     * [isActive] turned false during the wait, so the caller stops instead of sleeping on.
     */
    private fun awaitDue(ptsUs: Long, isActive: () -> Boolean): Boolean {
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

    private companion object {
        const val NO_PTS_US = Long.MIN_VALUE
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val PACING_SLICE_MS = 100L
        const val US_PER_MS = 1000L

        /** A dequeue index no real buffer can carry, returned when the codec is gone. */
        const val CODEC_GONE = Int.MIN_VALUE
    }
}
