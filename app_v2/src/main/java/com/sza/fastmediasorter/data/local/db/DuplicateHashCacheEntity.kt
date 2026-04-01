package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "duplicate_hash_cache",
    indices = [
        Index(value = ["resourceId", "filePath", "lastModified", "fileSize"], unique = true),
        Index(value = ["resourceId"]),
        Index(value = ["cachedAt"])
    ]
)
data class DuplicateHashCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val resourceId: Long,
    val filePath: String,
    val lastModified: Long,
    val fileSize: Long,
    val quickHash: String?,  // MD5 of first 4 KB; null if not yet computed
    val fullHash: String?,   // MD5 of full file; null if not yet computed
    val cachedAt: Long       // epoch ms — for future TTL cleanup
)
