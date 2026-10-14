package com.sza.fastmediasorter.core.clipboard

import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind

/**
 * Names a received clipboard value safely (contract DEVICE-EXCHANGE 22 item CB): the base name of the sender's
 * `name` only, every character a file system refuses replaced, and never a name already taken.
 */
object ExchangeClipboardFileNames {

    private const val FORBIDDEN = "<>:\"/\\|?*"
    private const val REPLACEMENT = '_'
    private const val MAX_NAME_CHARS = 120
    private const val GENERATED_PREFIX = "clipboard-"
    private const val FALLBACK_STAMP = "value"
    private const val FALLBACK_EXTENSION = "bin"
    private const val FIRST_SUFFIX = 2
    private val KNOWN_EXTENSIONS = mapOf(
        "image/png" to "png",
        "image/jpeg" to "jpg",
        "image/gif" to "gif",
        "image/webp" to "webp",
        "image/heic" to "heic",
        "image/bmp" to "bmp",
        "text/plain" to "txt",
        "application/pdf" to "pdf",
        "application/octet-stream" to FALLBACK_EXTENSION,
    )

    /**
     * The name a value is saved under. A text value always gets a made-up `.txt` name; an image without a usable
     * name gets one made from [storedAt] and [mimeType].
     */
    fun safeName(name: String?, kind: ExchangeClipboardKind, mimeType: String?, storedAt: String): String {
        val base = name?.substringAfterLast('/')?.substringAfterLast('\\')?.let(::clean)
            ?.takeIf { it.isNotEmpty() && it != "." && it != ".." }
        return base?.takeIf { kind != ExchangeClipboardKind.TEXT } ?: generated(kind, mimeType, storedAt)
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

    private fun generated(kind: ExchangeClipboardKind, mimeType: String?, storedAt: String): String {
        val stamp = storedAt.filter(Char::isDigit).ifEmpty { FALLBACK_STAMP }
        return "$GENERATED_PREFIX$stamp.${extensionOf(kind, mimeType)}"
    }

    private fun extensionOf(kind: ExchangeClipboardKind, mimeType: String?): String = when {
        kind == ExchangeClipboardKind.TEXT -> "txt"
        mimeType != null && mimeType in KNOWN_EXTENSIONS -> KNOWN_EXTENSIONS.getValue(mimeType)
        else -> mimeType?.substringAfter('/')?.filter(Char::isLetterOrDigit)?.lowercase()
            ?.takeIf(String::isNotEmpty) ?: FALLBACK_EXTENSION
    }
}
