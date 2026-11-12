package com.sza.fastmediasorter.domain.repository

import kotlinx.coroutines.flow.Flow
import java.net.HttpCookie
import java.time.Instant

/**
 * Domain-level facade over encrypted per-host authorization storage.
 *
 * S0166 keeps the persistence layer host/account-scoped so the share flow,
 * settings screen, and worker can make consistent decisions about active
 * sessions and permanent-skip records.
 */
interface AuthSessionRepository {

    fun observeAccounts(): Flow<List<AuthAccountDomain>>

    fun observeAccountsAll(): Flow<List<AuthAccountDomain>>

    suspend fun saveSession(
        host: String,
        accountId: String,
        displayName: String,
        cookies: List<HttpCookie>,
        userAgent: String? = null,
    )

    /**
     * S0211: dedup-aware save. Computes identity from [cookies] for known platforms;
     * if an existing account is found for (registrable domain, identity) → reuses its accountId
     * (S4549: a login saved on `www.` is found again from `vm.`). Otherwise generates a new UUID.
     * Cookies are stored under the exact [host]. Returns the accountId that was used, or `null`
     * when the call was rejected (blank host or empty cookies).
     */
    suspend fun saveSessionFromWebView(
        host: String,
        displayName: String,
        cookies: List<HttpCookie>,
        userAgent: String? = null,
    ): String?

    suspend fun deleteAccount(host: String, accountId: String)

    /** S0182: returns the User-Agent captured when the cookies were saved, or null. */
    suspend fun loadUserAgent(host: String, accountId: String): String?

    /**
     * S4549: every active account of the site [host] belongs to (registrable domain, so `vm.` and
     * `www.` agree), one entry per accountId, plus a dismissed marker when [host] was dismissed.
     * Each entry carries the host its cookies are stored under.
     */
    suspend fun listAccountsForHost(host: String): List<AuthAccountDomain>

    suspend fun markLastUsed(host: String, accountId: String)

    suspend fun updateDisplayName(host: String, accountId: String, newName: String)

    suspend fun hasAnySession(host: String): Boolean

    suspend fun markDismissed(host: String)

    suspend fun markDismissedForAccount(host: String, accountId: String, displayName: String? = null)

    suspend fun isDismissedForHost(host: String): Boolean

    suspend fun isDismissedForAccount(host: String, accountId: String): Boolean

    /**
     * S0406: dump every active session with live cookies for the unified backup payload.
     * Dismissed (permanent-skip) records are intentionally excluded - they are local UX state.
     */
    suspend fun exportSessions(): List<RawAuthSession>

    /** S0406: restore sessions from a backup payload, overwriting per (host, accountId). */
    suspend fun importSessions(sessions: List<RawAuthSession>)
}

data class AuthAccountDomain(
    val host: String,
    val accountId: String,
    val displayName: String,
    val cookieCount: Int,
    val savedAt: Instant,
    val lastUsedAt: Instant?,
    val isDismissed: Boolean = false,
)

/**
 * S0406: backup-facing snapshot of one saved authorization, including the raw cookies.
 * Feeds the unified backup payload; the persistence layer keeps its own encrypted format.
 */
data class RawAuthSession(
    val host: String,
    val accountId: String,
    val displayName: String,
    val userAgent: String?,
    val savedAtEpochMillis: Long,
    val lastUsedAtEpochMillis: Long,
    val cookies: List<HttpCookie>,
)