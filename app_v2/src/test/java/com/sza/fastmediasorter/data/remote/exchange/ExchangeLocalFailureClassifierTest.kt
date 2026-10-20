package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateChangedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateUntrustedException
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import javax.net.ssl.SSLHandshakeException

class ExchangeLocalFailureClassifierTest {

    @Test
    fun `a dead host or a refused connect is unreachable`() {
        listOf(UnknownHostException("h"), ConnectException("c"), SocketTimeoutException("t"), IOException("x"))
            .forEach { failure ->
                val result = ExchangeLocalFailureClassifier.of(failure)
                assertEquals(failure.javaClass.simpleName, ExchangeMessage.UNREACHABLE_SERVER, result.message)
                assertFalse(result.certificateTimeInvalid)
            }
    }

    @Test
    fun `a null cause is unreachable`() {
        assertEquals(ExchangeMessage.UNREACHABLE_SERVER, ExchangeLocalFailureClassifier.of(null).message)
    }

    @Test
    fun `a broken frame after the handshake is unanswered`() {
        val result = ExchangeLocalFailureClassifier.of(DeviceExchangeWireException("enroll was not answered"))
        assertEquals(ExchangeMessage.UNANSWERED, result.message)
    }

    @Test
    fun `a failed protected handshake is tls-failed`() {
        val result = ExchangeLocalFailureClassifier.of(SSLHandshakeException("handshake failed"))
        assertEquals(ExchangeMessage.TLS_FAILED, result.message)
        assertFalse(result.certificateTimeInvalid)
    }

    @Test
    fun `a certificate outside its validity period is tls-failed and points at the clock`() {
        val handshake = SSLHandshakeException("x")
        handshake.initCause(CertificateExpiredException("expired"))
        val expired = IOException("wrapped", handshake)
        val notYet = SSLHandshakeException("y")
        notYet.initCause(CertificateNotYetValidException("early"))
        listOf(expired, notYet).forEach { failure ->
            val result = ExchangeLocalFailureClassifier.of(failure)
            assertEquals(ExchangeMessage.TLS_FAILED, result.message)
            assertTrue(result.certificateTimeInvalid)
        }
    }

    @Test
    fun `a pinned or untrusted certificate is a changed certificate`() {
        assertEquals(
            ExchangeMessage.CERTIFICATE_CHANGED,
            ExchangeLocalFailureClassifier.of(ExchangeCertificateChangedException("a", "b")).message,
        )
        assertEquals(
            ExchangeMessage.CERTIFICATE_CHANGED,
            ExchangeLocalFailureClassifier.of(ExchangeCertificateUntrustedException("a")).message,
        )
    }

    @Test
    fun `a cyclic cause chain ends`() {
        val first = IOException("a")
        val second = IOException("b", first)
        first.initCause(second)
        assertEquals(ExchangeMessage.UNREACHABLE_SERVER, ExchangeLocalFailureClassifier.of(first).message)
    }
}
