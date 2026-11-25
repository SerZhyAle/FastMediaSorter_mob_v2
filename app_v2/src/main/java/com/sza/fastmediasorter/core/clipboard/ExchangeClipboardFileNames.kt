package com.sza.fastmediasorter.core.clipboard

import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Names a received clipboard value safely (contract DEVICE-EXCHANGE 22 item CB): the base name of the sender's
 * `name` only, every character a file system refuses replaced, and never a name already taken. A value without a
 * usable name is named as item EL (1.11) says.
 */
object ExchangeClipboardFileNames {

    private const val FORBIDDEN = "<>:\"/\\|?*"
    private const val REPLACEMENT = '_'
    private const val MAX_NAME_CHARS = 120
    private const val GENERATED_PREFIX = "clipboard-"
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    private const val TEXT_EXTENSION = "txt"
    private const val FIRST_SUFFIX = 2

    private val KNOWN_EXTENSIONS = mapOf(
        "image/png" to "png",
        "image/jpeg" to "jpg",
        "image/gif" to "gif",
        "image/webp" to "webp",
        "image/heic" to "heic",
        "image/bmp" to "bmp",
        "image/svg+xml" to "svg",
        "text/plain" to TEXT_EXTENSION,
        "text/html" to "html",
        "application/pdf" to "pdf",
        "application/zip" to "zip",
        "application/octet-stream" to "bin",
    )

    /**
     * The name a value is saved under. A text value always gets a made-up `.txt` name; an image without a usable
     * name gets one made from [storedAt] and [mimeType]. [now] stands in for an absent or unreadable [storedAt].
     */
    fun safeName(
        name: String?,
        kind: ExchangeClipboardKind,
        mimeType: String?,
        storedAt: String?,
        now: Instant = Instant.now(),
    ): String {
        val base = name?.substringAfterLast('/')?.substringAfterLast('\\')?.let(::clean)
            ?.takeIf { it.isNotEmpty() && it != "." && it != ".." }
        return base?.takeIf { kind != ExchangeClipboardKind.TEXT } ?: generated(kind, mimeType, storedAt, now)
    }

    /** [name], or the first `name (N).ext` that [existing] does not hold. */
    fun uniqueIn(existing: Set<String>, name: String): String {
        if (name !in existing) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val stem = dot?.let { name.substring(0, it) } ?: name
        val extension = dot?.let { name.substring(it) }.orEmpty()
        return generateSequence(FIRST_SUFFIX) { it + 1 }
            .map { "$stem ($it)$extension" }
            .first { it !in existing }
    }

    private fun clean(raw: String): String =
        raw.map { if (it.isISOControl() || it in FORBIDDEN) REPLACEMENT else it }
            .joinToString("")
            .trim()
            .take(MAX_NAME_CHARS)

    // Item EL: `clipboard-<yyyyMMdd-HHmmss><ext>` in UTC; the name is a hint to the person, never parsed back.
    private fun generated(kind: ExchangeClipboardKind, mimeType: String?, storedAt: String?, now: Instant): String {
        val stamp = STAMP.format(storedAt?.let(::parseStoredAt) ?: now)
        val extension = extensionOf(kind, mimeType)?.let { ".$it" }.orEmpty()
        return "$GENERATED_PREFIX$stamp$extension"
    }

    // RFC 3339 with `Z` or a numeric offset; a value that is not one falls back to the receiver's time.
    private fun parseStoredAt(storedAt: String): Instant? = try {
        OffsetDateTime.parse(storedAt).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }

    // An unknown type gets no extension (item EL): a subtype is not a usual extension.
    private fun extensionOf(kind: ExchangeClipboardKind, mimeType: String?): String? =
        if (kind == ExchangeClipboardKind.TEXT) TEXT_EXTENSION else KNOWN_EXTENSIONS[mimeType?.lowercase()]
}
