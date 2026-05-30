package com.sza.fastmediasorter.domain.transfer

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Unit tests for [FileOperationErrorHandler] - exception-to-message translation,
 * recoverability classification, and suggested actions. The injected [Context] is
 * never touched by the exercised code paths, so a relaxed mock suffices.
 */
class FileOperationErrorHandlerTest {

    private lateinit var handler: FileOperationErrorHandler

    @Before
    fun setup() {
        handler = FileOperationErrorHandler(mockk<Context>(relaxed = true))
    }

    @Test
    fun `handleError translates an unknown host and appends file context`() {
        val message = handler.handleError(
            UnknownHostException("nas.local"),
            operation = "copy",
            sourcePath = "/share/movie.mp4",
        )
        assertTrue(message.contains("Cannot resolve host"))
        assertTrue(message.contains("File: movie.mp4"))
    }

    @Test
    fun `translateException maps disk-full and permission IOExceptions`() {
        assertEquals("Insufficient disk space", handler.handleError(IOException("No space left on device"), "copy"))
        assertEquals("Permission denied", handler.handleError(IOException("Permission denied"), "copy"))
    }

    @Test
    fun `translateException maps timeouts and security errors`() {
        assertTrue(handler.handleError(SocketTimeoutException(), "move").contains("Connection timeout"))
        assertEquals("Permission denied", handler.handleError(SecurityException(), "delete"))
    }

    @Test
    fun `FileNotFoundException is caught by the IOException branch first`() {
        // FileNotFoundException extends IOException, and `is IOException` precedes the dedicated
        // FileNotFoundException branch in the when-expression, so the generic IO message is produced.
        val message = handler.handleError(FileNotFoundException("gone"), "move")
        assertTrue(message.contains("Network error during move"))
    }

    @Test
    fun `translateException recognises SMB status codes`() {
        val message = handler.handleError(Exception("SMBApiException: STATUS_ACCESS_DENIED"), "copy")
        assertTrue(message.contains("Access denied"))
    }

    @Test
    fun `translateException recognises authentication and connection failures`() {
        assertTrue(handler.handleError(Exception("Authentication failed"), "copy").contains("Authentication failed"))
        assertTrue(handler.handleError(Exception("Connection reset"), "copy").contains("Connection failed"))
    }

    @Test
    fun `isRecoverableError treats timeouts and generic IO as recoverable`() {
        assertTrue(handler.isRecoverableError(SocketTimeoutException()))
        assertTrue(handler.isRecoverableError(IOException("temporary glitch")))
    }

    @Test
    fun `isRecoverableError treats fatal IO and unrelated errors as non-recoverable`() {
        assertFalse(handler.isRecoverableError(IOException("No space left")))
        assertFalse(handler.isRecoverableError(IOException("Permission denied")))
        assertFalse(handler.isRecoverableError(IllegalStateException()))
    }

    @Test
    fun `getSuggestedAction returns guidance for known errors and null otherwise`() {
        assertTrue(handler.getSuggestedAction(UnknownHostException())!!.contains("network"))
        assertTrue(handler.getSuggestedAction(SecurityException())!!.contains("permissions"))
        assertTrue(handler.getSuggestedAction(IOException("No space left"))!!.contains("disk space"))
        assertNull(handler.getSuggestedAction(IllegalArgumentException()))
        assertNull(handler.getSuggestedAction(IOException("generic")))
    }
}
