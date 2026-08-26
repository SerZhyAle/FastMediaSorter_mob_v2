package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileContent
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.model.findByTargetRef
import com.sza.fastmediasorter.wear.domain.model.normalizeWearStreamUrl
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.repository.WearTileAssignmentRepository
import javax.inject.Inject

/**
 * S1955: reads the content for a Wear OS tile from local repositories only.
 *
 * ADR-2: no network call, no Data Layer call to phone, no Wearable clients.
 */
class LoadWearTileContentUseCase @Inject constructor(
    private val tileAssignmentRepository: WearTileAssignmentRepository,
    private val networkSourceRepository: NetworkSourceRepository,
    private val wearStreamChannelRepository: WearStreamChannelRepository,
    private val wearFavoritesRepository: WearFavoritesRepository
) {
    suspend operator fun invoke(kind: WearTileKind): WearTileContent = when (kind) {
        WearTileKind.RESOURCE -> loadResourceContent()
        WearTileKind.STREAM -> loadStreamContent()
        WearTileKind.FAVOURITES -> loadFavouritesContent()
    }

    private suspend fun loadResourceContent(): WearTileContent {
        val assignment = tileAssignmentRepository.assignmentFor(WearTileKind.RESOURCE)
            as? WearTileTargetRef.Resource

        return when {
            assignment == null -> WearTileContent.Unassigned(WearTileKind.RESOURCE)
            else -> {
                val sources = networkSourceRepository.getAllSources()
                val source = sources.findByTargetRef(assignment)
                if (source == null) {
                    WearTileContent.TargetMissing(WearTileKind.RESOURCE)
                } else {
                    WearTileContent.Assigned(
                        title = source.name,
                        subtitle = source.server,
                        iconResId = null,
                        launchTarget = WearLaunchTarget.Open(assignment)
                    )
                }
            }
        }
    }

    private suspend fun loadStreamContent(): WearTileContent {
        val assignment = tileAssignmentRepository.assignmentFor(WearTileKind.STREAM)
            as? WearTileTargetRef.Stream

        return when {
            assignment == null -> WearTileContent.Unassigned(WearTileKind.STREAM)
            else -> {
                val channels = wearStreamChannelRepository.getAllChannels()
                val channel = channels.firstOrNull { channel ->
                    normalizeWearStreamUrl(channel.url) == assignment.normalizedUrl
                }
                if (channel == null) {
                    WearTileContent.TargetMissing(WearTileKind.STREAM)
                } else {
                    WearTileContent.Assigned(
                        title = channel.name,
                        subtitle = channel.url,
                        iconResId = null,
                        launchTarget = WearLaunchTarget.Open(assignment)
                    )
                }
            }
        }
    }

    private suspend fun loadFavouritesContent(): WearTileContent {
        val favorites = wearFavoritesRepository.getFavorites()
        return if (favorites.isEmpty()) {
            WearTileContent.FavouritesEmpty
        } else {
            val entries = favorites.map { record ->
                record.displayName
            }
            WearTileContent.Assigned(
                title = "Favourites",
                subtitle = null,
                iconResId = null,
                launchTarget = WearLaunchTarget.Open(WearTileTargetRef.Favourites),
                entries = entries
            )
        }
    }
}
