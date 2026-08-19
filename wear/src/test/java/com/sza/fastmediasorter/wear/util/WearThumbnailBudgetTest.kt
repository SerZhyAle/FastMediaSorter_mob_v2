package com.sza.fastmediasorter.wear.util

import org.junit.Assert.assertTrue
import org.junit.Test

private const val ONE_MEGABYTE = 1024 * 1024
private const val TYPICAL_FOLDER_FILES = 20
private const val MIN_USABLE_EDGE_PX = 1
private const val MAX_SENSIBLE_EDGE_PX = 256

/**
 * The budget is the ticket's cost promise, so its numbers are asserted rather than left to review:
 * a later edit that raises the head cap into megabytes would silently restore the traffic the
 * ticket exists to avoid.
 */
class WearThumbnailBudgetTest {

    @Test
    fun `a folder of photos stays within a few megabytes of head reads`() {
        val worstCase = WearThumbnailBudget.MAX_HEAD_READ_BYTES.toLong() * TYPICAL_FOLDER_FILES

        assertTrue(
            "worst case $worstCase bytes for $TYPICAL_FOLDER_FILES files",
            worstCase < ONE_MEGABYTE.toLong() * TYPICAL_FOLDER_FILES / 2
        )
    }

    @Test
    fun `the thumbnail edge stays small enough for a watch cell`() {
        assertTrue(
            WearThumbnailBudget.MAX_THUMBNAIL_EDGE_PX in MIN_USABLE_EDGE_PX..MAX_SENSIBLE_EDGE_PX
        )
    }

    @Test
    fun `the cache holds more than one screen of cells`() {
        assertTrue(WearThumbnailBudget.MAX_CACHED_THUMBNAILS >= TYPICAL_FOLDER_FILES)
    }
}
