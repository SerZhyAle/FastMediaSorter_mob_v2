package com.sza.fastmediasorter.data.ticket

import com.sza.fastmediasorter.data.transfer.DirectoryTreeTransferManager
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.transferProtocolKeyFor
import com.sza.fastmediasorter.domain.model.ticket.TicketPacketCodec
import com.sza.fastmediasorter.domain.model.ticket.TicketPacketManifest
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4573: the ticket inbox as a folder of a resource - typically the computer's `ticket-inbox` folder
 * reached through the exchange server, read on the PC by the same listener as the Drive inbox.
 *
 * The spool layout is the Drive one: the phone writes only `new/<packetId>/`, payload files first and
 * `manifest.json` last, so the listener never sees a packet without its commit marker; `done/` and
 * `failed/` are the PC's and are only read. A retry overwrites what an interrupted attempt left.
 */
@Singleton
class ResourceTicketInboxDataSource @Inject constructor(
    private val resources: ResourceRepository,
    private val transfer: DirectoryTreeTransferManager,
    private val strategies: Map<String, @JvmSuppressWildcards FileOperationStrategy>
) {

    /** Write the packet staged in [folder] into `<resource root>/new/<packetId>/`. */
    suspend fun upload(resourceId: Long, folder: File, manifest: TicketPacketManifest) {
        Timber.d("S4573: folder inbox upload packet=${manifest.packetId} resource=$resourceId")
        val root = rootOf(resourceId)
        val strategy = strategyFor(root)
        val spool = join(root, NEW_DIR)
        val packet = join(spool, manifest.packetId)
        ensureDirectory(strategy, spool)
        ensureDirectory(strategy, packet)
        (manifest.fileNames + TicketPacketManifest.MANIFEST_FILE_NAME).forEach { name ->
            transfer.copyFile(File(folder, name).absolutePath, join(packet, name))
                .getOrElse { throw IOException("cannot write $name: ${it.message}", it) }
        }
    }

    /** The PC's verdict on [packetId] in the resource's `done/` or `failed/` spool. */
    suspend fun readVerdict(resourceId: Long, packetId: String): TicketInboxVerdict {
        val root = rootOf(resourceId)
        val strategy = strategyFor(root)
        val done = sidecar(strategy, root, DONE_DIR, packetId, RESULT_FILE)?.let(TicketPacketCodec::resultFromJson)
        if (done != null) return TicketInboxVerdict.Done(done)
        val failed = sidecar(strategy, root, FAILED_DIR, packetId, REASON_FILE)
            ?.let(TicketPacketCodec::refusalFromJson)
        return if (failed != null) TicketInboxVerdict.Failed(failed) else TicketInboxVerdict.Pending
    }

    // A missing sidecar is the normal answer while the PC has not decided; only a present file counts.
    private suspend fun sidecar(
        strategy: FileOperationStrategy,
        root: String,
        spool: String,
        packetId: String,
        file: String
    ): String? {
        val path = join(join(join(root, spool), packetId), file)
        if (!strategy.exists(path).getOrDefault(false)) return null
        return strategy.readFile(path).getOrElse { throw IOException("cannot read $path: ${it.message}", it) }
    }

    private suspend fun ensureDirectory(strategy: FileOperationStrategy, path: String) {
        if (strategy.exists(path).getOrDefault(false)) return
        strategy.createDirectory(path).getOrElse { throw IOException("cannot create $path: ${it.message}", it) }
    }

    private suspend fun rootOf(resourceId: Long): String {
        val resource = resources.getResourceById(resourceId) ?: throw IOException("inbox folder resource is gone")
        if (resource.isReadOnly) throw IOException("inbox folder ${resource.name} is read-only")
        return resource.path
    }

    private fun strategyFor(path: String): FileOperationStrategy =
        strategies[transferProtocolKeyFor(path)] ?: throw IOException("no file access for $path")

    private fun join(parent: String, child: String): String = "${parent.trimEnd('/')}/$child"

    private companion object {
        const val NEW_DIR = "new"
        const val DONE_DIR = "done"
        const val FAILED_DIR = "failed"
        const val RESULT_FILE = "result.json"
        const val REASON_FILE = "reason.json"
    }
}
