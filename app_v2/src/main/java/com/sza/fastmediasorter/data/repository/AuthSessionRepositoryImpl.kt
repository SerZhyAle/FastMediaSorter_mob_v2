package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import com.sza.fastmediasorter.domain.repository.AuthAccountDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpCookie
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar K: in-memory state-flow over [EncryptedCookieStore].
 *
 * S0155: Implements the full multi-account [AuthSessionRepository] API.
 * Legacy `observeDomains()` / `saveSession(domain, cookies)` stubs are kept
 * for backward-compat until all callers are updated in S0155 later phases.
 */
@Singleton
class AuthSessionRepositoryImpl @Inject constructor(
    private val store: EncryptedCookieStore,
) : AuthSessionRepository {

    /** Flow of active-only accounts; refreshed on every mutation. */
    private val accountFlow: MutableStateFlow<List<AuthAccountDomain>> =
        MutableStateFlow(emptyList())

    /** S0157: flow including active sessions AND dismissed records; for settings screen. */
    private val accountFlowAll: MutableStateFlow<List<AuthAccountDomain>> =
        MutableStateFlow(emptyList())

    /** Deprecated legacy flow; one entry per host (most-recently-saved account). */
    @Suppress("DEPRECATION")
    private val legacyFlow: MutableStateFlow<List<AuthSessionDomain>> =
        MutableStateFlow(emptyList())

    init {
        // S0155: migration requires a localized string for the legacy displayName;
        // the impl has no Context, so English fallback is used (store-level migration
        // runs once; user can rename in Settings).
        store.migrateIfNeeded(LEGACY_DISPLAY_NAME)
        refreshFlows()
    }

    // ── Multi-account API ─────────────────────────────────────────────────────

    override fun observeAccounts(): Flow<List<AuthAccountDomain>> = accountFlow.asStateFlow()

    override suspend fun saveSession(
        host: String,
        accountId: String,
        displayName: String,
        cookies: List<HttpCookie>,
    ) {
        if (host.isBlank() || accountId.isBlank() || cookies.isEmpty()) {
            Timber.i("AuthSessionRepositoryImpl: skipped empty account save host=%s accountId=%s", host, accountId)
            return
        }
        withContext(Dispatchers.IO) {
            store.saveForAccount(host, accountId, displayName, cookies)
            refreshFlows()
        }
    }

    override suspend fun deleteAccount(host: String, accountId: String) {
        withContext(Dispatchers.IO) {
            store.deleteForAccount(host, accountId)
            refreshFlows()
        }
    }

    override suspend fun listAccountsForHost(host: String): List<AuthAccountDomain> =
        withContext(Dispatchers.IO) {
            store.listAccounts(host).map { entry -> entry.toAuthAccountDomain(host) }
        }

    override suspend fun markLastUsed(host: String, accountId: String) {
        withContext(Dispatchers.IO) {
            store.markLastUsed(host, accountId)
            refreshFlows()
        }
    }

    override suspend fun updateDisplayName(host: String, accountId: String, newName: String) {
        withContext(Dispatchers.IO) {
            store.updateDisplayName(host, accountId, newName)
            refreshFlows()
        }
    }

    override suspend fun hasAnySession(host: String): Boolean =
        withContext(Dispatchers.IO) { store.listAccounts(host).isNotEmpty() }

    // ── Dismissed-record API (S0157) ──────────────────────────────────────────

    override suspend fun markDismissed(host: String) {
        if (host.isBlank()) return
        withContext(Dispatchers.IO) {
            store.saveAsDismissed(host)
            refreshFlows()
        }
    }

    override suspend fun markDismissedForAccount(host: String, accountId: String, displayName: String?) {
        if (host.isBlank()) return
        if (accountId.isBlank()) {
            markDismissed(host)
            return
        }
        withContext(Dispatchers.IO) {
            store.saveAsDismissedForAccount(host, accountId, displayName.orEmpty())
            refreshFlows()
        }
    }

    override suspend fun isDismissedForHost(host: String): Boolean =
        withContext(Dispatchers.IO) { store.hasDismissedRecord(host) }

    override suspend fun isDismissedForAccount(host: String, accountId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (accountId.isBlank()) {
                store.hasDismissedRecord(host)
            } else {
                // A host-level permanent skip must still suppress reactive prompts on that host.
                store.hasDismissedRecord(host) || store.hasDismissedRecordForAccount(host, accountId)
            }
        }

    override fun observeAccountsAll(): Flow<List<AuthAccountDomain>> = accountFlowAll.asStateFlow()

    // ── Deprecated legacy API ─────────────────────────────────────────────────

    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override fun observeDomains(): Flow<List<AuthSessionDomain>> = legacyFlow.asStateFlow()

    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override suspend fun saveSession(domain: String, cookies: List<HttpCookie>) {
        if (domain.isBlank() || cookies.isEmpty()) {
            Timber.i("S0140: skipped empty auth session save for %s", domain)
            return
        }
        withContext(Dispatchers.IO) {
            store.saveFor(domain, cookies)
            refreshFlows()
        }
    }

    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override suspend fun deleteSession(domain: String) {
        withContext(Dispatchers.IO) {
            store.deleteFor(domain)
            refreshFlows()
        }
    }

    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override suspend fun hasSession(domain: String): Boolean = hasAnySession(domain)

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun refreshFlows() {
        val all = store.listAllAccounts()
        val now = Instant.now()

        // S0157: prune only active entries with zero cookies; dismissed records must survive.
        val stalePairs = all.filter { (_, entry) ->
            entry.cookieCount == 0 && entry.type == EncryptedCookieStore.TYPE_ACTIVE
        }
        if (stalePairs.isNotEmpty()) {
            stalePairs.forEach { (host, entry) ->
                store.deleteForAccount(host, entry.accountId)
            }
            Timber.i("AuthSessionRepositoryImpl: pruned %d empty account(s)", stalePairs.size)
        }

        val live = all.filter { (_, entry) -> entry.cookieCount > 0 }
        val liveAccounts = live
            .map { (host, entry) -> entry.toAuthAccountDomain(host, now) }
            .sortedWith(SETTINGS_ACCOUNT_ORDER)
        accountFlow.value = liveAccounts

        // S0157: accountFlowAll includes active sessions + dismissed records.
        val dismissed = all.filter { (_, entry) -> entry.type == EncryptedCookieStore.TYPE_DISMISSED }
        accountFlowAll.value = (
            liveAccounts +
            dismissed.map { (host, entry) -> entry.toAuthAccountDomain(host, now, isDismissed = true) }
        ).sortedWith(SETTINGS_ACCOUNT_ORDER)

        @Suppress("DEPRECATION")
        legacyFlow.value = live
            .groupBy { (host, _) -> host }
            .mapValues { (_, pairs) ->
                pairs.maxByOrNull { (_, entry) -> entry.savedAt ?: Instant.MIN }
            }
            .mapNotNull { (host, pair) ->
                val entry = pair?.second ?: return@mapNotNull null
                AuthSessionDomain(
                    host = host,
                    cookieCount = entry.cookieCount,
                    savedAt = entry.savedAt ?: now,
                )
            }
            .sortedBy { it.host }
    }

    private fun EncryptedCookieStore.AccountEntry.toAuthAccountDomain(
        host: String,
        fallbackNow: Instant = Instant.now(),
        isDismissed: Boolean = false,
    ): AuthAccountDomain = AuthAccountDomain(
        host = host,
        accountId = accountId,
        displayName = displayName.trim().ifBlank {
            if (isDismissed) "" else accountId
        },
        cookieCount = cookieCount,
        savedAt = savedAt ?: fallbackNow,
        lastUsedAt = lastUsedAt,
        isDismissed = isDismissed,
    )

    private companion object {
        val SETTINGS_ACCOUNT_ORDER = Comparator<AuthAccountDomain> { first, second ->
            val byBucket = settingsBucket(first).compareTo(settingsBucket(second))
            if (byBucket != 0) return@Comparator byBucket

            val byLastUsed = compareValues(second.lastUsedAt, first.lastUsedAt)
            if (byLastUsed != 0) return@Comparator byLastUsed

            val bySavedAt = compareValues(second.savedAt, first.savedAt)
            if (bySavedAt != 0) return@Comparator bySavedAt

            val byHost = first.host.compareTo(second.host)
            if (byHost != 0) return@Comparator byHost

            first.displayName.compareTo(second.displayName)
        }

        // Hardcoded English fallback — the only context where the store migration runs;
        // the localized string (R.string.s0155_account_default_name) is not accessible here.
        const val LEGACY_DISPLAY_NAME = "Account 1"

        fun settingsBucket(account: AuthAccountDomain): Int = when {
            account.isDismissed -> 2
            account.lastUsedAt != null -> 0
            else -> 1
        }
    }
}
