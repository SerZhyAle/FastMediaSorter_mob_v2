package com.sza.fastmediasorter.data.network.exceptions

import android.content.Context
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.remote.sftp.SftpFailureCategory
import com.sza.fastmediasorter.data.remote.sftp.SftpOperationFailure

/**
 * Resolves a Browse-screen scan/load [Throwable] to a user-facing, resource-backed message.
 *
 * S2196: single copy of a heuristic that previously existed twice - in
 * `com.sza.fastmediasorter.ui.browse.BrowseViewModel` and in
 * `com.sza.fastmediasorter.ui.browse.managers.BrowseLoadingAuxManager` - and had already drifted:
 * only one of the two copies recognized [ScanTimeoutException] by type, so the same scan-watchdog
 * abort was described differently to the user depending on which class handled it.
 *
 * Lives in `data/network/exceptions/`, not `ui/browse/`, mirroring [NetworkErrorMessageMapper] -
 * a pure Throwable-to-string-resource mapper called directly from UI is the established shape here
 * (see that class's own KDoc); a Browse-owned copy under `ui/` would import these same exception/SFTP
 * types from a UI-package file, which is exactly what the `ui-imports-data` gate (S2103) exists to
 * catch. Kept separate from [NetworkErrorMessageMapper] itself (not merged into it) because the two
 * use different string resources with different wording (`friendly_copy_error_*` here vs
 * `error_network_*` there) and a different classification mechanism (direct [SftpOperationFailure]
 * category vs the typed [NetworkException] hierarchy) - merging would change Browse's displayed text.
 */
object BrowseFriendlyErrorResolver {

    fun message(context: Context, throwable: Throwable): String =
        context.getString(resolveRes(throwable))

    @StringRes
    fun resolveRes(throwable: Throwable): Int {
        val sftpCategory = SftpOperationFailure.fromThrowable(throwable).category
        return when {
            // Checked by type before the message-based heuristics: WifiRequiredException is a Wi-Fi
            // gate rejection (not a generic outage); a scan-watchdog abort is a distinct timeout shape
            // (a folder that never finished listing, not a per-request socket timeout); SFTP protocol
            // status is locale-independent unlike the server's text (Windows OpenSSH sends "cannot
            // find the file specified", which no message rule below matches).
            throwable is WifiRequiredException -> R.string.error_wifi_required_smb
            throwable is ScanTimeoutException -> R.string.error_scan_timeout
            sftpCategory == SftpFailureCategory.NOT_FOUND -> R.string.friendly_copy_error_not_found
            sftpCategory == SftpFailureCategory.PERMISSION_DENIED -> R.string.friendly_copy_error_access_denied
            else -> resolveFromMessage(throwable.message.orEmpty())
        }
    }

    // Split from resolveRes to stay under the CyclomaticComplexMethod threshold - the message-based
    // fallback heuristic alone (below) already carries most of the branch/operator count.
    @StringRes
    private fun resolveFromMessage(message: String): Int =
        resolveAuthOrAccessRes(message)
            ?: resolveNotFoundOrConnectivityRes(message)
            ?: resolveTimeoutOrGenericConnectionRes(message)
            ?: R.string.friendly_copy_error_generic

    @StringRes
    private fun resolveAuthOrAccessRes(message: String): Int? = when {
        message.contains("Authentication", ignoreCase = true) ||
            message.contains("LOGON_FAILURE", ignoreCase = true) ||
            message.contains("Not authenticated", ignoreCase = true) ->
            R.string.friendly_copy_error_auth_failed

        (
            message.contains("permission", ignoreCase = true) &&
                message.contains("denied", ignoreCase = true)
            ) ||
            message.contains("STATUS_ACCESS_DENIED", ignoreCase = true) ||
            message.contains("access denied", ignoreCase = true) ->
            R.string.friendly_copy_error_access_denied

        else -> null
    }

    @StringRes
    private fun resolveNotFoundOrConnectivityRes(message: String): Int? = when {
        message.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) ||
            message.contains("STATUS_OBJECT_NAME_NOT_FOUND", ignoreCase = true) ||
            message.contains("STATUS_OBJECT_PATH_NOT_FOUND", ignoreCase = true) ||
            message.contains("not found", ignoreCase = true) ->
            R.string.friendly_copy_error_not_found

        message.contains("unreachable", ignoreCase = true) ||
            message.contains("Cannot resolve host", ignoreCase = true) ||
            message.contains("Unknown host", ignoreCase = true) ->
            R.string.friendly_copy_error_no_connection

        message.contains("Connection reset", ignoreCase = true) ||
            message.contains("connection closed", ignoreCase = true) ||
            message.contains("broken pipe", ignoreCase = true) ||
            message.contains("connection lost", ignoreCase = true) ->
            R.string.error_network_connection_lost

        else -> null
    }

    @StringRes
    private fun resolveTimeoutOrGenericConnectionRes(message: String): Int? = when {
        message.contains("timed out", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("SocketTimeoutException", ignoreCase = true) ->
            R.string.error_network_timeout

        message.contains("Connection", ignoreCase = true) ||
            message.contains("Network", ignoreCase = true) ->
            R.string.error_network_connection

        else -> null
    }
}
