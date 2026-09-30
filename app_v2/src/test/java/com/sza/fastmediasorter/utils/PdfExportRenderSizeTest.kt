package com.sza.fastmediasorter.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfExportRenderSizeTest {

    @Test
    fun `a4 page renders at twice its point size`() {
        assertEquals(1190 to 1684, PdfExportHelper.renderSize(595, 842))
    }

    @Test
    fun `a0 page is capped by the pixel budget`() {
        val (w, h) = PdfExportHelper.renderSize(2384, 3370)
        assertTrue(w.toLong() * h <= 8_388_608L)
        assertTrue(maxOf(w, h) <= 4096)
        assertTrue(w > 2000)
    }

    @Test
    fun `very long page is capped by the side limit`() {
        val (w, h) = PdfExportHelper.renderSize(100, 20_000)
        assertTrue(h in 4095..4096)
        assertEquals(20, w)
    }

    @Test
    fun `degenerate page size still yields a drawable bitmap`() {
        val (w, h) = PdfExportHelper.renderSize(0, 0)
        assertTrue(w >= 1 && h >= 1)
    }
}
