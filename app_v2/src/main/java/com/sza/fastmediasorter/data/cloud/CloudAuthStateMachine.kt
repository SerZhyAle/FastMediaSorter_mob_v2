package com.sza.fastmediasorter.data.cloud

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified auth state machine for cloud providers (P3 / B1-T1..T9).
 *
 * Provides a single, observable source of truth for each cloud provider's authentication state.
 * Wraps GoogleDriveRestClient, OneDriveRestClient and DropboxClient with uniform state lifecycle.
 *
 * States per provider: Idle → Authenticating → Authenticated | AuthFailed
 *
 * Usage (ViewModel):
 * ```
 * cloudAuthStateMachine.stateOf(CloudProvider.GOOGLE_DRIVE).collect { state -> ... }
 * ```
 */
@Singleton
class CloudAuthStateMachine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val googleDriveClient: GoogleDriveRestClient,
    private val oneDriveClient: OneDriveRestClient,
    private val dropboxClient: DropboxClient
) {

    // ── States ──────────────────────────────────────────────────────────────────

    sealed class AuthState {
        /** No auth attempt has been made, or the user signed out. */
        object Idle : AuthState()

        /** An auth attempt is in progress (silent restore or interactive flow). */
        object Authenticating : AuthState()

        /** Successfully authenticated.
         * @param accountEmail E-mail of the signed-in account. */
        data class Authenticated(val accountEmail: String) : AuthState()

        /** Auth attempt failed.
         * @param message Human-readable failure reason (never shown raw; log only). */
        data class AuthFailed(val message: String) : AuthState()
    }

    // ── Internal StateFlows ──────────────────────────────────────────────────────

    private val _googleDriveState = MutableStateFlow<AuthState>(AuthState.Idle)
    private val _oneDriveState    = MutableStateFlow<AuthState>(AuthState.Idle)
    private val _dropboxState     = MutableStateFlow<AuthState>(AuthState.Idle)

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Observe authentication state for [provider].
     * Emits on every state transition (Idle, Authenticating, Authenticated, AuthFailed).
     */
    fun stateOf(provider: CloudProvider): StateFlow<AuthState> = when (provider) {
        CloudProvider.GOOGLE_DRIVE -> _googleDriveState.asStateFlow()
        CloudProvider.ONEDRIVE    -> _oneDriveState.asStateFlow()
        CloudProvider.DROPBOX     -> _dropboxState.asStateFlow()
    }

    /**
     * Attempt to restore existing credentials or perform silent auth for [provider].
     * Transitions: Idle → Authenticating → Authenticated | AuthFailed.
     *
     * @param provider Cloud provider to authenticate.
     * @param accountEmail Optional account e-mail hint (for per-account credential restore).
     * @return [CloudResult.Success] with account e-mail, or [CloudResult.Error] on failure.
     */
    suspend fun authenticateOrRestore(
        provider: CloudProvider,
        accountEmail: String? = null
    ): CloudResult<String> {
        val stateFlow = mutableStateFor(provider)
        stateFlow.value = AuthState.Authenticating
        Timber.d("[AuthSM/$provider] → Authenticating (hint=$accountEmail)")

        return try {
            val result: CloudResult<String> = when (provider) {

                CloudProvider.GOOGLE_DRIVE -> {
                    val restored = if (!accountEmail.isNullOrEmpty()) {
                        googleDriveClient.tryRestoreForAccount(accountEmail)
                    } else {
                        googleDriveClient.tryRestoreFromStorage()
                    }
                    if (restored) {
                        val email = googleDriveClient.getAccountEmail() ?: accountEmail ?: ""
                        CloudResult.Success(email)
                    } else {
                        when (val ar = googleDriveClient.authenticate()) {
                            is AuthResult.Success  -> CloudResult.Success(ar.accountName)
                            is AuthResult.Error    -> CloudResult.Error(ar.message)
                            AuthResult.Cancelled   -> CloudResult.Error(authCancelledMessage(CloudProvider.GOOGLE_DRIVE))
                        }
                    }
                }

                CloudProvider.ONEDRIVE -> {
                    // MSAL handles silent token acquisition in initialize()
                    val initialized = oneDriveClient.initialize("{}")
                    if (initialized) {
                        CloudResult.Success(oneDriveClient.getAccountEmail() ?: "")
                    } else {
                        when (val ar = oneDriveClient.authenticate()) {
                            is AuthResult.Success  -> CloudResult.Success(ar.accountName)
                            is AuthResult.Error    -> CloudResult.Error(ar.message)
                            AuthResult.Cancelled   -> CloudResult.Error(authCancelledMessage(CloudProvider.ONEDRIVE))
                        }
                    }
                }

                CloudProvider.DROPBOX -> {
                    val restored = if (!accountEmail.isNullOrEmpty()) {
                        dropboxClient.tryRestoreForAccount(accountEmail)
                    } else {
                        dropboxClient.tryRestoreFromStorage()
                    }
                    if (restored) {
                        CloudResult.Success(dropboxClient.getAccountEmail() ?: accountEmail ?: "")
                    } else {
                        when (val ar = dropboxClient.authenticate()) {
                            is AuthResult.Success  -> CloudResult.Success(ar.accountName)
                            is AuthResult.Error    -> CloudResult.Error(ar.message)
                            AuthResult.Cancelled   -> CloudResult.Error(authCancelledMessage(CloudProvider.DROPBOX))
                        }
                    }
                }
            }

            when (result) {
                is CloudResult.Success -> {
                    stateFlow.value = AuthState.Authenticated(result.data)
                    Timber.i("[AuthSM/$provider] → Authenticated (${result.data})")
                }
                is CloudResult.Error -> {
                    stateFlow.value = AuthState.AuthFailed(result.message)
                    Timber.w("[AuthSM/$provider] → AuthFailed: ${result.message}")
                }
            }
            result
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val msg = e.message ?: "Unknown error"
            stateFlow.value = AuthState.AuthFailed(msg)
            Timber.e(e, "[AuthSM/$provider] → AuthFailed (exception)")
            CloudResult.Error(msg, e)
        }
    }

    /**
     * Mark the provider as signed out - transitions state to [AuthState.Idle].
     * Must be called after a successful sign-out from the corresponding client.
     */
    fun onSignedOut(provider: CloudProvider) {
        mutableStateFor(provider).value = AuthState.Idle
        Timber.i("[AuthSM/$provider] → Idle (signed out)")
    }

    /**
     * Mark the provider auth as failed (e.g. after detecting a 401 response in a data call).
     * The caller should then trigger re-authentication via [authenticateOrRestore].
     */
    fun onAuthError(provider: CloudProvider, message: String) {
        mutableStateFor(provider).value = AuthState.AuthFailed(message)
        Timber.w("[AuthSM/$provider] → AuthFailed (external signal): $message")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun authCancelledMessage(provider: CloudProvider): String = when (provider) {
        CloudProvider.GOOGLE_DRIVE -> context.getString(R.string.google_sign_in_cancelled)
        CloudProvider.ONEDRIVE -> context.getString(R.string.msg_onedrive_auth_cancelled)
        CloudProvider.DROPBOX -> context.getString(R.string.msg_dropbox_auth_cancelled)
    }

    private fun mutableStateFor(provider: CloudProvider): MutableStateFlow<AuthState> = when (provider) {
        CloudProvider.GOOGLE_DRIVE -> _googleDriveState
        CloudProvider.ONEDRIVE    -> _oneDriveState
        CloudProvider.DROPBOX     -> _dropboxState
    }
}
