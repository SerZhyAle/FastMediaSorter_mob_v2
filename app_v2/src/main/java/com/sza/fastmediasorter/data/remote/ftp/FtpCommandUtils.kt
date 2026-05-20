package com.sza.fastmediasorter.data.remote.ftp

import org.apache.commons.net.ftp.FTPClient
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * S0206 Phase 01: read-loop buffer size for bounded FTP reads.
 * Picked as the smallest power-of-two that keeps syscall count low while limiting
 * overrun past the requested cap to at most one buffer.
 */
internal const val FTP_BOUNDED_READ_BUFFER_BYTES = 8 * 1024

/**
 * S0206 Phase 01: result of a single bounded FTP read.
 *
 * @property bytes The bytes that were actually read; never larger than the caller-requested cap.
 * @property abortInvoked `true` if the byte cap was reached and `client.abort()` was sent; `false` on natural EOF before cap.
 * @property completeOk `true` if `completePendingCommand` returned a positive completion code; `false` if it threw IO or returned non-positive after a deliberate ABOR. Diagnostic only - callers treat the returned bytes as success regardless.
 */
internal data class FtpBoundedReadResult(
    val bytes: ByteArray,
    val abortInvoked: Boolean,
    val completeOk: Boolean,
)

/**
 * Wrapper around [FTPClient.completePendingCommand] that converts Apache Commons Net's
 * internal NPE - raised inside `FTP.getReply()` when `_controlInput_` has been nulled
 * because the control channel was closed underneath us (typically by the FTP server
 * hitting an idle / data timeout during a long transfer) - into a normal [IOException].
 *
 * Without this guard the NPE bubbles up as a noisy ~25-line stack trace even though
 * the data bytes themselves have already been read successfully. After the converted
 * IOException propagates, the next [FtpClient] call will see `isConnected == false`
 * and reconnect.
 *
 * Shared by [FtpConnectedOperations] (pooled / stateful) and [FtpStandaloneOperations]
 * (one-shot connections) - both call `completePendingCommand` after stream reads.
 */
@Throws(IOException::class)
internal fun safeCompletePendingCommand(client: FTPClient, label: String): Boolean {
    return try {
        client.completePendingCommand()
    } catch (npe: NullPointerException) {
        Timber.w(
            "FTP completePendingCommand: control channel closed by peer ($label) - " +
                "treating as IOException; client will reconnect on next call"
        )
        throw IOException("FTP control channel closed during $label")
    }
}

/**
 * S0206 Phase 01: read up to [maxBytes] from a data-stream opened by
 * `FTPClient.retrieveFileStream`, then cleanly terminate the FTP transfer.
 *
 * The canonical FTP protocol sequence after a deliberate early-stop is:
 *
 *  1. Read what is needed from the data connection.
 *  2. `ABOR` (client → server) - tells the server to stop pushing further bytes.
 *  3. `completePendingCommand` - consumes the queued `426 Connection closed; transfer aborted`
 *     and `226/250` follow-up that Commons Net otherwise leaves in the control channel
 *     and trips up the next command.
 *
 * Some servers reply with a non-positive completion code after a deliberate ABOR, or
 * close the control channel mid-handshake. Because the **data** has already been
 * collected by the time we abort, such control-channel oddities are NOT a failure
 * of the read - we report them via [FtpBoundedReadResult.completeOk] but always
 * return the bytes. Read-loop `IOException` propagates to the caller unchanged.
 *
 * Buffer size is fixed at [FTP_BOUNDED_READ_BUFFER_BYTES] - overrun past the cap is
 * bounded to a single buffer (the read loop truncates the last `write` to exactly
 * [maxBytes]).
 */
@Throws(IOException::class)
internal fun readBoundedAndAbort(
    client: FTPClient,
    stream: InputStream,
    maxBytes: Int,
    label: String,
): FtpBoundedReadResult {
    require(maxBytes > 0) { "readBoundedAndAbort: maxBytes must be > 0, got $maxBytes" }
    val baos = ByteArrayOutputStream(minOf(maxBytes, FTP_BOUNDED_READ_BUFFER_BYTES))
    val buf = ByteArray(FTP_BOUNDED_READ_BUFFER_BYTES)
    var total = 0
    var reachedCap = false
    while (total < maxBytes) {
        val remaining = maxBytes - total
        val toRead = if (remaining < buf.size) remaining else buf.size
        val n = stream.read(buf, 0, toRead)
        if (n == -1) {
            // Natural EOF before the cap was reached - server already finished.
            break
        }
        baos.write(buf, 0, n)
        total += n
        if (total >= maxBytes) {
            reachedCap = true
            break
        }
    }

    val abortInvoked = reachedCap
    var completeOk = false
    if (abortInvoked) {
        try {
            client.abort()
        } catch (e: IOException) {
            Timber.w(
                e,
                "FTP ABOR ($label) failed; bytes already collected (%d), continuing",
                total,
            )
        }
    }
    completeOk = try {
        safeCompletePendingCommand(client, label)
    } catch (e: IOException) {
        Timber.w(
            "FTP completePendingCommand after %s ($label): control channel already closed by peer; bytes preserved",
            if (abortInvoked) "ABOR" else "EOF",
        )
        false
    }

    return FtpBoundedReadResult(
        bytes = baos.toByteArray(),
        abortInvoked = abortInvoked,
        completeOk = completeOk,
    )
}
