package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.utils.SftpPathUtils
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer

/** The binding of an attached resource (contract DEVICE-EXCHANGE 9.2 and 9.8): its store key and `resourceId`. */
data class ExchangeBinding(val sourceKey: String, val resourceId: String)

/** Whether an attached resource holds the PIN its owner asks for (17 items AI and AJ). */
enum class AttachmentPin {
    /** Not protected, or nothing says it is. */
    NONE,

    /** The stored PIN was accepted at its last use. */
    SAVED,

    /** No PIN is stored, or the owner refused the stored one: it has to be entered again. */
    NEEDED,
}

/** One resource attached from the directory, as the directory surface shows it now. */
data class ExchangeAttachment(
    val localId: Long,
    val name: String,
    val binding: ExchangeBinding,
    val state: ExchangeAttachmentState,
    val pin: AttachmentPin = AttachmentPin.NONE,
)

/**
 * The directory surface's whole input: the listing, the resources already attached from it, and the account
 * entries that never declared `pin-v1` ([signInSources]) - for those the server withholds a protected
 * resource's access until the device signs in again.
 */
data class ExchangeDirectoryView(
    val listing: ExchangeDirectoryListing,
    val attachments: List<ExchangeAttachment>,
    val signInSources: Set<String> = emptySet(),
) {

    fun isAttached(item: ExchangeListedResource): Boolean =
        attachments.any { it.binding == ExchangeBinding(item.sourceKey, item.record.resourceId) }

    fun attachment(binding: ExchangeBinding): ExchangeAttachment? = attachments.firstOrNull { it.binding == binding }

    companion object {
        val EMPTY = ExchangeDirectoryView(ExchangeDirectoryListing.EMPTY, emptyList())
    }
}

/**
 * How many of the asked resources were attached; [failed] names those whose access could not be used, and
 * [needPin] those that wait for their PIN - they are never attached without the proof (17 item AJ).
 */
data class ExchangeAttachResult(
    val attached: Int,
    val alreadyAttached: Int,
    val failed: Int,
    val needPin: List<ExchangeBinding> = emptyList(),
)

/** How presenting a PIN for one protected resource ended; none of them leaves a PIN the owner did not accept. */
enum class ExchangePinAttempt {
    ACCEPTED,

    /** The owner answered "locked": the PIN is wrong, or was changed. */
    REFUSED,

    /** No address of the resource answered, so nothing was proven either way. */
    UNREACHABLE,

    /** The access was unusable, the secret store refused the PIN, or the resource could not be added. */
    FAILED,
}

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
        /** The `Resource` field of the PIN proof (17 item AH); null for an unprotected share. */
        val proofRoot: String? = null,
    ) {
        val credentialHosts: List<HostPort>
            get() = listOf(HostPort(payload.hosts.first(), payload.port)) + altPaths

        override fun toString(): String = "Access(hosts=${payload.hosts.size}, altPaths=${altPaths.size})"
    }

    /**
     * Null when the record carries no usable access: none at all, a lock this build cannot prove, or a code this
     * build cannot read. A `pin-v1` record answers with its access and [Access.proofRoot]; whether the PIN is at
     * hand is the caller's business. [entryEndpoint] is the server of the entry that listed it, which reaches the
     * share by its `shareId` when the code names no exchange of its own.
     */
    fun access(item: ExchangeListedResource, entryEndpoint: HostPort?): Access? {
        val usable = item.access == ExchangeResourceAccess.ATTACHABLE ||
            item.access == ExchangeResourceAccess.PIN_REQUIRED
        val descriptor = item.record.descriptor?.takeIf { usable }
        val payload = descriptor?.let(SftpPairingPayload::decode)?.takeIf { it.hosts.isNotEmpty() }
        val pin = payload?.let { SshFingerprintNormalizer.canonical(it.hostKeyFingerprint) }
        if (payload == null || pin == null) return null
        val root = item.record.root?.let { if (it.startsWith(PATH_SEPARATOR)) it else PATH_SEPARATOR + it }.orEmpty()
        val tunnel = SftpTunnelAddress.fromPairing(payload)
            ?: shareTunnel(item.record.shareId ?: item.record.resourceId, entryEndpoint)
        return Access(
            payload = payload,
            pin = pin,
            path = "$SCHEME${payload.hosts.first()}:${payload.port}$root",
            altPaths = payload.hosts.drop(1).map { HostPort(it, payload.port) } + listOfNotNull(tunnel),
            proofRoot = item.record.proofRoot,
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
     * stores changed. Everything the user set on the resource stays, and so does a tunnel through another exchange
     * server that listed the same folder (item BB): this listing does not own it. [entryHost] is the server of the
     * entry that listed [access].
     */
    fun refreshed(bound: MediaResource, access: Access, credentialsId: String, entryHost: String?): MediaResource? {
        val otherServers = bound.altAccessPaths.filter { alt ->
            val tunnel = SftpTunnelAddress.parse(alt.host)
            tunnel != null && tunnel.exchangeHost != entryHost && alt !in access.altPaths
        }
        val updated = bound.copy(
            path = access.path,
            credentialsId = credentialsId,
            hostKeyFingerprint = access.pin,
            altAccessPaths = access.altPaths + otherServers,
        )
        return updated.takeIf { it != bound }
    }

    /** The identity of the folder [item] lists (item BA): its host key and the root a record of it opens. */
    fun identity(item: ExchangeListedResource, access: Access): ResourceIdentity? =
        ResourceIdentity.of(item.record.hostKeyFingerprint ?: access.pin, item.record.root ?: item.record.identityRoot)

    /**
     * [existing] - a record of the same identity reached by another channel - with [item]'s channels added
     * (item BB). An unbound record takes the binding and every path of [access]; a record already bound to another
     * entry takes only the tunnel, because its own entry keeps the binding. Name, read-only and every user setting
     * stay. Null when nothing it stores changes.
     */
    fun joined(existing: MediaResource, item: ExchangeListedResource, access: Access): MediaResource? {
        val unbound = existing.exchangeAccount == null
        val offered = if (unbound) {
            access.credentialHosts
        } else {
            access.altPaths.filter { SftpTunnelAddress.isTunnel(it.host) }
        }
        val primary = SftpPathUtils.parseSftpPath(existing.path)?.let { HostPort(it.host, it.port) }
        val updated = existing.copy(
            altAccessPaths = (existing.altAccessPaths + offered).distinct().filterNot { it == primary },
            exchangeAccount = if (unbound) item.sourceKey else existing.exchangeAccount,
            exchangeResourceId = if (unbound) item.record.resourceId else existing.exchangeResourceId,
        )
        return updated.takeIf { it != existing }
    }

    private fun shareTunnel(shareId: String?, entryEndpoint: HostPort?): HostPort? {
        if (shareId == null || entryEndpoint == null) return null
        return HostPort(SftpTunnelAddress.host(shareId, entryEndpoint.host), entryEndpoint.port)
    }

    private const val SCHEME = "sftp://"
    private const val PATH_SEPARATOR = "/"
}
