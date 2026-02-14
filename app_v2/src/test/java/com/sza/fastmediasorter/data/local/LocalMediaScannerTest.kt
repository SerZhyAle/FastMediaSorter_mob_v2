package com.sza.fastmediasorter.data.local

import android.content.Context
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.MediaStoreRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMediaScannerTest {

    private val context: Context = mockk(relaxed = true)
    private val mediaStoreRepository: MediaStoreRepository = mockk()

    private val scanner = LocalMediaScanner(
        context = context,
        mediaStoreRepository = mediaStoreRepository
    )

    @Test
    fun `scanFolder should use recent branch for virtual path and filter hidden files`() = runTest {
        val supportedTypes = setOf(MediaType.IMAGE)
        val recentVisible = MediaFile(
            name = "photo.jpg",
            path = "/storage/emulated/0/DCIM/photo.jpg",
            type = MediaType.IMAGE,
            size = 1024L,
            createdDate = 1700000000000L
        )
        val recentHidden = MediaFile(
            name = ".hidden.jpg",
            path = "/storage/emulated/0/DCIM/.hidden.jpg",
            type = MediaType.IMAGE,
            size = 2048L,
            createdDate = 1700000001000L
        )

        coEvery {
            mediaStoreRepository.getRecentFiles(limit = 1000, allowedTypes = supportedTypes)
        } returns listOf(recentVisible, recentHidden)

        val result = scanner.scanFolder(
            path = LocalMediaScanner.VIRTUAL_PATH_RECENT,
            supportedTypes = supportedTypes,
            sizeFilter = null,
            credentialsId = null,
            scanSubdirectories = false,
            showHiddenFiles = false,
            onProgress = null
        )

        assertEquals(1, result.size)
        assertEquals(recentVisible.path, result.first().path)

        coVerify(exactly = 1) {
            mediaStoreRepository.getRecentFiles(limit = 1000, allowedTypes = supportedTypes)
        }
        coVerify(exactly = 0) {
            mediaStoreRepository.getFilesInFolder(any(), any(), any(), any())
        }
    }
}
