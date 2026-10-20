package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.data.remote.sftp.SftpHostKeyPinRegistry
import com.sza.fastmediasorter.data.repository.settings.DeviceExchangeAccountStore
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SftpTunnelAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves tunnel host and port into an [ExchangeTunnelRoute]:
 * - If the tunnel endpoint matches an attached resource with an exchange account and resourceId,
 *   routes via [ExchangeTunnelRoute.OwnAccount] using that entry and resourceId.
 * - Otherwise routes via [ExchangeTunnelRoute.Outsider] with `parts.shareId`, using the entry's
 *   pinned certificate if an entry for the exchange server is held, or platform trust if unpinned/unknown.
 */
@Singleton
class DefaultExchangeTunnelRouter @Inject constructor(
    private val resourceDao: ResourceDao,
    private val accountStore: DeviceExchangeAccountStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ExchangeTunnelRouter {

    private val resourcesCache = MutableStateFlow<List<ResourceEntity>?>(null)
    private val entriesCache = MutableStateFlow<List<DeviceExchangeAccountEntry>?>(null)
    private val observing = AtomicBoolean(false)

    override fun route(tunnelHost: String, port: Int): ExchangeTunnelRoute? {
        val parts = SftpTunnelAddress.parse(tunnelHost) ?: return null
        ensureObserving()

        val allResources = resourcesCache.value ?: runBlocking {
            runCatching { resourceDao.getAllResourcesSync() }
                .onFailure { it.rethrowIfCancellation() }
                .getOrDefault(emptyList())
        }
        val allEntries = entriesCache.value ?: runBlocking {
            runCatching { accountStore.snapshot() }
                .onFailure { it.rethrowIfCancellation() }
                .getOrDefault(emptyList())
        }

        val address = "$tunnelHost:$port"
        val matchingResource = allResources.firstOrNull { entity ->
            val matchIdOrAddress = entity.exchangeResourceId == parts.shareId ||
                address in SftpHostKeyPinRegistry.addressesOf(entity)
            entity.type == ResourceType.SFTP &&
                entity.exchangeAccount != null &&
                entity.exchangeResourceId != null &&
                matchIdOrAddress
        }

        val ownRoute = matchingResource?.let { res ->
            val entry = allEntries.firstOrNull { it.identity == res.exchangeAccount }
            val resourceId = res.exchangeResourceId
            if (entry != null && resourceId != null) {
                ExchangeTunnelRoute.OwnAccount(entry, resourceId)
            } else {
                null
            }
        }

        return ownRoute ?: run {
            val entryForHost = allEntries.firstOrNull { it.host.equals(parts.exchangeHost, ignoreCase = true) }
            val trust = if (entryForHost?.pinnedCertificate != null) {
                ExchangeTrust.Verified(entryForHost.pinnedCertificate, entryForHost.pinnedKey)
            } else {
                ExchangeTrust.Verified(pinned = null)
            }
            ExchangeTunnelRoute.Outsider(parts.shareId, trust)
        }
    }

    private fun ensureObserving() {
        if (!observing.compareAndSet(false, true)) return
        applicationScope.launch {
            resourceDao.getAllResources()
                .catch { e -> Timber.e(e, "Exchange tunnel router: error observing resources") }
                .collect { resourcesCache.value = it }
        }
        applicationScope.launch {
            accountStore.entries
                .catch { e -> Timber.e(e, "Exchange tunnel router: error observing accounts") }
                .collect { entriesCache.value = it }
        }
    }
}
