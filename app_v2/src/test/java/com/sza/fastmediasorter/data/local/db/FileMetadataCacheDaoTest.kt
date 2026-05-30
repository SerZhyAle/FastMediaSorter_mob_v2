package com.sza.fastmediasorter.data.local.db

import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * In-memory Room coverage for [FileMetadataCacheDao]: the (resourceId, filePath) lookup,
 * the [FileChecksumRow] projection, the IN-list and credentials deletes, and TTL expiry.
 * A parent [ResourceEntity] is inserted first to satisfy the CASCADE foreign key.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileMetadataCacheDaoTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val dao get() = dbRule.db.fileMetadataCacheDao()

    private var resourceId = 0L

    @Before
    fun seedResource() = runTest {
        resourceId = dbRule.db.resourceDao().insert(
            ResourceEntity(name = "R", path = "/root", type = ResourceType.LOCAL)
        )
    }

    private fun entity(
        path: String,
        lastModified: Long = 1L,
        fileSize: Long = 10L,
        cachedAt: Long = 100L,
        credentialsId: String? = null,
    ) = FileMetadataCacheEntity(
        resourceId = resourceId,
        filePath = path,
        provider = "LOCAL",
        credentialsId = credentialsId,
        lastModified = lastModified,
        fileSize = fileSize,
        cachedAt = cachedAt,
        thumbnailPath = null,
        durationMs = null,
        width = null,
        height = null,
        videoRotation = null,
        exifDateTime = null,
        exifJson = null,
    )

    @Test
    fun `getEntry returns the matching row or null`() = runTest {
        dao.upsert(entity("/a"))
        assertEquals("/a", dao.getEntry(resourceId, "/a")?.filePath)
        assertNull(dao.getEntry(resourceId, "/missing"))
    }

    @Test
    fun `upsertAll inserts distinct rows and countForResource reflects them`() = runTest {
        dao.upsertAll(listOf(entity("/a"), entity("/b"), entity("/c")))
        assertEquals(3, dao.countForResource(resourceId))
        assertEquals(
            listOf("/a", "/b", "/c"),
            dao.getAllForResource(resourceId).map { it.filePath }.sorted(),
        )
    }

    @Test
    fun `getChecksumSnapshot projects mtime and size`() = runTest {
        dao.upsert(entity("/a", lastModified = 5L, fileSize = 50L))
        dao.upsert(entity("/b", lastModified = 6L, fileSize = 60L))

        val snapshot = dao.getChecksumSnapshot(resourceId).sortedBy { it.filePath }
        assertEquals(2, snapshot.size)
        assertEquals(FileChecksumRow(resourceId, "/a", 5L, 50L), snapshot[0])
        assertEquals(FileChecksumRow(resourceId, "/b", 6L, 60L), snapshot[1])
    }

    @Test
    fun `deleteEntry removes a single file`() = runTest {
        dao.upsertAll(listOf(entity("/a"), entity("/b")))
        dao.deleteEntry(resourceId, "/a")
        assertNull(dao.getEntry(resourceId, "/a"))
        assertEquals(1, dao.countForResource(resourceId))
    }

    @Test
    fun `deleteEntries removes the listed paths`() = runTest {
        dao.upsertAll(listOf(entity("/a"), entity("/b"), entity("/c")))
        dao.deleteEntries(resourceId, listOf("/a", "/c"))
        assertEquals(listOf("/b"), dao.getAllForResource(resourceId).map { it.filePath })
    }

    @Test
    fun `deleteAllForResource returns the deleted count`() = runTest {
        dao.upsertAll(listOf(entity("/a"), entity("/b")))
        assertEquals(2, dao.deleteAllForResource(resourceId))
        assertEquals(0, dao.countForResource(resourceId))
    }

    @Test
    fun `deleteExpired removes only rows older than the cutoff`() = runTest {
        dao.upsert(entity("/old", cachedAt = 100L))
        dao.upsert(entity("/new", cachedAt = 500L))

        val removed = dao.deleteExpired(olderThanMs = 300L)

        assertEquals(1, removed)
        assertNull(dao.getEntry(resourceId, "/old"))
        assertEquals("/new", dao.getEntry(resourceId, "/new")?.filePath)
    }

    @Test
    fun `deleteByCredentials targets matching credential rows`() = runTest {
        dao.upsert(entity("/a", credentialsId = "cred-1"))
        dao.upsert(entity("/b", credentialsId = "cred-2"))

        val removed = dao.deleteByCredentials("cred-1")

        assertEquals(1, removed)
        assertNull(dao.getEntry(resourceId, "/a"))
        assertEquals("/b", dao.getEntry(resourceId, "/b")?.filePath)
    }
}
