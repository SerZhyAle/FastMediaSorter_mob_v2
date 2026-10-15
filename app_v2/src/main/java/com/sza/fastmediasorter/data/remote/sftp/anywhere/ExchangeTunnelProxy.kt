package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.jcraft.jsch.Proxy
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.domain.model.SftpTunnelAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * The consumer of contract DEVICE-EXCHANGE 5.4 and 7.5 as a JSch [Proxy]: the SSH session to a
 * tunnel address (see [SftpTunnelAddress]) runs over a stream to the exchange server.
 *
 * When an [ExchangeTunnelRouter] and [DeviceExchangeAccountClient] are present, routing resolves whether the
 * endpoint belongs to an attached own-account resource ([ExchangeTunnelRoute.OwnAccount]) or an outsider share
 * ([ExchangeTunnelRoute.Outsider]). Own-account connections authenticate with the account's device token
 * and pinned exchange certificate, opening a tunnel by `resourceId`. Outsider connections connect anonymously
 * by `shareId`.
 */
class ExchangeTunnelProxy(
    private val connector: ExchangeConnector,
    private val parts: SftpTunnelAddress.Parts,
    private val router: ExchangeTunnelRouter? = null,
    private val client: DeviceExchangeAccountClient? = null,
) : Proxy {

    private var connection: ExchangeConnection? = null

    override fun connect(socketFactory: SocketFactory?, host: String?, port: Int, timeout: Int) {
        val tunnelHost = host ?: SftpTunnelAddress.host(parts.shareId, parts.exchangeHost)
        val route = router?.route(tunnelHost, port)
        connection = when {
            route is ExchangeTunnelRoute.OwnAccount && client != null -> {
                runBlocking(Dispatchers.IO) {
                    client.connectByResourceId(route.entry, route.resourceId)
                }
            }
            route is ExchangeTunnelRoute.Outsider -> {
                DeviceExchangeAccountClient.connectByShareId(
                    connector = connector,
                    host = parts.exchangeHost,
                    port = port,
                    shareId = route.shareId,
                    trust = route.trust,
                )
            }
            else -> {
                DeviceExchangeAccountClient.connectByShareId(
                    connector = connector,
                    host = parts.exchangeHost,
                    port = port,
                    shareId = parts.shareId,
                    trust = ExchangeTrust.Verified(pinned = null),
                )
            }
        }
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
        fun attachIfTunnel(
            session: Session,
            host: String,
            connector: ExchangeConnector = TlsExchangeConnector(),
            router: ExchangeTunnelRouter? = null,
            client: DeviceExchangeAccountClient? = null,
        ) {
            SftpTunnelAddress.parse(host)?.let {
                session.setProxy(ExchangeTunnelProxy(connector, it, router = router, client = client))
            }
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
