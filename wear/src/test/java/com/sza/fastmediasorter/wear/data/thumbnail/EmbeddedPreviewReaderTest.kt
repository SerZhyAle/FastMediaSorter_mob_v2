package com.sza.fastmediasorter.wear.data.thumbnail

import com.sza.fastmediasorter.wear.util.WearThumbnailBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

private const val OVERSIZE_FACTOR = 4
private const val SMALL_FILE_BYTES = 512

/**
 * The head read is what makes a grid of network photos affordable, so the byte ceiling is asserted
 * against a counting stream rather than inferred from the shape of the code.
 */
class EmbeddedPreviewReaderTest {

    private class CountingStream(size: Int) : InputStream() {
        private val delegate = ByteArrayInputStream(ByteArray(size))
        var bytesRead: Int = 0
            private set
        var closed: Boolean = false
            private set

        override fun read(): Int = delegate.read().also { if (it >= 0) bytesRead++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            delegate.read(b, off, len).also { if (it > 0) bytesRead += it }

        override fun close() {
            closed = true
            delegate.close()
        }
    }

    @Test
    fun `reads no more than the budgeted number of bytes`() {
        val stream = CountingStream(WearThumbnailBudget.MAX_HEAD_READ_BYTES * OVERSIZE_FACTOR)

        EmbeddedPreviewReader().readHead(stream)

        assertTrue(
            "read ${stream.bytesRead} bytes, budget is ${WearThumbnailBudget.MAX_HEAD_READ_BYTES}",
            stream.bytesRead <= WearThumbnailBudget.MAX_HEAD_READ_BYTES
        )
    }

    @Test
    fun `closes the stream without draining it`() {
        val size = WearThumbnailBudget.MAX_HEAD_READ_BYTES * OVERSIZE_FACTOR
        val stream = CountingStream(size)

        EmbeddedPreviewReader().readHead(stream)

        assertTrue("stream left open", stream.closed)
        assertTrue("stream was drained", stream.bytesRead < size)
    }

    @Test
    fun `returns the whole file when it is smaller than the budget`() {
        val stream = CountingStream(SMALL_FILE_BYTES)

        val head = EmbeddedPreviewReader().readHead(stream)

        assertEquals(SMALL_FILE_BYTES, head.size)
    }

    @Test
    fun `returns an empty head for an empty stream`() {
        val stream = CountingStream(0)

        val head = EmbeddedPreviewReader().readHead(stream)

        assertEquals(0, head.size)
        assertTrue("stream left open", stream.closed)
    }
}
