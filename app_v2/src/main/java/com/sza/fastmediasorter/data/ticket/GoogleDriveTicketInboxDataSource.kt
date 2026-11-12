package com.sza.fastmediasorter.data.ticket

import com.sza.fastmediasorter.data.cloud.CloudFile
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.domain.model.ticket.TicketPacketCodec
import com.sza.fastmediasorter.domain.model.ticket.TicketPacketManifest
import com.sza.fastmediasorter.domain.model.ticket.TicketPacketRefusal
import com.sza.fastmediasorter.domain.model.ticket.TicketPacketResult
import com.sza.fastmediasorter.domain.model.ticket.TicketProject
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** S4554: what the PC decided about one sent packet, or [Pending] while it sits in `new/`/`processing/`. */
sealed interface TicketInboxVerdict {
    data object Pending : TicketInboxVerdict
    data class Done(val result: TicketPacketResult) : TicketInboxVerdict
    data class Failed(val refusal: TicketPacketRefusal) : TicketInboxVerdict
}

/**
 * S4554: the SZA ticket inbox in the owner's visible Drive, `SZA/ticket-inbox/` from the Drive root
 * (`C:\GD\SZA\ticket-inbox` on the PC, where `C:\GD` mirrors My Drive).
 *
 * The phone writes only `new/<packetId>/`; `done/` and `failed/` are the PC's and are only read. Drive
 * allows two files of one name in a folder and Drive for desktop would mirror the second as
 * `name (1).ext`, which the reader refuses as `UnexpectedFile` - so every upload first deletes what a
 * previous, interrupted attempt left under the same name.
 */
@Singleton
class GoogleDriveTicketInboxDataSource @Inject constructor(
    private val drive: GoogleDriveRestClient
) {

    suspend fun ensureSignedIn(): Boolean = drive.isAuthenticated() || drive.tryRestoreFromStorage()

    /** Upload the packet in [folder] into `new/<packetId>/`: payload files first, manifest last. */
    suspend fun upload(folder: File, manifest: TicketPacketManifest) {
        val inbox = folderId(drive.ensureFolderExists(INBOX_DIR, folderId(drive.ensureFolderExists(ROOT_DIR))))
        val spool = folderId(drive.ensureFolderExists(NEW_DIR, inbox))
        val packet = folderId(drive.ensureFolderExists(manifest.packetId, spool))
        val existing = children(packet)
        val names = manifest.fileNames + TicketPacketManifest.MANIFEST_FILE_NAME
        names.forEach { name ->
            existing.filter { it.name == name }.forEach { drive.deleteFile(it.id) }
            uploadOne(File(folder, name), packet, mimeTypeOf(manifest, name))
        }
    }

    /** `projects.json` of the inbox root, null when the inbox or the file is absent or unreadable. */
    suspend fun readProjects(): List<TicketProject>? {
        val inbox = findInbox() ?: return null
        val bytes = (drive.downloadByName(PROJECTS_FILE, inbox) as? CloudResult.Success)?.data
        return bytes?.let { TicketPacketCodec.projectsFromJson(it.decodeToString()) }
    }

    /** The PC's verdict on [packetId]: `done/<id>/result.json`, `failed/<id>/reason.json`, or pending. */
    suspend fun readVerdict(packetId: String): TicketInboxVerdict {
        val inbox = findInbox() ?: throw IOException("ticket inbox not found on Drive")
        val done = sidecar(inbox, DONE_DIR, packetId, RESULT_FILE)?.let(TicketPacketCodec::resultFromJson)
        val failed = if (done == null) {
            sidecar(inbox, FAILED_DIR, packetId, REASON_FILE)?.let(TicketPacketCodec::refusalFromJson)
        } else {
            null
        }
        return when {
            done != null -> TicketInboxVerdict.Done(done)
            failed != null -> TicketInboxVerdict.Failed(failed)
            else -> TicketInboxVerdict.Pending
        }
    }

    private suspend fun sidecar(inbox: String, spool: String, packetId: String, file: String): String? {
        val spoolId = (drive.findFolderByName(spool, inbox) as? CloudResult.Success)?.data?.id
        val packetFolder = spoolId?.let { (drive.findFolderByName(packetId, it) as? CloudResult.Success)?.data?.id }
        val bytes = packetFolder?.let { (drive.downloadByName(file, it) as? CloudResult.Success)?.data }
        return bytes?.decodeToString()
    }

    private suspend fun findInbox(): String? {
        val root = (drive.findFolderByName(ROOT_DIR) as? CloudResult.Success)?.data?.id
        return root?.let { (drive.findFolderByName(INBOX_DIR, it) as? CloudResult.Success)?.data?.id }
    }

    private suspend fun children(folderId: String): List<CloudFile> {
        val all = mutableListOf<CloudFile>()
        var pageToken: String? = null
        do {
            val page = when (val listed = drive.listFiles(folderId, pageToken)) {
                is CloudResult.Success -> listed.data
                is CloudResult.Error -> throw IOException(listed.message, listed.cause)
            }
            all += page.first
            pageToken = page.second
        } while (pageToken != null)
        return all
    }

    private suspend fun uploadOne(file: File, parentId: String, mimeType: String) {
        val uploaded = file.inputStream().use { input ->
            drive.uploadFile(input, file.name, mimeType, parentId, file.length(), null)
        }
        if (uploaded is CloudResult.Error) throw IOException(uploaded.message, uploaded.cause)
    }

    private fun mimeTypeOf(manifest: TicketPacketManifest, name: String): String =
        manifest.files.firstOrNull { it.name == name }?.mimeType ?: MANIFEST_MIME

    private fun folderId(result: CloudResult<CloudFile>): String = when (result) {
        is CloudResult.Success -> result.data.id
        is CloudResult.Error -> throw IOException(result.message, result.cause)
    }

    companion object {
        const val ROOT_DIR = "SZA"
        const val INBOX_DIR = "ticket-inbox"
        private const val NEW_DIR = "new"
        private const val DONE_DIR = "done"
        private const val FAILED_DIR = "failed"
        private const val PROJECTS_FILE = "projects.json"
        private const val RESULT_FILE = "result.json"
        private const val REASON_FILE = "reason.json"
        private const val MANIFEST_MIME = "application/json"
    }
}
