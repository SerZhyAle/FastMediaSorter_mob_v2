package com.sza.fastmediasorter.domain.model.ticket

/**
 * S4554: the phone-ticket transport packet, INBOX-PACKET format version 1 (canon S0045).
 *
 * The envelope is the DEVICE-EXCHANGE Drive packet (`schemaVersion` 2) with `payloadKind` TICKET;
 * the PC reader refuses every member shape outside section 2.1 of the format by name.
 */

/** The five attachment roles of INBOX-PACKET 2.1; [wire] is the manifest spelling. */
enum class TicketAttachmentRole(val wire: String) {
    TEXT("text"),
    VOICE("voice"),
    SCREENSHOT("screenshot"),
    LOG("log"),
    OTHER("other");

    companion object {
        fun fromMimeType(mimeType: String, name: String): TicketAttachmentRole = when {
            mimeType.startsWith("image/") -> SCREENSHOT
            mimeType.startsWith("audio/") -> VOICE
            mimeType.startsWith("text/") || name.endsWith(".log", ignoreCase = true) -> LOG
            else -> OTHER
        }
    }
}

/** One payload file; [sha256] is lower-case hex over the file's bytes. */
data class TicketPacketFile(
    val name: String,
    val size: Long,
    val sha256: String,
    val mimeType: String,
    val role: TicketAttachmentRole?
)

/** `manifest.json` of a ticket packet, the commit marker written after every payload file. */
data class TicketPacketManifest(
    val packetId: String,
    val createdAtEpochMs: Long,
    val senderDeviceName: String,
    val senderDeviceId: String?,
    val targetDeviceName: String?,
    val files: List<TicketPacketFile>,
    val project: String?,
    val projects: List<String> = emptyList(),
    val text: String?,
    val status: String = STATUS_PENDING,
    val ttlSeconds: Long = DEFAULT_TTL_SECONDS
) {
    val fileNames: List<String> get() = files.map { it.name }
    val totalSizeBytes: Long get() = files.sumOf { it.size }

    companion object {
        const val SCHEMA_VERSION: Int = 2
        const val PAYLOAD_KIND: String = "TICKET"
        const val STATUS_PENDING: String = "PENDING"
        const val MANIFEST_FILE_NAME: String = "manifest.json"
        const val TEXT_FILE_NAME: String = "text.md"
        const val DEFAULT_TTL_SECONDS: Long = 604_800L
        const val MAX_FILES: Int = 50
        const val MAX_TOTAL_BYTES: Long = 200L * 1024L * 1024L

        private val packetIdGrammar = Regex("^[A-Za-z0-9_-]{1,64}$")
        private val projectKeyGrammar = Regex("^[a-z][a-z0-9-]*$")

        fun isValidPacketId(id: String): Boolean = packetIdGrammar.matches(id)

        fun isValidProjectKey(key: String): Boolean = projectKeyGrammar.matches(key)

        /** The base-name rule of INBOX-PACKET 2.1; the reader refuses anything else as `FileNameUnsafe`. */
        fun isSafeFileName(name: String): Boolean =
            name.isNotEmpty() && name != "." && name != ".." &&
                !name.equals(MANIFEST_FILE_NAME, ignoreCase = true) &&
                name.none { it == '/' || it == '\\' || it.isISOControl() || it in WINDOWS_RESERVED }

        // Drive for desktop mirrors the folder onto NTFS, where these characters cannot exist in a name.
        private const val WINDOWS_RESERVED = ":*?\"<>|"
    }
}

/** One entry of `projects.json`, the picker list the PC publishes into the inbox root. */
data class TicketProject(val key: String, val name: String)

/** `result.json` of a packet the PC moved to `done/`. */
data class TicketPacketResult(val tickets: List<String>, val verdict: String?)

/** `reason.json` of a packet the PC moved to `failed/`; [error] is a reason name of INBOX-PACKET 4. */
data class TicketPacketRefusal(val error: String, val detail: String?)

/** Where a locally queued packet stands. */
enum class TicketOutboxState {
    QUEUED,
    WAITING_ON_DRIVE,
    SENT,
    PROCESSED,
    FAILED
}

/** One packet of the phone outbox as the composer lists it. */
data class TicketOutboxEntry(
    val packetId: String,
    val createdAtEpochMs: Long,
    val title: String,
    val project: String?,
    val state: TicketOutboxState,
    val tickets: List<String> = emptyList(),
    val failure: String? = null
)
