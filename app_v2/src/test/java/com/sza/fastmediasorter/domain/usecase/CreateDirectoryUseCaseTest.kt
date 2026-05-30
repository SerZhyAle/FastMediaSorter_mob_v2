package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CreateDirectoryUseCaseTest {

    private val handler = mockk<UnifiedFileOperationHandler>()
    private lateinit var useCase: CreateDirectoryUseCase

    private val resource = createMediaResource(isReadOnly = false)

    @Before
    fun setup() {
        useCase = CreateDirectoryUseCase(handler)
        coEvery { handler.executeCreateDirectory(any()) } answers { Result.success(firstArg()) }
    }

    @Test
    fun `read-only resource fails without delegating`() = runTest {
        val result = useCase(createMediaResource(isReadOnly = true), "/p", "dir")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { handler.executeCreateDirectory(any()) }
    }

    @Test
    fun `empty name fails`() = runTest {
        assertTrue(useCase(resource, "/p", "   ").isFailure)
    }

    @Test
    fun `forbidden character fails`() = runTest {
        assertTrue(useCase(resource, "/p", "a/b").isFailure)
    }

    @Test
    fun `over-length name fails`() = runTest {
        assertTrue(useCase(resource, "/p", "x".repeat(256)).isFailure)
    }

    @Test
    fun `path joined with single slash when parent has no trailing slash`() = runTest {
        val pathSlot = slot<String>()
        coEvery { handler.executeCreateDirectory(capture(pathSlot)) } returns Result.success("ok")

        useCase(resource, "/parent", "child")

        assertEquals("/parent/child", pathSlot.captured)
    }

    @Test
    fun `path joined without doubling slash when parent ends with slash`() = runTest {
        val pathSlot = slot<String>()
        coEvery { handler.executeCreateDirectory(capture(pathSlot)) } returns Result.success("ok")

        useCase(resource, "smb://h/share/", "child")

        assertEquals("smb://h/share/child", pathSlot.captured)
    }

    @Test
    fun `name is trimmed before path construction`() = runTest {
        val pathSlot = slot<String>()
        coEvery { handler.executeCreateDirectory(capture(pathSlot)) } returns Result.success("ok")

        useCase(resource, "/p", "  spaced  ")

        assertEquals("/p/spaced", pathSlot.captured)
    }
}
