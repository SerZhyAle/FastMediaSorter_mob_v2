package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.LastUsedKind
import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.model.WearComplicationContent
import com.sza.fastmediasorter.wear.domain.model.WearComplicationKind
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearNowPlayingRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S2047: loads complication content for Wear OS complication data sources from local repositories only.
 *
 * ADR-2: no network call, no Data Layer call to phone, no Wearable clients.
 */
class LoadWearComplicationContentUseCase @Inject constructor(
    private val resolveLastUsedResourceUseCase: ResolveLastUsedResourceUseCase,
    private val networkSourceRepository: NetworkSourceRepository,
    private val wearFavoritesRepository: WearFavoritesRepository,
    private val wearNowPlayingRepository: WearNowPlayingRepository
) {
    suspend operator fun invoke(kind: WearComplicationKind): WearComplicationContent = when (kind) {
        WearComplicationKind.LAST_RESOURCE -> loadLastResourceContent()
        WearComplicationKind.FAVOURITES_COUNT -> loadFavouritesCountContent()
        WearComplicationKind.NOW_PLAYING -> loadNowPlayingContent()
    }

    /**
     * S2499: the newest entry is not necessarily a network source any more.
     *
     * Before that ticket the whole method assumed it was, so a user whose newest item became a channel
     * lost the complication entirely - it answered [WearComplicationContent.Empty], which reads as
     * "nothing has been opened". A channel needs no second lookup: the resolver has already dropped it
     * if the catalog no longer lists it, and it carried the live channel's name across.
     */
    private suspend fun loadLastResourceContent(): WearComplicationContent {
        val newest = resolveLastUsedResourceUseCase().first().firstOrNull()
        val target = newest?.let { lastResourceTarget(it) }
        return if (target == null) {
            WearComplicationContent.Empty
        } else {
            WearComplicationContent.Value(
                shortText = target.caption,
                longText = target.caption,
                contentDescription = "Last resource: ${target.caption}",
                launchTarget = WearLaunchTarget.Open(target.ref)
            )
        }
    }

    /** Null means the newest entry no longer addresses anything this watch can open. */
    private suspend fun lastResourceTarget(newest: LastUsedResource): LastResourceTarget? =
        when (newest.kind) {
            // The channel's caption comes from the entry, which the resolver already refreshed from
            // the live catalog row - a second lookup here would answer the same thing.
            LastUsedKind.STREAM -> LastResourceTarget(newest.name, WearTileTargetRef.Stream(newest.id))

            // A source's caption is read from the store rather than from the entry, which is what
            // keeps a renamed source current on the complication.
            LastUsedKind.RESOURCE -> networkSourceRepository.getAllSources()
                .find { it.id == newest.id }
                ?.let { source ->
                    LastResourceTarget(
                        caption = source.name,
                        ref = WearTileTargetRef.Resource(
                            id = source.id,
                            type = source.type,
                            server = source.server,
                            port = source.port,
                            shareName = source.shareName,
                            basePath = source.basePath
                        )
                    )
                }
        }

    private data class LastResourceTarget(val caption: String, val ref: WearTileTargetRef)

    private suspend fun loadFavouritesCountContent(): WearComplicationContent {
        val favorites = wearFavoritesRepository.getFavorites()
        if (favorites.isEmpty()) return WearComplicationContent.Empty

        val count = favorites.size
        return WearComplicationContent.Value(
            shortText = count.toString(),
            longText = "$count favourites",
            contentDescription = "$count favourites",
            launchTarget = WearLaunchTarget.Open(WearTileTargetRef.Favourites)
        )
    }

    private suspend fun loadNowPlayingContent(): WearComplicationContent {
        val nowPlaying = wearNowPlayingRepository.nowPlaying.first()
        if (!nowPlaying.hasContent) return WearComplicationContent.Empty

        val shortText = nowPlaying.title
        val longText = if (nowPlaying.subtitle.isNullOrBlank()) {
            nowPlaying.title
        } else {
            "${nowPlaying.title} - ${nowPlaying.subtitle}"
        }
        val description = if (nowPlaying.isPlaying) {
            "Playing: $shortText"
        } else {
            "Last played: $shortText"
        }

        return WearComplicationContent.Value(
            shortText = shortText,
            longText = longText,
            contentDescription = description,
            launchTarget = null
        )
    }
}
