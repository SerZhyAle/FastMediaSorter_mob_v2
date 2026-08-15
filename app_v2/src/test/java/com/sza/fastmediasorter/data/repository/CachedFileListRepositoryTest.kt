package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.CachedFileListDao
import com.sza.fastmediasorter.data.local.db.CachedFileListEntity
import com.sza.fastmediasorter.testing.createMediaFile
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
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Exercises the GZIP+Gson round-trip and patch logic of [CachedFileListRepository].
 * The DAO is faked; the compression/serialisation under test is pure JVM (java.util.zip + Gson).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CachedFileListRepositoryTest {

    private lateinit var dao: CachedFileListDao
    private lateinit var repo: CachedFileListRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repo = CachedFileListRepository(dao)
    }

    @Test
    fun `save then load round-trips the file list`() = runTest {
        val files = listOf(
            createMediaFile(name = "a.jpg", path = "/a.jpg"),
            createMediaFile(name = "b.mp4", path = "/b.mp4"),
        )
        val saved = slot<CachedFileListEntity>()
        coEvery { dao.insertOrReplace(capture(saved)) } returns Unit

        repo.saveCachedFiles(resourceId = 1L, files = files, lastScanTimestamp = 50L, lastModifiedFolder = 60L)

        assertEquals(2, saved.captured.fileCount)
        assertEquals(50L, saved.captured.lastScanTimestamp)
        assertEquals(60L, saved.captured.lastModifiedFolder)

        coEvery { dao.getByResourceId(1L) } returns saved.captured
        val loaded = repo.getCachedFiles(1L)
        assertEquals(listOf("/a.jpg", "/b.mp4"), loaded?.map { it.path })
    }

    @Test
    fun `saveCachedFiles skips when count exceeds one million`() = runTest {
        repo.saveCachedFiles(resourceId = 1L, files = oversizedList())
        coVerify(exactly = 0) { dao.insertOrReplace(any()) }
    }

    @Test
    fun `getCachedFiles returns null when nothing cached`() = runTest {
        coEvery { dao.getByResourceId(1L) } returns null
        assertNull(repo.getCachedFiles(1L))
    }

    @Test
    fun `getCachedFiles returns null on corrupt blob`() = runTest {
        coEvery { dao.getByResourceId(1L) } returns entity(byteArrayOf(0, 1, 2, 3), 1)
        assertNull(repo.getCachedFiles(1L))
    }

    @Test
    fun `getCachedFiles discards a snapshot whose entry misses a required field`() = runTest {
        coEvery { dao.getByResourceId(1L) } returns entity(gzip(SNAPSHOT_WITHOUT_PATH), 1)

        assertNull(repo.getCachedFiles(1L))

        coVerify { dao.deleteByResourceId(1L) }
    }

    @Test
    fun `patching a snapshot with a missing required field never re-saves it`() = runTest {
        coEvery { dao.getByResourceId(1L) } returns entity(gzip(SNAPSHOT_WITHOUT_PATH), 1)

        repo.deleteFile(1L, "/a.jpg")

        coVerify(exactly = 0) { dao.insertOrReplace(any()) }
        coVerify { dao.deleteByResourceId(1L) }
    }

    @Test
    fun `hasCachedFiles reflects dao presence`() = runTest {
        coEvery { dao.getByResourceId(1L) } returns entity(compressOf(emptyList()), 0)
        coEvery { dao.getByResourceId(2L) } returns null
        assertTrue(repo.hasCachedFiles(1L))
        assertFalse(repo.hasCachedFiles(2L))
    }

    @Test
    fun `updateFile replaces matching entry and recompresses`() = runTest {
        val original = listOf(createMediaFile(name = "old.jpg", path = "/old.jpg"))
        coEvery { dao.getByResourceId(1L) } returns entity(compressOf(original), 1)
        val saved = slot<CachedFileListEntity>()
        coEvery { dao.insertOrReplace(capture(saved)) } returns Unit

        val replacement = createMediaFile(name = "new.jpg", path = "/new.jpg")
        repo.updateFile(1L, "/old.jpg", replacement)

        coEvery { dao.getByResourceId(1L) } returns saved.captured
        assertEquals(listOf("/new.jpg"), repo.getCachedFiles(1L)?.map { it.path })
        assertEquals(1, saved.captured.fileCount)
    }

    @Test
    fun `updateFile is a no-op when path not present`() = runTest {
        coEvery { dao.getByResourceId(1L) } returns entity(compressOf(listOf(createMediaFile(path = "/x"))), 1)
        repo.updateFile(1L, "/missing", createMediaFile(path = "/y"))
        coVerify(exactly = 0) { dao.insertOrReplace(any()) }
    }

    @Test
    fun `updateFile is a no-op when no snapshot cached`() = runTest {
        coEvery { dao.getByResourceId(1L) } returns null
        repo.updateFile(1L, "/x", createMediaFile())
        coVerify(exactly = 0) { dao.insertOrReplace(any()) }
    }

    @Test
    fun `deleteFile removes matching entry`() = runTest {
        val original = listOf(
            createMediaFile(path = "/keep"),
            createMediaFile(path = "/drop"),
        )
        coEvery { dao.getByResourceId(1L) } returns entity(compressOf(original), 2)
        val saved = slot<CachedFileListEntity>()
        coEvery { dao.insertOrReplace(capture(saved)) } returns Unit

        repo.deleteFile(1L, "/drop")

        coEvery { dao.getByResourceId(1L) } returns saved.captured
        assertEquals(listOf("/keep"), repo.getCachedFiles(1L)?.map { it.path })
        assertEquals(1, saved.captured.fileCount)
    }

    @Test
    fun `deleteFile is a no-op when path not present`() = runTest {
        coEvery { dao.getByResourceId(1L) } returns entity(compressOf(listOf(createMediaFile(path = "/x"))), 1)
        repo.deleteFile(1L, "/missing")
        coVerify(exactly = 0) { dao.insertOrReplace(any()) }
    }

    @Test
    fun `deleteCachedFiles and deleteAllCachedFiles delegate to dao`() = runTest {
        repo.deleteCachedFiles(3L)
        repo.deleteAllCachedFiles()
        coVerify { dao.deleteByResourceId(3L) }
        coVerify { dao.deleteAll() }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Reproduces an R8 mapping mismatch: Gson parses the blob fine but leaves a required field unset. */
    private fun gzip(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return baos.toByteArray()
    }

    private fun entity(data: ByteArray, count: Int) = CachedFileListEntity(
        id = 1L,
        resourceId = 1L,
        compressedData = data,
        fileCount = count,
    )

    /** Reuse the repository's own save path to produce a valid compressed blob for a list. */
    private suspend fun compressOf(files: List<com.sza.fastmediasorter.domain.model.MediaFile>): ByteArray {
        val saved = slot<CachedFileListEntity>()
        val localDao: CachedFileListDao = mockk(relaxed = true)
        coEvery { localDao.insertOrReplace(capture(saved)) } returns Unit
        CachedFileListRepository(localDao).saveCachedFiles(1L, files)
        return saved.captured.compressedData
    }

    private fun oversizedList(): List<com.sza.fastmediasorter.domain.model.MediaFile> =
        object : AbstractList<com.sza.fastmediasorter.domain.model.MediaFile>() {
            override val size: Int = 1_000_001
            override fun get(index: Int) = createMediaFile()
        }

    private companion object {
        const val SNAPSHOT_WITHOUT_PATH =
            """[{"name":"a.jpg","type":"IMAGE","size":1024,"createdDate":0}]"""
    }
}
