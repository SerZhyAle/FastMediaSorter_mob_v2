package com.sza.fastmediasorter_v2.data.local.db

import androidx.room.*
import com.sza.fastmediasorter_v2.data.cloud.CloudProvider
import com.sza.fastmediasorter_v2.domain.model.DisplayMode
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.model.SortMode

@Entity(tableName = "resources")
data class ResourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,
    val path: String,
    val type: ResourceType,
    val credentialsId: String? = null,
    
    // For CLOUD type resources
    val cloudProvider: CloudProvider? = null,
    val cloudFolderId: String? = null, // Cloud-specific folder ID (for Drive/OneDrive/Dropbox)
    
    val supportedMediaTypesFlags: Int = 0b1111, // Binary flags for IMAGE, VIDEO, AUDIO, GIF
    val sortMode: SortMode = SortMode.NAME_ASC,
    val displayMode: DisplayMode = DisplayMode.LIST,
    
    val lastViewedFile: String? = null,
    val fileCount: Int = 0,
    val lastAccessedDate: Long = System.currentTimeMillis(),
    
    val slideshowInterval: Int = 10,
    
    val isDestination: Boolean = false,
    val destinationOrder: Int = -1,
    val destinationColor: Int = 0xFF4CAF50.toInt(), // Default green color
    val isWritable: Boolean = false,
    
    val createdDate: Long = System.currentTimeMillis(),
    
    val displayOrder: Int = 0 // Order for display in resource list
)
