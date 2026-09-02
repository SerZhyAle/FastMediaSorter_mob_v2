package com.sza.fastmediasorter.wear.ui.common

import com.sza.fastmediasorter.wear.domain.model.WearContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2129: strategic criterion 4 is observable on a device only as "the name is wider", which cannot
 * tell a correct predicate from one that compacts every list. The branches are settled here so the
 * device pass is left judging legibility rather than logic.
 */
class WearRowDensityTest {

    @Test
    fun `a list of mixed types stays normal`() {
        assertEquals(
            WearRowDensity.NORMAL,
            rowDensityFor(
                types = listOf(WearContentType.MUSIC, WearContentType.IMAGE),
                canProduceThumbnails = false
            )
        )
    }

    @Test
    fun `one repeated type stays normal while thumbnails are possible`() {
        assertEquals(
            WearRowDensity.NORMAL,
            rowDensityFor(
                types = listOf(WearContentType.IMAGE, WearContentType.IMAGE),
                canProduceThumbnails = true
            )
        )
    }

    @Test
    fun `one repeated type with no thumbnail possible compacts`() {
        assertEquals(
            WearRowDensity.COMPACT,
            rowDensityFor(
                types = listOf(WearContentType.MUSIC, WearContentType.MUSIC, WearContentType.MUSIC),
                canProduceThumbnails = false
            )
        )
    }

    @Test
    fun `an empty list stays normal`() {
        assertEquals(
            WearRowDensity.NORMAL,
            rowDensityFor(types = emptyList(), canProduceThumbnails = false)
        )
    }

    @Test
    fun `a single entry with no thumbnail possible compacts`() {
        assertEquals(
            WearRowDensity.COMPACT,
            rowDensityFor(
                types = listOf(WearContentType.DOCUMENT),
                canProduceThumbnails = false
            )
        )
    }

    @Test
    fun `compact draws the smaller of the two icon sizes and normal the larger`() {
        assertEquals(WearListMetrics.LeadingIconNormal, WearRowDensity.NORMAL.leadingIconSize)
        assertEquals(WearListMetrics.LeadingIconCompact, WearRowDensity.COMPACT.leadingIconSize)
        assertTrue(
            WearRowDensity.COMPACT.leadingIconSize < WearRowDensity.NORMAL.leadingIconSize
        )
    }
}
