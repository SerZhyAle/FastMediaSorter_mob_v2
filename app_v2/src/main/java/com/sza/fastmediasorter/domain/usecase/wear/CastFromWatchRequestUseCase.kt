package com.sza.fastmediasorter.domain.usecase.wear

import com.sza.fastmediasorter.core.cast.ActiveCastControllerHolder
import com.sza.fastmediasorter.core.cast.CastController
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.WearCastAck
import com.sza.fastmediasorter.domain.model.WearCastMediaType
import com.sza.fastmediasorter.domain.model.WearCastOrigin
import com.sza.fastmediasorter.domain.model.WearCastOutcome
import com.sza.fastmediasorter.domain.model.WearCastRequest
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.PhoneResourceToken
import javax.inject.Inject

/** S2531: turns one description of remote content into a cast on this phone's own session. */
class CastFromWatchRequestUseCase @Inject constructor(
    private val activeCastControllerHolder: ActiveCastControllerHolder,
    private val resourceRepository: ResourceRepository
) {

    suspend operator fun invoke(request: WearCastRequest): WearCastAck =
        WearCastAck(requestId = request.requestId, outcome = decide(request))

    /**
     * No live controller is [WearCastOutcome.PICKER_NEEDED], not [WearCastOutcome.CAST_UNAVAILABLE]:
     * nothing is casting yet, which is the same "act on the phone" sentence as an unchosen receiver.
     */
    private suspend fun decide(request: WearCastRequest): WearCastOutcome {
        val controller = activeCastControllerHolder.current()
        return when {
            controller == null -> WearCastOutcome.PICKER_NEEDED
            !controller.isCastAvailable -> WearCastOutcome.CAST_UNAVAILABLE
            else -> when (val resolution = resolve(request)) {
                is Resolution.Refused -> resolution.outcome
                is Resolution.Ready -> handOver(controller, resolution.file)
            }
        }
    }

    /**
     * Picking a receiver needs an Activity, which no caller of this class has, so an available seam
     * with no session is answered rather than driven - the user's next move is on the phone.
     */
    private fun handOver(controller: CastController, file: MediaFile): WearCastOutcome =
        if (controller.isCasting) {
            controller.sendCurrentMedia(file)
            WearCastOutcome.CASTING
        } else {
            WearCastOutcome.PICKER_NEEDED
        }

    private suspend fun resolve(request: WearCastRequest): Resolution = when (request.origin) {
        WearCastOrigin.STREAM -> resolveStream(request)
        WearCastOrigin.NETWORK_SOURCE -> resolveNetworkSource(request)
    }

    private fun resolveStream(request: WearCastRequest): Resolution =
        if (request.address.isBlank()) {
            Resolution.Refused(WearCastOutcome.UNSUPPORTED_CONTENT)
        } else {
            Resolution.Ready(mediaFileFor(request, request.address, resourceId = null))
        }

    /**
     * The address is rebuilt with the very token the phone-resource bridge already resolves watch
     * requests by, so a path that browsed on the watch cannot address a different file here.
     */
    private suspend fun resolveNetworkSource(request: WearCastRequest): Resolution {
        if (request.address.isBlank()) {
            return Resolution.Refused(WearCastOutcome.UNSUPPORTED_CONTENT)
        }
        val resource = resourceRepository.getResourceById(request.sourceId)
        return if (resource == null) {
            Resolution.Refused(WearCastOutcome.NOT_FOUND)
        } else {
            val address = PhoneResourceToken(
                resourceId = request.sourceId,
                relativePath = request.address
            ).resolveAgainst(resource)
            Resolution.Ready(mediaFileFor(request, address, resourceId = request.sourceId))
        }
    }

    /**
     * Size and date are left at zero: the watch never measured the file, and the cast seam routes on
     * the path alone, so a guessed number would only reach the session title as a wrong one.
     */
    private fun mediaFileFor(request: WearCastRequest, address: String, resourceId: Long?): MediaFile =
        MediaFile(
            name = request.displayName,
            path = address,
            type = request.mediaType.toMediaType(),
            size = 0L,
            createdDate = 0L,
            resourceId = resourceId
        )

    private fun WearCastMediaType.toMediaType(): MediaType = when (this) {
        WearCastMediaType.IMAGE -> MediaType.IMAGE
        WearCastMediaType.VIDEO -> MediaType.VIDEO
        WearCastMediaType.AUDIO -> MediaType.AUDIO
    }

    private sealed interface Resolution {

        data class Ready(val file: MediaFile) : Resolution

        data class Refused(val outcome: WearCastOutcome) : Resolution
    }
}
