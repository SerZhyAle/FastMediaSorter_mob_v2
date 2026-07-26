package com.sza.fastmediasorter.data.repository.streams

import android.graphics.Bitmap
import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * S1169: [StreamFrameCache] paints the last captured frame until a fresher one lands, decouples
 * freshness (drives re-capture) from what is shown, and evicts by total byte footprint - not a fixed
 * row count - so a still-visible tile is not dropped just because the catalog is large.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamFrameCacheTest {

    private val cache = StreamFrameCache()

    // 640x360 ARGB_8888 ~= 0.9 MB each, so ~14 of them exceed the 12 MB budget.
    private fun frame(): Bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)

    @Test
    fun `get returns the last stored frame`() {
        val bitmap = frame()
        cache.put("u", bitmap)
        assertSame(bitmap, cache.get("u"))
    }

    @Test
    fun `restored entry is shown but never counts as fresh`() {
        cache.putRestored("u", frame())
        assertNotNull(cache.get("u"))
        assertFalse(cache.isFresh("u"))
    }

    @Test
    fun `a live entry stops being fresh once its TTL elapses`() {
        cache.put("u", frame())
        assertTrue(cache.isFresh("u"))
        ShadowSystemClock.advanceBy(Duration.ofMillis(61_000L))
        assertFalse(cache.isFresh("u"))
        // Still shown (get is decoupled from freshness) until a fresh capture replaces it.
        assertNotNull(cache.get("u"))
    }

    @Test
    fun `putRestored does not clobber an existing live entry`() {
        val live = frame()
        cache.put("u", live)
        cache.putRestored("u", frame())
        assertSame(live, cache.get("u"))
        assertTrue(cache.isFresh("u"))
    }

    @Test
    fun `oldest frames are evicted once the byte budget is exceeded`() {
        repeat(BIG_INSERT) { i -> cache.put("u$i", frame()) }
        // The earliest inserts must have been evicted; the most recent must survive.
        assertNull(cache.get("u0"))
        assertNotNull(cache.get("u${BIG_INSERT - 1}"))
    }

    @Test
    fun `advanceClock helper keeps SystemClock usable`() {
        // Guards the Robolectric elapsed-realtime shadow this suite relies on.
        val before = SystemClock.elapsedRealtime()
        ShadowSystemClock.advanceBy(Duration.ofMillis(1_000L))
        assertEquals(before + 1_000L, SystemClock.elapsedRealtime())
    }

    private companion object {
        const val BIG_INSERT = 20
    }
}
