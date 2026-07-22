package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.ui.player.helpers.StreamQualityStepDownController.Rendition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1128: off-device coverage of the quality step-down policy. No Media3 dependency, so the threshold,
 * hysteresis, floor, and single-quality branches are all exercised deterministically.
 */
class StreamQualityStepDownControllerTest {

    private val controller = StreamQualityStepDownController()

    private fun ladder3() = listOf(
        Rendition(1920, 1080, 5_000_000),
        Rendition(1280, 720, 2_500_000),
        Rendition(640, 360, 800_000),
    )

    @Test
    fun `single-quality ladder never steps down`() {
        controller.setRenditions(listOf(Rendition(1280, 720, 2_500_000)))

        assertTrue(controller.isSingleQuality)
        assertNull(controller.registerStall())
        assertNull(controller.registerStall())
        assertNull(controller.registerStall())
    }

    @Test
    fun `empty ladder is inert`() {
        controller.setRenditions(emptyList())

        assertTrue(controller.isSingleQuality)
        assertNull(controller.registerStall())
    }

    @Test
    fun `stall below threshold does not step`() {
        controller.setRenditions(ladder3())

        assertNull(controller.registerStall())
    }

    @Test
    fun `stall at threshold steps down one rung to the next-lower rendition`() {
        controller.setRenditions(ladder3())
        // Ceiling starts at the top (index 2 = 1080p). Threshold is 2 stalls.
        assertNull(controller.registerStall())
        val cap = controller.registerStall()

        assertEquals(1280, cap?.maxWidthPx)
        assertEquals(720, cap?.maxHeightPx)
        assertEquals(2_500_000, cap?.maxBitrateBps)
        assertEquals(1, controller.currentCeilingIndex)
    }

    @Test
    fun `repeated stall batches cascade down one rung each and stop at the floor`() {
        controller.setRenditions(ladder3())

        controller.registerStall()
        val toMid = controller.registerStall() // -> 720p (index 1)
        assertEquals(720, toMid?.maxHeightPx)

        controller.registerStall()
        val toFloor = controller.registerStall() // -> 360p (index 0)
        assertEquals(360, toFloor?.maxHeightPx)
        assertEquals(0, controller.currentCeilingIndex)

        // Already at the floor: further stall batches return null.
        controller.registerStall()
        assertNull(controller.registerStall())
        assertEquals(0, controller.currentCeilingIndex)
    }

    @Test
    fun `counter resets after a step so a lone induced stall does not step again`() {
        controller.setRenditions(ladder3())

        controller.registerStall()
        assertEquals(720, controller.registerStall()?.maxHeightPx) // stepped, counter reset

        // One lone stall right after the step (the cap-induced rebuffer) must not step again.
        assertNull(controller.registerStall())
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

        controller.registerStall()
        val cap = controller.registerStall() // step from 1080p ceiling down to 360p

        assertEquals(360, cap?.maxHeightPx)
        assertEquals(Int.MAX_VALUE, cap?.maxBitrateBps)
    }

    @Test
    fun `re-inventory re-arms the full range and resets the ceiling`() {
        controller.setRenditions(ladder3())
        controller.registerStall()
        controller.registerStall() // stepped to index 1

        controller.setRenditions(ladder3()) // track change re-inventories
        assertEquals(2, controller.currentCeilingIndex)
        assertNull(controller.registerStall()) // counter also reset
    }
}
