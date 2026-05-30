package com.sza.fastmediasorter.data.repository

import android.content.Context
import com.sza.fastmediasorter.data.local.db.ThumbnailCacheDao
import com.sza.fastmediasorter.data.local.db.ThumbnailCacheEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Covers the file-existence branches, DAO orchestration, and LRU eviction maths of
 * [ThumbnailCacheRepositoryImpl]. Local thumbnail files live in a [TemporaryFolder];
 * the DAO is faked and the Context is a relaxed mock (only the unused lazy cacheDir
 * touches it, and these methods do not).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThumbnailCacheRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private lateinit var dao: ThumbnailCacheDao
    private lateinit var repo: ThumbnailCacheRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repo = ThumbnailCacheRepositoryImpl(context, dao)
    }

    private fun entity(filePath: String, thumbPath: String, size: Long = 100L, accessed: Long = 0L) =
        ThumbnailCacheEntity(
            filePath = filePath,
            thumbnailPath = thumbPath,
            fileSize = size,
            createdAt = 0L,
            lastAccessedAt = accessed,
        )

    @Test
    fun `getCachedThumbnail returns null when nothing cached`() = runTest {
        coEvery { dao.getThumbnail("/v") } returns null
        assertNull(repo.getCachedThumbnail("/v"))
    }

    @Test
    fun `getCachedThumbnail returns file and touches access time on hit`() = runTest {
        val thumb = tempFolder.newFile("t.jpg")
        coEvery { dao.getThumbnail("/v") } returns entity("/v", thumb.absolutePath)

        val result = repo.getCachedThumbnail("/v")

        assertEquals(thumb.absolutePath, result?.absolutePath)
        coVerify { dao.updateAccessTime("/v", any()) }
    }

    @Test
    fun `getCachedThumbnail purges stale entry when physical file is missing`() = runTest {
        val missing = File(tempFolder.root, "gone.jpg").absolutePath
        coEvery { dao.getThumbnail("/v") } returns entity("/v", missing)

        assertNull(repo.getCachedThumbnail("/v"))
        coVerify { dao.deleteThumbnail("/v") }
        coVerify(exactly = 0) { dao.updateAccessTime(any(), any()) }
    }

    @Test
    fun `saveThumbnail skips a non-existent source file`() = runTest {
        val missing = File(tempFolder.root, "nope.jpg")
        repo.saveThumbnail("/v", missing)
        coVerify(exactly = 0) { dao.insertThumbnail(any()) }
    }

    @Test
    fun `saveThumbnail inserts an entry with the file length`() = runTest {
        val thumb = tempFolder.newFile("t.jpg").apply { writeBytes(ByteArray(7)) }
        val captured = slot<ThumbnailCacheEntity>()
        coEvery { dao.insertThumbnail(capture(captured)) } returns Unit

        repo.saveThumbnail("/v", thumb)

        assertEquals("/v", captured.captured.filePath)
        assertEquals(thumb.absolutePath, captured.captured.thumbnailPath)
        assertEquals(7L, captured.captured.fileSize)
    }

    @Test
    fun `deleteThumbnail removes physical file and db entry`() = runTest {
        val thumb = tempFolder.newFile("t.jpg")
        coEvery { dao.getThumbnail("/v") } returns entity("/v", thumb.absolutePath)

        repo.deleteThumbnail("/v")

        assertFalse(thumb.exists())
        coVerify { dao.deleteThumbnail("/v") }
    }

    @Test
    fun `deleteThumbnail is a no-op when entry absent`() = runTest {
        coEvery { dao.getThumbnail("/v") } returns null
        repo.deleteThumbnail("/v")
        coVerify(exactly = 0) { dao.deleteThumbnail(any()) }
    }

    @Test
    fun `cleanupOldThumbnails deletes files and returns db deleted count`() = runTest {
        val old = tempFolder.newFile("old.jpg")
        coEvery { dao.getThumbnailsOlderThan(any()) } returns listOf(entity("/v", old.absolutePath))
        coEvery { dao.deleteOldThumbnails(any()) } returns 1

        val deleted = repo.cleanupOldThumbnails(days = 30)

        assertEquals(1, deleted)
        assertFalse(old.exists())
    }

    @Test
    fun `getCacheStats maps dao counters`() = runTest {
        coEvery { dao.getCacheCount() } returns 4
        coEvery { dao.getTotalCacheSize() } returns 4096L

        val stats = repo.getCacheStats()

        assertEquals(4, stats.entryCount)
        assertEquals(4096L, stats.totalSizeBytes)
    }

    @Test
    fun `getCacheStats returns zeros on dao failure`() = runTest {
        coEvery { dao.getCacheCount() } throws RuntimeException("db down")

        val stats = repo.getCacheStats()

        assertEquals(0, stats.entryCount)
        assertEquals(0L, stats.totalSizeBytes)
    }

    @Test
    fun `enforceSizeLimit evicts nothing when already under limit`() = runTest {
        coEvery { dao.getTotalCacheSize() } returns 500L
        assertEquals(0, repo.enforceSizeLimit(maxBytes = 1_000L))
        coVerify(exactly = 0) { dao.deleteByPaths(any()) }
    }

    @Test
    fun `enforceSizeLimit evicts LRU entries until under the limit`() = runTest {
        // Total 300; limit 150 -> must evict from the oldest until <= 150.
        val f1 = tempFolder.newFile("a.jpg").apply { writeBytes(ByteArray(100)) }
        val f2 = tempFolder.newFile("b.jpg").apply { writeBytes(ByteArray(100)) }
        val f3 = tempFolder.newFile("c.jpg").apply { writeBytes(ByteArray(100)) }
        coEvery { dao.getTotalCacheSize() } returns 300L
        coEvery { dao.getAllByLruOrder() } returns listOf(
            entity("/a", f1.absolutePath, size = 100L, accessed = 1L),
            entity("/b", f2.absolutePath, size = 100L, accessed = 2L),
            entity("/c", f3.absolutePath, size = 100L, accessed = 3L),
        )
        val deletedPaths = slot<List<String>>()
        coEvery { dao.deleteByPaths(capture(deletedPaths)) } answers { firstArg<List<String>>().size }

        val deleted = repo.enforceSizeLimit(maxBytes = 150L)

        // After freeing 100 (->200) still > 150, free another 100 (->100) <= 150; two evicted.
        assertEquals(2, deleted)
        assertEquals(listOf("/a", "/b"), deletedPaths.captured)
        assertFalse(f1.exists())
        assertFalse(f2.exists())
        assertTrue(f3.exists())
    }
}
