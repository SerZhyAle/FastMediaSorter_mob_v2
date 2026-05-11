package com.sza.fastmediasorter.domain.repository

import kotlinx.coroutines.flow.Flow
import java.net.HttpCookie
import java.time.Instant

/**
 * S0116 §5.1 pillar K: domain-level facade over the encrypted cookie store.
 *
 * S0155: Extended to support multiple accounts per host. Each account has a
 * stable [AuthAccountDomain.accountId] (UUID) and a user-editable [AuthAccountDomain.displayName].
 *
 * UI layer talks to this interface only; the concrete cookie storage backend is
 * internal to `data/`.
 */
interface AuthSessionRepository {

    // ── Multi-account API (S0155) ─────────────────────────────────────────────

    /** Hot list of all saved accounts across all hosts. Updates on every save / delete. */
    fun observeAccounts(): Flow<List<AuthAccountDomain>>

    suspend fun saveSession(host: String, accountId: String, displayName: String, cookies: List<HttpCookie>)

    suspend fun deleteAccount(host: String, accountId: String)

    suspend fun listAccountsForHost(host: String): List<AuthAccountDomain>

    suspend fun markLastUsed(host: String, accountId: String)

    suspend fun updateDisplayName(host: String, accountId: String, newName: String)

    /** True if at least one account (any) exists for [host]. */
    suspend fun hasAnySession(host: String): Boolean

    // ── Dismissed-record API (S0157) ──────────────────────────────────────────

    /** Stores a permanent-skip record for [host] — no cookies; offer will not appear until deleted. */
    suspend fun markDismissed(host: String)

    /** Stores a permanent-skip record for one reactive re-auth target under [host]. */
    suspend fun markDismissedForAccount(host: String, accountId: String, displayName: String? = null)

    /** Returns true if a dismissed record (no active session) exists for [host]. */
    suspend fun isDismissedForHost(host: String): Boolean

    /** Returns true if a dismissed record exists for the reactive re-auth target [accountId]. */
    suspend fun isDismissedForAccount(host: String, accountId: String): Boolean

    /** Hot flow including both active sessions and dismissed records; used by the settings screen. */
    fun observeAccountsAll(): Flow<List<AuthAccountDomain>>

    // ── Deprecated single-domain API (S0116; kept for callers updated in later phases) ──

    /** Hot list of saved sessions (one per domain). Updates immediately on save / delete. */
    @Deprecated("Use observeAccounts()", level = DeprecationLevel.WARNING)
    fun observeDomains(): Flow<List<AuthSessionDomain>>

    @Deprecated("Use saveSession(host, accountId, displayName, cookies)", level = DeprecationLevel.WARNING)
    suspend fun saveSession(domain: String, cookies: List<HttpCookie>)

    @Deprecated("Use deleteAccount(host, accountId)", level = DeprecationLevel.WARNING)
    suspend fun deleteSession(domain: String)

    @Deprecated("Use hasAnySession(host)", level = DeprecationLevel.WARNING)
    suspend fun hasSession(domain: String): Boolean
}

// ── Domain models ─────────────────────────────────────────────────────────────

/** S0155: per-account projection for UI listing. */
data class AuthAccountDomain(
    val host: String,
    val accountId: String,
    val displayName: String,
    val cookieCount: Int,
    val savedAt: Instant,
    val lastUsedAt: Instant?,
    /** S0157: true when this entry is a permanent-skip record (no active session). */
    val isDismissed: Boolean = false,
)

/** S0116: single-host session projection. @Deprecated — use [AuthAccountDomain]. */
@Deprecated("Use AuthAccountDomain", level = DeprecationLevel.WARNING)
data class AuthSessionDomain(
    val host: String,
    val cookieCount: Int,
    val savedAt: Instant,
)
