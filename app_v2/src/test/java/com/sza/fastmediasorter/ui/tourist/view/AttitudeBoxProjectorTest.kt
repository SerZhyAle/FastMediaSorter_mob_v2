package com.sza.fastmediasorter.ui.tourist.view

import com.sza.fastmediasorter.ui.tourist.view.AttitudeBoxProjector.Companion.FACE_BACK
import com.sza.fastmediasorter.ui.tourist.view.AttitudeBoxProjector.Companion.FACE_COUNT
import com.sza.fastmediasorter.ui.tourist.view.AttitudeBoxProjector.Companion.FACE_SCREEN
import com.sza.fastmediasorter.ui.tourist.view.AttitudeBoxProjector.Companion.FACE_TOP
import com.sza.fastmediasorter.ui.tourist.view.AttitudeBoxProjector.Companion.MAX_EXTENT
import com.sza.fastmediasorter.ui.tourist.view.AttitudeBoxProjector.Companion.VERTEX_COUNT
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class AttitudeBoxProjectorTest {

    private val points = FloatArray(VERTEX_COUNT * 2)
    private val shades = FloatArray(FACE_COUNT)

    @Test
    fun `flat phone shows its screen and hides its back`() {
        AttitudeBoxProjector().project(0f, 0f, 0f, 1f, points, shades)

        assertTrue(shades[FACE_SCREEN] > 0f)
        assertEquals(0f, shades[FACE_BACK], 0f)
    }

    @Test
    fun `upright phone faces the user with its top edge up`() {
        val half = sqrt(0.5f)
        AttitudeBoxProjector().project(half, 0f, 0f, half, points, shades)

        assertTrue(shades[FACE_SCREEN] > 0f)
        assertEquals(0f, shades[FACE_BACK], 0f)
        // Corner 6 is top-left on the screen side, corner 4 bottom-left.
        assertTrue(points[6 * 2 + 1] > points[4 * 2 + 1])
        assertTrue(shades[FACE_TOP] > 0f)
    }

    @Test
    fun `first pose is the reference so any starting heading draws the same box`() {
        val identity = FloatArray(points.size)
        AttitudeBoxProjector().project(0f, 0f, 0f, 1f, identity, shades)

        val half = sqrt(0.5f)
        AttitudeBoxProjector().project(0f, 0f, half, half, points, shades)

        assertArrayEquals(identity, points, TOLERANCE)
    }

    @Test
    fun `turning after the reference turns the box`() {
        val projector = AttitudeBoxProjector()
        val before = FloatArray(points.size)
        projector.project(0f, 0f, 0f, 1f, before, shades)

        val half = sqrt(0.5f)
        projector.project(0f, 0f, half, half, points, shades)

        assertTrue(points.indices.any { abs(points[it] - before[it]) > TOLERANCE })
    }

    @Test
    fun `degenerate quaternion draws the resting box without NaN`() {
        AttitudeBoxProjector().project(0f, 0f, 0f, 0f, points, shades)

        assertTrue(points.none { it.isNaN() || abs(it) > MAX_EXTENT })
        assertTrue(shades[FACE_SCREEN] > 0f)
    }

    @Test
    fun `every corner stays inside the fitted extent at an arbitrary attitude`() {
        val projector = AttitudeBoxProjector()
        projector.project(0f, 0f, 0f, 1f, points, shades)
        projector.project(0.3f, -0.5f, 0.6f, 0.55f, points, shades)

        assertTrue(points.all { abs(it) <= MAX_EXTENT })
        assertTrue(shades.count { it > 0f } in 1..3)
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
