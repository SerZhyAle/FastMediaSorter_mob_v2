package com.sza.fastmediasorter.wear.data.network.ftp

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply

/**
 * S1554 - one connect-and-login path shared by the listing and the connection test.
 *
 * Written once rather than twice deliberately: the test exists to predict whether browsing will
 * work, and it can only do that while both take the same route. Timeouts stay with the caller,
 * because the test bounds itself while a listing follows the client's own defaults.
 */
internal fun FTPClient.openFtpSession(source: NetworkSource) {
    // S1688: before connect, never after - the control-channel reader is built inside connect
    // from the encoding current at that moment.
    applyUtf8Encoding()
    connect(source.server, source.port)
    if (!FTPReply.isPositiveCompletion(replyCode)) {
        error("FTP server refused connection (code=$replyCode)")
    }
    if (!login(source.username, source.password)) {
        error("FTP login failed for ${source.username}@${source.server}")
    }
    // S1688: RFC 2640 servers decode names as UTF-8 only after this; older ones refuse it and
    // keep working on the pre-connect encoding alone.
    enableUtf8Mode()
    enterLocalPassiveMode()
}
