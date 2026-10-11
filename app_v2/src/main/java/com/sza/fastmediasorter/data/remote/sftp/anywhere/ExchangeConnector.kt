package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Opens one stream to an exchange server - the control stream or one tunnel's data stream. */
interface ExchangeConnector {

    /**
     * Throws [ExchangeCertificateUntrustedException] or [ExchangeCertificateChangedException] when [trust]
     * refuses the certificate the server presented, and [IOException] when it cannot be reached.
     */
    fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection
}

/** How the server's certificate is judged (contract DEVICE-EXCHANGE 7.1 with amendment D). */
sealed interface ExchangeTrust {

    /**
     * The platform trust store first; only when it refuses the chain does the leaf have to equal [pinned],
     * the fingerprint the user accepted or a descriptor stated (7.7). A pin that differs is always refused.
     */
    data class Verified(val pinned: String?) : ExchangeTrust
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

/** The platform does not trust the server and nothing is pinned: the user must accept [presented] first. */
class ExchangeCertificateUntrustedException(val presented: String) :
    IOException("exchange server certificate is not trusted by the platform and not accepted")

/** The server presented [presented] where [pinned] was accepted; both are shown, never replaced silently. */
class ExchangeCertificateChangedException(val pinned: String, val presented: String) :
    IOException("exchange server certificate differs from the pinned one")

/** The decision of amendment D, apart from the socket so it can be judged without a TLS server. */
object ExchangeTrustDecision {

    enum class Verdict { ACCEPT, UNTRUSTED, CHANGED }

    fun decide(trust: ExchangeTrust, platformTrusted: Boolean, presented: String): Verdict = when (trust) {
        is ExchangeTrust.Verified -> when {
            trust.pinned != null && trust.pinned != presented -> Verdict.CHANGED
            trust.pinned != null || platformTrusted -> Verdict.ACCEPT
            else -> Verdict.UNTRUSTED
        }
    }
}

/**
 * TLS to the exchange server with the trust model of contract DEVICE-EXCHANGE amendment D: a CA-signed
 * deployment is trusted through the platform store and pinned nowhere, because its leaf renews on the CA's
 * schedule and a device holding no password could never re-enroll past a stale pin; a self-signed leaf is
 * trusted only by the fingerprint the user accepted.
 *
 * The handshake itself never fails on trust: the decision runs after it and before a single envelope is
 * written, so a refused server receives nothing but the handshake.
 */
@Singleton
class TlsExchangeConnector @Inject constructor() : ExchangeConnector {

    override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection {
        val inspector = InspectingTrustManager(platformTrustManager())
        val context = SSLContext.getInstance(TLS).apply { init(null, arrayOf(inspector), null) }
        val socket = context.socketFactory.createSocket() as SSLSocket
        try {
            val address = InetSocketAddress(host, port)
            // DIAGNOSTIC-REPORT section 10: a connect failure names the address in its message, by name and by IP.
            PrivateDiagnosticValues.register(host, address.address?.hostAddress)
            socket.connect(address, CONNECT_TIMEOUT_MS)
            socket.startHandshake()
        } catch (e: IOException) {
            socket.close()
            throw e
        }
        val presented = inspector.presented
        if (presented == null) {
            socket.close()
            throw IOException("exchange server presented no certificate")
        }
        val platformTrusted = inspector.chainTrusted &&
            HttpsURLConnection.getDefaultHostnameVerifier().verify(host, socket.session)
        val verdict = ExchangeTrustDecision.decide(trust, platformTrusted, presented)
        if (verdict != ExchangeTrustDecision.Verdict.ACCEPT) socket.close()
        return when (verdict) {
            ExchangeTrustDecision.Verdict.ACCEPT -> ExchangeConnection(socket, presented)
            ExchangeTrustDecision.Verdict.UNTRUSTED -> throw ExchangeCertificateUntrustedException(presented)
            ExchangeTrustDecision.Verdict.CHANGED -> throw ExchangeCertificateChangedException(
                pinned = (trust as? ExchangeTrust.Verified)?.pinned.orEmpty(),
                presented = presented,
            )
        }
    }

    private fun platformTrustManager(): X509TrustManager? {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
    }

    // Records what the platform thinks of the chain instead of failing the handshake on it.
    private class InspectingTrustManager(private val platform: X509TrustManager?) : X509TrustManager {
        @Volatile
        var presented: String? = null

        @Volatile
        var chainTrusted: Boolean = false

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("the exchange connector never accepts client connections")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("server presented no certificate")
            presented = certificateFingerprint(leaf.encoded)
            // A self-signed or private-CA leaf fails here; the pin decides after the handshake.
            val platformCheck = platform ?: return
            chainTrusted = runCatching { platformCheck.checkServerTrusted(chain, authType) }.isSuccess
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
