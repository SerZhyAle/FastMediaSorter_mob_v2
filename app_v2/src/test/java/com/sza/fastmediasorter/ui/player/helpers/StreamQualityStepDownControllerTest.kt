package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.ui.player.helpers.StreamQualityStepDownController.Cap
import com.sza.fastmediasorter.ui.player.helpers.StreamQualityStepDownController.Rendition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1128: off-device coverage of the quality step-down policy. No Media3 dependency, so the threshold,
 * hysteresis, floor, and single-quality branches are all exercised deterministically.
 *
 * S1508: the controller takes the clock as a parameter, so the decay window is driven by [clockMs] here
 * rather than by real time - no Robolectric, no sleeping test.
 */
class StreamQualityStepDownControllerTest {

    private val controller = StreamQualityStepDownController()

    /** Virtual clock standing in for the caller's `SystemClock.elapsedRealtime()`. */
    private var clockMs = 10_000L

    private fun ladder3() = listOf(
        Rendition(1920, 1080, 5_000_000),
        Rendition(1280, 720, 2_500_000),
        Rendition(640, 360, 800_000),
    )

    /** Advance the virtual clock, then report one stall at the new instant. */
    private fun stall(advanceMs: Long = 0L, playing: Rendition? = null): Cap? {
        clockMs += advanceMs
        return controller.registerStall(clockMs, playing)
    }

    @Test
    fun `single-quality ladder never steps down`() {
        controller.setRenditions(listOf(Rendition(1280, 720, 2_500_000)))

        assertTrue(controller.isSingleQuality)
        assertNull(stall())
        assertNull(stall())
        assertNull(stall())
    }

    @Test
    fun `empty ladder is inert`() {
        controller.setRenditions(emptyList())

        assertTrue(controller.isSingleQuality)
        assertNull(stall())
    }

    @Test
    fun `stall below threshold does not step`() {
        controller.setRenditions(ladder3())

        assertNull(stall())
    }

    @Test
    fun `stall at threshold steps down one rung to the next-lower rendition`() {
        controller.setRenditions(ladder3())
        // Ceiling starts at the top (index 2 = 1080p). Threshold is 2 stalls.
        assertNull(stall())
        val cap = stall()

        assertEquals(1280, cap?.maxWidthPx)
        assertEquals(720, cap?.maxHeightPx)
        assertEquals(2_500_000, cap?.maxBitrateBps)
        assertEquals(1, controller.currentCeilingIndex)
    }

    @Test
    fun `repeated stall batches cascade down one rung each and stop at the floor`() {
        controller.setRenditions(ladder3())

        stall()
        val toMid = stall() // -> 720p (index 1)
        assertEquals(720, toMid?.maxHeightPx)

        stall()
        val toFloor = stall() // -> 360p (index 0)
        assertEquals(360, toFloor?.maxHeightPx)
        assertEquals(0, controller.currentCeilingIndex)

        // Already at the floor: further stall batches return null.
        stall()
        assertNull(stall())
        assertEquals(0, controller.currentCeilingIndex)
    }

    @Test
    fun `counter resets after a step so a lone induced stall does not step again`() {
        controller.setRenditions(ladder3())

        stall()
        assertEquals(720, stall()?.maxHeightPx) // stepped, window cleared

        // One lone stall right after the step (the cap-induced rebuffer) must not step again.
        assertNull(stall())
        assertEquals(1, controller.currentCeilingIndex)
    }

    @Test
    fun `unknown bitrate sorts by height and caps by size only`() {
        controller.setRenditions(
            listOf(
                Rendition(1920, 1080, -1),
                Rendition(640, 360, -1),
            ),
        )
        assertFalse(controller.isSingleQuality)

        stall()
        val cap = stall() // step from 1080p ceiling down to 360p

        assertEquals(360, cap?.maxHeightPx)
        assertEquals(Int.MAX_VALUE, cap?.maxBitrateBps)
    }

    @Test
    fun `re-inventory re-arms the full range and resets the ceiling`() {
        controller.setRenditions(ladder3())
        stall()
        stall() // stepped to index 1

        controller.setRenditions(ladder3()) // track change re-inventories
        assertEquals(2, controller.currentCeilingIndex)
        assertNull(stall()) // stall history also cleared
    }

