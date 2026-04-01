package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.DuplicateHashCacheDao
import com.sza.fastmediasorter.data.local.db.DuplicateHashCacheEntity
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.DuplicateHashRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DuplicateHashRepositoryImpl @Inject constructor(
    private val dao: DuplicateHashCacheDao
) : DuplicateHashRepository {

    // MediaFile.resourceId is nullable; use 0L for files without a resource (local legacy).
    // lastModified is used as the cache-invalidation timestamp.

    override suspend fun getCachedQuickHash(file: MediaFile): String? {
        val rid = file.resourceId ?: 0L
        return dao.getByKey(rid, file.path, file.lastModified, file.size)?.quickHash
    }

    override suspend fun getCachedFullHash(file: MediaFile): String? {
        val rid = file.resourceId ?: 0L
        return dao.getByKey(rid, file.path, file.lastModified, file.size)?.fullHash
    }

    override suspend fun saveQuickHash(file: MediaFile, hash: String) {
        val rid = file.resourceId ?: 0L
        val now = System.currentTimeMillis()
        val existing = dao.getByKey(rid, file.path, file.lastModified, file.size)
        val entity = existing?.copy(quickHash = hash, cachedAt = now)
            ?: DuplicateHashCacheEntity(
                resourceId = rid,
                filePath = file.path,
                lastModified = file.lastModified,
                fileSize = file.size,
                quickHash = hash,
                fullHash = null,
                cachedAt = now
            )
        dao.upsert(entity)
    }

    override suspend fun saveFullHash(file: MediaFile, hash: String) {
        val rid = file.resourceId ?: 0L
        val now = System.currentTimeMillis()
        val existing = dao.getByKey(rid, file.path, file.lastModified, file.size)
        val entity = existing?.copy(fullHash = hash, cachedAt = now)
            ?: DuplicateHashCacheEntity(
                resourceId = rid,
                filePath = file.path,
                lastModified = file.lastModified,
                fileSize = file.size,
                quickHash = null,
                fullHash = hash,
                cachedAt = now
            )
        dao.upsert(entity)
    }

    override suspend fun deleteByResourceId(resourceId: Long) {
        dao.deleteByResourceId(resourceId)
    }

    override suspend fun deleteHashEntry(file: MediaFile) {
        val rid = file.resourceId ?: 0L
        dao.deleteByResourceAndPath(rid, file.path)
    }
}
