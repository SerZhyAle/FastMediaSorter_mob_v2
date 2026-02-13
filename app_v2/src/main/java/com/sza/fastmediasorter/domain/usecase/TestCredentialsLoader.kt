package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.os.Environment
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.data.cloud.CloudProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Loads test credentials from test_credentials.json file.
 * Used for integration testing with real network resources.
 */
class TestCredentialsLoader @Inject constructor(
    private val context: Context
) {
    
    data class TestCredential(
        val type: ResourceType,
        val server: String,
        val port: Int? = null,
        val username: String,
        val password: String,
        val shareName: String? = null,
        val folder: String? = null,
        val domain: String? = null,
        val cloudProvider: CloudProvider? = null  // For distinguishing cloud providers
    )
    
    /**
     * Load test credentials - HARDCODED for reliable testing.
     * No JSON file dependency.
     */
    suspend fun loadCredentials(): List<TestCredential> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Loading hardcoded test credentials")
            
            val credentials = listOf(
                // SMB
                TestCredential(
                    type = ResourceType.SMB,
                    server = "192.168.1.100",
                    port = 445,
                    username = "smbuser",
                    password = "smbpass123",
                    shareName = "test_share",
                    folder = "", // Root folder
                    domain = null
                ),
                // SFTP
                TestCredential(
                    type = ResourceType.SFTP,
                    server = "193.178.50.43",
                    port = 22,
                    username = "sza",
                    password = "SerZhyA25SerZhyA25",
                    shareName = null,
                    folder = "", // Root folder
                    domain = null
                ),
                // FTP
                TestCredential(
                    type = ResourceType.FTP,
                    server = "193.178.50.43",
                    port = 21,
                    username = "sza",
                    password = "SerZhyA25SerZhyA25",
                    shareName = null,
                    folder = "", // Root folder
                    domain = null
                ),
                // Google Drive
                TestCredential(
                    type = ResourceType.CLOUD,
                    server = "drive.google.com",
                    port = null,
                    username = "user@example.com", // Not used for OAuth
                    password = "", // Not used for OAuth
                    shareName = null,
                    folder = "", // Root folder
                    domain = null,
                    cloudProvider = CloudProvider.GOOGLE_DRIVE
                ),
                // OneDrive
                TestCredential(
                    type = ResourceType.CLOUD,
                    server = "onedrive.live.com",
                    port = null,
                    username = "user@example.com", // Not used for OAuth
                    password = "", // Not used for OAuth
                    shareName = null,
                    folder = "", // Root folder
                    domain = null,
                    cloudProvider = CloudProvider.ONEDRIVE
                ),
                // Dropbox
                TestCredential(
                    type = ResourceType.CLOUD,
                    server = "dropbox.com",
                    port = null,
                    username = "user@example.com", // Not used for OAuth
                    password = "", // Not used for OAuth
                    shareName = null,
                    folder = "", // Root folder
                    domain = null,
                    cloudProvider = CloudProvider.DROPBOX
                )
            )
            
            Timber.d("Successfully loaded ${credentials.size} hardcoded credentials")
            credentials
        } catch (e: Exception) {
            Timber.e(e, "Failed to load credentials")
            emptyList()
        }
    }
    
    
    private fun tryLoadFromExternalStorage(): String? {
        return try {
            val externalStoragePath = Environment.getExternalStorageDirectory().path
            // Try multiple locations
            val locations = listOf(
                File("$externalStoragePath/test_media/test_credentials.json"),
                File("$externalStoragePath/Android/data/${context.packageName}/files/test_media/test_credentials.json"),
                File(context.getExternalFilesDir(null), "test_media/test_credentials.json")
            )
            
            for (file in locations) {
                if (file.exists()) {
                    Timber.d("Loading credentials from: ${file.absolutePath}")
                    return file.readText()
                }
            }
            
            Timber.w("test_credentials.json not found in any location")
            null
        } catch (e: Exception) {
            Timber.w("Could not load from external storage: ${e.message}")
            null
        }
    }
    
    private fun tryLoadFromAssets(): String? {
        return try {
            val content = context.assets.open("test_credentials.json").bufferedReader().use { it.readText() }
            Timber.d("Successfully loaded credentials from assets")
            content
        } catch (e: Exception) {
            Timber.w("Could not load from assets: ${e.message}")
            null
        }
    }
    
    private fun parseCredentials(jsonContent: String): List<TestCredential> {
        val credentials = mutableListOf<TestCredential>()
        
        try {
            val json = JSONObject(jsonContent)
            val credentialsArray = json.getJSONArray("credentials")
            
            for (i in 0 until credentialsArray.length()) {
                val credObj = credentialsArray.getJSONObject(i)
                
                val type = when (credObj.getString("type").uppercase()) {
                    "SMB" -> ResourceType.SMB
                    "SFTP" -> ResourceType.SFTP
                    "FTP" -> ResourceType.FTP
                    "CLOUD", "GOOGLE_DRIVE", "GOOGLEDRIVE", "GDRIVE", 
                    "ONEDRIVE", "DROPBOX" -> ResourceType.CLOUD
                    else -> continue
                }
                
                // Parse cloud provider from type or cloudProvider field
                val cloudProvider = when {
                    credObj.has("cloudProvider") -> when (credObj.getString("cloudProvider").uppercase()) {
                        "GOOGLE_DRIVE", "GOOGLEDRIVE", "GDRIVE" -> CloudProvider.GOOGLE_DRIVE
                        "ONEDRIVE" -> CloudProvider.ONEDRIVE
                        "DROPBOX" -> CloudProvider.DROPBOX
                        else -> null
                    }
                    credObj.getString("type").uppercase() in listOf("GOOGLE_DRIVE", "GOOGLEDRIVE", "GDRIVE") -> CloudProvider.GOOGLE_DRIVE
                    credObj.getString("type").uppercase() == "ONEDRIVE" -> CloudProvider.ONEDRIVE
                    credObj.getString("type").uppercase() == "DROPBOX" -> CloudProvider.DROPBOX
                    else -> null
                }
                
                val credential = TestCredential(
                    type = type,
                    server = credObj.getString("server"),
                    port = run {
                        val p = credObj.optInt("port", 0)
                        if (type == ResourceType.SMB && p == 21) 445
                        else if (p > 0) p
                        else when (type) {
                            ResourceType.SFTP -> 22
                            ResourceType.SMB -> 445
                            else -> 21
                        }
                    },
                    username = credObj.getString("username"),
                    password = credObj.getString("password"),
                    shareName = credObj.optString("shareName", "").takeIf { it.isNotEmpty() },
                    folder = credObj.optString("folder", "").takeIf { it.isNotEmpty() },
                    domain = credObj.optString("domain", "").takeIf { it.isNotEmpty() },
                    cloudProvider = cloudProvider
                )
                
                credentials.add(credential)
                val providerInfo = cloudProvider?.let { " (${it.name})" } ?: ""
                Timber.d("Loaded credential: ${type.name}$providerInfo - ${credential.server}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing credentials JSON")
        }
        
        return credentials
    }
    
    /**
     * Get credential for specific resource type.
     */
    suspend fun getCredential(type: ResourceType): TestCredential? {
        return loadCredentials().firstOrNull { it.type == type }
    }
    
    /**
     * Get credential for specific cloud provider.
     */
    suspend fun getCloudCredential(provider: CloudProvider): TestCredential? {
        return loadCredentials().firstOrNull { 
            it.type == ResourceType.CLOUD && it.cloudProvider == provider 
        }
    }
    
    /**
     * Get all available cloud providers from credentials.
     */
    suspend fun getAvailableCloudProviders(): List<CloudProvider> {
        return loadCredentials()
            .filter { it.type == ResourceType.CLOUD && it.cloudProvider != null }
            .mapNotNull { it.cloudProvider }
            .distinct()
    }
}
