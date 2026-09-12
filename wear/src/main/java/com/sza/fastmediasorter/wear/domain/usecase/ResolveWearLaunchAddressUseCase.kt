package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearFileOpenRequest
import com.sza.fastmediasorter.wear.domain.model.WearLaunchAddress
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.model.findByTargetRef
import com.sza.fastmediasorter.wear.domain.model.normalizeWearStreamUrl
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * S1955: turns what an outside caller asked for into the address that opens it.
 *
 * A tile is tapped when the app may never have run, so resolution has to read the stores rather than the
 * app's live state. Only the stream target needs the player's numeric route, and that number is handed out
 * by an in-memory singleton which is empty on a fresh process - hence [PrepareWearStreamPlaybackUseCase] is
 * called here to fill it before an address is returned (strategic §6.8).
 *
 * A target that no longer resolves returns null rather than a fallback, because "the thing you pinned is
 * gone" and "here is the home screen" are different answers and the caller has to be able to tell them
 * apart (strategic §5.2).
 *
 * S2751: the answer is a [WearLaunchAddress] rather than a navigation route. The route table and the
 * player picker are presentation, and reaching for them from here was the inverted dependency this ticket
 * removed; the navigation branch maps the address it gets back.
 */
class ResolveWearLaunchAddressUseCase @Inject constructor(
    private val networkSourceRepository: NetworkSourceRepository,
    private val streamChannelRepository: WearStreamChannelRepository,
    private val prepareWearStreamPlayback: PrepareWearStreamPlaybackUseCase,
    private val prepareWearFilePlayback: PrepareWearFilePlaybackUseCase,
    private val capabilities: WearRestrictedCapabilities,
) {

    suspend operator fun invoke(target: WearLaunchTarget): WearLaunchAddress? {
        Timber.d("S2751: launch address requested, target=%s", target)
        return addressFor(target)
    }

    private suspend fun addressFor(target: WearLaunchTarget): WearLaunchAddress? = when (target) {
        is WearLaunchTarget.Pick -> WearLaunchAddress.TileTargetPicker(target.kind)
        is WearLaunchTarget.Open -> resolveOpen(target.ref)
        is WearLaunchTarget.File -> resolveFile(target)
        is WearLaunchTarget.Destination -> screenFor(target.id)
    }

    /**
     * S2511 / S2995: resolves static destination addresses. Restricted capabilities return null in builds where disabled.
     */
    private fun screenFor(id: WearDestinationId): WearLaunchAddress.Screen? {
        return when (id) {
            WearDestinationId.BODY_SENSOR -> if (capabilities.offersBodySensorDiagnostics) WearLaunchAddress.Screen(id) else null
            WearDestinationId.BLOOD_PRESSURE,
            WearDestinationId.MOTION_MONITOR -> if (capabilities.offersHealthFeatures) WearLaunchAddress.Screen(id) else null
            else -> WearLaunchAddress.Screen(id)
        }
    }

    private suspend fun resolveOpen(ref: WearTileTargetRef): WearLaunchAddress? = when (ref) {
        WearTileTargetRef.Favourites -> WearLaunchAddress.Screen(WearDestinationId.FAVOURITES)
        is WearTileTargetRef.Resource -> resolveResource(ref)
        is WearTileTargetRef.Stream -> resolveStream(ref)
    }

    /** The overview screen re-reads the store from its id, so the address survives a cold start as is. */
    private suspend fun resolveResource(ref: WearTileTargetRef.Resource): WearLaunchAddress? =
        networkSourceRepository.getAllSources()
            .findByTargetRef(ref)
            ?.let { WearLaunchAddress.SourceOverview(it.id, it.name) }

    private suspend fun resolveStream(ref: WearTileTargetRef.Stream): WearLaunchAddress? {
        val channel = streamChannelRepository.getAllChannels()
            .firstOrNull { normalizeWearStreamUrl(it.url) == ref.normalizedUrl }
            ?: return null
        val playback = prepareWearStreamPlayback(channel)
        return WearLaunchAddress.StreamPlayback(playback.fileId, playback.isVideo)
    }

    private fun resolveFile(target: WearLaunchTarget.File): WearLaunchAddress {
        val playback = prepareWearFilePlayback(WearFileOpenRequest(target.path, target.mimeType))
        return WearLaunchAddress.MediaFile(playback.fileId, playback.mimeType, fileName = target.path)
    }
}
