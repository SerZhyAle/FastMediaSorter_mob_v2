package com.sza.fastmediasorter.wear.data.wear

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeAccountRepository
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEnrollResult
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeFailureClassifier
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeJoin
import com.sza.fastmediasorter.wear.domain.model.WearExchangeMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * S4158: takes one exchange entry the phone handed over, enrolls this watch into its account with the pairing code
 * and answers. The answer is sent for every outcome, refusals included: the phone must tell "the code expired"
 * from "the watch never got it", because those ask the owner for different things.
 *
 * Nothing logged here names the code, the token, the login or the address (contract section 7.9).
 */
class WearExchangeHandoverReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val accounts: WearExchangeAccountRepository,
) {

    suspend fun handle(nodeId: String, data: ByteArray) {
        val ack = when (val parsed = WearExchangeHandoverCodec.parse(data, gson)) {
            WearExchangeHandoverParseResult.Malformed -> refusal(requestId = "", WearExchangeHandoverReasons.MALFORMED)
            is WearExchangeHandoverParseResult.UnsupportedVersion -> {
                Timber.w("Exchange hand-over: unknown format version ${parsed.version}")
                refusal(requestId = "", WearExchangeHandoverReasons.UNSUPPORTED_VERSION)
            }
            is WearExchangeHandoverParseResult.Parsed -> take(parsed.payload)
        }
        answer(nodeId, ack)
    }

    private suspend fun take(payload: WearExchangeHandoverPayload): WearExchangeHandoverAck =
        if (isExpired(payload.expiresAt, Instant.now())) {
            refusal(payload.requestId, WearExchangeHandoverReasons.CODE_EXPIRED)
        } else {
            val join = WearExchangeJoin(
                host = payload.host,
                port = payload.port,
                login = payload.login,
                pinnedCertificate = payload.pinnedCertificate,
                pairingCode = payload.pairingCode,
            )
            acknowledge(payload.requestId, accounts.enroll(join))
        }

    private fun acknowledge(requestId: String, result: WearExchangeEnrollResult): WearExchangeHandoverAck =
        when (result) {
            is WearExchangeEnrollResult.Enrolled -> {
                Timber.i("Exchange hand-over: this watch enrolled")
                WearExchangeHandoverAck(requestId, enrolled = true)
            }
            is WearExchangeEnrollResult.Refused -> refusal(requestId, result.reason)
            WearExchangeEnrollResult.CertificateUntrusted ->
                refusal(requestId, WearExchangeHandoverReasons.CERTIFICATE_UNTRUSTED)
            WearExchangeEnrollResult.CertificateChanged ->
                refusal(requestId, WearExchangeHandoverReasons.CERTIFICATE_CHANGED)
            is WearExchangeEnrollResult.Unreachable -> {
                val failure = WearExchangeFailureClassifier.of(result.cause)
                // The class only: a connect failure's message names the host.
                Timber.i(
                    "Exchange hand-over: server not reached, code=%s (%s)",
                    failure.code,
                    result.cause.javaClass.simpleName,
                )
                WearExchangeHandoverAck(requestId, enrolled = false, reason = unreachableReason(failure))
            }
        }

    // The answer lets the phone name the right step: a silent server and a failed handshake are not a dead host.
    private fun unreachableReason(failure: WearExchangeMessage): String = when (failure) {
        WearExchangeMessage.UNANSWERED -> WearExchangeHandoverReasons.UNANSWERED
        WearExchangeMessage.TLS_FAILED -> WearExchangeHandoverReasons.TLS_FAILED
        else -> WearExchangeHandoverReasons.UNREACHABLE
    }

    private fun refusal(requestId: String, reason: String): WearExchangeHandoverAck {
        Timber.w("Exchange hand-over: refused - %s", reason)
        return WearExchangeHandoverAck(requestId, enrolled = false, reason = reason)
    }

    private suspend fun answer(nodeId: String, ack: WearExchangeHandoverAck) {
        if (nodeId.isBlank()) return
        try {
            Wearable.getMessageClient(context)
                .sendMessage(
                    nodeId,
                    WearDataLayerPaths.EXCHANGE_HANDOVER_ACK,
                    WearExchangeHandoverCodec.serializeAck(ack, gson),
                )
                .await()
        } catch (e: ApiException) {
            // The phone then waits out its timeout and reports that the watch did not answer.
            Timber.e(e, "Exchange hand-over: the answer could not be sent")
        }
    }

    companion object {

        /**
         * True only for a server time already past. A text this build cannot read is left to the server, which
         * refuses a stale code with its own reason - refusing it here would turn a format change into an outage.
         */
        fun isExpired(expiresAt: String?, now: Instant): Boolean = try {
            expiresAt != null && Instant.parse(expiresAt).isBefore(now)
        } catch (e: DateTimeParseException) {
            Timber.w("Exchange hand-over: unreadable expiry (%s)", e.javaClass.simpleName)
            false
        }
    }
}
