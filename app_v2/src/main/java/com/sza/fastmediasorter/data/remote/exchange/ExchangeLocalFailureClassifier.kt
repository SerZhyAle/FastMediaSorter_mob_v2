package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateChangedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateUntrustedException
import com.sza.fastmediasorter.domain.model.ExchangeLocalFailure
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import javax.net.ssl.SSLException

/**
 * Tells the three local failures of contract DEVICE-EXCHANGE 25 item CZ apart from the exception a connect threw:
 * a pinned or untrusted certificate, a server that did not answer after the handshake, a protected connection
 * that could not be set up, and everything else as "cannot reach".
 */
object ExchangeLocalFailureClassifier {

    private const val MAX_CHAIN = 8

    fun of(failure: Throwable?): ExchangeLocalFailure {
        val chain = generateSequence(failure) { it.cause }.take(MAX_CHAIN).toList()
        val timeInvalid = chain.any { it is CertificateExpiredException || it is CertificateNotYetValidException }
        val message = when {
            chain.any { it is ExchangeCertificateChangedException || it is ExchangeCertificateUntrustedException } ->
                ExchangeMessage.CERTIFICATE_CHANGED
            chain.any { it is DeviceExchangeWireException } -> ExchangeMessage.UNANSWERED
            timeInvalid || chain.any { it is SSLException } -> ExchangeMessage.TLS_FAILED
            else -> ExchangeMessage.UNREACHABLE_SERVER
        }
        return ExchangeLocalFailure(message, timeInvalid)
    }
}
