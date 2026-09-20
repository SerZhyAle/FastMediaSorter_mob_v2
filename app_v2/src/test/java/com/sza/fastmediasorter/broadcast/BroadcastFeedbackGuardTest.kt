package com.sza.fastmediasorter.broadcast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3349: the loop this guard breaks needs two devices and a room, so the behaviour is proved here on
 * synthesised buffers instead - a steady loud signal must drive the gain down and raise the flag, a
 * quiet one must give the gain back, and a disabled guard must behave exactly as the old capture loop.
 */
class BroadcastFeedbackGuardTest {

    @Test
    fun `sustained full-scale input drives the gain down and confirms the loop`() {
        val guard = BroadcastFeedbackGuard(userGainPercent = 100, enabled = true)
        var confirmed = false
        repeat(WINDOWS_TO_SETTLE) {
            confirmed = guard.process(loudBuffer(), BUFFER_BYTES)
        }

        assertTrue("gain must fall well below the user value", guard.appliedGainMultiplier < 0.5f)
        assertTrue("a sustained loop must be reported", confirmed)
    }

    @Test
    fun `silence after suppression walks the gain back toward the user value`() {
        val guard = BroadcastFeedbackGuard(userGainPercent = 100, enabled = true)
        repeat(WINDOWS_TO_SETTLE) { guard.process(loudBuffer(), BUFFER_BYTES) }
        val suppressed = guard.appliedGainMultiplier

        var confirmed = true
        repeat(WINDOWS_TO_RECOVER) {
            confirmed = guard.process(ByteArray(BUFFER_BYTES), BUFFER_BYTES)
        }

        assertTrue("gain must recover", guard.appliedGainMultiplier > suppressed)
        assertFalse("silence is not a loop", confirmed)
    }

    @Test
    fun `a disabled guard leaves the user gain alone however loud the input`() {
        val guard = BroadcastFeedbackGuard(userGainPercent = 200, enabled = false)
        var confirmed = false
        repeat(WINDOWS_TO_SETTLE) {
            confirmed = guard.process(loudBuffer(), BUFFER_BYTES)
        }

        assertTrue("the user gain must stay untouched", guard.appliedGainMultiplier == 2f)
        assertFalse("a disabled guard never reports a loop", confirmed)
    }

    @Test
    fun `isolated loud bursts do not trip the attack`() {
        val guard = BroadcastFeedbackGuard(userGainPercent = 100, enabled = true)
        repeat(WINDOWS_TO_SETTLE) { index ->
            val buffer = if (index % 2 == 0) loudBuffer() else ByteArray(BUFFER_BYTES)
            guard.process(buffer, BUFFER_BYTES)
        }

        assertTrue("a clap must not pull the gain down", guard.appliedGainMultiplier == 1f)
    }

    /** Every sample near full scale - what a microphone reads once a feedback loop has taken hold. */
    private fun loudBuffer(): ByteArray {
        val buffer = ByteArray(BUFFER_BYTES)
        var i = 0
        while (i + 1 < BUFFER_BYTES) {
            buffer[i] = (LOUD_SAMPLE and 0xFF).toByte()
            buffer[i + 1] = ((LOUD_SAMPLE shr 8) and 0xFF).toByte()
            i += 2
        }
        return buffer
    }

    private companion object {
        const val BUFFER_BYTES = 8192
        const val LOUD_SAMPLE = 31000
        const val WINDOWS_TO_SETTLE = 40
        const val WINDOWS_TO_RECOVER = 60
    }
}
