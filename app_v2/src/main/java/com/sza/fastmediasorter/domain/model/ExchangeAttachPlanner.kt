package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.utils.SshFingerprintNormalizer

/** The binding of an attached resource (contract DEVICE-EXCHANGE 9.2 and 9.8): its store key and `resourceId`. */
data class ExchangeBinding(val sourceKey: String, val resourceId: String)

/** One resource attached from the directory, as the directory surface shows it now. */
data class ExchangeAttachment(
    val localId: Long,
    val name: String,
    val binding: ExchangeBinding,
    val state: ExchangeAttachmentState,
)

/** The directory surface's whole input: the listing and the resources already attached from it. */
data class ExchangeDirectoryView(
    val listing: ExchangeDirectoryListing,
    val attachments: List<ExchangeAttachment>,
) {

    fun isAttached(item: ExchangeListedResource): Boolean =
        attachments.any { it.binding == ExchangeBinding(item.sourceKey, item.record.resourceId) }

    companion object {
        val EMPTY = ExchangeDirectoryView(ExchangeDirectoryListing.EMPTY, emptyList())
    }
}

/** How many of the asked resources were attached; [failed] names those whose access could not be used. */
data class ExchangeAttachResult(val attached: Int, val alreadyAttached: Int, val failed: Int)

/** The defaults a new resource takes from the app settings, as the add-resource wizard applies them. */
data class ExchangeResourceDefaults(
    val displayMode: DisplayMode,
    val sortMode: SortMode,
    val slideshowInterval: Int,
    val supportedMediaTypes: Set<MediaType>,
)

/**
 * Turns a listed `sftp-share` into an SFTP resource and keeps it current (section 9.2): the `access.descriptor` is
 * an `FMSSFTP1`/`FMSSFTP2` code exactly as a scanned pairing code, so the resource it makes is the one the QR path
 * makes. The first direct host is the address; the other direct hosts and the tunnel are alternate paths, direct
 * first (9.4).
 */
object ExchangeAttachPlanner {

    /** What [descriptor] says about reaching the share; [credentialHosts] each need a credentials row. */
    data class Access(
        val payload: SftpPairingPayload,
        val pin: String,
        val path: String,
        val altPaths: List<HostPort>,
    ) {
        val credentialHosts: List<HostPort>
            get() = listOf(HostPort(payload.hosts.first(), payload.port)) + altPaths

        override fun toString(): String = "Access(hosts=${payload.hosts.size}, altPaths=${altPaths.size})"
    }

    /**
     * Null when the record carries no usable access: none at all, a locked record, or a code this build cannot
     * read. [entryEndpoint] is the server of the entry that listed it, which reaches the share by its `shareId`
     * when the code names no exchange of its own.
     */
    fun access(item: ExchangeListedResource, entryEndpoint: HostPort?): Access? {
        val descriptor = item.record.descriptor?.takeIf { item.access == ExchangeResourceAccess.ATTACHABLE }
        val payload = descriptor?.let(SftpPairingPayload::decode)?.takeIf { it.hosts.isNotEmpty() }
        val pin = payload?.let { SshFingerprintNormalizer.canonical(it.hostKeyFingerprint) }
        if (payload == null || pin == null) return null
        val root = item.record.root?.let { if (it.startsWith(PATH_SEPARATOR)) it else PATH_SEPARATOR + it }.orEmpty()
        val tunnel = SftpTunnelAddress.fromPairing(payload) ?: shareTunnel(item.record.shareId, entryEndpoint)
        return Access(
            payload = payload,
            pin = pin,
            path = "$SCHEME${payload.hosts.first()}:${payload.port}$root",
            altPaths = payload.hosts.drop(1).map { HostPort(it, payload.port) } + listOfNotNull(tunnel),
        )
    }

    fun newResource(
        item: ExchangeListedResource,
        access: Access,
        credentialsId: String,
        defaults: ExchangeResourceDefaults,
    ): MediaResource = MediaResource(
        name = item.record.name.ifBlank { access.payload.hosts.first() },
        path = access.path,
        type = ResourceType.SFTP,
        credentialsId = credentialsId,
        displayMode = defaults.displayMode,
        sortMode = defaults.sortMode,
        slideshowInterval = defaults.slideshowInterval,
        supportedMediaTypes = defaults.supportedMediaTypes,
        hostKeyFingerprint = access.pin,
        altAccessPaths = access.altPaths,
        exchangeAccount = item.sourceKey,
        exchangeResourceId = item.record.resourceId,
    )

    /**
     * The attached resource with a later record's access in place of the kept one (9.2), or null when nothing it
     * stores changed. Everything the user set on the resource stays.
     */
    fun refreshed(bound: MediaResource, access: Access, credentialsId: String): MediaResource? {
        val updated = bound.copy(
            path = access.path,
            credentialsId = credentialsId,
            hostKeyFingerprint = access.pin,
            altAccessPaths = access.altPaths,
        )
        return updated.takeIf { it != bound }
    }

    private fun shareTunnel(shareId: String?, entryEndpoint: HostPort?): HostPort? {
        if (shareId == null || entryEndpoint == null) return null
        return HostPort(SftpTunnelAddress.host(shareId, entryEndpoint.host), entryEndpoint.port)
    }

    private const val SCHEME = "sftp://"
    private const val PATH_SEPARATOR = "/"
}
