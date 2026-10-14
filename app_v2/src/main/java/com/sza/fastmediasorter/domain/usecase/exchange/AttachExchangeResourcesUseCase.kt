package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.data.remote.exchange.ExchangeResourcePinStore
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.repository.ExchangeDirectoryRepository
import com.sza.fastmediasorter.domain.model.AttachmentPin
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.ExchangeAttachPlanner
import com.sza.fastmediasorter.domain.model.ExchangeAttachResult
import com.sza.fastmediasorter.domain.model.ExchangeAttachment
import com.sza.fastmediasorter.domain.model.ExchangeAttachmentState
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryListing
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryRules
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryView
import com.sza.fastmediasorter.domain.model.ExchangeListedResource
import com.sza.fastmediasorter.domain.model.ExchangePinAttempt
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * The directory surface of S4116 phase 05 (contract DEVICE-EXCHANGE 9.1-9.3): the account's resources by device,
 * attaching the ones the user marks, keeping attached ones current, and showing what became of each. It never
 * deletes a resource - absence is not authority to destroy (9.3).
 *
 * A protected resource (17 items AG-AJ) is attached only through [attachProtected]: the PIN is proven at its
 * owner first and kept only once the owner accepted it.
 */
class AttachExchangeResourcesUseCase @Inject constructor(
    private val directory: ExchangeDirectoryRepository,
    private val accounts: DeviceExchangeAccountRepository,
    private val resources: ResourceRepository,
    private val addResource: AddResourceUseCase,
    private val credentials: SmbOperationsUseCase,
    private val settings: SettingsRepository,
    private val capabilities: CapabilityAvailability,
    private val pinStore: ExchangeResourcePinStore,
    private val verifyPin: VerifyExchangePinUseCase,
) {

    /** The live directory; every listing is first applied to the attached resources by [sync]. */
    fun observe(): Flow<ExchangeDirectoryView> =
        combine(directory.observe(playableModes()), verifyPin.refusals) { listing, _ -> listing }.map { sync(it) }

    /**
     * Replaces the access of every attached resource whose record changed it, attaches what an auto-attach entry
     * lists (item AD), and judges each attached resource by section 9.3.
     */
    suspend fun sync(listing: ExchangeDirectoryListing): ExchangeDirectoryView {
        val entries = accounts.entries.first()
        attached().forEach { bound ->
            refresh(bound, listing, entries)
            forgetPinWhenGone(bound, listing)
        }
        val automatic = entries.filter(DeviceExchangeAccountEntry::autoAttach).mapTo(HashSet()) { it.identity }
        val unbound = listing.devices.flatMap { it.resources }.filter { item ->
            item.sourceKey in automatic && item.access == ExchangeResourceAccess.ATTACHABLE
        }
        if (unbound.isNotEmpty()) attach(unbound, entries)
        val refused = verifyPin.refusals.value
        val signIn = entries.filterNot(DeviceExchangeAccountEntry::pinCapable).mapTo(HashSet()) { it.identity }
        return ExchangeDirectoryView(listing, attached().map { it.toAttachment(listing, refused) }, signIn)
    }

    /**
     * Attaches [items] that need no PIN, skipping any already attached under the same binding (9.8). A protected
     * item is not attached here: its binding comes back in [ExchangeAttachResult.needPin].
     */
    suspend fun attach(items: List<ExchangeListedResource>): ExchangeAttachResult {
        return attach(items, accounts.entries.first())
    }

    /**
     * Attaches the protected [item] with [pin]. The PIN is presented to the resource's owner through a session of
     * its own; only an accepted PIN is kept and only then is the resource added (17 item AJ).
     */
    suspend fun attachProtected(item: ExchangeListedResource, pin: String): ExchangePinAttempt {
        val access = usableAccess(item)
        val proven = access?.let { verifyPin(it, pin) } ?: ExchangePinAttempt.FAILED
        return if (access != null && proven == ExchangePinAttempt.ACCEPTED) keepAndAdd(item, access, pin) else proven
    }

    /** Replaces the PIN of an attached resource once the owner accepts the new one (17 item AI). */
    suspend fun renewPin(item: ExchangeListedResource, pin: String): ExchangePinAttempt {
        val binding = ExchangeBinding(item.sourceKey, item.record.resourceId)
        // No usable access means the owner is offline: nothing was proven either way.
        val proven = usableAccess(item)?.let { verifyPin(it, pin) } ?: ExchangePinAttempt.UNREACHABLE
        if (proven == ExchangePinAttempt.ACCEPTED) {
            if (!pinStore.save(binding, pin)) return ExchangePinAttempt.FAILED
            verifyPin.accepted(binding)
        }
        return proven
    }

    private suspend fun attach(
        items: List<ExchangeListedResource>,
        entries: List<DeviceExchangeAccountEntry>,
    ): ExchangeAttachResult {
        val held = attached().mapTo(HashSet()) { it.binding() }
        val fresh = items.filterNot { ExchangeBinding(it.sourceKey, it.record.resourceId) in held }
        val (protectedItems, open) = fresh.partition { it.access == ExchangeResourceAccess.PIN_REQUIRED }
        val defaults = defaults()
        val made = open.mapNotNull { item ->
            val access = ExchangeAttachPlanner.access(item, endpointOf(item.sourceKey, entries))
            val credentialsId = access?.let { saveCredentials(it) }
            credentialsId?.let { ExchangeAttachPlanner.newResource(item, access, it, defaults) }
        }
        val added = if (made.isEmpty()) 0 else addResource.addMultiple(made).getOrNull()?.addedCount ?: 0
        val failed = open.size - added
        if (failed > 0) Timber.i("AttachExchangeResourcesUseCase: %d of %d not attached", failed, open.size)
        return ExchangeAttachResult(
            attached = added,
            alreadyAttached = items.size - fresh.size,
            failed = failed,
            needPin = protectedItems.map { ExchangeBinding(it.sourceKey, it.record.resourceId) },
        )
    }

    private suspend fun usableAccess(item: ExchangeListedResource): ExchangeAttachPlanner.Access? =
        ExchangeAttachPlanner.access(item, endpointOf(item.sourceKey, accounts.entries.first()))
            ?.takeIf { it.proofRoot != null }

    private suspend fun keepAndAdd(
        item: ExchangeListedResource,
        access: ExchangeAttachPlanner.Access,
        pin: String,
    ): ExchangePinAttempt {
        val binding = ExchangeBinding(item.sourceKey, item.record.resourceId)
        val credentialsId = if (pinStore.save(binding, pin)) saveCredentials(access) else null
        val added = credentialsId?.let { id ->
            val resource = ExchangeAttachPlanner.newResource(item, access, id, defaults())
            addResource.addMultiple(listOf(resource)).getOrNull()?.addedCount
        } ?: 0
        if (added > 0) verifyPin.accepted(binding) else pinStore.forget(binding)
        return if (added > 0) ExchangePinAttempt.ACCEPTED else ExchangePinAttempt.FAILED
    }

    // DEVICE-EXCHANGE 17 item AI: a PIN is cleared once the resource shows "no longer published". The resource
    // itself stays (9.3); if its producer publishes it again, the PIN is asked for again.
    private suspend fun forgetPinWhenGone(bound: MediaResource, listing: ExchangeDirectoryListing) {
        val binding = bound.binding()
        val state = ExchangeDirectoryRules.attachmentState(listing, binding.sourceKey, binding.resourceId)
        val gone = state == ExchangeAttachmentState.NO_LONGER_PUBLISHED
        // Every directory emission passes here, so a binding that holds nothing costs no preferences write.
        if (gone && pinStore.pin(binding) != null) pinStore.forget(binding)
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

    private suspend fun MediaResource.toAttachment(
        listing: ExchangeDirectoryListing,
        refused: Set<ExchangeBinding>,
    ): ExchangeAttachment {
        val binding = binding()
        val state = ExchangeDirectoryRules.attachmentState(listing, binding.sourceKey, binding.resourceId)
        val stored = pinStore.pin(binding) != null
        val listedAsProtected = listing.resource(binding.sourceKey, binding.resourceId)?.record?.pinProtected == true
        val pin = when {
            stored && binding !in refused -> AttachmentPin.SAVED
            stored || listedAsProtected -> AttachmentPin.NEEDED
            else -> AttachmentPin.NONE
        }
        return ExchangeAttachment(id, name, binding, state, pin)
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
