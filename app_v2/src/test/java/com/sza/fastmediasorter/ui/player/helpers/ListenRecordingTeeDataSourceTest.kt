package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * S2881: nothing shaped like this decorator existed in the tree before, so the file it writes has no
 * precedent to inherit correctness from - and strategic §7 names a truncated or wrong recording as a
 * live risk against a response that declares no length.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
@UnstableApi
class ListenRecordingTeeDataSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `the written file is byte-identical to what the player read`() {
        val chunks = listOf("ADTS".toByteArray(), "frame-one".toByteArray(), "frame-two".toByteArray())
        val target = temporaryFolder.newFile("recording.aac")

        val played = playThrough(FakeSource(chunks), sinkTo(target))

        assertArrayEquals(chunks.reduce { a, b -> a + b }, target.readBytes())
        assertArrayEquals(target.readBytes(), played)
    }

    @Test
    fun `a close in the middle of the stream keeps what was already heard`() {
        val chunks = listOf("first".toByteArray(), "second".toByteArray(), "never-read".toByteArray())
        val target = temporaryFolder.newFile("partial.aac")
        val source = ListenRecordingTeeDataSource(FakeSource(chunks), sinkTo(target))

        source.open(DataSpec(Uri.parse(STREAM_URL)))
        val buffer = ByteArray(BUFFER_SIZE)
        source.read(buffer, 0, buffer.size)
        source.read(buffer, 0, buffer.size)
        source.close()

        assertArrayEquals("firstsecond".toByteArray(), target.readBytes())
    }

    @Test
    fun `a delegate that throws still leaves a whole file behind`() {
        val chunks = listOf("heard".toByteArray())
        val target = temporaryFolder.newFile("dropped.aac")
        val source = ListenRecordingTeeDataSource(FakeSource(chunks, throwAfterChunks = true), sinkTo(target))

        source.open(DataSpec(Uri.parse(STREAM_URL)))
        val buffer = ByteArray(BUFFER_SIZE)
        source.read(buffer, 0, buffer.size)
        val thrown = runCatching { source.read(buffer, 0, buffer.size) }.exceptionOrNull()

        assertTrue("the read error must reach the player", thrown is IOException)
        assertArrayEquals("heard".toByteArray(), target.readBytes())
    }

    @Test
    fun `a sink that refuses to write does not interrupt playback`() {
        val chunks = listOf("one".toByteArray(), "two".toByteArray())
        val refusingSink: (Uri) -> OutputStream = {
            object : ByteArrayOutputStream() {
                override fun write(b: ByteArray, off: Int, len: Int) = throw IOException("disk full")
            }
        }

        val played = playThrough(FakeSource(chunks), refusingSink)

        assertArrayEquals("onetwo".toByteArray(), played)
    }

    @Test
    fun `an address that is not being recorded plays without a file`() {
        val chunks = listOf("radio".toByteArray())
        val target = temporaryFolder.newFile("untouched.aac")

        val played = playThrough(FakeSource(chunks), openSink = { null })

        assertArrayEquals("radio".toByteArray(), played)
        assertEquals(0L, target.length())
    }

    /** Read the whole source the way the player does, and return every byte it handed over. */
    private fun playThrough(delegate: DataSource, openSink: (Uri) -> OutputStream?): ByteArray {
        val source = ListenRecordingTeeDataSource(delegate, openSink)
        val heard = ByteArrayOutputStream()
        source.open(DataSpec(Uri.parse(STREAM_URL)))
        val buffer = ByteArray(BUFFER_SIZE)
        var read = source.read(buffer, 0, buffer.size)
        while (read != C.RESULT_END_OF_INPUT) {
            heard.write(buffer, 0, read)
            read = source.read(buffer, 0, buffer.size)
        }
        source.close()
        return heard.toByteArray()
    }

    private fun sinkTo(file: File): (Uri) -> OutputStream = { FileOutputStream(file) }

    /**
     * Hands back one chunk per read, which is what the real stream does - a single-read fake would
     * hide exactly the accumulation bug this decorator can have.
     */
    private class FakeSource(
        private val chunks: List<ByteArray>,
        private val throwAfterChunks: Boolean = false,
    ) : DataSource {

        private var index = 0

        override fun addTransferListener(transferListener: TransferListener) = Unit

        // The watch's response declares no length, and this fake must not be kinder than it is.
        override fun open(dataSpec: DataSpec): Long = C.LENGTH_UNSET.toLong()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (index == chunks.size) {
                return if (throwAfterChunks) throw IOException("the watch went away") else C.RESULT_END_OF_INPUT
            }
            val chunk = chunks[index++]
            chunk.copyInto(buffer, offset)
            return chunk.size
        }

        override fun getUri(): Uri = Uri.parse(STREAM_URL)

        override fun close() = Unit
    }

    private companion object {
        const val STREAM_URL = "http://192.168.0.9:8099/listen"
        const val BUFFER_SIZE = 64
    }
}
