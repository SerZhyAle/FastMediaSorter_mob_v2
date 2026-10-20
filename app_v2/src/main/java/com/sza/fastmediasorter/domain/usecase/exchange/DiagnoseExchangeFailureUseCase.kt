package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.core.network.ExchangeNetworkEvidenceReader
import com.sza.fastmediasorter.data.remote.exchange.ExchangeLocalFailureClassifier
import com.sza.fastmediasorter.domain.model.ExchangeCheckEvidence
import com.sza.fastmediasorter.domain.model.ExchangeCheckLadder
import com.sza.fastmediasorter.domain.model.ExchangeCheckRoute
import com.sza.fastmediasorter.domain.model.ExchangeCheckTarget
import com.sza.fastmediasorter.domain.model.ExchangeDiagnosis
import com.sza.fastmediasorter.domain.model.ExchangeLocalFailure
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import javax.inject.Inject

/**
 * Turns a failed exchange connection into what the screen shows: the table row and the ordered steps of the
 * check ladder (contract DEVICE-EXCHANGE 25 items CY and CZ). The device's own facts are read only for a failure
 * that is walked along the ladder; a refusal with a reason of its own offers its one step as it is.
 */
class DiagnoseExchangeFailureUseCase @Inject constructor(
    private val network: ExchangeNetworkEvidenceReader,
) {

    operator fun invoke(
        message: ExchangeMessage,
        host: String?,
        target: ExchangeCheckTarget = ExchangeCheckTarget.SERVER,
        certificateTimeInvalid: Boolean = false,
    ): ExchangeDiagnosis {
        val evidence = if (message.walked) {
            network.read(certificateTimeInvalid)
        } else {
            ExchangeCheckEvidence(certificateTimeInvalid = certificateTimeInvalid)
        }
        return derive(message, host, target, evidence)
    }

    fun fromLocal(
        failure: ExchangeLocalFailure,
        host: String?,
        target: ExchangeCheckTarget = ExchangeCheckTarget.SERVER,
    ): ExchangeDiagnosis = invoke(failure.message, host, target, failure.certificateTimeInvalid)

    /** The failure a connect threw, read through the local classes of item CZ. */
    fun fromCause(
        cause: Throwable?,
        host: String?,
        target: ExchangeCheckTarget = ExchangeCheckTarget.SERVER,
    ): ExchangeDiagnosis = fromLocal(ExchangeLocalFailureClassifier.of(cause), host, target)

    internal companion object {
        fun derive(
            message: ExchangeMessage,
            host: String?,
            target: ExchangeCheckTarget,
            evidence: ExchangeCheckEvidence,
        ): ExchangeDiagnosis {
            val route = host?.let(ExchangeCheckLadder::routeOf) ?: ExchangeCheckRoute.INTERNET
            val family = host?.let(ExchangeCheckLadder::familyOf)
            return ExchangeDiagnosis(message, ExchangeCheckLadder.walk(message, target, route, evidence, family))
        }
    }
}
