package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1510: the arithmetic and the wording of the periodic stream line.
 *
 * The prefix assertion is not pedantry. The release log persists nothing below WARN except messages
 * beginning with one of a registered set of prefixes, so a line spelled any other way never reaches
 * the file and the whole tick looks broken from outside (S1468).
 */
class StreamStatsFormatterTest {

    @Test
    fun `a line carries the prefix that survives the release log filter`() {
        val line = StreamStatsFormatter.line(sample(atMs = 0, frames = 0), sample(atMs = 1_000, frames = 30))

        assertTrue(line!!.startsWith("Stream diag: "))
    }

    @Test
    fun `the rate is the frame difference over elapsed time, not an instant reading`() {
        assertEquals(30.0, StreamStatsFormatter.renderedFps(100, 130, 1_000)!!, 0.001)
        assertEquals(15.0, StreamStatsFormatter.renderedFps(100, 130, 2_000)!!, 0.001)
    }

    @Test
    fun `the first sample of a session yields no line`() {
        assertNull(StreamStatsFormatter.line(null, sample(atMs = 1_000, frames = 30)))
    }

    @Test
    fun `a non-advancing clock yields no line rather than a division by zero`() {
        assertNull(StreamStatsFormatter.line(sample(atMs = 1_000, frames = 0), sample(atMs = 1_000, frames = 30)))
    }

    @Test
    fun `a counter that went backwards reports no rate instead of inventing a stall`() {
        // The renderer restarts its count when it is re-enabled mid-session; a negative difference is
        // that restart, not a drop to zero frames.
        assertNull(StreamStatsFormatter.renderedFps(500, 10, 1_000))
    }

    @Test
    fun `an audio-only stream reports no frame rate rather than zero`() {
        assertNull(StreamStatsFormatter.renderedFps(null, null, 1_000))

        val line = StreamStatsFormatter.line(
            sample(atMs = 0, frames = null),
            sample(atMs = 5_000, frames = null)
        )

        assertTrue(line!!.contains("fps=n/a"))
    }

    @Test
    fun `the line names the rendition actually playing`() {
        val line = StreamStatsFormatter.line(
            sample(atMs = 0, frames = 0),
            sample(atMs = 1_000, frames = 25, width = 1280, height = 720, formatBitrate = 2_500_000)
        )

        assertTrue(line!!.contains("rung=1280x720@2500kbps"))
    }

    @Test
    fun `a rendition without a size reads as unknown rather than as zero by zero`() {
        val line = StreamStatsFormatter.line(sample(atMs = 0, frames = 0), sample(atMs = 1_000, frames = 25))

        assertTrue(line!!.contains("rung=n/a"))
    }

    @Test
    fun `the network estimate is reported in kbps`() {
        val line = StreamStatsFormatter.line(
            sample(atMs = 0, frames = 0),
            sample(atMs = 1_000, frames = 25, bitrateEstimate = 3_400_000)
        )

        assertTrue(line!!.contains("net=3400kbps"))
    }

    @Test
    fun `the rate keeps one decimal so a near-nominal stream is distinguishable from a nominal one`() {
        val line = StreamStatsFormatter.line(sample(atMs = 0, frames = 0), sample(atMs = 1_000, frames = 24))

        assertTrue(line!!.contains("fps=24.0"))
    }

    private fun sample(
        atMs: Long,
        frames: Int?,
        bitrateEstimate: Long = 0L,
        width: Int = 0,
        height: Int = 0,
        formatBitrate: Int = 0,
    ) = StreamStatsSample(
        atMs = atMs,
        renderedFrames = frames,
        bitrateEstimateBps = bitrateEstimate,
        width = width,
        height = height,
        formatBitrateBps = formatBitrate,
    )
}
