package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTlsProtocols
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * The inner TLS of a sealed broadcast tunnel (contract DEVICE-EXCHANGE 27 item DM): the producer answers every
 * `TUNNEL` stream as a TLS server on the broadcast's own leaf, and the consumer accepts exactly the leaf whose pin
 * the record's box or the outward descriptor carries - no name and no chain check, so the operator, who carries
 * the stream, can read nothing.
 *
 * Each side runs a plain TLS socket against a one-shot loopback hop whose other end is spliced to the tunnel
 * stream. A TLS socket layered over the exchange server's own TLS socket would, on older platforms, write to the
 * shared file descriptor below the outer TLS; the hop keeps the two sessions on separate sockets.
 */
object BroadcastTunnelTls {

    private const val BUFFER_SIZE = 16 * 1024
    private const val HANDSHAKE_TIMEOUT_MS = 15_000
    private const val TLS = "TLS"

    /** The `SHA256:` pin of [leaf], the form `tunnelPin` and `seal.pin` carry (7.1). */
    fun pinOf(leaf: X509Certificate): String = ExchangeCertificateFingerprint.of(leaf.encoded)

    /**
     * The producer's side: the clear bytes of a TLS server session run with [context] over the tunnel stream
     * [outerInput]/[outerOutput]. Closing the returned socket ends [outer] as well, and the reverse.
     */
    @Throws(IOException::class)
    fun serve(context: SSLContext, outerInput: InputStream, outerOutput: OutputStream, outer: Closeable): Socket {
        val hop = context.serverSocketFactory.createServerSocket(0, 1, loopback()) as SSLServerSocket
        return hop.use { server ->
            server.enabledProtocols = ExchangeTlsProtocols.allowed(server.supportedProtocols)
            val raw = Socket(loopback(), server.localPort)
            val tls = server.accept()
            splice(outerInput, outerOutput, outer, raw)
            tls
        }
    }

    /**
     * The consumer's side: a TLS client session over the tunnel stream that accepts only the leaf pinned by [pin].
     * A different leaf fails the handshake here, and the caller ends the connection (`sealed-failed`).
     */
    @Throws(IOException::class)
    fun connect(pin: String, outerInput: InputStream, outerOutput: OutputStream, outer: Closeable): SSLSocket {
        val context = SSLContext.getInstance(TLS).apply { init(null, arrayOf(PinnedLeafTrustManager(pin)), null) }
        val hop = ServerSocket(0, 1, loopback())
        val tls = hop.use { server ->
            val client = context.socketFactory.createSocket(loopback(), server.localPort) as SSLSocket
            splice(outerInput, outerOutput, outer, server.accept())
            client
        }
        try {
            ExchangeTlsProtocols.restrict(tls)
            tls.soTimeout = HANDSHAKE_TIMEOUT_MS
            tls.startHandshake()
            tls.soTimeout = 0
        } catch (e: IOException) {
            Timber.w("BroadcastTunnelTls: the tunnel leaf was refused, code=sealed-failed (%s)", e.javaClass.simpleName)
            closeQuietly(tls)
            throw e
        }
        return tls
    }

    private fun loopback(): InetAddress = InetAddress.getByName(BroadcastExchangeEndpoints.LOOPBACK_HOST)

    // Two daemon pumps; either direction ending closes both ends, which unblocks the opposite pump.
    private fun splice(outerInput: InputStream, outerOutput: OutputStream, outer: Closeable, hop: Socket) {
        pumpThread(outerInput, hop.getOutputStream(), outer, hop)
        pumpThread(hop.getInputStream(), outerOutput, outer, hop)
    }

    private fun pumpThread(from: InputStream, to: OutputStream, first: Closeable, second: Closeable) {
        Thread({
            val buffer = ByteArray(BUFFER_SIZE)
            try {
                var read = from.read(buffer)
                while (read >= 0) {
                    to.write(buffer, 0, read)
                    to.flush()
                    read = from.read(buffer)
                }
            } catch (e: IOException) {
                Timber.v("BroadcastTunnelTls: hop closed - %s", e.javaClass.simpleName)
            } finally {
                closeQuietly(first)
                closeQuietly(second)
            }
        }, "fms-tunnel-tls-hop").apply { isDaemon = true }.start()
    }

    private fun closeQuietly(stream: Closeable) {
        try {
            stream.close()
        } catch (e: IOException) {
            Timber.v("BroadcastTunnelTls: close failed - %s", e.javaClass.simpleName)
        }
    }

    /** Accepts exactly the leaf whose pin is [pin]; nothing about its name, issuer or validity is checked. */
    internal class PinnedLeafTrustManager(private val pin: String) : X509TrustManager {

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("a tunnel client never accepts client connections")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("the tunnel presented no certificate")
            if (pinOf(leaf) != pin) throw CertificateException("the tunnel leaf is not the pinned one")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
