package com.sza.fastmediasorter_v2.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing network credentials (SMB/SFTP).
 * Credentials are stored separately from resources for security and reusability.
 */
@Entity(tableName = "network_credentials")
data class NetworkCredentialsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val credentialId: String, // Unique identifier (UUID)
    val type: String, // "SMB" or "SFTP"
    val server: String, // Server address or IP
    val port: Int, // Default: 445 for SMB, 22 for SFTP
    val username: String,
    val password: String, // TODO: Encrypt in production
    val domain: String = "", // For SMB domain authentication
    val shareName: String? = null, // For SMB: share name
    val createdDate: Long = System.currentTimeMillis()
)
