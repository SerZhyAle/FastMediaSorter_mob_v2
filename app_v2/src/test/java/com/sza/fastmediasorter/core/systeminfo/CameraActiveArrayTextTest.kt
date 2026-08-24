package com.sza.fastmediasorter.core.systeminfo

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraActiveArrayTextTest {

    @Test
    fun `origin at zero prints size only`() {
        assertEquals("4032x3024", CameraActiveArrayText.format(0, 0, 4032, 3024))
    }

    @Test
    fun `non-zero origin is printed because a sub-lens can report one`() {
        assertEquals("4000x3000 at 16,12", CameraActiveArrayText.format(16, 12, 4016, 3012))
    }

    @Test
    fun `degenerate rectangle reports zero rather than throwing`() {
        assertEquals("0x0", CameraActiveArrayText.format(0, 0, 0, 0))
    }
}
