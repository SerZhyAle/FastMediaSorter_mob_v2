package com.sza.fastmediasorter.data.repository.streams

import android.graphics.Bitmap
import android.graphics.Color
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealStreamFrameIngestorTest {

    private val cache = mockk<StreamFrameCache>(relaxed = true)
    private val persistentStore = mockk<StreamFramePersistentStore>(relaxed = true)
    private val ingestor = RealStreamFrameIngestor(cache, persistentStore)

    @Test
    fun `normal frame is cached and persisted`() = runTest {
        val bitmap = solidBitmap(Color.BLUE)

        assertTrue(ingestor.ingest(STREAM_URL, bitmap))

        verify(exactly = 1) { cache.put(STREAM_URL, bitmap) }
        coVerify(exactly = 1) { persistentStore.save(STREAM_URL, bitmap) }
    }

    @Test
    fun `all black frame is rejected without writes`() = runTest {
        val bitmap = solidBitmap(Color.BLACK)

        assertFalse(ingestor.ingest(STREAM_URL, bitmap))

        verify(exactly = 0) { cache.put(any(), any()) }
        coVerify(exactly = 0) { persistentStore.save(any(), any()) }
    }

    @Test
    fun `recycled frame is rejected without writes`() = runTest {
        val bitmap = solidBitmap(Color.BLUE).apply { recycle() }

        assertFalse(ingestor.ingest(STREAM_URL, bitmap))

        verify(exactly = 0) { cache.put(any(), any()) }
        coVerify(exactly = 0) { persistentStore.save(any(), any()) }
    }

    private fun solidBitmap(color: Int): Bitmap =
        Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }

    private companion object {
        const val STREAM_URL = "rtsp://example.test/live"
        const val BITMAP_SIZE = 16
    }
}