    @Test
    fun `S1508 - stalls spread wider than the decay window never reach the threshold`() {
        controller.setRenditions(ladder3())

        assertNull(stall())
        assertNull(stall(DECAY_WINDOW_MS + 1))
        assertNull(stall(DECAY_WINDOW_MS + 1))
        assertEquals(2, controller.currentCeilingIndex)
    }

    @Test
    fun `S1508 - two stalls exactly one window apart still count as a cluster`() {
        controller.setRenditions(ladder3())

        assertNull(stall())
        // The older stall sits exactly on the window edge, which is inclusive.
        assertEquals(720, stall(DECAY_WINDOW_MS)?.maxHeightPx)
        assertEquals(1, controller.currentCeilingIndex)
    }

    @Test
    fun `S1508 - a decayed stall does not combine with a later one`() {
        controller.setRenditions(ladder3())

        assertNull(stall())
        // The first stall has aged out, so this one is alone in the window and cannot borrow it.
        assertNull(stall(DECAY_WINDOW_MS + 1))
        assertEquals(2, controller.currentCeilingIndex)

        // A genuine pair on top of the survivor does step - the window kept the right stall, not none.
        assertEquals(720, stall(1)?.maxHeightPx)
    }

    // --- S1514: the step is anchored at what is playing, not at the ceiling ---------------------

    @Test
    fun `S1514 - a step from a low playing rung lands below it, not below the ceiling`() {
        controller.setRenditions(ladder3())

        // The ladder is ascending, so index 2 is 1080 and index 0 is 360. ABR has already dropped to the
        // middle rung while the ceiling is still at the top: decrementing the ceiling would put it at 720,
        // exactly where the picture already is, and change nothing the viewer could see.
        assertNull(stall(playing = Rendition(1280, 720, 2_500_000)))
        val cap = stall(playing = Rendition(1280, 720, 2_500_000))

        assertEquals(360, cap?.maxHeightPx)
        assertEquals(0, controller.currentCeilingIndex)
    }

    @Test
    fun `S1514 - without the playing rendition the step still falls back to the ceiling`() {
        controller.setRenditions(ladder3())

        // The old behaviour, kept for the audio-only and unknown-format cases.
        assertNull(stall())
        assertEquals(720, stall()?.maxHeightPx)
        assertEquals(1, controller.currentCeilingIndex)
    }

    @Test
    fun `S1514 - a playing rung above the ceiling cannot raise the anchor`() {
        controller.setRenditions(ladder3())

        assertNull(stall())
        assertEquals(720, stall()?.maxHeightPx)

        // The renderer still reports the top rung - a stale frame, or ABR not yet obeying the new cap.
        // Anchoring there would step back to 720, undoing the cap that is already in force.
        assertNull(stall(playing = Rendition(1920, 1080, 5_000_000)))
        val cap = stall(playing = Rendition(1920, 1080, 5_000_000))

        assertEquals(360, cap?.maxHeightPx)
    }

    @Test
    fun `S1514 - playing the floor yields no step at all`() {
        controller.setRenditions(ladder3())

        // The picture is already on the bottom rung. There is nothing below it, so no number of stalls
        // may produce a cap - and the controller must say so rather than report a step it did not make.
        // Before this ticket the same stalls would have walked the ceiling down twice, logging both.
        assertNull(stall(playing = Rendition(640, 360, 800_000)))
        assertNull(stall(playing = Rendition(640, 360, 800_000)))
        assertNull(stall(playing = Rendition(640, 360, 800_000)))
        assertNull(stall(playing = Rendition(640, 360, 800_000)))

        assertEquals(2, controller.currentCeilingIndex)
    }

    @Test
    fun `S1514 - a format the ladder does not carry verbatim is placed by height`() {
        controller.setRenditions(ladder3())

        // 960x540 is on no rung; the highest rung no taller than it is 640x360, at index 0 of the
        // ascending ladder.
        assertEquals(0, controller.indexOfPlaying(Rendition(960, 540, 1_500_000)))
        // An exact size wins over the height fallback even when the bitrate differs.
        assertEquals(1, controller.indexOfPlaying(Rendition(1280, 720, 9_999)))
        // Shorter than every rung - unplaceable, so the anchor falls back to the ceiling.
        assertNull(controller.indexOfPlaying(Rendition(320, 180, 200_000)))
    }

    private companion object {
        /** Mirrors the controller's private `STALL_DECAY_WINDOW_MS`. */
        const val DECAY_WINDOW_MS = 120_000L
    }
}
