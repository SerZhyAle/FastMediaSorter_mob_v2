package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.testing.createAppSettings
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DeleteFilesUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>()
    private val fileOperationUseCase = mockk<FileOperationUseCase>()
    private lateinit var useCase: DeleteFilesUseCase

    @Before
    fun setup() {
        useCase = DeleteFilesUseCase(settingsRepository, fileOperationUseCase)
    }

    private fun stubSettings(useTrash: Boolean) {
        coEvery { settingsRepository.getSettings() } returns
            flowOf(createAppSettings().copy(useTrash = useTrash))
    }

    private fun captureDelete(): CapturingSlot<FileOperation> {
        val op = slot<FileOperation>()
        coEvery { fileOperationUseCase.execute(capture(op)) } returns
            FileOperationResult.Success(processedCount = 0, operation = FileOperation.Delete(emptyList()))
        return op
    }

    @Test
    fun `soft delete enabled for local files when useTrash is on`() = runTest {
        stubSettings(useTrash = true)
        val op = captureDelete()

        useCase(listOf(File("/storage/emulated/0/DCIM/a.jpg")))

        val delete = op.captured as FileOperation.Delete
        assertTrue(delete.softDelete)
    }

    @Test
    fun `soft delete disabled when useTrash is off`() = runTest {
        stubSettings(useTrash = false)
        val op = captureDelete()

        useCase(listOf(File("/storage/emulated/0/DCIM/a.jpg")))

        assertFalse((op.captured as FileOperation.Delete).softDelete)
    }
}
