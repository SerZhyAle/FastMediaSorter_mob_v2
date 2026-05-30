package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.testing.createMediaFile
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DeleteByFileSizeUseCaseTest {

    private val getMediaFiles = mockk<GetMediaFilesUseCase>()
    private val deleteFiles = mockk<DeleteFilesUseCase>()
    private lateinit var useCase: DeleteByFileSizeUseCase

    private val mb = 1024L * 1024L

    @Before
    fun setup() {
        useCase = DeleteByFileSizeUseCase(getMediaFiles, deleteFiles)
    }

    private fun stubScan(files: List<MediaFile>) {
        every {
            getMediaFiles(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns flowOf(files)
    }

    @Test
    fun `scan requires exactly one threshold`() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase.scan(createMediaResource(), maxSizeMb = null, minSizeMb = null)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                useCase.scan(createMediaResource(), maxSizeMb = 1f, minSizeMb = 1f)
            }
        }
    }

    @Test
    fun `scan with maxSize keeps files strictly smaller than threshold`() = runTest {
        val small = createMediaFile(name = "s", size = 1 * mb)
        val big = createMediaFile(name = "b", size = 5 * mb)
        stubScan(listOf(small, big))

        val result = useCase.scan(createMediaResource(), maxSizeMb = 2f)

        assertEquals(listOf("s"), result.files.map { it.name })
        assertEquals(1 * mb, result.totalBytes)
    }

    @Test
    fun `scan with minSize keeps files strictly larger than threshold`() = runTest {
        val small = createMediaFile(name = "s", size = 1 * mb)
        val big = createMediaFile(name = "b", size = 5 * mb)
        stubScan(listOf(small, big))

        val result = useCase.scan(createMediaResource(), minSizeMb = 2f)

        assertEquals(listOf("b"), result.files.map { it.name })
        assertEquals(5 * mb, result.totalBytes)
    }

    @Test
    fun `scan returns empty result when listing throws`() = runTest {
        every {
            getMediaFiles(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("io")

        val result = useCase.scan(createMediaResource(), maxSizeMb = 2f)

        assertTrue(result.files.isEmpty())
        assertEquals(0L, result.totalBytes)
    }

    @Test
    fun `execute short-circuits to success on empty list`() = runTest {
        val result = useCase.execute(emptyList())
        result as FileOperationResult.Success
        assertEquals(0, result.processedCount)
    }

    @Test
    fun `execute delegates to deleteFilesUseCase preserving original paths`() = runTest {
        val capturedPaths = mutableListOf<String>()
        coEvery { deleteFiles(any()) } answers {
            firstArg<List<File>>().forEach { capturedPaths.add(it.absolutePath) }
            FileOperationResult.Success(2, FileOperation.Delete(emptyList()))
        }

        useCase.execute(
            listOf(
                createMediaFile(path = "smb://h/share/a.mp4"),
                createMediaFile(path = "/local/b.mp4"),
            )
        )

        assertEquals(listOf("smb://h/share/a.mp4", "/local/b.mp4"), capturedPaths)
    }

    @Test
    fun `invoke maps success result to deleted count`() = runTest {
        stubScan(listOf(createMediaFile(name = "s", size = 1 * mb)))
        coEvery { deleteFiles(any()) } returns
            FileOperationResult.Success(1, FileOperation.Delete(emptyList()))

        val result = useCase(createMediaResource(), maxSizeMb = 2f)

        assertEquals(1, result.deletedCount)
        assertEquals(0, result.failedCount)
    }

    @Test
    fun `invoke maps non-success result to failure count of scanned files`() = runTest {
        stubScan(listOf(createMediaFile(name = "s", size = 1 * mb)))
        coEvery { deleteFiles(any()) } returns FileOperationResult.Failure("nope")

        val result = useCase(createMediaResource(), maxSizeMb = 2f)

        assertEquals(0, result.deletedCount)
        assertEquals(1, result.failedCount)
    }
}
