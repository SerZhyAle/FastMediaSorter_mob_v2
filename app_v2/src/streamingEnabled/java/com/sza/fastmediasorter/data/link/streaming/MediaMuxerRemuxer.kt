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

        val buffer = ByteBuffer.allocate(BUFFER_BYTES)
        var muxerStarted = false
        var sampleCount = 0
        var trackVideoFormat: MediaFormat? = null
        var trackAudioFormat: MediaFormat? = null
        var muxerVideoTrack: Int = -1
        var muxerAudioTrack: Int = -1

        try {
            // Pass 1: walk every segment, register tracks once, then mux samples.
            for (segment in bundle.segmentFiles) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(segment.absolutePath)
                } catch (t: Throwable) {
                    return RemuxResult.MuxFailed(codec = "extractor_failed:${segment.name}")
                }
                try {
                    val trackIndices = mutableMapOf<Int, Int>()
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                        val isVideo = mime.startsWith("video/")
                        val isAudio = mime.startsWith("audio/")
                        if (!isVideo && !isAudio) continue
                        if (isVideo && !SUPPORTED_VIDEO.contains(mime)) {
                            return RemuxResult.MuxFailed(codec = mime)
                        }
                        if (isAudio && !SUPPORTED_AUDIO.contains(mime)) {
                            return RemuxResult.MuxFailed(codec = mime)
                        }
                        if (isVideo && trackVideoFormat == null) {
                            trackVideoFormat = format
                            muxerVideoTrack = muxer.addTrack(format)
                            trackIndices[i] = muxerVideoTrack
                            extractor.selectTrack(i)
                        } else if (isAudio && trackAudioFormat == null) {
                            trackAudioFormat = format
                            muxerAudioTrack = muxer.addTrack(format)
                            trackIndices[i] = muxerAudioTrack
                            extractor.selectTrack(i)
                        } else {
                            // Track of same kind reappearing in later segment - reuse muxer track.
                            val muxerTrack = if (isVideo) muxerVideoTrack else muxerAudioTrack
                            if (muxerTrack >= 0) {
                                trackIndices[i] = muxerTrack
                                extractor.selectTrack(i)
                            }
                        }
                    }
                    if (!muxerStarted && (trackVideoFormat != null || trackAudioFormat != null)) {
                        muxer.start()
                        muxerStarted = true
                    }
                    if (!muxerStarted) continue

                    val info = MediaCodec.BufferInfo()
                    while (true) {
                        buffer.clear()
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break
                        val muxerTrack = trackIndices[extractor.sampleTrackIndex] ?: run {
                            extractor.advance(); continue
                        }
                        info.offset = 0
                        info.size = sampleSize
                        info.presentationTimeUs = extractor.sampleTime
                        info.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerTrack, buffer, info)
                        sampleCount++
                        extractor.advance()
                    }
                } finally {
                    runCatching { extractor.release() }
                }
            }

            if (!muxerStarted || sampleCount == 0) {
                return RemuxResult.MuxFailed(codec = "no_samples")
            }
            LinkDownloadTrace.verbose(
                "media-muxer-remuxer wrote samples=$sampleCount video=${trackVideoFormat != null} audio=${trackAudioFormat != null}",
            )
            return RemuxResult.Success(file = outputFile)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            return RemuxResult.MuxFailed(codec = "remux_runtime_failed:${t::class.simpleName}")
        } finally {
            runCatching {
                if (muxerStarted) muxer.stop()
                muxer.release()
            }
        }
    }

    private companion object {
        const val BUFFER_BYTES = 1 * 1024 * 1024 // 1 MiB reusable sample buffer.
        val SUPPORTED_VIDEO = setOf("video/avc", "video/hevc", "video/av01")
        val SUPPORTED_AUDIO = setOf("audio/mp4a-latm", "audio/raw")
    }
}

sealed interface RemuxResult {
    data class Success(val file: File) : RemuxResult
    data class MuxFailed(val codec: String) : RemuxResult
}
