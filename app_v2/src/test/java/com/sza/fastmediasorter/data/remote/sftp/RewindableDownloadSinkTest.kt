package com.sza.fastmediasorter.data.remote.sftp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class RewindableDownloadSinkTest {

    private val full = "0123456789".toByteArray()

    @Test
    fun `a retried file download holds the file once, not the partial bytes before it`() {
        val file = File.createTempFile("sink", ".bin")
        try {
            FileOutputStream(file).use { out ->
                val sink = RewindableDownloadSink(out)
                sink.write(full, 0, 4)
                assertTrue(sink.rewind())
                sink.write(full, 0, full.size)
            }
            assertArrayEquals(full, file.readBytes())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `an append-mode rewind keeps the content written before the download`() {
        val file = File.createTempFile("sink", ".bin")
        try {
            file.writeBytes("head".toByteArray())
            FileOutputStream(file, true).use { out ->
                val sink = RewindableDownloadSink(out)
                sink.write(full, 0, 3)
                assertTrue(sink.rewind())
                sink.write(full, 0, full.size)
            }
            assertEquals("head0123456789", file.readText())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a byte array destination is reset`() {
        val out = ByteArrayOutputStream()
        val sink = RewindableDownloadSink(out)
        sink.write(full, 0, 5)
        assertTrue(sink.rewind())
        sink.write(full, 0, full.size)
        assertArrayEquals(full, out.toByteArray())
    }

    @Test
    fun `a stream that cannot be rewound refuses the retry once bytes were written`() {
        val sink = RewindableDownloadSink(BufferedOutputStream(ByteArrayOutputStream()))
        assertTrue(sink.rewind())
        sink.write(1)
        assertFalse(sink.rewind())
    }
}
