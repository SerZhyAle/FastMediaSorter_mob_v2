package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import com.sza.fastmediasorter.domain.repository.AuthSessionDomain
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpCookie
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar K: in-memory state-flow over [EncryptedCookieStore]. The
 * stored set is small (one entry per saved domain) so the list is rebuilt on
 * every mutation rather than mirrored field-by-field.
 */
@Singleton
class AuthSessionRepositoryImpl @Inject constructor(
    private val store: EncryptedCookieStore,
) : AuthSessionRepository {

    private val flow: MutableStateFlow<List<AuthSessionDomain>> = MutableStateFlow(snapshot())

    override fun observeDomains(): Flow<List<AuthSessionDomain>> = flow.asStateFlow()

    override suspend fun saveSession(domain: String, cookies: List<HttpCookie>) {
        withContext(Dispatchers.IO) {
            store.saveFor(domain, cookies)
            flow.value = snapshot()
        }
    }

    override suspend fun deleteSession(domain: String) {
        withContext(Dispatchers.IO) {
            store.deleteFor(domain)
            flow.value = snapshot()
        }
    }

    override suspend fun hasSession(domain: String): Boolean = withContext(Dispatchers.IO) {
        store.loadFor(domain).isNotEmpty()
    }

    private fun snapshot(): List<AuthSessionDomain> {
        val now = Instant.now()
        return store.listDomains().map { host ->
            AuthSessionDomain(
                host = host,
                cookieCount = store.loadFor(host).size,
                savedAt = store.savedAt(host) ?: now,
            )
        }
    }
}
