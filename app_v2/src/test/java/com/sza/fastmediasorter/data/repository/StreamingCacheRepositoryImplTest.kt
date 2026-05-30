package com.sza.fastmediasorter.data.repository

import android.content.Context
import com.sza.fastmediasorter.data.local.db.StreamingCacheDao
import com.sza.fastmediasorter.data.local.db.StreamingCacheEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
 * Covers [StreamingCacheRepositoryImpl] logic that does not require the `content://`
 * ContentResolver path: the deterministic [StreamingCacheRepositoryImpl.resolveHash],
 * the find/delete/prune/clear orchestration, and TTL threshold maths. Local filesystem
 * paths are backed by a [TemporaryFolder]. The DAO is faked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamingCacheRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private lateinit var dao: StreamingCacheDao
    private lateinit var repo: StreamingCacheRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repo = StreamingCacheRepositoryImpl(context, dao)
    }

    @Test
    fun `resolveHash is deterministic and 16 hex chars`() {
        val a = repo.resolveHash("smb://host/share/file.mp4")
        val b = repo.resolveHash("smb://host/share/file.mp4")
        assertEquals(a, b)
        assertEquals(16, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
    }

    @Test
    fun `resolveHash differs for different uris`() {
        assertFalse(repo.resolveHash("a") == repo.resolveHash("b"))
    }

    @Test
    fun `findByOriginalUri looks up by resolved hash`() = runTest {
        val uri = "smb://host/x.mp4"
        val expected = entry(repo.resolveHash(uri), "/tmp/x")
        coEvery { dao.findByHash(repo.resolveHash(uri)) } returns expected

        assertEquals(expected, repo.findByOriginalUri(uri))
    }

    @Test
    fun `delete returns false when entry is unknown`() = runTest {
        coEvery { dao.findByHash("h") } returns null
        assertFalse(repo.delete("h"))
        coVerify(exactly = 0) { dao.deleteByHash(any()) }
    }

    @Test
    fun `delete removes local file and row when entry exists`() = runTest {
        val local = tempFolder.newFile("offload.bin")
        coEvery { dao.findByHash("h") } returns entry("h", local.absolutePath)

        assertTrue(repo.delete("h"))
        assertFalse(local.exists())
        coVerify { dao.deleteByHash("h") }
    }

    @Test
    fun `verifyAndPrune drops entries whose local file is missing`() = runTest {
        val present = tempFolder.newFile("present.bin")
        val missingPath = File(tempFolder.root, "gone.bin").absolutePath
        coEvery { dao.getAll() } returns listOf(
            entry("h1", present.absolutePath),
            entry("h2", missingPath),
        )

        val pruned = repo.verifyAndPrune()

        assertEquals(1, pruned)
        coVerify { dao.deleteByHash("h2") }
        coVerify(exactly = 0) { dao.deleteByHash("h1") }
    }

    @Test
    fun `findStaleByTtl queries dao with now minus ttl threshold`() = runTest {
        coEvery { dao.findOlderThan(1_000L - 200L) } returns listOf(entry("h", "/tmp/x"))
        val result = repo.findStaleByTtl(now = 1_000L, ttlMs = 200L)
        assertEquals(1, result.size)
        coVerify { dao.findOlderThan(800L) }
    }

    @Test
    fun `clearAll deletes every local file and returns count`() = runTest {
        val f1 = tempFolder.newFile("a.bin")
        val f2 = tempFolder.newFile("b.bin")
        coEvery { dao.getAll() } returns listOf(entry("h1", f1.absolutePath), entry("h2", f2.absolutePath))

        val cleared = repo.clearAll()

        assertEquals(2, cleared)
        assertFalse(f1.exists())
        assertFalse(f2.exists())
        coVerify { dao.deleteAll() }
    }

    @Test
    fun `record touchPlayed and getAll delegate to dao`() = runTest {
        val e = entry("h", "/tmp/x")
        coEvery { dao.getAll() } returns listOf(e)

        repo.record(e)
        repo.touchPlayed("h", 42L)
        assertEquals(listOf(e), repo.getAll())

        coVerify { dao.upsert(e) }
        coVerify { dao.touchLastPlayedAt("h", 42L) }
    }

    @Test
    fun `findByHash returns null when dao has no row`() = runTest {
        coEvery { dao.findByHash("h") } returns null
        assertNull(repo.findByHash("h"))
    }

    private fun entry(hash: String, localPath: String) = StreamingCacheEntry(
        resourceHash = hash,
        localPath = localPath,
        originalUri = "smb://host/orig",
        resourceKey = "smb://host/share/",
        sizeBytes = 100L,
        downloadedAt = 0L,
        lastPlayedAt = 0L,
        sourceProtocol = "SMB",
    )
}
