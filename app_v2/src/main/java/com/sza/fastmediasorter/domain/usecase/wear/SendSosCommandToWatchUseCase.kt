package com.sza.fastmediasorter.domain.usecase.wear

import com.sza.fastmediasorter.domain.model.sos.SosMode
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import timber.log.Timber
import javax.inject.Inject

/**
 * S3216: asks the paired watch to raise, or drop, the same distress signal this phone is running.
 *
 * Fire and forget by ADR-2: there is no ack, no timeout and no retry, and an unreachable watch is not an
 * error. The phone's own siren is already sounding by the time this runs, so the only thing a failure
 * here could add is a delay in an emergency - autonomous operation is the designed answer to a companion
 * being out of range, not a degraded one.
 *
 * The payload is the mode's member name and nothing else: the two modules share no source, so the enum's
 * member names are the whole wire contract.
 */
class SendSosCommandToWatchUseCase @Inject constructor(
    private val dataLayerRepository: WearableDataLayerRepository,
) {

    /** Raises the signal on every connected watch, in [mode]. */
    suspend fun start(mode: SosMode) {
        send(WearDataLayerPaths.SOS_START_FROM_PHONE, mode.name.toByteArray())
    }

    /** Drops the signal on every connected watch, whichever device started it. */
    suspend fun stop() {
        send(WearDataLayerPaths.SOS_STOP_FROM_PHONE, ByteArray(0))
    }

    private suspend fun send(path: String, payload: ByteArray) {
        val nodes = runCatching { dataLayerRepository.getConnectedNodes() }
            .onFailure { Timber.w(it, "SOS: could not list connected watches, signalling alone") }
            .getOrDefault(emptyList())
        if (nodes.isEmpty()) {
            Timber.i("SOS: no watch in range, the phone signals alone")
            return
        }
        // Every connected node, not the first that accepts: a second watch is a second signal, which is
        // the point of the feature rather than a duplicate to suppress.
        nodes.forEach { node ->
            runCatching { dataLayerRepository.sendMessage(node.id, path, payload) }
                .onFailure { Timber.w(it, "SOS: %s to %s failed", path, node.id) }
        }
    }
}
