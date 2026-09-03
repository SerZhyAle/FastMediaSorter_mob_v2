package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Scales the left and right channel amplitudes independently on the 16-bit PCM stream, which is the
 * channel component `Player.volume` does not have (S1267).
 *
 * The gain pair arrives through [gains] instead of being latched at construction, so the three
 * presets stay a thin caller over a role that accepts any pair.
 */
class ChannelBalanceAudioProcessor(
    private val gains: ChannelBalanceSource = ChannelBalanceController
) : BaseAudioProcessor() {

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        ChannelBalanceController.reportChannelCount(inputAudioFormat.channelCount)
        // Gain scaling changes neither channel count nor sample rate.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining())
        // Balance is defined for two channels only: mono has no sides, and a stereo pair read out of
        // a multichannel layout would scale the wrong speakers.
        if (inputAudioFormat.channelCount != ChannelBalanceController.STEREO_CHANNEL_COUNT) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        val leftGain = gains.leftGain
        val rightGain = gains.rightGain
        var onLeftChannel = true
        while (inputBuffer.hasRemaining()) {
            val gain = if (onLeftChannel) leftGain else rightGain
            output.putShort(scaleSample(inputBuffer.short, gain))
            onLeftChannel = !onLeftChannel
        }
        output.flip()
    }

    private fun scaleSample(sample: Short, gain: Float): Short {
        if (gain == ChannelBalanceController.UNITY_GAIN) {
            return sample
        }
        return (sample * gain).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}
