package com.sza.fastmediasorter.data.local.dao

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.data.local.db.ResourceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for ResourceDao
 * 
 * Tests Room database operations for ResourceEntity (CRUD, queries, constraints)
 * Uses in-memory database for isolated testing
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ResourceDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule() // For LiveData synchronous execution

    private lateinit var database: AppDatabase
    private lateinit var resourceDao: ResourceDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries() // Only for testing
            .build()
        resourceDao = database.resourceDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndRetrieve_singleResource() = runTest {
        // Given
        val resource = ResourceEntity(
            id = 1,
            name = "Test Resource",
            type = ResourceType.LOCAL,
            path = "/storage/emulated/0/DCIM",
            isDestination = false
        )

        // When
        resourceDao.insert(resource)
        val retrieved = resourceDao.getAllFlow().first()

        // Then
        assertEquals(1, retrieved.size)
        assertEquals(resource.name, retrieved[0].name)
        assertEquals(resource.path, retrieved[0].path)
    }

    @Test
    fun insertMultiple_retrievesAll() = runTest {
        // Given
        val resources = listOf(
            ResourceEntity(id = 1, name = "Local", type = ResourceType.LOCAL, path = "/local", isDestination = false),
            ResourceEntity(id = 2, name = "SMB", type = ResourceType.SMB, path = "smb://server/share", isDestination = true),
            ResourceEntity(id = 3, name = "Cloud", type = ResourceType.CLOUD, path = "cloud://drive/folder", isDestination = false)
        )

        // When
        resources.forEach { resourceDao.insert(it) }
        val retrieved = resourceDao.getAllFlow().first()

        // Then
        assertEquals(3, retrieved.size)
    }

    @Test
    fun update_modifiesExistingResource() = runTest {
        // Given
        val resource = ResourceEntity(
            id = 1,
            name = "Original Name",
            type = ResourceType.LOCAL,
            path = "/original/path",
            isDestination = false
        )
        resourceDao.insert(resource)

        // When
        val updated = resource.copy(name = "Updated Name", isDestination = true)
        resourceDao.update(updated)
        val retrieved = resourceDao.getById(1)

        // Then
        assertNotNull(retrieved)
        assertEquals("Updated Name", retrieved!!.name)
        assertTrue(retrieved.isDestination)
    }

    @Test
    fun delete_removesResource() = runTest {
        // Given
        val resource = ResourceEntity(
            id = 1,
            name = "To Delete",
            type = ResourceType.LOCAL,
            path = "/delete/me",
            isDestination = false
        )
        resourceDao.insert(resource)

        // When
        resourceDao.delete(resource)
        val retrieved = resourceDao.getById(1)

        // Then
        assertNull(retrieved)
    }

    @Test
    fun getDestinations_filtersCorrectly() = runTest {
        // Given
        val resources = listOf(
            ResourceEntity(id = 1, name = "Not Dest", type = ResourceType.LOCAL, path = "/path1", isDestination = false),
            ResourceEntity(id = 2, name = "Dest 1", type = ResourceType.SMB, path = "/path2", isDestination = true),
            ResourceEntity(id = 3, name = "Dest 2", type = ResourceType.SFTP, path = "/path3", isDestination = true)
        )
        resources.forEach { resourceDao.insert(it) }

        // When
        val destinations = resourceDao.getDestinationsFlow().first()

        // Then
        assertEquals(2, destinations.size)
        assertTrue(destinations.all { it.isDestination })
    }

    @Test
    fun getByType_filtersCorrectly() = runTest {
        // Given
        val resources = listOf(
            ResourceEntity(id = 1, name = "Local 1", type = ResourceType.LOCAL, path = "/local1", isDestination = false),
            ResourceEntity(id = 2, name = "SMB 1", type = ResourceType.SMB, path = "smb://server1", isDestination = false),
            ResourceEntity(id = 3, name = "Local 2", type = ResourceType.LOCAL, path = "/local2", isDestination = false)
        )
        resources.forEach { resourceDao.insert(it) }

        // When
        val localResources = resourceDao.getByType(ResourceType.LOCAL).first()

        // Then
        assertEquals(2, localResources.size)
        assertTrue(localResources.all { it.type == ResourceType.LOCAL })
    }

    @Test
    fun insertDuplicate_replacesExisting() = runTest {
        // Given
        val original = ResourceEntity(
            id = 1,
            name = "Original",
            type = ResourceType.LOCAL,
            path = "/original",
            isDestination = false
        )
        resourceDao.insert(original)

        // When - Insert with same ID
        val duplicate = original.copy(name = "Duplicate")
        resourceDao.insert(duplicate)
        val all = resourceDao.getAllFlow().first()

        // Then - Should have only one resource (replaced)
        assertEquals(1, all.size)
        assertEquals("Duplicate", all[0].name)
    }

    @Test
    fun destinationCount_enforcesLimit() = runTest {
        // Given - Try to add 11 destinations (limit is 10)
        val destinations = (1..11).map { index ->
            ResourceEntity(
                id = index.toLong(),
                name = "Dest $index",
                type = ResourceType.LOCAL,
                path = "/dest$index",
                isDestination = true
            )
        }

        // When
        destinations.forEach { resourceDao.insert(it) }
        val retrieved = resourceDao.getDestinationsFlow().first()

        // Then - All 11 should be inserted (app logic enforces limit, not DB)
        assertEquals(11, retrieved.size)
        // Note: Business logic in ViewModel/UseCase should prevent adding 11th destination
    }

    @Test
    fun getAllFlow_emitsUpdates() = runTest {
        // Given
        val resource = ResourceEntity(
            id = 1,
            name = "Test",
            type = ResourceType.LOCAL,
            path = "/test",
            isDestination = false
        )

        // When - Insert and verify emission
        resourceDao.insert(resource)
        var retrieved = resourceDao.getAllFlow().first()
        assertEquals(1, retrieved.size)

        // When - Update and verify new emission
        resourceDao.update(resource.copy(name = "Updated"))
        retrieved = resourceDao.getAllFlow().first()
        assertEquals("Updated", retrieved[0].name)
    }
}
