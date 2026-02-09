package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["uri"], unique = true, name = "idx_favorites_uri"),
        Index(value = ["addedTimestamp"], name = "idx_favorites_addedTimestamp")
    ]
)
data class FavoritesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val uri: String, // Full path or URI of the file
    val resourceId: Long, // ID of the resource this file belongs to (for credentials/context)
    val displayName: String,
    val mediaType: Int, // Media type flag (1=Image, 2=Video, etc) - keeping consistent with other parts
    val size: Long,
    val lastKnownPath: String, // Redundant with uri but good for fallback if URI scheme changes
    val dateModified: Long, // File modification date
    val addedTimestamp: Long = System.currentTimeMillis() // When it was favorited
)
