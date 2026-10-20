package com.sza.fastmediasorter.wear.data.network

import com.hierynomus.mssmb2.SMBApiException
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeTunnelUnavailableException
import com.sza.fastmediasorter.wear.data.network.ftp.FtpStoreRefusedException
import com.sza.fastmediasorter.wear.data.network.sftp.WearResourcePinMissingException
import com.sza.fastmediasorter.wear.domain.model.WearNetworkFailure
import com.sza.fastmediasorter.wear.domain.model.WearResourceSendFailure
import org.apache.commons.net.ftp.FTPReply
import java.io.FileNotFoundException
import javax.inject.Inject

/**
 * S4230: turns what a write to a resource threw into the one reason the wearer can act on.
 *
 * Typed library failures are read first, down the cause chain, because JSch, commons-net and smbj each wrap the
 * socket error differently and a check on the top throwable alone would call every refused write "broke off". What
 * no type names is left to [WearNetworkFailureClassifier], which already knows host, timeout and login failures.
 * The exception message is never copied into the result: it can name a path, a host or a user.
 */
class WearResourceSendFailureClassifier @Inject constructor(
    private val network: WearNetworkFailureClassifier
) {

    fun classify(error: Throwable): WearResourceSendFailure =
        typedFailure(error)
            ?: fromNetwork(network.classify(error))
            ?: messageFailure(error)
            ?: WearResourceSendFailure.BROKEN

    private fun typedFailure(error: Throwable): WearResourceSendFailure? {
        var current: Throwable? = error
        var depth = 0
        var found: WearResourceSendFailure? = null
        while (found == null && current != null && depth < MAX_CAUSE_DEPTH) {
            found = typedOne(current)
            current = current.cause
            depth++
        }
        return found
    }

    private fun typedOne(error: Throwable): WearResourceSendFailure? = when (error) {
        is SftpException -> fromSftpStatus(error.id, error.message)
        is FtpStoreRefusedException -> fromFtpReply(error.replyCode)
        is SMBApiException -> fromSmbStatus(error.status?.name)
        is FileNotFoundException, is SecurityException -> WearResourceSendFailure.SOURCE_UNREADABLE
        is WearExchangeNeedsOwnNetworkException -> WearResourceSendFailure.NO_NETWORK
        is WearResourcePinMissingException -> WearResourceSendFailure.PIN_NEEDED
        is WearExchangeEntryMissingException -> WearResourceSendFailure.NOT_CONNECTED
        is WearExchangeTunnelUnavailableException -> WearResourceSendFailure.UNREACHABLE
        else -> null
    }

    private fun fromNetwork(failure: WearNetworkFailure): WearResourceSendFailure? = when (failure) {
        WearNetworkFailure.UNKNOWN_HOST,
        WearNetworkFailure.TIMEOUT,
        WearNetworkFailure.CONNECTION_REFUSED -> WearResourceSendFailure.UNREACHABLE
        WearNetworkFailure.AUTH_REJECTED -> WearResourceSendFailure.REFUSED
        WearNetworkFailure.OTHER -> null
    }

    /** The FTP session path raises `IllegalStateException` with these two sentences when connect or login fails. */
    private fun messageFailure(error: Throwable): WearResourceSendFailure? {
        val text = error.message.orEmpty().lowercase()
        return when {
            text.contains("login failed") -> WearResourceSendFailure.REFUSED
            text.contains("refused connection") -> WearResourceSendFailure.UNREACHABLE
            else -> null
        }
    }

    /**
     * An SFTP server answers a full disk with a plain failure status and a sentence, so the sentence is read too;
     * a vanished directory is the resource being unavailable, not the file being refused.
     */
    internal fun fromSftpStatus(id: Int, message: String?): WearResourceSendFailure {
        val text = message.orEmpty().lowercase()
        return when {
            id == ChannelSftp.SSH_FX_PERMISSION_DENIED -> WearResourceSendFailure.REFUSED
            id == ChannelSftp.SSH_FX_NO_SUCH_FILE -> WearResourceSendFailure.UNREACHABLE
            NO_SPACE_WORDS.any { text.contains(it) } -> WearResourceSendFailure.NO_SPACE
            else -> WearResourceSendFailure.BROKEN
        }
    }

    internal fun fromFtpReply(code: Int): WearResourceSendFailure = when (code) {
        FTPReply.INSUFFICIENT_STORAGE,
        FTPReply.STORAGE_ALLOCATION_EXCEEDED -> WearResourceSendFailure.NO_SPACE
        FTPReply.FILE_ACTION_NOT_TAKEN,
        FTPReply.NOT_LOGGED_IN,
        FTPReply.NEED_ACCOUNT_FOR_STORING_FILES,
        FTPReply.FILE_UNAVAILABLE,
        FTPReply.FILE_NAME_NOT_ALLOWED -> WearResourceSendFailure.REFUSED
        FTPReply.SERVICE_NOT_AVAILABLE,
        FTPReply.CANNOT_OPEN_DATA_CONNECTION -> WearResourceSendFailure.UNREACHABLE
        else -> WearResourceSendFailure.BROKEN
    }

    internal fun fromSmbStatus(statusName: String?): WearResourceSendFailure = when (statusName) {
        "STATUS_DISK_FULL",
        "STATUS_QUOTA_EXCEEDED" -> WearResourceSendFailure.NO_SPACE
        "STATUS_ACCESS_DENIED",
        "STATUS_LOGON_FAILURE",
        "STATUS_WRONG_PASSWORD",
        "STATUS_ACCOUNT_DISABLED",
        "STATUS_MEDIA_WRITE_PROTECTED",
        "STATUS_SHARING_VIOLATION" -> WearResourceSendFailure.REFUSED
        "STATUS_BAD_NETWORK_NAME",
        "STATUS_OBJECT_PATH_NOT_FOUND",
        "STATUS_OBJECT_NAME_NOT_FOUND" -> WearResourceSendFailure.UNREACHABLE
        else -> WearResourceSendFailure.BROKEN
    }

    private companion object {
        /** A cause chain this deep is a cycle or a wrapper storm, not a diagnosis. */
        const val MAX_CAUSE_DEPTH = 10

        val NO_SPACE_WORDS = listOf("no space", "disk full", "quota")
    }
}
