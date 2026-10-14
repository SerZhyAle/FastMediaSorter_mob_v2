package com.sza.fastmediasorter.wear.domain.model

import com.sza.fastmediasorter.wear.data.exchange.WearExchangeCertificateChangedException
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeCertificateUntrustedException
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeFailureClassifier
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeNotEnrolledException
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeRefusedException
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeWireException
import com.sza.fastmediasorter.wear.ui.common.messageRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import javax.net.ssl.SSLHandshakeException

class WearExchangeMessageTest {

    @Test
    fun `every wire reason the watch can meet maps to its own row`() {
        val expected = mapOf(
            "bad-credentials" to WearExchangeMessage.BAD_CREDENTIALS,
            "device-revoked" to WearExchangeMessage.DEVICE_REVOKED,
            "signed-out" to WearExchangeMessage.SIGNED_OUT,
            "version-unsupported" to WearExchangeMessage.VERSION_UNSUPPORTED,
            "tls-required" to WearExchangeMessage.TLS_REQUIRED,
            "id-collision" to WearExchangeMessage.ID_COLLISION,
            "capacity" to WearExchangeMessage.CAPACITY,
            "port-unavailable" to WearExchangeMessage.PORT_UNAVAILABLE,
            "unavailable" to WearExchangeMessage.UNAVAILABLE,
            "rate-limited" to WearExchangeMessage.RATE_LIMITED,
            "protection-unsupported" to WearExchangeMessage.PROTECTION_UNSUPPORTED,
            "limit-reached" to WearExchangeMessage.LIMIT_REACHED,
            "not-permitted" to WearExchangeMessage.NOT_PERMITTED,
        )
        expected.forEach { (reason, message) -> assertEquals(reason, message, WearExchangeMessage.fromWire(reason)) }
    }

    @Test
    fun `the clipboard flag and the unknown cases`() {
        assertEquals(
            WearExchangeMessage.LIMIT_REACHED_CLIPBOARD,
            WearExchangeMessage.fromWire("limit-reached", clipboardSend = true),
        )
        assertEquals(WearExchangeMessage.DEVICE_REVOKED, WearExchangeMessage.fromWire("not-enrolled"))
        assertEquals(WearExchangeMessage.REFUSED, WearExchangeMessage.fromWire("something-new"))
        assertEquals(WearExchangeMessage.REFUSED, WearExchangeMessage.fromWire(null))
        assertEquals(WearExchangeMessage.REFUSED, WearExchangeMessage.fromWire(""))
    }

    @Test
    fun `every row has words and no two rows share them`() {
        val resources = WearExchangeMessage.entries.map { it.messageRes() }
        assertEquals(WearExchangeMessage.entries.size, resources.toSet().size)
        resources.forEach { assertNotEquals(0, it) }
    }

    @Test
    fun `a failure reads as the row of item CZ`() {
        assertEquals(
            WearExchangeMessage.RATE_LIMITED,
            WearExchangeFailureClassifier.of(WearExchangeRefusedException("rate-limited")),
        )
        assertEquals(
            WearExchangeMessage.DEVICE_REVOKED,
            WearExchangeFailureClassifier.of(WearExchangeNotEnrolledException()),
        )
        assertEquals(
            WearExchangeMessage.CERTIFICATE_CHANGED,
            WearExchangeFailureClassifier.of(WearExchangeCertificateChangedException()),
        )
        assertEquals(
            WearExchangeMessage.CERTIFICATE_CHANGED,
            WearExchangeFailureClassifier.of(WearExchangeCertificateUntrustedException()),
        )
        assertEquals(
            WearExchangeMessage.UNANSWERED,
            WearExchangeFailureClassifier.of(WearExchangeWireException("enroll was not answered")),
        )
        assertEquals(
            WearExchangeMessage.TLS_FAILED,
            WearExchangeFailureClassifier.of(SSLHandshakeException("handshake")),
        )
        assertEquals(
            WearExchangeMessage.UNREACHABLE_SERVER,
            WearExchangeFailureClassifier.of(ConnectException("refused")),
        )
        assertEquals(WearExchangeMessage.UNREACHABLE_SERVER, WearExchangeFailureClassifier.of(IOException("x")))
        assertEquals(WearExchangeMessage.UNREACHABLE_SERVER, WearExchangeFailureClassifier.of(null))
    }

    @Test
    fun `a wrapped refusal is still found in the cause chain`() {
        val wrapped = IOException("outer", WearExchangeRefusedException("signed-out"))
        assertEquals(WearExchangeMessage.SIGNED_OUT, WearExchangeFailureClassifier.of(wrapped))
    }
}
