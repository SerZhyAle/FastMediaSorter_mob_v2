package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalDeclinedScopeException
import com.microsoft.identity.client.exception.MsalException
import com.sza.fastmediasorter.data.local.db.PendingRevocationDao
import com.sza.fastmediasorter.data.local.db.PendingRevocationEntity
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OneDrive implementation of CloudStorageClient using Microsoft Graph REST API v1.0
 * 
 * REST API approach avoids Graph SDK v5 CompletableFuture/Kotlin coroutine incompatibilities
 * 
 * Authentication: MSAL 6.0.1 OAuth 2.0 flow
 * API: Direct HTTP calls to graph.microsoft.com/v1.0
 * 
 * Endpoints:
 * - /me/drive - Get drive metadata
 * - /me/drive/root/children - List root files
 * - /me/drive/items/{id}/children - List folder contents
 * - /me/drive/items/{id} - Get/update/delete item
 * - /me/drive/items/{id}/content - Download/upload file
 * - /me/drive/items/{id}/thumbnails - Get thumbnails
 * 
 * Reference: https://learn.microsoft.com/en-us/graph/api/resources/onedrive
 */
import kotlinx.coroutines.DelicateCoroutinesApi

@Singleton
class OneDriveRestClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingRevocationDao: PendingRevocationDao,
    private val networkCredentialsRepository: NetworkCredentialsRepository
) : CloudStorageClient {
    
    override val provider = CloudProvider.ONEDRIVE
    
    private var msalApp: ISingleAccountPublicClientApplication? = null
    private var accessToken: String? = null
    private var accountEmail: String? = null
    private var tokenTimestamp: Long = 0L  // Track when token was obtained
    
    override fun isAuthenticated(): Boolean = accessToken != null
    
    companion object {
        private const val GRAPH_API_BASE = "https://graph.microsoft.com/v1.0"
        val SCOPES = arrayOf("Files.ReadWrite.All", "offline_access")
        /** Best-effort token revoke endpoint; POST token=<accessToken> form-encoded */
        private const val MSONLINE_REVOKE_URL =
            "https://login.microsoftonline.com/common/oauth2/v2.0/logout"
        
        // MIME types for filtering
        private const val FOLDER_MIME_TYPE = "application/vnd.microsoft.folder"
        
        // Token management
        private const val TOKEN_REFRESH_THRESHOLD_MS = 50 * 60 * 1000L  // 50 minutes
        private const val TOKEN_MAX_RETRY_ATTEMPTS = 3
        private const val TOKEN_RETRY_DELAY_MS = 2000L  // 2 seconds between retries
    }
    
    /**
     * Initialize MSAL application
     */
    private suspend fun initializeMsal(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context,
                com.sza.fastmediasorter.R.raw.msal_config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        msalApp = application
                        Timber.d("MSAL app initialized successfully")
                        continuation.resume(true)
                    }
                    
                    override fun onError(exception: MsalException) {
                        Timber.e(exception, "MSAL initialization failed")
                        continuation.resume(false)
                    }
                }
            )
        }
    }
    
    /**
     * Start OAuth 2.0 authentication flow
     * Must be called from Activity context
     */
    override suspend fun authenticate(): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                // Initialize MSAL if needed
                if (msalApp == null) {
                    val initialized = initializeMsal()
                    if (!initialized) {
                        return@withContext AuthResult.Error("Failed to initialize MSAL")
                    }
                }
                
                val app = msalApp ?: return@withContext AuthResult.Error("MSAL not initialized")
                
                // Check if already signed in
                val account = app.currentAccount.currentAccount
                Timber.d("OneDrive authenticate: cachedAccount=${account?.username ?: "none"}")
                if (account != null) {
                    // Try silent authentication
                    val result = acquireTokenSilently(account)
                    if (result != null) {
                        accessToken = result.accessToken
                        tokenTimestamp = System.currentTimeMillis()
                        accountEmail = result.account.username
                        Timber.i("OneDrive silent auth success: $accountEmail")
                        return@withContext AuthResult.Success(
                            accountName = accountEmail ?: "Unknown",
                            credentialsJson = serializeAccount(result.account)
                        )
                    }
                    Timber.w("OneDrive silent auth failed, interactive sign-in required")
                } else {
                    Timber.d("OneDrive: no cached account, interactive sign-in required")
                }
                
                // Need interactive authentication - must be initiated from Activity via AddResourceActivity
                AuthResult.Error("Interactive sign-in required")
            } catch (e: Exception) {
                Timber.e(e, "OneDrive authentication failed")
                AuthResult.Error("Authentication failed: ${e.message}")
            }
        }
    }
    
    /**
     * Start interactive sign-in flow
     */
    fun signIn(activity: Activity, callback: (AuthResult) -> Unit) {
        if (msalApp == null) {
            Timber.d("MSAL not yet initialized, initializing before signIn...")
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context,
                com.sza.fastmediasorter.R.raw.msal_config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        msalApp = application
                        Timber.d("MSAL initialized successfully in signIn, proceeding")
                        signInWithApp(activity, application, callback)
                    }

                    override fun onError(exception: MsalException) {
                        Timber.e(exception, "MSAL initialization failed in signIn")
                        callback(AuthResult.Error("MSAL initialization failed: ${exception.message}"))
                    }
                }
            )
            return
        }
        val app = msalApp!!
        signInWithApp(activity, app, callback)
    }

    /**
     * Proceed with sign-in after MSAL is confirmed initialized.
     * Handles sign-out of existing account if needed, then calls signInInternal.
     */
    private fun signInWithApp(activity: Activity, app: ISingleAccountPublicClientApplication, callback: (AuthResult) -> Unit) {
        val currentAccount = try {
            app.currentAccount.currentAccount
        } catch (e: Exception) { null }

        if (currentAccount != null) {
            Timber.d("Account already exists, signing out before interactive sign-in")
            app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    signInInternal(activity, app, callback)
                }

                override fun onError(exception: MsalException) {
                    Timber.e(exception, "Sign-out failed during re-login attempt")
                    callback(AuthResult.Error("Re-login failed during sign-out: ${exception.message}"))
                }
            })
        } else {
            signInInternal(activity, app, callback)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun signInInternal(
        activity: Activity,
        app: ISingleAccountPublicClientApplication,
        callback: (AuthResult) -> Unit
    ) {
        Timber.d("OneDrive signInInternal: starting interactive login, scopes=${SCOPES.toList()}")
        @Suppress("DEPRECATION")
        app.signIn(activity, null, SCOPES, object : AuthenticationCallback {
            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                // Handle result on background dispatcher because handleAuthenticationResult updates token/email
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                    val result = handleAuthenticationResult(authenticationResult)
                    callback(result)
                }
            }
            
            
            override fun onError(exception: MsalException) {
                // MsalDeclinedScopeException logic is handled in acquireTokenSilently for silent flow,
                // but for interactive flow we might see it here too.
                if (exception is MsalDeclinedScopeException) {
                     val grantedScopes = exception.grantedScopes
                     if (grantedScopes.contains("Files.ReadWrite.All")) {
                         // Interactive login succeeded partially. We have a user now.
                         // We need to get the token for granted scopes.
                         Timber.w("Interactive declined scopes, proceeding with granted: $grantedScopes")
                         
                         kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            // Wait a bit for MSAL to update internal state if needed?
                            // account should be available now
                             val currentAccount = app.currentAccount.currentAccount
                             if (currentAccount != null) {
                                 val result = acquireTokenSilently(currentAccount, grantedScopes.toTypedArray())
                                 if (result != null) {
                                     kotlinx.coroutines.withContext(Dispatchers.Main) {
                                         accessToken = result.accessToken
                                         tokenTimestamp = System.currentTimeMillis()
                                         accountEmail = result.account.username
                                         callback(AuthResult.Success(
                                             accountName = accountEmail ?: "Unknown",
                                             credentialsJson = serializeAccount(result.account)
                                         ))
                                     }
                                 } else {
                                     kotlinx.coroutines.withContext(Dispatchers.Main) {
                                         callback(AuthResult.Error("Failed to acquire token with granted scopes after interactive login"))
                                     }
                                 }
                             } else {
                                 kotlinx.coroutines.withContext(Dispatchers.Main) {
                                     callback(AuthResult.Error("Partial success but no account"))
                                 }
                             }
                         }
                         return
                     }
                }
                
                Timber.e(exception, "Interactive sign-in failed")
                callback(AuthResult.Error("Sign-in failed: ${exception.message}"))
            }
            
            override fun onCancel() {
                Timber.d("Interactive sign-in cancelled")
                callback(AuthResult.Cancelled)
            }
        })
    }
    
    /**
     * Acquire token silently for cached account
     */
    private suspend fun acquireTokenSilently(
        account: IAccount, 
        scopes: Array<String> = SCOPES
    ): IAuthenticationResult? {
        return suspendCancellableCoroutine { continuation ->
            val app = msalApp ?: run {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            
            val scopesToUse = scopes.ifEmpty { SCOPES }
            
            @Suppress("DEPRECATION")
            app.acquireTokenSilentAsync(
                scopesToUse,
                account.authority,
                object : SilentAuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        Timber.d("Silent token acquisition successful")
                        continuation.resume(authenticationResult)
                    }
                    
                    override fun onError(exception: MsalException) {
                        if (exception is MsalDeclinedScopeException) {
                            val grantedScopes = exception.grantedScopes
                            // Check if we have essential scopes
                            if (grantedScopes.contains("Files.ReadWrite.All")) {
                                Timber.w("MsalDeclinedScopeException caught. Retrying with granted scopes only: $grantedScopes")
                                
                                // Retry with reduced scopes
                                app.acquireTokenSilentAsync(
                                    grantedScopes.toTypedArray(),
                                    account.authority,
                                    object : SilentAuthenticationCallback {
                                        override fun onSuccess(res: IAuthenticationResult) {
                                            Timber.d("Retry silent auth with granted scopes successful")
                                            continuation.resume(res)
                                        }
                                        
                                        override fun onError(e: MsalException) {
                                            Timber.e(e, "Retry silent auth failed")
                                            continuation.resume(null)
                                        }
                                    }
                                )
                                return
                            }
                        }
                        
                        Timber.w(exception, "Silent token acquisition failed")
                        continuation.resume(null)
                    }
                }
            )
        }
    }
    
    /**
     * Handle interactive authentication result
     * Call from Activity after user completes OAuth flow
     */
    suspend fun handleAuthenticationResult(result: IAuthenticationResult?): AuthResult {
        return if (result != null) {
            accessToken = result.accessToken
            tokenTimestamp = System.currentTimeMillis()
            accountEmail = result.account.username
            
            // Make sure this account is registered in NetworkCredentialsEntity for multi-account picker
            accountEmail?.let { email ->
                val existing = networkCredentialsRepository.getByTypeAndAccountId(CloudProvider.ONEDRIVE.name, email)
                if (existing == null) {
                    val entity = com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity.create(
                        credentialId = java.util.UUID.randomUUID().toString(),
                        type = CloudProvider.ONEDRIVE.name,
                        server = "",
                        port = 0,
                        username = email,
                        plaintextPassword = "", // MSAL handles its own token cache
                        accountId = email
                    )
                    networkCredentialsRepository.insert(entity)
                    Timber.d("Registered OneDrive account in database: $email")
                }
            }
            
            AuthResult.Success(
                accountName = accountEmail ?: "Unknown",
                credentialsJson = serializeAccount(result.account)
            )
        } else {
            AuthResult.Error("Authentication failed or cancelled")
        }
    }
    
    /**
     * Serialize account info for storage
     */
    private fun serializeAccount(account: IAccount): String {
        return JSONObject().apply {
            put("username", account.username)
            put("id", account.id)
            put("authority", account.authority)
        }.toString()
    }
    
    /**
     * Deserialize account info
     */
    private fun deserializeAccount(json: String): String {
        return try {
            val obj = JSONObject(json)
            if (obj.has("username")) {
                obj.getString("username")
            } else {
                // Fallback for legacy or malformed JSON
                ""
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize account")
            ""
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
            val app = msalApp ?: return
            val account = try {
                app.currentAccount.currentAccount
            } catch (e: Exception) {
                Timber.w(e, "Failed to get current account for token refresh")
                return
            }
            
            if (account != null) {
                val result = acquireTokenSilently(account)
                if (result != null) {
                    accessToken = result.accessToken
                    tokenTimestamp = System.currentTimeMillis()
                    accountEmail = result.account.username
                    Timber.i("Token proactively refreshed successfully")
                } else {
                    Timber.w("Failed to proactively refresh token")
                }
            }
        }
    }
    
    override suspend fun initialize(credentialsJson: String): Boolean {
        return try {
            if (msalApp == null) {
                val initialized = initializeMsal()
                if (!initialized) return false
            }
            
            val app = msalApp ?: return false
            val account = app.currentAccount.currentAccount
            
            if (account != null) {
                val username = deserializeAccount(credentialsJson)
                // If username is empty or matches current account, proceed
                if (username.isEmpty() || account.username == username) {
                    // Try to get fresh token
                    val result = acquireTokenSilently(account)
                    if (result != null) {
                        accessToken = result.accessToken
                        tokenTimestamp = System.currentTimeMillis()
                        accountEmail = result.account.username
                        Timber.d("OneDrive initialized successfully with account: ${result.account.username}")
                        true
                    } else {
                        Timber.w("Failed to acquire token silently")
                        false
                    }
                } else {
                    Timber.w("Stored account ($username) doesn't match current account (${account.username})")
                    false
                }
            } else {
                Timber.w("No account signed in")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize OneDrive client")
            false
        }
    }
    
    override suspend fun testConnection(): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val url = URL("$GRAPH_API_BASE/me/drive")
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess) {
                    // Update account email if not set
                    if (accountEmail == null) {
                        try {
                            val userUrl = URL("$GRAPH_API_BASE/me")
                            val userResponse = makeAuthenticatedRequest(userUrl, "GET", token)
                            if (userResponse.isSuccess) {
                                val userJson = JSONObject(userResponse.data ?: "{}")
                                accountEmail = userJson.optString("userPrincipalName") 
                                    ?: userJson.optString("mail")
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to fetch user email")
                        }
                    }
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
    
    /**
     * Get current account email
     */
    fun getAccountEmail(): String? = accountEmail
    
    /**
     * Resolve folder ID from name if necessary.
     * If the ID looks like a name (short), it ensures the folder exists and returns its ID.
     */
    private suspend fun resolveOrEnsureFolder(idOrName: String?): String? {
        // Handle null or empty string as root folder
        if (idOrName.isNullOrEmpty()) return null
        
        // Simple heuristic: OneDrive IDs are usually alphanumeric and long (e.g. 19 chars or more)
        // IDs often look like: 5889656D12345678!123
        // Names like "test_media" are unlikely to contain '!' and are usually simpler.
        // But let's be safe: if we fail to use it as ID, we might fallback? 
        // No, better to try to resolve if it looks like a name.
        if (!idOrName.contains("!") && idOrName.length < 32) {
             Timber.d("Resolving folder name '$idOrName' to ID...")
             val result = ensureFolderExists(idOrName)
             if (result is CloudResult.Success) {
                 Timber.d("Resolved '$idOrName' to ID: ${result.data.id}")
                 return result.data.id
             }
        }
        return idOrName
    }

    private fun normalizeCloudItemReference(fileId: String): String {
        return if (fileId.startsWith("cloud://onedrive/")) {
            fileId.substringAfter("cloud://onedrive/")
        } else {
            fileId
        }
    }

    private suspend fun buildItemUrlFromReference(fileRef: String): URL {
        val actualRef = normalizeCloudItemReference(fileRef)

        return if (actualRef.contains("/")) {
            val parts = actualRef.split("/", limit = 2)
            val resolvedFolderId = resolveOrEnsureFolder(parts[0])
            val encodedFileName = Uri.encode(parts[1])

            if (resolvedFolderId.isNullOrEmpty()) {
                URL("$GRAPH_API_BASE/me/drive/root:/$encodedFileName:")
            } else {
                URL("$GRAPH_API_BASE/me/drive/items/$resolvedFolderId:/$encodedFileName:")
            }
        } else {
            URL("$GRAPH_API_BASE/me/drive/items/$actualRef")
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
                
                // Resolve folder ID if it's a name (e.g. "test_media")
                val resolvedFolderId = resolveOrEnsureFolder(folderId)
                
                val endpoint = if (resolvedFolderId != null) {
                    "$GRAPH_API_BASE/me/drive/items/$resolvedFolderId/children?\$expand=thumbnails"
                } else {
                    "$GRAPH_API_BASE/me/drive/root/children?\$expand=thumbnails"
                }
                
                val url = URL(endpoint)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val items = json.getJSONArray("value")
                    val cloudFiles = parseItems(items, folderId ?: "root")
                    
                    val nextLink: String? = json.optString("@odata.nextLink").takeIf { it.isNotEmpty() }
                    val nextToken = nextLink?.substringAfterLast("skiptoken=")
                    
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
                
                val endpoint = if (parentFolderId != null) {
                    "$GRAPH_API_BASE/me/drive/items/$parentFolderId/children?\$filter=folder ne null"
                } else {
                    "$GRAPH_API_BASE/me/drive/root/children?\$filter=folder ne null"
                }
                
                val url = URL(endpoint)
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val items = json.getJSONArray("value")
                    val folders = parseItems(items, parentFolderId ?: "root")
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
                Timber.d("OneDrive.getFileMetadata: Original fileId='$fileId'")
                
                // Strip cloud:// prefix if present
                val actualFileId = if (fileId.startsWith("cloud://onedrive/")) {
                    fileId.substringAfter("cloud://onedrive/")
                } else {
                    fileId
                }
                
                Timber.d("OneDrive.getFileMetadata: Processed fileId='$actualFileId'")
                Timber.d("OneDrive.getFileMetadata: File ID format analysis - contains '!': ${actualFileId.contains("!")}, contains '/': ${actualFileId.contains("/")}, length: ${actualFileId.length}")
                
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val url = URL("$GRAPH_API_BASE/me/drive/items/$actualFileId")
                Timber.d("OneDrive.getFileMetadata: Request URL: $url")
                
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parentId = json.optJSONObject("parentReference")?.optString("id", "root") ?: "root"
                    val fileName = json.optString("name", "unknown")
                    Timber.i("OneDrive.getFileMetadata: SUCCESS - fileName='$fileName', parentId='$parentId'")
                    CloudResult.Success(parseItem(json, parentId))
                } else {
                    Timber.e("OneDrive.getFileMetadata: FAILED - ${response.errorMessage}")
                    CloudResult.Error("Failed to get metadata: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "OneDrive.getFileMetadata: EXCEPTION for fileId='$fileId'")
                CloudResult.Error("Failed to get metadata: ${e.message}", e)
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
                Timber.d("OneDrive.downloadFile: START - Original fileId='$fileId'")
                
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val actualFileId = normalizeCloudItemReference(fileId)
                
                Timber.d("OneDrive.downloadFile: Processed fileId='$actualFileId'")
                Timber.d("OneDrive.downloadFile: File ID analysis - contains '!': ${actualFileId.contains("!")}, contains '/': ${actualFileId.contains("/")}, length: ${actualFileId.length}")

                val metadataUrl = buildItemUrlFromReference(fileId)
                
                Timber.d("OneDrive.downloadFile: Metadata request URL: $metadataUrl")
                val metadataResponse = makeAuthenticatedRequest(metadataUrl, "GET", token)
                
                if (!metadataResponse.isSuccess || metadataResponse.data == null) {
                    Timber.e("OneDrive.downloadFile: Failed to get metadata - ${metadataResponse.errorMessage}")
                    return@withContext CloudResult.Error("Failed to get download URL: ${metadataResponse.errorMessage}")
                }
                
                val json = JSONObject(metadataResponse.data)
                val downloadUrl = json.optString("@microsoft.graph.downloadUrl")
                val size = json.optLong("size", 0L)
                val fileName = json.optString("name", "unknown")
                
                Timber.i("OneDrive.downloadFile: Metadata retrieved - fileName='$fileName', size=$size bytes")
                
                if (downloadUrl.isEmpty()) {
                    Timber.e("OneDrive.downloadFile: Download URL not available in metadata")
                    return@withContext CloudResult.Error("Download URL not available")
                }
                
                Timber.d("OneDrive.downloadFile: Download URL obtained, starting download...")
                
                // Download file
                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
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
                    Timber.i("OneDrive.downloadFile: SUCCESS - Downloaded $totalBytes bytes for '$fileName'")
                    CloudResult.Success(true)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "OneDrive.downloadFile: EXCEPTION for fileId='$fileId'")
                CloudResult.Error("Download failed: ${e.message}", e)
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
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                // Resolve parent folder ID if it's a name
                val resolvedParentId = resolveOrEnsureFolder(parentFolderId)
                
                val endpoint = if (resolvedParentId != null) {
                    "$GRAPH_API_BASE/me/drive/items/$resolvedParentId:/$fileName:/content"
                } else {
                    "$GRAPH_API_BASE/me/drive/root:/$fileName:/content"
                }
                
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", mimeType)
                connection.doOutput = true
                
                try {
                    val outputStream = connection.outputStream
                    val buffer = ByteArray(65536) // 64KB buffer for better network throughput
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        progressCallback?.invoke(TransferProgress(totalBytes, 0L))
                    }
                    
                    outputStream.flush()
                    
                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        val responseData = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(responseData)
                        val parentId = json.optJSONObject("parentReference")?.optString("id", "root") ?: "root"
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
                
                // Resolve parent folder ID if it's a name
                val resolvedParentId = resolveOrEnsureFolder(parentFolderId)
                
                val endpoint = if (resolvedParentId != null) {
                    "$GRAPH_API_BASE/me/drive/items/$resolvedParentId/children"
                } else {
                    "$GRAPH_API_BASE/me/drive/root/children"
                }
                
                val requestBody = JSONObject().apply {
                    put("name", folderName)
                    put("folder", JSONObject())
                    put("@microsoft.graph.conflictBehavior", "rename")
                }.toString()
                
                val url = URL(endpoint)
                val response = makeAuthenticatedRequest(url, "POST", token, requestBody)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parentId = json.optJSONObject("parentReference")?.optString("id", "root") ?: "root"
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
                // List all folders in parent and find exact match
                val listResult = listFolders(parentFolderId)
                
                when (listResult) {
                    is CloudResult.Success -> {
                        val matchingFolder = listResult.data.firstOrNull { it.name == folderName }
                        CloudResult.Success(matchingFolder)
                    }
                    is CloudResult.Error -> {
                        Timber.e("Failed to list folders: ${listResult.message}")
                        listResult
                    }
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
                        Timber.e("ensureFolderExists: List failed: ${findResult.message}")
                        findResult
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "ensureFolderExists failed")
                CloudResult.Error("ensureFolderExists failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun deleteFile(fileId: String): CloudResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("OneDrive.deleteFile: Original fileId='$fileId'")
                
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                val actualFileId = normalizeCloudItemReference(fileId)
                
                Timber.d("OneDrive.deleteFile: Processed fileId='$actualFileId'")
                val url = buildItemUrlFromReference(fileId)
                
                Timber.d("OneDrive.deleteFile: Request URL: $url")
                val response = makeAuthenticatedRequest(url, "DELETE", token)
                
                if (response.isSuccess) {
                    Timber.i("OneDrive.deleteFile: SUCCESS for fileId='$actualFileId'")
                    CloudResult.Success(true)
                } else {
                    Timber.e("OneDrive.deleteFile: FAILED - ${response.errorMessage}")
                    CloudResult.Error("Failed to delete: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "OneDrive.deleteFile: EXCEPTION for fileId='$fileId'")
                CloudResult.Error("Deletion failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun renameFile(fileId: String, newName: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("OneDrive.renameFile: fileId='$fileId', newName='$newName'")
                
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val requestBody = JSONObject().apply {
                    put("name", newName)
                }.toString()
                
                val actualFileId = normalizeCloudItemReference(fileId)
                
                Timber.d("OneDrive.renameFile: Processed fileId='$actualFileId'")
                val url = buildItemUrlFromReference(fileId)
                
                Timber.d("OneDrive.renameFile: Request URL: $url")
                val response = makeAuthenticatedRequest(url, "PATCH", token, requestBody)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val parentId = json.optJSONObject("parentReference")?.optString("id", "root") ?: "root"
                    Timber.i("OneDrive.renameFile: SUCCESS - renamed to '$newName'")
                    CloudResult.Success(parseItem(json, parentId))
                } else {
                    Timber.e("OneDrive.renameFile: FAILED - ${response.errorMessage}")
                    CloudResult.Error("Failed to rename: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "OneDrive.renameFile: EXCEPTION for fileId='$fileId'")
                CloudResult.Error("Rename failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun moveFile(fileId: String, newParentId: String): CloudResult<CloudFile> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val requestBody = JSONObject().apply {
                    put("parentReference", JSONObject().apply {
                        put("id", newParentId)
                    })
                }.toString()
                
                val url = buildItemUrlFromReference(fileId)
                
                val response = makeAuthenticatedRequest(url, "PATCH", token, requestBody)
                
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
                    put("parentReference", JSONObject().apply {
                        put("id", newParentId)
                    })
                    if (newName != null) {
                        put("name", newName)
                    }
                }.toString()
                
                // Strip cloud:// prefix if present
                val actualFileId = if (fileId.startsWith("cloud://onedrive/")) {
                    fileId.substringAfter("cloud://onedrive/")
                } else {
                    fileId
                }

                val url = URL("$GRAPH_API_BASE/me/drive/items/$actualFileId/copy")
                val response = makeAuthenticatedRequest(url, "POST", token, requestBody)
                
                if (response.isSuccess) {
                    // Copy is async, returns 202 Accepted with Location header
                    // For now, return success without waiting for completion
                    CloudResult.Success(CloudFile(
                        id = fileId,
                        name = newName ?: "copying...",
                        path = newParentId,
                        isFolder = false,
                        size = 0,
                        modifiedDate = System.currentTimeMillis(),
                        mimeType = null,
                        thumbnailUrl = null,
                        webViewUrl = null
                    ))
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
                
                val endpoint = if (parentId == "root" || parentId.isEmpty()) {
                    "$GRAPH_API_BASE/me/drive/root/children"
                } else {
                    "$GRAPH_API_BASE/me/drive/items/$parentId/children"
                }
                
                val filter = "name eq '$fileName'"
                val encodedFilter = java.net.URLEncoder.encode(filter, "UTF-8")
                val url = URL("$endpoint?\$filter=$encodedFilter")
                
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val items = json.getJSONArray("value")
                    CloudResult.Success(items.length() > 0)
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
                
                val url = URL("$GRAPH_API_BASE/me/drive/root/search(q='$query')")
                val response = makeAuthenticatedRequest(url, "GET", token)
                
                if (response.isSuccess && response.data != null) {
                    val json = JSONObject(response.data)
                    val items = json.getJSONArray("value")
                    val cloudFiles = parseItems(items, "search")
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
    
    override suspend fun getThumbnail(fileId: String, size: Int): CloudResult<InputStream> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                val thumbnailSize = when {
                    size <= 96 -> "small"   // 96x96
                    size <= 176 -> "medium" // 176x176
                    size <= 800 -> "large"  // 800x800
                    else -> "large"
                }
                
                val url = URL("$GRAPH_API_BASE/me/drive/items/$fileId/thumbnails/0/$thumbnailSize/content")
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
     * Get input stream for file content (for streaming)
     * Used by ExoPlayer's CloudDataSource for video/audio streaming
     * Supports HTTP Range requests for seeking
     */
    override suspend fun getFileInputStream(
        fileId: String,
        position: Long,
        length: Long
    ): CloudResult<InputStream> {
        return withContext(Dispatchers.IO) {
            try {
                val token = accessToken ?: return@withContext CloudResult.Error("Not authenticated")
                
                Timber.d("OneDrive.getFileInputStream: START - fileId='$fileId', position=$position, length=$length")
                
                // Parse fileId (handle both direct ID and "folderId/filename" format)
                val actualFileId = if (fileId.startsWith("cloud://onedrive/")) {
                    fileId.substringAfter("cloud://onedrive/")
                } else {
                    fileId
                }
                
                Timber.d("OneDrive.getFileInputStream: Processed fileId='$actualFileId'")
                
                val metadataUrl = buildItemUrlFromReference(fileId)
                val url = URL("${metadataUrl}/content")
                
                Timber.d("OneDrive.getFileInputStream: Request URL: $url")
                
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer $token")
                
                // Add Range header for streaming support
                if (position > 0 || length != -1L) {
                    val rangeHeader = if (length == -1L) {
                        "bytes=$position-"
                    } else {
                        "bytes=$position-${position + length - 1}"
                    }
                    connection.setRequestProperty("Range", rangeHeader)
                    Timber.d("OneDrive.getFileInputStream: Range header: $rangeHeader")
                }
                
                val responseCode = connection.responseCode
                Timber.d("OneDrive.getFileInputStream: Response code: $responseCode")
                
                if (responseCode == 200 || responseCode == 206) {
                    Timber.i("OneDrive.getFileInputStream: SUCCESS - Stream opened (HTTP $responseCode)")
                    CloudResult.Success(connection.inputStream)
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "HTTP $responseCode"
                    connection.disconnect()
                    Timber.e("OneDrive.getFileInputStream: FAILED - HTTP $responseCode: $error")
                    CloudResult.Error("Download failed: $error")
                }
            } catch (e: Exception) {
                Timber.e(e, "OneDrive.getFileInputStream: Exception")
                CloudResult.Error("Failed to get input stream: ${e.message}", e)
            }
        }
    }
    
    override suspend fun signOut(): CloudResult<Boolean> {
        // Capture token before clearing — best-effort server-side revocation queued to DB
        val tokenToRevoke = accessToken

        // Local sign-out on Main thread
        var signOutError: CloudResult.Error? = null
        withContext(Dispatchers.Main) {
            try {
                msalApp?.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                    override fun onSignOut() {
                        Timber.d("OneDrive sign-out successful")
                    }

                    override fun onError(exception: MsalException) {
                        Timber.e(exception, "Sign-out error")
                    }
                })
                accessToken = null
                accountEmail = null
            } catch (e: Exception) {
                Timber.e(e, "Failed to sign out")
                signOutError = CloudResult.Error("Sign-out failed: ${e.message}", e)
            }
        }
        if (signOutError != null) return signOutError!!

        // Queue access token for best-effort revocation (MSAL clears refresh token locally).
        // Access tokens are short-lived (~1 h) but queuing provides an extra safety layer.
        if (tokenToRevoke != null) {
            withContext(Dispatchers.IO) {
                try {
                    pendingRevocationDao.insert(
                        PendingRevocationEntity(
                            provider = "onedrive",
                            token = tokenToRevoke,
                            revokeUrl = MSONLINE_REVOKE_URL
                        )
                    )
                    Timber.d("OneDriveRestClient: access token queued for revocation")
                } catch (e: Exception) {
                    Timber.w(e, "OneDriveRestClient: failed to queue token for revocation")
                }
            }
        }

        return CloudResult.Success(true)
    }
    
    /**
     * Make authenticated HTTP request to Graph API
     */
    private suspend fun makeAuthenticatedRequest(
        url: URL,
        method: String,
        token: String,
        body: String? = null,
        retryCount: Int = 0
    ): ApiResponse {
        var connection: HttpURLConnection? = null
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.bufferedWriter().use { it.write(body) }
            }
            
            val responseCode = connection.responseCode
            
            // Handle 401 Unauthorized - Token expired
            if (responseCode == 401 && retryCount < TOKEN_MAX_RETRY_ATTEMPTS) {
                connection.disconnect()
                Timber.w("Received 401 Unauthorized (attempt ${retryCount + 1}/$TOKEN_MAX_RETRY_ATTEMPTS). Attempting silent token refresh...")
                
                // Delay before retry to avoid hammering server
                if (retryCount > 0) {
                    delay(TOKEN_RETRY_DELAY_MS)
                }
                
                // Try to refresh token
                val app = msalApp
                val account = app?.currentAccount?.currentAccount
                if (app != null && account != null) {
                    val result = acquireTokenSilently(account)
                    if (result != null) {
                        accessToken = result.accessToken
                        tokenTimestamp = System.currentTimeMillis()
                        Timber.i("Silent token refresh successful. Retrying request (attempt ${retryCount + 2})...")
                        return makeAuthenticatedRequest(url, method, result.accessToken, body, retryCount + 1)
                    }
                }
                
                // If not last retry, try again with same token
                if (retryCount < TOKEN_MAX_RETRY_ATTEMPTS - 1) {
                    Timber.w("Silent token refresh failed, but will retry again...")
                    delay(TOKEN_RETRY_DELAY_MS)
                    return makeAuthenticatedRequest(url, method, token, body, retryCount + 1)
                }
                
                Timber.e("All retry attempts exhausted ($TOKEN_MAX_RETRY_ATTEMPTS attempts). Returning 401 error.")
                return ApiResponse(
                    isSuccess = false,
                    data = null,
                    errorMessage = "Authentication expired after $TOKEN_MAX_RETRY_ATTEMPTS retry attempts. Token invalid or revoked. Please re-authenticate in Settings → Edit Resource."
                )
            }
            
            return if (responseCode in 200..299) {
                val data = connection.inputStream.bufferedReader().use { it.readText() }
                ApiResponse(isSuccess = true, data = data, errorMessage = null)
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                ApiResponse(isSuccess = false, data = null, errorMessage = error)
            }
        } catch (e: Exception) {
            Timber.e(e, "Request failed: $method $url")
            return ApiResponse(isSuccess = false, data = null, errorMessage = e.message)
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Parse JSON array of DriveItem objects
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
     * Parse single DriveItem JSON to CloudFile
     */
    private fun parseItem(item: JSONObject, parentPath: String): CloudFile {
        val id = item.getString("id")
        val name = item.getString("name")
        val isFolder = item.has("folder")
        val size = item.optLong("size", 0L)
        val modifiedTime = item.optString("lastModifiedDateTime", "")
        val mimeType: String? = item.optString("mimeType").takeIf { it.isNotEmpty() }
        
        // Parse ISO 8601 date to timestamp
        val modifiedDate = try {
            if (modifiedTime.isNotEmpty()) {
                // Simple ISO 8601 parsing (assumes format: 2024-11-17T12:00:00Z)
                val instant = java.time.Instant.parse(modifiedTime)
                instant.toEpochMilli()
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
        
        // When using $expand=thumbnails, OneDrive returns 'thumbnails' as an array directly
        // Structure: item.thumbnails[0].large.url
        val thumbnailUrl = item.optJSONArray("thumbnails")
            ?.optJSONObject(0)
            ?.optJSONObject("large")
            ?.optString("url")
            ?.takeIf { it.isNotEmpty() }
        
        val webViewUrl: String? = item.optString("webUrl").takeIf { it.isNotEmpty() }
        
        return CloudFile(
            id = id,
            name = name,
            path = parentPath,
            isFolder = isFolder,
            size = size,
            modifiedDate = modifiedDate,
            mimeType = mimeType,
            thumbnailUrl = thumbnailUrl,
            webViewUrl = webViewUrl
        )
    }
    
    /**
     * API response wrapper
     */
    @Keep
    private data class ApiResponse(
        val isSuccess: Boolean,
        val data: String?,
        val errorMessage: String?
    )
}
