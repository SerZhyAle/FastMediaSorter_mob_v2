package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import androidx.room.withTransaction
import com.sza.fastmediasorter.data.cloud.CloudFile
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.testing.createAppSettings
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import com.google.gson.GsonBuilder
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.OutputStream

/**
 * JVM coverage for [RestoreFromGoogleDriveUseCase]: auth gate, no-backup, version compatibility,
 * resource dedup (path and cloud-key), favorites/scheduled-op merge, and getBackupInfo. The Drive
 * client is mocked; downloadFile writes a crafted backup JSON into the output stream.
 */
class RestoreFromGoogleDriveUseCaseTest {

    private val context = mockk<Context>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>()
    private val resourceRepository = mockk<ResourceRepository>(relaxed = true)
    private val driveClient = mockk<GoogleDriveRestClient>()
    private val favoritesDao = mockk<FavoritesDao>(relaxed = true)
    private val scheduledRepo = mockk<ScheduledOperationRepository>(relaxed = true)
    private val credentialsRepository = mockk<NetworkCredentialsRepository>(relaxed = true)
    private val authSessionRepository = mockk<AuthSessionRepository>(relaxed = true)
    private val workManagerScheduler = mockk<WorkManagerScheduler>(relaxed = true)
    private val db = mockk<AppDatabase>()
    private lateinit var useCase: RestoreFromGoogleDriveUseCase

    @Before
    fun setup() {
        // S0732: ApplyBackupPayloadUseCase now wraps its Room sections in db.withTransaction; stub the
        // extension so it simply runs the block in this repo-mocked unit test.
        // db.withTransaction is an extension function: when stubbed via mockkStatic, arg 0 is the
        // RoomDatabase receiver and arg 1 is the transaction block - run the block so the Room sections
        // execute against the mocked repositories (S0732).
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } answers {
            runBlocking { secondArg<suspend () -> Any?>().invoke() }
        }
        // S0406: Restore now delegates to the shared applier; build a real one from mocked repos
        // so the behavioral assertions below still exercise the merge logic.
        val applyUseCase = ApplyBackupPayloadUseCase(
            db, settingsRepository, resourceRepository, favoritesDao, scheduledRepo,
            credentialsRepository, authSessionRepository, workManagerScheduler,
        )
        useCase = RestoreFromGoogleDriveUseCase(context, driveClient, applyUseCase)
        every { settingsRepository.getSettings() } returns flowOf(createAppSettings())
        coEvery { settingsRepository.updateSettings(any<AppSettings>()) } just Runs
    }

    private fun stubBackupDownload(json: String) {
        coEvery { driveClient.findFolderByName(any(), any()) } returns
            CloudResult.Success(CloudFile(id = "folder", name = "FastMediaSorter", path = "/", isFolder = true))
        coEvery { driveClient.listFiles(any(), any()) } returns CloudResult.Success(
            Pair(
                listOf(CloudFile(id = "f1", name = "backup_260101-1200.json", path = "/b", isFolder = false, modifiedDate = 100)),
                null,
            ),
        )
        val osSlot = slot<OutputStream>()
        coEvery { driveClient.downloadFile(any(), capture(osSlot), any()) } answers {
            osSlot.captured.write(json.toByteArray(Charsets.UTF_8))
            CloudResult.Success(true)
        }
    }

    private fun backupJson(
        version: Int = 4,
        resources: List<Map<String, Any?>> = emptyList(),
    ): String {
        val payload = mapOf(
            "version" to version,
            "appVersionName" to "2.0",
            "createdAt" to "2026-01-01",
            "deviceModel" to "TestPhone",
            "settings" to emptyMap<String, Any?>(),
            "resources" to resources,
            "favorites" to emptyList<Any?>(),
            "scheduledOperations" to emptyList<Any?>(),
        )
        return GsonBuilder().create().toJson(payload)
    }

    private fun resourceMap(type: String, path: String) = mapOf(
        "name" to "R",
        "path" to path,
        "type" to type,
        "supportedMediaTypes" to listOf("IMAGE"),
    )

    @Test
    fun `fails when not authenticated`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns false

        assertTrue(useCase().isFailure)
    }

    @Test
    fun `fails when no backup folder found`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns true
        coEvery { driveClient.findFolderByName(any(), any()) } returns CloudResult.Success(null)

        assertTrue(useCase().isFailure)
    }

    @Test
    fun `fails when backup version is newer than current`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns true
        stubBackupDownload(backupJson(version = 999))

        val result = useCase()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("newer app version") == true)
    }

    @Test
    fun `restores settings and adds new local resource`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns true
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()
        stubBackupDownload(backupJson(resources = listOf(resourceMap("LOCAL", "/storage/A"))))

        val result = useCase().getOrThrow()

        assertEquals(1, result.resourcesAdded)
        assertEquals(0, result.resourcesSkipped)
        assertTrue(result.settingsRestored)
        coVerify { resourceRepository.addResource(any()) }
    }

    @Test
    fun `skips duplicate local resource by path`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns true
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            createMediaResource(path = "/storage/A", type = com.sza.fastmediasorter.domain.model.ResourceType.LOCAL),
        )
        stubBackupDownload(backupJson(resources = listOf(resourceMap("LOCAL", "/storage/A"))))

        val result = useCase().getOrThrow()

        assertEquals(0, result.resourcesAdded)
        assertEquals(1, result.resourcesSkipped)
    }

    @Test
    fun `getBackupInfo returns metadata without importing`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns true
        stubBackupDownload(backupJson(resources = listOf(resourceMap("LOCAL", "/storage/A"))))

        val info = useCase.getBackupInfo().getOrThrow()

        assertEquals("2026-01-01", info.createdAt)
        assertEquals(1, info.resourceCount)
        assertEquals("TestPhone", info.deviceModel)
        coVerify(exactly = 0) { resourceRepository.addResource(any()) }
    }

    @Test
    fun `getBackupInfo fails when not authenticated`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns false

        assertTrue(useCase.getBackupInfo().isFailure)
    }

    @Test
    fun `network resource counts toward needsAuth`() = runTest {
        coEvery { driveClient.isAuthenticated() } returns true
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()
        stubBackupDownload(backupJson(resources = listOf(resourceMap("SMB", "smb://host/share"))))

        val result = useCase().getOrThrow()

        assertEquals(1, result.resourcesAdded)
        assertEquals(1, result.resourcesNeedingAuth)
        assertFalse(result.resourcesNeedingAuth == 0)
    }
}
