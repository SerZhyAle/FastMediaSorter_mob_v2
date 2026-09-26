package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WAVE-PARTICLES section 4 as amended in 0.12, the particle step of [AudioWaveParticleView]. The
 * scenario that broke the old bounce: one tick at the stall cap carries the fastest particle past an
 * edge, then 60 Hz ticks follow, each returning less than the overshoot.
 */
class WaveParticleEdgeTest {

    @Test
    fun `a stall-capped step past either edge lands inside and moving inward`() {
        val (left, leftVelocity) = step(1f, -MAX_SPEED, STALL_FRAMES)
        assertInside(left)
        assertTrue("velocity after leaving through 0: $leftVelocity", leftVelocity > 0f)

        val (right, rightVelocity) = step(EXTENT - 1f, MAX_SPEED, STALL_FRAMES)
        assertInside(right)
        assertTrue("velocity after leaving through the far edge: $rightVelocity", rightVelocity < 0f)
    }

    @Test
    fun `single-frame ticks after a stall never leave the canvas`() {
        var x = 0.5f
        var v = -MAX_SPEED
        repeat(TICKS) { tick ->
            val (nextX, nextV) = step(x, v, if (tick == 0) STALL_FRAMES else 1f)
            x = nextX
            v = nextV
            assertInside(x, "tick $tick")
        }
    }

    @Test
    fun `a velocity already pointing inward is kept`() {
        assertEquals(0.5f, WaveParticleEdge.inward(-2f, 0.5f, EXTENT), 0f)
        assertEquals(-0.5f, WaveParticleEdge.inward(EXTENT + 2f, -0.5f, EXTENT), 0f)
    }

    @Test
    fun `an overshoot longer than the canvas stops at the opposite edge`() {
        assertEquals(EXTENT, WaveParticleEdge.reflect(-3 * EXTENT, EXTENT), 0f)
        assertEquals(0f, WaveParticleEdge.reflect(3 * EXTENT, EXTENT), 0f)
    }

    @Test
    fun `a particle inside the canvas is untouched`() {
        for (position in listOf(0f, 40f, EXTENT)) {
            assertEquals(position, WaveParticleEdge.reflect(position, EXTENT), 0f)
            assertEquals(-0.3f, WaveParticleEdge.inward(position, -0.3f, EXTENT), 0f)
        }
    }

    private fun step(x: Float, v: Float, frames: Float): Pair<Float, Float> {
        val moved = x + v * frames
        return WaveParticleEdge.reflect(moved, EXTENT) to WaveParticleEdge.inward(moved, v, EXTENT)
    }

    private fun assertInside(x: Float, where: String = "") {
        assertTrue("$where x=$x outside [0, $EXTENT]", x in 0f..EXTENT)
    }

    private companion object {
        const val EXTENT = 320f

        // Section 3.2: the fastest particle on one axis - top of the directional speed at the top speed
        // multiplier, plus the widest jitter.
        const val MAX_SPEED = (0.12f + 0.42f) * 1.5f + 0.14f * 1.5f

        // The renderer's MAX_FRAME_CATCHUP.
        const val STALL_FRAMES = 4f
        const val TICKS = 600
    }
}
