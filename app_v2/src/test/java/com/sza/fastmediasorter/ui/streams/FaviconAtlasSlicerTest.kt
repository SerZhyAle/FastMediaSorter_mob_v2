package com.sza.fastmediasorter.ui.streams

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * S0668 / PHASE_04: the index->rect math (pure) plus a decode of the committed fixture atlas.
 *
 * Fixture geometry: `favicon-atlas.png` is 64x64 = a 2x2 grid of 32 px tiles. Under the FIXED
 * 16-column contract only four indices land in-bounds: 0 (top-left), 1 (top-right), 16 (bottom-left),
 * 17 (bottom-right). Index 3 - which a naive 4-wide reading would call "tile-3" - is intentionally OUT
 * of bounds here (`rectFor(3)` starts at x=96, past a 64 px-wide atlas), exercising the guard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FaviconAtlasSlicerTest {

    private val slicer = FaviconAtlasSlicer { fixtureAtlasFile() }

    @Test
    fun `rectFor matches the 32px 16-col contract`() {
        assertEquals(android.graphics.Rect(0, 0, 32, 32), slicer.rectFor(0))
        assertEquals(android.graphics.Rect(32, 0, 64, 32), slicer.rectFor(1))
        // index 16 wraps to the second row (16 % 16 = 0, 16 / 16 = 1).
        assertEquals(android.graphics.Rect(0, 32, 32, 64), slicer.rectFor(16))
        assertEquals(android.graphics.Rect(32, 32, 64, 64), slicer.rectFor(17))
    }

    @Test
    fun `isInBounds rejects negative and over-range indices`() {
        assertFalse(slicer.isInBounds(-1, 64, 64))
        // index 3 -> rect (96,0,128,32), exceeds a 64x64 atlas.
        assertFalse(slicer.isInBounds(3, 64, 64))
        // index 17 -> rect (32,32,64,64), fits exactly.
        assertTrue(slicer.isInBounds(17, 64, 64))
        assertTrue(slicer.isInBounds(0, 64, 64))
    }

    @Test
    fun `tileFor returns a 32x32 tile whose centre matches the fixture colour`() = runTest {
        val tile0 = slicer.tileFor(0)
        assertNotNull("index 0 must decode", tile0)
        assertEquals(32, tile0!!.width)
        assertEquals(32, tile0.height)
        // Tile 0 (top-left) is solid red in the fixture.
        assertColorClose(0xFFDC2828.toInt(), tile0.getPixel(16, 16))

        // Tile 17 (bottom-right) is solid yellow.
        val tile17 = slicer.tileFor(17)
        assertNotNull(tile17)
        assertColorClose(0xFFF0D21E.toInt(), tile17!!.getPixel(16, 16))
    }

    @Test
    fun `tileFor returns null for an out-of-bounds index`() = runTest {
        // index 3 is out of bounds for the 64x64 fixture (see class doc).
        assertNull(slicer.tileFor(3))
        assertNull(slicer.tileFor(-1))
    }

    @Test
    fun `tileFor returns null when the atlas file is absent`() = runTest {
        val noAtlas = FaviconAtlasSlicer { null }
        assertNull(noAtlas.tileFor(0))
    }

    private fun assertColorClose(expected: Int, actual: Int) {
        // PNG round-trip + decode can shift channels by a hair; allow a small tolerance.
        val er = (expected shr 16) and 0xFF
        val eg = (expected shr 8) and 0xFF
        val eb = expected and 0xFF
        val ar = (actual shr 16) and 0xFF
        val ag = (actual shr 8) and 0xFF
        val ab = actual and 0xFF
        val close = Math.abs(er - ar) <= 8 && Math.abs(eg - ag) <= 8 && Math.abs(eb - ab) <= 8
        assertTrue(
            "colour mismatch: expected ~#%08X but was #%08X".format(expected, actual),
            close,
        )
    }

    /** Copies the committed test-resource atlas to a temp file so BitmapFactory.decodeFile can read it. */
    private fun fixtureAtlasFile(): File {
        val stream = javaClass.classLoader!!.getResourceAsStream("streams/favicon-atlas.png")
            ?: error("fixture favicon-atlas.png not found on the test classpath")
        val tmp = File.createTempFile("favicon-atlas", ".png")
        stream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        tmp.deleteOnExit()
        return tmp
    }
}
