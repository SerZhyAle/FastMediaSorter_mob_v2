package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.MediaStoreRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanLocalFoldersUseCaseTest {

    private val context: Context = mockk(relaxed = true)
    private val resourceRepository: ResourceRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val mediaStoreRepository: MediaStoreRepository = mockk()

    private val useCase = ScanLocalFoldersUseCase(
        context = context,
        repository = resourceRepository,
        settingsRepository = settingsRepository,
        mediaStoreRepository = mediaStoreRepository
    )

    @Test
    fun `invoke includes EPUB in globally enabled types and documents resource`() = runTest {
        every { context.getString(any()) } returns "Stub"

        coEvery { resourceRepository.getAllResources() } returns flowOf(emptyList())
        coEvery { settingsRepository.getSettings() } returns flowOf(
            AppSettings(
                supportAudio = false,
                supportVideos = false,
                supportImages = false,
                supportGifs = false,
                supportText = false,
                supportPdf = false,
                supportEpub = true,
                supportOfficeDocuments = false
            )
        )
        coEvery { mediaStoreRepository.getStandardFolders() } returns emptyList()
        coEvery { mediaStoreRepository.getFoldersWithMedia(any()) } returns emptyList()

        val result = useCase().getOrThrow()

        val docsResource = result.firstOrNull { it.path == "virtual://all_docs" }
        assertTrue(docsResource != null)
        assertEquals(setOf(MediaType.EPUB), docsResource?.supportedMediaTypes)

        coVerify(exactly = 1) {
            mediaStoreRepository.getFoldersWithMedia(setOf(MediaType.EPUB))
        }
    }
}
