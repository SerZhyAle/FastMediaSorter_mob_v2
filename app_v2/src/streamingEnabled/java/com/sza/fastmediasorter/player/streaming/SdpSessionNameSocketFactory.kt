package com.sza.fastmediasorter.player.streaming

import timber.log.Timber
import java.io.FilterInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/**
 * Preserves the RFC 8866 fallback session name before Media3 trims SDP lines.
 *
 * RFC 8866 recommends `s= ` for a session without a meaningful name. Media3 trims each SDP line
 * before parsing, turning that valid line into invalid `s=`. The replacement keeps the byte count
 * unchanged, which preserves the RTSP response's Content-Length framing.
 */
internal class SdpSessionNameSocketFactory(
    private val delegate: SocketFactory = SocketFactory.getDefault(),
) : SocketFactory() {

    override fun createSocket(): Socket = SdpSessionNameSocket(delegate.createSocket())

    override fun createSocket(host: String, port: Int): Socket =
        SdpSessionNameSocket(delegate.createSocket(host, port))

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        SdpSessionNameSocket(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress, port: Int): Socket =
        SdpSessionNameSocket(delegate.createSocket(host, port))

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket = SdpSessionNameSocket(delegate.createSocket(address, port, localAddress, localPort))
}

private class SdpSessionNameSocket(
    private val delegate: Socket,
) : Socket() {

    private val normalizedInputStream by lazy { SdpSessionNameNormalizingInputStream(delegate.inputStream) }

    override fun bind(bindpoint: SocketAddress) = delegate.bind(bindpoint)

    override fun close() = delegate.close()

    override fun connect(endpoint: SocketAddress) = delegate.connect(endpoint)

    override fun connect(endpoint: SocketAddress, timeout: Int) = delegate.connect(endpoint, timeout)

    override fun getInetAddress(): InetAddress = delegate.inetAddress

    override fun getInputStream(): InputStream = normalizedInputStream

    override fun getLocalAddress(): InetAddress = delegate.localAddress

    override fun getLocalPort(): Int = delegate.localPort

    override fun getOutputStream() = delegate.outputStream

    override fun getPort(): Int = delegate.port

    override fun getSoTimeout(): Int = delegate.soTimeout

    override fun isBound(): Boolean = delegate.isBound

    override fun isClosed(): Boolean = delegate.isClosed

    override fun isConnected(): Boolean = delegate.isConnected

    override fun isInputShutdown(): Boolean = delegate.isInputShutdown

    override fun isOutputShutdown(): Boolean = delegate.isOutputShutdown

    override fun setSoTimeout(timeout: Int) {
        delegate.soTimeout = timeout
    }

    override fun shutdownInput() = delegate.shutdownInput()

    override fun shutdownOutput() = delegate.shutdownOutput()
}

internal class SdpSessionNameNormalizingInputStream(
    source: InputStream,
) : FilterInputStream(source) {

    private var lineState = LINE_START
    private var sessionNameNormalized = false

    override fun read(): Int {
        val value = super.read()
        return if (value == END_OF_STREAM) value else normalize(value)
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(bytes, offset, length)
        if (count <= 0) return count
        for (index in offset until offset + count) {
            bytes[index] = normalize(bytes[index].toInt() and BYTE_MASK).toByte()
        }
        return count
    }

    private fun normalize(value: Int): Int = when {
        value == CARRIAGE_RETURN || value == LINE_FEED -> {
            lineState = LINE_START
            value
        }
        lineState == LINE_START && value == SESSION_TAG -> {
            lineState = SESSION_TAG_SEEN
            value
        }
        lineState == SESSION_TAG_SEEN && value == EQUALS -> {
            lineState = SESSION_VALUE_START
            value
        }
        lineState == SESSION_VALUE_START && value == SPACE && !sessionNameNormalized -> {
            lineState = OTHER
            sessionNameNormalized = true
            DASH
        }
        else -> {
            lineState = OTHER
            value
        }
    }

    private companion object {
        const val END_OF_STREAM = -1
        const val BYTE_MASK = 0xFF
        const val CARRIAGE_RETURN = '\r'.code
        const val DASH = '-'.code
        const val EQUALS = '='.code
        const val LINE_FEED = '\n'.code
        const val SESSION_TAG = 's'.code
        const val SPACE = ' '.code
        const val LINE_START = 0
        const val SESSION_TAG_SEEN = 1
        const val SESSION_VALUE_START = 2
        const val OTHER = 3
    }
}
