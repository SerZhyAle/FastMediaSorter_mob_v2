package com.sza.fastmediasorter.domain.model

/**
 * The records one store holds right now (contract DEVICE-EXCHANGE section 4): an account entry's server, keyed by
 * the entry identity, or the Drive channel, keyed by [DRIVE_SOURCE]. [listed] is false when the store could not be
 * read this time, so nothing under its key may be judged absent.
 */
data class ExchangeDirectorySource(
    val key: String,
    val devices: List<SftpRendezvousDevice>,
    val resources: List<SftpRendezvousResource>,
    val broadcasts: List<SftpRendezvousBroadcast>,
    val listed: Boolean = true,
) {

    companion object {
        /** The binding key of a resource attached from the Drive channel, which belongs to no account entry. */
        const val DRIVE_SOURCE = "drive"

        fun unreachable(key: String) = ExchangeDirectorySource(key, emptyList(), emptyList(), emptyList(), false)
    }
}

/** Why a listed resource can or cannot be attached from its record alone (sections 6.2, 9.2 and 15 item X). */
enum class ExchangeResourceAccess {
    ATTACHABLE,

    /** The producer kept `access` off this store, so the record names the share but cannot reach it. */
    NO_ACCESS,

    /** Item AG: a `pin-v1` lock with its access described - attaches only through the PIN proof (item AJ). */
    PIN_REQUIRED,

    /** A `pin-v1` lock whose access did not arrive: the producer is offline, or this entry never declared `pin-v1`. */
    PIN_NO_ACCESS,

    /** A lock this build cannot prove - an unknown or malformed scheme, or no usable root - listed, never attached. */
    PIN_LOCKED,
}

/** One resource of another device, under the store [sourceKey] it was listed by. */
data class ExchangeListedResource(
    val sourceKey: String,
    val record: SftpRendezvousResource,
    val online: Boolean,
) {

    val access: ExchangeResourceAccess
        get() = when {
            record.proofRoot != null -> ExchangeResourceAccess.PIN_REQUIRED
            record.pinProtected && record.protectionScheme == SftpRendezvousResource.PROTECTION_PIN_V1 &&
                record.descriptor == null -> ExchangeResourceAccess.PIN_NO_ACCESS
            record.pinProtected -> ExchangeResourceAccess.PIN_LOCKED
            record.descriptor == null -> ExchangeResourceAccess.NO_ACCESS
            else -> ExchangeResourceAccess.ATTACHABLE
        }
}

/** One live broadcast of another device; [playable] is false for a mode this build cannot play (9.1). */
data class ExchangeListedBroadcast(
    val sourceKey: String,
    val record: SftpRendezvousBroadcast,
    val playable: Boolean,
)

/** Another device of the account with what it publishes; [device] is null when only its records were listed. */
data class ExchangeListedDevice(
    val sourceKey: String,
    val deviceId: String,
    val device: SftpRendezvousDevice?,
    val resources: List<ExchangeListedResource>,
    val broadcasts: List<ExchangeListedBroadcast>,
) {

    val online: Boolean get() = device?.isOnline ?: resources.any(ExchangeListedResource::online)
}

/** The account directory as section 9.1 shows it: grouped by device, this device's own records left out. */
data class ExchangeDirectoryListing(
    val devices: List<ExchangeListedDevice>,
    val listedSources: Set<String>,
    val unreachableSources: Set<String>,
    /** At least one store has not answered yet; what is listed is only part of the directory. */
    val pending: Boolean = false,
) {

    fun resource(sourceKey: String, resourceId: String): ExchangeListedResource? = devices.asSequence()
        .filter { it.sourceKey == sourceKey }
        .flatMap { it.resources.asSequence() }
        .firstOrNull { it.record.resourceId == resourceId }

    companion object {
        val EMPTY = ExchangeDirectoryListing(emptyList(), emptySet(), emptySet())
    }
}

/** What an attached resource shows while its directory is open (section 9.3); none of them deletes it. */
enum class ExchangeAttachmentState {
    AVAILABLE,

    /** The producer is offline, or its store could not be read this time. */
    UNAVAILABLE,

    /** The record left a store that was read: unpublished, or its device revoked. */
    NO_LONGER_PUBLISHED,
}

/**
 * The pure rules of the directory surface: merging the stores of section 4 into the listing of section 9.1, and
 * judging an attached resource by section 9.3.
 */
object ExchangeDirectoryRules {

