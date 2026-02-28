package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import com.sza.fastmediasorter.core.logging.StructuredLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the interactive cloud authentication flows across different providers
 * (Phase P3 / B1 Cloud Auth Unification).
 *
 * This manager delegates the actual UI flows to provider-specific plugins and
 * updates the [CloudAuthStateMachine] synchronously.
 */
@Singleton
class UnifiedCloudAuthManager @Inject constructor(
    private val authStateMachine: CloudAuthStateMachine,
    googleDriveAuthPlugin: GoogleDriveAuthPlugin,
    dropboxAuthPlugin: DropboxAuthPlugin,
    oneDriveAuthPlugin: OneDriveAuthPlugin
) {
    // Map of provider to its specific interactive authenticator plugin
    private val plugins = mapOf(
        CloudProvider.GOOGLE_DRIVE to googleDriveAuthPlugin,
        CloudProvider.DROPBOX to dropboxAuthPlugin,
        CloudProvider.ONEDRIVE to oneDriveAuthPlugin
    )

    // Tracks which provider is currently undergoing an interactive authentication flow
    private var activeProvider: CloudProvider? = null

    // Event stream for the UI (Activity) to observe success or failures
    private val _authEvents = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val authEvents = _authEvents.asSharedFlow()

    sealed class AuthEvent {
        data class Success(val provider: CloudProvider, val accountEmail: String) : AuthEvent()
        data class Error(val provider: CloudProvider, val message: String) : AuthEvent()
    }

    /**
     * Starts the interactive sign-in flow for the given provider.
     */
    fun startInteractiveSignIn(activity: Activity, provider: CloudProvider) {
        StructuredLogger.d("Starting unified interactive signIn", "provider" to provider.name)
        activeProvider = provider
        val plugin = plugins[provider]
        if (plugin == null) {
            handleFailedAuth(provider, "No auth plugin configured for ${provider.name}")
            return
        }
        
        try {
            plugin.startInteractiveSignIn(activity)
        } catch (e: Exception) {
            handleFailedAuth(provider, "Failed to start sign-in: ${e.message}")
        }
    }

    /**
     * Routes Intent result to the active plugin.
     * To be called from `AddResourceActivity` ActivityResultLauncher.
     */
    suspend fun processIntentResult(data: Intent?) {
        val provider = activeProvider ?: return
        StructuredLogger.d("Handling unified intent result", "provider" to provider.name)
        
        val plugin = plugins[provider] ?: return
        val result = plugin.processIntentResult(data)
        
        if (result != null) {
            processPluginResult(provider, result)
        }
    }

    /**
     * Routes `onResume` to the active plugin.
     * To be called from `AddResourceActivity.onResume`.
     */
    suspend fun handleResume() {
        val provider = activeProvider ?: return
        val plugin = plugins[provider] ?: return
        
        val result = plugin.handleResume()
        if (result != null) {
            StructuredLogger.d("Handling unified resume", "provider" to provider.name)
            processPluginResult(provider, result)
        }
    }

    private fun processPluginResult(provider: CloudProvider, result: AuthResult) {
        when (result) {
            is AuthResult.Success -> {
                // Update State Machine
                // The State Machine's authenticateOrRestore is for silent auth, 
                // but since we already got Success here, we conceptually bypass it.
                // However, StateMachine state must be updated. 
                // Since there is no public setter for Authenticated, we just let AddResourceActivity 
                // proceed. In a perfect world, StateMachine would have a method `onInteractiveAuthSuccess`.
                // For now, we emit the UI event.
                StructuredLogger.i("Unified interactive auth succeeded", "provider" to provider.name, "account" to result.accountName)
                _authEvents.tryEmit(AuthEvent.Success(provider, result.accountName))
                activeProvider = null
            }
            is AuthResult.Error -> {
                handleFailedAuth(provider, result.message)
            }
            is AuthResult.Cancelled -> {
                StructuredLogger.i("Unified interactive auth cancelled", "provider" to provider.name)
                // Do not show an error dialog for intentional cancellations, just clear state
                activeProvider = null
            }
        }
    }

    private fun handleFailedAuth(provider: CloudProvider, message: String) {
        StructuredLogger.e("Unified interactive auth failed", "provider" to provider.name, "reason" to message)
        authStateMachine.onAuthError(provider, message)
        _authEvents.tryEmit(AuthEvent.Error(provider, message))
        activeProvider = null
    }
}
