package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SyntheticResourceIds
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FavoritesUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val statsSink: StatsSink,
) {
    fun getAllFavorites(): Flow<List<FavoritesEntity>> {
        return favoritesRepository.getAllFavorites()
    }

    fun isFavorite(uri: String): Flow<Boolean> {
        return favoritesRepository.isFavorite(uri)
    }
    
    suspend fun isFavoriteSync(uri: String): Boolean {
        return favoritesRepository.isFavoriteSync(uri)
    }

    suspend fun getFavoritesForPaths(paths: List<String>): Map<String, Boolean> {
        return favoritesRepository.getFavoritesForPaths(paths)
    }

    suspend fun toggleFavorite(mediaFile: MediaFile, resourceId: Long) {
        timber.log.Timber.d("FavoritesUseCase.toggleFavorite: START - file='${mediaFile.name}', path='${mediaFile.path}', resourceId=$resourceId")
        val isFav = favoritesRepository.isFavoriteSync(mediaFile.path)
        timber.log.Timber.d("FavoritesUseCase.toggleFavorite: Current status - isFavorite=$isFav")
        
        if (isFav) {
            timber.log.Timber.d("FavoritesUseCase.toggleFavorite: Removing from favorites")
            favoritesRepository.removeFavorite(mediaFile.path)
            timber.log.Timber.d("FavoritesUseCase.toggleFavorite: REMOVED successfully")
            statsSink.record(StatsEvent.Favorite(added = false))
        } else {
            timber.log.Timber.d("FavoritesUseCase.toggleFavorite: Adding to favorites")
            val entity = FavoritesEntity(
                uri = mediaFile.path,
                resourceId = resourceId,
                displayName = mediaFile.name,
                mediaType = mediaFile.type.ordinal,
                size = mediaFile.size,
                lastKnownPath = mediaFile.path,
                dateModified = mediaFile.createdDate
            )
            favoritesRepository.addFavorite(entity)
            timber.log.Timber.d("FavoritesUseCase.toggleFavorite: ADDED successfully - entity.uri='${entity.uri}', entity.resourceId=${entity.resourceId}")
            statsSink.record(StatsEvent.Favorite(added = true))
        }
    }

    /**
     * S0783: the set of favorited channel URLs, so the streams UI can label its per-channel action
     * "add" vs "remove". Only STREAM rows are surfaced; file URIs never collide with channel URLs but
     * are excluded to keep the set small.
     */
    fun observeFavoriteStreamUrls(): Flow<Set<String>> =
        favoritesRepository.getAllFavorites().map { list ->
            list.asSequence()
                .filter { it.kind == FavoritesEntity.KIND_STREAM }
                .map { it.uri }
                .toSet()
        }

    /**
     * S0783: add or remove a live channel from the shared favorites, keyed by its URL. Independent of
     * the streams pin. A STREAM favorites row carries the channel URL, title and media kind so the
     * favorites screen can list it by name and open it in the stream player.
     */
    suspend fun toggleStreamFavorite(source: StreamSourceEntity) {
        timber.log.Timber.d("S0783: toggleStreamFavorite url=${source.url} kind=${source.mediaKind}")
        if (favoritesRepository.isFavoriteSync(source.url)) {
            favoritesRepository.removeFavorite(source.url)
            statsSink.record(StatsEvent.Favorite(added = false))
        } else {
            val entity = FavoritesEntity(
                uri = source.url,
                resourceId = SyntheticResourceIds.STREAM,
                displayName = source.title,
                mediaType = streamFavoriteMediaType(source.mediaKind).ordinal,
                size = 0L,
                lastKnownPath = source.url,
                dateModified = source.addedAt,
                kind = FavoritesEntity.KIND_STREAM,
                streamMediaKind = source.mediaKind,
            )
            favoritesRepository.addFavorite(entity)
            statsSink.record(StatsEvent.Favorite(added = true))
        }
    }

    // Audio channels map to AUDIO; VIDEO and RTSP both render/open as video.
    private fun streamFavoriteMediaType(mediaKind: String): MediaType =
        if (mediaKind == "AUDIO") MediaType.AUDIO else MediaType.VIDEO
}
