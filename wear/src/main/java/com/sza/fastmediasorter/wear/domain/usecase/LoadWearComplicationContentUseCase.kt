package com.sza.fastmediasorter.wear.domain.usecase

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

    private suspend fun loadLastResourceContent(): WearComplicationContent {
        val lastUsedId = resolveLastUsedResourceUseCase().first().firstOrNull()?.id
        val source = lastUsedId?.let { id -> networkSourceRepository.getAllSources().find { it.id == id } }
            ?: return WearComplicationContent.Empty

        val targetRef = WearTileTargetRef.Resource(
            id = source.id,
            type = source.type,
            server = source.server,
            port = source.port,
            shareName = source.shareName,
            basePath = source.basePath
        )
        return WearComplicationContent.Value(
            shortText = source.name,
            longText = source.name,
            contentDescription = "Last resource: ${source.name}",
            launchTarget = WearLaunchTarget.Open(targetRef)
        )
    }

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
