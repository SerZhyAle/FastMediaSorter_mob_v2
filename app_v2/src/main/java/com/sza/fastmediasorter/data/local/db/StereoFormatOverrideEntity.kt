package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists a manual VR stereo override for a specific file path.
 *
 * The key is the fully qualified player path/URI so local, network, cloud,
 * and content-backed files can all reuse the same lookup path without an
 * additional resource mapping layer.
 */
@Entity(
    tableName = "stereo_format_overrides",
    indices = [
        Index(value = ["updatedAt"], name = "idx_sfo_updated_at")
    ]
)
data class StereoFormatOverrideEntity(
    @PrimaryKey
    val filePath: String,
    val stereoModeKey: String,
    val updatedAt: Long,
)