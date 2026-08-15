package com.sza.fastmediasorter.wear.data.network.ftp

import org.apache.commons.net.ftp.FTPClient
import timber.log.Timber
import java.io.IOException

/**
 * UTF-8 negotiation helpers for [FTPClient] in the wear module.
 *
 * S1688: the phone module solved this in S0212 and the two modules share no code, so the technique
 * is carried over rather than imported. Without it the client keeps the library's default
 * single-byte control encoding and a Cyrillic file name arrives decoded as latin-1 - readable as
 * mojibake on the watch, which is the only place a track name can be told apart.
 */

/**
 * Applies UTF-8 to the control channel. **Must run before [FTPClient.connect]**: Apache Commons Net
 * builds the control-channel reader and writer inside its connect action using the encoding value
 * current at that moment, so a later assignment never reaches them. Setting this one line in the
 * wrong place looks exactly like a fix and changes nothing.
 */
internal fun FTPClient.applyUtf8Encoding() {
    controlEncoding = "UTF-8"
}

/**
 * Negotiates UTF-8 file names with RFC 2640 servers. **Must run after a successful login.**
 *
 * Best-effort by design: a legacy server answers with a refusal and the connection keeps working,
 * because for a server that already defaults to UTF-8 the pre-connect encoding alone is enough.
 */
internal fun FTPClient.enableUtf8Mode() {
    try {
        val replyCode = sendCommand("OPTS", "UTF8 ON")
        Timber.d("FTP: OPTS UTF8 ON reply=$replyCode")
    } catch (e: IOException) {
        Timber.w(e, "FTP: OPTS UTF8 ON failed (non-critical)")
    }
}
