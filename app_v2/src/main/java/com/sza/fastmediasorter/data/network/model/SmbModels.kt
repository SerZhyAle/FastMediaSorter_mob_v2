package com.sza.fastmediasorter.data.network.model

/**
 * Data class for SMB connection parameters
 */
data class SmbConnectionInfo(
    val server: String,
    val shareName: String,
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val port: Int = 445
)

/**
 * Data class for file information
 */
data class SmbFileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

/**
 * Result wrapper for operations
 */
sealed class SmbResult<out T> {
    data class Success<T>(val data: T) : SmbResult<T>()
    data class Error(val message: String, val exception: Exception? = null) : SmbResult<Nothing>()
}

/**
 * Key used for identifying unique connections in the pool
 */
data class ConnectionKey(
    val server: String,
    val port: Int,
    val shareName: String,
    val username: String,
    val domain: String
)
