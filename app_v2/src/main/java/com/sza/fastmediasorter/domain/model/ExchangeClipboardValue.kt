package com.sza.fastmediasorter.domain.model

import java.time.Instant

/** The three kinds one clipboard value can be (contract DEVICE-EXCHANGE 22 item BU). */
enum class ExchangeClipboardKind(val wire: String) {
    TEXT("text"),
    IMAGE("image"),
    FILE("file"),
    ;

    companion object {
        fun fromWire(wire: String?): ExchangeClipboardKind? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * What a `clipboard-put` declares before any byte flows (22 item BW). A value that breaks these rules would be an
 * invalid envelope that closes the stream, so it cannot be built at all.
 */
data class ExchangeClipboardMeta(
    val kind: ExchangeClipboardKind,
    val size: Long,
    val mimeType: String?,
    val name: String?,
) {

    init {
        require(size >= 1) { "a clipboard value holds at least one byte" }
        when (kind) {
            ExchangeClipboardKind.TEXT -> require(mimeType == null && name == null) { "text carries no type or name" }
            ExchangeClipboardKind.IMAGE -> require(mimeType != null) { "an image carries its type" }
            ExchangeClipboardKind.FILE -> require(mimeType != null && name != null) { "a file carries type and name" }
        }
        mimeType?.let { require(isValidMimeType(it)) { "the type breaks the type/subtype form" } }
        name?.let { require(isValidName(it)) { "the name breaks the contract rule" } }
    }

    // A name is private: it may describe the content (22 item CC).
    override fun toString(): String = "ExchangeClipboardMeta(kind=$kind, size=$size, mimeType=$mimeType)"

    companion object {
        private const val MAX_NAME_BYTES = 255
        private const val MAX_MIME_BYTES = 127
        private val MIME_FORM = Regex("[^/\\s]+/[^/\\s]+")

        fun isValidName(name: String): Boolean =
            name.isNotEmpty() &&
                name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES &&
                name != "." && name != ".." &&
                name.none { it == '/' || it == '\\' || it.isISOControl() }

        fun isValidMimeType(mimeType: String): Boolean =
            mimeType.toByteArray(Charsets.UTF_8).size <= MAX_MIME_BYTES && MIME_FORM.matches(mimeType)
    }
}

/** One value of an account's clipboard on the server, with the members the server set (22 items BU and BY). */
data class ExchangeClipboardValue(
    val clipId: String,
    val kind: ExchangeClipboardKind,
    val size: Long,
    val sha256: String,
    val storedAt: String,
    val sourceDeviceId: String?,
    val sourceDeviceName: String?,
    val mimeType: String?,
    val name: String?,
) {

    /** The server's clock as an instant, or null when it is not ISO-8601; never throws. */
    fun storedAtInstant(): Instant? =
        runCatching { Instant.parse(storedAt) }.getOrNull()

    // 22 item CC: no name, sha256 or clipId in a log line.
    override fun toString(): String =
        "ExchangeClipboardValue(kind=$kind, size=$size, storedAt=$storedAt, mimeType=$mimeType)"
}
