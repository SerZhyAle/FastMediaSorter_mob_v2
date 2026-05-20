@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.data.remote.ftp

import org.apache.commons.net.ftp.FTPClient
import timber.log.Timber

/**
 * UTF-8 negotiation helpers for [FTPClient].
 *
 * Apache Commons Net initialises the control-channel `InputStreamReader` /
 * `OutputStreamWriter` inside `_connectAction_()` using the *current* value of
 * `getControlEncoding()`. Setting `controlEncoding` AFTER `connect()` therefore
 * has no effect on the already-created readers/writers - the encoding must be
 * applied before [FTPClient.connect]. See [applyUtf8Encoding].
 *
 * RFC 2640 servers (Microsoft IIS FTP, FileZilla Server on Windows, recent
 * vsftpd builds) require an explicit `OPTS UTF8 ON` after login before they
 * decode filename bytes as UTF-8 - otherwise they interpret them as the
 * system OEM/ANSI codepage and respond with `550 The filename, directory name,
 * or volume label syntax is incorrect` for any non-ASCII filename. See
 * [enableUtf8Mode]. Legacy servers without RFC 2640 support reply with
 * 500/502; the helper logs and returns - non-fatal.
 */

/**
 * Set UTF-8 as the control-connection encoding. MUST be called before
 * [FTPClient.connect] - Apache Commons Net captures the encoding when it
 * creates the control-channel readers inside `_connectAction_()`, and later
 * changes have no effect.
 */
internal fun FTPClient.applyUtf8Encoding() {
    controlEncoding = "UTF-8"
}

/**
 * Send `OPTS UTF8 ON` to negotiate UTF-8 filename interpretation on RFC 2640
 * servers. Best-effort: legacy servers reply 500/502 and the connection
 * continues to work without explicit negotiation (the pre-connect encoding
 * fix alone is sufficient for servers that already default to UTF-8).
 *
 * Must be called AFTER successful login.
 */
internal fun FTPClient.enableUtf8Mode() {
    try {
        val replyCode = sendCommand("OPTS", "UTF8 ON")
        Timber.d("FTP: OPTS UTF8 ON reply=$replyCode")
    } catch (e: Exception) {
        Timber.w(e, "FTP: OPTS UTF8 ON failed (non-critical)")
    }
}
