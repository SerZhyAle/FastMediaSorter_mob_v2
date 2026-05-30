package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import com.sza.fastmediasorter.testing.fakes.FakeSettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncNetworkResourcesUseCaseTest {

    private val resourceRepository = FakeResourceRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val scannerFactory = mockk<MediaScannerFactory>()
    private val scanner = mockk<MediaScanner>()

    private lateinit var useCase: SyncNetworkResourcesUseCase

    @Before
    fun setup() {
        every { scannerFactory.getScanner(any()) } returns scanner
        useCase = SyncNetworkResourcesUseCase(resourceRepository, settingsRepository, scannerFactory)
    }

    @Test
    fun `syncAll returns zero when no network resources exist`() = runTest {
        resourceRepository.setResources(listOf(createMediaResource(id = 1L, type = ResourceType.LOCAL)))

        val result = useCase.syncAll()

        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `syncAll updates file count and reports success count`() = runTest {
        resourceRepository.setResources(
            listOf(
                createMediaResource(id = 1L, type = ResourceType.SMB, path = "smb://h/a"),
                createMediaResource(id = 2L, type = ResourceType.SFTP, path = "sftp://h/b"),
                createMediaResource(id = 3L, type = ResourceType.LOCAL), // excluded
            )
        )
        coEvery { scanner.getFileCount(any(), any(), any(), any(), any(), any()) } returns 42

        val result = useCase.syncAll()

        assertEquals(2, result.getOrThrow())
        assertEquals(2, resourceRepository.updatedResources.size)
        assertTrue(resourceRepository.updatedResources.all { it.fileCount == 42 })
    }

    @Test
    fun `syncAll swallows per-resource failure and continues`() = runTest {
        resourceRepository.setResources(
            listOf(
                createMediaResource(id = 1L, type = ResourceType.SMB, path = "smb://h/a"),
                createMediaResource(id = 2L, type = ResourceType.FTP, path = "ftp://h/b"),
            )
        )
        coEvery { scanner.getFileCount("smb://h/a", any(), any(), any(), any(), any()) } throws RuntimeException("io")
        coEvery { scanner.getFileCount("ftp://h/b", any(), any(), any(), any(), any()) } returns 5

        val result = useCase.syncAll()

        assertEquals(1, result.getOrThrow())
    }

    @Test
    fun `syncAll reports progress for each resource`() = runTest {
        resourceRepository.setResources(
            listOf(createMediaResource(id = 1L, type = ResourceType.SMB, path = "smb://h/a"))
        )
        coEvery { scanner.getFileCount(any(), any(), any(), any(), any(), any()) } returns 1
        val progress = mutableListOf<Triple<Int, Int, Int>>()

        useCase.syncAll { processed, total, success -> progress.add(Triple(processed, total, success)) }

        // Initial (0,1,0) plus post-processing (1,1,1).
        assertEquals(Triple(0, 1, 0), progress.first())
        assertEquals(Triple(1, 1, 1), progress.last())
    }

    @Test
    fun `syncSingle fails when resource not found`() = runTest {
        resourceRepository.setResources(emptyList())

        val result = useCase.syncSingle(99L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `syncSingle fails for non-network resource`() = runTest {
        resourceRepository.setResources(listOf(createMediaResource(id = 1L, type = ResourceType.LOCAL)))

        val result = useCase.syncSingle(1L)

        assertTrue(result.isFailure)
    }

    @Test
    fun `syncSingle updates network resource and succeeds`() = runTest {
        resourceRepository.setResources(
            listOf(createMediaResource(id = 1L, type = ResourceType.SMB, path = "smb://h/a"))
        )
        coEvery { scanner.getFileCount(any(), any(), any(), any(), any(), any()) } returns 99

        val result = useCase.syncSingle(1L)

        assertTrue(result.isSuccess)
        assertEquals(99, resourceRepository.updatedResources.single().fileCount)
    }
}
