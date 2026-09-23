package com.sza.fastmediasorter.service

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.sza.fastmediasorter.domain.model.WearPhoneResourceDeleteAck
import com.sza.fastmediasorter.domain.model.WearPhoneResourceDeleteOutcome
import com.sza.fastmediasorter.domain.model.WearPhoneResourceDeleteRequest
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.domain.usecase.DeleteWatchRequestedFileUseCase
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3359: serves the watch's ask to remove an original, and answers what this phone did with it.
 *
 * A collaborator rather than a branch in the Data Layer listener, matching the SOS, cast and camera
 * handlers beside it: that class is at detekt's 40-function ceiling, and deleting a file is a decision
 * with a precondition rather than a dispatch.
 *
 * The precondition is the owner's Wear Companion switch. With it off this phone serves the watch
 * nothing, and a destructive request is the last one to make an exception for - so the answer is a
 * refusal, published through [WearableDataLayerRepository.putCompanionRefusal] because every ordinary
 * publish is swallowed by that very switch (S2981).
 */
@Singleton
class WearDeleteRequestHandler @Inject constructor(
    private val deleteWatchRequestedFile: DeleteWatchRequestedFileUseCase,
    private val wearableDataLayerRepository: WearableDataLayerRepository,
    private val gson: Gson
) {

    /**
     * Answers whether [path] is the delete route, acting on it when it is.
     *
     * The match lives here rather than in the listener's dispatch for the reason the class KDoc gives;
     * the boolean exists so the listener's fall-through can try one handler after another without
     * growing a function of its own.
     */
    suspend fun handle(path: String, payload: ByteArray): Boolean {
        if (path != WearDataLayerPaths.PHONE_RESOURCE_DELETE_REQUEST) return false
        // An unreadable ask is still this handler's route: answering nothing leaves the watch waiting
        // out its own timeout, which it reads as "not confirmed" - the safe side of the same contract.
        parse(payload)?.let { serve(it) }
        return true
    }

    private suspend fun serve(request: WearPhoneResourceDeleteRequest) {
        if (!wearableDataLayerRepository.isCompanionEnabled()) {
            Timber.i("Watch delete request refused: the Wear Companion switch is off")
            publish(request.requestId, WearPhoneResourceDeleteOutcome.COMPANION_DISABLED, refusal = true)
            return
        }
        val outcome = deleteWatchRequestedFile(request.token, request.expectedSizeBytes)
        publish(request.requestId, outcome, refusal = false)
    }

    /**
     * The ack rides a per-request Data Item, the shape the page response already uses, so a burst of
     * requests cannot overwrite each other's answers before the watch has read them.
     */
    // A publish failure has one answer whatever GMS raised - the watch's own wait runs out and reads
    // the silence as "not confirmed", which leaves the original claimed only by this phone's log.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun publish(
        requestId: String,
        outcome: WearPhoneResourceDeleteOutcome,
        refusal: Boolean
    ) {
        val path = "${WearDataLayerPaths.PHONE_RESOURCE_DELETE_ACK}/$requestId"
        val bytes = gson.toJson(WearPhoneResourceDeleteAck(requestId = requestId, outcome = outcome))
            .toByteArray(Charsets.UTF_8)
        try {
            if (refusal) {
                wearableDataLayerRepository.putCompanionRefusal(path, bytes)
            } else {
                wearableDataLayerRepository.putDataItem(path, bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Watch delete acknowledgement could not be published")
        }
    }

    private fun parse(payload: ByteArray): WearPhoneResourceDeleteRequest? = try {
        gson.fromJson(payload.decodeToString(), WearPhoneResourceDeleteRequest::class.java)
    } catch (e: JsonSyntaxException) {
        // Without a request id there is nothing to correlate an answer with, and without a token there
        // is nothing to resolve - so an unreadable ask ends here rather than being answered blindly.
        Timber.e(e, "Failed to deserialize a watch delete request")
        null
    }
}
