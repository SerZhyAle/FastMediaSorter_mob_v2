package com.sza.fastmediasorter.wear.data.files

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** S3950: the staging and publishing copies stop at the next chunk once their caller is cancelled. */
class CopyToCancellableTest {

    @Test
    fun `a live copy moves every byte and reports the count`() = runTest {
        val source = ByteArray(SIZE) { it.toByte() }
        val sink = ByteArrayOutputStream()

        val copied = ByteArrayInputStream(source).copyToCancellable(sink)

        assertEquals(SIZE.toLong(), copied)
        assertArrayEquals(source, sink.toByteArray())
    }

    @Test
    fun `a cancelled copy stops after the chunk it was reading`() = runTest {
        val job = Job()
        val sink = ByteArrayOutputStream()
        // Cancels the caller while the first chunk is being read, as a user leaving the screen would.
        val source = object : InputStream() {
            private val delegate = ByteArrayInputStream(ByteArray(SIZE))

            override fun read(): Int = delegate.read()

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                job.cancel()
                return delegate.read(b, off, len)
            }
        }

        val thrown = runCatching { withContext(job) { source.copyToCancellable(sink) } }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(0, sink.size())
    }

    private companion object {
        const val SIZE = DEFAULT_BUFFER_SIZE * 3 + 17
    }
}
