package com.sza.fastmediasorter.ui.streams

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * S1445: the container contract of [StreamTilePackReader] - entry name is the slot index as a plain
 * decimal string. That name is the one thing that can silently drift between the offline packer and
 * the app, so it is asserted against a real ZIP rather than a mock. Bitmap decoding itself is
 * Robolectric's, so these tests prove entry addressing, not image fidelity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamTilePackReaderTest {

    private val context = RuntimeEnvironment.getApplication()
    private val budget = StreamTilePackReader.budgetBytes(context)

    private fun packWithEntries(vararg indices: Int): File {
        val file = File(context.filesDir, "tiles-${indices.joinToString("-")}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            indices.forEach { index ->
                zip.putNextEntry(ZipEntry(index.toString()))
                zip.write(byteArrayOf(0x52, 0x49, 0x46, 0x46))
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `absent pack reports no pack and yields no tile`() = runTest {
        val reader = StreamTilePackReader({ null }, budget)

        assertFalse(reader.hasPack())
        assertNull(reader.tile(0))
    }

    @Test
    fun `present pack reports a pack`() {
        val reader = StreamTilePackReader({ packWithEntries(0, 1, 2) }, budget)

        assertTrue(reader.hasPack())
    }

    @Test
    fun `entry is addressed by the plain decimal index`() = runTest {
        val reader = StreamTilePackReader({ packWithEntries(0, 1, 2) }, budget)

        assertNotNull("index 1 must resolve to the entry named 1", reader.tile(1))
    }

    @Test
    fun `index with no entry yields no tile`() = runTest {
        val reader = StreamTilePackReader({ packWithEntries(0, 1, 2) }, budget)

        assertNull(reader.tile(7))
    }

    @Test
    fun `negative index yields no tile`() = runTest {
        val reader = StreamTilePackReader({ packWithEntries(0) }, budget)

        assertNull(reader.tile(-1))
    }

    @Test
    fun `invalidate is safe before any read and repeatedly`() = runTest {
        val reader = StreamTilePackReader({ packWithEntries(0) }, budget)

        reader.invalidate()
        reader.invalidate()
        assertNotNull("the pack must reopen after invalidation", reader.tile(0))
        reader.invalidate()
    }

    @Test
    fun `budget stays inside the clamp`() {
        assertTrue(budget >= 4 * 1024 * 1024)
        assertTrue(budget <= 24 * 1024 * 1024)
    }
}
