package com.sza.fastmediasorter.data.transfer.drive

import android.os.Build
import androidx.annotation.RequiresApi
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.remote.sftp.server.SafPathResolver
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerNode
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketCodec
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketExpiry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.DriveChannelReplyHeader
import com.sza.fastmediasorter.domain.model.transfer.DriveChannelRequest
import com.sza.fastmediasorter.domain.model.transfer.DriveListing
import com.sza.fastmediasorter.domain.model.transfer.DriveListingEntry
import com.sza.fastmediasorter.domain.model.transfer.DrivePacketNames
import com.sza.fastmediasorter.domain.model.transfer.DriveQueueFull
import com.sza.fastmediasorter.domain.model.transfer.DriveRefusalReason
import com.sza.fastmediasorter.domain.model.transfer.DriveRelativePath
import com.sza.fastmediasorter.domain.model.transfer.DriveReplyOutcome
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentity
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.usecase.transfer.ReceiveCrossDevicePacketUseCase
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.nio.channels.Channels
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4231: the producer half of contract DEVICE-EXCHANGE item CO - answers a `REQUEST` addressed to this
 * device with `REPLY` packets for the one resource it published, inside the same roots and namespace the
 * live SFTP server serves, and deletes the request after its last reply (item CR).
 */
@Singleton
class DriveChannelResponder @Inject constructor(
    private val repository: CrossDeviceTransferRepository,
    private val settings: DriveChannelSettings,
    private val servedResource: DriveChannelServedResource,
) {

    // A request is answered once even though it stays visible until its folder is gone from the listing.
    private val handled = ConcurrentHashMap<String, Long>()

    /** Answer [entry] when it is a `REQUEST` targeted at [self]; anything else is ignored. */
    suspend fun handle(entry: CrossDevicePacketEntry, self: LocalDeviceIdentity, serverNowMs: Long) {
        handled.entries.removeIf { serverNowMs - it.value > HANDLED_MEMORY_MS }
        val manifest = entry.manifest
        val isOurs = manifest.payloadKind == CrossDevicePayloadKind.REQUEST &&
            ReceiveCrossDevicePacketUseCase.isTargetedAt(manifest, self)
        if (isOurs && handled.putIfAbsent(entry.packetId, serverNowMs) == null) {
            Timber.d("S4231: answer a Drive channel request")
            runCatching { answer(manifest, self) }
                .onFailure { it.rethrowIfCancellation() }
                .onSuccess { repository.deletePacket(entry) }
                .onFailure { error ->
                    // Not answered: forget it so a later poll tries again within the request's lifetime.
                    handled.remove(entry.packetId)
                    Timber.w("Drive channel: request %s not answered (%s)", entry.packetId, error.javaClass.simpleName)
                }
        }
    }

    private suspend fun answer(request: CrossDevicePacketManifest, self: LocalDeviceIdentity) {
        val body = request.request
        val served = when {
            body == null -> ServedResource.Unavailable
            !settings.enabled.first() -> ServedResource.Unavailable
            else -> servedResource.resolve(body.resourceId, self.deviceId)
        }
        val parts = when {
            body != null && served is ServedResource.Served && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                build(body, served.roots)
            served is ServedResource.PinRequired -> listOf(refused(DriveRefusalReason.PIN_REQUIRED))
            else -> listOf(refused(DriveRefusalReason.UNAVAILABLE))
        }
        publish(request, self, parts)
    }

    private suspend fun publish(request: CrossDevicePacketManifest, self: LocalDeviceIdentity, parts: List<ReplyPart>) {
        var full = false
        parts.forEachIndexed { index, part ->
            if (!full) {
                val header = part.header.copy(final = index == parts.lastIndex)
                val sent = repository.publishPacket(replyManifest(request, self, header), part.files)
                full = sent.exceptionOrNull() is DriveQueueFull
                if (!full) sent.getOrThrow()
            }
        }
        if (full) {
            val header = refused(DriveRefusalReason.DRIVE_FULL).header
            repository.publishPacket(replyManifest(request, self, header), emptyList()).getOrThrow()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun build(request: DriveChannelRequest, roots: List<SftpServerNode>): List<ReplyPart> {
        val resolver = SafPathResolver(roots)
        val asOf = Instant.now().toString()
        return try {
            when (request) {
                is DriveChannelRequest.ListFolder -> listOf(listing(resolver, request.path, asOf))
                is DriveChannelRequest.Fetch -> fetch(resolver, request.paths, asOf)
                is DriveChannelRequest.Changes -> changes(resolver, request, asOf)
            }
        } catch (_: AccessDeniedException) {
            listOf(refused(DriveRefusalReason.NOT_PERMITTED))
        } catch (_: NoSuchFileException) {
            listOf(refused(DriveRefusalReason.UNAVAILABLE))
        } catch (refusal: Refusal) {
            listOf(refused(refusal.reason))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun listing(resolver: SafPathResolver, path: String, asOf: String): ReplyPart {
        val children = when (val target = resolver.resolve(sftpPath(path))) {
            SafPathResolver.Target.VirtualRoot -> resolver.mounts.map { mount -> mount.name to mount.node }
            is SafPathResolver.Target.Existing -> folderChildren(target.node)
            is SafPathResolver.Target.Absent -> throw Refusal(DriveRefusalReason.UNAVAILABLE)
        }
        val entries = children
            .filter { (name, _) -> DrivePacketNames.isSafeBaseName(name) }
            .map { (name, node) ->
                DriveListingEntry(
                    name = name,
                    dir = node.isDirectory,
                    size = if (node.isDirectory) 0L else node.length,
                    modifiedAt = Instant.ofEpochMilli(node.lastModified).toString()
                )
            }
        val bytes = CrossDevicePacketCodec.listingToJson(DriveListing(asOf, entries)).toByteArray()
        val file = OutgoingPacketFile(
            name = DriveListing.FILE_NAME,
            size = bytes.size.toLong(),
            mimeType = LISTING_MIME,
            open = bytes::inputStream
        )
        return ReplyPart(ok(asOf), listOf(file))
    }

    private fun folderChildren(node: SftpServerNode): List<Pair<String, SftpServerNode>> {
        if (!node.isDirectory) throw Refusal(DriveRefusalReason.UNAVAILABLE)
        return node.listChildren().map { it.name to it }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun fetch(resolver: SafPathResolver, paths: List<String>, asOf: String): List<ReplyPart> {
        if (paths.isEmpty() || paths.size > DriveChannelRequest.MAX_FETCH_PATHS) {
            throw Refusal(DriveRefusalReason.NOT_PERMITTED)
        }
        val files = paths.map { path ->
            val target = resolver.resolve(sftpPath(path)) as? SafPathResolver.Target.Existing
            target?.node?.takeUnless { it.isDirectory } ?: throw Refusal(DriveRefusalReason.UNAVAILABLE)
        }
        return pack(files, asOf)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun changes(
        resolver: SafPathResolver,
        request: DriveChannelRequest.Changes,
        asOf: String
    ): List<ReplyPart> {
        val after = request.modifiedAfter?.let(::epochMsOrRefuse) ?: Long.MIN_VALUE
        val start = when (val target = resolver.resolve(sftpPath(request.path))) {
            SafPathResolver.Target.VirtualRoot -> resolver.mounts.map { it.node }
            is SafPathResolver.Target.Existing -> listOf(target.node)
            is SafPathResolver.Target.Absent -> throw Refusal(DriveRefusalReason.UNAVAILABLE)
        }
        val changed = start.flatMap(::walkFiles).filter { it.lastModified > after }
        return pack(changed, asOf).ifEmpty { listOf(ReplyPart(ok(asOf), emptyList())) }
    }

    // Splits files into replies of at most packetBytes, each with unique base names (item CN).
    private suspend fun pack(nodes: List<SftpServerNode>, asOf: String): List<ReplyPart> {
        val limit = settings.packetBytes()
        if (nodes.any { it.length > limit }) throw Refusal(DriveRefusalReason.TOO_LARGE)
        val parts = mutableListOf<MutableList<SftpServerNode>>()
        nodes.filter { DrivePacketNames.isSafeBaseName(it.name) }.forEach { node ->
            val current = parts.lastOrNull()
            val fits = current != null &&
                current.sumOf { it.length } + node.length <= limit &&
                current.none { it.name == node.name }
            if (fits) current?.add(node) else parts += mutableListOf(node)
        }
        return parts.map { group -> ReplyPart(ok(asOf), group.map(::outgoing)) }
    }

    private fun outgoing(node: SftpServerNode): OutgoingPacketFile =
        OutgoingPacketFile(node.name, node.length) {
            Channels.newInputStream(node.openChannel(SftpServerNode.ChannelMode.READ))
        }

    private fun walkFiles(node: SftpServerNode): List<SftpServerNode> =
        if (node.isDirectory) node.listChildren().flatMap(::walkFiles) else listOf(node)

    private fun replyManifest(
        request: CrossDevicePacketManifest,
        self: LocalDeviceIdentity,
        header: DriveChannelReplyHeader
    ) = CrossDevicePacketManifest(
        packetId = UUID.randomUUID().toString().replace("-", ""),
        createdAtEpochMs = System.currentTimeMillis(),
        senderDeviceName = self.displayName,
        targetDeviceName = request.senderDeviceName,
        payloadKind = CrossDevicePayloadKind.REPLY,
        fileNames = emptyList(),
        totalSizeBytes = 0L,
        schemaVersion = CrossDevicePacketManifest.SCHEMA_V2,
        senderDeviceId = self.deviceId,
        targetDeviceId = request.senderDeviceId,
        inReplyTo = request.packetId,
        ttlSeconds = CrossDevicePacketExpiry.REPLY_TTL_SECONDS,
        reply = header
    )

    private data class ReplyPart(val header: DriveChannelReplyHeader, val files: List<OutgoingPacketFile>)

    private class Refusal(val reason: DriveRefusalReason) : Exception(reason.wire)

    private fun ok(asOf: String) = DriveChannelReplyHeader(DriveReplyOutcome.OK, asOf = asOf)

    private fun refused(reason: DriveRefusalReason) =
        ReplyPart(DriveChannelReplyHeader(DriveReplyOutcome.REFUSED, reason = reason), emptyList())

    private fun sftpPath(relative: String): String {
        val segments = DriveRelativePath.parse(relative) ?: throw Refusal(DriveRefusalReason.NOT_PERMITTED)
        return "/" + DriveRelativePath.join(segments)
    }

    private fun epochMsOrRefuse(rfc3339: String): Long =
        runCatching { Instant.parse(rfc3339).toEpochMilli() }
            .getOrElse { throw Refusal(DriveRefusalReason.NOT_PERMITTED) }

    private companion object {
        const val LISTING_MIME = "application/json"
        val HANDLED_MEMORY_MS: Long = TimeUnit.SECONDS.toMillis(CrossDevicePacketExpiry.REQUEST_TTL_SECONDS)
    }
}
