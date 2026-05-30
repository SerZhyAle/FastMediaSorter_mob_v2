package com.sza.fastmediasorter.data.local.db

import androidx.room.*
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode

@Entity(
    tableName = "resources",
    indices = [
        Index(value = ["displayOrder", "name"], name = "idx_resources_display_order_name"),
        Index(value = ["type", "displayOrder", "name"], name = "idx_resources_type_display_order_name"),
        Index(value = ["isDestination", "destinationOrder"], name = "idx_resources_is_destination_order"),
        Index(value = ["supportedMediaTypesFlags"], name = "idx_resources_media_types"),
        Index(value = ["credentialsId"], name = "idx_resources_credentials_id"),
        Index(value = ["cloudProvider"], name = "idx_resources_cloud_provider")
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
    
    val supportedMediaTypesFlags: Int = 0b1111, // Binary flags for IMAGE(1), VIDEO(2), AUDIO(4), GIF(8), TEXT(16), PDF(32), EPUB(64), OFFICE_DOCUMENT(128)
    val sortMode: SortMode = SortMode.NAME_ASC,
    val displayMode: DisplayMode = DisplayMode.LIST,
    
    val lastViewedFile: String? = null,
    val lastScrollPosition: Int = 0, // First visible item position in RecyclerView
    val fileCount: Int = 0,
    val subfolderCount: Int = 0, // Number of subfolders in resource root (from last testConnection)
    val lastAccessedDate: Long = System.currentTimeMillis(),
    
    val slideshowInterval: Int = 10,
    
    val isDestination: Boolean = false,
    val destinationOrder: Int = -1,
    val destinationColor: Int = 0xFF4CAF50.toInt(), // Default green color
    val isWritable: Boolean = false,
    
    val isReadOnly: Boolean = false, // Read-only mode: prevents file operations (copy, move, delete)
    
    val isAvailable: Boolean = true, // Resource availability indicator
    
    val showCommandPanel: Boolean? = null, // User preference: null = use global default, true/false = override
    
    val createdDate: Long = System.currentTimeMillis(),
    
    val lastBrowseDate: Long? = null, // Last time resource was opened in BrowseActivity
    
    val lastSyncDate: Long? = null, // Last time network resource was synced (for SMB/SFTP/FTP only)
    
    val scanSubdirectories: Boolean = false, // Scan subdirectories for media files (default: disabled)
    
    val disableThumbnails: Boolean = false, // Disable thumbnail loading (use extension icons only). Auto-enabled for >10000 files.
    
    val allFiles: Boolean = false, // Show all files in folder (not just media). Auto-enables all media types when ON.
    
    val showHiddenFiles: Boolean = false, // Show hidden files and folders (those starting with a dot). Depends on allFiles being ON.
    
    val showSubfoldersAsItems: Boolean = false, // Show subfolders as separate clickable items in Browse. Only works when scanSubdirectories is enabled.
    
    val displayOrder: Int = 0, // Order for display in resource list
    
    val accessPin: String? = null, // PIN code to access the resource (null = no PIN protection)
    
    val comment: String? = null, // User comment for the resource
    
    @ColumnInfo(name = "read_speed_mbps")
    val readSpeedMbps: Double? = null,
    
    @ColumnInfo(name = "write_speed_mbps")
    val writeSpeedMbps: Double? = null,
    
    @ColumnInfo(name = "recommended_threads")
    val recommendedThreads: Int? = null,
    
    @ColumnInfo(name = "last_speed_test_date")
    val lastSpeedTestDate: Long? = null,

    val rememberFileList: Boolean = false,

    @ColumnInfo(name = "profile", defaultValue = "NONE")
    val profile: String = "NONE", // ResourceProfile.name - quick-setup preset applied at creation

    @ColumnInfo(name = "icon_id", defaultValue = "NULL")
    val iconId: String? = null, // Format: ico-XX-NNN (XX=set 01..99, NNN=ordinal 001..999); null = unassigned legacy row

    @ColumnInfo(name = "host_key_fingerprint", defaultValue = "NULL")
    val hostKeyFingerprint: String? = null, // S0046: SHA256 fingerprint of expected SFTP host key; null = permissive

    @ColumnInfo(name = "needs_sign_in", defaultValue = "0")
    val needsSignIn: Boolean = false // S0200: Drive resource is in "requires sign-in" state (set by S0200AuthStateWipe; cleared on successful sign-in via clearNeedsSignInForCredentials)
)
