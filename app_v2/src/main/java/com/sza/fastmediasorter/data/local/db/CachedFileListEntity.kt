package com.sza.fastmediasorter.data.local.db

import androidx.room.*

@Entity(
    tableName = "cached_file_lists",
    indices = [
        Index(value = ["resourceId"], name = "idx_cached_files_resource_id")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["resourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CachedFileListEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val resourceId: Long,
    
    @ColumnInfo(name = "media_file_json")
    val mediaFileJson: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
