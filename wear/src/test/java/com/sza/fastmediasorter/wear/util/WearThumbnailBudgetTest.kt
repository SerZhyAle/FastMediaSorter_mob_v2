package com.sza.fastmediasorter.wear.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ONE_MEGABYTE = 1024 * 1024
private const val TYPICAL_FOLDER_FILES = 20
private const val MIN_USABLE_EDGE_PX = 1
private const val MAX_SENSIBLE_EDGE_PX = 256
private const val ARGB_BYTES = 4

/** Two cell edges squared, in ARGB: the most a sampled decode may hold before its final downscale. */
private const val MAX_SAMPLED_BYTES = 256L * 256L * ARGB_BYTES

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

    @Test
    fun `S3797 an oversized picture is sampled to near the cell edge before it is held`() {
        val edge = WearThumbnailBudget.MAX_THUMBNAIL_EDGE_PX
        listOf(12_000 to 9_000, 4_032 to 3_024, 300 to 20_000, 129 to 129).forEach { (width, height) ->
            val sample = WearThumbnailBudget.sampleSizeFor(width, height)
            val sampledLongest = maxOf(width, height) / sample

            assertTrue("$width x $height sampled to $sampledLongest", sampledLongest >= edge)
            assertTrue("$width x $height sampled to $sampledLongest", sampledLongest < edge * 2)
            val retainedBytes = (width / sample).toLong() * (height / sample) * ARGB_BYTES
            assertTrue("$width x $height retains $retainedBytes bytes", retainedBytes <= MAX_SAMPLED_BYTES)
        }
    }

    @Test
    fun `S3797 a picture already inside the cell is not sampled`() {
        assertEquals(1, WearThumbnailBudget.sampleSizeFor(100, 80))
        assertEquals(1, WearThumbnailBudget.sampleSizeFor(0, 0))
    }

    @Test
    fun `S3797 the phone thumbnail ceiling stays within the head-read budget`() {
        assertTrue(
            WearThumbnailBudget.MAX_PHONE_THUMBNAIL_BASE64_CHARS <= WearThumbnailBudget.MAX_HEAD_READ_BYTES
        )
    }
}
