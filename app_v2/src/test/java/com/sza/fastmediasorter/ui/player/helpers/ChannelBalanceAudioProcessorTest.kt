package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * S2638: on a non-stereo stream the processor used to take a pass-through branch in `queueInput`
 * that copied media3's shared `EMPTY_BUFFER` onto itself, so the pipeline's very first pump threw
 * `IllegalArgumentException: The source buffer is this buffer` and no mono file ever played. It now
 * reports itself inactive from `onConfigure`, which keeps it out of the pipeline altogether.
 */
class ChannelBalanceAudioProcessorTest {

    @Test
    fun `mono format leaves the processor inactive`() {
        val processor = ChannelBalanceAudioProcessor(unityGains())

        val outputFormat = processor.configure(pcm16Format(MONO_CHANNEL_COUNT))

        assertEquals(AudioProcessor.AudioFormat.NOT_SET, outputFormat)
        assertFalse(processor.isActive)
    }

    @Test
    fun `multichannel format leaves the processor inactive`() {
        val processor = ChannelBalanceAudioProcessor(unityGains())

        val outputFormat = processor.configure(pcm16Format(SURROUND_CHANNEL_COUNT))

        assertEquals(AudioProcessor.AudioFormat.NOT_SET, outputFormat)
        assertFalse(processor.isActive)
    }

    @Test
    fun `stereo format keeps the processor active on the unchanged input format`() {
        val processor = ChannelBalanceAudioProcessor(unityGains())
        val inputFormat = pcm16Format(ChannelBalanceController.STEREO_CHANNEL_COUNT)

        val outputFormat = processor.configure(inputFormat)

        assertTrue(processor.isActive)
        assertEquals(inputFormat, outputFormat)
    }

    /**
     * The exact call chain from the crash report: `getOutput` pumps the pipeline with the shared
     * empty buffer even when nothing was queued.
     */
    @Test
    fun `pipeline pumping a mono stream yields no output instead of throwing`() {
        val pipeline = AudioProcessingPipeline(
            ImmutableList.of<AudioProcessor>(ChannelBalanceAudioProcessor(unityGains()))
        )

        pipeline.configure(pcm16Format(MONO_CHANNEL_COUNT))
        pipeline.flush()

        assertFalse(pipeline.isOperational)
        assertFalse(pipeline.output.hasRemaining())
    }

    @Test
    fun `stereo with an unsupported encoding is still rejected`() {
        val processor = ChannelBalanceAudioProcessor(unityGains())
        val unsupported = AudioProcessor.AudioFormat(
            SAMPLE_RATE_HZ,
            ChannelBalanceController.STEREO_CHANNEL_COUNT,
            C.ENCODING_PCM_8BIT
        )

        val rejection = assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
            processor.configure(unsupported)
        }

        assertEquals(unsupported, rejection.inputAudioFormat)
    }

    /** The encoding check sits below the inactive exit, so a format it will not touch passes. */
    @Test
    fun `mono with an unsupported encoding is skipped rather than rejected`() {
        val processor = ChannelBalanceAudioProcessor(unityGains())
        val unsupported = AudioProcessor.AudioFormat(
            SAMPLE_RATE_HZ,
            MONO_CHANNEL_COUNT,
            C.ENCODING_PCM_8BIT
        )

        assertEquals(AudioProcessor.AudioFormat.NOT_SET, processor.configure(unsupported))
    }

    @Test
    fun `channel count reaches the controller before the inactive exit`() {
        val processor = ChannelBalanceAudioProcessor(unityGains())

        processor.configure(pcm16Format(ChannelBalanceController.STEREO_CHANNEL_COUNT))
        assertTrue(ChannelBalanceController.isStereoContentActive)

        processor.configure(pcm16Format(MONO_CHANNEL_COUNT))
        assertFalse(ChannelBalanceController.isStereoContentActive)
    }

    @Test
    fun `each stereo gain scales only its own channel`() {
        val processor = ChannelBalanceAudioProcessor(
            FixedGains(leftGain = HALF_GAIN, rightGain = ChannelBalanceController.UNITY_GAIN)
        )
        processor.configure(pcm16Format(ChannelBalanceController.STEREO_CHANNEL_COUNT))
        processor.flush()

        processor.queueInput(directBufferOf(LEFT_SAMPLE, RIGHT_SAMPLE))
        val output = processor.output

        assertEquals(HALVED_LEFT_SAMPLE, output.short)
        assertEquals(RIGHT_SAMPLE, output.short)
    }

    private fun unityGains(): ChannelBalanceSource = FixedGains(
        leftGain = ChannelBalanceController.UNITY_GAIN,
        rightGain = ChannelBalanceController.UNITY_GAIN
    )

    private fun pcm16Format(channelCount: Int): AudioProcessor.AudioFormat =
        AudioProcessor.AudioFormat(SAMPLE_RATE_HZ, channelCount, C.ENCODING_PCM_16BIT)

    private fun directBufferOf(vararg samples: Short): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        for (sample in samples) {
            buffer.putShort(sample)
        }
        buffer.flip()
        return buffer
    }

    private class FixedGains(
        override val leftGain: Float,
        override val rightGain: Float
    ) : ChannelBalanceSource
}

private const val SAMPLE_RATE_HZ = 44100
private const val MONO_CHANNEL_COUNT = 1
private const val SURROUND_CHANNEL_COUNT = 6
private const val HALF_GAIN = 0.5f
private const val LEFT_SAMPLE: Short = 1000
private const val RIGHT_SAMPLE: Short = -2000
private const val HALVED_LEFT_SAMPLE: Short = 500
