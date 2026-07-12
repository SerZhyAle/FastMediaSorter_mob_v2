package com.sza.fastmediasorter.data.network

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.remote.sftp.SftpFailureCategory
import com.sza.fastmediasorter.data.remote.sftp.SftpOperationFailure

/**
 * Translates a typed [SftpOperationFailure] into UI-ready resource identifiers
 * and a short log label.
 *
 * Kept separate from the classifier so that Android resource dependencies stay
 * out of the domain/data-source layer.
 *
 * S0149 - message contract for SFTP write-operation failures.
 */
object SftpOperationMessageResolver {

    /** Resolution output - everything the UI layer needs without knowing error internals. */
    data class MessageSpec(
        /** String resource id for the primary user-facing message. */
        val messageResId: Int,
        /** Format args for the resource string (may be empty). */
        val formatArgs: Array<Any> = emptyArray(),
        /** Short label logged at debug level for filtering in logcat. */
        val logLabel: String,
    ) {
        // equals/hashCode override needed because Array<Any> breaks data-class defaults
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MessageSpec) return false
            return messageResId == other.messageResId &&
                formatArgs.contentEquals(other.formatArgs) &&
                logLabel == other.logLabel
        }

        override fun hashCode(): Int {
            var result = messageResId
            result = 31 * result + formatArgs.contentHashCode()
            result = 31 * result + logLabel.hashCode()
            return result
        }
    }

    /**
     * Resolve a [MessageSpec] from a typed SFTP failure.
     *
     * The [copyCompleted] flag in the failure drives the special case where
     * a move operation copied the file successfully but could not delete the
     * source - the file exists at the destination, so the user needs to know
     * that to avoid double-action.
     */
    fun resolve(failure: SftpOperationFailure): MessageSpec {
        // Special case: move - copy phase succeeded, source deletion failed.
        // Report this regardless of the delete-failure category so the user
        // knows the destination file is intact.
        if (failure.copyCompleted) {
            return MessageSpec(
                messageResId = R.string.error_sftp_move_copied_source_remains,
                logLabel = "sftp/move-copy-ok-delete-failed[${failure.statusCode}]",
            )
        }

        return when (failure.category) {
            SftpFailureCategory.PERMISSION_DENIED -> MessageSpec(
                messageResId = R.string.error_sftp_access_denied,
                logLabel = "sftp/permission-denied[${failure.statusCode}]",
            )

            SftpFailureCategory.NOT_FOUND -> MessageSpec(
                messageResId = R.string.friendly_copy_error_not_found,
                logLabel = "sftp/not-found[${failure.statusCode}]",
            )

            SftpFailureCategory.GENERIC -> MessageSpec(
                messageResId = R.string.error_sftp_server_rejected,
                logLabel = "sftp/server-rejected[${failure.statusCode}]",
            )

            SftpFailureCategory.TRANSIENT -> MessageSpec(
                messageResId = R.string.error_sftp_server_rejected,
                logLabel = "sftp/transient[${failure.statusCode}]",
            )

            SftpFailureCategory.EXPECTED_STREAM_CLOSE -> MessageSpec(
                messageResId = R.string.error_sftp_server_rejected,
                logLabel = "sftp/expected-stream-close",
            )
        }
    }
}
