package com.sza.fastmediasorter.wear.data.exchange

import android.net.Network
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
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** S4158: opens one TLS stream to an exchange server, judged by the trust decision the phone entry carried. */
interface WearExchangeConnector {

    /**
     * [pinned] is the leaf the phone's user accepted, null where the platform trusts the server; [pinnedKey] is its
     * public-key fingerprint, which lets a renewed leaf with the same key pass (19 item AY) - an entry's own pin
     * only, a descriptor's pin carries none. [readTimeoutMs] is [NO_READ_TIMEOUT] for a control stream, which is
     * silent between keepalives and judged by their window. [network] binds the socket to one network (S4227: the
     * Wi-Fi or LTE of the watch, never the Bluetooth proxy); null keeps whatever route the platform picks.
     *
     * @throws WearExchangeCertificateUntrustedException when the platform refuses and nothing is pinned.
     * @throws WearExchangeCertificateChangedException when the server presented another leaf than [pinned] whose
     * key is not [pinnedKey].
     * @throws IOException when the server cannot be reached.
     */
    fun connect(
        host: String,
        port: Int,
        pinned: String?,
        pinnedKey: String? = null,
        readTimeoutMs: Int = ENROLL_READ_TIMEOUT_MS,
        network: Network? = null,
    ): WearExchangeConnection

    companion object {
        // Enrollment is one question and one answer; a server silent this long is treated as unreachable.
        const val ENROLL_READ_TIMEOUT_MS = 20_000
        const val NO_READ_TIMEOUT = 0
    }
}

/**
 * A connected stream with the fingerprints of the leaf the server presented and of its public key; both null only
 * where a test stands in for the TLS connector.
 */
class WearExchangeConnection(
    val socket: Socket,
    val certificateFingerprint: String? = null,
    val keyFingerprint: String? = null,
) : Closeable {
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

    /** [RENEWED] is accepted like [ACCEPT]; the stored entry then keeps the presented leaf as its pin. */
    enum class Verdict { ACCEPT, RENEWED, UNTRUSTED, CHANGED }

    /**
     * The phone's rule (19 item AY): a pinned leaf is never judged by the platform, so its dates never refuse it,
     * and another leaf passes only on a known key - a pin whose key was never learned cannot tell a renewal from a
     * substitute.
     */
    fun decide(
        pinned: String?,
        platformTrusted: Boolean,
        presented: String,
        pinnedKey: String? = null,
        presentedKey: String? = null,
    ): Verdict = when {
        pinned == null -> if (platformTrusted) Verdict.ACCEPT else Verdict.UNTRUSTED
        pinned == presented -> Verdict.ACCEPT
        pinnedKey != null && pinnedKey == presentedKey -> Verdict.RENEWED
        else -> Verdict.CHANGED
    }
}

/**
 * TLS with the trust model of amendment D, mirrored from the phone's `TlsExchangeConnector`: the handshake never
 * fails on trust, the decision runs after it and before a single envelope is written, so a refused server
 * receives nothing but the handshake - and never the pairing code.
 */
@Singleton
class TlsWearExchangeConnector @Inject constructor() : WearExchangeConnector {

    override fun connect(
        host: String,
        port: Int,
        pinned: String?,
        pinnedKey: String?,
        readTimeoutMs: Int,
        network: Network?,
    ): WearExchangeConnection {
        val inspector = InspectingTrustManager(platformTrustManager())
        val context = SSLContext.getInstance(TLS).apply { init(null, arrayOf(inspector), null) }
        val socket = context.socketFactory.createSocket() as SSLSocket
        try {
            restrictProtocols(socket)
            network?.bindSocket(socket)
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            // The handshake is bounded even for a control stream, whose own reads wait for the keepalive window.
            socket.soTimeout = WearExchangeConnector.ENROLL_READ_TIMEOUT_MS
            socket.startHandshake()
            socket.soTimeout = readTimeoutMs
        } catch (e: IOException) {
            socket.close()
            throw e
        }
        val presented = inspector.presented
        val presentedKey = inspector.presentedKey
        val platformTrusted = inspector.chainTrusted &&
            HttpsURLConnection.getDefaultHostnameVerifier().verify(host, socket.session)
        val verdict = presented?.let {
            WearExchangeTrustDecision.decide(pinned, platformTrusted, it, pinnedKey, presentedKey)
        }
        val accepted = verdict == WearExchangeTrustDecision.Verdict.ACCEPT ||
            verdict == WearExchangeTrustDecision.Verdict.RENEWED
        if (!accepted) socket.close()
        return when (verdict) {
            WearExchangeTrustDecision.Verdict.ACCEPT,
            WearExchangeTrustDecision.Verdict.RENEWED -> WearExchangeConnection(socket, presented, presentedKey)
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

    // 19 item AX, as the phone's `ExchangeTlsProtocols`: a platform offering neither is refused, never downgraded.
    private fun restrictProtocols(socket: SSLSocket) {
        val enabled = allowedProtocols(socket.supportedProtocols)
        if (enabled.isEmpty()) throw SSLHandshakeException("neither TLSv1.3 nor TLSv1.2 is supported")
        socket.enabledProtocols = enabled
    }

    // Records what the platform thinks of the chain instead of failing the handshake on it.
    private class InspectingTrustManager(private val platform: X509TrustManager?) : X509TrustManager {
        @Volatile
        var presented: String? = null

        @Volatile
        var presentedKey: String? = null

        @Volatile
        var chainTrusted: Boolean = false

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("the exchange connector never accepts client connections")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("server presented no certificate")
            presented = certificateFingerprint(leaf.encoded)
            presentedKey = certificateFingerprint(leaf.publicKey.encoded)
            val platformCheck = platform ?: return
            chainTrusted = runCatching { platformCheck.checkServerTrusted(chain, authType) }.isSuccess
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private const val TLS = "TLS"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private val ALLOWED_PROTOCOLS = listOf("TLSv1.3", "TLSv1.2")

        /** The allowed protocols (TLS 1.3, then 1.2) that [supported] offers. */
        fun allowedProtocols(supported: Array<String>): Array<String> =
            ALLOWED_PROTOCOLS.filter { it in supported }.toTypedArray()

        /**
         * `SHA256:` plus the unpadded base64 digest of [der] - the leaf DER, or its public key's encoding for the
         * key half of a pin - the form the phone entry pins.
         */
        fun certificateFingerprint(der: ByteArray): String =
            "SHA256:" + Base64.getEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(der))
    }
}
