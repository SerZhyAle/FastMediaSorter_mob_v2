package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S0783: maps favorites rows into browse [MediaFile]s. STREAM rows re-resolve their display name from
 * the live streams catalog by URL (falling back to the stored snapshot when the channel is no longer in
 * the catalog), so the favorites list shows the channel's current catalog title instead of a stale value
 * such as the URL tail. FILE rows are unchanged. Shared by both favorites-materialization sites
 * ([GetMediaFilesUseCase] virtual-resource branch and BrowseStateSyncManager) so the two cannot drift.
 *
 * The catalog list additionally de-dupes a redundant `head (head)` parenthetical (ui StreamTitleFormatter
 * / S0691); that ui-only nicety is intentionally not mirrored here to keep this use case free of a
 * ui-layer dependency - the catalog title is authoritative and this is the domain layer.
 */
class MaterializeFavoritesUseCase @Inject constructor(
    private val observeStreamSources: ObserveStreamSourcesUseCase,
) {
    suspend fun toMediaFiles(favorites: List<FavoritesEntity>): List<MediaFile> {
        // Batch-load the catalog once (url -> title) so N stream favorites are not N point queries.
        val streamFavorites = favorites.count { it.kind == FavoritesEntity.KIND_STREAM }
        val catalogTitles: Map<String, String> =
            if (streamFavorites > 0) {
                observeStreamSources().first().associate { it.url to it.title }
            } else {
                emptyMap()
            }
        return favorites.map { entity ->
            MediaFile(
                path = entity.uri,
                name = entity.resolveName(catalogTitles),
                type = MediaType.entries.getOrElse(entity.mediaType) { MediaType.IMAGE },
                size = entity.size,
                createdDate = entity.dateModified,
                isFavorite = true,
                resourceId = entity.resourceId,
                width = 0,
                height = 0,
                duration = 0,
            )
        }
    }

    private fun FavoritesEntity.resolveName(catalogTitles: Map<String, String>): String =
        if (kind == FavoritesEntity.KIND_STREAM) catalogTitles[uri] ?: displayName else displayName
}
