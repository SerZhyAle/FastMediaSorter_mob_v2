package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeCertificateFingerprint
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
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
     * the fingerprint the user accepted or a descriptor stated (7.7). A pin that differs is refused unless
     * [pinnedKey], the public-key fingerprint an entry keeps beside its leaf (19 item AY), equals the presented
     * leaf's key: that is a renewal of the same server. A descriptor's pin carries no key and never renews.
     */
    data class Verified(val pinned: String?, val pinnedKey: String? = null) : ExchangeTrust
}

/**
 * A connected stream with the fingerprints of the certificate the server presented on it: the leaf DER and its
 * public key ([keyFingerprint], null only where a test stands in for the TLS connector).
 */
class ExchangeConnection(
    val socket: Socket,
    val certificateFingerprint: String,
    val keyFingerprint: String? = null,
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

    /** [RENEWED] is accepted like [ACCEPT]; the caller that keeps the pin replaces its leaf with the presented one. */
    enum class Verdict { ACCEPT, RENEWED, UNTRUSTED, CHANGED }

    /**
     * A pinned leaf is never judged by the platform, so its validity dates never refuse it (19 item AY). Another
     * leaf passes only on a known key: a pin whose key was never learned cannot tell a renewal from a substitute.
     */
    fun decide(
        trust: ExchangeTrust,
        platformTrusted: Boolean,
        presented: String,
        presentedKey: String? = null,
    ): Verdict = when (trust) {
        is ExchangeTrust.Verified -> when {
            trust.pinned == null -> if (platformTrusted) Verdict.ACCEPT else Verdict.UNTRUSTED
            trust.pinned == presented -> Verdict.ACCEPT
            trust.pinnedKey != null && trust.pinnedKey == presentedKey -> Verdict.RENEWED
            else -> Verdict.CHANGED
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
            Timber.d("Exchange connect: %s:%d resolved=%s", host, port, address.address?.hostAddress)
            ExchangeTlsProtocols.restrict(socket)
            socket.connect(address, CONNECT_TIMEOUT_MS)
            Timber.d("Exchange connect: TCP established to %s:%d, starting TLS handshake", host, port)
            socket.startHandshake()
        } catch (e: IOException) {
            // The class and message are what tells a refused port, a timeout and a failed handshake apart.
            Timber.w(e, "Exchange connect failed before any envelope (%s:%d, %s)", host, port, e.javaClass.simpleName)
            socket.close()
            throw e
        }
        val presented = inspector.presented
        if (presented == null) {
            Timber.w("Exchange connect: handshake finished with no certificate (%s:%d)", host, port)
            socket.close()
            throw IOException("exchange server presented no certificate")
        }
        val platformTrusted = inspector.chainTrusted &&
            HttpsURLConnection.getDefaultHostnameVerifier().verify(host, socket.session)
        val presentedKey = inspector.presentedKey
        val verdict = ExchangeTrustDecision.decide(trust, platformTrusted, presented, presentedKey)
        Timber.d(
            "Exchange connect: TLS %s %s, presented=%s key=%s pinned=%s chainTrusted=%s platformTrusted=%s verdict=%s",
            socket.session.protocol,
            socket.session.cipherSuite,
            presented,
            presentedKey,
            (trust as? ExchangeTrust.Verified)?.pinned,
            inspector.chainTrusted,
            platformTrusted,
            verdict,
        )
        val accepted = verdict == ExchangeTrustDecision.Verdict.ACCEPT ||
            verdict == ExchangeTrustDecision.Verdict.RENEWED
        if (!accepted) socket.close()
        return when (verdict) {
            ExchangeTrustDecision.Verdict.ACCEPT,
            ExchangeTrustDecision.Verdict.RENEWED -> ExchangeConnection(socket, presented, presentedKey)
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
            // A self-signed or private-CA leaf fails here; the pin decides after the handshake.
            val platformCheck = platform ?: return
            chainTrusted = runCatching { platformCheck.checkServerTrusted(chain, authType) }.isSuccess
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private const val TLS = "TLS"
        private const val CONNECT_TIMEOUT_MS = 15_000

        /**
         * `SHA256:` plus the unpadded base64 digest of [der] - the leaf DER, or its public key's encoding for the
         * key half of a pin (19 item AY) - in the family's host-key text form. Encoded
         * without `java.util.Base64` so a descriptor's pin is checkable on the API 23 `legacy` build (S4160).
         */
        fun certificateFingerprint(der: ByteArray): String = ExchangeCertificateFingerprint.of(der)
    }
}
