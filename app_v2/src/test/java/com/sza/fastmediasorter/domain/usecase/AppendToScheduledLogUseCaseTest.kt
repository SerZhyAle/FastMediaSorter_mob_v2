package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppendToScheduledLogUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private lateinit var filesDir: File
    private lateinit var useCase: AppendToScheduledLogUseCase

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        every { context.filesDir } returns filesDir
        useCase = AppendToScheduledLogUseCase(context)
    }

    private fun logFile() = File(filesDir, AppendToScheduledLogUseCase.LOG_FILE_NAME)

    @Test
    fun `append creates file and writes line with newline`() {
        useCase("first line")

        assertEquals("first line\n", logFile().readText())
    }

    @Test
    fun `multiple appends accumulate lines in order`() {
        useCase("a")
        useCase("b")
        useCase("c")

        assertEquals(listOf("a", "b", "c"), logFile().readLines())
    }

    @Test
    fun `oversized log is trimmed to the last 500 lines`() {
        // Pre-seed the file beyond the 1 MB trim threshold with > 500 lines.
        val line = "x".repeat(2_048) // ~2 KB per line; ~600 lines -> > 1 MB
        val seeded = (1..600).joinToString("\n") { "$it-$line" } + "\n"
        logFile().writeText(seeded)

        useCase("LAST")

        val lines = logFile().readLines()
        assertEquals(500, lines.size)
        // Trim keeps the tail, so the just-appended line must survive.
        assertTrue(lines.last().contains("LAST") || lines.last().isEmpty())
        assertTrue(lines.any { it.contains("LAST") })
    }

    @Test
    fun `small log is not trimmed`() {
        repeat(10) { useCase("line-$it") }

        assertEquals(10, logFile().readLines().size)
    }
}
