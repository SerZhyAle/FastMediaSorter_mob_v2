package com.sza.fastmediasorter.identity

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.sza.fastmediasorter.core.di.CloudTokenDispatcher
import com.sza.fastmediasorter.domain.identity.GoogleAccessToken
import com.sza.fastmediasorter.domain.identity.GoogleScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Outcome of a token issuance attempt. Distinguishes a removed device account (a self-heal signal
 * for the identity repository) from a transient / refreshable failure, so a stale binding is cleared
 * only when the account is provably gone from the device. S0743.
 */
sealed interface TokenIssueResult {
    data class Success(val token: GoogleAccessToken) : TokenIssueResult

    /** GMS `ACCOUNT_NOT_PRESENT` - the bound account is no longer on the device. */
    data object AccountAbsent : TokenIssueResult

    /** Refreshable / transient failure (network, revoked grant, etc.) - keep the binding. */
    data object Failed : TokenIssueResult
}

/**
 * Issues OAuth 2.0 access tokens for the currently bound primary Google account (strategic S0200).
 *
 * Implementation note: Credential Manager (`androidx.credentials`) handles the consent / sign-in
 * dialog, but token issuance for OAuth scopes still goes through `GoogleAuthUtil.getToken` - Google
 * has not yet shipped a Credential Manager equivalent for arbitrary-scope access tokens. This is
 * the documented migration path (see the Credential Manager developer guide section "Authorize
 * access for additional scopes"). The file-level deprecation suppress at the top is intentionally
 * scoped to THIS file only and MUST NOT spread elsewhere.
 *
 * Concurrency: token refresh is serialised by [mutex]. Concurrent callers requesting the same
 * scope set get the same cached token. Cache hits skip the mutex via the early-return.
 *
 * Token rotation: `GoogleAuthUtil` tokens expire at the GMS-default ~60 minutes; the cache
 * preemptively re-issues a token when the cached one is within 60 s of expiry.
 */
@Singleton
class GoogleTokenIssuer @Inject constructor(
    @ApplicationContext private val context: Context,
    @CloudTokenDispatcher private val tokenDispatcher: CoroutineDispatcher
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<Set<GoogleScope>, GoogleAccessToken>()

    /**
     * Returns a fresh-enough access token for the [email] account covering [scopes].
     * On failure returns [TokenIssueResult.AccountAbsent] when GMS reports the bound account is no
     * longer on the device, or [TokenIssueResult.Failed] for any transient / refreshable error.
     */
    suspend fun issue(email: String, scopes: Set<GoogleScope>): TokenIssueResult = mutex.withLock {
        val cached = cache[scopes]
        if (cached != null && cached.expiresAt.isAfter(Instant.now().plusSeconds(REFRESH_THRESHOLD_SECONDS))) {
            return@withLock TokenIssueResult.Success(cached)
        }
        val scopeObjects = scopes.map { Scope(it.value) }
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(scopeObjects)
            .build()
        withContext(tokenDispatcher) {
            runCatching {
                val result = Identity.getAuthorizationClient(context)
                    .authorize(authorizationRequest)
                    .await()
                if (result.hasResolution()) {
                    throw Exception("Authorization requires resolution")
                }
                val raw = result.accessToken ?: throw Exception("Authorization returned null access token")
                GoogleAccessToken(
                    token = raw,
                    scopes = scopes,
                    expiresAt = Instant.now().plus(TOKEN_LIFETIME)
                )
            }.fold(
                onSuccess = { token ->
                    cache[scopes] = token
                    TokenIssueResult.Success(token)
                },
                onFailure = { error ->
                    Timber.w(error, "Token issuance failed for scopes=$scopes")
                    if (isAccountNotPresent(error)) TokenIssueResult.AccountAbsent else TokenIssueResult.Failed
                }
            )
        }
    }

    // GMS surfaces a removed/absent device account as an IOException whose message is the status
    // name "AccountNotPresent" (logcat: "[GoogleAuthUtil] error status:ACCOUNT_NOT_PRESENT"). Walk a
    // bounded cause chain; anything not positively matched is treated as a refreshable failure so a
    // misclassification never clears a valid binding. S0743.
    private fun isAccountNotPresent(error: Throwable?): Boolean {
        var cause = error
        var depth = 0
        while (cause != null && depth < CAUSE_WALK_LIMIT) {
            if (cause.message?.contains(ACCOUNT_NOT_PRESENT_MARKER) == true) return true
            cause = cause.cause
            depth++
        }
        return false
    }

    /** Invalidates every cached token and revokes them locally via `GoogleAuthUtil.clearToken`. */
    @Suppress("DEPRECATION") // GoogleAuthUtil.clearToken - same Credential Manager gap as [issue]; see KDoc.
    suspend fun invalidate(): Unit = mutex.withLock {
        withContext(tokenDispatcher) {
            cache.values.forEach { runCatching { GoogleAuthUtil.clearToken(context, it.token) } }
        }
        cache.clear()
    }

    /** Removes only the cached tokens whose [GoogleAccessToken.expiresAt] is already past. */
    suspend fun invalidateExpired(): Unit = mutex.withLock {
        val now = Instant.now()
        cache.entries.removeAll { it.value.expiresAt.isBefore(now) }
    }

    private companion object {
        const val REFRESH_THRESHOLD_SECONDS = 60L

        /** GMS default access tokens expire at ~60 min; we preemptively re-issue at the 55-min mark. */
        val TOKEN_LIFETIME: Duration = Duration.ofMinutes(55)

        /** GMS status name carried in the `GoogleAuthUtil.getToken` failure when the account is gone. */
        const val ACCOUNT_NOT_PRESENT_MARKER = "AccountNotPresent"
        const val CAUSE_WALK_LIMIT = 4
    }
}
