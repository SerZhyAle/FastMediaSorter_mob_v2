package com.sza.fastmediasorter.domain.repository

interface PlaybackPositionRepository {
    /**
     * Get saved playback position for file.
     * Returns null if no position saved, or 0 if file was completed (>95% watched).
     */
    suspend fun getPosition(filePath: String): Long?
    
    /**
     * Save playback position for file.
     * Automatically marks as completed if position > 95% of duration.
     */
    suspend fun savePosition(filePath: String, position: Long, duration: Long)
    
    /**
     * Mark file as completed (watched to the end).
     */
    suspend fun markAsCompleted(filePath: String)
    
    /**
     * Delete saved position for file.
     */
    suspend fun deletePosition(filePath: String)
    
    /**
     * Cleanup positions by count limit.
     * Enforces maximum count limit (10000 positions, trims to 9000).
     */
    suspend fun cleanupOldPositions()
    
    /**
     * Delete all saved positions (for cache clear).
     */
    suspend fun deleteAllPositions()
}
