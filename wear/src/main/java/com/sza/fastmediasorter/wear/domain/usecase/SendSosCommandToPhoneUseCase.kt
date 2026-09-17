package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.SosMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * S3216: asks the paired phone to raise, or drop, the same distress signal this watch is running.
 *
 * Fire and forget by ADR-2: no ack, no timeout, no retry, and an unreachable phone is not an error. The
 * watch is already sounding by the time this runs, so the only thing a failure here could add is a delay
 * in an emergency - autonomous operation is the designed answer to a companion out of range.
 *
 * Raw bytes rather than an event envelope, unlike its neighbours: the payload is the `SosMode` member
 * name and nothing else, and wrapping one token in a JSON envelope would add a shape the phone would
 * have to decode before it could start a siren.
 */
class SendSosCommandToPhoneUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Raises the signal on the paired phone, in [mode]. */
    suspend fun start(mode: SosMode) {
        send(WearDataLayerPaths.SOS_START_FROM_WATCH, mode.name.toByteArray())
    }

    /** Drops the signal on the paired phone, whichever device started it. */
    suspend fun stop() {
        send(WearDataLayerPaths.SOS_STOP_FROM_WATCH, ByteArray(0))
    }

    private suspend fun send(path: String, payload: ByteArray) {
        Timber.d("S3216: sending $path to the phone")
        val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.await() }
            .onFailure { Timber.w(it, "SOS: could not list connected phones, signalling alone") }
            .getOrDefault(emptyList())
        if (nodes.isEmpty()) {
            Timber.i("SOS: no phone in range, the watch signals alone")
            return
        }
        nodes.forEach { node ->
            runCatching {
                Wearable.getMessageClient(context).sendMessage(node.id, path, payload).await()
            }.onFailure { Timber.w(it, "SOS: %s to %s failed", path, node.id) }
        }
    }
}
