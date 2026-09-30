package com.sza.fastmediasorter.data.link.streaming

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar I (sample-copy remux to MP4).
 *
 * Reads compressed samples from each segment file via [MediaExtractor] and writes
 * them into a single MP4 via [MediaMuxer] without re-encoding. Supported sample
 * MIMEs (sample-copy is codec-agnostic but `MediaMuxer` only accepts these into
 * MPEG-4 containers):
 *
 * - Video: `video/avc`, `video/hevc`, `video/av01`.
 * - Audio: `audio/mp4a-latm` (AAC), `audio/raw`.
 *
 * Any other MIME → terminate with [RemuxResult.MuxFailed]; the caller surfaces a
 * `PipelineOutcome.MuxFailed(codec)` toast. No partial MP4 is produced.
 */
@Singleton
class MediaMuxerRemuxer @Inject constructor() {

    fun remux(bundle: SegmentBundle, outputFile: File): RemuxResult {
        if (bundle.segmentFiles.isEmpty()) {
            return RemuxResult.MuxFailed(codec = "no_segments")
        }
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        val muxer = try {
            MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (t: Throwable) {
            return RemuxResult.MuxFailed(codec = "muxer_init_failed:${t.message ?: t::class.simpleName}")
        }
        val session = MuxSession(muxer)
        return try {
            session.run(bundle.segmentFiles, outputFile)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            RemuxResult.MuxFailed(codec = "remux_runtime_failed:${t::class.simpleName}")
        } finally {
            session.close()
        }
    }

    /** One output file: the tracks of every input are registered first, then samples are copied. */
    private class MuxSession(private val muxer: MediaMuxer) {
        private val buffer = ByteBuffer.allocate(BUFFER_BYTES)
        private var started = false
        private var videoTrack = NO_TRACK
        private var audioTrack = NO_TRACK

        fun run(files: List<File>, outputFile: File): RemuxResult {
            // MediaMuxer refuses addTrack after start(), and a stream with a separate audio
            // rendition arrives as a second file - so no sample is written before every file was read.
            val registrationFailure = files.firstNotNullOfOrNull(::registerTracks)
            if (registrationFailure == null && (videoTrack != NO_TRACK || audioTrack != NO_TRACK)) {
                muxer.start()
                started = true
            }
            val copied = if (started) files.map(::copySamples) else emptyList()
            val sampleCount = copied.sumOf { it.samples }
            val result = registrationFailure
                ?: copied.firstNotNullOfOrNull { it.failure }
                ?: if (sampleCount == 0) RemuxResult.MuxFailed("no_samples") else RemuxResult.Success(outputFile)
            if (result is RemuxResult.Success) {
                LinkDownloadTrace.verbose(
                    "media-muxer-remuxer wrote samples=$sampleCount files=${files.size} " +
                        "video=${videoTrack != NO_TRACK} audio=${audioTrack != NO_TRACK}",
                )
            }
            return result
        }

        fun close() {
            runCatching {
                if (started) muxer.stop()
                muxer.release()
            }
        }

        /** Returns a failure, or null once the supported tracks of [file] are known to the muxer. */
        private fun registerTracks(file: File): RemuxResult.MuxFailed? =
            withExtractor(file, onOpenFailure = { extractorFailure(file) }) { extractor ->
                var failure: RemuxResult.MuxFailed? = null
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    val isVideo = mime.startsWith("video/")
                    val isAudio = mime.startsWith("audio/")
                    when {
                        isVideo && mime !in SUPPORTED_VIDEO -> failure = RemuxResult.MuxFailed(codec = mime)
                        isAudio && mime !in SUPPORTED_AUDIO -> failure = RemuxResult.MuxFailed(codec = mime)
                        isVideo && videoTrack == NO_TRACK -> videoTrack = muxer.addTrack(format)
                        isAudio && audioTrack == NO_TRACK -> audioTrack = muxer.addTrack(format)
                    }
                    if (failure != null) break
                }
                failure
            }

        private fun copySamples(file: File): CopyOutcome =
            withExtractor(file, onOpenFailure = { CopyOutcome(samples = 0, failure = extractorFailure(file)) }) {
                // The first track of each kind in this file feeds the muxer track of that kind.
                val trackMap = mutableMapOf<Int, Int>()
                for (i in 0 until it.trackCount) {
                    val mime = it.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                    val target = when {
                        mime.startsWith("video/") -> videoTrack
                        mime.startsWith("audio/") -> audioTrack
                        else -> NO_TRACK
                    }
                    if (target != NO_TRACK && target !in trackMap.values) {
                        trackMap[i] = target
                        it.selectTrack(i)
                    }
                }
                CopyOutcome(samples = writeSelectedSamples(it, trackMap), failure = null)
            }

        private fun writeSelectedSamples(extractor: MediaExtractor, trackMap: Map<Int, Int>): Int {
            val info = MediaCodec.BufferInfo()
            var count = 0
            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val muxerTrack = trackMap[extractor.sampleTrackIndex]
                if (muxerTrack != null) {
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = extractor.sampleTime
                    // MediaExtractor.sampleFlags reports SAMPLE_FLAG_* constants, but
                    // MediaCodec.BufferInfo.flags expects BUFFER_FLAG_* constants. Translate the
                    // only one that matters for a sample-copy mux: keyframe. SAMPLE_FLAG_ENCRYPTED
                    // and SAMPLE_FLAG_PARTIAL_FRAME have no muxer equivalent and are dropped.
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    muxer.writeSampleData(muxerTrack, buffer, info)
                    count++
                }
                extractor.advance()
            }
            return count
        }

        /** Opens [file] in a fresh extractor, runs [block] and always releases the extractor. */
        private inline fun <T> withExtractor(
            file: File,
            onOpenFailure: () -> T,
            block: (MediaExtractor) -> T,
        ): T {
            val extractor = MediaExtractor()
            try {
                val opened = runCatching { extractor.setDataSource(file.absolutePath) }.isSuccess
                return if (opened) block(extractor) else onOpenFailure()
            } finally {
                runCatching { extractor.release() }
            }
        }

        private fun extractorFailure(file: File) = RemuxResult.MuxFailed(codec = "extractor_failed:${file.name}")
    }

    private class CopyOutcome(val samples: Int, val failure: RemuxResult.MuxFailed?)

    private companion object {
        const val BUFFER_BYTES = 1 * 1024 * 1024 // 1 MiB reusable sample buffer.
        const val NO_TRACK = -1
        val SUPPORTED_VIDEO = setOf("video/avc", "video/hevc", "video/av01")
        val SUPPORTED_AUDIO = setOf("audio/mp4a-latm", "audio/raw")
    }
}

sealed interface RemuxResult {
    data class Success(val file: File) : RemuxResult
    data class MuxFailed(val codec: String) : RemuxResult
}
