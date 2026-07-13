package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException

/** Unit tests for [SftpOperationFailure] classification from throwables. */
class SftpOperationFailureTest {

    // ── fromThrowable ────────────────────────────────────────────────────────

    @Test
    fun `permission denied status maps to PERMISSION_DENIED`() {
        val ex = SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "denied")
        val failure = SftpOperationFailure.fromThrowable(ex, operationKind = "delete")
        assertEquals(SftpFailureCategory.PERMISSION_DENIED, failure.category)
        assertEquals(ChannelSftp.SSH_FX_PERMISSION_DENIED, failure.statusCode)
        assertEquals("delete", failure.operationKind)
    }

    @Test
    fun `no-such-file status maps to NOT_FOUND`() {
        // S1000: Windows OpenSSH sends a locale-dependent "cannot find the file specified" text,
        // so classification must key off the protocol id, not the message.
        val ex = SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "GetFileAttributesEx: not there")
        val failure = SftpOperationFailure.fromThrowable(ex)
        assertEquals(SftpFailureCategory.NOT_FOUND, failure.category)
        assertEquals(ChannelSftp.SSH_FX_NO_SUCH_FILE, failure.statusCode)
    }

    @Test
    fun `other sftp status maps to GENERIC`() {
        val ex = SftpException(ChannelSftp.SSH_FX_FAILURE, "failure")
        val failure = SftpOperationFailure.fromThrowable(ex)
        assertEquals(SftpFailureCategory.GENERIC, failure.category)
        assertEquals(ChannelSftp.SSH_FX_FAILURE, failure.statusCode)
    }

    @Test
    fun `non-sftp throwable maps to TRANSIENT with null status`() {
        val failure = SftpOperationFailure.fromThrowable(IOException("socket dead"))
        assertEquals(SftpFailureCategory.TRANSIENT, failure.category)
        assertNull(failure.statusCode)
    }

    @Test
    fun `sftp exception wrapped in cause chain is extracted`() {
        val ex = SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "denied")
        val wrapped = RuntimeException("wrapper", IOException("io", ex))
        val failure = SftpOperationFailure.fromThrowable(wrapped)
        assertEquals(SftpFailureCategory.PERMISSION_DENIED, failure.category)
    }

    @Test
    fun `copyCompleted flag is propagated`() {
        val failure = SftpOperationFailure.fromThrowable(
            IOException("delete failed"),
            operationKind = "move",
            copyCompleted = true
        )
        assertEquals(true, failure.copyCompleted)
    }

    @Test
    fun `original message is preserved`() {
        val failure = SftpOperationFailure.fromThrowable(IOException("specific text"))
        assertEquals("specific text", failure.originalMessage)
    }

    // ── fromStreamCloseThrowable ─────────────────────────────────────────────

    @Test
    fun `interrupted io close is EXPECTED_STREAM_CLOSE`() {
        val failure = SftpOperationFailure.fromStreamCloseThrowable(
            InterruptedIOException("interrupted"),
            channelAlreadyBroken = false,
            streamWasOpen = true
        )
        assertEquals(SftpFailureCategory.EXPECTED_STREAM_CLOSE, failure.category)
    }

    @Test
    fun `already broken channel close is EXPECTED_STREAM_CLOSE`() {
        val failure = SftpOperationFailure.fromStreamCloseThrowable(
            IOException("anything"),
            channelAlreadyBroken = true,
            streamWasOpen = true
        )
        assertEquals(SftpFailureCategory.EXPECTED_STREAM_CLOSE, failure.category)
    }

    @Test
    fun `pipe closed on open stream is EXPECTED_STREAM_CLOSE`() {
        val failure = SftpOperationFailure.fromStreamCloseThrowable(
            IOException("Pipe closed"),
            channelAlreadyBroken = false,
            streamWasOpen = true
        )
        assertEquals(SftpFailureCategory.EXPECTED_STREAM_CLOSE, failure.category)
    }

    @Test
    fun `pipe closed when stream not open is TRANSIENT`() {
        val failure = SftpOperationFailure.fromStreamCloseThrowable(
            IOException("Pipe closed"),
            channelAlreadyBroken = false,
            streamWasOpen = false
        )
        assertEquals(SftpFailureCategory.TRANSIENT, failure.category)
    }

    @Test
    fun `unrelated close failure is TRANSIENT`() {
        val failure = SftpOperationFailure.fromStreamCloseThrowable(
            IOException("connection reset"),
            channelAlreadyBroken = false,
            streamWasOpen = true
        )
        assertEquals(SftpFailureCategory.TRANSIENT, failure.category)
        assertNull(failure.statusCode)
    }
}
