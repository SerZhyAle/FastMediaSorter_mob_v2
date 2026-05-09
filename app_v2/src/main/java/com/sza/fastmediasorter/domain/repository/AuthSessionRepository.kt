package com.sza.fastmediasorter.domain.repository

import kotlinx.coroutines.flow.Flow
import java.net.HttpCookie
import java.time.Instant

/**
 * S0116 §5.1 pillar K: domain-level facade over the encrypted cookie store.
 *
 * UI layer (saved-authorizations list, post-auth retry coordinator) talks to this
 * interface only; the concrete cookie storage backend is internal to `data/`.
 */
interface AuthSessionRepository {
    /** Hot list of saved sessions (one per domain). Updates immediately on save / delete. */
    fun observeDomains(): Flow<List<AuthSessionDomain>>

    suspend fun saveSession(domain: String, cookies: List<HttpCookie>)

    suspend fun deleteSession(domain: String)

    suspend fun hasSession(domain: String): Boolean
}

/** Public projection of a single saved auth session for UI listing. */
data class AuthSessionDomain(
    val host: String,
    val cookieCount: Int,
    val savedAt: Instant,
)
