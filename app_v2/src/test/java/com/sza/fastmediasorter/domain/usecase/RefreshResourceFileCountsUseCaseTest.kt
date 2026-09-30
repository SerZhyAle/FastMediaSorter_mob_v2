package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshResourceFileCountsUseCaseTest {

    private val repository = FakeResourceRepository()
    private val settingsRepository = mockk<SettingsRepository> {
        every { getSettings() } returns flowOf(mockk<AppSettings>(relaxed = true))
    }
    private val scanner = mockk<MediaScanner>()
    private val scannerFactory = mockk<MediaScannerFactory> {
        every { getScanner(any()) } returns scanner
    }
    private val resolveScanFilter = mockk<ResolveScanFilterUseCase> {
        every { this@mockk.invoke(any(), any()) } returns ScanFilter(emptySet(), mockk(relaxed = true))
    }
    private val useCase = RefreshResourceFileCountsUseCase(
        repository,
        settingsRepository,
        scannerFactory,
        resolveScanFilter,
    )

    @Test
    fun `an edit made during the scan survives the count write`() = runTest {
        repository.setResources(listOf(createMediaResource(id = 7L, name = "Before", fileCount = 1)))
        coEvery { scanner.getFileCount(any(), any(), any(), any(), any(), any()) } coAnswers {
            repository.setResources(listOf(repository.resources.single().copy(name = "Renamed")))
            42
        }

        useCase(listOf(7L))

        val stored = repository.resources.single()
        assertEquals(42, stored.fileCount)
        assertEquals("Renamed", stored.name)
        assertTrue(repository.updatedResources.isEmpty())
    }

    @Test
    fun `an unchanged count writes nothing`() = runTest {
        repository.setResources(listOf(createMediaResource(id = 7L, fileCount = 5)))
        coEvery { scanner.getFileCount(any(), any(), any(), any(), any(), any()) } returns 5

        useCase(listOf(7L))

        assertEquals(5, repository.resources.single().fileCount)
        assertTrue(repository.updatedResources.isEmpty())
    }
}
