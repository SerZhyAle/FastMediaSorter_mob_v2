package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Covers [GetScheduledOperationsLogUseCase] (exists/absent branch) and
 * [ClearScheduledOperationsLogUseCase] (file delete) against a real temp filesDir.
 */
class ScheduledOperationsLogUseCasesTest {

    private lateinit var filesDir: File
    private val context = mockk<Context>()
    private val logFileName = AppendToScheduledLogUseCase.LOG_FILE_NAME

    @Before
    fun setup() {
        filesDir = Files.createTempDirectory("sched-log").toFile()
        every { context.filesDir } returns filesDir
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `get returns empty string when log file absent`() {
        val useCase = GetScheduledOperationsLogUseCase(context)
        assertEquals("", useCase())
    }

    @Test
    fun `get returns file contents when log file present`() {
        File(filesDir, logFileName).writeText("line1\nline2")
        val useCase = GetScheduledOperationsLogUseCase(context)
        assertEquals("line1\nline2", useCase())
    }

    @Test
    fun `clear deletes the log file`() {
        val file = File(filesDir, logFileName).apply { writeText("data") }
        val useCase = ClearScheduledOperationsLogUseCase(context)

        useCase()

        assertFalse(file.exists())
    }

    @Test
    fun `clear is a no-op when file is absent`() {
        val useCase = ClearScheduledOperationsLogUseCase(context)
        // Should not throw.
        useCase()
        assertFalse(File(filesDir, logFileName).exists())
    }
}
