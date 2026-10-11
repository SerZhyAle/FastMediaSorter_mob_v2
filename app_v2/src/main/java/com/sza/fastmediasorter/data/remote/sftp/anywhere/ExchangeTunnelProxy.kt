package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.jcraft.jsch.Proxy
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.domain.model.SftpTunnelAddress
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * The outsider consumer of contract DEVICE-EXCHANGE 5.4 and 7.5 as a JSch [Proxy]: the SSH session to a
 * tunnel address (see [SftpTunnelAddress]) runs over a stream to the exchange server that asked for the
 * share by its id and was handed over to raw bytes. Everything above the stream - the host-key pin,
 * authentication, SFTP - is unchanged, so the tunnel is just one more path to the same server.
 *
 * A pairing code carries no certificate fingerprint for its exchange server, so the server must pass the
 * platform trust store (contract DEVICE-EXCHANGE section 15, item V): a self-signed deployment is refused
 * here until its fingerprint reaches the device by a connection string or an accepted prompt.
 */
class ExchangeTunnelProxy(
    private val connector: ExchangeConnector,
    private val parts: SftpTunnelAddress.Parts,
) : Proxy {

    private var connection: ExchangeConnection? = null

    override fun connect(socketFactory: SocketFactory?, host: String?, port: Int, timeout: Int) {
        Timber.d("S4094: consumer tunnel connect")
        connection = DeviceExchangeAccountClient.connectByShareId(
            connector = connector,
            host = parts.exchangeHost,
            port = port,
            shareId = parts.shareId,
            trust = ExchangeTrust.Verified(pinned = null),
        )
    }

    override fun getInputStream(): InputStream = checkNotNull(connection) { "tunnel not connected" }.input

    override fun getOutputStream(): OutputStream = checkNotNull(connection) { "tunnel not connected" }.output

    override fun getSocket(): Socket? = connection?.socket

    override fun close() {
        connection?.close()
        connection = null
    }

    companion object {
        /** Routes [session] through the exchange server when [host] is a tunnel address; other hosts stay direct. */
        fun attachIfTunnel(session: Session, host: String, connector: ExchangeConnector = TlsExchangeConnector()) {
            SftpTunnelAddress.parse(host)?.let { session.setProxy(ExchangeTunnelProxy(connector, it)) }
        }
    }
}

/**
 * The exchange server has no live registration for the share (unknown, ended or full - contract DEVICE-EXCHANGE
 * 7.5 answers all three alike), or it closed the stream before answering. Kept apart from transport,
 * authentication and host-key failures (`SHARE-SESSION` rule 7).
 */
class ExchangeTunnelUnavailableException(reason: String?) :
    IOException("exchange tunnel unavailable: ${reason ?: "no answer"}")
