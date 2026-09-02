package com.sza.fastmediasorter.wear.util

import com.sza.fastmediasorter.wear.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * The boundaries below are the ones the browse chip printed before the label was localised. A failure
 * here means the numbers moved, and S2353 changed only what names them.
 */
class FileSizePartsTest {

    @Test
    fun `below a kilobyte the exact byte count is kept`() {
        val parts = fileSizeParts(1023, Locale.US)
        assertEquals("1023", parts.value)
        assertEquals(R.string.wear_unit_size_bytes, parts.unitRes)
    }

    @Test
    fun `a full kilobyte crosses into the kilobyte unit`() {
        val parts = fileSizeParts(1024, Locale.US)
        assertEquals("1.0", parts.value)
        assertEquals(R.string.wear_unit_size_kb, parts.unitRes)
    }

    @Test
    fun `the megabyte step is taken at a full megabyte and not before`() {
        assertEquals(R.string.wear_unit_size_kb, fileSizeParts(1024L * 1024 - 1, Locale.US).unitRes)

        val parts = fileSizeParts(1024L * 1024, Locale.US)
        assertEquals("1.0", parts.value)
        assertEquals(R.string.wear_unit_size_mb, parts.unitRes)
    }

    @Test
    fun `the decimal separator follows the locale`() {
        assertEquals("44.6", fileSizeParts(45_700, Locale.US).value)
        assertEquals("44,6", fileSizeParts(45_700, Locale.forLanguageTag("ru")).value)
    }
}
