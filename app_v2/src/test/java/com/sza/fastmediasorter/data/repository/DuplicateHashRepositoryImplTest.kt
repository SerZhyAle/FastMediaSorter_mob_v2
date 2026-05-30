package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.DuplicateHashCacheDao
import com.sza.fastmediasorter.data.local.db.DuplicateHashCacheEntity
import com.sza.fastmediasorter.testing.createMediaFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DuplicateHashRepositoryImplTest {

    private lateinit var dao: DuplicateHashCacheDao
    private lateinit var repo: DuplicateHashRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repo = DuplicateHashRepositoryImpl(dao)
    }

    @Test
    fun `getCachedQuickHash returns stored quick hash`() = runTest {
        val file = createMediaFile(path = "/a", size = 10, lastModified = 5, resourceId = 3L)
        coEvery { dao.getByKey(3L, "/a", 5, 10) } returns entity(quickHash = "qh", fullHash = null)

        assertEquals("qh", repo.getCachedQuickHash(file))
    }

    @Test
    fun `null resourceId is coalesced to zero in the lookup key`() = runTest {
        val file = createMediaFile(path = "/a", size = 10, lastModified = 5, resourceId = null)
        coEvery { dao.getByKey(0L, "/a", 5, 10) } returns null

        assertNull(repo.getCachedQuickHash(file))
        coVerify { dao.getByKey(0L, "/a", 5, 10) }
    }

    @Test
    fun `getCachedFullHash returns stored full hash`() = runTest {
        val file = createMediaFile(path = "/b", size = 20, lastModified = 6, resourceId = 4L)
        coEvery { dao.getByKey(4L, "/b", 6, 20) } returns entity(quickHash = null, fullHash = "fh")

        assertEquals("fh", repo.getCachedFullHash(file))
    }

    @Test
    fun `saveQuickHash inserts a fresh entity when none exists`() = runTest {
        val file = createMediaFile(path = "/c", size = 30, lastModified = 7, resourceId = 5L)
        coEvery { dao.getByKey(5L, "/c", 7, 30) } returns null
        val captured = slot<DuplicateHashCacheEntity>()
        coEvery { dao.upsert(capture(captured)) } returns Unit

        repo.saveQuickHash(file, "newQuick")

        assertEquals(5L, captured.captured.resourceId)
        assertEquals("/c", captured.captured.filePath)
        assertEquals(7L, captured.captured.lastModified)
        assertEquals(30L, captured.captured.fileSize)
        assertEquals("newQuick", captured.captured.quickHash)
        assertNull(captured.captured.fullHash)
    }

    @Test
    fun `saveQuickHash preserves existing full hash when updating`() = runTest {
        val file = createMediaFile(path = "/d", size = 40, lastModified = 8, resourceId = 6L)
        coEvery { dao.getByKey(6L, "/d", 8, 40) } returns entity(quickHash = "old", fullHash = "keepFull")
        val captured = slot<DuplicateHashCacheEntity>()
        coEvery { dao.upsert(capture(captured)) } returns Unit

        repo.saveQuickHash(file, "updatedQuick")

        assertEquals("updatedQuick", captured.captured.quickHash)
        assertEquals("keepFull", captured.captured.fullHash)
    }

    @Test
    fun `saveFullHash inserts a fresh entity when none exists`() = runTest {
        val file = createMediaFile(path = "/e", size = 50, lastModified = 9, resourceId = 7L)
        coEvery { dao.getByKey(7L, "/e", 9, 50) } returns null
        val captured = slot<DuplicateHashCacheEntity>()
        coEvery { dao.upsert(capture(captured)) } returns Unit

        repo.saveFullHash(file, "newFull")

        assertEquals("newFull", captured.captured.fullHash)
        assertNull(captured.captured.quickHash)
    }

    @Test
    fun `deleteByResourceId delegates to dao`() = runTest {
        repo.deleteByResourceId(99L)
        coVerify { dao.deleteByResourceId(99L) }
    }

    @Test
    fun `deleteHashEntry uses coalesced resource id`() = runTest {
        val file = createMediaFile(path = "/f", resourceId = null)
        repo.deleteHashEntry(file)
        coVerify { dao.deleteByResourceAndPath(0L, "/f") }
    }

    private fun entity(quickHash: String?, fullHash: String?) = DuplicateHashCacheEntity(
        id = 1L,
        resourceId = 1L,
        filePath = "/x",
        lastModified = 0L,
        fileSize = 0L,
        quickHash = quickHash,
        fullHash = fullHash,
        cachedAt = 0L,
    )
}
