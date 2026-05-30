package com.sza.fastmediasorter.domain.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for the [FileOperationError] string formatters.
 */
class FileOperationErrorTest {

    @Test
    fun `formatTransferError lays out file source destination and error on separate lines`() {
        val result = FileOperationError.formatTransferError(
            fileName = "movie.mp4",
            sourcePath = "/a/movie.mp4",
            destinationPath = "/b/movie.mp4",
            errorMessage = "disk full",
        )
        assertEquals(
            "movie.mp4\n  From: /a/movie.mp4\n  To: /b/movie.mp4\n  Error: disk full",
            result,
        )
    }

    @Test
    fun `formatDeleteError lays out file path and error`() {
        val result = FileOperationError.formatDeleteError(
            fileName = "old.txt",
            filePath = "/a/old.txt",
            errorMessage = "permission denied",
        )
        assertEquals("old.txt\n  Path: /a/old.txt\n  Error: permission denied", result)
    }

    @Test
    fun `extractErrorMessage combines exception class name and message`() {
        val result = FileOperationError.extractErrorMessage(IOException("no space"))
        assertEquals("IOException - no space", result)
    }

    @Test
    fun `extractErrorMessage falls back when the message is null`() {
        val result = FileOperationError.extractErrorMessage(IOException())
        assertTrue(result.startsWith("IOException - "))
        assertTrue(result.endsWith("Unknown error"))
    }
}
