package com.sza.fastmediasorter.data.link.streaming

import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class CachedStreamAssemblerTest {

    private fun format(height: Int = Format.NO_VALUE, bitrate: Int = Format.NO_VALUE): Format =
        Format.Builder().setHeight(height).setPeakBitrate(bitrate).build()

    @Test
    fun `picks the tallest rendition within the cap`() {
        val formats = listOf(format(1080, 5_000_000), format(480, 1_000_000), format(720, 2_500_000))
        assertEquals(2, pickVideoIndex(formats, maxHeightPx = 720))
    }

    @Test
    fun `picks the tallest rendition when the cap is unbounded`() {
        val formats = listOf(format(480, 1_000_000), format(1080, 5_000_000), format(720, 2_500_000))
        assertEquals(1, pickVideoIndex(formats, maxHeightPx = Int.MAX_VALUE))
    }

    @Test
    fun `breaks a height tie by bitrate`() {
        val formats = listOf(format(720, 2_000_000), format(720, 3_000_000))
        assertEquals(1, pickVideoIndex(formats, maxHeightPx = 720))
    }

    @Test
    fun `falls back to the smallest rendition when none fits the cap`() {
        val formats = listOf(format(1080, 5_000_000), format(720, 2_500_000))
        assertEquals(1, pickVideoIndex(formats, maxHeightPx = 480))
    }

    @Test
    fun `picks the highest bitrate when no height is known`() {
        val formats = listOf(format(bitrate = 800_000), format(bitrate = 2_000_000), format(bitrate = 1_200_000))
        assertEquals(1, pickVideoIndex(formats, maxHeightPx = 720))
    }
}
