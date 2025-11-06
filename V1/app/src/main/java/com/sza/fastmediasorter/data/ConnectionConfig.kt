package com.sza.fastmediasorter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a connection configuration for media sources.
 * Stores settings for both SMB network connections and local storage folders.
 * Used for managing slideshow intervals, authentication, and folder paths.
 */
@Entity(tableName = "connection_configs")
data class ConnectionConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val serverAddress: String,
    val username: String,
    val password: String,
    val folderPath: String,
    val interval: Int = 10,
    val lastUsed: Long = 0,
    val sortOrder: Int? = null,
    val sortName: String? = null,
    val type: String = "SMB",
    val localUri: String? = null,
    val localDisplayName: String? = null,
    val lastSlideshowIndex: Int = 0,
    val writePermission: Boolean = false,
)
