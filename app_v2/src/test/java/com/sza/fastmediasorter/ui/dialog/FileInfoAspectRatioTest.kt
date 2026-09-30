package com.sza.fastmediasorter.ui.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S3902: a stream the retriever sizes as 0x0 must not divide by a zero GCD. */
class FileInfoAspectRatioTest {

    @Test
    fun `common sizes simplify to their ratio`() {
        assertEquals("16:9", simplifiedAspectRatio(1920, 1080))
        assertEquals("4:3", simplifiedAspectRatio(640, 480))
        assertEquals("9:16", simplifiedAspectRatio(1080, 1920))
    }

    @Test
    fun `zero or negative sides give no ratio instead of throwing`() {
        assertNull(simplifiedAspectRatio(0, 0))
        assertNull(simplifiedAspectRatio(1920, 0))
        assertNull(simplifiedAspectRatio(0, 1080))
        assertNull(simplifiedAspectRatio(-1, 1080))
    }

    @Test
    fun `missing sides give no ratio`() {
        assertNull(simplifiedAspectRatio(null, 1080))
        assertNull(simplifiedAspectRatio(1920, null))
    }
}
