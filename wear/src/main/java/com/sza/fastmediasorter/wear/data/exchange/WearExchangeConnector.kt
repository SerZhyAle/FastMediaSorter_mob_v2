package com.sza.fastmediasorter.wear.data.exchange

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

/** S4158: opens one TLS stream to an exchange server, judged by the trust decision the phone entry carried. */
interface WearExchangeConnector {

    /**
     * [pinned] is the leaf the phone's user accepted, null where the platform trusts the server.
     *
     * @throws WearExchangeCertificateUntrustedException when the platform refuses and nothing is pinned.
     * @throws WearExchangeCertificateChangedException when the server presented another leaf than [pinned].
     * @throws IOException when the server cannot be reached.
     */
    fun connect(host: String, port: Int, pinned: String?): WearExchangeConnection
}

class WearExchangeConnection(val socket: Socket) : Closeable {
    val input: InputStream get() = socket.getInputStream()
    val output: OutputStream get() = socket.getOutputStream()

    override fun close() = socket.close()
}

/** The platform does not trust the server and the phone entry pinned nothing. */
class WearExchangeCertificateUntrustedException : IOException("exchange server certificate is not trusted")

/** The server presented a leaf other than the one the phone entry pinned; never replaced on the watch. */
class WearExchangeCertificateChangedException : IOException("exchange server certificate differs from the pin")

/** Contract DEVICE-EXCHANGE amendment D, apart from the socket so it can be judged without a TLS server. */
object WearExchangeTrustDecision {

    enum class Verdict { ACCEPT, UNTRUSTED, CHANGED }

    fun decide(pinned: String?, platformTrusted: Boolean, presented: String): Verdict = when {
        pinned != null && pinned != presented -> Verdict.CHANGED
        pinned != null || platformTrusted -> Verdict.ACCEPT
        else -> Verdict.UNTRUSTED
    }
}

/**
 * TLS with the trust model of amendment D, mirrored from the phone's `TlsExchangeConnector`: the handshake never
 * fails on trust, the decision runs after it and before a single envelope is written, so a refused server
 * receives nothing but the handshake - and never the pairing code.
 */
@Singleton
class TlsWearExchangeConnector @Inject constructor() : WearExchangeConnector {

    override fun connect(host: String, port: Int, pinned: String?): WearExchangeConnection {
        val inspector = InspectingTrustManager(platformTrustManager())
        val context = SSLContext.getInstance(TLS).apply { init(null, arrayOf(inspector), null) }
        val socket = context.socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            socket.startHandshake()
        } catch (e: IOException) {
            socket.close()
            throw e
        }
        val presented = inspector.presented
        val platformTrusted = inspector.chainTrusted &&
            HttpsURLConnection.getDefaultHostnameVerifier().verify(host, socket.session)
        val verdict = presented?.let { WearExchangeTrustDecision.decide(pinned, platformTrusted, it) }
        if (verdict != WearExchangeTrustDecision.Verdict.ACCEPT) socket.close()
        return when (verdict) {
            WearExchangeTrustDecision.Verdict.ACCEPT -> WearExchangeConnection(socket)
            WearExchangeTrustDecision.Verdict.UNTRUSTED -> throw WearExchangeCertificateUntrustedException()
            WearExchangeTrustDecision.Verdict.CHANGED -> throw WearExchangeCertificateChangedException()
            null -> throw IOException("exchange server presented no certificate")
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
            val platformCheck = platform ?: return
            chainTrusted = runCatching { platformCheck.checkServerTrusted(chain, authType) }.isSuccess
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private const val TLS = "TLS"
        private const val CONNECT_TIMEOUT_MS = 15_000

        // Enrollment is one question and one answer; a server silent this long is treated as unreachable.
        private const val READ_TIMEOUT_MS = 20_000

        /** `SHA256:` plus the unpadded base64 digest of the leaf DER - the form the phone entry pins. */
        fun certificateFingerprint(der: ByteArray): String =
            "SHA256:" + Base64.getEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(der))
    }
}
