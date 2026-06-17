package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.testing.MainDispatcherRule
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CreateTextNoteUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val handler = mockk<UnifiedFileOperationHandler>()
    private lateinit var useCase: CreateTextNoteUseCase

    @Before
    fun setup() {
        useCase = CreateTextNoteUseCase(handler, mockk(relaxed = true))
        coEvery { handler.executeCreateTextFile(any(), any(), any(), any()) } answers {
            Result.success("/local/dir/" + secondArg<String>())
        }
    }

    private val localResource = createMediaResource(id = 1L, path = "/local/dir", isReadOnly = false)

    @Test
    fun `read-only resource fails without delegating`() = runTest {
        val result = useCase(createMediaResource(isReadOnly = true), "/local/dir", "note.txt")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { handler.executeCreateTextFile(any(), any(), any(), any()) }
    }

    @Test
    fun `blank name fails`() = runTest {
        assertTrue(useCase(localResource, "/local/dir", "   ").isFailure)
    }

    @Test
    fun `forbidden character fails`() = runTest {
        assertTrue(useCase(localResource, "/local/dir", "a:b.txt").isFailure)
    }

    @Test
    fun `over-length name fails`() = runTest {
        val tooLong = "a".repeat(256) + ".txt"
        assertTrue(useCase(localResource, "/local/dir", tooLong).isFailure)
    }

    @Test
    fun `extensionless name gets txt appended`() = runTest {
        val nameSlot = slot<String>()
        coEvery { handler.executeCreateTextFile(any(), capture(nameSlot), any(), any()) } returns
            Result.success("/local/dir/mynote.txt")

        useCase(localResource, "/local/dir", "mynote")

        assertEquals("mynote.txt", nameSlot.captured)
    }

    @Test
    fun `name with existing extension is preserved`() = runTest {
        val nameSlot = slot<String>()
        coEvery { handler.executeCreateTextFile(any(), capture(nameSlot), any(), any()) } returns
            Result.success("/local/dir/log.md")

        useCase(localResource, "/local/dir", "log.md")

        assertEquals("log.md", nameSlot.captured)
    }

    @Test
    fun `valid request delegates with resource id and content`() = runTest {
        val result = useCase(localResource, "/local/dir", "note.txt", content = "hello")

        assertTrue(result.isSuccess)
        coVerify {
            handler.executeCreateTextFile(
                parentPath = "/local/dir",
                fileName = "note.txt",
                resourceId = 1L,
                content = "hello",
            )
        }
    }

    @Test
    fun `name is trimmed before validation`() = runTest {
        val nameSlot = slot<String>()
        coEvery { handler.executeCreateTextFile(any(), capture(nameSlot), any(), any()) } returns
            Result.success("/local/dir/trimmed.txt")

        useCase(localResource, "/local/dir", "  trimmed.txt  ")

        assertEquals("trimmed.txt", nameSlot.captured)
        assertFalse(nameSlot.captured.contains(" "))
    }
}
