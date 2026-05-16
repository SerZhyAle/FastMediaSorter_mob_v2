package com.sza.fastmediasorter.core.memory

import android.graphics.Bitmap
import com.bumptech.glide.load.DecodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPressureDecodeFormatResolverTest {

    @Test
    fun `profile RGB565 keeps RGB565 even without pressure`() {
        assertTrue(
            MemoryPressureDecodeFormatResolver.shouldUseRgb565(
                profileUseRgb565 = true,
                underPressure = false,
            ),
        )
    }

    @Test
    fun `pressure forces RGB565 even when profile prefers ARGB8888`() {
        assertEquals(
            DecodeFormat.PREFER_RGB_565,
            MemoryPressureDecodeFormatResolver.decodeFormatFor(
                profileUseRgb565 = false,
                underPressure = true,
            ),
        )
        assertEquals(
            Bitmap.Config.RGB_565,
            MemoryPressureDecodeFormatResolver.bitmapConfigFor(
                profileUseRgb565 = false,
                underPressure = true,
            ),
        )
    }

    @Test
    fun `ARGB8888 remains when profile and pressure both say no`() {
        assertFalse(
            MemoryPressureDecodeFormatResolver.shouldUseRgb565(
                profileUseRgb565 = false,
                underPressure = false,
            ),
        )
        assertEquals(
            DecodeFormat.PREFER_ARGB_8888,
            MemoryPressureDecodeFormatResolver.decodeFormatFor(
                profileUseRgb565 = false,
                underPressure = false,
            ),
        )
        assertEquals(
            Bitmap.Config.ARGB_8888,
            MemoryPressureDecodeFormatResolver.bitmapConfigFor(
                profileUseRgb565 = false,
                underPressure = false,
            ),
        )
    }
}