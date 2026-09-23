package com.sza.fastmediasorter.broadcast

import kotlin.math.sqrt

/**
 * S3349: breaks the acoustic feedback loop that forms when a listener plays the broadcast aloud in the
 * same room as the broadcasting microphone - the listener's speaker reaches the microphone, goes back
 * into the stream, and the round-trip gain stays above one until the level is unbearable.
 *
 * A peak limiter does not help here: it caps the samples at full scale, which is where the loop ends up
 * anyway, only without the distortion. The only thing that opens the loop is lowering the gain applied
 * per round, which is what this class does - it walks [appliedGainMultiplier] down while the output
 * level sits at the ceiling and walks it back up, far more slowly, once the level falls.
 *
 * The loop is reported from the size of the correction, not from the level: once the gain is down the
 * stream is quiet, the listener's speaker is quiet, and the microphone reads a perfectly ordinary
 * level - so a detector watching the level would announce that the loop had gone the moment it worked.
 * A gain held at half the user's value or less is the only lasting trace the loop leaves.
 *
 * Deliberately free of Android types so the whole behaviour is provable by unit test: the loop it
 * guards against needs two devices and a room, which no automated check has.
 */
class BroadcastFeedbackGuard(
    userGainPercent: Int,
    private val enabled: Boolean,
) {

    private val userGain: Float = userGainPercent / PERCENT_SCALE
    private val gainFloor: Float = userGain * FLOOR_FRACTION
    private val suppressionMark: Float = userGain * SUPPRESSION_RATIO

    /** The multiplier actually applied to the last processed buffer. */
    var appliedGainMultiplier: Float = userGain
        private set

    private var loudWindows: Int = 0
    private var quietWindows: Int = 0
    private var suppressedWindows: Int = 0

    /**
     * Scales [length] bytes of 16-bit little-endian PCM in place and returns whether a feedback loop is
     * currently being held down - a correction this large, sustained this long, is not an ordinary loud
     * passage.
     */
    fun process(buffer: ByteArray, length: Int): Boolean {
        if (!enabled) {
            scale(buffer, length, userGain)
            return false
        }
        val inputEnergy = scale(buffer, length, appliedGainMultiplier)
        val sampleCount = length / BYTES_PER_SAMPLE
        if (sampleCount > 0) {
            val inputLevel = (sqrt(inputEnergy / sampleCount) / FULL_SCALE).toFloat()
            regulate(inputLevel * appliedGainMultiplier)
        }
        return suppressedWindows >= LOOP_CONFIRM_WINDOWS
    }

    private fun regulate(outputLevel: Float) {
        when {
            outputLevel >= CEILING_LEVEL -> {
                quietWindows = 0
                loudWindows++
                if (loudWindows >= ATTACK_SUSTAIN_WINDOWS) {
                    appliedGainMultiplier = (appliedGainMultiplier * ATTACK_STEP).coerceAtLeast(gainFloor)
                }
            }

            outputLevel < RELEASE_LEVEL -> {
                loudWindows = 0
                quietWindows++
                if (quietWindows >= RELEASE_HOLD_WINDOWS) {
                    appliedGainMultiplier = (appliedGainMultiplier * RELEASE_STEP).coerceAtMost(userGain)
                }
            }

            else -> {
                loudWindows = 0
                quietWindows = 0
            }
        }
        // A quiet room clears the warning at once rather than after the slow release, so the banner
        // does not outlive the loop by the seconds the gain takes to walk back.
        suppressedWindows = if (outputLevel >= RELEASE_LEVEL && appliedGainMultiplier <= suppressionMark) {
            suppressedWindows + 1
        } else {
            0
        }
    }

    /**
     * Scales the buffer in place and returns the sum of squared *input* samples, so measurement and
     * scaling cost one pass. The input is measured rather than the output because the output level is
     * what the regulator is driving to a constant, and a constant carries no information.
     */
    private fun scale(buffer: ByteArray, length: Int, gain: Float): Double {
        var energy = 0.0
        var i = 0
        while (i + 1 < length) {
            val raw = ((buffer[i].toInt() and BYTE_MASK) or (buffer[i + 1].toInt() shl BYTE_BITS)).toShort()
            val scaled = (raw * gain).toInt().coerceIn(SAMPLE_MIN, SAMPLE_MAX)
            buffer[i] = (scaled and BYTE_MASK).toByte()
            buffer[i + 1] = ((scaled shr BYTE_BITS) and BYTE_MASK).toByte()
            energy += raw.toDouble() * raw.toDouble()
            i += BYTES_PER_SAMPLE
        }
        return energy
    }

    private companion object {
        const val PERCENT_SCALE = 100f
        const val BYTE_MASK = 0xFF
        const val BYTE_BITS = 8
        const val BYTES_PER_SAMPLE = 2
        const val SAMPLE_MIN = -32768
        const val SAMPLE_MAX = 32767
        const val FULL_SCALE = 32768.0

        /** Levels are fractions of full scale; a capture buffer is the window, roughly 90 ms. */
        const val CEILING_LEVEL = 0.5f
        const val RELEASE_LEVEL = 0.25f

        /** Three windows of headroom so a door slam or a clap does not pull the gain down. */
        const val ATTACK_SUSTAIN_WINDOWS = 3
        const val ATTACK_STEP = 0.75f

        /**
         * The release is deliberately far slower than the attack: restoring the gain as fast as it was
         * taken away re-arms the same loop and turns a stable level into a pulsing one.
         */
        const val RELEASE_HOLD_WINDOWS = 20
        const val RELEASE_STEP = 1.05f
        const val FLOOR_FRACTION = 0.05f

        /** Half the user's gain or less - past what an ordinary loud passage ever costs. */
        const val SUPPRESSION_RATIO = 0.5f

        /** About a second of that correction before the user is told, so the warning is not a flicker. */
        const val LOOP_CONFIRM_WINDOWS = 10
    }
}
