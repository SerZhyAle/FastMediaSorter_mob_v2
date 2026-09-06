package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearFileOpenRequest
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.model.findByTargetRef
import com.sza.fastmediasorter.wear.domain.model.normalizeWearStreamUrl
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.ui.common.playerRouteFor
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import timber.log.Timber
import javax.inject.Inject

/**
 * S1955: turns what an outside caller asked for into the address that opens it.
 *
 * A tile is tapped when the app may never have run, so resolution has to read the stores rather than the
 * app's live state. Only the stream target needs the player's numeric route, and that number is handed out
 * by an in-memory singleton which is empty on a fresh process - hence [PrepareWearStreamPlaybackUseCase] is
 * called here to fill it before a route is returned (strategic §6.8).
 *
 * A target that no longer resolves returns null rather than a fallback, because "the thing you pinned is
 * gone" and "here is the home screen" are different answers and the caller has to be able to tell them
 * apart (strategic §5.2).
 */
class ResolveWearLaunchRouteUseCase @Inject constructor(
    private val networkSourceRepository: NetworkSourceRepository,
    private val streamChannelRepository: WearStreamChannelRepository,
    private val prepareWearStreamPlayback: PrepareWearStreamPlaybackUseCase,
    private val prepareWearFilePlayback: PrepareWearFilePlaybackUseCase,
) {

    suspend operator fun invoke(target: WearLaunchTarget): String? = when (target) {
        is WearLaunchTarget.Pick -> WearRoutes.tileTargetPicker(target.kind.name)
        is WearLaunchTarget.Open -> resolveOpen(target.ref)
        is WearLaunchTarget.File -> resolveFile(target)
        is WearLaunchTarget.Destination -> routeFor(target.id)
    }

    /**
     * S2511: reads nothing, because a destination is a static address.
     *
     * The other three targets resolve by consulting a store, which is what makes them able to return null
     * for "the thing you pinned is gone". A destination cannot go missing, so it always resolves - and it
     * must, because a shortcut grid is tapped on a watch where the app may never have run.
     *
     * Exhaustive with no else branch on purpose: a twelfth destination fails compilation here rather than
     * silently resolving to nothing.
     */
    private fun routeFor(id: WearDestinationId): String {
        Timber.d("S2511: shortcut tapped, destination=%s", id)
        return routeOf(id)
    }

    private fun routeOf(id: WearDestinationId): String = when (id) {
        WearDestinationId.RESOURCES -> WearRoutes.NETWORK_SOURCES
        WearDestinationId.PHONE -> WearRoutes.PHONE_HOME
        WearDestinationId.LOCAL -> WearRoutes.LOCAL_HOME
        WearDestinationId.STREAMS -> WearRoutes.STREAMS
        WearDestinationId.APPS -> WearRoutes.APPS
        WearDestinationId.FAVOURITES -> WearRoutes.FAVOURITES
        WearDestinationId.CALCULATOR -> WearRoutes.CALCULATOR
        WearDestinationId.NETWORK_MONITOR -> WearRoutes.NETWORK_MONITOR
        WearDestinationId.GAME -> WearRoutes.GAME
        WearDestinationId.VOICE_RECORDER -> WearRoutes.VOICE_RECORDER
        WearDestinationId.SYSTEM_INFO -> WearRoutes.SYSTEM_INFO
        WearDestinationId.WATER_FLASHLIGHT -> WearRoutes.WATER_FLASHLIGHT
        WearDestinationId.MOTION_MONITOR -> WearRoutes.MOTION_MONITOR
        // S2457: resolves in both flavors on purpose. A shortcut naming this destination on a `standard`
        // watch lands on the screen, which states that the build withholds the capability - a better
        // answer than a dead tap, and the only one available here, since a route is a static address and
        // this resolver has no way to ask whether the Apps catalog offered the row.
        WearDestinationId.BODY_SENSOR -> WearRoutes.BODY_SENSOR
    }

    private suspend fun resolveOpen(ref: WearTileTargetRef): String? = when (ref) {
        WearTileTargetRef.Favourites -> WearRoutes.FAVOURITES
        is WearTileTargetRef.Resource -> resolveResource(ref)
        is WearTileTargetRef.Stream -> resolveStream(ref)
    }

    /** The overview screen re-reads the store from its id, so a string route survives a cold start as is. */
    private suspend fun resolveResource(ref: WearTileTargetRef.Resource): String? =
        networkSourceRepository.getAllSources()
            .findByTargetRef(ref)
            ?.let { WearRoutes.sourceMediaType(it.id, it.name) }

    private suspend fun resolveStream(ref: WearTileTargetRef.Stream): String? {
        val channel = streamChannelRepository.getAllChannels()
            .firstOrNull { normalizeWearStreamUrl(it.url) == ref.normalizedUrl }
            ?: return null
        val playback = prepareWearStreamPlayback(channel)
        return if (playback.isVideo) {
            WearRoutes.videoPlayer(playback.fileId)
        } else {
            WearRoutes.audioPlayer(playback.fileId)
        }
    }

    private fun resolveFile(target: WearLaunchTarget.File): String? {
        val playback = prepareWearFilePlayback(WearFileOpenRequest(target.path, target.mimeType))
        return playerRouteFor(playback.fileId, playback.mimeType)
    }
}
