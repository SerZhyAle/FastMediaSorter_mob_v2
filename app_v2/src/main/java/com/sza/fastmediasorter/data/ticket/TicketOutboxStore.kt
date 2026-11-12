package com.sza.fastmediasorter.data.ticket

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.domain.model.ticket.TicketAttachmentRole
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxEntry
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxState
import com.sza.fastmediasorter.domain.model.ticket.TicketPacketCodec
import com.sza.fastmediasorter.domain.model.ticket.TicketPacketFile
import com.sza.fastmediasorter.domain.model.ticket.TicketPacketManifest
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
import com.sza.fastmediasorter.domain.ticket.TicketDraft
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** S4554: one staged packet - its folder, its manifest and where it stands. */
data class StagedTicketPacket(val folder: File, val manifest: TicketPacketManifest, val entry: TicketOutboxEntry)

/**
 * S4554: the phone outbox, `filesDir/ticket_outbox/<packetId>/`.
 *
 * A packet folder holds exactly the payload files and `manifest.json` that will be uploaded, plus
 * `state.json`, which never leaves the phone. The manifest is written last, as on Drive, so a folder
 * without one is a staging that died midway and is removed rather than sent half-built.
 */
@Singleton
class TicketOutboxStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val identity: LocalDeviceIdentitySource
) {

    private val root: File get() = File(context.filesDir, ROOT_DIR)

    /** Copy [draft] into a new packet folder; every attachment is streamed through SHA-256. */
    suspend fun stage(draft: TicketDraft): StagedTicketPacket {
        val packetId = UUID.randomUUID().toString().replace("-", "")
        val folder = File(root, packetId).apply { mkdirs() }
        try {
            val files = mutableListOf<TicketPacketFile>()
            val text = draft.text.trim()
            if (text.isNotEmpty()) {
                files += writeEntry(folder, TicketPacketManifest.TEXT_FILE_NAME, TEXT_MIME, TicketAttachmentRole.TEXT) {
                    "$text\n".byteInputStream(Charsets.UTF_8)
                }
            }
            draft.attachments.forEach { attachment ->
                val name = uniqueName(safeName(attachment.displayName), files.map { it.name })
                files += writeEntry(folder, name, attachment.mimeType, attachment.role) {
                    context.contentResolver.openInputStream(Uri.parse(attachment.uri))
                        ?: throw IOException("cannot open ${attachment.displayName}")
                }
            }
            val sender = identity.current()
            val manifest = TicketPacketManifest(
                packetId = packetId,
                createdAtEpochMs = System.currentTimeMillis(),
                senderDeviceName = sender.displayName,
                senderDeviceId = sender.deviceId,
                targetDeviceName = null,
                files = files,
                project = draft.project,
                text = text.ifEmpty { null }
            )
            val entry = TicketOutboxEntry(
                packetId = packetId,
                createdAtEpochMs = manifest.createdAtEpochMs,
                title = titleOf(text, files),
                project = draft.project,
                state = TicketOutboxState.QUEUED
            )
            writeState(folder, entry, files)
            File(folder, TicketPacketManifest.MANIFEST_FILE_NAME)
                .writeText(TicketPacketCodec.manifestToJson(manifest), Charsets.UTF_8)
            return StagedTicketPacket(folder, manifest, entry)
        } catch (error: IOException) {
            folder.deleteRecursively()
            throw error
        }
    }

    /** Every complete packet, newest first; a folder without a manifest is removed. */
    fun list(): List<StagedTicketPacket> {
        val folders = root.listFiles { file -> file.isDirectory }.orEmpty()
        return folders.mapNotNull(::read).sortedByDescending { it.entry.createdAtEpochMs }
    }

    fun update(packet: StagedTicketPacket, entry: TicketOutboxEntry) = writeState(packet.folder, entry)

    fun delete(packetId: String) {
        if (TicketPacketManifest.isValidPacketId(packetId)) File(root, packetId).deleteRecursively()
    }

    private fun read(folder: File): StagedTicketPacket? {
        if (!File(folder, TicketPacketManifest.MANIFEST_FILE_NAME).isFile) {
            folder.deleteRecursively()
            return null
        }
        return runCatching {
            val state = JSONObject(File(folder, STATE_FILE).readText())
            StagedTicketPacket(folder, manifestOf(folder, state), entryOf(folder.name, state))
        }.onFailure { Timber.w(it, "Ticket outbox state unreadable: ${folder.name}") }.getOrNull()
    }

    // The manifest bytes on disk are what gets uploaded; this model only drives the upload order.
    private fun manifestOf(folder: File, state: JSONObject): TicketPacketManifest {
        val names = state.getJSONArray(KEY_FILES)
        val files = (0 until names.length()).map { index ->
            val file = names.getJSONObject(index)
            TicketPacketFile(
                name = file.getString("name"),
                size = file.getLong("size"),
                sha256 = file.getString("sha256"),
                mimeType = file.getString("mimeType"),
                role = null
            )
        }
        return TicketPacketManifest(
            packetId = folder.name,
            createdAtEpochMs = state.optLong(KEY_CREATED),
            senderDeviceName = "",
            senderDeviceId = null,
            targetDeviceName = null,
            files = files,
            project = state.optString(KEY_PROJECT).ifEmpty { null },
            text = null
        )
    }

    private fun entryOf(packetId: String, state: JSONObject): TicketOutboxEntry {
        val tickets = state.optJSONArray(KEY_TICKETS) ?: JSONArray()
        return TicketOutboxEntry(
            packetId = packetId,
            createdAtEpochMs = state.optLong(KEY_CREATED),
            title = state.optString(KEY_TITLE),
            project = state.optString(KEY_PROJECT).ifEmpty { null },
            state = runCatching { TicketOutboxState.valueOf(state.optString(KEY_STATE)) }
                .getOrDefault(TicketOutboxState.QUEUED),
            tickets = (0 until tickets.length()).map(tickets::getString),
            failure = state.optString(KEY_FAILURE).ifEmpty { null }
        )
    }

    private fun writeState(folder: File, entry: TicketOutboxEntry, files: List<TicketPacketFile>? = null) {
        val previous = runCatching { JSONObject(File(folder, STATE_FILE).readText()) }.getOrNull()
        val json = JSONObject()
            .put(KEY_CREATED, entry.createdAtEpochMs)
            .put(KEY_TITLE, entry.title)
            .put(KEY_PROJECT, entry.project ?: "")
            .put(KEY_STATE, entry.state.name)
            .put(KEY_TICKETS, JSONArray(entry.tickets))
            .put(KEY_FAILURE, entry.failure ?: "")
        val fileArray = files?.let { list -> JSONArray(list.map(::fileJson)) }
            ?: previous?.optJSONArray(KEY_FILES)
            ?: JSONArray()
        json.put(KEY_FILES, fileArray)
        val temp = File(folder, "$STATE_FILE.tmp")
        temp.writeText(json.toString())
        val target = File(folder, STATE_FILE)
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) throw IOException("cannot write ticket outbox state")
    }

    private fun fileJson(file: TicketPacketFile): JSONObject = JSONObject()
        .put("name", file.name)
        .put("size", file.size)
        .put("sha256", file.sha256)
        .put("mimeType", file.mimeType)

    private fun writeEntry(
        folder: File,
        name: String,
        mimeType: String,
        role: TicketAttachmentRole,
        open: () -> InputStream
    ): TicketPacketFile {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        val target = File(folder, name)
        open().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    size += read
                }
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        return TicketPacketFile(name, size, sha, mimeType.ifBlank { DEFAULT_MIME }, role)
    }

    private fun safeName(displayName: String): String {
        val cleaned = displayName.map { ch ->
            if (ch.isISOControl() || ch in UNSAFE_CHARS) '_' else ch
        }.joinToString("").trim().take(MAX_NAME_CHARS)
        return cleaned.takeIf(TicketPacketManifest::isSafeFileName) ?: "attachment"
    }

    private fun uniqueName(name: String, taken: List<String>): String {
        if (name !in taken && name != TicketPacketManifest.TEXT_FILE_NAME) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        return generateSequence(2) { it + 1 }.map { "$base-$it$ext" }.first { it !in taken }
    }

    private fun titleOf(text: String, files: List<TicketPacketFile>): String =
        text.lineSequence().firstOrNull { it.isNotBlank() }?.take(MAX_TITLE_CHARS)
            ?: files.firstOrNull()?.name.orEmpty()

    companion object {
        const val ROOT_DIR = "ticket_outbox"
        private const val STATE_FILE = "state.json"
        private const val KEY_CREATED = "createdAtEpochMs"
        private const val KEY_TITLE = "title"
        private const val KEY_PROJECT = "project"
        private const val KEY_STATE = "state"
        private const val KEY_TICKETS = "tickets"
        private const val KEY_FAILURE = "failure"
        private const val KEY_FILES = "files"
        private const val DEFAULT_MIME = "application/octet-stream"
        private const val TEXT_MIME = "text/markdown"
        private const val COPY_BUFFER_BYTES = 65_536
        private const val MAX_NAME_CHARS = 120
        private const val MAX_TITLE_CHARS = 80
        private const val UNSAFE_CHARS = "/\\:*?\"<>|"
    }
}
