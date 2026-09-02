package com.sza.fastmediasorter.testing.fakes

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.model.FavoritesRemapOutcome
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written fake for [FavoritesRepository].
 *
 * Backing state is an in-memory list of [FavoritesEntity] exposed via [favoritesFlow]. Reactive
 * `isFavorite` derives from the same flow so observers see live updates. Adds/removes are recorded
 * for assertions. No real I/O, no logging.
 */
class FakeFavoritesRepository : FavoritesRepository {

    private val favoritesFlow = MutableStateFlow<List<FavoritesEntity>>(emptyList())

    /** Snapshot of the current favorites. */
    val favorites: List<FavoritesEntity>
        get() = favoritesFlow.value

    // Invocation records ---------------------------------------------------
    val addedFavorites: MutableList<FavoritesEntity> = mutableListOf()
    val removedUris: MutableList<String> = mutableListOf()
    val removedIds: MutableList<Long> = mutableListOf()
    private var nextId: Long = 1L

    /** Seed the backing list directly. */
    fun setFavorites(items: List<FavoritesEntity>) {
        favoritesFlow.value = items
    }

    override fun getAllFavorites(): Flow<List<FavoritesEntity>> = favoritesFlow

    /** Same file-only slice the real query applies, so a seeded STREAM row is filtered here too. */
    override fun getFileFavorites(): Flow<List<FavoritesEntity>> =
        favoritesFlow.map { list -> list.filter { it.kind == FavoritesEntity.KIND_FILE } }

    override fun isFavorite(uri: String): Flow<Boolean> =
        favoritesFlow.map { list -> list.any { it.uri == uri } }

    override suspend fun isFavoriteSync(uri: String): Boolean =
        favoritesFlow.value.any { it.uri == uri }

    override suspend fun getFavoritesForPaths(paths: List<String>): Map<String, Boolean> {
        val favoriteUris = favoritesFlow.value.map { it.uri }.toSet()
        return paths.associateWith { it in favoriteUris }
    }

    override suspend fun addFavorite(entity: FavoritesEntity) {
        addedFavorites.add(entity)
        val stored = if (entity.id != 0L) entity else entity.copy(id = nextId++)
        favoritesFlow.value = favoritesFlow.value.filterNot { it.uri == stored.uri } + stored
    }

    override suspend fun removeFavorite(uri: String) {
        removedUris.add(uri)
        favoritesFlow.value = favoritesFlow.value.filterNot { it.uri == uri }
    }

    override suspend fun removeFavoriteById(id: Long) {
        removedIds.add(id)
        favoritesFlow.value = favoritesFlow.value.filterNot { it.id == id }
    }

    /**
     * S2370: the real remap resolves each rewritten address against the picked tree, which needs a
     * platform document provider. This fake rewrites the prefix and reports every row as remapped -
     * enough for a caller assertion, and never a stand-in for the existence half of the contract.
     */
    override suspend fun remapResourceFavoritesToTree(
        resourceId: Long,
        oldPathPrefix: String,
        treeUriString: String,
    ): FavoritesRemapOutcome {
        val rows = favoritesFlow.value.filter { it.resourceId == resourceId }
        val matching = rows.filter { it.uri.startsWith(oldPathPrefix) }
        favoritesFlow.value = favoritesFlow.value.map { row ->
            if (row in matching) {
                row.copy(
                    uri = treeUriString + row.uri.removePrefix(oldPathPrefix),
                    lastKnownPath = row.uri,
                )
            } else {
                row
            }
        }
        return FavoritesRemapOutcome(
            total = rows.size,
            remapped = matching.size,
            keptMissing = 0,
            untouched = rows.size - matching.size,
        )
    }
}
