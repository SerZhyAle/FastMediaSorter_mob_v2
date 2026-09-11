package com.sza.fastmediasorter.ui.wear.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCompanionScreenVersionTest {

    @Test
    fun `extractVersionGeneration keys a stamped version name by its build date`() {
        assertEquals("260911", extractVersionGeneration("2.60.9110.137-NoLegal-DEBUG"))
        assertEquals("260911", extractVersionGeneration("2.60.9110.140-DEBUG"))
        assertEquals("260910", extractVersionGeneration("2.60.9101.431-NoLegal-DEBUG"))
        assertEquals("260205", extractVersionGeneration("2.60.2050.151"))
    }

    @Test
    fun `extractVersionGeneration falls back to major minor when the name carries no stamp`() {
        assertEquals("2.6", extractVersionGeneration("2.6.0"))
        assertEquals("1.0", extractVersionGeneration("1.0.3-beta"))
        assertEquals("3.0", extractVersionGeneration("3.0"))
        // Four groups, but not the stamp's digit widths - read as major.minor, not as a date.
        assertEquals("2.60", extractVersionGeneration("2.60.911.137"))
    }

    @Test
    fun `isWatchVersionMismatched accepts two builds of one revision`() {
        // The S2861 run 4 finding: the halves are packaged minutes apart and their stamps differ.
        assertFalse(
            isWatchVersionMismatched(
                watch = "2.60.9110.140-DEBUG",
                phone = "2.60.9110.137-NoLegal-DEBUG"
            )
        )
        // Same day, hours apart, one half built as a different flavor.
        assertFalse(
            isWatchVersionMismatched(
                watch = "2.60.9112.301-DEBUG",
                phone = "2.60.9110.137-NoLegal-DEBUG"
            )
        )
    }

    @Test
    fun `isWatchVersionMismatched stays silent when the watch reported no version`() {
        // Both are drawn by the "version unknown" branch, which is not a mismatch (strategic 2.5).
        assertFalse(isWatchVersionMismatched(watch = null, phone = "2.60.9110.137"))
        assertFalse(isWatchVersionMismatched(watch = "", phone = "2.60.9110.137"))
    }

    @Test
    fun `isWatchVersionMismatched flags a watch left on an older build`() {
        // S2861 run 3's real pair, one day apart - the case the readout exists to catch.
        assertTrue(
            isWatchVersionMismatched(
                watch = "2.60.9101.431-NoLegal-DEBUG",
                phone = "2.60.9110.036-NoLegal-DEBUG"
            )
        )
        // Months apart inside one calendar year: keying by major.minor would read as the year and
        // green this pair, which is the regression the date key exists to prevent.
        assertTrue(
            isWatchVersionMismatched(
                watch = "2.60.5120.301-NoLegal-DEBUG",
                phone = "2.60.9110.137-NoLegal-DEBUG"
            )
        )
    }

    @Test
    fun `isWatchVersionMismatched flags a watch carrying an unstamped version name`() {
        assertTrue(isWatchVersionMismatched(watch = "1.0.0", phone = "2.60.9110.137-NoLegal-DEBUG"))
        assertTrue(isWatchVersionMismatched(watch = "3.0.0-DEBUG", phone = "2.60.9110.137-NoLegal-DEBUG"))
    }
}
