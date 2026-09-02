package com.sza.fastmediasorter.data.repository

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.model.FavoritesRemapOutcome
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import com.sza.fastmediasorter.utils.SafHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoritesDao: FavoritesDao
) : FavoritesRepository {
    companion object {
        private const val SQLITE_IN_CLAUSE_LIMIT = 900
    }

    override fun getAllFavorites(): Flow<List<FavoritesEntity>> {
        // Room invalidates at table granularity; dedup so an unrelated favorites write doesn't re-render unchanged rows (S0733/S0717 P3).
        return favoritesDao.getAllFavorites().distinctUntilChanged()
    }

    override fun getFileFavorites(): Flow<List<FavoritesEntity>> {
        return favoritesDao.getFileFavorites().distinctUntilChanged()
    }

    override fun isFavorite(uri: String): Flow<Boolean> {
        return favoritesDao.isFavorite(uri).distinctUntilChanged()
    }

    override suspend fun isFavoriteSync(uri: String): Boolean {
        return favoritesDao.isFavoriteSync(uri)
    }

    override suspend fun getFavoritesForPaths(paths: List<String>): Map<String, Boolean> {
        if (paths.isEmpty()) return emptyMap()

        val favoriteUris = mutableSetOf<String>()
        paths.distinct().chunked(SQLITE_IN_CLAUSE_LIMIT).forEach { chunk ->
            favoriteUris += favoritesDao.getFavoriteUrisForPaths(chunk)
        }

        return paths.associateWith { it in favoriteUris }
    }

    override suspend fun addFavorite(entity: FavoritesEntity) {
        favoritesDao.insert(entity)
    }

    override suspend fun removeFavorite(uri: String) {
        favoritesDao.deleteByUri(uri)
    }

    override suspend fun removeFavoriteById(id: Long) {
        favoritesDao.deleteById(id)
    }

    override suspend fun remapResourceFavoritesToTree(
        resourceId: Long,
        oldPathPrefix: String,
        treeUriString: String,
    ): FavoritesRemapOutcome = withContext(Dispatchers.IO) {
        val rows = favoritesDao.getFavoritesForResource(resourceId)
        if (rows.isEmpty()) return@withContext FavoritesRemapOutcome(0, 0, 0, 0)

        val treeRoot = SafHelper.getTreeRoot(context, treeUriString)
            ?: return@withContext FavoritesRemapOutcome(rows.size, 0, rows.size, 0)
        // A trailing separator would make every relative path lose its leading segment boundary, so the
        // base is normalised once rather than at each comparison.
        val base = oldPathPrefix.trimEnd('/')

        var remapped = 0
        var keptMissing = 0
        var untouched = 0
        val changed = mutableListOf<FavoritesEntity>()

        for (row in rows) {
            // Segment boundary, not a bare prefix: "/sdcard/Download" must not swallow
            // "/sdcard/Downloads2/x.txt", which shares the prefix but is a different folder.
            val matchesPrefix = row.uri == base || row.uri.startsWith("$base/")
            val updated = if (matchesPrefix) remapRowToTree(row, base, treeRoot) else null
            when {
                !matchesPrefix -> untouched++
                // S2370: the file is gone from the new root, but the favorite row is not - it keeps
                // its old address and the lastKnownPath fallback, and is reported by count.
                updated == null -> keptMissing++
                else -> {
                    changed += updated
                    remapped++
                }
            }
        }

        if (changed.isNotEmpty()) favoritesDao.updateFavorites(changed)
        FavoritesRemapOutcome(total = rows.size, remapped = remapped, keptMissing = keptMissing, untouched = untouched)
    }

    /**
     * The rewritten row for a favorite whose address fell under the old prefix, or null when the
     * target file does not exist under the new tree (the caller keeps and counts such rows).
     *
     * The tree is walked segment by segment rather than having the relative path pasted onto the tree's
     * document id. Only `com.android.externalstorage.documents` shapes its ids as `volume:Rel/Path`;
     * the Downloads provider answers `downloads`, and others `raw:/..` or `msf:123`, so the pasted id
     * would name nothing, every row would miss, and the reconnect would report success having moved
     * no favorite at all.
     */
    private fun remapRowToTree(
        row: FavoritesEntity,
        basePath: String,
        treeRoot: DocumentFile,
    ): FavoritesEntity? {
        val relative = row.uri.removePrefix(basePath).trim('/')
        val target = relative.takeIf { it.isNotEmpty() }?.let { resolveUnderTree(treeRoot, it) }
        return target?.let { row.copy(uri = it.uri.toString(), lastKnownPath = row.uri) }
    }

    /** The document [relative] names under [treeRoot], or null as soon as one segment is missing. */
    private fun resolveUnderTree(treeRoot: DocumentFile, relative: String): DocumentFile? {
        var node: DocumentFile? = treeRoot
        for (segment in relative.split('/')) {
            node = node?.findFile(segment)
        }
        return node?.takeIf { it.exists() }
    }
}
