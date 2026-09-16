package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.port.MediaAddressResolver
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * S3161: removes the favourites the watch delta wrote under an address only the watch can resolve.
 *
 * `ApplyWatchFavoritesDeltaUseCase` now refuses such an item, but that refusal cannot reach a row
 * already in the table - nothing rewrites a favourite that is already there, and the owner's phone
 * carries rows of this shape from earlier builds.
 *
 * Two conditions must BOTH hold before a row is deleted. The shape test alone would be a guess, and
 * the resolver alone would delete a favourite whose file the owner merely moved or deleted - a row
 * they made deliberately and may still want to see. Together they name only a row this app itself
 * fabricated and that points at nothing.
 */
class PruneWatchLocalFavoritesUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val mediaAddressResolver: MediaAddressResolver
) {

    /** Returns how many rows were removed. */
    suspend operator fun invoke(): Int {
        val candidates = favoritesRepository.getFileFavoritesSync().filter(::isWatchDeltaFavoriteShape)
        Timber.d("S3161: watch-written favourite prune, ${candidates.size} candidate row(s)")
        if (candidates.isEmpty()) return 0

        var removed = 0
        for (row in candidates) {
            if (mediaAddressResolver.exists(row.uri)) continue
            favoritesRepository.removeFavoriteById(row.id)
            removed++
        }
        if (removed > 0) {
            Timber.i("PruneWatchLocalFavorites: removed %d unresolvable watch-written row(s)", removed)
        }
        return removed
    }
}

/**
 * Every sign of a row built by `ApplyWatchFavoritesDeltaUseCase` from a bare path, and nothing else.
 *
 * A favourite the phone itself made carries the file's real name, its size and its media type, so it
 * fails this test on the first field even when its file has since disappeared.
 */
internal fun isWatchDeltaFavoriteShape(entity: FavoritesEntity): Boolean =
    entity.kind == FavoritesEntity.KIND_FILE &&
        entity.resourceId == 0L &&
        entity.size == 0L &&
        entity.mediaType == 0 &&
        entity.uri.startsWith(MEDIA_CONTENT_PREFIX) &&
        entity.lastKnownPath == entity.uri &&
        entity.displayName == entity.uri.substringAfterLast('/')

/** The only address family a watch MediaStore row can arrive under. */
private const val MEDIA_CONTENT_PREFIX = "content://media/"
