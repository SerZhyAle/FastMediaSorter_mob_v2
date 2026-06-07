package com.sza.fastmediasorter.data.local

import android.content.Context
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.MediaStoreRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `scanFolder recent rethrows CancellationException instead of swallowing into empty list`() = runTest {
        // S0373: a coroutine cancellation while scanning the recent virtual resource must propagate,
        // not be caught by the generic catch and reported as a hard scan failure (empty list).
        val supportedTypes = setOf(MediaType.IMAGE)

        coEvery {
            mediaStoreRepository.getRecentFiles(limit = 1000, allowedTypes = supportedTypes)
        } throws CancellationException("scan cancelled")

        var propagated = false
        try {
            scanner.scanFolder(
                path = LocalMediaScanner.VIRTUAL_PATH_RECENT,
                supportedTypes = supportedTypes,
                sizeFilter = null,
                credentialsId = null,
                scanSubdirectories = false,
                showHiddenFiles = false,
                onProgress = null
            )
        } catch (e: CancellationException) {
            propagated = true
        }

        assertTrue(
            "CancellationException must be rethrown, not swallowed into an empty result list",
            propagated
        )
    }

    // ==================== Virtual Aggregate Paths ====================

    private fun makeAudioFile(name: String) = MediaFile(
        name = name,
        path = "/storage/emulated/0/Music/$name",
        type = MediaType.AUDIO,
        size = 5_000_000L,
        createdDate = 1700000000000L
    )

    private fun makeVideoFile(name: String) = MediaFile(
        name = name,
        path = "/storage/emulated/0/Video/$name",
        type = MediaType.VIDEO,
        size = 50_000_000L,
        createdDate = 1700000000000L
    )

    private fun makeDocFile(name: String, type: MediaType = MediaType.PDF) = MediaFile(
        name = name,
        path = "/storage/emulated/0/Documents/$name",
        type = type,
        size = 100_000L,
        createdDate = 1700000000000L
    )

    private fun makeMediaFile(name: String, type: MediaType) = MediaFile(
        name = name,
        path = "/storage/emulated/0/Media/$name",
        type = type,
        size = 1_000_000L,
        createdDate = 1700000000000L
    )

    @Test
    fun `scanFolder virtual all_audio calls getAllFilesByTypes with AUDIO`() = runTest {
        val audioFiles = listOf(makeAudioFile("song1.mp3"), makeAudioFile("song2.flac"))

        coEvery {
            mediaStoreRepository.getAllFilesByTypes(
                allowedTypes = setOf(MediaType.AUDIO),
                limit = 10_000,
                showHiddenFiles = false
            )
        } returns audioFiles

        val result = scanner.scanFolder(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
            supportedTypes = setOf(MediaType.AUDIO),
            sizeFilter = null,
            credentialsId = null,
            scanSubdirectories = false,
            showHiddenFiles = false,
            onProgress = null
        )

        assertEquals(2, result.size)
        coVerify(exactly = 1) {
            mediaStoreRepository.getAllFilesByTypes(
                allowedTypes = setOf(MediaType.AUDIO),
                limit = 10_000,
                showHiddenFiles = false
            )
        }
    }

    @Test
    fun `scanFolder virtual all_video calls getAllFilesByTypes with VIDEO`() = runTest {
        val videoFiles = listOf(makeVideoFile("clip.mp4"))

        coEvery {
            mediaStoreRepository.getAllFilesByTypes(
                allowedTypes = setOf(MediaType.VIDEO),
                limit = 10_000,
                showHiddenFiles = false
            )
        } returns videoFiles

        val result = scanner.scanFolder(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
            supportedTypes = setOf(MediaType.VIDEO),
            sizeFilter = null,
            credentialsId = null,
            scanSubdirectories = false,
            showHiddenFiles = false,
            onProgress = null
        )

        assertEquals(1, result.size)
        assertEquals("clip.mp4", result.first().name)
    }

    @Test
    fun `scanFolder virtual all_docs uses document types from supportedTypes`() = runTest {
        val docs = listOf(makeDocFile("report.pdf", MediaType.PDF), makeDocFile("notes.txt", MediaType.TEXT))

        coEvery {
            mediaStoreRepository.getAllFilesByTypes(
                allowedTypes = setOf(MediaType.PDF, MediaType.TEXT),
                limit = 10_000,
                showHiddenFiles = false
            )
        } returns docs

        val result = scanner.scanFolder(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS,
            supportedTypes = setOf(MediaType.IMAGE, MediaType.PDF, MediaType.TEXT),
            sizeFilter = null,
            credentialsId = null,
            scanSubdirectories = false,
            showHiddenFiles = false,
            onProgress = null
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `scanFolder virtual all_docs returns empty when no doc types in supportedTypes`() = runTest {
        val result = scanner.scanFolder(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS,
            supportedTypes = setOf(MediaType.IMAGE, MediaType.VIDEO),
            sizeFilter = null,
            credentialsId = null,
            scanSubdirectories = false,
            showHiddenFiles = false,
            onProgress = null
        )

        assertEquals(0, result.size)
    }

    @Test
    fun `scanFolder virtual all_images calls getAllFilesByTypes with IMAGE and GIF`() = runTest {
        val images = listOf(
            makeMediaFile("photo.jpg", MediaType.IMAGE),
            makeMediaFile("anim.gif", MediaType.GIF)
        )

        coEvery {
            mediaStoreRepository.getAllFilesByTypes(
                allowedTypes = setOf(MediaType.IMAGE, MediaType.GIF),
                limit = 10_000,
                showHiddenFiles = false
            )
        } returns images

        val result = scanner.scanFolder(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
            supportedTypes = setOf(MediaType.IMAGE, MediaType.GIF, MediaType.VIDEO),
            sizeFilter = null,
            credentialsId = null,
            scanSubdirectories = false,
            showHiddenFiles = false,
            onProgress = null
        )

        assertEquals(2, result.size)
        assertEquals("photo.jpg", result[0].name)
        assertEquals("anim.gif", result[1].name)
    }

    @Test
    fun `scanFolder virtual all_images returns empty when no image types in supportedTypes`() = runTest {
        val result = scanner.scanFolder(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
            supportedTypes = setOf(MediaType.VIDEO, MediaType.AUDIO),
            sizeFilter = null,
            credentialsId = null,
            scanSubdirectories = false,
            showHiddenFiles = false,
            onProgress = null
        )

        assertEquals(0, result.size)
    }

    @Test
    fun `getFileCount for virtual all_images returns correct count`() = runTest {
        coEvery {
            mediaStoreRepository.countAllFilesByTypes(
                allowedTypes = setOf(MediaType.IMAGE, MediaType.GIF),
                limit = 10_000,
                showHiddenFiles = false,
                sizeFilter = null
            )
        } returns 42

        val count = scanner.getFileCount(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
            supportedTypes = setOf(MediaType.IMAGE, MediaType.GIF),
            sizeFilter = null,
            credentialsId = null,
            scanSubdirectories = false,
            showHiddenFiles = false
        )

        assertEquals(42, count)
    }

    @Test
    fun `getFileCount for virtual all_audio returns correct count`() = runTest {
        coEvery {
            mediaStoreRepository.countAllFilesByTypes(
                allowedTypes = setOf(MediaType.AUDIO),
                limit = 10_000,
                showHiddenFiles = false,
                sizeFilter = null
            )
        } returns 3

        val count = scanner.getFileCount(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
            supportedTypes = setOf(MediaType.AUDIO),
            sizeFilter = null,
            credentialsId = null,
            scanSubdirectories = false,
            showHiddenFiles = false
        )

        assertEquals(3, count)

        coVerify(exactly = 1) {
            mediaStoreRepository.countAllFilesByTypes(
                allowedTypes = setOf(MediaType.AUDIO),
                limit = 10_000,
                showHiddenFiles = false,
                sizeFilter = null
            )
        }
    }

    @Test
    fun `isWritable returns false for virtual all_audio`() = runTest {
        val result = scanner.isWritable(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
            credentialsId = null
        )
        assertFalse(result)
    }

    @Test
    fun `isWritable returns false for virtual all_video`() = runTest {
        val result = scanner.isWritable(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
            credentialsId = null
        )
        assertFalse(result)
    }

    @Test
    fun `isWritable returns false for virtual all_docs`() = runTest {
        val result = scanner.isWritable(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS,
            credentialsId = null
        )
        assertFalse(result)
    }

    @Test
    fun `isWritable returns false for virtual all_images`() = runTest {
        val result = scanner.isWritable(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
            credentialsId = null
        )
        assertFalse(result)
    }
}
