package com.sza.fastmediasorter.core.share.handlers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class WatchShareTargetStagingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `finished copy returns the staged path with identical bytes`() = runBlocking {
        val payload = ByteArray(PAYLOAD_SIZE) { it.toByte() }
        val target = File(tempFolder.root, "clip.mp4")

        val path = WatchShareTargetHandler.stageStream(target) { ByteArrayInputStream(payload) }

        assertEquals(target.absolutePath, path)
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun `cancelling mid-copy stops the copy and removes the staged file`() = runBlocking {
        val target = File(tempFolder.root, "large.mp4")
        lateinit var job: Job
        // Never reaches end of stream: only the per-chunk cancellation check can end this copy.
        val endless = object : InputStream() {
            var reads = 0
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reads++
                if (reads == CANCEL_AT_READ) job.cancel()
                return len
            }
        }

        job = launch(Dispatchers.IO) { WatchShareTargetHandler.stageStream(target) { endless } }
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(endless.reads <= CANCEL_AT_READ + 1)
        assertFalse(target.exists())
    }

    @Test
    fun `missing stream yields no path and no staged file`() = runBlocking {
        val target = File(tempFolder.root, "missing.mp4")

        assertNull(WatchShareTargetHandler.stageStream(target) { null })
        assertFalse(target.exists())
    }

    private companion object {
        const val PAYLOAD_SIZE = 200_000
        const val CANCEL_AT_READ = 3
    }
}
