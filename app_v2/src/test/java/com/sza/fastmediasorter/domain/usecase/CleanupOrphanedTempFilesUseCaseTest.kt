package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.cloud.CloudPathParser
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CleanupOrphanedTempFilesUseCaseTest {

    private val smbHandler = mockk<SmbFileOperationHandler>()
    private val cloudHandler = mockk<CloudFileOperationHandler>()
    private val cloudPathParser = CloudPathParser()

    private lateinit var useCase: CleanupOrphanedTempFilesUseCase

    @Before
    fun setup() {
        useCase = CleanupOrphanedTempFilesUseCase(smbHandler, cloudHandler, cloudPathParser)
    }

    @Test
    fun `cloud path is skipped returning zero`() = runTest {
        val result = useCase("cloud://gdrive/folder")

        assertEquals(0, result.getOrThrow())
        coVerify(exactly = 0) { smbHandler.listFiles(any()) }
    }

    @Test
    fun `virtual path is skipped returning zero`() = runTest {
        val result = useCase("virtual://all_video")

        assertEquals(0, result.getOrThrow())
        coVerify(exactly = 0) { smbHandler.listFiles(any()) }
    }

    @Test
    fun `list failure propagates as Result failure`() = runTest {
        coEvery { smbHandler.listFiles(any()) } returns Result.failure(RuntimeException("listing failed"))

        val result = useCase("/local/dir")

        assertTrue(result.isFailure)
        assertEquals("listing failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `no temp files returns zero and deletes nothing`() = runTest {
        coEvery { smbHandler.listFiles(any()) } returns
            Result.success(listOf("/local/dir/a.jpg", "/local/dir/b.mp4"))

        val result = useCase("/local/dir")

        assertEquals(0, result.getOrThrow())
        coVerify(exactly = 0) { smbHandler.deleteFile(any()) }
    }

    @Test
    fun `temp files are filtered and deleted`() = runTest {
        coEvery { smbHandler.listFiles(any()) } returns Result.success(
            listOf(
                "/local/dir/keep.jpg",
                "/local/dir/x.jpg.temp_copy",
                "/local/dir/y.mp4.temp_copy",
            )
        )
        coEvery { smbHandler.deleteFile(any()) } returns Result.success(Unit)

        val result = useCase("/local/dir")

        assertEquals(2, result.getOrThrow())
        coVerify(exactly = 1) { smbHandler.deleteFile("/local/dir/x.jpg.temp_copy") }
        coVerify(exactly = 1) { smbHandler.deleteFile("/local/dir/y.mp4.temp_copy") }
        coVerify(exactly = 0) { smbHandler.deleteFile("/local/dir/keep.jpg") }
    }

    @Test
    fun `partial delete failure returns count of successful deletes`() = runTest {
        coEvery { smbHandler.listFiles(any()) } returns Result.success(
            listOf("/local/dir/a.temp_copy", "/local/dir/b.temp_copy")
        )
        coEvery { smbHandler.deleteFile("/local/dir/a.temp_copy") } returns Result.success(Unit)
        coEvery { smbHandler.deleteFile("/local/dir/b.temp_copy") } returns
            Result.failure(RuntimeException("locked"))

        val result = useCase("/local/dir")

        assertEquals(1, result.getOrThrow())
    }

    @Test
    fun `gdrive scheme dispatches to cloud handler`() = runTest {
        coEvery { cloudHandler.listFiles(any()) } returns Result.success(emptyList())

        val result = useCase("gdrive:/folder/path")

        assertEquals(0, result.getOrThrow())
        coVerify { cloudHandler.listFiles("gdrive:/folder/path") }
        coVerify(exactly = 0) { smbHandler.listFiles(any()) }
    }
}
