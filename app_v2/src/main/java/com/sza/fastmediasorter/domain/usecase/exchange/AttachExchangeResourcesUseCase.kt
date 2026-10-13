package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.repository.ExchangeDirectoryRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.ExchangeAttachPlanner
import com.sza.fastmediasorter.domain.model.ExchangeAttachResult
import com.sza.fastmediasorter.domain.model.ExchangeAttachment
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryListing
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryRules
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryView
import com.sza.fastmediasorter.domain.model.ExchangeListedResource
import com.sza.fastmediasorter.domain.model.ExchangeResourceAccess
import com.sza.fastmediasorter.domain.model.ExchangeResourceDefaults
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * The directory surface of S4116 phase 05 (contract DEVICE-EXCHANGE 9.1-9.3): the account's resources by device,
 * attaching the ones the user marks, keeping attached ones current, and showing what became of each. It never
 * deletes a resource - absence is not authority to destroy (9.3).
 */
class AttachExchangeResourcesUseCase @Inject constructor(
    private val directory: ExchangeDirectoryRepository,
    private val accounts: DeviceExchangeAccountRepository,
    private val resources: ResourceRepository,
    private val addResource: AddResourceUseCase,
    private val credentials: SmbOperationsUseCase,
    private val settings: SettingsRepository,
    private val capabilities: CapabilityAvailability,
) {

    /** The live directory; every listing is first applied to the attached resources by [sync]. */
    fun observe(): Flow<ExchangeDirectoryView> = directory.observe(playableModes()).map { sync(it) }

    /**
     * Replaces the access of every attached resource whose record changed it, attaches what an auto-attach entry
     * lists (item AD), and judges each attached resource by section 9.3.
     */
    suspend fun sync(listing: ExchangeDirectoryListing): ExchangeDirectoryView {
        val entries = accounts.entries.first()
        attached().forEach { bound -> refresh(bound, listing, entries) }
        val automatic = entries.filter(DeviceExchangeAccountEntry::autoAttach).mapTo(HashSet()) { it.identity }
        val unbound = listing.devices.flatMap { it.resources }.filter { item ->
            item.sourceKey in automatic && item.access == ExchangeResourceAccess.ATTACHABLE
        }
        if (unbound.isNotEmpty()) attach(unbound, entries)
        return ExchangeDirectoryView(listing, attached().map { it.toAttachment(listing) })
    }

    /** Attaches [items], skipping any already attached under the same binding (9.8). */
    suspend fun attach(items: List<ExchangeListedResource>): ExchangeAttachResult {
        Timber.d("S4116: attach from my devices")
        return attach(items, accounts.entries.first())
    }

    private suspend fun attach(
        items: List<ExchangeListedResource>,
        entries: List<DeviceExchangeAccountEntry>,
    ): ExchangeAttachResult {
        val held = attached().mapTo(HashSet()) { it.binding() }
        val fresh = items.filterNot { ExchangeBinding(it.sourceKey, it.record.resourceId) in held }
        val defaults = defaults()
        val made = fresh.mapNotNull { item ->
            val access = ExchangeAttachPlanner.access(item, endpointOf(item.sourceKey, entries))
            val credentialsId = access?.let { saveCredentials(it) }
            credentialsId?.let { ExchangeAttachPlanner.newResource(item, access, it, defaults) }
        }
        val added = if (made.isEmpty()) 0 else addResource.addMultiple(made).getOrNull()?.addedCount ?: 0
        val failed = fresh.size - added
        if (failed > 0) Timber.i("AttachExchangeResourcesUseCase: %d of %d not attached", failed, fresh.size)
        return ExchangeAttachResult(attached = added, alreadyAttached = items.size - fresh.size, failed = failed)
    }

    private suspend fun refresh(
        bound: MediaResource,
        listing: ExchangeDirectoryListing,
        entries: List<DeviceExchangeAccountEntry>,
    ) {
        val binding = bound.binding()
        val access = listing.resource(binding.sourceKey, binding.resourceId)
            ?.let { item -> ExchangeAttachPlanner.access(item, endpointOf(item.sourceKey, entries)) } ?: return
        val credentialsId = saveCredentials(access) ?: return
        ExchangeAttachPlanner.refreshed(bound, access, credentialsId)?.let { resources.updateResource(it) }
    }

    // Credentials are looked up per host:port at connect time, so every path of the share needs its own row.
    private suspend fun saveCredentials(access: ExchangeAttachPlanner.Access): String? {
        val password = access.payload.password.orEmpty()
        val ids = access.credentialHosts.map { path ->
            credentials.saveSftpCredentials(path.host, path.port, access.payload.username, password).getOrNull()
        }
        return ids.firstOrNull()
    }

    private suspend fun attached(): List<MediaResource> = resources.getAllResourcesSync()
        .filter { it.exchangeAccount != null && it.exchangeResourceId != null }

    private fun MediaResource.binding() =
        ExchangeBinding(checkNotNull(exchangeAccount), checkNotNull(exchangeResourceId))

    private fun MediaResource.toAttachment(listing: ExchangeDirectoryListing): ExchangeAttachment {
        val binding = binding()
        val state = ExchangeDirectoryRules.attachmentState(listing, binding.sourceKey, binding.resourceId)
        return ExchangeAttachment(id, name, binding, state)
    }

    private fun endpointOf(sourceKey: String, entries: List<DeviceExchangeAccountEntry>): HostPort? =
        entries.firstOrNull { it.identity == sourceKey }?.let { HostPort(it.host, it.port) }

    private suspend fun defaults(): ExchangeResourceDefaults {
        val current = settings.getSettings().first()
        return ExchangeResourceDefaults(
            displayMode = if (current.defaultGridMode) DisplayMode.GRID else DisplayMode.LIST,
            sortMode = current.defaultSortMode,
            slideshowInterval = current.slideshowInterval,
            supportedMediaTypes = if (current.allFiles) {
                MediaType.entries.toSet()
            } else {
                buildSet {
                    if (current.supportImages) add(MediaType.IMAGE)
                    if (current.supportVideos) add(MediaType.VIDEO)
                    if (current.supportAudio) add(MediaType.AUDIO)
                    if (current.supportGifs) add(MediaType.GIF)
                }
            },
        )
    }

    // A build without the streams player plays no broadcast mode; marking them unsupported is section 9.1.
    private fun playableModes(): Set<String> =
        if (capabilities.isStreamsAvailable()) BroadcastMode.entries.mapTo(HashSet()) { it.name } else emptySet()
}
