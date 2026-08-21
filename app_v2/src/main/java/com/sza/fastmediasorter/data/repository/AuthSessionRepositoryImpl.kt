package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.link.auth.AccountIdentityExtractor
import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import com.sza.fastmediasorter.data.link.cookie.registrableDomainOrNull
import com.sza.fastmediasorter.domain.repository.AuthAccountDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.repository.RawAuthSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpCookie
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionRepositoryImpl @Inject constructor(
    private val store: EncryptedCookieStore,
    @ApplicationScope private val appScope: CoroutineScope,
) : AuthSessionRepository {

    private val accountFlow = MutableStateFlow<List<AuthAccountDomain>>(emptyList())
    private val accountFlowAll = MutableStateFlow<List<AuthAccountDomain>>(emptyList())

    init {
        // S1153: the one-time legacy migration + initial flow snapshot both read the encrypted
        // prefs from disk. This singleton is built during Activity field injection on the main
        // thread at startup, so doing it in the ctor tripped StrictMode DiskRead. Defer onto the
        // app-scope IO scope; the StateFlows start empty and populate reactively a moment later
        // (observers already collect them). migrateIfNeeded is idempotent + volatile-guarded, so
        // the tiny startup window before it completes is safe.
        appScope.launch {
            store.migrateIfNeeded(LEGACY_DISPLAY_NAME)
            refreshFlows()
        }
    }

    override fun observeAccounts(): Flow<List<AuthAccountDomain>> = accountFlow.asStateFlow()

    override fun observeAccountsAll(): Flow<List<AuthAccountDomain>> = accountFlowAll.asStateFlow()

    override suspend fun saveSession(
        host: String,
        accountId: String,
        displayName: String,
        cookies: List<HttpCookie>,
        userAgent: String?,
    ) {
        if (host.isBlank() || accountId.isBlank() || cookies.isEmpty()) {
            Timber.i("AuthSessionRepositoryImpl: skipped empty account save host=%s accountId=%s", host, accountId)
            return
        }
        withContext(Dispatchers.IO) {
            store.saveForAccount(host, accountId, displayName, cookies, userAgent)
            refreshFlows()
        }
    }

    override suspend fun saveSessionFromWebView(
        host: String,
        displayName: String,
        cookies: List<HttpCookie>,
        userAgent: String?,
    ): String? {
        if (host.isBlank() || cookies.isEmpty()) {
            Timber.i("AuthSessionRepositoryImpl: skipped empty webview save host=%s", host)
            return null
        }
        return withContext(Dispatchers.IO) {
            val identity = AccountIdentityExtractor.extract(host, cookies)
            val reusedId = identity?.let { store.findAccountIdByIdentity(host, it) }
            val accountId = reusedId ?: java.util.UUID.randomUUID().toString()
            store.saveForAccount(host, accountId, displayName, cookies, userAgent)
            refreshFlows()
            val maskedIdentity = identity?.let { it.take(4) + "***" } ?: "<none>"
            Timber.i(
                "webview save host=%s reused=%s identity=%s accountId=%s",
                host, reusedId != null, maskedIdentity, accountId,
            )
            accountId
        }
    }

    override suspend fun loadUserAgent(host: String, accountId: String): String? =
        withContext(Dispatchers.IO) { store.loadUserAgentForAccount(host, accountId) }

    override suspend fun deleteAccount(host: String, accountId: String) {
        withContext(Dispatchers.IO) {
            store.deleteForAccount(host, accountId)
            refreshFlows()
        }
    }

    override suspend fun listAccountsForHost(host: String): List<AuthAccountDomain> =
        withContext(Dispatchers.IO) {
            // S0166 fix: exact host first; if empty, fall back to any account that lives
            // under the same eTLD+1 registrable domain. Without this, sharing from
            // vm.tiktok.com fails to find the account saved under www.tiktok.com and the
            // user is forced to re-authenticate even though valid cookies exist.
            // Matches the resolution logic in LinkAutoDownloadCoordinator.resolveSessionHost.
            val exactEntries = store.listAccounts(host)
            val (resolvedHost, entries) = if (exactEntries.isNotEmpty()) {
                host to exactEntries
            } else {
                val reg = registrableDomainOrNull(host)
                val fallbackHost = if (reg != null) {
                    store.listAllAccounts()
                        .asSequence()
                        .filter { (h, e) ->
                            e.type == EncryptedCookieStore.TYPE_ACTIVE &&
                                e.cookieCount > 0 &&
                                registrableDomainOrNull(h) == reg
                        }
                        .map { it.first }
                        .firstOrNull()
                } else {
                    null
                }
                if (fallbackHost != null) {
                    Timber.d(
                        "listAccountsForHost eTLD+1 fallback: host=%s resolvedHost=%s reg=%s",
                        host,
                        fallbackHost,
                        reg,
                    )
                    fallbackHost to store.listAccounts(fallbackHost)
                } else {
                    host to emptyList()
                }
            }
            val live = entries.map { entry -> entry.toDomain(resolvedHost) }
            val dismissed = buildList {
                val hostDismiss = store.hasDismissedRecord(host)
                if (hostDismiss) {
                    add(
                        AuthAccountDomain(
                            host = host,
                            accountId = EncryptedCookieStore.DISMISSED_ACCOUNT_ID,
                            displayName = "",
                            cookieCount = 0,
                            savedAt = Instant.now(),
                            lastUsedAt = null,
                            isDismissed = true,
                        ),
                    )
                }
            }
            (live + dismissed).sortedWith(SETTINGS_ACCOUNT_ORDER)
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

    override suspend fun exportSessions(): List<RawAuthSession> = withContext(Dispatchers.IO) {
        store.listAllAccounts()
            .asSequence()
            .filter { (_, entry) -> entry.type == EncryptedCookieStore.TYPE_ACTIVE && entry.cookieCount > 0 }
            .mapNotNull { (host, entry) ->
                val cookies = store.loadForAccount(host, entry.accountId)
                if (cookies.isEmpty()) return@mapNotNull null
                RawAuthSession(
                    host = host,
                    accountId = entry.accountId,
                    displayName = entry.displayName,
                    userAgent = store.loadUserAgentForAccount(host, entry.accountId),
                    savedAtEpochMillis = entry.savedAt?.toEpochMilli() ?: 0L,
                    lastUsedAtEpochMillis = entry.lastUsedAt?.toEpochMilli() ?: 0L,
                    cookies = cookies,
                )
            }
            .toList()
    }

    override suspend fun importSessions(sessions: List<RawAuthSession>) {
        withContext(Dispatchers.IO) {
            sessions.forEach { session ->
                if (session.host.isBlank() || session.accountId.isBlank() || session.cookies.isEmpty()) {
                    return@forEach
                }
                store.saveForAccount(
                    host = session.host,
                    accountId = session.accountId,
                    displayName = session.displayName,
                    cookies = session.cookies,
                    userAgent = session.userAgent,
                )
            }
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
                store.hasDismissedRecord(host) || store.hasDismissedRecordForAccount(host, accountId)
            }
        }

    private fun refreshFlows() {
        val all = store.listAllAccounts()
        val now = Instant.now()

        val live = all.filter { (_, entry) ->
            entry.type == EncryptedCookieStore.TYPE_ACTIVE && entry.cookieCount > 0
        }
        val dismissed = all.filter { (_, entry) -> entry.type == EncryptedCookieStore.TYPE_DISMISSED }

        val liveAccounts = live
            .map { (host, entry) -> entry.toDomain(host, now) }
            .sortedWith(SETTINGS_ACCOUNT_ORDER)
        accountFlow.value = liveAccounts

        accountFlowAll.value = (
            liveAccounts +
                dismissed.map { (host, entry) -> entry.toDomain(host, now, isDismissed = true) }
        ).sortedWith(SETTINGS_ACCOUNT_ORDER)
    }

    private fun EncryptedCookieStore.AccountEntry.toDomain(
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
        const val LEGACY_DISPLAY_NAME = "Account 1"

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

        fun settingsBucket(account: AuthAccountDomain): Int = when {
            account.isDismissed -> 2
            account.lastUsedAt != null -> 0
            else -> 1
        }
    }
}