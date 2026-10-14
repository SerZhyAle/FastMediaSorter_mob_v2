package com.sza.fastmediasorter.wear.data.network.sftp

import com.jcraft.jsch.Proxy
import com.jcraft.jsch.SocketFactory
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeConnection
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * S4228: the SSH session to an exchange folder carried over a tunnel of the watch's own account (contract
 * DEVICE-EXCHANGE 7.5) as a JSch [Proxy]. The host-key pin, authentication and the root proof all run above it
 * unchanged, so the tunnel is just one more path to the same server (20 item BB).
 *
 * The tunnel is opened before the session connects, from a coroutine that can bound it by the tunnel window,
 * so [connect] only hands the open stream to JSch instead of blocking its thread on the network a second way.
 */
internal class WearExchangeTunnelProxy(private var connection: WearExchangeConnection?) : Proxy {

    override fun connect(socketFactory: SocketFactory?, host: String?, port: Int, timeout: Int) {
        checkNotNull(connection) { "tunnel already closed" }
    }

    override fun getInputStream(): InputStream = checkNotNull(connection) { "tunnel not connected" }.input

    override fun getOutputStream(): OutputStream = checkNotNull(connection) { "tunnel not connected" }.output

    override fun getSocket(): Socket? = connection?.socket

    override fun close() {
        connection?.close()
        connection = null
    }
}
