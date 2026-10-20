package com.sza.fastmediasorter.data.remote.sftp.anywhere

import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket

/**
 * The TLS floor of contract DEVICE-EXCHANGE 19 item AX: TLS 1.2 at least, TLS 1.3 preferred, on every exchange
 * client socket. On API 23-28 the platform default still enables TLS 1.0 and 1.1, so a socket left alone could be
 * talked down to them.
 */
object ExchangeTlsProtocols {

    private val ALLOWED = listOf("TLSv1.3", "TLSv1.2")

    /** The allowed protocols [supported] offers, most preferred first. */
    fun allowed(supported: Array<String>): Array<String> = ALLOWED.filter { it in supported }.toTypedArray()

    /**
     * Enables only the allowed protocols on [socket]. A platform that offers neither is refused before any byte is
     * sent rather than falling back to an older protocol.
     */
    @Throws(SSLHandshakeException::class)
    fun restrict(socket: SSLSocket) {
        val enabled = allowed(socket.supportedProtocols)
        if (enabled.isEmpty()) throw SSLHandshakeException("neither TLSv1.3 nor TLSv1.2 is supported")
        socket.enabledProtocols = enabled
    }
}
