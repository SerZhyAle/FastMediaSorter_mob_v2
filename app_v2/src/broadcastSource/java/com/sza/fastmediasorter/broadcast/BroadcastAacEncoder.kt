package com.sza.fastmediasorter.broadcast

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Encodes raw PCM 16-bit captured from the microphone into ADTS-framed AAC-LC.
 *
 * The listener side is an ordinary ExoPlayer progressive source: it sniffs the stream and hands it
 * to the ADTS extractor, which needs the per-frame 7-byte header this class prepends. Feeding it the
 * raw PCM the recorder produces yields a stream nothing can decode even though the response calls
 * itself `audio/aac`.
 */
class BroadcastAacEncoder(
    private val sampleRate: Int,
    private val channelCount: Int,
    private val bitRate: Int,
    private val onEncodedFrame: (ByteArray, Int, Int) -> Unit,
) {

    private var codec: MediaCodec? = null
    private val bufferInfo = MediaCodec.BufferInfo()
    private var presentationTimeUs = 0L
    private val adtsHeader = ByteArray(ADTS_HEADER_SIZE)

    /** Sample-rate index the ADTS header carries; -1 when this rate has no MPEG-4 index. */
    private val sampleRateIndex = SAMPLE_RATE_TABLE.indexOf(sampleRate)

    /**
     * Returns false when the device has no AAC encoder or the rate cannot be expressed in an ADTS
     * header, so the caller can fail the broadcast loudly instead of serving an undecodable stream.
     */
    @Suppress("TooGenericExceptionCaught")
    fun start(): Boolean {
        if (sampleRateIndex < 0) {
            Timber.e("BroadcastAacEncoder: sample rate $sampleRate has no ADTS index")
            return false
        }
        return try {
            val format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, channelCount).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE_BYTES)
            }
            val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            codec = encoder
            true
        } catch (e: Exception) {
            Timber.e(e, "BroadcastAacEncoder: failed to start the AAC encoder")
            releaseCodec()
            false
        }
    }

    /** Feeds one PCM chunk and drains whatever the encoder has ready for it. */
    @Suppress("TooGenericExceptionCaught")
    fun encode(pcm: ByteArray, length: Int) {
        val encoder = codec ?: return
        try {
            queueInput(encoder, pcm, length)
            drainOutput(encoder)
        } catch (e: Exception) {
            Timber.e(e, "BroadcastAacEncoder: encode step failed")
        }
    }

    private fun queueInput(encoder: MediaCodec, pcm: ByteArray, length: Int) {
        val inputIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (inputIndex < 0) return
        val input = encoder.getInputBuffer(inputIndex) ?: return
        input.clear()
        input.put(pcm, 0, length)
        encoder.queueInputBuffer(inputIndex, 0, length, presentationTimeUs, 0)
        presentationTimeUs += framesToMicros(length)
    }

    private fun drainOutput(encoder: MediaCodec) {
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            if (outputIndex < 0) return
            val output = encoder.getOutputBuffer(outputIndex)
            if (output != null && bufferInfo.size > 0 && !isCodecConfig()) {
                emitFrame(output)
            }
            encoder.releaseOutputBuffer(outputIndex, false)
        }
    }

    /**
     * The codec-config buffer carries the AudioSpecificConfig that a container would hold. ADTS
     * repeats that information in every frame header, so forwarding it would inject two stray bytes
     * into the elementary stream.
     */
    private fun isCodecConfig(): Boolean =
        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0

    private fun emitFrame(output: ByteBuffer) {
        val payloadSize = bufferInfo.size
        val frame = ByteArray(ADTS_HEADER_SIZE + payloadSize)
        writeAdtsHeader(payloadSize)
        adtsHeader.copyInto(frame, 0)
        output.position(bufferInfo.offset)
        output.limit(bufferInfo.offset + payloadSize)
        output.get(frame, ADTS_HEADER_SIZE, payloadSize)
        onEncodedFrame(frame, 0, frame.size)
    }

    @Suppress("MagicNumber")
    private fun writeAdtsHeader(payloadSize: Int) {
        val packetLength = payloadSize + ADTS_HEADER_SIZE
        adtsHeader[0] = 0xFF.toByte()
        adtsHeader[1] = 0xF1.toByte()
        adtsHeader[2] = (
            (AAC_LC_PROFILE_BITS shl 6) or (sampleRateIndex shl 2) or (channelCount shr 2)
            ).toByte()
        adtsHeader[3] = (((channelCount and 3) shl 6) or (packetLength shr 11)).toByte()
        adtsHeader[4] = ((packetLength and 0x7FF) shr 3).toByte()
        adtsHeader[5] = (((packetLength and 7) shl 5) or 0x1F).toByte()
        adtsHeader[6] = 0xFC.toByte()
    }

    @Suppress("MagicNumber")
    private fun framesToMicros(pcmBytes: Int): Long {
        val bytesPerFrame = channelCount * BYTES_PER_SAMPLE
        return pcmBytes.toLong() * 1_000_000L / (sampleRate.toLong() * bytesPerFrame)
    }

    fun stop() {
        releaseCodec()
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun releaseCodec() {
        val encoder = codec ?: return
        codec = null
        try {
            encoder.stop()
        } catch (e: Exception) {
            Timber.d(e, "BroadcastAacEncoder: encoder already stopped")
        }
        try {
            encoder.release()
        } catch (e: Exception) {
            Timber.d(e, "BroadcastAacEncoder: encoder already released")
        }
    }

    companion object {
        const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
        const val ADTS_HEADER_SIZE = 7

        private const val BYTES_PER_SAMPLE = 2
        private const val MAX_INPUT_SIZE_BYTES = 16384
        private const val DEQUEUE_TIMEOUT_US = 10_000L

        /** AAC-LC is MPEG-4 audio object type 2; the ADTS profile field stores it minus one. */
        private const val AAC_LC_PROFILE_BITS = 1

        /** MPEG-4 sampling-frequency index table; the position is the value the header carries. */
        private val SAMPLE_RATE_TABLE = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000, 7350,
        )
    }
}
