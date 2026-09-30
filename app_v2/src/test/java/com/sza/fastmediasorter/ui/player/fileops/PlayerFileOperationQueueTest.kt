package com.sza.fastmediasorter.ui.player.fileops

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.testing.createAppSettings
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class PlayerFileOperationQueueTest {

    private val settingsRepository = mockk<SettingsRepository>()
    private val fileOperationUseCase = mockk<FileOperationUseCase>()
    private val executedNames = Collections.synchronizedList(mutableListOf<String>())
    private val secondExecuted = CompletableDeferred<Unit>()

    @Test
    fun `operation enqueued while the idle worker exits is still processed`() = runBlocking {
        coEvery { settingsRepository.getSettings() } returns flowOf(createAppSettings())
        coEvery { fileOperationUseCase.execute(any(), any()) } answers {
            val operation = firstArg<FileOperation>() as FileOperation.Rename
            executedNames += operation.newName
            if (operation.newName == SECOND) {
                secondExecuted.complete(Unit)
            }
            FileOperationResult.Success(processedCount = 1, operation = operation)
        }
        val queue = PlayerFileOperationQueue(this + Dispatchers.Default, fileOperationUseCase, settingsRepository)
        val raced = AtomicBoolean(false)
        queue.afterWorkerRetiredHook = {
            if (raced.compareAndSet(false, true)) {
                queue.enqueue(rename(SECOND))
            }
        }

        queue.enqueue(rename(FIRST))

        val completed = withTimeoutOrNull(TIMEOUT_MS) { secondExecuted.await() }
        assertEquals(Unit, completed)
        assertEquals(listOf(FIRST, SECOND), executedNames.toList())
    }

    private fun rename(newName: String) = PlayerFileOperation.Rename(
        sourcePath = "/tmp/source.jpg",
        sourceName = "source.jpg",
        sourceCredentialsId = null,
        newName = newName,
    )

    private companion object {
        const val FIRST = "first.jpg"
        const val SECOND = "second.jpg"
        const val TIMEOUT_MS = 5_000L
    }
}
