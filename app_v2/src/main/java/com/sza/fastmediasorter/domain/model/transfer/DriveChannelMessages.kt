package com.sza.fastmediasorter.domain.model.transfer

/**
 * S4231: the request/reply vocabulary of the Drive channel (contract DEVICE-EXCHANGE item CO) and the
 * name rules every packet obeys (item CN).
 */

/** What a consumer asks a producer for. Paths are relative to the resource root, `/`-separated. */
sealed interface DriveChannelRequest {
    val resourceId: String

    data class ListFolder(override val resourceId: String, val path: String) : DriveChannelRequest

    data class Fetch(override val resourceId: String, val paths: List<String>) : DriveChannelRequest

    /** [modifiedAfter] is the `asOf` of the previous reply on the producer's clock; null asks for everything. */
    data class Changes(
        override val resourceId: String,
        val path: String,
        val modifiedAfter: String?
    ) : DriveChannelRequest

    companion object {
        const val MAX_FETCH_PATHS: Int = 100
    }
}

enum class DriveReplyOutcome(val wire: String) {
    OK("ok"),
    REFUSED("refused");

    companion object {
        fun fromWire(value: String): DriveReplyOutcome? = entries.firstOrNull { it.wire == value }
    }
}

enum class DriveRefusalReason(val wire: String) {
    UNAVAILABLE("unavailable"),
    NOT_PERMITTED("not-permitted"),
    PIN_REQUIRED("pin-required"),
    TOO_LARGE("too-large"),
    DRIVE_FULL("drive-full");

    companion object {
        fun fromWire(value: String): DriveRefusalReason? = entries.firstOrNull { it.wire == value }
    }
}

/** The reply members of a `REPLY` manifest; [asOf] is RFC 3339 UTC on the producer's clock. */
data class DriveChannelReplyHeader(
    val outcome: DriveReplyOutcome,
    val reason: DriveRefusalReason? = null,
    val final: Boolean = true,
    val asOf: String? = null
)

enum class ClipKind(val wire: String) {
    TEXT("text"),
    IMAGE("image"),
    FILE("file");

    companion object {
        fun fromWire(value: String): ClipKind? = entries.firstOrNull { it.wire == value }
    }
}

/** One entry of `listing.json`; [modifiedAt] is RFC 3339 UTC on the producer's clock. */
data class DriveListingEntry(
    val name: String,
    val dir: Boolean,
    val size: Long,
    val modifiedAt: String
)

data class DriveListing(val asOf: String, val entries: List<DriveListingEntry>) {
    companion object {
        const val FILE_NAME: String = "listing.json"
    }
}

/** A relative path inside a resource root: no empty, `.` or `..` segment, no backslash or control character. */
object DriveRelativePath {

    /** The segments of [raw], an empty list for the root itself, or null when the path is not acceptable. */
    fun parse(raw: String): List<String>? {
        val trimmed = raw.trim('/')
        if (trimmed.isEmpty()) return emptyList()
        val segments = trimmed.split('/')
        return segments.takeIf { parts -> parts.all(::isSafeSegment) }
    }

    fun join(segments: List<String>): String = segments.joinToString("/")

    private fun isSafeSegment(segment: String): Boolean =
        segment.isNotEmpty() && segment != "." && segment != ".." &&
            segment.none { it == '\\' || it.isISOControl() }
}

/** The name rules of item CN, checked before any disk or Drive operation. */
object DrivePacketNames {

    private const val MAX_PACKET_ID_LENGTH = 64
    private val packetIdPattern = Regex("[A-Za-z0-9_-]+")

    fun isSafeBaseName(name: String): Boolean =
        name.isNotEmpty() &&
            name != "." && name != ".." &&
            name != CrossDevicePacketManifest.MANIFEST_FILE_NAME &&
            name.none { it == '/' || it == '\\' || it.isISOControl() }

    fun isSafePacketId(id: String): Boolean = id.length <= MAX_PACKET_ID_LENGTH && packetIdPattern.matches(id)
}
