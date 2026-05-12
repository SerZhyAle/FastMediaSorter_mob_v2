package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException

/**
 * Typed classification of an SFTP write-operation failure.
 *
 * Preserves the raw SFTP protocol status code (SftpException.id) so that
 * upper layers can produce specific user-facing messages instead of generic
 * "operation failed" text.
 *
 * S0149 — conservative approach: only SSH_FX_PERMISSION_DENIED (3) maps to
 * the "access denied" category; all other failures fall into GENERIC.
 * TRANSIENT is reserved for connection/IO failures that bypass the SFTP protocol layer.
 */
enum class SftpFailureCategory {
    /** SFTP status code 3 — server explicitly denied the operation. */
    PERMISSION_DENIED,

    /** Any other SFTP protocol error or non-SFTP exception. */
    GENERIC,

    /** Network/IO failure that did not produce an SFTP status code. */
    TRANSIENT,
}

data class SftpOperationFailure(
    /** SFTP protocol status code from SftpException.id; null when cause is not SftpException. */
    val statusCode: Int?,
    val category: SftpFailureCategory,
    val originalMessage: String?,
    /** Human-readable label for the operation that failed ("upload", "delete", "rename", etc.). */
    val operationKind: String? = null,
    /**
     * True when this failure occurred during the delete phase of a move operation,
     * meaning the copy phase completed successfully — the file exists at the destination.
     */
    val copyCompleted: Boolean = false,
) {
    companion object {
        /**
         * Classify a throwable without inspecting its message text.
         *
         * Walks the cause chain looking for SftpException so that wrappers that
         * re-throw without discarding the original cause are handled transparently.
         */
        fun fromThrowable(
            throwable: Throwable,
            operationKind: String? = null,
            copyCompleted: Boolean = false,
        ): SftpOperationFailure {
            val sftpEx = extractSftpException(throwable)
            val statusCode = sftpEx?.id

            val category = when {
                sftpEx == null -> SftpFailureCategory.TRANSIENT
                statusCode == ChannelSftp.SSH_FX_PERMISSION_DENIED -> SftpFailureCategory.PERMISSION_DENIED
                else -> SftpFailureCategory.GENERIC
            }

            return SftpOperationFailure(
                statusCode = statusCode,
                category = category,
                originalMessage = throwable.message,
                operationKind = operationKind,
                copyCompleted = copyCompleted,
            )
        }

        /** Walks the cause chain looking for the first SftpException. */
        private fun extractSftpException(throwable: Throwable): SftpException? {
            if (throwable is SftpException) return throwable
            val cause = throwable.cause ?: return null
            // Guard against circular cause chains (JDK allows them in theory)
            if (cause === throwable) return null
            return extractSftpException(cause)
        }
    }
}
