package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface StereoFormatOverrideDao {

    @Query("SELECT * FROM stereo_format_overrides WHERE filePath = :filePath LIMIT 1")
    suspend fun getEntry(filePath: String): StereoFormatOverrideEntity?

    @Upsert
    suspend fun upsert(entry: StereoFormatOverrideEntity)

    @Query("DELETE FROM stereo_format_overrides WHERE filePath = :filePath")
    suspend fun deleteEntry(filePath: String)
}