package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalDeclinedScopeException
import com.microsoft.identity.client.exception.MsalException
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Owns the MSAL authentication state and Graph HTTP plumbing for OneDriveRestClient.
 *
 * State held here (was previously in OneDriveRestClient):
 *   - msalApp (ISingleAccountPublicClientApplication)
 *   - accessToken, accountEmail, tokenTimestamp
 *
 * Responsibilities:
 *   - MSAL application initialization
 *   - silent + interactive sign-in with MsalDeclinedScopeException recovery
 *   - silent token refresh (proactive + 401-driven)
 *   - authenticated Graph requests with bounded retry on 401
 *   - registering the signed-in account in NetworkCredentialsEntity
 *
 * The client delegates every auth/HTTP concern here and stays focused on Graph endpoint
 * shaping, JSON parsing, and the CloudStorageClient surface.
 *
 * Extracted to keep OneDriveRestClient below the 1000-line cap.
 */
class OneDriveAuthCoordinator(
    private val context: Context,
    private val networkCredentialsRepository: NetworkCredentialsRepository,
    private val applicationScope: CoroutineScope
) {

    private var msalApp: ISingleAccountPublicClientApplication? = null
    var accessToken: String? = null
        private set
    var accountEmail: String? = null
        internal set
    private var tokenTimestamp: Long = 0L

    fun isAuthenticated(): Boolean = accessToken != null

    /** Drop in-memory token + email. Used by signOut after server revocation is queued. */
    fun clearAuth() {
        accessToken = null
        accountEmail = null
    }

    /** Returns the captured access token (for revocation queueing). */
    fun captureToken(): String? = accessToken

    /** Reset the local MSAL session and clear in-memory state on Main thread. */
    suspend fun signOutLocal(onError: (Exception) -> Unit) {
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
                onError(e)
            }
        }
    }

    suspend fun initializeMsal(): Boolean = suspendCancellableCoroutine { continuation ->
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

    suspend fun authenticate(): AuthResult = withContext(Dispatchers.IO) {
        try {
            if (msalApp == null) {
                if (!initializeMsal()) return@withContext AuthResult.Error("Failed to initialize MSAL")
            }

            val app = msalApp ?: return@withContext AuthResult.Error("MSAL not initialized")

            val account = app.currentAccount.currentAccount
            Timber.d("OneDrive authenticate: cachedAccount=${account?.username ?: "none"}")
            if (account != null) {
                val result = acquireTokenSilently(account)
                if (result != null) {
                    accessToken = result.accessToken
                    tokenTimestamp = System.currentTimeMillis()
                    accountEmail = result.account.username
                    Timber.i("OneDrive silent auth success: $accountEmail")
                    return@withContext AuthResult.Success(
                        accountName = accountEmail ?: "Unknown",
                        credentialsJson = OneDriveRestClientUtils.serializeAccount(result.account)
                    )
                }
                Timber.w("OneDrive silent auth failed, interactive sign-in required")
            } else {
                Timber.d("OneDrive: no cached account, interactive sign-in required")
            }

            // Interactive flow must be initiated from an Activity via signIn(..)
            AuthResult.Error("Interactive sign-in required")
        } catch (e: Exception) {
            Timber.e(e, "OneDrive authentication failed")
            AuthResult.Error("Authentication failed: ${e.message}")
        }
    }

    /** Initialize MSAL on demand (sync), then dispatch to interactive sign-in. */
    fun signIn(activity: Activity, callback: (AuthResult) -> Unit) {
        if (msalApp == null) {
            Timber.d("MSAL not yet initialized, initializing before signIn..")
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
        signInWithApp(activity, msalApp!!, callback)
    }

    /** If an account is cached, sign it out first (so MSAL prompts fresh), then sign in interactively. */
    private fun signInWithApp(
        activity: Activity,
        app: ISingleAccountPublicClientApplication,
        callback: (AuthResult) -> Unit
    ) {
        val currentAccount = try { app.currentAccount.currentAccount } catch (_: Exception) { null }

        if (currentAccount != null) {
            Timber.d("Account already exists, signing out before interactive sign-in")
            app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() = signInInternal(activity, app, callback)
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
                applicationScope.launch(Dispatchers.Main) {
                    callback(handleAuthenticationResult(authenticationResult))
                }
            }

            override fun onError(exception: MsalException) {
                if (exception is MsalDeclinedScopeException) {
                    val grantedScopes = exception.grantedScopes
                    if (grantedScopes.contains("Files.ReadWrite.All")) {
                        // Interactive login partially succeeded — try silent auth with whatever was granted
                        Timber.w("Interactive declined scopes, proceeding with granted: $grantedScopes")
                        applicationScope.launch(Dispatchers.IO) {
                            val currentAccount = app.currentAccount.currentAccount
                            if (currentAccount != null) {
                                val result = acquireTokenSilently(currentAccount, grantedScopes.toTypedArray())
                                withContext(Dispatchers.Main) {
                                    if (result != null) {
                                        accessToken = result.accessToken
                                        tokenTimestamp = System.currentTimeMillis()
                                        accountEmail = result.account.username
                                        callback(AuthResult.Success(
                                            accountName = accountEmail ?: "Unknown",
                                            credentialsJson = OneDriveRestClientUtils.serializeAccount(result.account)
                                        ))
                                    } else {
                                        callback(AuthResult.Error("Failed to acquire token with granted scopes after interactive login"))
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
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

    /** Silent token acquisition with MsalDeclinedScopeException retry on a reduced scope set. */
    private suspend fun acquireTokenSilently(
        account: IAccount,
        scopes: Array<String> = SCOPES
    ): IAuthenticationResult? = suspendCancellableCoroutine { continuation ->
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
                        if (grantedScopes.contains("Files.ReadWrite.All")) {
                            Timber.w("MsalDeclinedScopeException caught. Retrying with granted scopes only: $grantedScopes")
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

    /** Call from Activity after the user completes the OAuth flow. Persists account row in DB. */
    suspend fun handleAuthenticationResult(result: IAuthenticationResult?): AuthResult {
        return if (result != null) {
            accessToken = result.accessToken
            tokenTimestamp = System.currentTimeMillis()
            accountEmail = result.account.username

            // Mirror sign-in into NetworkCredentialsEntity for the multi-account picker.
            accountEmail?.let { email ->
                val existing = networkCredentialsRepository.getByTypeAndAccountId(CloudProvider.ONEDRIVE.name, email)
                if (existing == null) {
                    val entity = NetworkCredentialsEntity.create(
                        credentialId = java.util.UUID.randomUUID().toString(),
                        type = CloudProvider.ONEDRIVE.name,
                        server = "",
                        port = 0,
                        username = email,
                        plaintextPassword = "", // MSAL owns its own token cache
                        accountId = email
                    )
                    networkCredentialsRepository.insert(entity)
                    Timber.d("Registered OneDrive account in database: $email")
                }
            }

            AuthResult.Success(
                accountName = accountEmail ?: "Unknown",
                credentialsJson = OneDriveRestClientUtils.serializeAccount(result.account)
            )
        } else {
            AuthResult.Error("Authentication failed or cancelled")
        }
    }

    /** Initialize from a stored credentials JSON. Returns true if a fresh token was acquired. */
    suspend fun initializeFromStored(credentialsJson: String): Boolean {
        return try {
            if (msalApp == null) {
                if (!initializeMsal()) return false
            }
            val app = msalApp ?: return false
            val account = app.currentAccount.currentAccount

            if (account == null) {
                Timber.w("No account signed in")
                return false
            }

            val username = OneDriveRestClientUtils.deserializeAccount(credentialsJson)
            if (username.isNotEmpty() && account.username != username) {
                Timber.w("Stored account ($username) doesn't match current account (${account.username})")
                return false
            }

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
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize OneDrive client")
            false
        }
    }

    private fun shouldRefreshToken(): Boolean {
        if (tokenTimestamp == 0L) return false
        return System.currentTimeMillis() - tokenTimestamp > TOKEN_REFRESH_THRESHOLD_MS
    }

    /** Pre-emptively refresh the token if it is near expiry, to avoid 401 mid-operation. */
    suspend fun ensureTokenFresh() {
        if (!shouldRefreshToken()) return
        Timber.d("Token is old (>50 min), proactively refreshing..")
        val app = msalApp ?: return
        val account = try { app.currentAccount.currentAccount } catch (e: Exception) {
            Timber.w(e, "Failed to get current account for token refresh")
            return
        } ?: return

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

    /** Authenticated Graph request with bounded retry on 401 (silent refresh between attempts). */
    suspend fun makeAuthenticatedRequest(
        url: URL,
        method: String,
        token: String = accessToken ?: "",
        body: String? = null,
        retryCount: Int = 0
    ): OneDriveRestClientUtils.ApiResponse {
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

            if (responseCode == 401 && retryCount < TOKEN_MAX_RETRY_ATTEMPTS) {
                connection.disconnect()
                Timber.w("Received 401 Unauthorized (attempt ${retryCount + 1}/$TOKEN_MAX_RETRY_ATTEMPTS). Attempting silent token refresh..")

                if (retryCount > 0) delay(TOKEN_RETRY_DELAY_MS)

                val app = msalApp
                val account = app?.currentAccount?.currentAccount
                if (app != null && account != null) {
                    val result = acquireTokenSilently(account)
                    if (result != null) {
                        accessToken = result.accessToken
                        tokenTimestamp = System.currentTimeMillis()
                        Timber.i("Silent token refresh successful. Retrying request (attempt ${retryCount + 2})..")
                        return makeAuthenticatedRequest(url, method, result.accessToken, body, retryCount + 1)
                    }
                }

                if (retryCount < TOKEN_MAX_RETRY_ATTEMPTS - 1) {
                    Timber.w("Silent token refresh failed, but will retry again..")
                    delay(TOKEN_RETRY_DELAY_MS)
                    return makeAuthenticatedRequest(url, method, token, body, retryCount + 1)
                }

                Timber.e("All retry attempts exhausted ($TOKEN_MAX_RETRY_ATTEMPTS attempts). Returning 401 error.")
                return OneDriveRestClientUtils.ApiResponse(
                    isSuccess = false,
                    data = null,
                    errorMessage = "Authentication expired after $TOKEN_MAX_RETRY_ATTEMPTS retry attempts. Token invalid or revoked. Please re-authenticate in Settings → Edit Resource."
                )
            }

            return if (responseCode in 200..299) {
                val data = connection.inputStream.bufferedReader().use { it.readText() }
                OneDriveRestClientUtils.ApiResponse(isSuccess = true, data = data, errorMessage = null)
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                OneDriveRestClientUtils.ApiResponse(isSuccess = false, data = null, errorMessage = error)
            }
        } catch (e: Exception) {
            Timber.e(e, "Request failed: $method $url")
            return OneDriveRestClientUtils.ApiResponse(isSuccess = false, data = null, errorMessage = e.message)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        val SCOPES = arrayOf("Files.ReadWrite.All", "offline_access")
        private const val TOKEN_REFRESH_THRESHOLD_MS = 50 * 60 * 1000L  // 50 minutes
        private const val TOKEN_MAX_RETRY_ATTEMPTS = 3
        private const val TOKEN_RETRY_DELAY_MS = 2000L
    }
}
