package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.util.StreamChannelIdentity
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SyntheticResourceIds
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
     * S0783: the set of favorited channels, so the streams UI can label its per-channel action "add" vs
     * "remove". Only STREAM rows are surfaced; file URIs never collide with channel URLs but are excluded
     * to keep the set small.
     *
     * S1842: the set holds channel IDENTITIES, not stored URLs. A catalog that re-publishes a channel
     * under a cosmetically different address - http against https, a trailing slash, host case, an
     * explicit default port - used to stop matching the stored favourite, and the star went dark while
     * the row sat there intact. Callers compare against StreamSourceEntity.identityKey, which the
     * catalog row already carries, so no call site recomputes the rule.
     * "add" vs "remove". Only STREAM rows are surfaced; file URIs never collide with channel URLs but
     * are excluded to keep the set small.
     */
    fun observeFavoriteStreamIdentities(): Flow<Set<String>> =
        favoritesRepository.getAllFavorites().map { list ->
            list.asSequence()
                .filter { it.kind == FavoritesEntity.KIND_STREAM }
                .map { StreamChannelIdentity.of(it.uri) }
                .toSet()
        }

    /**
     * S0783: add or remove a live channel from the shared favorites. Independent of the streams pin. A
     * STREAM favorites row carries the channel URL, title and media kind so the favorites screen can list
     * it by name and open it in the stream player.
     *
     * S1842: which row to remove is decided by channel identity, not by string equality on the URL.
     * Fixing only the read side would light the star and then let this method add a SECOND row instead of
     * removing the first, because the add/remove decision was its own byte-comparison.
     *
     * The stored `uri` stays the real, launchable address and never the identity: the identity replaces
     * http/https with a `web` token, and this same column is what the favorites screen opens.
     * the streams pin. A STREAM favorites row carries the channel URL, title and media kind so the
     * favorites screen can list it by name and open it in the stream player.
     */
    /**
     * S1842: the identity a channel row is matched by. Prefers the key the catalog write path already
     * derived (StreamSourceRepository.withIdentity), and falls back to deriving it when that column is
     * still empty - the column defaults to "" and a row that never went through a write path would
     * otherwise match nothing and put the star back into exactly the dark state this ticket removes.
     *
     * Lives here so the rule has one home: no comparison site recomputes it.
     */
    fun channelIdentity(source: StreamSourceEntity): String = StreamChannelIdentity.ofSource(source)

    suspend fun toggleStreamFavorite(source: StreamSourceEntity) {
        val identity = StreamChannelIdentity.of(source.url)
        val stored = favoritesRepository.getAllFavorites().first()
            .firstOrNull { it.kind == FavoritesEntity.KIND_STREAM && StreamChannelIdentity.of(it.uri) == identity }
        if (stored != null) {
            favoritesRepository.removeFavorite(stored.uri)
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
