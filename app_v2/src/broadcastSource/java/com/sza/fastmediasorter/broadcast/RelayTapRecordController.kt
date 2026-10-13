package com.sza.fastmediasorter.broadcast

import android.media.MediaCodec
import android.media.MediaFormat
import com.pedro.common.AudioCodec
import com.pedro.common.VideoCodec
import com.pedro.library.base.recording.RecordController
import com.sza.fastmediasorter.data.broadcast.exchange.AdtsHeader
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeRelayChunk
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeVideoRelayFeed
import com.sza.fastmediasorter.data.broadcast.exchange.MpegTsMuxer
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Taps the encoded H.264 and AAC of an RTSP camera session for the relay upload (DEVICE-EXCHANGE 7.6). The camera
 * hands every encoded buffer to its record controller whether or not a recording runs, so this controller never
 * starts one: it reports itself idle, which leaves the camera's stop path untouched, and muxes into [feed] only
 * while the relay reads it.
 *
 * Called on the encoder threads; the buffers are the encoder's own, so they are read through a duplicate and copied.
 */
class RelayTapRecordController(
    private val feed: ExchangeVideoRelayFeed,
    hasAudio: Boolean,
) : RecordController {

    private val muxer = MpegTsMuxer(hasAudio)
    private val lock = Any()

    @Volatile
    private var sampleRateHz = 0

    @Volatile
    private var channelCount = 0

    private var videoCodec = VideoCodec.H264
    private var audioCodec = AudioCodec.AAC

    init {
        feed.open(muxer.header)
    }

    /** Ends the tap: a relay reading the feed waits for the next session's header. */
    fun release() = feed.close()

    override fun recordVideo(videoBuffer: ByteBuffer, videoInfo: MediaCodec.BufferInfo) {
        if (!feed.isWanted || videoInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
        val keyFrame = videoInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        val accessUnit = videoBuffer.copyOf(videoInfo)
        val packets = synchronized(lock) { muxer.video(accessUnit, videoInfo.presentationTimeUs, keyFrame) }
        feed.offer(ExchangeRelayChunk(packets, joinable = keyFrame))
    }

    override fun recordAudio(audioBuffer: ByteBuffer, audioInfo: MediaCodec.BufferInfo) {
        if (!feed.isWanted || audioInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
        val frame = AdtsHeader.wrap(audioBuffer.copyOf(audioInfo), sampleRateHz, channelCount) ?: return
        val packets = synchronized(lock) { muxer.audio(frame, audioInfo.presentationTimeUs) }
        feed.offer(ExchangeRelayChunk(packets, joinable = false))
    }

    // The encoder carries SPS and PPS in the format, not in its keyframes; the muxer repeats them on each one.
    override fun setVideoFormat(videoFormat: MediaFormat) {
        val sps = videoFormat.bytesOf(CSD_SPS)
        val pps = videoFormat.bytesOf(CSD_PPS)
        synchronized(lock) { muxer.parameterSets = sps + pps }
    }

    override fun setAudioFormat(audioFormat: MediaFormat) {
        sampleRateHz = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    }

    override fun startRecord(
        path: String,
        listener: RecordController.Listener?,
        tracks: RecordController.RecordTracks,
    ) = Unit

    override fun startRecord(
        fd: FileDescriptor,
        listener: RecordController.Listener?,
        tracks: RecordController.RecordTracks,
    ) = Unit

    override fun stopRecord() = Unit

    override fun resetFormats() = Unit

    override fun isRunning(): Boolean = false

    override fun isRecording(): Boolean = false

    override fun setVideoCodec(videoCodec: VideoCodec) {
        this.videoCodec = videoCodec
    }

    override fun setAudioCodec(audioCodec: AudioCodec) {
        this.audioCodec = audioCodec
    }

    override fun pauseRecord() = Unit

    override fun resumeRecord() = Unit

    override fun updateInfo(videoCodec: VideoCodec, audioCodec: AudioCodec) {
        this.videoCodec = videoCodec
        this.audioCodec = audioCodec
    }

    override fun getVideoCodec(): VideoCodec = videoCodec

    override fun getAudioCodec(): AudioCodec = audioCodec

    override fun setRequestKeyFrame(requestKeyFrame: RecordController.RequestKeyFrame?) = Unit

    override fun getStatus(): RecordController.Status = RecordController.Status.STOPPED

    private fun ByteBuffer.copyOf(info: MediaCodec.BufferInfo): ByteArray {
        val view = duplicate()
        view.position(info.offset)
        view.limit(info.offset + info.size)
        return ByteArray(info.size).also { view.get(it) }
    }

    private fun MediaFormat.bytesOf(key: String): ByteArray {
        val buffer = getByteBuffer(key)?.duplicate() ?: return ByteArray(0)
        buffer.rewind()
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    private companion object {
        const val CSD_SPS = "csd-0"
        const val CSD_PPS = "csd-1"
    }
}