    /**
     * Merges [servers] and [drive] into one listing. A record both stores carry keeps the later `updatedAt` and
     * stays under the server entry, so its binding key does not depend on which store answered last; a record only
     * Drive carries is listed under [ExchangeDirectorySource.DRIVE_SOURCE]. Records of [ownDeviceIds] are hidden.
     */
    fun listing(
        servers: List<ExchangeDirectorySource>,
        drive: ExchangeDirectorySource?,
        ownDeviceIds: Set<String>,
        playableModes: Set<String>,
    ): ExchangeDirectoryListing {
        val driveResources = drive?.resources.orEmpty().associateBy(SftpRendezvousResource::resourceId)
        val driveDevices = drive?.devices.orEmpty().associateBy(SftpRendezvousDevice::deviceId)
        val serverResourceIds = servers.flatMapTo(HashSet()) { it.resources.map(SftpRendezvousResource::resourceId) }
        val serverDeviceIds = servers.flatMapTo(HashSet()) { it.devices.map(SftpRendezvousDevice::deviceId) }
        val merged = servers.map { source ->
            source.copy(
                devices = source.devices.map {
                    later(it, driveDevices[it.deviceId], SftpRendezvousDevice::updatedAtMs)
                },
                resources = source.resources.map {
                    later(it, driveResources[it.resourceId], SftpRendezvousResource::updatedAtMs)
                },
            )
        }
        val driveOnly = drive?.copy(
            devices = drive.devices.filterNot { it.deviceId in serverDeviceIds },
            resources = drive.resources.filterNot { it.resourceId in serverResourceIds },
        )
        val all = merged + listOfNotNull(driveOnly)
        return ExchangeDirectoryListing(
            devices = all.filter(ExchangeDirectorySource::listed)
                .flatMap { grouped(it, ownDeviceIds, playableModes, driveDevices) },
            listedSources = all.filter(ExchangeDirectorySource::listed).mapTo(HashSet(), ExchangeDirectorySource::key),
            unreachableSources = all.filterNot(ExchangeDirectorySource::listed)
                .mapTo(HashSet(), ExchangeDirectorySource::key),
        )
    }

    /**
     * Section 9.3 for a resource bound to [sourceKey] and [resourceId]. Absence counts only from a store that was
     * read; a store not read leaves the resource unavailable rather than gone.
     */
    fun attachmentState(
        listing: ExchangeDirectoryListing,
        sourceKey: String,
        resourceId: String,
    ): ExchangeAttachmentState {
        val listed = listing.resource(sourceKey, resourceId)
        return when {
            sourceKey !in listing.listedSources -> ExchangeAttachmentState.UNAVAILABLE
            listed == null -> ExchangeAttachmentState.NO_LONGER_PUBLISHED
            listed.online -> ExchangeAttachmentState.AVAILABLE
            else -> ExchangeAttachmentState.UNAVAILABLE
        }
    }

    private fun grouped(
        source: ExchangeDirectorySource,
        ownDeviceIds: Set<String>,
        playableModes: Set<String>,
        driveDevices: Map<String, SftpRendezvousDevice>,
    ): List<ExchangeListedDevice> {
        val devices = source.devices.associateBy(SftpRendezvousDevice::deviceId)
        val resources = source.resources.filterNot { it.deviceId in ownDeviceIds }.groupBy { it.deviceId }
        val broadcasts = source.broadcasts.filterNot { it.deviceId in ownDeviceIds }.groupBy { it.deviceId }
        val ids = (devices.keys + resources.keys + broadcasts.keys).filterNot { it in ownDeviceIds }
        return ids.map { deviceId ->
            val device = devices[deviceId] ?: driveDevices[deviceId]
            ExchangeListedDevice(
                sourceKey = source.key,
                deviceId = deviceId,
                device = device,
                resources = resources[deviceId].orEmpty().map { record ->
                    ExchangeListedResource(source.key, record, isOnline(record, device))
                },
                broadcasts = broadcasts[deviceId].orEmpty().map { record ->
                    ExchangeListedBroadcast(source.key, record, record.mode in playableModes)
                },
            )
        }.sortedWith(compareBy<ExchangeListedDevice> { !it.online }.thenBy { it.device?.deviceName ?: it.deviceId })
    }

    // The resource record carries its producer's presence (6.2); a record without one falls back to the device's.
    private fun isOnline(record: SftpRendezvousResource, device: SftpRendezvousDevice?): Boolean =
        record.presence?.let { it == SftpRendezvousDevice.PRESENCE_ONLINE } ?: (device?.isOnline == true)

    private fun <T> later(held: T, other: T?, updatedAt: (T) -> Long): T =
        if (other != null && updatedAt(other) > updatedAt(held)) other else held
}
