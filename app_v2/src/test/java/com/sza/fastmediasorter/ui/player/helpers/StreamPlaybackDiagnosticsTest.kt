package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1127: off-device coverage of the stream diagnostics aggregator. The clock is injected, so TTFF and
 * stall durations are deterministic without a real player.
 */
class StreamPlaybackDiagnosticsTest {

    private var now = 0L
    private val diagnostics = StreamPlaybackDiagnostics { now }

    @Test
    fun `time to first frame is measured from prepare to first frame`() {
        now = 1_000L
        diagnostics.onPrepared()
        now = 1_250L
        diagnostics.onFirstFrameRendered()

        assertEquals(250L, diagnostics.timeToFirstFrameMs)
    }

    @Test
    fun `first frame is recorded only once`() {
        now = 1_000L
        diagnostics.onPrepared()
        now = 1_250L
        diagnostics.onFirstFrameRendered()
        now = 5_000L
        diagnostics.onFirstFrameRendered()

        assertEquals(250L, diagnostics.timeToFirstFrameMs)
    }

    @Test
    fun `buffering before the first frame is startup, not a stall`() {
        now = 1_000L
        diagnostics.onPrepared()
        diagnostics.onStallStarted()
        now = 1_500L
        diagnostics.onStallEnded()

        assertEquals(0, diagnostics.stallCount)
        assertEquals(0L, diagnostics.totalStallMs)
    }

    @Test
    fun `stalls after the first frame accumulate count and duration`() {
        now = 1_000L
        diagnostics.onPrepared()
        now = 1_200L
        diagnostics.onFirstFrameRendered()

        now = 2_000L
        diagnostics.onStallStarted()
        now = 2_300L
        diagnostics.onStallEnded()

        now = 5_000L
        diagnostics.onStallStarted()
        now = 5_700L
        diagnostics.onStallEnded()

        assertEquals(2, diagnostics.stallCount)
        assertEquals(1_000L, diagnostics.totalStallMs)
    }

    @Test
    fun `a second buffering without a ready between does not double-open a stall`() {
        now = 1_000L
        diagnostics.onPrepared()
        now = 1_100L
        diagnostics.onFirstFrameRendered()

        now = 2_000L
        diagnostics.onStallStarted()
        now = 2_200L
        diagnostics.onStallStarted()
        now = 2_500L
        diagnostics.onStallEnded()

        assertEquals(1, diagnostics.stallCount)
        assertEquals(500L, diagnostics.totalStallMs)
    }

    @Test
    fun `dropped frames accumulate`() {
        diagnostics.onDroppedFrames(5)
        diagnostics.onDroppedFrames(3)

        assertEquals(8, diagnostics.droppedFrames)
    }

    @Test
    fun `decoder name and hardware flag are captured`() {
        diagnostics.onDecoderInitialized("OMX.qcom.video.decoder.avc", hardwareAccelerated = true)

        assertEquals("OMX.qcom.video.decoder.avc", diagnostics.decoderName)
        assertEquals(true, diagnostics.isHardwareDecoder)
    }

    @Test
    fun `summary reflects the accumulated metrics`() {
        now = 1_000L
        diagnostics.onPrepared()
        now = 1_150L
        diagnostics.onFirstFrameRendered()
        diagnostics.onDroppedFrames(4)
        diagnostics.onDecoderInitialized("c2.android.avc.decoder", hardwareAccelerated = false)

        val summary = diagnostics.summary()

        assertTrue(summary, summary.contains("ttff=150ms"))
        assertTrue(summary, summary.contains("dropped=4"))
        assertTrue(summary, summary.contains("decoder=c2.android.avc.decoder(sw)"))
    }

    @Test
    fun `time to first frame is unset when no frame rendered`() {
        now = 1_000L
        diagnostics.onPrepared()

        assertEquals(-1L, diagnostics.timeToFirstFrameMs)
        assertNull(diagnostics.decoderName)
    }
}
