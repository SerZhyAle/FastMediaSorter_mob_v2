package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrInputBitmapLoaderTest {

    @Test
    fun `keeps a bitmap at the OCR limit unchanged`() {
        assertNull(OcrInputBitmapLoader.boundedSize(2048, 2048))
    }

    @Test
    fun `limits a larger bitmap proportionally`() {
        assertEquals(2048 to 1024, OcrInputBitmapLoader.boundedSize(4096, 2048))
    }
}
