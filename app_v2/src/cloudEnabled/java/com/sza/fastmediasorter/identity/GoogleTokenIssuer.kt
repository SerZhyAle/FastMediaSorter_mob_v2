package com.sza.fastmediasorter.identity

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.sza.fastmediasorter.domain.identity.GoogleAccessToken
import com.sza.fastmediasorter.domain.identity.GoogleScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

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
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<Set<GoogleScope>, GoogleAccessToken>()

    /**
     * Returns a fresh-enough access token for the [email] account covering [scopes].
     * Returns null when `GoogleAuthUtil.getToken` fails (network, revoked grant, etc.).
     */
    @Suppress("DEPRECATION") // GoogleAuthUtil.getToken - Credential Manager has no token-issuance equivalent yet; see KDoc.
    suspend fun issue(email: String, scopes: Set<GoogleScope>): GoogleAccessToken? = mutex.withLock {
        val cached = cache[scopes]
        if (cached != null && cached.expiresAt.isAfter(Instant.now().plusSeconds(REFRESH_THRESHOLD_SECONDS))) {
            return@withLock cached
        }
        val scopeString = "oauth2:" + scopes.joinToString(" ") { it.value }
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = GoogleAuthUtil.getToken(context, email, scopeString)
                GoogleAccessToken(
                    token = raw,
                    scopes = scopes,
                    expiresAt = Instant.now().plus(TOKEN_LIFETIME)
                )
            }.onSuccess { cache[scopes] = it }
             .onFailure { Timber.w(it, "Token issuance failed for scopes=$scopes") }
             .getOrNull()
        }
    }

    /** Invalidates every cached token and revokes them locally via `GoogleAuthUtil.clearToken`. */
    @Suppress("DEPRECATION") // GoogleAuthUtil.clearToken - same Credential Manager gap as [issue]; see KDoc.
    suspend fun invalidate(): Unit = mutex.withLock {
        cache.values.forEach { runCatching { GoogleAuthUtil.clearToken(context, it.token) } }
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
    }
}
