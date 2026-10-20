package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.wear.domain.model.WearExchangeMessage
import javax.net.ssl.SSLException

/**
 * Reads the row of contract DEVICE-EXCHANGE 25 item CZ off what a connect or an open threw: a refusal reason, a
 * certificate the pin does not accept, a frame the server answered badly, a protected connection that could not
 * be set up, and everything else as "cannot reach".
 */
object WearExchangeFailureClassifier {

    private const val MAX_CHAIN = 8

    fun of(failure: Throwable?): WearExchangeMessage {
        val chain = generateSequence(failure) { it.cause }.take(MAX_CHAIN).toList()
        val refused = chain.filterIsInstance<WearExchangeRefusedException>().firstOrNull()
        return when {
            refused != null -> WearExchangeMessage.fromWire(refused.reason)
            chain.any { it is WearExchangeNotEnrolledException } -> WearExchangeMessage.DEVICE_REVOKED
            chain.any {
                it is WearExchangeCertificateChangedException || it is WearExchangeCertificateUntrustedException
            } -> WearExchangeMessage.CERTIFICATE_CHANGED
            chain.any { it is WearExchangeWireException } -> WearExchangeMessage.UNANSWERED
            chain.any { it is SSLException } -> WearExchangeMessage.TLS_FAILED
            else -> WearExchangeMessage.UNREACHABLE_SERVER
        }
    }
}
