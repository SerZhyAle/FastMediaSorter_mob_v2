package com.sza.fastmediasorter.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hand-written [CachedFileListEntity.equals]/[CachedFileListEntity.hashCode] intentionally
 * ignore the [CachedFileListEntity.compressedData] BLOB and the scan-state timestamps, keying
 * identity on (id, resourceId, fileCount) only. These are the branches under test.
 */
class CachedFileListEntityTest {

    private fun entity(
        id: Long = 1L,
        resourceId: Long = 10L,
        data: ByteArray = byteArrayOf(1, 2, 3),
        fileCount: Int = 5,
        createdAt: Long = 100L,
        lastScan: Long? = 200L,
        lastModifiedFolder: Long? = 300L,
    ) = CachedFileListEntity(
        id = id,
        resourceId = resourceId,
        compressedData = data,
        fileCount = fileCount,
        createdAt = createdAt,
        lastScanTimestamp = lastScan,
        lastModifiedFolder = lastModifiedFolder,
    )

    @Test
    fun `equals is reflexive`() {
        val e = entity()
        assertTrue(e == e)
    }

    @Test
    fun `equals ignores compressedData createdAt and scan-state fields`() {
        val a = entity(data = byteArrayOf(1), createdAt = 1L, lastScan = 1L, lastModifiedFolder = 1L)
        val b = entity(data = byteArrayOf(9, 9, 9), createdAt = 999L, lastScan = 999L, lastModifiedFolder = 999L)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `equals distinguishes by id resourceId and fileCount`() {
        val base = entity()
        assertNotEquals(base, entity(id = 2L))
        assertNotEquals(base, entity(resourceId = 99L))
        assertNotEquals(base, entity(fileCount = 6))
    }

    @Test
    fun `equals rejects different type`() {
        assertFalse(entity().equals("not an entity"))
    }

    @Test
    fun `hashCode is stable across calls`() {
        val e = entity()
        assertEquals(e.hashCode(), e.hashCode())
    }
}
