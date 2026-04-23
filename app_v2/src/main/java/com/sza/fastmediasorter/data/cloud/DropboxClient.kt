package com.sza.fastmediasorter.data.cloud

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dropbox.core.DbxException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.http.OkHttp3Requestor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.CreateFolderErrorException
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.GetMetadataErrorException
import com.dropbox.core.v2.files.Metadata
import com.dropbox.core.v2.files.SearchMatchV2
import com.dropbox.core.v2.files.ThumbnailFormat
import com.dropbox.core.v2.files.ThumbnailSize
import com.dropbox.core.v2.files.WriteMode
import com.sza.fastmediasorter.domain.model.MediaExtensions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dropbox implementation of CloudStorageClient
 * 
 * Uses Dropbox API v2 with OAuth 2.0 PKCE flow
 * 
 * Authentication flow:
 * 1. User calls authenticate() -> launches Dropbox OAuth in browser/app
 * 2. After authorization, credentials are retrieved via Auth.getDbxCredential()
 * 3. Credentials stored as JSON (access token, refresh token, expiry)
 * 
 * File operations:
 * - Uses Dropbox API v2
 * - Paths use "/" prefix (e.g., "/Photos/vacation.jpg")
 * - Empty string "" represents root folder
 * - All operations run on IO dispatcher
 */
