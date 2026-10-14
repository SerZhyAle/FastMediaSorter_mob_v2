package com.sza.fastmediasorter.wear.data.wear

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.exchange.WearFolderIdentity
import com.sza.fastmediasorter.wear.data.exchange.WearResourcePinStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * S4228: keeps the PIN of one protected exchange folder the phone handed over and answers. The answer is sent for
 * every outcome, so the phone can tell a refusal from a pass that never arrived.
 *
 * Nothing logged here names the PIN, the root, the account or the address (contract section 7.9).
 */
class WearResourcePassReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val pins: WearResourcePinStore,
) {

    suspend fun handle(nodeId: String, data: ByteArray) {
        Timber.d("S4228: watch received a PIN pass from the phone")
        val ack = when (val parsed = WearResourcePassCodec.parse(data, gson)) {
            WearResourcePassParseResult.Malformed -> refusal(requestId = "", WearResourcePassReasons.MALFORMED)
            is WearResourcePassParseResult.UnsupportedVersion -> {
                Timber.w("Resource pass: unknown format version ${parsed.version}")
                refusal(requestId = "", WearResourcePassReasons.UNSUPPORTED_VERSION)
            }
            is WearResourcePassParseResult.Parsed -> keep(parsed.payload)
        }
        answer(nodeId, ack)
    }

    private suspend fun keep(payload: WearResourcePassPayload): WearResourcePassAck {
        val identity = WearFolderIdentity(payload.fingerprint, payload.root.trim('/'))
        return if (pins.save(identity, payload.pin)) {
            Timber.i("Resource pass: a protected folder's PIN is kept")
            WearResourcePassAck(payload.requestId, stored = true)
        } else {
            refusal(payload.requestId, WearResourcePassReasons.NOT_STORED)
        }
    }

    private fun refusal(requestId: String, reason: String): WearResourcePassAck {
        Timber.w("Resource pass: refused - %s", reason)
        return WearResourcePassAck(requestId, stored = false, reason = reason)
    }

    private suspend fun answer(nodeId: String, ack: WearResourcePassAck) {
        if (nodeId.isBlank()) return
        try {
            Wearable.getMessageClient(context)
                .sendMessage(
                    nodeId,
                    WearDataLayerPaths.RESOURCE_PASS_ACK,
                    WearResourcePassCodec.serializeAck(ack, gson),
                )
                .await()
        } catch (e: ApiException) {
            // The PIN is kept either way; the phone only loses the confirmation and logs it as unanswered.
            Timber.e(e, "Resource pass: the answer could not be sent")
        }
    }
}
