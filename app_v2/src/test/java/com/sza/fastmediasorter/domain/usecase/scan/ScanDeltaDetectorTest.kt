package com.sza.fastmediasorter.domain.usecase.scan

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ScanDeltaDetector] delta computation logic (A5-T12).
 *
 * Covers:
 * - Empty previous snapshot (first scan)
 * - No changes between scans (all unchanged)
 * - Added files
 * - Modified files (size change)
 * - Modified files (mtime / createdDate change)
 * - Deleted files
 * - Mixed: add + modify + delete + unchanged in one scan
 * - Directories are treated the same as files
 */
class ScanDeltaDetectorTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun file(
        path: String,
        size: Long = 1024L,
        createdDate: Long = 1_000_000L,
        type: MediaType = MediaType.IMAGE
    ) = MediaFile(
        name        = path.substringAfterLast('/'),
        path        = path,
        type        = type,
        size        = size,
        createdDate = createdDate
    )

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `empty previous returns everything as added`() {
        val current = listOf(file("/a.jpg"), file("/b.jpg"))
        val delta = ScanDeltaDetector.compute(current, previous = emptyList())

        assertEquals(2, delta.added.size)
        assertTrue(delta.modified.isEmpty())
        assertTrue(delta.deleted.isEmpty())
        assertTrue(delta.unchanged.isEmpty())
        assertEquals(2, delta.currentTotal)
        assertEquals(2, delta.changedTotal) // added only
    }

    @Test
    fun `identical snapshots produce no changes`() {
        val files = listOf(file("/a.jpg"), file("/b.jpg"))
        val delta = ScanDeltaDetector.compute(files, files)

        assertTrue(delta.added.isEmpty())
        assertTrue(delta.modified.isEmpty())
        assertTrue(delta.deleted.isEmpty())
        assertEquals(2, delta.unchanged.size)
        assertTrue(delta.isEmpty)
    }

    @Test
    fun `new file in current is added`() {
        val previous = listOf(file("/a.jpg"))
        val current  = listOf(file("/a.jpg"), file("/b.jpg"))
        val delta = ScanDeltaDetector.compute(current, previous)

        assertEquals(1, delta.added.size)
        assertEquals("/b.jpg", delta.added.first().path)
        assertTrue(delta.modified.isEmpty())
        assertTrue(delta.deleted.isEmpty())
        assertEquals(1, delta.unchanged.size)
    }

    @Test
    fun `file absent in current is deleted`() {
        val previous = listOf(file("/a.jpg"), file("/b.jpg"))
        val current  = listOf(file("/a.jpg"))
        val delta = ScanDeltaDetector.compute(current, previous)

        assertTrue(delta.added.isEmpty())
        assertTrue(delta.modified.isEmpty())
        assertEquals(1, delta.deleted.size)
        assertEquals("/b.jpg", delta.deleted.first().path)
        assertEquals(1, delta.unchanged.size)
    }

    @Test
    fun `file with changed size is modified`() {
        val previous = listOf(file("/a.jpg", size = 1024L))
        val current  = listOf(file("/a.jpg", size = 2048L))
        val delta = ScanDeltaDetector.compute(current, previous)

        assertTrue(delta.added.isEmpty())
        assertEquals(1, delta.modified.size)
        assertEquals("/a.jpg", delta.modified.first().path)
        assertTrue(delta.deleted.isEmpty())
        assertTrue(delta.unchanged.isEmpty())
    }

    @Test
    fun `file with changed createdDate is modified`() {
        val previous = listOf(file("/a.jpg", createdDate = 1_000L))
        val current  = listOf(file("/a.jpg", createdDate = 2_000L))
        val delta = ScanDeltaDetector.compute(current, previous)

        assertEquals(1, delta.modified.size)
        assertEquals("/a.jpg", delta.modified.first().path)
    }

    @Test
    fun `file with same size and date is unchanged`() {
        val f = file("/a.jpg", size = 100L, createdDate = 999L)
        val delta = ScanDeltaDetector.compute(listOf(f), listOf(f.copy()))

        assertTrue(delta.unchanged.isNotEmpty())
        assertTrue(delta.modified.isEmpty())
    }

    @Test
    fun `mixed scenario add modify delete unchanged`() {
        val prevA = file("/a.jpg", size = 100L)
        val prevB = file("/b.jpg", size = 200L)
        val prevC = file("/c.jpg")

        val currA = file("/a.jpg", size = 999L) // modified
        val currB = file("/b.jpg", size = 200L) // unchanged
        // prevC is absent → deleted
        val currD = file("/d.jpg")               // added

        val delta = ScanDeltaDetector.compute(
            current  = listOf(currA, currB, currD),
            previous = listOf(prevA, prevB, prevC)
        )

        assertEquals(listOf(currD), delta.added)
        assertEquals(listOf(currA), delta.modified)
        assertEquals(listOf(prevC), delta.deleted)
        assertEquals(listOf(currB), delta.unchanged)

        assertEquals(listOf(currD, currA), delta.changedFiles)
        assertEquals(3, delta.currentTotal)  // added + modified + unchanged
        assertEquals(3, delta.changedTotal)  // added + modified + deleted
    }

    @Test
    fun `isEmpty returns true when no changes`() {
        val f = file("/x.jpg")
        val delta = ScanDeltaDetector.compute(listOf(f), listOf(f))
        assertTrue(delta.isEmpty)
    }

    @Test
    fun `isEmpty returns false when there are changes`() {
        val f = file("/x.jpg")
        val g = file("/y.jpg")
        val delta = ScanDeltaDetector.compute(listOf(g), listOf(f))
        assertTrue(!delta.isEmpty)
    }

    @Test
    fun `toString contains all partition sizes`() {
        val delta = ScanDeltaDetector.compute(
            current  = listOf(file("/a.jpg")),
            previous = listOf(file("/b.jpg"))
        )
        val str = delta.toString()
        assertTrue(str.contains("added=1"))
        assertTrue(str.contains("deleted=1"))
    }
}
