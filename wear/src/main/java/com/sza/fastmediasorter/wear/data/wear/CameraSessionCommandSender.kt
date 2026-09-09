package com.sza.fastmediasorter.wear.data.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.wear.domain.model.CameraCommandPayload
import com.sza.fastmediasorter.wear.domain.model.CameraSessionPayloadCodec
import com.sza.fastmediasorter.wear.domain.model.PhoneCameraFailure
import com.sza.fastmediasorter.wear.domain.repository.PhoneCameraSessionHolder
import com.sza.fastmediasorter.wear.util.errorUnlessCancellation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2551 pillar A: the three camera commands, on the bridge that already carries control traffic.
 *
 * Shaped after `ListenAckSender`, the shipped mirror going the other way. Nothing but commands rides
 * here - the picture travels over the phone's own LAN server, whose address arrives in the ack.
 *
 * A send that fails becomes a refusal rather than an exception, for the reason S2550 made refusal a
 * value: the owner must never have to tell "the phone said no" from "the phone never heard".
 */
@Singleton
class CameraSessionCommandSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holder: PhoneCameraSessionHolder,
    private val codec: CameraSessionPayloadCodec,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        send(WearDataLayerPaths.CAMERA_VIEW_START, lensId = null)
    }

    fun switchLens(lensId: String) {
        send(WearDataLayerPaths.CAMERA_VIEW_SWITCH, lensId = lensId)
    }

    fun stop() {
        send(WearDataLayerPaths.CAMERA_VIEW_STOP, lensId = null)
    }

    /**
     * The id is minted here and written into the holder BEFORE the bytes leave, because the ack can
     * arrive at the listener before this coroutine resumes - a holder still holding the previous id
     * at that moment would drop the answer to the command it just sent.
     */
    private fun send(path: String, lensId: String?) {
        val requestId = UUID.randomUUID().toString()
        holder.markRequested(requestId)
        val payload = codec.encodeCommand(CameraCommandPayload(requestId = requestId, lensId = lensId))
        scope.launch {
            runCatching {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                if (nodes.isEmpty()) {
                    holder.markRefused(PhoneCameraFailure.NO_PHONE)
                    return@runCatching
                }
                nodes.forEach { node ->
                    Wearable.getMessageClient(context).sendMessage(node.id, path, payload).await()
                }
                Timber.i("Sent a camera command: path=%s", path)
            }.onFailure {
                it.errorUnlessCancellation("Failed to send a camera command")
                holder.markRefused(PhoneCameraFailure.NO_PHONE)
            }
        }
    }
}
