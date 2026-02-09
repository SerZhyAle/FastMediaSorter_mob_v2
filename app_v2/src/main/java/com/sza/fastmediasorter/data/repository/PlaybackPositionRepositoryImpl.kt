package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.PlaybackPositionDao
import com.sza.fastmediasorter.data.local.db.PlaybackPositionEntity
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackPositionRepositoryImpl @Inject constructor(
    private val dao: PlaybackPositionDao
) : PlaybackPositionRepository {
    
    companion object {
        private const val COMPLETED_THRESHOLD = 0.95 // 95% threshold
        private const val MAX_POSITIONS = 10000 // Maximum number of positions to keep
        private const val TRIM_TO_COUNT = 9000 // Trim to this number when limit exceeded
    }
    
    override suspend fun getPosition(filePath: String): Long? {
        return try {
            dao.getPosition(filePath)?.let { entity ->
                // If file was completed or position > 95%, start from beginning
                if (entity.isCompleted || (entity.duration > 0 && entity.position > entity.duration * COMPLETED_THRESHOLD)) {
                    Timber.d("PlaybackPosition: File completed, starting from beginning: $filePath")
                    0L
                } else {
                    Timber.d("PlaybackPosition: Restored position ${entity.position}ms for $filePath")
                    entity.position
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "PlaybackPosition: Failed to get position for $filePath")
            null
        }
    }
    
    override suspend fun savePosition(filePath: String, position: Long, duration: Long) {
        try {
            val isCompleted = duration > 0 && position > duration * COMPLETED_THRESHOLD
            val entity = PlaybackPositionEntity(
                filePath = filePath,
                position = position,
                duration = duration,
                lastPlayedAt = System.currentTimeMillis(),
                isCompleted = isCompleted
            )
            dao.savePosition(entity)
            Timber.d("PlaybackPosition: Saved position ${position}ms/${duration}ms for $filePath (completed=$isCompleted)")
        } catch (e: Exception) {
            Timber.e(e, "PlaybackPosition: Failed to save position for $filePath")
        }
    }
    
    override suspend fun markAsCompleted(filePath: String) {
        try {
            val existing = dao.getPosition(filePath)
            if (existing != null) {
                dao.savePosition(existing.copy(isCompleted = true))
                Timber.d("PlaybackPosition: Marked as completed: $filePath")
            }
        } catch (e: Exception) {
            Timber.e(e, "PlaybackPosition: Failed to mark as completed: $filePath")
        }
    }
    
    override suspend fun deletePosition(filePath: String) {
        try {
            dao.deletePosition(filePath)
            Timber.d("PlaybackPosition: Deleted position for $filePath")
        } catch (e: Exception) {
            Timber.e(e, "PlaybackPosition: Failed to delete position for $filePath")
        }
    }
    
    override suspend fun cleanupOldPositions() {
        try {
            // Check count limit and trim if exceeded
            val currentCount = dao.getPositionsCount()
            if (currentCount >= MAX_POSITIONS) {
                dao.keepOnlyRecentPositions(TRIM_TO_COUNT)
                Timber.d("PlaybackPosition: Count limit exceeded ($currentCount), trimmed to $TRIM_TO_COUNT")
            }
        } catch (e: Exception) {
            Timber.e(e, "PlaybackPosition: Failed to cleanup old positions")
        }
    }
    
    override suspend fun deleteAllPositions() {
        try {
            dao.deleteAllPositions()
            Timber.d("PlaybackPosition: Deleted all positions")
        } catch (e: Exception) {
            Timber.e(e, "PlaybackPosition: Failed to delete all positions")
        }
    }
}
