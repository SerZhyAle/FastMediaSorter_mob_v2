package com.sza.fastmediasorter.data.cloud

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveHttpClient
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.net.URL

/**
 * Owns Drive HTTP token plumbing for GoogleDriveRestClient.
 *
 * S0200 Phase 04b - token source switched to [GoogleIdentityRepository] (Credential Manager).
 * Old `GoogleSignIn` / `GoogleAuthUtil` calls are replaced by `identityRepository.getAccessToken`
 * and `invalidateToken`. Cached `accessToken` is kept as a synchronous read accessor for the
 * RestClient hot path; it is refreshed via [fetchTokenFromIdentity] on every suspend entry point.
 *
 * S0200 Phase 04c: legacy `GoogleSignIn` / `GoogleAuthUtil` stubs deleted. The only remaining
 * `GoogleSignIn*` references in the Drive cloud surface are doc-comments referring to the
 * historical context.
 */
class GoogleDriveAuthCoordinator(
    private val context: Context,
    private val httpClient: GoogleDriveHttpClient,
    private val identityRepository: GoogleIdentityRepository,
    private val browserAuthManager: GoogleDriveBrowserAuthManager
) {

    private val driveScopes: Set<GoogleScope> = setOf(GoogleScope.DRIVE, GoogleScope.DRIVE_READONLY)
    private val tokenMutex = Mutex()

    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var tokenTimestamp: Long = 0L
    @Volatile private var gmsOnlyMode: Boolean = false

    /** Synchronous read of the cached token. Always refresh via [fetchTokenFromIdentity] first. */
    val accessToken: String? get() = cachedAccessToken

    fun setGmsOnlyMode(enabled: Boolean): Boolean {
        val previous = gmsOnlyMode
        gmsOnlyMode = enabled
        Timber.d("Google Drive auth coordinator gmsOnlyMode set to $enabled")
        return previous
    }

    /** Email of the currently bound primary Google account, or null when unbound. */
    val accountEmail: String?
        get() = when {
            !gmsOnlyMode && browserAuthManager.hasActiveSession() -> browserAuthManager.peekStoredAccountEmail()
            identityRepository.state.value is PrimaryGoogleAccountState.Bound -> {
                (identityRepository.state.value as PrimaryGoogleAccountState.Bound).account.email
            }

            !gmsOnlyMode -> browserAuthManager.peekStoredAccountEmail()
            else -> null
        }

    fun isAuthenticated(): Boolean =
        if (gmsOnlyMode) {
            identityRepository.state.value is PrimaryGoogleAccountState.Bound
        } else {
            browserAuthManager.hasActiveSession() ||
                identityRepository.state.value is PrimaryGoogleAccountState.Bound ||
                browserAuthManager.hasStoredCredentials()
        }

    fun captureToken(): String? = cachedAccessToken

    fun clearAuth() {
        cachedAccessToken = null
        tokenTimestamp = 0L
    }

    /**
     * Fetch a fresh Drive access token from the identity domain.
     * Caches the value for synchronous reads in [accessToken]. Returns null when the primary
     * account is unbound or the identity-domain silent refresh failed.
     */
    suspend fun fetchAccessToken(): String? = tokenMutex.withLock {
        val token = resolveAccessToken()
        if (token != null) {
            cachedAccessToken = token
            tokenTimestamp = System.currentTimeMillis()
        } else {
            cachedAccessToken = null
        }
        token
    }

    /**
     * Silent sign-in via identity domain - invalidate cached token, request a fresh one.
     * [webClientIdResId] is accepted for legacy API compatibility; Credential Manager owns the
     * client-id configuration so the parameter is ignored.
     */
    suspend fun silentSignIn(@Suppress("UNUSED_PARAMETER") webClientIdResId: Int): AuthResult {
        val token = fetchAccessToken()
        return if (token != null) {
            Timber.i("Silent sign-in successful for Google Drive")
            AuthResult.Success(
                accountName = accountEmail ?: "Unknown",
                credentialsJson = accountEmail.orEmpty()
            )
        } else {
            Timber.w("Silent sign-in failed: no Google Drive token source returned a token")
            AuthResult.Error("Silent sign-in failed: no Google Drive token source returned a token")
        }
    }

    /**
     * Issue a Drive token from the currently bound primary account. Falls back to silent refresh
     * via [identityRepository.getAccessToken]. Returns AuthResult.Error when the primary account
     * is unbound - caller surfaces this so the user signs in through the Settings card.
     */
    suspend fun authenticate(@Suppress("UNUSED_PARAMETER") webClientIdResId: Int): AuthResult {
        val token = fetchAccessToken()
        return if (token != null) {
            AuthResult.Success(
                accountName = accountEmail ?: "Unknown",
                credentialsJson = accountEmail.orEmpty()
            )
        } else {
            AuthResult.Error("Re-authentication required. Please re-add this Google Drive resource.")
        }
    }

    private fun shouldRefreshToken(): Boolean {
        if (tokenTimestamp == 0L) return false
        return System.currentTimeMillis() - tokenTimestamp > TOKEN_REFRESH_THRESHOLD_MS
    }

    /** Pre-emptive refresh past the 50-minute threshold to avoid 401 mid-operation. */
    suspend fun ensureTokenFresh(@Suppress("UNUSED_PARAMETER") webClientIdResId: Int) {
        if (shouldRefreshToken()) {
            Timber.d("Token is old (>50 min), proactively refreshing Google Drive auth state..")
            fetchAccessToken()
        }
    }

    /**
     * S0200 Phase 04b: existing call from RestClient.initialize() - identity-domain owns the
     * persistence of the primary account; this just verifies that a Drive token is reachable now.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun initializeFromStored(credentialsJson: String, webClientIdResId: Int): Boolean {
        if (gmsOnlyMode) {
            return fetchAccessToken() != null
        }
        val restoredBrowser = browserAuthManager.restoreCredentialBlob(credentialsJson)
        val ok = if (restoredBrowser) {
            browserAuthManager.getFreshAccessToken() != null
        } else {
            fetchAccessToken() != null
        }
        if (ok) {
            Timber.d("Initialized Google Drive authentication state")
        } else {
            Timber.w("No Google Drive token source was available during initializeFromStored")
        }
        return ok
    }

    /**
     * Authenticated Drive request with bounded retry on 401. Delegates the HTTP call to
     * [httpClient.makeAuthenticatedRequest]; this layer adds the silent-refresh recursion via
     * the identity domain.
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
            Timber.w("Received 401 Unauthorized (attempt ${retryCount + 1}/$TOKEN_MAX_RETRY_ATTEMPTS). Attempting Google Drive token refresh..")

            if (retryCount > 0) delay(TOKEN_RETRY_DELAY_MS)

            val newToken = refreshForRetry()
            if (newToken != null) {
                Timber.i("Silent refresh successful. Retrying request (attempt ${retryCount + 2})..")
                return makeAuthenticatedRequest(url, method, newToken, webClientIdResId, body, retryCount + 1)
            }

            if (retryCount < TOKEN_MAX_RETRY_ATTEMPTS - 1) {
                Timber.w("Silent refresh failed, but will retry again..")
                delay(TOKEN_RETRY_DELAY_MS)
                return makeAuthenticatedRequest(url, method, token, webClientIdResId, body, retryCount + 1)
            }

            Timber.e("All retry attempts exhausted ($TOKEN_MAX_RETRY_ATTEMPTS attempts). Returning 401 with detailed error.")
            return GoogleDriveHttpClient.ApiResponse(
                isSuccess = false,
                httpCode = 401,
                data = null,
                errorMessage = context.getString(
                    R.string.cloud_auth_required,
                    context.getString(R.string.google_drive)
                )
            )
        }

        return response
    }

    private suspend fun resolveAccessToken(): String? {
        if (!gmsOnlyMode && browserAuthManager.hasActiveSession()) {
            return browserAuthManager.getFreshAccessToken()
        }

        val bound = identityRepository.state.value as? PrimaryGoogleAccountState.Bound
        if (bound != null) {
            return identityRepository.getAccessToken(driveScopes)?.token
        }

        if (!gmsOnlyMode && browserAuthManager.ensureActiveFromStored()) {
            return browserAuthManager.getFreshAccessToken()
        }

        return null
    }

    private suspend fun refreshForRetry(): String? {
        if (!gmsOnlyMode && browserAuthManager.hasActiveSession()) {
            return browserAuthManager.getFreshAccessToken()
        }

        identityRepository.invalidateToken()
        return resolveAccessToken()
    }

    companion object {
        const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive"
        const val SCOPE_DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"
        private const val TOKEN_REFRESH_THRESHOLD_MS = 50 * 60 * 1000L  // 50 minutes
        private const val TOKEN_MAX_RETRY_ATTEMPTS = 3
        private const val TOKEN_RETRY_DELAY_MS = 2000L
    }
}
