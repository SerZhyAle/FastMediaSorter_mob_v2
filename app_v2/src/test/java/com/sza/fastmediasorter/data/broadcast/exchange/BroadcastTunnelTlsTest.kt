package com.sza.fastmediasorter.data.broadcast.exchange

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.concurrent.thread

/**
 * A P-256 leaf from `device-exchange/tunnel-test.p12`, standing in for the per-broadcast Keystore identity that a
 * JVM test cannot make (contract DEVICE-EXCHANGE 27 item DM).
 */
object TunnelTestIdentity {
    private const val RESOURCE = "device-exchange/tunnel-test.p12"
    private val PASSWORD = "changeit".toCharArray()

    private val store: KeyStore by lazy {
        KeyStore.getInstance("PKCS12").apply {
            val resource = TunnelTestIdentity::class.java.classLoader?.getResourceAsStream(RESOURCE)
            requireNotNull(resource) { "missing test resource $RESOURCE" }.use { load(it, PASSWORD) }
        }
    }

    val leaf: X509Certificate get() = store.getCertificate(store.aliases().nextElement()) as X509Certificate

    val pin: String get() = BroadcastTunnelTls.pinOf(leaf)

    fun context(): SSLContext {
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(store, PASSWORD)
        }
        return SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
    }
}

class BroadcastTunnelTlsTest {

    private val outer = ServerSocket(0, 1, InetAddress.getLoopbackAddress())

    @After
    fun tearDown() {
        outer.close()
    }

    @Test
    fun `the tunnel pin is the SHA256 form of the exchange certificate fingerprint`() {
        val pin = BroadcastTunnelTls.pinOf(TunnelTestIdentity.leaf)

        assertEquals(ExchangeCertificateFingerprint.of(TunnelTestIdentity.leaf.encoded), pin)
        assertTrue(pin, Regex("SHA256:[A-Za-z0-9+/]{43}").matches(pin))
    }

    @Test
    fun `the pinned leaf carries clear bytes both ways through the tunnel`() {
        serveUppercaseEcho()

        val answer = Socket(InetAddress.getLoopbackAddress(), outer.localPort).use { stream ->
            stream.soTimeout = TIMEOUT_MS
            val tls = BroadcastTunnelTls.connect(
                TunnelTestIdentity.pin,
                stream.getInputStream(),
                stream.getOutputStream(),
                stream,
            )
            tls.getOutputStream().apply {
                write(PAYLOAD.toByteArray())
                flush()
            }
            String(readExactly(tls, PAYLOAD.length))
        }

        assertEquals(PAYLOAD.uppercase(), answer)
    }

    @Test
    fun `a leaf with another pin is refused`() {
        serveUppercaseEcho()

        Socket(InetAddress.getLoopbackAddress(), outer.localPort).use { stream ->
            stream.soTimeout = TIMEOUT_MS
            assertThrows(IOException::class.java) {
                BroadcastTunnelTls.connect(OTHER_PIN, stream.getInputStream(), stream.getOutputStream(), stream)
            }
        }
    }

    private fun serveUppercaseEcho() {
        thread(isDaemon = true) {
            runCatching {
                val stream = outer.accept()
                val tls = BroadcastTunnelTls.serve(
                    TunnelTestIdentity.context(),
                    stream.getInputStream(),
                    stream.getOutputStream(),
                    stream,
                )
                tls.use {
                    val asked = String(readExactly(it, PAYLOAD.length))
                    it.getOutputStream().apply {
                        write(asked.uppercase().toByteArray())
                        flush()
                    }
                }
            }
        }
    }

    private fun readExactly(socket: Socket, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var filled = 0
        while (filled < length) {
            val read = socket.getInputStream().read(buffer, filled, length - filled)
            if (read < 0) throw IOException("stream ended after $filled bytes")
            filled += read
        }
        return buffer
    }

    private companion object {
        const val PAYLOAD = "get /live"
        const val TIMEOUT_MS = 10_000
        const val OTHER_PIN = "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }
}
