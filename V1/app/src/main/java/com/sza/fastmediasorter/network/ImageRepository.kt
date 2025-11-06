package com.sza.fastmediasorter.network

import com.sza.fastmediasorter.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository class responsible for managing image and media file operations over SMB connections.
 * Provides a clean interface for loading media files from network shares, handling authentication,
 * and managing connection lifecycle.
 *
 * This repository abstracts the complexity of SMB protocol interactions and provides
 * coroutine-based asynchronous operations for media file access.
 *
 * @property smbClient The SMB client instance used for network operations
 * @property preferenceManager Manager for accessing user preferences and connection settings
 */
class ImageRepository(
    val smbClient: SmbClient,
    private val preferenceManager: PreferenceManager,
) {
    /**
     * Loads a list of image and video files from the configured SMB share.
     * Handles connection establishment, authentication, and file enumeration.
     * Supports filtering by file type and size limits for videos.
     *
     * @return Result containing the list of file paths on success, or an exception on failure
     */
    suspend fun loadImages(): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                val serverAddress = preferenceManager.getServerAddress()
                var username = preferenceManager.getUsername()
                var password = preferenceManager.getPassword()
                val folderPath = preferenceManager.getFolderPath()

                // Use default credentials if not set
                if (username.isEmpty()) {
                    username = preferenceManager.getDefaultUsername()
                }
                if (password.isEmpty()) {
                    password = preferenceManager.getDefaultPassword()
                }

                if (serverAddress.isEmpty() || folderPath.isEmpty()) {
                    return@withContext Result.failure(Exception("Connection settings not configured"))
                }

                val connected = smbClient.connect(serverAddress, username, password)
                if (!connected) {
                    return@withContext Result.failure(
                        Exception(
                            "Failed to connect to server: $serverAddress\n\nCheck:\n• Server is running?\n• Firewall settings?",
                        ),
                    )
                }

                val isVideoEnabled = preferenceManager.isVideoEnabled()
                val maxVideoSizeMb = preferenceManager.getMaxVideoSizeMb()
                val result = smbClient.getImageFiles(serverAddress, folderPath, isVideoEnabled, maxVideoSizeMb)
                if (result.errorMessage != null) {
                    return@withContext Result.failure(Exception(result.errorMessage))
                }

                // For image loading, warnings are OK - only errors should fail
                // result.warningMessage is informational only

                Result.success(result.files)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Downloads the content of a specific image file from the SMB share.
     *
     * @param imageUrl The SMB URL of the image file to download
     * @return The image data as ByteArray, or null if download failed
     */
    suspend fun downloadImage(imageUrl: String): ByteArray? = smbClient.downloadImage(imageUrl)

    /**
     * Retrieves the current SMB context for advanced operations.
     * The context contains authentication and connection information.
     *
     * @return The CIFSContext if available, null otherwise
     */
    fun getSmbContext(): jcifs.CIFSContext? = smbClient.getContext()

    /**
     * Clears stored credentials and disconnects from the SMB server.
     * Should be called when switching connections or on logout.
     */
    fun clearCredentials() {
        smbClient.disconnect()
    }
}
