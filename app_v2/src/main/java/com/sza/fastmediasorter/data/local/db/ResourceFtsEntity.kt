package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "resources_fts")
@Fts4(contentEntity = ResourceEntity::class)
data class ResourceFtsEntity(
    val name: String,
    val path: String
)
