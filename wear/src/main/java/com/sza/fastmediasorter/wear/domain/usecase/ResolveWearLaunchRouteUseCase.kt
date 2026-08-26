package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.model.findByTargetRef
import com.sza.fastmediasorter.wear.domain.model.normalizeWearStreamUrl
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
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
) {

    suspend operator fun invoke(target: WearLaunchTarget): String? = when (target) {
        is WearLaunchTarget.Pick -> WearRoutes.tileTargetPicker(target.kind.name)
        is WearLaunchTarget.Open -> resolveOpen(target.ref)
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
}
