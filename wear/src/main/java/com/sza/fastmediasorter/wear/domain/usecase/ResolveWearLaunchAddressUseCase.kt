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
        return addressFor(target)
    }

    private suspend fun addressFor(target: WearLaunchTarget): WearLaunchAddress? = when (target) {
        // S3178: the picker assigns a tile to a resource or a channel, both of which are remote
        // sources; the File target is content arriving from outside this watch.
        is WearLaunchTarget.Pick ->
            if (capabilities.offersRemoteSources) WearLaunchAddress.TileTargetPicker(target.kind) else null

        is WearLaunchTarget.Open -> resolveOpen(target.ref)
        is WearLaunchTarget.File -> if (capabilities.offersContentTransfer) resolveFile(target) else null
        is WearLaunchTarget.Destination -> screenFor(target.id)
    }

    /**
     * S2511 / S2995: resolves static destination addresses. A restricted capability returns null in
     * the builds where it is disabled.
     *
     * S3178: this is the narrowest place the whole store boundary can be enforced for an OUTSIDE caller.
     * A tile, a complication, a launch intent from the paired phone and another application's shortcut
     * all arrive here carrying a destination name, none of them having passed through a screen this
     * build drew - so a destination filtered out of the home or Apps catalog is still nameable unless
     * it is refused here too. Null is the existing "the thing you pinned is gone" answer and is the
     * right one: the caller is told the address did not resolve rather than being sent somewhere else.
     */
    private fun screenFor(id: WearDestinationId): WearLaunchAddress.Screen? =
        if (offers(id)) WearLaunchAddress.Screen(id) else null

    /**
     * S3178: whether this build offers the destination, by the same categories as
     * `wear/config/store-boundary-policy.json`. Exhaustive rather than `else -> true`: a destination
     * added later must be classified deliberately, and an unclassified one reaching the store artifact
     * is exactly the silent widening the policy exists to prevent.
     */
    private fun offers(id: WearDestinationId): Boolean = when (id) {
        WearDestinationId.RESOURCES,
        WearDestinationId.STREAMS -> capabilities.offersRemoteSources

        WearDestinationId.PHONE,
        WearDestinationId.PHONE_CAMERA -> capabilities.offersContentTransfer

        WearDestinationId.LOCAL,
        WearDestinationId.FAVOURITES -> capabilities.offersMediaAccess

        WearDestinationId.NETWORK_MONITOR ->
            capabilities.offersDeviceDiagnostics && capabilities.offersNearbyDeviceState

        WearDestinationId.SYSTEM_INFO,
        WearDestinationId.TOURIST -> capabilities.offersDeviceDiagnostics

        WearDestinationId.VOICE_RECORDER,
        WearDestinationId.BROADCAST -> capabilities.offersVoiceRecording

        WearDestinationId.BLOOD_PRESSURE,
        WearDestinationId.MOTION_MONITOR -> capabilities.offersHealthFeatures

        WearDestinationId.BODY_SENSOR -> capabilities.offersBodySensorDiagnostics

        // S3362: the two screens a swipe cannot leave. Declaring no permission is what kept them in
        // the store-safe group below; WO-V3 is about the gesture, not about the manifest.
        WearDestinationId.WATER_FLASHLIGHT,
        WearDestinationId.SOS -> capabilities.offersScreenTakeoverPrograms

        // S3362: the clipboard's one action is a Data Layer round trip to the paired phone.
        WearDestinationId.CLIPBOARD -> capabilities.offersContentTransfer

        // The store-safe surface: no permission, no user content, no device data.
        WearDestinationId.APPS,
        WearDestinationId.CALCULATOR,
        WearDestinationId.GAME,
        WearDestinationId.STOPWATCH,
        WearDestinationId.HOME -> true
    }

    private suspend fun resolveOpen(ref: WearTileTargetRef): WearLaunchAddress? = when (ref) {
        WearTileTargetRef.Favourites -> screenFor(WearDestinationId.FAVOURITES)
        is WearTileTargetRef.Resource -> if (capabilities.offersRemoteSources) resolveResource(ref) else null
        is WearTileTargetRef.Stream -> if (capabilities.offersRemoteSources) resolveStream(ref) else null
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
