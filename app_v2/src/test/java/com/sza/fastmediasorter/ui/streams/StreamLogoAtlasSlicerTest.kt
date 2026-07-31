package com.sza.fastmediasorter.ui.streams

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * S1201/S1245: the pure index->rect geometry of [StreamLogoAtlasSlicer] on the 136x136 tile /
 * 59-column contract (even tile edge - the sheet is lossy 4:2:0 WebP, an odd edge bleeds chroma
 * across tiles). Geometry only - no bitmap decode here (Robolectric supplies a real [Rect]).
 *
 * The grid is a two-party contract with the offline packer; the parity test reads the packer's
 * constants straight from its script so a drift on either side turns this suite red instead of
 * silently mis-cropping every logo (the S1245 failure mode was the reverse: the contract moved
 * and this class kept asserting the retired 135/60 grid).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamLogoAtlasSlicerTest {

    private val slicer = StreamLogoAtlasSlicer { null }

    @Test
    fun `rectFor matches the 136x136 59-col contract`() {
        val tile = StreamLogoAtlasSlicer.TILE_W
        assertEquals(Rect(0, 0, tile, tile), slicer.rectFor(0))
        assertEquals(Rect(tile, 0, 2 * tile, tile), slicer.rectFor(1))
        // index COLS wraps to the second row (COLS % COLS = 0, COLS / COLS = 1, top = tile).
        assertEquals(Rect(0, tile, tile, 2 * tile), slicer.rectFor(StreamLogoAtlasSlicer.COLS))
        // one before the wrap is the last column of the first row.
        val lastCol = StreamLogoAtlasSlicer.COLS - 1
        assertEquals(Rect(lastCol * tile, 0, (lastCol + 1) * tile, tile), slicer.rectFor(lastCol))
    }

    @Test
    fun `rectFor column wraps at COLS`() {
        val cols = StreamLogoAtlasSlicer.COLS
        // index cols+2 -> row 1, col 2 (272 = 2 * 136).
        val rect = slicer.rectFor(cols + 2)
        assertEquals(2 * StreamLogoAtlasSlicer.TILE_W, rect.left)
        assertEquals(StreamLogoAtlasSlicer.TILE_H, rect.top)
    }

    @Test
    fun `tile is square unlike the preview atlas`() {
        // The two atlases share a slicer shape but not a geometry; a copy-paste that dragged the
        // preview's 240x135 across would silently mis-crop every logo.
        assertEquals(StreamLogoAtlasSlicer.TILE_W, StreamLogoAtlasSlicer.TILE_H)
    }

    @Test
    fun `isInBounds rejects negative and over-range indices on the packer row budget`() {
        // Sheet height varies per publish (rows = ceil(count / COLS)); the packer's row BUDGET is
        // the stable ceiling, so bounds are asserted against a full-budget sheet rather than a
        // point-in-time published size that goes stale on the next catalog publish (S1245).
        val cols = StreamLogoAtlasSlicer.COLS
        val w = cols * StreamLogoAtlasSlicer.TILE_W
        val h = PACKER_ROW_BUDGET * StreamLogoAtlasSlicer.TILE_H
        assertFalse("negative index is out of bounds", slicer.isInBounds(-1, w, h))
        val overflowIndex = PACKER_ROW_BUDGET * cols
        assertFalse("a rect past the sheet bottom is out of bounds", slicer.isInBounds(overflowIndex, w, h))
        assertTrue("an in-range index fits", slicer.isInBounds(0, w, h))
        assertTrue("last tile of the last row fits", slicer.isInBounds(overflowIndex - 1, w, h))
    }

    @Test
    fun `app grid constants match the offline packer script`() {
        // Two halves of one contract: scripts/streams/collect-stream-candidates.ps1 writes the
        // sheet this slicer cuts. Unit tests run with the module as the working directory.
        val packer = File("../scripts/streams/collect-stream-candidates.ps1")
        assertTrue("packer script not found at ${packer.absolutePath}", packer.exists())
        val text = packer.readText()
        assertEquals(
            StreamLogoAtlasSlicer.TILE_W,
            packerValue(text, "LogoTileW"),
        )
        assertEquals(
            StreamLogoAtlasSlicer.TILE_H,
            packerValue(text, "LogoTileH"),
        )
        assertEquals(
            StreamLogoAtlasSlicer.COLS,
            packerValue(text, "LogoCols"),
        )
        assertEquals(PACKER_ROW_BUDGET, packerValue(text, "LogoMaxRows"))
    }

    private fun packerValue(script: String, name: String): Int {
        val match = Regex("\\\$script:$name\\s*=\\s*(\\d+)").find(script)
        assertTrue("packer constant $name not found", match != null)
        return match!!.groupValues[1].toInt()
    }

    private companion object {
        /** Mirrors `$script:LogoMaxRows` - the packer's sheet-capacity ceiling. */
        const val PACKER_ROW_BUDGET = 60
    }
}
