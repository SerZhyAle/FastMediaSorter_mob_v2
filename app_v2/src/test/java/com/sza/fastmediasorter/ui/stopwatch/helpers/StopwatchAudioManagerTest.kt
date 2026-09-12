package com.sza.fastmediasorter.ui.stopwatch.helpers

import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the ownership rules of the accompaniment against a fake output.
 *
 * Strategic §7 names a second playback point drifting in behaviour from the existing one as the live
 * risk of this phase, and start/stop ownership across N participants is where that drift would first
 * appear: the manager is called on every state emission, so every entry point has to be idempotent.
 */
class StopwatchAudioManagerTest {

    private class FakeOutput : StopwatchAudioOutput {
        var prepares = 0
        var starts = 0
        var stops = 0
        var releases = 0

        override val maxStreamVolume: Int = MAX_STREAM_VOLUME
        override var streamVolume: Int = 0

        override fun prepare(uri: Uri) {
            prepares++
        }

        override fun start() {
            starts++
        }

        override fun stop() {
            stops++
        }

        override fun release() {
            releases++
        }
    }

    private val output = FakeOutput()
    private val manager = StopwatchAudioManager(output)
    private val track: Uri = mockk(relaxed = true)

    @Test
    fun `two participants starting in sequence produce exactly one start`() {
        manager.selectTrack(track)

        manager.syncWith(anyRunning = true)
        manager.syncWith(anyRunning = true)

        assertEquals(1, output.starts)
    }

    @Test
    fun `stopping one of two leaves the accompaniment running`() {
        manager.selectTrack(track)
        manager.syncWith(anyRunning = true)

        // The other participant is still running, so the collected state still reports anyRunning.
        manager.syncWith(anyRunning = true)

        assertEquals(0, output.stops)
    }

    @Test
    fun `stopping the last participant stops the accompaniment`() {
        manager.selectTrack(track)
        manager.syncWith(anyRunning = true)

        manager.syncWith(anyRunning = false)

        assertEquals(1, output.stops)
    }

    @Test
    fun `a silent state emitted twice stops the accompaniment once`() {
        manager.selectTrack(track)
        manager.syncWith(anyRunning = true)

        manager.syncWith(anyRunning = false)
        manager.syncWith(anyRunning = false)

        assertEquals(1, output.stops)
    }

    @Test
    fun `release after stop is safe to call twice`() {
        manager.selectTrack(track)
        manager.syncWith(anyRunning = true)
        manager.syncWith(anyRunning = false)

        manager.release()
        manager.release()

        assertEquals(1, output.releases)
    }

    @Test
    fun `a released manager does not start again`() {
        manager.selectTrack(track)
        manager.release()

        manager.syncWith(anyRunning = true)

        assertEquals(0, output.starts)
    }

    @Test
    fun `no track means the run stays silent`() {
        manager.syncWith(anyRunning = true)

        assertEquals(0, output.starts)
        assertEquals(0, output.prepares)
    }

    @Test
    fun `choosing a track while it plays stops the previous one first`() {
        manager.selectTrack(track)
        manager.syncWith(anyRunning = true)

        manager.selectTrack(mockk(relaxed = true))

        assertEquals(1, output.stops)
        assertEquals(2, output.prepares)
    }

    @Test
    fun `the volume level passes straight through to the stream`() {
        manager.streamVolume = MAX_STREAM_VOLUME

        assertEquals(MAX_STREAM_VOLUME, output.streamVolume)
        assertTrue(manager.maxStreamVolume == MAX_STREAM_VOLUME)
    }

    private companion object {
        const val MAX_STREAM_VOLUME = 15
    }
}
