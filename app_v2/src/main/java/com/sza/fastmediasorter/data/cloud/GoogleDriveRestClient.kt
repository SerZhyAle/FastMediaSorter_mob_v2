@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.data.cloud

import android.content.Context
import android.content.Intent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveCredentialsManager
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveHttpClient
import com.sza.fastmediasorter.data.local.db.PendingRevocationDao
import com.sza.fastmediasorter.data.local.db.PendingRevocationEntity
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.google.android.gms.tasks.Tasks

/**
 * Google Drive implementation of CloudStorageClient using REST API v3
 * 
 * REST API approach avoids heavy Google Drive SDK dependencies (~10-12 MB)
 * 
 * Authentication: Google Sign-In API (play-services-auth only)
 * API: Direct HTTP calls to www.googleapis.com/drive/v3
 * 
 * Endpoints:
 * - /about - Get drive info
 * - /files - List/create/search files
 * - /files/{fileId} - Get/update/delete file
 * - /files/{fileId}?alt=media - Download file content
 * - /files/{fileId}/copy - Copy file
 * 
 * Reference: https://developers.google.com/drive/api/v3/reference
 */
@Singleton
class GoogleDriveRestClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val credentialsManager: GoogleDriveCredentialsManager,
    private val httpClient: GoogleDriveHttpClient,
    private val pendingRevocationDao: PendingRevocationDao,
    private val networkCredentialsRepository: NetworkCredentialsRepository
) : CloudStorageClient {
    
    override val provider = CloudProvider.GOOGLE_DRIVE
    
    private var accessToken: String? = null
    private var accountEmail: String? = null
    private var tokenTimestamp: Long = 0L  // Track when token was obtained

    override fun isAuthenticated(): Boolean = accessToken != null

    /** Returns the email of the currently authenticated account, or null if not authenticated. */
    fun getAccountEmail(): String? = accountEmail
    
    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive"
        private const val SCOPE_DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"
        private const val GOOGLE_REVOKE_URL = "https://oauth2.googleapis.com/revoke"
        
        // MIME types
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
        
        private const val PAGE_SIZE = 100
        
        // Token management
        private const val TOKEN_REFRESH_THRESHOLD_MS = 50 * 60 * 1000L  // 50 minutes
        private const val TOKEN_MAX_RETRY_ATTEMPTS = 3
        private const val TOKEN_RETRY_DELAY_MS = 2000L  // 2 seconds between retries
    }

    /**
     * Try to restore client from stored credentials
     * Attempts silent sign-in first, then falls back to stored credentials
     */
    suspend fun tryRestoreFromStorage(): Boolean {
        if (isAuthenticated()) return true
        
        // Try silent sign-in first (most reliable)
        val silentResult = silentSignIn()
        if (silentResult is AuthResult.Success) {
            Timber.d("Google Drive client restored via silent sign-in")
            return true
        }
        
        // Fallback to stored credentials
        val stored = credentialsManager.loadStoredCredentials()
        if (stored != null) {
            val result = initialize(stored)
            if (result) {
                Timber.d("Google Drive client restored from stored credentials")
                return true
            }
        }
        
        Timber.w("Failed to restore Google Drive authentication")
        return false
    }

    /**
     * Try to restore the Google Drive client for a specific account (identified by email).
     * Used for multi-account support when scanning cloud resources.
     * Falls back to any stored credentials if no per-account key found.
     *
     * @param email Account email (stored as credentialsId in ResourceEntity for cloud resources)
     * @return true if client successfully restored or already active for this account
     */
    suspend fun tryRestoreForAccount(email: String): Boolean {
        // Already authenticated with the correct account
        if (isAuthenticated() && accountEmail == email) return true

        // Try per-account stored credentials
        val perAccountStored = credentialsManager.loadStoredCredentials(email)
        if (perAccountStored != null) {
            if (initialize(perAccountStored)) {
                Timber.d("Google Drive client restored for account: $email (current: $accountEmail)")
                return true
            }
        }

        // Fallback: try silent sign-in (may be for a different account)
        return tryRestoreFromStorage()
    }

    /**
     * Get Google Sign-In options
     * Uses Web Client ID from Google Cloud Console for ID token
     */
    fun getSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(context.getString(R.string.google_web_client_id))
            .requestScopes(Scope(SCOPE_DRIVE))
            .requestScopes(Scope(SCOPE_DRIVE_READONLY))
            .build()
    }
    
    /**
     * Get sign-in intent for launching from Activity
     */
    fun getSignInIntent(): Intent {
        val signInOptions = getSignInOptions()
        val client = GoogleSignIn.getClient(context, signInOptions)
        return client.signInIntent
    }
    
    /**
     * Start Google Sign-In authentication flow
     * Must be called from Activity context
     * 
     * @throws com.google.android.gms.auth.UserRecoverableAuthException if user consent is required
     */
    override suspend fun authenticate(): AuthResult {
        return withContext(Dispatchers.Main) {
            try {
                // Try silent sign-in first
                val silentResult = silentSignIn()
                if (silentResult is AuthResult.Success) {
                    return@withContext silentResult
                }
                
                // Fallback to existing logic if silent sign-in fails
                val account = GoogleSignIn.getLastSignedInAccount(context)
                
                if (account != null) {
                    // Try to get OAuth token with current scopes
                    // GoogleAuthUtil will automatically request additional permissions if needed
                    val token = getAccessToken(account)
                    if (token != null) {
                        accessToken = token
                        tokenTimestamp = System.currentTimeMillis()
                        accountEmail = account.email
                        return@withContext AuthResult.Success(
                            accountName = accountEmail ?: "Unknown",
                            credentialsJson = credentialsManager.serializeAccount(account)
                        )
                    }
                }
                
                // Need interactive authentication - must be initiated from Activity via AddResourceActivity
                AuthResult.Error("Re-authentication required. Please re-add this Google Drive resource.")
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                // Re-throw UserRecoverableAuthException so caller can handle interactive auth
                Timber.e(e, "Google Drive authentication failed")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Google Drive authentication failed")
                AuthResult.Error("Authentication failed: ${e.message}")
            }
        }
    }

    /**
     * Attempt silent sign-in to refresh credentials
     */
    private suspend fun silentSignIn(): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val signInOptions = getSignInOptions()
                val client = GoogleSignIn.getClient(context, signInOptions)
                
                // silentSignIn returns Task<GoogleSignInAccount>
                val task = client.silentSignIn()
                
                // Wait for task to complete synchronously
                val account = com.google.android.gms.tasks.Tasks.await(task)
                
                if (account != null) {
                    val token = getAccessToken(account, forceRefresh = true)
                    if (token != null) {
                        accessToken = token
                        tokenTimestamp = System.currentTimeMillis()
                        accountEmail = account.email
                        Timber.i("Silent sign-in successful")
                        return@withContext AuthResult.Success(
                            accountName = accountEmail ?: "Unknown",
                            credentialsJson = credentialsManager.serializeAccount(account)
                        )
                    }
                }
                AuthResult.Error("Silent sign-in failed: No account or token")
            } catch (e: Exception) {
                Timber.w("Silent sign-in failed: ${e.message}")
                AuthResult.Error("Silent sign-in failed: ${e.message}")
            }
        }
    }
    
    /**
     * Handle sign-in result from Activity
     */
    suspend fun handleSignInResult(account: GoogleSignInAccount?): AuthResult {
        return if (account != null) {
            val token = getAccessToken(account)
            if (token != null) {
                accessToken = token
                tokenTimestamp = System.currentTimeMillis()
                accountEmail = account.email
                val credentials = credentialsManager.serializeAccount(account)
                // Save to encrypted storage for automatic restoration (both legacy and per-account key)
                credentialsManager.saveCredentials(credentials, account.email)
                
                // Make sure this account is registered in NetworkCredentialsEntity for multi-account picker
                account.email?.let { email ->
                    val existing = networkCredentialsRepository.getByTypeAndAccountId(CloudProvider.GOOGLE_DRIVE.name, email)
                    if (existing == null) {
                        val entity = com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity.create(
                            credentialId = UUID.randomUUID().toString(),
                            type = CloudProvider.GOOGLE_DRIVE.name,
                            server = "",
                            port = 0,
                            username = email,
                            plaintextPassword = "", // Drive uses its own credentialsManager for the actual token, so leave empty
                            accountId = email
                        )
                        networkCredentialsRepository.insert(entity)
                        Timber.d("Registered Google Drive account in database: $email")
                    }
                }
                
                AuthResult.Success(
                    accountName = accountEmail ?: "Unknown",
                    credentialsJson = credentials
                )
            } else {
                AuthResult.Error("Failed to get access token")
            }
        } else {
            AuthResult.Error("Sign-in failed or cancelled")
        }
    }
    
    /**
     * Get OAuth access token from signed-in account
     * Uses GoogleAuthUtil to get a proper access token for Drive API
     * ID token cannot be used directly with Drive REST API
     * 
     * @param account The Google Sign-In account
     * @param forceRefresh If true, clears cached token before requesting new one
     */
    private suspend fun getAccessToken(account: GoogleSignInAccount, forceRefresh: Boolean = false): String? {
        return withContext(Dispatchers.IO) {
            try {
                val scope = "oauth2:$SCOPE_DRIVE $SCOPE_DRIVE_READONLY"
                Timber.d("Requesting access token with scope: $scope (forceRefresh=$forceRefresh)")
                
                // Clear any cached token if forceRefresh or if we already have a token
                // This is important when scope changes (e.g., from drive.file to drive)
                if (forceRefresh || accessToken != null) {
                    try {
                        val currentToken = accessToken
                        if (currentToken != null) {
                            Timber.d("Clearing cached token")
                            GoogleAuthUtil.clearToken(context, currentToken)
                        }
                    } catch (e: Exception) {
                        Timber.d("No cached token to clear or clearToken failed: ${e.message}")
                    }
                }
                
                // GoogleAuthUtil returns proper OAuth2 access token for Drive API
                val token = GoogleAuthUtil.getToken(context, account.account!!, scope)
                Timber.i("Successfully obtained access token")
                token
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                // Re-throw UserRecoverableAuthException so caller can handle interactive auth
                Timber.e(e, "Failed to get access token: ${e.javaClass.simpleName} - ${e.message}")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to get access token: ${e.javaClass.simpleName} - ${e.message}")
                null
            }
        }
    }
    
    /**
     * Check if current token should be refreshed based on age
     */
    private fun shouldRefreshToken(): Boolean {
        if (tokenTimestamp == 0L) return false
        val age = System.currentTimeMillis() - tokenTimestamp
        return age > TOKEN_REFRESH_THRESHOLD_MS
    }
    
    /**
     * Proactively refresh token if it's old to prevent expiration during operation
     */
    private suspend fun ensureTokenFresh() {
        if (shouldRefreshToken()) {
            Timber.d("Token is old (>50 min), proactively refreshing...")
            silentSignIn()
        }
    }
    
    /**
     * Check if account has required Drive permissions
     */
    private fun hasRequiredPermissions(account: GoogleSignInAccount): Boolean {
        val grantedScopes = account.grantedScopes
        val requiredScope = Scope(SCOPE_DRIVE)
        return grantedScopes.contains(requiredScope)
    }
    
    override suspend fun initialize(credentialsJson: String): Boolean {
        return try {
            // First, try silent sign-in which should restore the session
            val silentResult = silentSignIn()
            if (silentResult is AuthResult.Success) {
                Timber.d("Initialized Google Drive via silent sign-in")
                return true
            }
            
            // Fallback: check if there's a signed-in account
            val account = withContext(Dispatchers.Main) {
                GoogleSignIn.getLastSignedInAccount(context)
            }
            
            if (account != null) {
                val email = credentialsManager.deserializeAccount(credentialsJson)
                if (account.email == email) {
                    // Get fresh token
                    val token = getAccessToken(account)
                    if (token != null) {
                        accessToken = token
                        tokenTimestamp = System.currentTimeMillis()
                        accountEmail = account.email
                        // Save to encrypted storage for future automatic restoration (per-account key too)
                        credentialsManager.saveCredentials(credentialsJson, account.email)
                        Timber.d("Initialized Google Drive for account: $email")
                        true
                    } else {
                        Timber.w("Failed to get access token for account: $email")
                        false
                    }
                } else {
                    Timber.w("Stored account ($email) doesn't match current account (${account.email})")
                    false
                }
            } else {
                Timber.w("No account signed in, cannot initialize")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Google Drive client")
            false
        }
    }
    
    override suspend fun testConnection(): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val url = URL("$DRIVE_API_BASE/about?fields=user")
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess) {
                    CloudResult.Success(true)
                } else {
                    CloudResult.Error("Connection test failed: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Connection test failed")
                CloudResult.Error("Connection test failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun listFiles(
        folderId: String?,
        pageToken: String?
    ): CloudResult<Pair<List<CloudFile>, String?>> {
        return withContext(Dispatchers.IO) {
            try {
                // Proactively refresh token if old
                ensureTokenFresh()
                
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val query = if (folderId != null) {
                    "'$folderId' in parents and trashed = false"
                } else {
                    "'root' in parents and trashed = false"
                }
                
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val fields = URLEncoder.encode("nextPageToken, files(id, name, mimeType, size, modifiedTime, thumbnailLink, webViewLink)", "UTF-8")
                
                val urlString = buildString {
                    append("$DRIVE_API_BASE/files")
                    append("?q=$encodedQuery")
                    append("&pageSize=$PAGE_SIZE")
                    append("&fields=$fields")
                    append("&orderBy=folder,name")
                    if (pageToken != null) {
                        append("&pageToken=$pageToken")
                    }
                }
                
                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val files = json.getJSONArray("files")
                    val cloudFiles = parseItems(files, folderId ?: "root")
                    
                    val nextToken: String? = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
                    
                    CloudResult.Success(cloudFiles to nextToken)
                } else {
                    CloudResult.Error("Failed to list files: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to list files")
                CloudResult.Error("Failed to list files: ${e.message}", e)
            }
        }
    }
    
    override suspend fun listFolders(parentFolderId: String?): CloudResult<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val parentQuery = if (parentFolderId != null) {
                    "'$parentFolderId' in parents"
                } else {
                    "'root' in parents"
                }
                
                val query = "$parentQuery and mimeType = '$MIME_TYPE_FOLDER' and trashed = false"
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val fields = URLEncoder.encode("files(id, name, mimeType, modifiedTime)", "UTF-8")
                
                val urlString = "$DRIVE_API_BASE/files?q=$encodedQuery&pageSize=$PAGE_SIZE&fields=$fields&orderBy=name"
                
                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val files = json.getJSONArray("files")
                    val folders = parseItems(files, parentFolderId ?: "root")
                        .filter { it.isFolder }
                    
                    CloudResult.Success(folders)
                } else {
                    CloudResult.Error("Failed to list folders: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to list folders")
                CloudResult.Error("Failed to list folders: ${e.message}", e)
            }
        }
    }
    
    override suspend fun getFileMetadata(fileId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                // Strip cloud:// prefix if present
                val actualFileId = if (fileId.startsWith("cloud://google_drive/")) {
                    fileId.substringAfter("cloud://google_drive/")
                } else {
                    fileId
                }

                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val fields = URLEncoder.encode("id, name, mimeType, size, modifiedTime, thumbnailLink, webViewLink, parents", "UTF-8")
                val url = URL("$DRIVE_API_BASE/files/$actualFileId?fields=$fields")
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parents = json.optJSONArray("parents")
                    val parentId = if (parents != null && parents.length() > 0) {
                        parents.getString(0)
                    } else {
                        "root"
                    }
                    CloudResult.Success(parseItem(json, parentId))
                } else {
                    CloudResult.Error("Failed to get metadata: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get file metadata")
                CloudResult.Error("Failed to get metadata: ${e.message}", e)
            }
        }
    }
    
    /**
     * Resolve filename to file ID by searching in parent folder.
     * Used when only filename is known (e.g., from integration tests or legacy code).
     * @param fileName Name of file to find
     * @param parentFolderId Parent folder ID to search in
     * @return CloudResult with file ID, or Error if not found
     */
    private suspend fun resolveFileIdFromName(fileName: String, parentFolderId: String?): CloudResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val parentQuery = if (parentFolderId != null && parentFolderId != "root") {
                    "'$parentFolderId' in parents"
                } else {
                    "'root' in parents"
                }
                
                // Escape backslash and single quote per Drive API query language spec
                val escapedFileName = fileName.replace("\\", "\\\\").replace("'", "\\'")
                val query = "$parentQuery and name = '$escapedFileName' and trashed = false"
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val fields = URLEncoder.encode("files(id)", "UTF-8")
                
                val urlString = "$DRIVE_API_BASE/files?q=$encodedQuery&pageSize=1&fields=$fields"
                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val files = json.getJSONArray("files")
                    if (files.length() > 0) {
                        val fileId = files.getJSONObject(0).getString("id")
                        CloudResult.Success(fileId)
                    } else {
                        CloudResult.Error("File not found: $fileName")
                    }
                } else {
                    CloudResult.Error("Search failed: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve file ID from name: $fileName")
                CloudResult.Error("Failed to resolve file ID: ${e.message}", e)
            }
        }
    }
    
    /**
     * Parse fileId parameter which can be:
     * 1. Actual Google Drive file ID (e.g., "1AbC...xyz")
     * 2. "folderId/filename" format (e.g., "1AbC.../document.txt") - needs resolution
     * 3. Just filename (e.g., "document.txt") - needs resolution in root
     * 4. Full cloud path (e.g., "cloud://google_drive/folder/file.txt")
     * 
     * @param fileId Input parameter that may be ID, filename, or path
     * @return Resolved file ID or null if resolution fails
     */
    private suspend fun parseAndResolveFileId(fileId: String): String? {
        // Strip cloud:// prefix if present and extract parts
        val actualInput = if (fileId.startsWith("cloud://google_drive/")) {
            fileId.substringAfter("cloud://google_drive/")
        } else {
            fileId
        }
        
        // Check if it contains folder/filename format
        val (potentialFolderId, potentialFilename) = if (actualInput.contains('/')) {
            val parts = actualInput.split('/', limit = 2)
            parts[0] to parts.getOrNull(1)
        } else {
            null to actualInput
        }
        
        // If we have a folder/filename split
        if (potentialFolderId != null && potentialFilename != null) {
            // Handle empty folder ID as root
            val targetFolderId = if (potentialFolderId.isEmpty()) {
                "root"
            } else {
                resolveFolderId(potentialFolderId) ?: potentialFolderId
            }
            
            // Check if first part looks like a Google Drive ID
            if (potentialFolderId.isEmpty() || (targetFolderId.length > 25 && targetFolderId.matches(Regex("^[a-zA-Z0-9_-]+$")))) {
                // Try to resolve the filename in this folder
                val resolveResult = resolveFileIdFromName(potentialFilename, targetFolderId)
                return when (resolveResult) {
                    is CloudResult.Success -> resolveResult.data
                    is CloudResult.Error -> {
                        Timber.w("Could not resolve '$potentialFilename' in folder '$targetFolderId': ${resolveResult.message}")
                        null
                    }
                }
            }
        }
        
        // If it looks like a Google Drive ID (long alphanumeric + special chars), use it directly
        // Google Drive IDs are typically 30+ characters with alphanumeric and _ - chars
        if (actualInput.length > 25 && actualInput.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            return actualInput
        }
        
        // Otherwise, it might be just a filename - try to resolve it in root
        val resolveResult = resolveFileIdFromName(actualInput, "root")
        return when (resolveResult) {
            is CloudResult.Success -> resolveResult.data
            is CloudResult.Error -> {
                Timber.w("Could not resolve '$actualInput' to file ID: ${resolveResult.message}")
                // Return the input as-is and let the API call fail with a proper error
                actualInput
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
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated - token missing. Please re-authenticate.")
                
                // Resolve fileId (may be filename, folderId/filename, or actual ID)
                val actualFileId = parseAndResolveFileId(fileId)
                if (actualFileId == null) {
                    return@withContext CloudResult.Error("Could not resolve file ID from: $fileId")
                }

                // Get file size first
                val metadataResult = getFileMetadata(actualFileId)
                val size = if (metadataResult is CloudResult.Success) {
                    metadataResult.data.size
                } else {
                    0L
                }
                
                // Download file content
                val url = URL("$DRIVE_API_BASE/files/$actualFileId?alt=media")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                
                try {
                    val inputStream = BufferedInputStream(connection.inputStream)
                    val buffer = ByteArray(65536) // 64KB buffer for better network throughput
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        progressCallback?.invoke(TransferProgress(totalBytes, size))
                    }
                    
                    outputStream.flush()
                    CloudResult.Success(true)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to download file")
                CloudResult.Error("Download failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Helper to resolve folder name to ID if needed.
     */
    private suspend fun resolveFolderId(idOrName: String?): String? {
        // Handle null or empty string as root folder (no parent)
        if (idOrName.isNullOrEmpty()) return null
        
        // Google Drive IDs are typically long (33+ chars). Names like "test_media" are short.
        if (idOrName.length < 25 && !idOrName.startsWith("cloud://")) {
            Timber.d("Resolving folder '$idOrName' to ID...")
            val result = ensureFolderExists(idOrName)
            if (result is CloudResult.Success) {
                return result.data.id
            }
        }
        return idOrName
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
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated - token missing. Please re-authenticate.")
                
                val resolvedParentId = resolveFolderId(parentFolderId)
                
                // Create metadata
                val metadata = JSONObject().apply {
                    put("name", fileName)
                    if (resolvedParentId != null) {
                        put("parents", JSONArray().put(resolvedParentId))
                    }
                }
                
                // Multipart upload
                val boundary = "----FastMediaSorterBoundary${System.currentTimeMillis()}"
                val url = URL("$DRIVE_UPLOAD_BASE/files?uploadType=multipart")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                connection.doOutput = true
                
                try {
                    val outputStream = connection.outputStream
                    
                    // Write metadata part
                    outputStream.write("--$boundary\r\n".toByteArray())
                    outputStream.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
                    outputStream.write(metadata.toString().toByteArray())
                    outputStream.write("\r\n".toByteArray())
                    
                    // Write file content part
                    outputStream.write("--$boundary\r\n".toByteArray())
                    outputStream.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
                    
                    val buffer = ByteArray(65536) // 64KB buffer for better network throughput
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        progressCallback?.invoke(TransferProgress(totalBytes, 0L))
                    }
                    
                    outputStream.write("\r\n--$boundary--\r\n".toByteArray())
                    outputStream.flush()
                    
                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        val responseData = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(responseData)
                        val parents = json.optJSONArray("parents")
                        val parentId = if (parents != null && parents.length() > 0) {
                            parents.getString(0)
                        } else {
                            "root"
                        }
                        CloudResult.Success(parseItem(json, parentId))
                    } else {
                        val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                        CloudResult.Error("Upload failed: $error")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload file")
                CloudResult.Error("Upload failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun createFolder(
        folderName: String,
        parentFolderId: String?
    ): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val resolvedParentId = resolveFolderId(parentFolderId)
                
                val requestBody = JSONObject().apply {
                    put("name", folderName)
                    put("mimeType", MIME_TYPE_FOLDER)
                    if (resolvedParentId != null) {
                        put("parents", JSONArray().put(resolvedParentId))
                    }
                }.toString()
                
                val url = URL(DRIVE_API_BASE + "/files")
                val response = makeAuthenticatedRequest(url, "POST", token, requestBody)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parents = json.optJSONArray("parents")
                    val parentId = if (parents != null && parents.length() > 0) {
                        parents.getString(0)
                    } else {
                        "root"
                    }
                    CloudResult.Success(parseItem(json, parentId))
                } else {
                    CloudResult.Error("Failed to create folder: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to create folder")
                CloudResult.Error("Failed to create folder: ${e.message}", e)
            }
        }
    }
    
    override suspend fun deleteFile(fileId: String): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Resolve fileId (may be filename, folderId/filename, or actual ID)
                val actualFileId = parseAndResolveFileId(fileId)
                if (actualFileId == null) {
                    return@withContext CloudResult.Error("Could not resolve file ID from: $fileId")
                }

                val url = URL("$DRIVE_API_BASE/files/$actualFileId")
                val response = makeAuthenticatedRequest(url, "DELETE", token)
                
                if (response.isSuccess) {
                    CloudResult.Success(true)
                } else {
                    CloudResult.Error("Failed to delete: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete file")
                CloudResult.Error("Deletion failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun renameFile(fileId: String, newName: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val requestBody = JSONObject().apply {
                    put("name", newName)
                }.toString()
                
                // Resolve fileId (may be filename, folderId/filename, or actual ID)
                val actualFileId = parseAndResolveFileId(fileId)
                if (actualFileId == null) {
                    return@withContext CloudResult.Error("Could not resolve file ID from: $fileId")
                }

                val url = URL("$DRIVE_API_BASE/files/$actualFileId")
                val response = makeAuthenticatedRequest(url, "PATCH", token, requestBody)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parents = json.optJSONArray("parents")
                    val parentId = if (parents != null && parents.length() > 0) {
                        parents.getString(0)
                    } else {
                        "root"
                    }
                    CloudResult.Success(parseItem(json, parentId))
                } else {
                    CloudResult.Error("Failed to rename: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to rename file")
                CloudResult.Error("Rename failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun moveFile(fileId: String, newParentId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Get current parents
                val metadataResult = getFileMetadata(fileId)
                if (metadataResult !is CloudResult.Success) {
                    return@withContext CloudResult.Error("Failed to get file metadata")
                }
                
                // Get current parent from path (simplified)
                val currentParentId = metadataResult.data.path.substringBeforeLast("/")
                
                val fields = URLEncoder.encode("id, name, mimeType, size, modifiedTime, parents", "UTF-8")
                val actualFileId = parseAndResolveFileId(fileId)
                    ?: return@withContext CloudResult.Error("Could not resolve file ID from: $fileId")

                val urlString = buildString {
                    append("$DRIVE_API_BASE/files/$actualFileId")
                    append("?addParents=$newParentId")
                    if (currentParentId.isNotEmpty() && currentParentId != "root") {
                        append("&removeParents=$currentParentId")
                    }
                    append("&fields=$fields")
                }
                
                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "PATCH", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    CloudResult.Success(parseItem(json, newParentId))
                } else {
                    CloudResult.Error("Failed to move: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to move file")
                CloudResult.Error("Move failed: ${e.message}", e)
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
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val requestBody = JSONObject().apply {
                    put("parents", JSONArray().put(newParentId))
                    if (newName != null) {
                        put("name", newName)
                    }
                }.toString()
                
                val actualFileId = parseAndResolveFileId(fileId)
                    ?: return@withContext CloudResult.Error("Could not resolve file ID from: $fileId")

                val url = URL("$DRIVE_API_BASE/files/$actualFileId/copy")
                val response = makeAuthenticatedRequest(url, "POST", token, requestBody)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    CloudResult.Success(parseItem(json, newParentId))
                } else {
                    CloudResult.Error("Failed to copy: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy file")
                CloudResult.Error("Copy failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun fileExists(fileName: String, parentId: String): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Escape backslash and single quote per Drive API query language spec
                val escapedFileName = fileName.replace("\\", "\\\\").replace("'", "\\'")
                val query = "name = '$escapedFileName' and '$parentId' in parents and trashed = false"
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val fields = URLEncoder.encode("files(id)", "UTF-8")
                
                val urlString = "$DRIVE_API_BASE/files?q=$encodedQuery&pageSize=1&fields=$fields"
                
                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val files = json.getJSONArray("files")
                    CloudResult.Success(files.length() > 0)
                } else {
                    CloudResult.Error("Failed to check file existence: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check file existence")
                CloudResult.Error("Check failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun searchFiles(query: String, mimeType: String?): CloudResult<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val searchQuery = buildString {
                    append("name contains '$query' and trashed = false")
                    if (mimeType != null) {
                        append(" and mimeType = '$mimeType'")
                    }
                }
                
                val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
                val fields = URLEncoder.encode("files(id, name, mimeType, size, modifiedTime, thumbnailLink, parents)", "UTF-8")
                val urlString = "$DRIVE_API_BASE/files?q=$encodedQuery&pageSize=$PAGE_SIZE&fields=$fields"
                
                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val files = json.getJSONArray("files")
                    val cloudFiles = parseItems(files, "search")
                    CloudResult.Success(cloudFiles)
                } else {
                    CloudResult.Error("Search failed: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Search failed")
                CloudResult.Error("Search failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Find folder by exact name match in parent folder.
     * Returns the folder's CloudFile if found, null if not found.
     * 
     * @param folderName Exact name of the folder to find
     * @param parentFolderId Parent folder ID to search in (null for root)
     * @return CloudResult with CloudFile if found, null if not found
     */
    suspend fun findFolderByName(folderName: String, parentFolderId: String? = null): CloudResult<CloudFile?> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val parentQuery = if (parentFolderId != null && parentFolderId != "root") {
                    "'$parentFolderId' in parents"
                } else {
                    "'root' in parents"
                }
                
                val query = "$parentQuery and name = '$folderName' and mimeType = '$MIME_TYPE_FOLDER' and trashed = false"
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val fields = URLEncoder.encode("files(id, name, mimeType, modifiedTime, parents)", "UTF-8")
                
                val urlString = "$DRIVE_API_BASE/files?q=$encodedQuery&pageSize=1&fields=$fields"
                
                val url = URL(urlString)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val files = json.getJSONArray("files")
                    if (files.length() > 0) {
                        val folder = parseItem(files.getJSONObject(0), parentFolderId ?: "root")
                        CloudResult.Success(folder)
                    } else {
                        CloudResult.Success(null) // Not found
                    }
                } else {
                    CloudResult.Error("Search failed: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to find folder: $folderName")
                CloudResult.Error("Find folder failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Ensure folder exists - find it or create it if not found.
     * This is a convenience method for integration tests and automated workflows.
     * 
     * @param folderName Name of the folder to ensure exists
     * @param parentFolderId Parent folder ID (null for root)
     * @return CloudResult with CloudFile of existing or newly created folder
     */
    suspend fun ensureFolderExists(folderName: String, parentFolderId: String? = null): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                // First, try to find existing folder
                val findResult = findFolderByName(folderName, parentFolderId)
                
                when (findResult) {
                    is CloudResult.Success -> {
                        if (findResult.data != null) {
                            // Folder found
                            Timber.d("ensureFolderExists: Folder '$folderName' already exists with ID: ${findResult.data.id}")
                            CloudResult.Success(findResult.data)
                        } else {
                            // Folder not found, create it
                            Timber.i("ensureFolderExists: Folder '$folderName' not found, creating...")
                            val createResult = createFolder(folderName, parentFolderId)
                            when (createResult) {
                                is CloudResult.Success -> {
                                    Timber.i("ensureFolderExists: Created folder '$folderName' with ID: ${createResult.data.id}")
                                    createResult
                                }
                                is CloudResult.Error -> {
                                    Timber.e("ensureFolderExists: Failed to create folder: ${createResult.message}")
                                    createResult
                                }
                            }
                        }
                    }
                    is CloudResult.Error -> {
                        Timber.e("ensureFolderExists: Search failed: ${findResult.message}")
                        findResult
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "ensureFolderExists failed")
                CloudResult.Error("ensureFolderExists failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun getThumbnail(fileId: String, size: Int): CloudResult<InputStream> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Get thumbnail link from metadata
                val metadataResult = getFileMetadata(fileId)
                if (metadataResult !is CloudResult.Success) {
                    return@withContext CloudResult.Error("Failed to get metadata")
                }
                
                val thumbnailLink = metadataResult.data.thumbnailUrl
                if (thumbnailLink.isNullOrEmpty()) {
                    // Fallback: download actual file content
                    return@withContext downloadFileAsStream(fileId, token)
                }
                
                // Download thumbnail
                val url = URL(thumbnailLink)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                
                try {
                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        val bytes = connection.inputStream.readBytes()
                        CloudResult.Success(bytes.inputStream())
                    } else {
                        val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                        CloudResult.Error("Thumbnail failed: $error")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get thumbnail")
                CloudResult.Error("Thumbnail failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Download file as InputStream (for thumbnails)
     */
    private suspend fun downloadFileAsStream(fileId: String, token: String): CloudResult<InputStream> {
        return httpClient.downloadFileAsStream(fileId, DRIVE_API_BASE, token)
    }
    
    override suspend fun signOut(): CloudResult<Boolean> {
        // Capture token before clearing — revoke call happens off-Main
        val tokenToRevoke = accessToken

        // Local sign-out on Main thread; capture any failure to return early
        var signOutError: CloudResult.Error? = null
        withContext(Dispatchers.Main) {
            try {
                val signInClient = GoogleSignIn.getClient(context, getSignInOptions())
                signInClient.signOut()
                accessToken = null
                accountEmail = null
            } catch (e: Exception) {
                Timber.e(e, "Failed to sign out")
                signOutError = CloudResult.Error("Sign-out failed: ${e.message}", e)
            }
        }
        if (signOutError != null) return signOutError!!

        // Server-side revocation — performed on IO after local state is cleared
        if (tokenToRevoke != null) {
            withContext(Dispatchers.IO) {
                try {
                    val url = URL("$GOOGLE_REVOKE_URL?token=$tokenToRevoke")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.connect()
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code == 200 || code == 400) {
                        Timber.i("GoogleDriveRestClient: token revoked (HTTP $code)")
                    } else {
                        Timber.w("GoogleDriveRestClient: revoke returned HTTP $code — queuing for retry")
                        pendingRevocationDao.insert(
                            PendingRevocationEntity(
                                provider = "google",
                                token = tokenToRevoke,
                                revokeUrl = GOOGLE_REVOKE_URL
                            )
                        )
                    }
                } catch (e: Exception) {
                    Timber.w(e, "GoogleDriveRestClient: revoke network error — queuing for retry")
                    pendingRevocationDao.insert(
                        PendingRevocationEntity(
                            provider = "google",
                            token = tokenToRevoke,
                            revokeUrl = GOOGLE_REVOKE_URL
                        )
                    )
                }
            }
        }

        return CloudResult.Success(true)
    }
    
    /**
     * Get authenticated InputStream for media file with range support (for ExoPlayer).
     * Used by CloudDataSource for video/audio streaming.
     * 
     * @param fileId Google Drive file ID
     * @param position Starting byte position (0 for start of file)
     * @param length Number of bytes to read (-1 for entire file)
     * @return InputStream with requested range
     */
    override suspend fun getFileInputStream(
        fileId: String,
        position: Long,
        length: Long
    ): CloudResult<InputStream> {
        return getFileInputStreamInternal(fileId, position, length, retryCount = 0)
    }
    
    /**
     * Internal implementation with retry support
     */
    private suspend fun getFileInputStreamInternal(
        fileId: String,
        position: Long,
        length: Long,
        retryCount: Int
    ): CloudResult<InputStream> {
        // Get current account
        val account = withContext(Dispatchers.Main) {
            GoogleSignIn.getLastSignedInAccount(context)
        }
        if (account == null) {
            Timber.e("getFileInputStream: No signed-in account")
            return CloudResult.Error("Not authenticated")
        }
        
        // Get fresh OAuth token
        val token = getAccessToken(account)
        if (token == null) {
            Timber.e("getFileInputStream: Failed to obtain access token")
            return CloudResult.Error("Failed to obtain access token")
        }
        
        val result = httpClient.getFileInputStream(fileId, DRIVE_API_BASE, token, position, length)
        
        // Handle 401 Unauthorized - Token expired
        if (result is GoogleDriveHttpClient.StreamResult.Error && result.httpCode == 401 && retryCount < TOKEN_MAX_RETRY_ATTEMPTS) {
            Timber.w("getFileInputStream: Received 401 Unauthorized (attempt ${retryCount + 1}/$TOKEN_MAX_RETRY_ATTEMPTS). Attempting silent sign-in and retry...")
            
            // Delay before retry
            if (retryCount > 0) {
                delay(TOKEN_RETRY_DELAY_MS)
            }
            
            val authResult = silentSignIn()
            if (authResult is AuthResult.Success) {
                Timber.i("getFileInputStream: Silent sign-in successful. Retrying request (attempt ${retryCount + 2})...")
                return getFileInputStreamInternal(fileId, position, length, retryCount + 1)
            }
            
            // If not last retry, try again
            if (retryCount < TOKEN_MAX_RETRY_ATTEMPTS - 1) {
                Timber.w("getFileInputStream: Silent sign-in failed, but will retry again...")
                delay(TOKEN_RETRY_DELAY_MS)
                return getFileInputStreamInternal(fileId, position, length, retryCount + 1)
            }
            
            Timber.e("getFileInputStream: All retry attempts exhausted ($TOKEN_MAX_RETRY_ATTEMPTS attempts). Returning error.")
            return CloudResult.Error("Authentication expired after $TOKEN_MAX_RETRY_ATTEMPTS retry attempts. Token invalid. Please re-authenticate in Settings.")
        }
        
        return when (result) {
            is GoogleDriveHttpClient.StreamResult.Success -> {
                Timber.d("getFileInputStream: Stream opened successfully")
                CloudResult.Success(result.stream)
            }
            is GoogleDriveHttpClient.StreamResult.Error -> {
                Timber.e("getFileInputStream: ${result.message}")
                CloudResult.Error(result.message)
            }
        }
    }
    
    /**
     * Make authenticated HTTP request to Drive API with auto-retry on 401
     */
    private suspend fun makeAuthenticatedRequest(
        url: URL,
        method: String,
        token: String,
        body: String? = null,
        retryCount: Int = 0
    ): GoogleDriveHttpClient.ApiResponse {
        // First attempt
        val response = httpClient.makeAuthenticatedRequest(url, method, token, body)
        
        // Handle 401 Unauthorized - Token expired
        if (response.httpCode == 401 && retryCount < TOKEN_MAX_RETRY_ATTEMPTS) {
            Timber.w("Received 401 Unauthorized (attempt ${retryCount + 1}/$TOKEN_MAX_RETRY_ATTEMPTS). Attempting silent sign-in and retry...")
            
            // Delay before retry to avoid hammering server
            if (retryCount > 0) {
                delay(TOKEN_RETRY_DELAY_MS)
            }
            
            val authResult = silentSignIn()
            if (authResult is AuthResult.Success) {
                val newToken = accessToken
                if (newToken != null) {
                    Timber.i("Silent sign-in successful. Retrying request (attempt ${retryCount + 2})...")
                    return makeAuthenticatedRequest(url, method, newToken, body, retryCount + 1)
                }
            }
            
            // If not last retry, try again
            if (retryCount < TOKEN_MAX_RETRY_ATTEMPTS - 1) {
                Timber.w("Silent sign-in failed, but will retry again...")
                delay(TOKEN_RETRY_DELAY_MS)
                return makeAuthenticatedRequest(url, method, token, body, retryCount + 1)
            }
            
            Timber.e("All retry attempts exhausted ($TOKEN_MAX_RETRY_ATTEMPTS attempts). Returning 401 with detailed error.")
            // Return more informative error for UI
            return GoogleDriveHttpClient.ApiResponse(
                isSuccess = false,
                httpCode = 401,
                data = null,
                errorMessage = "Authentication expired after $TOKEN_MAX_RETRY_ATTEMPTS retry attempts. Token invalid or revoked. Please re-authenticate in Settings → Edit Resource."
            )
        }
        
        return response
    }
    
    /**
     * Parse JSON array of File objects
     */
    private fun parseItems(items: JSONArray, parentPath: String): List<CloudFile> {
        val cloudFiles = mutableListOf<CloudFile>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            cloudFiles.add(parseItem(item, parentPath))
        }
        return cloudFiles
    }
    
    /**
     * Parse single File JSON to CloudFile
     */
    private fun parseItem(item: JSONObject, parentPath: String): CloudFile {
        val id = item.getString("id")
        val name = item.getString("name")
        val mimeType: String? = item.optString("mimeType").takeIf { it.isNotEmpty() }
        val isFolder = mimeType == MIME_TYPE_FOLDER
        val size = item.optLong("size", 0L)
        val modifiedTime = item.optString("modifiedTime", "")
        
        // Parse RFC 3339 date to timestamp
        val modifiedDate = try {
            if (modifiedTime.isNotEmpty()) {
                // Simple RFC 3339 parsing (assumes format: 2024-11-17T12:00:00.000Z)
                val instant = java.time.Instant.parse(modifiedTime)
                instant.toEpochMilli()
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
        
        val thumbnailUrl: String? = item.optString("thumbnailLink").takeIf { it.isNotEmpty() }
        val webViewUrl: String? = item.optString("webViewLink").takeIf { it.isNotEmpty() }
        
        return CloudFile(
            id = id,
            name = name,
            path = "$parentPath/$name",
            isFolder = isFolder,
            size = size,
            modifiedDate = modifiedDate,
            mimeType = mimeType,
            thumbnailUrl = thumbnailUrl,
            webViewUrl = webViewUrl
        )
    }
}
