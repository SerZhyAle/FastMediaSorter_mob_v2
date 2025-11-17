package com.sza.fastmediasorter_v2.data.local.db

import androidx.room.*
import com.sza.fastmediasorter_v2.data.cloud.CloudProvider
import com.sza.fastmediasorter_v2.domain.model.DisplayMode
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.model.SortMode

@Entity(
    tableName = "resources",
    indices = [
        Index(value = ["displayOrder", "name"], name = "idx_resources_display_order_name"),
        Index(value = ["type", "displayOrder", "name"], name = "idx_resources_type_display_order_name"),
        Index(value = ["isDestination", "destinationOrder"], name = "idx_resources_is_destination_order")
    ]
)
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
    
    val isAvailable: Boolean = true, // Resource availability indicator
    
    val showCommandPanel: Boolean? = null, // User preference: null = use global default, true/false = override
    
    val createdDate: Long = System.currentTimeMillis(),
    
    val lastBrowseDate: Long? = null, // Last time resource was opened in BrowseActivity
    
    val lastSyncDate: Long? = null, // Last time network resource was synced (for SMB/SFTP/FTP only)
    
    val displayOrder: Int = 0 // Order for display in resource list
)
