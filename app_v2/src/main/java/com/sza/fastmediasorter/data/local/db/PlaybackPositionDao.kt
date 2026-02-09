package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaybackPositionDao {
    
    @Query("SELECT * FROM playback_positions WHERE filePath = :path")
    suspend fun getPosition(path: String): PlaybackPositionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(position: PlaybackPositionEntity)
    
    @Query("DELETE FROM playback_positions WHERE filePath = :path")
    suspend fun deletePosition(path: String)
    
    /**
     * Get total count of saved positions
     */
    @Query("SELECT COUNT(*) FROM playback_positions")
    suspend fun getPositionsCount(): Int
    
    /**
     * Delete oldest positions keeping only the most recent N records
     * @param keepCount number of most recent positions to keep
     */
    @Query("""
        DELETE FROM playback_positions 
        WHERE filePath NOT IN (
            SELECT filePath FROM playback_positions 
            ORDER BY lastPlayedAt DESC 
            LIMIT :keepCount
        )
    """)
    suspend fun keepOnlyRecentPositions(keepCount: Int)
    
    /**
     * Delete all positions (for cache clear)
     */
    @Query("DELETE FROM playback_positions")
    suspend fun deleteAllPositions()
    
    /**
     * Get all positions (for debugging)
     */
    @Query("SELECT * FROM playback_positions ORDER BY lastPlayedAt DESC")
    suspend fun getAllPositions(): List<PlaybackPositionEntity>
}
