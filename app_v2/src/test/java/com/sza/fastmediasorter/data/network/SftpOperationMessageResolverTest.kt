package com.sza.fastmediasorter.data.network

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.remote.sftp.SftpFailureCategory
import com.sza.fastmediasorter.data.remote.sftp.SftpOperationFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [SftpOperationMessageResolver] - failure to UI MessageSpec mapping. */
class SftpOperationMessageResolverTest {

    private fun failure(
        category: SftpFailureCategory,
        statusCode: Int? = null,
        copyCompleted: Boolean = false,
    ) = SftpOperationFailure(
        statusCode = statusCode,
        category = category,
        originalMessage = null,
        copyCompleted = copyCompleted,
    )

    @Test
    fun `copy-completed move takes precedence over category`() {
        val spec = SftpOperationMessageResolver.resolve(
            failure(SftpFailureCategory.PERMISSION_DENIED, statusCode = 3, copyCompleted = true)
        )
        assertEquals(R.string.error_sftp_move_copied_source_remains, spec.messageResId)
        assertTrue(spec.logLabel.contains("move-copy-ok-delete-failed"))
        assertTrue(spec.logLabel.contains("3"))
    }

    @Test
    fun `permission denied maps to access-denied resource`() {
        val spec = SftpOperationMessageResolver.resolve(
            failure(SftpFailureCategory.PERMISSION_DENIED, statusCode = 3)
        )
        assertEquals(R.string.error_sftp_access_denied, spec.messageResId)
        assertTrue(spec.logLabel.contains("permission-denied"))
    }

    @Test
    fun `generic maps to server-rejected resource`() {
        val spec = SftpOperationMessageResolver.resolve(
            failure(SftpFailureCategory.GENERIC, statusCode = 4)
        )
        assertEquals(R.string.error_sftp_server_rejected, spec.messageResId)
        assertTrue(spec.logLabel.contains("server-rejected"))
    }

    @Test
    fun `transient maps to server-rejected resource`() {
        val spec = SftpOperationMessageResolver.resolve(failure(SftpFailureCategory.TRANSIENT))
        assertEquals(R.string.error_sftp_server_rejected, spec.messageResId)
        assertTrue(spec.logLabel.contains("transient"))
    }

    @Test
    fun `expected stream close maps to server-rejected resource`() {
        val spec = SftpOperationMessageResolver.resolve(failure(SftpFailureCategory.EXPECTED_STREAM_CLOSE))
        assertEquals(R.string.error_sftp_server_rejected, spec.messageResId)
        assertTrue(spec.logLabel.contains("expected-stream-close"))
    }
}
