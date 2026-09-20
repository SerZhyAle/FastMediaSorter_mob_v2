package com.sza.fastmediasorter.ui.sos

import android.content.Context
import android.media.AudioManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S3333: the alarm channel is the siren's volume, and the owner heard it below full.
 *
 * The audio track itself cannot be built in a JVM test, and does not need to be: [SosSoundGenerator]
 * raises the channel before it opens the track and restores it after releasing one, so the volume
 * contract is reachable through the public surface with the track half failing harmlessly.
 */
class SosSoundGeneratorTest {

    private val audioManager: AudioManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true) {
        every { getSystemService(Context.AUDIO_SERVICE) } returns audioManager
    }

    @Test
    fun `raises the alarm channel to its maximum`() {
        givenChannel(max = 7, startingAt = 2)

        SosSoundGenerator().start(context)

        verify { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 7, 0) }
    }

    @Test
    fun `steps the channel up when the direct set did not land`() {
        val max = 4
        var current = 1
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) } returns max
        every { audioManager.getStreamVolume(AudioManager.STREAM_ALARM) } answers { current }
        // The firmware ignores a direct set under Do Not Disturb but honours a stepwise raise.
        every {
            audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_RAISE, any())
        } answers { current = (current + 1).coerceAtMost(max) }

        SosSoundGenerator().start(context)

        assertEquals(max, current)
    }

    @Test
    fun `stops stepping when the channel refuses to move`() {
        givenChannel(max = 5, startingAt = 1)

        SosSoundGenerator().start(context)

        // Bounded by the channel maximum rather than looping while the value stays put.
        verify(atMost = 5) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_RAISE, any())
        }
    }

    @Test
    fun `restores the volume the device had before the siren`() {
        givenChannel(max = 7, startingAt = 3)
        val generator = SosSoundGenerator()
        generator.start(context)
        val restored = slot<Int>()
        every {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, capture(restored), any())
        } answers { }

        generator.stop(context)

        assertEquals(3, restored.captured)
    }

    private fun givenChannel(max: Int, startingAt: Int) {
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) } returns max
        every { audioManager.getStreamVolume(AudioManager.STREAM_ALARM) } returns startingAt
    }
}
