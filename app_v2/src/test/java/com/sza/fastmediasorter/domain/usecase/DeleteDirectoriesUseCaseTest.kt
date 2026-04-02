package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeleteDirectoriesUseCaseTest {

    private val handler: UnifiedFileOperationHandler = mockk()
    private lateinit var useCase: DeleteDirectoriesUseCase

    @Before
    fun setup() {
        useCase = DeleteDirectoriesUseCase(handler)
    }

    private fun dir(path: String, name: String = path.trimEnd('/').substringAfterLast('/')) = MediaFile(
        name = name,
        path = path,
        type = MediaType.IMAGE,
        size = 0L,
        createdDate = 0L,
        isDirectory = true
    )

    @Test
    fun `invoke with empty list returns success 0`() = runTest {
        val result = useCase(emptyList())
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `invoke deletes single directory and returns count`() = runTest {
        coEvery { handler.executeDeleteDirectory("/storage/Photos", any()) } returns Result.success(5)

        val result = useCase(listOf(dir("/storage/Photos")))
        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrThrow())
        coVerify(exactly = 1) { handler.executeDeleteDirectory("/storage/Photos", any()) }
    }

    @Test
    fun `invoke aggregates counts from multiple directories`() = runTest {
        coEvery { handler.executeDeleteDirectory("/a", any()) } returns Result.success(3)
        coEvery { handler.executeDeleteDirectory("/b", any()) } returns Result.success(7)

        val result = useCase(listOf(dir("/a"), dir("/b")))
        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrThrow())
    }

    @Test
    fun `invoke returns failure on partial error`() = runTest {
        coEvery { handler.executeDeleteDirectory("/ok", any()) } returns Result.success(2)
        coEvery { handler.executeDeleteDirectory("/fail", any()) } returns Result.failure(Exception("perm denied"))

        val result = useCase(listOf(dir("/ok"), dir("/fail")))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("perm denied"))
    }

    @Test
    fun `invoke returns failure when all fail`() = runTest {
        coEvery { handler.executeDeleteDirectory(any(), any()) } returns Result.failure(Exception("error"))

        val result = useCase(listOf(dir("/x"), dir("/y")))
        assertTrue(result.isFailure)
    }
}
