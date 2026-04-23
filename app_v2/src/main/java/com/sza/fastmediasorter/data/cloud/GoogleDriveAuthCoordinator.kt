@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.data.cloud

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveCredentialsManager
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveHttpClient
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URL
import java.util.UUID

/**
 * Owns Google Sign-In state and Drive HTTP plumbing for GoogleDriveRestClient.
 *
 * State held here (was previously in GoogleDriveRestClient):
 *   - accessToken, accountEmail, tokenTimestamp
 *
 * Responsibilities:
 *   - silent + interactive Google Sign-In, OAuth token acquisition via GoogleAuthUtil
 *   - persistence of credentials via GoogleDriveCredentialsManager
 *   - registering the signed-in account in NetworkCredentialsEntity
 *   - proactive token refresh + 401-driven silent re-auth
 *   - authenticated Drive requests with bounded retry
 *
 * The client delegates every auth/HTTP concern here and stays focused on Drive endpoint
 * shaping, JSON parsing, and the CloudStorageClient surface.
 *
 * Extracted to keep GoogleDriveRestClient below the 1000-line cap.
 */
class GoogleDriveAuthCoordinator(
    private val context: Context,
    private val credentialsManager: GoogleDriveCredentialsManager,
    private val httpClient: GoogleDriveHttpClient,
    private val networkCredentialsRepository: NetworkCredentialsRepository
) {

    var accessToken: String? = null
        private set
    var accountEmail: String? = null
        private set
    private var tokenTimestamp: Long = 0L

    fun isAuthenticated(): Boolean = accessToken != null

    fun captureToken(): String? = accessToken

    fun clearAuth() {
        accessToken = null
        accountEmail = null
        tokenTimestamp = 0L
    }

    /** Build the GoogleSignInOptions used for both silent + interactive sign-in. */
    fun buildSignInOptions(webClientIdResId: Int): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(context.getString(webClientIdResId))
            .requestScopes(Scope(SCOPE_DRIVE))
            .requestScopes(Scope(SCOPE_DRIVE_READONLY))
            .build()

    /**
     * Silent-first authenticate. Falls back to GoogleSignIn.getLastSignedInAccount and a fresh
     * token request. Returns AuthResult.Error("Re-authentication required..") when neither path
     * yields a token — caller surfaces this so the user re-adds the resource interactively.
     */
    suspend fun authenticate(webClientIdResId: Int): AuthResult = withContext(Dispatchers.Main) {
        try {
            val silentResult = silentSignIn(webClientIdResId)
            if (silentResult is AuthResult.Success) return@withContext silentResult

            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                // GoogleAuthUtil.getToken automatically requests additional permissions if needed
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

            AuthResult.Error("Re-authentication required. Please re-add this Google Drive resource.")
        } catch (e: UserRecoverableAuthException) {
            // Re-throw so caller can launch the recovery intent
            Timber.e(e, "Google Drive authentication failed (recoverable)")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Google Drive authentication failed")
            AuthResult.Error("Authentication failed: ${e.message}")
        }
    }

    /** Silent sign-in: refresh credentials without UI. Returns Success only if a token was fetched. */
    suspend fun silentSignIn(webClientIdResId: Int): AuthResult = withContext(Dispatchers.IO) {
        try {
            val signInOptions = buildSignInOptions(webClientIdResId)
            val client = GoogleSignIn.getClient(context, signInOptions)

            val task = client.silentSignIn()
            val account = Tasks.await(task)

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

    /** Process an interactive sign-in result from GoogleSignIn.getSignInIntent(). */
    suspend fun handleSignInResult(account: GoogleSignInAccount?): AuthResult {
        if (account == null) return AuthResult.Error("Sign-in failed or cancelled")

        val token = getAccessToken(account) ?: return AuthResult.Error("Failed to get access token")
        accessToken = token
        tokenTimestamp = System.currentTimeMillis()
        accountEmail = account.email
        val credentials = credentialsManager.serializeAccount(account)
        // Save to encrypted storage for automatic restoration (legacy + per-account key)
        credentialsManager.saveCredentials(credentials, account.email)

        // Mirror sign-in into NetworkCredentialsEntity for the multi-account picker
        account.email?.let { email ->
            val existing = networkCredentialsRepository.getByTypeAndAccountId(CloudProvider.GOOGLE_DRIVE.name, email)
            if (existing == null) {
                val entity = NetworkCredentialsEntity.create(
                    credentialId = UUID.randomUUID().toString(),
                    type = CloudProvider.GOOGLE_DRIVE.name,
                    server = "",
                    port = 0,
                    username = email,
                    plaintextPassword = "", // token lives in credentialsManager
                    accountId = email
                )
                networkCredentialsRepository.insert(entity)
                Timber.d("Registered Google Drive account in database: $email")
            }
        }

        return AuthResult.Success(accountName = accountEmail ?: "Unknown", credentialsJson = credentials)
    }

    /**
     * Get OAuth access token via GoogleAuthUtil. The ID token from GoogleSignIn cannot be used
     * directly with the Drive REST API. [forceRefresh] clears the cached token before requesting.
     */
    suspend fun getAccessToken(account: GoogleSignInAccount, forceRefresh: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            try {
                val scope = "oauth2:$SCOPE_DRIVE $SCOPE_DRIVE_READONLY"
                Timber.d("Requesting access token with scope: $scope (forceRefresh=$forceRefresh)")

                // Clear cached token if forceRefresh or if scope changed (e.g., drive.file → drive)
                if (forceRefresh || accessToken != null) {
                    try {
                        accessToken?.let {
                            Timber.d("Clearing cached token")
                            GoogleAuthUtil.clearToken(context, it)
                        }
                    } catch (e: Exception) {
                        Timber.d("No cached token to clear or clearToken failed: ${e.message}")
                    }
                }

                val token = GoogleAuthUtil.getToken(context, account.account!!, scope)
                Timber.i("Successfully obtained access token")
                token
            } catch (e: UserRecoverableAuthException) {
                Timber.e(e, "Failed to get access token: ${e.javaClass.simpleName} - ${e.message}")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to get access token: ${e.javaClass.simpleName} - ${e.message}")
                null
            }
        }

    private fun shouldRefreshToken(): Boolean {
        if (tokenTimestamp == 0L) return false
        return System.currentTimeMillis() - tokenTimestamp > TOKEN_REFRESH_THRESHOLD_MS
    }

    /** Pre-emptive refresh past the 50-minute threshold to avoid 401 mid-operation. */
    suspend fun ensureTokenFresh(webClientIdResId: Int) {
        if (shouldRefreshToken()) {
            Timber.d("Token is old (>50 min), proactively refreshing..")
            silentSignIn(webClientIdResId)
        }
    }

    /**
     * Initialize from a stored credentials JSON. Returns true if a fresh token was acquired
     * (either via silent sign-in or via getAccessToken on the cached GoogleSignInAccount).
     */
    suspend fun initializeFromStored(credentialsJson: String, webClientIdResId: Int): Boolean {
        return try {
            val silentResult = silentSignIn(webClientIdResId)
            if (silentResult is AuthResult.Success) {
                Timber.d("Initialized Google Drive via silent sign-in")
                return true
            }

            val account = withContext(Dispatchers.Main) {
                GoogleSignIn.getLastSignedInAccount(context)
            } ?: run {
                Timber.w("No account signed in, cannot initialize")
                return false
            }

            val email = credentialsManager.deserializeAccount(credentialsJson)
            if (account.email != email) {
                Timber.w("Stored account ($email) doesn't match current account (${account.email})")
                return false
            }

            val token = getAccessToken(account)
            if (token == null) {
                Timber.w("Failed to get access token for account: $email")
                return false
            }
            accessToken = token
            tokenTimestamp = System.currentTimeMillis()
            accountEmail = account.email
            credentialsManager.saveCredentials(credentialsJson, account.email)
            Timber.d("Initialized Google Drive for account: $email")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Google Drive client")
            false
        }
    }

    /**
     * Authenticated Drive request with bounded retry on 401. Delegates the actual HTTP call to
     * [httpClient.makeAuthenticatedRequest]; this layer adds the silent-refresh recursion.
     */
    suspend fun makeAuthenticatedRequest(
        url: URL,
        method: String,
        token: String,
        webClientIdResId: Int,
        body: String? = null,
        retryCount: Int = 0
    ): GoogleDriveHttpClient.ApiResponse {
        val response = httpClient.makeAuthenticatedRequest(url, method, token, body)

        if (response.httpCode == 401 && retryCount < TOKEN_MAX_RETRY_ATTEMPTS) {
            Timber.w("Received 401 Unauthorized (attempt ${retryCount + 1}/$TOKEN_MAX_RETRY_ATTEMPTS). Attempting silent sign-in and retry..")

            if (retryCount > 0) delay(TOKEN_RETRY_DELAY_MS)

            val authResult = silentSignIn(webClientIdResId)
            if (authResult is AuthResult.Success) {
                val newToken = accessToken
                if (newToken != null) {
                    Timber.i("Silent sign-in successful. Retrying request (attempt ${retryCount + 2})..")
                    return makeAuthenticatedRequest(url, method, newToken, webClientIdResId, body, retryCount + 1)
                }
            }

            if (retryCount < TOKEN_MAX_RETRY_ATTEMPTS - 1) {
                Timber.w("Silent sign-in failed, but will retry again..")
                delay(TOKEN_RETRY_DELAY_MS)
                return makeAuthenticatedRequest(url, method, token, webClientIdResId, body, retryCount + 1)
            }

            Timber.e("All retry attempts exhausted ($TOKEN_MAX_RETRY_ATTEMPTS attempts). Returning 401 with detailed error.")
            return GoogleDriveHttpClient.ApiResponse(
                isSuccess = false,
                httpCode = 401,
                data = null,
                errorMessage = "Authentication expired after $TOKEN_MAX_RETRY_ATTEMPTS retry attempts. Token invalid or revoked. Please re-authenticate in Settings → Edit Resource."
            )
        }

        return response
    }

    companion object {
        const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive"
        const val SCOPE_DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"
        private const val TOKEN_REFRESH_THRESHOLD_MS = 50 * 60 * 1000L  // 50 minutes
        private const val TOKEN_MAX_RETRY_ATTEMPTS = 3
        private const val TOKEN_RETRY_DELAY_MS = 2000L
    }
}
