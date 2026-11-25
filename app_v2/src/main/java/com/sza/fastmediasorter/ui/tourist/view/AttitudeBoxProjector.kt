package com.sza.fastmediasorter.ui.tourist.view

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * S4560: projects a phone-shaped box, turned by a device attitude quaternion, onto the gyroscope tile.
 *
 * The camera sits behind the user and above, looking down at [VIEW_TILT_DEGREES]. The game rotation vector
 * has no fixed north, so the camera's heading is taken from the first attitude after [resetReference]: the
 * box starts facing the user and then turns exactly as much as the phone does. Without that anchor the
 * same grip could show the box from its back on one phone and from its front on another.
 *
 * Output is in model units, x right and y up; the view scales and flips it. Pure Kotlin so it is unit tested.
 */
class AttitudeBoxProjector {

    // Device-to-world rotation, row-major r<row><column>.
    private var r00 = 1f
    private var r01 = 0f
    private var r02 = 0f
    private var r10 = 0f
    private var r11 = 1f
    private var r12 = 0f
    private var r20 = 0f
    private var r21 = 0f
    private var r22 = 1f

    // World-to-camera rotation: heading about the vertical, then the downward tilt.
    private var c00 = 1f
    private var c01 = 0f
    private var c10 = 0f
    private var c11 = 1f
    private var c12 = 0f
    private var c20 = 0f
    private var c21 = 0f
    private var c22 = 1f

    private var referenceHeadingRad: Float? = null

    /** The next [project] call takes its pose as "facing the user". */
    fun resetReference() {
        referenceHeadingRad = null
    }

    /**
     * Fills [points] with the 8 projected corners (x, y pairs) and [shades] with one value per face of
     * [FACES]: 0 for a face turned away, otherwise 0..1 by how squarely it faces the camera.
     */
    fun project(qx: Float, qy: Float, qz: Float, qw: Float, points: FloatArray, shades: FloatArray) {
        toRotationMatrix(qx, qy, qz, qw)
        val heading = referenceHeadingRad ?: forwardHeading().also { referenceHeadingRad = it }
        buildCamera(heading)
        for (i in 0 until VERTEX_COUNT) {
            val vx = if (i and X_BIT != 0) HALF_WIDTH else -HALF_WIDTH
            val vy = if (i and Y_BIT != 0) HALF_HEIGHT else -HALF_HEIGHT
            val vz = if (i and Z_BIT != 0) HALF_DEPTH else -HALF_DEPTH
            val wx = r00 * vx + r01 * vy + r02 * vz
            val wy = r10 * vx + r11 * vy + r12 * vz
            val wz = r20 * vx + r21 * vy + r22 * vz
            val scale = FOCAL_LENGTH / (FOCAL_LENGTH - cameraZ(wx, wy, wz))
            points[i * 2] = (c00 * wx + c01 * wy) * scale
            points[i * 2 + 1] = (c10 * wx + c11 * wy + c12 * wz) * scale
        }
        for (face in 0 until FACE_COUNT) {
            shades[face] = if (signedArea(face, points) > 0f) {
                faceNormalTowardCamera(face).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    /** Standard unit-quaternion rotation; a degenerate quaternion reads as no rotation. */
    private fun toRotationMatrix(qx: Float, qy: Float, qz: Float, qw: Float) {
        val norm = sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
        val ok = norm > EPSILON && !norm.isNaN()
        val x = if (ok) qx / norm else 0f
        val y = if (ok) qy / norm else 0f
        val z = if (ok) qz / norm else 0f
        val w = if (ok) qw / norm else 1f
        r00 = 1f - 2f * (y * y + z * z)
        r01 = 2f * (x * y - z * w)
        r02 = 2f * (x * z + y * w)
        r10 = 2f * (x * y + z * w)
        r11 = 1f - 2f * (x * x + z * z)
        r12 = 2f * (y * z - x * w)
        r20 = 2f * (x * z - y * w)
        r21 = 2f * (y * z + x * w)
        r22 = 1f - 2f * (x * x + y * y)
    }

    /**
     * Where the user looks: away through the back of the phone plus along its top edge, so the heading is
     * defined both for a phone held upright (the back dominates) and one lying flat (the top edge does).
     */
    private fun forwardHeading(): Float = atan2(r01 - r02, r11 - r12)

    /** Turn the world so the reference heading points into the screen, then tilt the camera down. */
    private fun buildCamera(heading: Float) {
        val ch = cos(heading)
        val sh = sin(heading)
        val tilt = Math.toRadians(-VIEW_TILT_DEGREES.toDouble())
        val ct = cos(tilt).toFloat()
        val st = sin(tilt).toFloat()
        c00 = ch
        c01 = -sh
        c10 = ct * sh
        c11 = ct * ch
        c12 = -st
        c20 = st * sh
        c21 = st * ch
        c22 = ct
    }

    private fun cameraZ(wx: Float, wy: Float, wz: Float): Float = c20 * wx + c21 * wy + c22 * wz

    private fun faceNormalTowardCamera(face: Int): Float {
        val base = face * AXES
        val nx = NORMALS[base]
        val ny = NORMALS[base + 1]
        val nz = NORMALS[base + 2]
        return cameraZ(
            r00 * nx + r01 * ny + r02 * nz,
            r10 * nx + r11 * ny + r12 * nz,
            r20 * nx + r21 * ny + r22 * nz,
        )
    }

    /** Faces are wound counter-clockwise seen from outside, so a positive area means the face is visible. */
    private fun signedArea(face: Int, points: FloatArray): Float {
        var area = 0f
        val base = face * CORNERS_PER_FACE
        for (k in 0 until CORNERS_PER_FACE) {
            val a = FACES[base + k]
            val b = FACES[base + (k + 1) % CORNERS_PER_FACE]
            area += points[a * 2] * points[b * 2 + 1] - points[b * 2] * points[a * 2 + 1]
        }
        return area
    }
    companion object {
        const val VERTEX_COUNT = 8
        const val FACE_COUNT = 6
        const val CORNERS_PER_FACE = 4
        const val FACE_SCREEN = 0
        const val FACE_BACK = 1
        const val FACE_TOP = 4

        /** Widest projected reach in model units, so the view can fit the box at any attitude. */
        const val MAX_EXTENT = 1.4f

        /**
         * Corner indices per face; corner i has x, y, z positive when bits 0, 1, 2 are set. Order: screen,
         * back, right, left, top, bottom.
         */
        val FACES = intArrayOf(
            4, 5, 7, 6,
            0, 2, 3, 1,
            1, 3, 7, 5,
            0, 4, 6, 2,
            2, 6, 7, 3,
            0, 1, 5, 4,
        )

        private val NORMALS = floatArrayOf(
            0f, 0f, 1f,
            0f, 0f, -1f,
            1f, 0f, 0f,
            -1f, 0f, 0f,
            0f, 1f, 0f,
            0f, -1f, 0f,
        )

        private const val HALF_WIDTH = 0.5f
        private const val HALF_HEIGHT = 1f
        private const val HALF_DEPTH = 0.1f
        private const val FOCAL_LENGTH = 5f
        private const val VIEW_TILT_DEGREES = 50f
        private const val AXES = 3
        private const val X_BIT = 1
        private const val Y_BIT = 2
        private const val Z_BIT = 4
        private const val EPSILON = 1e-6f
    }
}
