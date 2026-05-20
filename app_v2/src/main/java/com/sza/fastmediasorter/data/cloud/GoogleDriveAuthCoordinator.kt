package com.sza.fastmediasorter.data.cloud

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveCredentialsManager
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveHttpClient
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
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
    private val credentialsManager: GoogleDriveCredentialsManager,
    private val httpClient: GoogleDriveHttpClient,
    private val networkCredentialsRepository: NetworkCredentialsRepository,
    private val identityRepository: GoogleIdentityRepository
) {

    private val driveScopes: Set<GoogleScope> = setOf(GoogleScope.DRIVE, GoogleScope.DRIVE_READONLY)
    private val tokenMutex = Mutex()

    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var tokenTimestamp: Long = 0L

    /** Synchronous read of the cached token. Always refresh via [fetchTokenFromIdentity] first. */
    val accessToken: String? get() = cachedAccessToken

    /** Email of the currently bound primary Google account, or null when unbound. */
    val accountEmail: String?
        get() = (identityRepository.state.value as? PrimaryGoogleAccountState.Bound)?.account?.email

    fun isAuthenticated(): Boolean =
        identityRepository.state.value is PrimaryGoogleAccountState.Bound

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
    suspend fun fetchTokenFromIdentity(): String? = tokenMutex.withLock {
        val token = identityRepository.getAccessToken(driveScopes)?.token
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
        identityRepository.invalidateToken()
        val token = fetchTokenFromIdentity()
        return if (token != null) {
            Timber.i("Silent sign-in successful via identity domain")
            AuthResult.Success(
                accountName = accountEmail ?: "Unknown",
                credentialsJson = accountEmail.orEmpty()
            )
        } else {
            Timber.w("Silent sign-in failed: identity-domain returned no token")
            AuthResult.Error("Silent sign-in failed: identity-domain returned no token")
        }
    }

    /**
     * Issue a Drive token from the currently bound primary account. Falls back to silent refresh
     * via [identityRepository.getAccessToken]. Returns AuthResult.Error when the primary account
     * is unbound - caller surfaces this so the user signs in through the Settings card.
     */
    suspend fun authenticate(@Suppress("UNUSED_PARAMETER") webClientIdResId: Int): AuthResult {
        val token = fetchTokenFromIdentity()
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
            Timber.d("Token is old (>50 min), proactively refreshing via identity-domain..")
            fetchTokenFromIdentity()
        }
    }

    /**
     * S0200 Phase 04b: existing call from RestClient.initialize() - identity-domain owns the
     * persistence of the primary account; this just verifies that a Drive token is reachable now.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun initializeFromStored(credentialsJson: String, webClientIdResId: Int): Boolean {
        val ok = fetchTokenFromIdentity() != null
        if (ok) Timber.d("Initialized Google Drive via identity-domain") else Timber.w("No token from identity-domain")
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
            Timber.w("Received 401 Unauthorized (attempt ${retryCount + 1}/$TOKEN_MAX_RETRY_ATTEMPTS). Attempting silent refresh via identity-domain..")

            if (retryCount > 0) delay(TOKEN_RETRY_DELAY_MS)

            identityRepository.invalidateToken()
            val newToken = fetchTokenFromIdentity()
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

    companion object {
        const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive"
        const val SCOPE_DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"
        private const val TOKEN_REFRESH_THRESHOLD_MS = 50 * 60 * 1000L  // 50 minutes
        private const val TOKEN_MAX_RETRY_ATTEMPTS = 3
        private const val TOKEN_RETRY_DELAY_MS = 2000L
    }
}
