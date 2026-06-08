package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.logging.StructuredLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
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
    oneDriveAuthPlugin: OneDriveAuthPlugin,
    @param:ApplicationScope private val appScope: CoroutineScope
) {
    // Map of provider to its specific interactive authenticator plugin
    private val plugins = mapOf(
        CloudProvider.GOOGLE_DRIVE to googleDriveAuthPlugin,
        CloudProvider.DROPBOX to dropboxAuthPlugin,
        CloudProvider.ONEDRIVE to oneDriveAuthPlugin
    )

    // Tracks which provider is currently undergoing an interactive authentication flow
    private var activeProvider: CloudProvider? = null

    // Per-attempt subscription job - cancelled by a new startInteractiveSignIn or on
    // result/timeout to guarantee a single observer per attempt across rapid re-clicks.
    private var subscriptionJob: Job? = null

    // Event stream for the UI (Activity) to observe success or failures
    private val _authEvents = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val authEvents = _authEvents.asSharedFlow()

    sealed class AuthEvent {
        data class Success(val provider: CloudProvider, val accountEmail: String) : AuthEvent()
        data class Error(val provider: CloudProvider, val message: String) : AuthEvent()
    }

    /**
     * Starts the interactive sign-in flow for the given provider.
     *
     * Cancels any prior subscription, subscribes to the active plugin's [InteractiveCloudAuthenticator.results]
     * channel under a fresh [Job], and bounds the wait by [INTERACTIVE_AUTH_TIMEOUT_MS]. The plugin produces
     * exactly one [AuthResult] per attempt - regardless of whether its SDK signals through a coroutine
     * (Credential Manager), an MSAL callback, or `Activity.onResume` (Dropbox).
     */
    fun startInteractiveSignIn(activity: Activity, provider: CloudProvider) {
        Timber.d("S0243: UnifiedCloudAuthManager.startInteractiveSignIn provider=${provider.name}")
        subscriptionJob?.cancel()
        subscriptionJob = null
        StructuredLogger.d("Interactive cloud auth begin", "provider" to provider.name)
        activeProvider = provider
        val plugin = plugins[provider]
        if (plugin == null) {
            handleFailedAuth(provider, "No auth plugin configured for ${provider.name}")
            return
        }

        subscriptionJob = appScope.launch(Dispatchers.Main) {
            val result = withTimeoutOrNull(INTERACTIVE_AUTH_TIMEOUT_MS) {
                plugin.results.first()
            }
            if (activeProvider != provider) {
                StructuredLogger.i("Interactive cloud auth result dropped - provider changed", "provider" to provider.name)
                return@launch
            }
            if (result == null) {
                StructuredLogger.w("Interactive cloud auth timeout", "provider" to provider.name)
                handleFailedAuth(
                    provider,
                    "Interactive sign-in timed out after ${INTERACTIVE_AUTH_TIMEOUT_MS / 1000} seconds"
                )
                return@launch
            }
            StructuredLogger.d(
                "Interactive cloud auth end",
                "provider" to provider.name,
                "outcome" to result::class.java.simpleName
            )
            processPluginResult(provider, result)
        }

        try {
            plugin.startInteractiveSignIn(activity)
        } catch (e: Exception) {
            subscriptionJob?.cancel()
            subscriptionJob = null
            StructuredLogger.e(e, "Failed to start sign-in", "provider" to provider.name)
            handleFailedAuth(provider, "Failed to start sign-in: ${e.message}")
        }
    }

    /**
     * Routes `Activity.onResume` to the active plugin. Plugins that finalize on next foreground
     * (Dropbox) emit their result to [InteractiveCloudAuthenticator.results] from here; the
     * orchestrator's subscription job picks it up via the [results.first()] call in
     * [startInteractiveSignIn]. Other plugins implement this as a no-op.
     */
    suspend fun handleResume() {
        val provider = activeProvider ?: return
        val plugin = plugins[provider] ?: return
        StructuredLogger.d("Interactive cloud auth onResume", "provider" to provider.name)
        plugin.onResume()
    }

    private fun processPluginResult(provider: CloudProvider, result: AuthResult) {
        when (result) {
            is AuthResult.Success -> {
                // The State Machine's authenticateOrRestore is for silent auth,
                // but since we already got Success here, we conceptually bypass it.
                // However, StateMachine state must be updated.
                // Since there is no public setter for Authenticated, we just let AddResourceActivity
                // proceed. In a perfect world, StateMachine would have a method `onInteractiveAuthSuccess`.
                // For now, we emit the UI event.
                StructuredLogger.i("Unified interactive auth succeeded", "provider" to provider.name, "account" to result.accountName)
                _authEvents.tryEmit(AuthEvent.Success(provider, result.accountName))
                activeProvider = null
                subscriptionJob = null
            }
            is AuthResult.Error -> {
                handleFailedAuth(provider, result.message)
            }
            is AuthResult.Cancelled -> {
                StructuredLogger.i("Unified interactive auth cancelled", "provider" to provider.name)
                // Do not show an error dialog for intentional cancellations, just clear state
                activeProvider = null
                subscriptionJob = null
            }
        }
    }

    private fun handleFailedAuth(provider: CloudProvider, message: String) {
        StructuredLogger.e("Unified interactive auth failed", "provider" to provider.name, "reason" to message)
        authStateMachine.onAuthError(provider, message)
        _authEvents.tryEmit(AuthEvent.Error(provider, message))
        activeProvider = null
        subscriptionJob = null
    }

    companion object {
        // Single point of "stuck" interactive flow detection - covers Credential Manager,
        // MSAL broker, and Dropbox browser round-trip with margin.
        private const val INTERACTIVE_AUTH_TIMEOUT_MS = 300_000L
    }
}