@Singleton
class DropboxClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkCredentialsRepository: com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
) : CloudStorageClient {
    
    override val provider = CloudProvider.DROPBOX
    
    private var dbxClient: DbxClientV2? = null
    private var accountEmail: String? = null
    @Volatile
    private var lastInitializationError: String? = null
    
    /**
     * Shared DbxRequestConfig using app-bundled OkHttp3 instead of system's
     * com.android.okhttp (StandardHttpRequestor). Fixes TLS validation issues
     * on devices with outdated system OkHttp or MITM-intercepting proxies.
     */
    private val dbxRequestConfig by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        DbxRequestConfig.newBuilder(APP_NAME)
            .withHttpRequestor(OkHttp3Requestor(okHttpClient))
            .build()
    }

    private val encryptedPrefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to create encrypted preferences, using default")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    companion object {
        private const val APP_NAME = "FastMediaSorter/2.0"
        private const val PREFS_NAME = "dropbox_credentials"
        private const val KEY_CREDENTIALS = "credentials_json"           // Legacy single-account key
        private const val KEY_CREDENTIALS_PREFIX = "credentials_json_"   // Per-account key prefix
        
        // Dropbox App Key for credential restoration
        private const val DROPBOX_APP_KEY = "dpy64e70kqobr6x"
        
        // Dropbox uses "" for root folder, not null
        private const val ROOT_PATH = ""
        
        // Retry logic for transient errors
        private const val RETRY_MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L  // 2 seconds between retries
        
        // Common image extensions
        private val IMAGE_EXTENSIONS = MediaExtensions.IMAGE
        
        // Common video extensions
        private val VIDEO_EXTENSIONS = MediaExtensions.VIDEO
        
        // Common audio extensions
        private val AUDIO_EXTENSIONS = MediaExtensions.AUDIO
        
        /**
         * Normalize path for Dropbox API.
         * Dropbox requires paths to start with "/" (except root which is "").
         */
        private fun normalizeDropboxPath(path: String?): String {
            if (path.isNullOrEmpty() || path == "/" || path == ROOT_PATH) {
                return ROOT_PATH
            }
            // Ensure path starts with /
            return if (path.startsWith("/")) path else "/$path"
        }
    }
    
    /**
     * Save credentials to encrypted storage.
     * Saves both to the legacy single-account key and to the per-account key (keyed by accountEmail).
     */
    private fun saveCredentials(json: String) {
        val edit = encryptedPrefs.edit().putString(KEY_CREDENTIALS, json) // Always update legacy key
        accountEmail?.let { email ->
            edit.putString("$KEY_CREDENTIALS_PREFIX$email", json) // Save per-account key
        }
        edit.apply()
        Timber.d("Dropbox credentials saved (account: ${accountEmail ?: "unknown"})")
    }

    /**
     * Load credentials from encrypted storage.
     * If [email] is provided, tries per-account key first, then falls back to legacy.
     */
    private fun loadStoredCredentials(email: String? = null): String? {
        if (email != null) {
            val perAccount = encryptedPrefs.getString("$KEY_CREDENTIALS_PREFIX$email", null)
            if (perAccount != null) return perAccount
        }
        return encryptedPrefs.getString(KEY_CREDENTIALS, null)
    }

    /**
     * Try to restore the Dropbox client for a specific account (identified by email).
     * Used for multi-account support when scanning cloud resources.
     * Falls back to any stored credentials if no per-account key found.
     */
    suspend fun tryRestoreForAccount(email: String): Boolean {
        if (dbxClient != null && accountEmail == email) return true // Already correct account
        val stored = loadStoredCredentials(email)
        if (stored != null) {
            if (initialize(stored)) {
                Timber.d("Dropbox client restored for account: $email (current: $accountEmail)")
                return true
            }
        }
        return tryRestoreFromStorage() // Fallback to any stored credentials
    }
    
    /**
     * Clear stored credentials
     */
    private fun clearStoredCredentials() {
        encryptedPrefs.edit().remove(KEY_CREDENTIALS).apply()
        Timber.d("Dropbox credentials cleared")
    }
    
    /**
     * Try to restore client from stored credentials
     */
    suspend fun tryRestoreFromStorage(): Boolean {
        if (dbxClient != null) return true
        
        val stored = loadStoredCredentials()
        if (stored != null) {
            val result = initialize(stored)
            if (result) {
                Timber.d("Dropbox client restored from stored credentials")
                return true
            }
        }
        return false
    }
    
    /**
     * Start Dropbox OAuth 2.0 PKCE flow from an Activity.
     * Replaces the legacy Auth.startOAuth2Authentication() to avoid Dropbox security alerts.
     */
    fun startPkceAuthentication(activity: android.app.Activity, appKey: String) {
        Auth.startOAuth2PKCE(activity, appKey, dbxRequestConfig)
    }

    /**
     * Start Dropbox OAuth 2.0 flow
     * This launches browser/Dropbox app for user authentication
     *
     * After user authorizes, call finishAuthentication() to complete
     */
    override suspend fun authenticate(): AuthResult {
        return withContext(Dispatchers.Main) {
            try {
                // FIRST: Try to restore from stored credentials
                if (dbxClient != null) {
                    // Already authenticated
                    return@withContext AuthResult.Success(
                        accountName = accountEmail ?: "Unknown",
                        credentialsJson = loadStoredCredentials() ?: "{}"
                    )
                }
                
                // Try to restore from encrypted storage
                val storedJson = loadStoredCredentials()
                if (storedJson != null) {
                    val restored = initialize(storedJson)
                    if (restored) {
                        Timber.d("Restored Dropbox client from encrypted storage")
                        return@withContext AuthResult.Success(
                            accountName = accountEmail ?: "Unknown",
                            credentialsJson = storedJson
                        )
                    }
                }
                
                // SECOND: Check if already authenticated via PKCE (new method)
                val credential = Auth.getDbxCredential()
                if (credential != null) {
                    // Already authenticated, initialize client
                    val result = initializeWithCredential(credential)
                    if (result) {
                        return@withContext AuthResult.Success(
                            accountName = accountEmail ?: "Unknown",
                            credentialsJson = serializeCredential(credential)
                        )
                    }
                }
                
                // THIRD: Check legacy OAuth2 token
                val accessToken = Auth.getOAuth2Token()
                if (accessToken != null) {
                    val result = initializeWithAccessToken(accessToken)
                    if (result) {
                        return@withContext AuthResult.Success(
                            accountName = accountEmail ?: "Unknown",
                            credentialsJson = serializeAccessToken(accessToken)
                        )
                    }
                }
                
                // Need OAuth flow - must be initiated from Activity via AddResourceActivity
                AuthResult.Error("Re-authentication required. Please re-add this Dropbox resource.")
            } catch (e: Exception) {
                Timber.e(e, "Dropbox authentication failed")
                AuthResult.Error("Authentication failed: ${e.message}")
            }
        }
    }
    
    /**
     * Complete authentication after OAuth flow
     * Call this from Activity's onResume() after startPkceAuthentication()
     */
    suspend fun finishAuthentication(): AuthResult {
        return withContext(Dispatchers.Main) {
            try {
                // Try PKCE credential first (new method)
                val credential = Auth.getDbxCredential()
                if (credential != null) {
                    val result = initializeWithCredential(credential)
                    if (result) {
                        return@withContext AuthResult.Success(
                            accountName = accountEmail ?: "Unknown",
                            credentialsJson = serializeCredential(credential)
                        )
                    } else {
                        return@withContext AuthResult.Error(
                            lastInitializationError ?: "Failed to initialize Dropbox client"
                        )
                    }
                }
                
                // Try legacy OAuth2 token
                val accessToken = Auth.getOAuth2Token()
                if (accessToken != null) {
                    val result = initializeWithAccessToken(accessToken)
                    if (result) {
                        return@withContext AuthResult.Success(
                            accountName = accountEmail ?: "Unknown",
                            credentialsJson = serializeAccessToken(accessToken)
                        )
                    } else {
                        return@withContext AuthResult.Error(
                            lastInitializationError ?: "Failed to initialize Dropbox client"
                        )
                    }
                }
                
                AuthResult.Cancelled
            } catch (e: Exception) {
                Timber.e(e, "Failed to finish Dropbox authentication")
                AuthResult.Error(buildUserFriendlyErrorMessage(e))
            }
        }
    }
    
    /**
     * Initialize Dropbox client with credential
     */
    private suspend fun initializeWithCredential(credential: DbxCredential): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                dbxClient = DbxClientV2(dbxRequestConfig, credential)
                
                // Get account info to verify connection and get email
                val account = dbxClient!!.users().currentAccount
                accountEmail = account.email
                
                accountEmail?.let { registerAccountInDatabase(it) }
                
                // Save credentials to encrypted storage for later restoration
                val credentialsJson = serializeCredential(credential)
                saveCredentials(credentialsJson)
                lastInitializationError = null
                
                Timber.d("Dropbox client initialized for account: ${account.email}")
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Dropbox client")
                logTlsDiagnostics(e, "initialize_with_credential")
                lastInitializationError = buildUserFriendlyErrorMessage(e)
                false
            }
        }
    }
    
    /**
     * Initialize Dropbox client with simple access token (legacy OAuth2)
     */
    private suspend fun initializeWithAccessToken(accessToken: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                dbxClient = DbxClientV2(dbxRequestConfig, accessToken)
                
                // Get account info to verify connection and get email
                val account = dbxClient!!.users().currentAccount
                accountEmail = account.email
                
                accountEmail?.let { registerAccountInDatabase(it) }
                
                // Save credentials to encrypted storage for later restoration
                val credentialsJson = serializeAccessToken(accessToken)
                saveCredentials(credentialsJson)
                lastInitializationError = null
                
                Timber.d("Dropbox client initialized with access token for account: ${account.email}")
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Dropbox client with access token")
                logTlsDiagnostics(e, "initialize_with_access_token")
                lastInitializationError = buildUserFriendlyErrorMessage(e)
                false
            }
        }
    }
    
    /**
     * Serialize access token to JSON for storage
     */
    private fun serializeAccessToken(accessToken: String): String =
        DropboxClientUtils.serializeAccessToken(accessToken)

    private fun serializeCredential(credential: DbxCredential): String =
        DropboxClientUtils.serializeCredential(credential)

    private fun deserializeCredential(json: String): DbxCredential? =
        DropboxClientUtils.deserializeCredential(json, DROPBOX_APP_KEY)

    private suspend fun registerAccountInDatabase(email: String) {
        val existing = networkCredentialsRepository.getByTypeAndAccountId(CloudProvider.DROPBOX.name, email)
        if (existing == null) {
            val entity = com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity.create(
                credentialId = java.util.UUID.randomUUID().toString(),
                type = CloudProvider.DROPBOX.name,
                server = "",
                port = 0,
                username = email,
                plaintextPassword = "", // Dropbox uses EncryptedSharedPreferences
                accountId = email
            )
            networkCredentialsRepository.insert(entity)
            Timber.d("Registered Dropbox account in database: $email")
        }
    }

    private fun buildUserFriendlyErrorMessage(error: Throwable): String =
        DropboxClientUtils.buildUserFriendlyErrorMessage(error)

    private fun logTlsDiagnostics(error: Throwable, stage: String) =
        DropboxClientUtils.logTlsDiagnostics(error, stage)
    
    private suspend fun <T> withRetry(operation: String, block: suspend () -> T): T =
        DropboxClientUtils.withRetry(operation, RETRY_MAX_ATTEMPTS, RETRY_DELAY_MS, block)
    
    override suspend fun initialize(credentialsJson: String): Boolean {
        return try {
            val obj = JSONObject(credentialsJson)
            
            // Check if it's a legacy access token
            if (obj.optString("type") == "legacy") {
                val accessToken = obj.getString("access_token")
                return initializeWithAccessToken(accessToken)
            }
            
            // Try PKCE credential
            val credential = deserializeCredential(credentialsJson) ?: return false
            initializeWithCredential(credential)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Dropbox client from stored credentials")
            false
        }
    }
    
    /**
     * Check if client is currently authenticated (has valid client)
     */
    override fun isAuthenticated(): Boolean = dbxClient != null
    
    override suspend fun testConnection(): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Test connection by getting account info
                val account = client.users().currentAccount
                // Update cached email
                accountEmail = account.email
                CloudResult.Success(true)
            } catch (e: DbxException) {
                Timber.e(e, "Dropbox connection test failed")
                logTlsDiagnostics(e, "test_connection_dbx_exception")
                CloudResult.Error(buildUserFriendlyErrorMessage(e), e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during connection test")
                logTlsDiagnostics(e, "test_connection_exception")
                CloudResult.Error(buildUserFriendlyErrorMessage(e), e)
            }
        }
    }
    
    /**
     * Get current account email
     * Returns cached email or fetches it if not available
     */
    suspend fun getAccountEmail(): String? {
        if (accountEmail != null) return accountEmail
        
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext null
                withRetry("getAccountEmail") {
                    val account = client.users().currentAccount
                    accountEmail = account.email
                    accountEmail
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get account email")
                null
            }
        }
    }
    
    override suspend fun listFiles(
        folderId: String?,
        pageToken: String?
    ): CloudResult<Pair<List<CloudFile>, String?>> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val path = normalizeDropboxPath(folderId)
                val result = withRetry("listFiles") {
                    client.files().listFolder(path)
                }
                
                val cloudFiles = result.entries.map { metadata ->
                    metadataToCloudFile(metadata, path)
                }
                
                val nextToken = if (result.hasMore) result.cursor else null
                
                CloudResult.Success(cloudFiles to nextToken)
            } catch (e: DbxException) {
                Timber.e(e, "Failed to list files in folder: $folderId")
                CloudResult.Error("Failed to list files: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error listing files")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun listFolders(parentFolderId: String?): CloudResult<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val path = normalizeDropboxPath(parentFolderId)
                val result = withRetry("listFolders") {
                    client.files().listFolder(path)
                }
                
                val folders = result.entries
                    .filterIsInstance<FolderMetadata>()
                    .map { metadata ->
                        metadataToCloudFile(metadata, path)
                    }
                
                CloudResult.Success(folders)
            } catch (e: DbxException) {
                Timber.e(e, "Failed to list folders in: $parentFolderId")
                CloudResult.Error("Failed to list folders: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error listing folders")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun getFileMetadata(fileId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val metadata = withRetry("getFileMetadata") {
                    client.files().getMetadata(fileId)
                }
                val parentPath = fileId.substringBeforeLast('/', "")
                
                CloudResult.Success(metadataToCloudFile(metadata, parentPath))
            } catch (e: GetMetadataErrorException) {
                Timber.e(e, "File not found: $fileId")
                CloudResult.Error("File not found: ${e.message}", e)
            } catch (e: DbxException) {
                Timber.e(e, "Failed to get file metadata: $fileId")
                CloudResult.Error("Failed to get metadata: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting metadata")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun downloadFile(
        fileId: String,
        outputStream: OutputStream,
        progressCallback: ((TransferProgress) -> Unit)?
    ): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Parse cloud:// path to extract Dropbox-relative path
                // Explicitly check for "cloud://dropbox" prefix
                // Normalize fileId to remove leading slash first (fixes issue where fileId comes as /cloud://...)
                val normalizedFileId = fileId.removePrefix("/")
                val rawPath = if (normalizedFileId.startsWith("cloud://dropbox")) {
                    normalizedFileId.substringAfter("cloud://dropbox")
                } else {
                    normalizedFileId
                }
                
                // Trim all leading slashes
                val trimmedPath = rawPath.trimStart { it == '/' }
                
                // Ensure single leading slash
                val dropboxPath = if (trimmedPath.isEmpty()) "" else "/$trimmedPath"
                
                Timber.d("DropboxClient.downloadFile DEBUG: fileId='$fileId', rawPath='$rawPath', trimmedPath='$trimmedPath', dropboxPath='$dropboxPath'")
                
                val downloader = withRetry("downloadFile") {
                    client.files().download(dropboxPath)
                }
                val metadata = downloader.result
                val totalBytes = metadata.size
                var bytesTransferred = 0L
                
                downloader.inputStream.use { input ->
                    val buffer = ByteArray(65536) // 64KB buffer for better network throughput
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesTransferred += bytesRead
                        
                        progressCallback?.invoke(
                            TransferProgress(bytesTransferred, totalBytes)
                        )
                    }
                }
                
                outputStream.flush()
                Timber.d("Successfully downloaded file: $dropboxPath ($bytesTransferred bytes)")
                CloudResult.Success(true)
            } catch (e: DbxException) {
                Timber.e(e, "Failed to download file: $fileId")
                CloudResult.Error("Download failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error downloading file")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun uploadFile(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        parentFolderId: String?,
        progressCallback: ((TransferProgress) -> Unit)?
    ): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val parentPath = normalizeDropboxPath(parentFolderId)
                val filePath = if (parentPath.isEmpty()) "/$fileName" else "$parentPath/$fileName"
                
                // Upload with overwrite mode
                val metadata = withRetry("uploadFile") {
                    client.files().uploadBuilder(filePath)
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(inputStream)
                }
                
                Timber.d("Successfully uploaded file: $filePath")
                CloudResult.Success(metadataToCloudFile(metadata, parentPath))
            } catch (e: DbxException) {
                Timber.e(e, "Failed to upload file: $fileName")
                CloudResult.Error("Upload failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error uploading file")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun createFolder(
        folderName: String,
        parentFolderId: String?
    ): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val parentPath = normalizeDropboxPath(parentFolderId)
                val folderPath = if (parentPath.isEmpty()) "/$folderName" else "$parentPath/$folderName"
                
                val metadata = withRetry("createFolder") {
                    client.files().createFolderV2(folderPath).metadata
                }
                
                Timber.d("Successfully created folder: $folderPath")
                CloudResult.Success(metadataToCloudFile(metadata, parentPath))
            } catch (e: CreateFolderErrorException) {
                Timber.e(e, "Failed to create folder: $folderName")
                CloudResult.Error("Folder creation failed: ${e.message}", e)
            } catch (e: DbxException) {
                Timber.e(e, "Failed to create folder: $folderName")
                CloudResult.Error("Failed to create folder: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error creating folder")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun deleteFile(fileId: String): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Parse cloud:// path to extract Dropbox-relative path
                val rawPath = if (fileId.startsWith("cloud://dropbox")) {
                    fileId.substringAfter("cloud://dropbox")
                } else {
                    fileId
                }
                
                val dropboxPath = rawPath
                    .trimStart { it == '/' }
                    .let { if (it.isEmpty()) "" else "/$it" }

                Timber.d("DropboxClient.deleteFile DEBUG: fileId='$fileId', dropboxPath='$dropboxPath'")
                
                withRetry("deleteFile") {
                    client.files().deleteV2(dropboxPath)
                }
                
                Timber.d("Successfully deleted: $dropboxPath")
                CloudResult.Success(true)
            } catch (e: DbxException) {
                Timber.e(e, "Failed to delete: $fileId")
                CloudResult.Error("Deletion failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error deleting file")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun renameFile(fileId: String, newName: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Parse cloud:// path to extract Dropbox-relative path
                val rawPath = if (fileId.startsWith("cloud://dropbox")) {
                    fileId.substringAfter("cloud://dropbox")
                } else {
                    fileId
                }
                
                val fromPath = rawPath
                    .trimStart { it == '/' }
                    .let { if (it.isEmpty()) "" else "/$it" }
                
                val parentPath = fromPath.substringBeforeLast('/', "")
                val toPath = if (parentPath.isEmpty()) "/$newName" else "$parentPath/$newName"
                
                val metadata = withRetry("renameFile") {
                    client.files().moveV2(fromPath, toPath).metadata
                }
                
                Timber.d("Successfully renamed: $fromPath -> $toPath")
                CloudResult.Success(metadataToCloudFile(metadata, parentPath))
            } catch (e: DbxException) {
                Timber.e(e, "Failed to rename: $fileId")
                CloudResult.Error("Rename failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error renaming file")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun moveFile(fileId: String, newParentId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val fileName = fileId.substringAfterLast('/')
                val newPath = if (newParentId.isEmpty()) "/$fileName" else "$newParentId/$fileName"
                
                val metadata = client.files().moveV2(fileId, newPath).metadata
                
                Timber.d("Successfully moved: $fileId -> $newPath")
                CloudResult.Success(metadataToCloudFile(metadata, newParentId))
            } catch (e: DbxException) {
                Timber.e(e, "Failed to move: $fileId")
                CloudResult.Error("Move failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error moving file")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun copyFile(
        fileId: String,
        newParentId: String,
        newName: String?
    ): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val fileName = newName ?: fileId.substringAfterLast('/')
                val newPath = if (newParentId.isEmpty()) "/$fileName" else "$newParentId/$fileName"
                
                val metadata = client.files().copyV2(fileId, newPath).metadata
                
                Timber.d("Successfully copied: $fileId -> $newPath")
                CloudResult.Success(metadataToCloudFile(metadata, newParentId))
            } catch (e: DbxException) {
                Timber.e(e, "Failed to copy: $fileId")
                CloudResult.Error("Copy failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error copying file")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun searchFiles(
        query: String,
        mimeType: String?
    ): CloudResult<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val result = client.files().searchV2(query)
                
                val cloudFiles = result.matches.mapNotNull { match: SearchMatchV2 ->
                    val metadata = match.metadata.metadataValue
                    if (metadata != null) {
                        val parentPath = metadata.pathDisplay?.substringBeforeLast('/', "") ?: ""
                        metadataToCloudFile(metadata, parentPath)
                    } else null
                }
                
                CloudResult.Success(cloudFiles)
            } catch (e: DbxException) {
                Timber.e(e, "Search failed: $query")
                CloudResult.Error("Search failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during search")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get input stream for file content (for streaming)
     * Used by ExoPlayer's CloudDataSource for video/audio streaming
     */
    override suspend fun getFileInputStream(
        fileId: String,
        position: Long,
        length: Long
    ): CloudResult<InputStream> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                Timber.d("Dropbox.getFileInputStream: START - fileId='$fileId', position=$position, length=$length")
                
                // Parse cloud:// path to extract Dropbox-relative path
                val normalizedFileId = fileId.removePrefix("/")
                val rawPath = if (normalizedFileId.startsWith("cloud://dropbox")) {
                    normalizedFileId.substringAfter("cloud://dropbox")
                } else {
                    normalizedFileId
                }
                
                // Trim all leading slashes and ensure single leading slash
                val trimmedPath = rawPath.trimStart { it == '/' }
                val dropboxPath = if (trimmedPath.isEmpty()) "" else "/$trimmedPath"
                
                Timber.d("Dropbox.getFileInputStream: dropboxPath='$dropboxPath'")
                
                // Download file from Dropbox
                val downloader = client.files().download(dropboxPath)
                val inputStream = downloader.inputStream
                
                // Handle range requests by skipping bytes
                if (position > 0) {
                    val skipped = inputStream.skip(position)
                    Timber.d("Dropbox.getFileInputStream: Skipped $skipped bytes (requested $position)")
                }
                
                Timber.i("Dropbox.getFileInputStream: SUCCESS - Stream opened for '$dropboxPath'")
                CloudResult.Success(inputStream)
            } catch (e: DbxException) {
                Timber.e(e, "Dropbox.getFileInputStream: DbxException")
                CloudResult.Error("Failed to get input stream: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Dropbox.getFileInputStream: Exception")
                CloudResult.Error("Failed to get input stream: ${e.message}", e)
            }
        }
    }

    override suspend fun getThumbnail(fileId: String, size: Int): CloudResult<InputStream> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val thumbnailSize = when {
                    size <= 64 -> ThumbnailSize.W64H64
                    size <= 128 -> ThumbnailSize.W128H128
                    size <= 256 -> ThumbnailSize.W256H256
                    size <= 480 -> ThumbnailSize.W480H320
                    size <= 640 -> ThumbnailSize.W640H480
                    size <= 960 -> ThumbnailSize.W960H640
                    size <= 1024 -> ThumbnailSize.W1024H768
                    else -> ThumbnailSize.W2048H1536
                }
                
                val downloader = client.files().getThumbnailBuilder(fileId)
                    .withFormat(ThumbnailFormat.JPEG)
                    .withSize(thumbnailSize)
                    .start()
                
                // Read thumbnail into byte array to return as InputStream
                val thumbnailBytes = downloader.inputStream.readBytes()
                
                CloudResult.Success(ByteArrayInputStream(thumbnailBytes))
            } catch (e: DbxException) {
                Timber.e(e, "Failed to get thumbnail: $fileId")
                CloudResult.Error("Thumbnail failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting thumbnail")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
    
    override suspend fun signOut(): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // Revoke access token if possible
                dbxClient?.auth()?.tokenRevoke()
                
                // Clear client
                dbxClient = null
                accountEmail = null
                
                Timber.d("Dropbox sign-out successful")
                CloudResult.Success(true)
            } catch (e: Exception) {
                Timber.e(e, "Error during sign-out")
                CloudResult.Error("Sign-out failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Convert Dropbox Metadata to CloudFile
     */
    private fun metadataToCloudFile(metadata: Metadata, parentPath: String): CloudFile =
        DropboxClientUtils.metadataToCloudFile(metadata, parentPath)

    private fun guessMimeType(fileName: String): String? =
        DropboxClientUtils.guessMimeType(fileName)

    override suspend fun fileExists(fileName: String, parentId: String): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val client = dbxClient ?: return@withContext CloudResult.Error("Not authenticated")
                
                val parentPath = normalizeDropboxPath(parentId)
                val filePath = if (parentPath.isEmpty()) "/$fileName" else "$parentPath/$fileName"
                
                try {
                    client.files().getMetadata(filePath)
                    CloudResult.Success(true)
                } catch (e: GetMetadataErrorException) {
                    if (e.errorValue.isPath && e.errorValue.pathValue.isNotFound) {
                        CloudResult.Success(false)
                    } else {
                        throw e
                    }
                }
            } catch (e: DbxException) {
                Timber.e(e, "Failed to check file existence: $fileName")
                CloudResult.Error("Check failed: ${e.message}", e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error checking file existence")
                CloudResult.Error("Unexpected error: ${e.message}", e)
            }
        }
    }
}
