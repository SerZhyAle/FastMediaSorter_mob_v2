package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.core.logging.CorrelationContext
import com.sza.fastmediasorter.core.logging.StructuredLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for cloud storage authentication management.
 *
 * Handles:
 * - Getting authenticated cloud clients with auto-restoration
 * - Automatic silent re-authentication on auth failures
 * - Retrying operations after successful re-auth
 *
 * Delegates state tracking to [CloudAuthStateMachine] (P3 / B1 Unified Auth).
 */
@Singleton
class CloudAuthenticationHelper @Inject constructor(
    private val googleDriveClient: GoogleDriveRestClient,
    private val dropboxClient: DropboxClient,
    private val oneDriveClient: OneDriveRestClient,
    /** Unified auth state machine — updated on every auth/restore outcome. */
    private val authStateMachine: CloudAuthStateMachine
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
     * Get cloud client for provider, initializing from stored credentials if needed.
     * Also updates [CloudAuthStateMachine] state for observability.
     *
     * @return CloudClientResult with client if authenticated, AuthRequired if needs auth
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
            // Delegate restore to the unified auth state machine
            val smResult = authStateMachine.authenticateOrRestore(provider)

            when (smResult) {
                is CloudResult.Success -> {
                    StructuredLogger.i("Auto-restored client via state machine", "provider" to provider.name, "account" to smResult.data)
                    CloudClientResult.Success(client)
                }
                is CloudResult.Error -> {
                    StructuredLogger.w("State machine failed to restore client", "provider" to provider.name, "msg" to smResult.message)
                    CloudClientResult.AuthRequired(provider)
                }
            }
        }
    }

    /**
     * Get cloud client for provider, initializing from stored credentials if needed.
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
            authStateMachine.onAuthError(provider, result.message)

            return CorrelationContext.start("cloud-reauth", mapOf("provider" to provider.name)) {
                // Attempt silent re-authentication via state machine
                val smResult = authStateMachine.authenticateOrRestore(provider)

                when (smResult) {
                    is CloudResult.Success -> {
                        StructuredLogger.i("Silent re-auth successful, retrying")
                        operation(client)  // Retry once
                    }
                    is CloudResult.Error -> {
                        StructuredLogger.e("Re-auth failed", "msg" to smResult.message)
                        null
                    }
                }
            }
        }

        return result
    }
}


