package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.core.logging.CorrelationContext
import com.sza.fastmediasorter.core.logging.StructuredLogger
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for cloud storage authentication management.
 * 
 * Handles:
 * - Getting authenticated cloud clients with auto-restoration
 * - Automatic silent re-authentication on auth failures
 * - Retrying operations after successful re-auth
 */
@Singleton
class CloudAuthenticationHelper @Inject constructor(
    private val googleDriveClient: GoogleDriveRestClient,
    private val dropboxClient: DropboxClient,
    private val oneDriveClient: OneDriveRestClient
) {

    /**
     * Result of getting cloud client
     */
    sealed class CloudClientResult {
        data class Success(val client: CloudStorageClient) : CloudClientResult()
        data class AuthRequired(val provider: CloudProvider) : CloudClientResult()
        data object NotSupported : CloudClientResult()
    }

    /**
     * Get cloud client for provider, initializing from stored credentials if needed
     * @return CloudClientResult with client if authenticated, AuthRequired if needs auth, NotSupported if provider unknown
     */
    suspend fun getCloudClientResult(provider: CloudProvider): CloudClientResult {
        val client = when (provider) {
            CloudProvider.GOOGLE_DRIVE -> googleDriveClient
            CloudProvider.DROPBOX -> dropboxClient
            CloudProvider.ONEDRIVE -> oneDriveClient
        }
        
        // Check if already authenticated
        if (client.isAuthenticated()) {
            return CloudClientResult.Success(client)
        }
        
        return CorrelationContext.start("cloud-restore", mapOf("provider" to provider.name)) {
            // Try to restore from client's own encrypted storage
            val restored = when (provider) {
                CloudProvider.DROPBOX -> (client as? DropboxClient)?.tryRestoreFromStorage() == true
                CloudProvider.GOOGLE_DRIVE -> (client as? GoogleDriveRestClient)?.tryRestoreFromStorage() == true
                CloudProvider.ONEDRIVE -> {
                     (client as? OneDriveRestClient)?.initialize("{}") == true
                }
            }
            
            if (restored) {
                StructuredLogger.i("Auto-restored client", "provider" to provider.name)
                CloudClientResult.Success(client)
            } else {
                StructuredLogger.w("Failed to restore client", "provider" to provider.name)
                CloudClientResult.AuthRequired(provider)
            }
        }
    }

    /**
     * Get cloud client for provider, initializing from stored credentials if needed
     * @return CloudStorageClient if authenticated, null otherwise
     */
    suspend fun getCloudClient(provider: CloudProvider): CloudStorageClient? {
        return when (val result = getCloudClientResult(provider)) {
            is CloudClientResult.Success -> result.client
            else -> null
        }
    }

    /**
     * Execute operation with automatic re-authentication on auth errors.
     * If operation fails with authentication error, attempts silent re-authentication and retries once.
     * 
     * @param provider Cloud provider
     * @param operation Suspending operation that returns CloudResult<T>
     * @return Operation result or null if re-auth failed/cancelled
     */
    suspend fun <T> executeWithAutoReauth(
        provider: CloudProvider,
        operation: suspend (CloudStorageClient) -> CloudResult<T>
    ): CloudResult<T>? {
        val client = getCloudClient(provider) ?: return null
        
        val result = operation(client)
        
        // Check if authentication error
        if (result is CloudResult.Error && result.message.contains("Not authenticated", ignoreCase = true)) {
            StructuredLogger.w("Auth error detected, attempting silent re-auth", "provider" to provider.name)
            
            return CorrelationContext.start("cloud-reauth", mapOf("provider" to provider.name)) {
                // Attempt silent re-authentication
                val reAuthResult = when (provider) {
                    CloudProvider.GOOGLE_DRIVE -> {
                        try {
                            googleDriveClient.authenticate()
                        } catch (e: Exception) {
                            StructuredLogger.e(e, "Silent re-auth failed")
                            AuthResult.Error("Re-authentication failed: ${e.message}")
                        }
                    }
                    else -> AuthResult.Error("Auto re-authentication not supported for $provider")
                }
                
                when (reAuthResult) {
                    is AuthResult.Success -> {
                        StructuredLogger.i("Silent re-auth successful, retrying")
                        operation(client)  // Retry once
                    }
                    is AuthResult.Error -> {
                        StructuredLogger.e("Re-auth failed", "msg" to reAuthResult.message)
                        null
                    }
                    is AuthResult.Cancelled -> {
                        StructuredLogger.w("Re-auth cancelled")
                        null
                    }
                }
            }
        }
        
        return result
    }
}
