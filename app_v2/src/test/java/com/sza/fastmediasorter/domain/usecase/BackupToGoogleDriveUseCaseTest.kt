package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.cloud.CloudFile
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.testing.createAppSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM coverage for [BackupToGoogleDriveUseCase]: auth gate, folder find-vs-create, README upload on
 * create, and upload success/error mapping. The Google Drive client and DAO are mocked.
 */
class BackupToGoogleDriveUseCaseTest {

    private val context = mockk<Context>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>()
    private val resourceRepository = mockk<ResourceRepository>()
    private val driveClient = mockk<GoogleDriveRestClient>()
    private val favoritesDao = mockk<FavoritesDao>()
    private val scheduledRepo = mockk<ScheduledOperationRepository>()
    private lateinit var useCase: BackupToGoogleDriveUseCase

    @Before
    fun setup() {
        useCase = BackupToGoogleDriveUseCase(
            context, settingsRepository, resourceRepository, driveClient, favoritesDao, scheduledRepo,
        )
        every { settingsRepository.getSettings() } returns flowOf(createAppSettings())
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()
        coEvery { favoritesDao.getAllFavoritesSync() } returns emptyList()
        every { scheduledRepo.getAll() } returns flowOf(emptyList())
    }

    private fun cloudFile(id: String) = CloudFile(id = id, name = "x", path = "/x", isFolder = true)

    @Test
    fun `fails when not authenticated`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns false

        val result = useCase()

        assertTrue(result.isFailure)
    }

    @Test
    fun `uses existing folder and reports success on upload`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns true
        coEvery { driveClient.getAccountEmail() } returns "user@example.com"
        coEvery { driveClient.findFolderByName(any(), any()) } returns CloudResult.Success(cloudFile("folder1"))
        coEvery { driveClient.uploadFile(any(), any(), any(), any(), any()) } returns
            CloudResult.Success(CloudFile(id = "f", name = "backup.json", path = "/b", isFolder = false))

        val result = useCase()

        assertTrue(result.isSuccess)
        val backup = result.getOrThrow()
        assertTrue(backup.accountEmail == "user@example.com")
        // No folder creation when one already exists.
        coVerify(exactly = 0) { driveClient.createFolder(any(), any()) }
    }

    @Test
    fun `creates folder and uploads README when none exists`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns true
        coEvery { driveClient.getAccountEmail() } returns "user@example.com"
        coEvery { driveClient.findFolderByName(any(), any()) } returns CloudResult.Success(null)
        coEvery { driveClient.createFolder(any(), any()) } returns CloudResult.Success(cloudFile("newFolder"))
        coEvery { driveClient.uploadFile(any(), any(), any(), any(), any()) } returns
            CloudResult.Success(CloudFile(id = "f", name = "backup.json", path = "/b", isFolder = false))

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify { driveClient.createFolder(any(), any()) }
        // README upload + backup upload = at least 2 uploads.
        coVerify(atLeast = 2) { driveClient.uploadFile(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fails when folder cannot be created`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns true
        coEvery { driveClient.getAccountEmail() } returns "user@example.com"
        coEvery { driveClient.findFolderByName(any(), any()) } returns CloudResult.Success(null)
        coEvery { driveClient.createFolder(any(), any()) } returns CloudResult.Error("quota")

        val result = useCase()

        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when upload returns error`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns true
        coEvery { driveClient.getAccountEmail() } returns "user@example.com"
        coEvery { driveClient.findFolderByName(any(), any()) } returns CloudResult.Success(cloudFile("folder1"))
        coEvery { driveClient.uploadFile(any(), any(), any(), any(), any()) } returns CloudResult.Error("offline")

        val result = useCase()

        assertTrue(result.isFailure)
    }

    @Test
    fun `generateFileName produces timestamped backup name`() {
        val name = BackupToGoogleDriveUseCase.generateFileName()
        assertTrue(name.startsWith("backup_"))
        assertTrue(name.endsWith(".json"))
        assertFalse(name.contains(" "))
    }
}
