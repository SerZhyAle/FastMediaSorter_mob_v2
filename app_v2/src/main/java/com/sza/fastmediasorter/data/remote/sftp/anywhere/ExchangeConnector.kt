package com.sza.fastmediasorter.data.remote.sftp.anywhere

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** Opens one stream to an exchange server - the control stream or one tunnel's data stream. */
interface ExchangeConnector {

    /**
     * Throws [ExchangeCertificateChangedException] when the server presents a certificate other than
     * [pinnedCertificate], and [IOException] when it cannot be reached.
     */
    fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection
}

/** A connected stream and the fingerprint of the certificate the server presented on it. */
class ExchangeConnection(
    val socket: Socket,
    val certificateFingerprint: String,
) : Closeable {
    val input: InputStream get() = socket.getInputStream()
    val output: OutputStream get() = socket.getOutputStream()

    override fun close() = socket.close()
}

class ExchangeCertificateChangedException : IOException("exchange server certificate differs from the pinned one")

/**
 * TLS to the exchange server with the trust model of contract ANYWHERE-ACCESS section 6.1: a home
 * deployment serves a self-signed certificate, so the leaf certificate's fingerprint is pinned on the
 * first registration and every later connection must present the same one - exactly how an SSH host
 * key is trusted. A CA chain buys nothing over the pin, so it is not consulted.
 */
@Singleton
class TlsExchangeConnector @Inject constructor() : ExchangeConnector {

    override fun connect(host: String, port: Int, pinnedCertificate: String?): ExchangeConnection {
        val trustManager = PinningTrustManager(pinnedCertificate)
        val context = SSLContext.getInstance(TLS).apply { init(null, arrayOf(trustManager), null) }
        val socket = context.socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.startHandshake()
        } catch (e: IOException) {
            socket.close()
            // The pin refusal surfaces as an SSLException; only the trust manager knows it was the pin.
            throw if (trustManager.changed) ExchangeCertificateChangedException() else e
        }
        return ExchangeConnection(socket, checkNotNull(trustManager.presented))
    }

    private class PinningTrustManager(private val pinned: String?) : X509TrustManager {
        @Volatile
        var presented: String? = null

        @Volatile
        var changed: Boolean = false

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("the exchange connector never accepts client connections")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("server presented no certificate")
            val fingerprint = certificateFingerprint(leaf.encoded)
            presented = fingerprint
            if (pinned != null && pinned != fingerprint) {
                changed = true
                throw CertificateException("server certificate differs from the pinned one")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private const val TLS = "TLS"
        private const val CONNECT_TIMEOUT_MS = 15_000

        /** `SHA256:` plus the unpadded base64 digest of the leaf DER - the family's host-key text form. */
        fun certificateFingerprint(der: ByteArray): String =
            "SHA256:" + Base64.getEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(der))
    }
}
