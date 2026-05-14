package com.sza.fastmediasorter.data.remote.ftp

import org.apache.commons.net.ftp.FTPClient
import timber.log.Timber
import java.io.IOException

/**
 * Wrapper around [FTPClient.completePendingCommand] that converts Apache Commons Net's
 * internal NPE — raised inside `FTP.getReply()` when `_controlInput_` has been nulled
 * because the control channel was closed underneath us (typically by the FTP server
 * hitting an idle / data timeout during a long transfer) — into a normal [IOException].
 *
 * Without this guard the NPE bubbles up as a noisy ~25-line stack trace even though
 * the data bytes themselves have already been read successfully. After the converted
 * IOException propagates, the next [FtpClient] call will see `isConnected == false`
 * and reconnect.
 *
 * Shared by [FtpConnectedOperations] (pooled / stateful) and [FtpStandaloneOperations]
 * (one-shot connections) — both call `completePendingCommand` after stream reads.
 */
@Throws(IOException::class)
internal fun safeCompletePendingCommand(client: FTPClient, label: String): Boolean {
    return try {
        client.completePendingCommand()
    } catch (npe: NullPointerException) {
        Timber.w(
            "FTP completePendingCommand: control channel closed by peer ($label) — " +
                "treating as IOException; client will reconnect on next call"
        )
        throw IOException("FTP control channel closed during $label")
    }
}
