package com.sza.fastmediasorter.domain.usecase.link

/**
 * S0003 — strategic §3.2 safety constraint: only media/document MIME types are
 * allowed through the auto-download channel. `application/octet-stream`,
 * executables, and archives are rejected explicitly.
 */
object MediaMimeWhitelist {

    private val IMAGE_SUBTYPES = setOf(
        "jpeg", "jpg", "png", "webp", "heic", "heif", "gif", "bmp", "avif",
    )
    private val VIDEO_SUBTYPES = setOf(
        "mp4", "webm", "ogg", "x-matroska", "quicktime", "mpeg", "x-msvideo", "3gpp",
    )
    private val AUDIO_SUBTYPES = setOf(
        "mpeg", "mp4", "ogg", "x-flac", "x-wav", "x-aac", "opus", "flac", "wav", "aac",
    )
    private val APPLICATION_WHITELIST = setOf(
        "application/pdf", "application/epub+zip",
    )
    private val TEXT_SUBTYPES = setOf(
        "plain", "markdown",
    )

    private val EXTENSION_TO_MIME = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "webp" to "image/webp", "gif" to "image/gif", "bmp" to "image/bmp",
        "avif" to "image/avif", "heic" to "image/heic", "heif" to "image/heif",
        "mp4" to "video/mp4", "webm" to "video/webm", "mkv" to "video/x-matroska",
        "mov" to "video/quicktime", "mpeg" to "video/mpeg", "avi" to "video/x-msvideo",
        "3gp" to "video/3gpp",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "flac" to "audio/x-flac",
        "wav" to "audio/x-wav", "aac" to "audio/x-aac", "opus" to "audio/opus",
        "pdf" to "application/pdf", "epub" to "application/epub+zip",
        "txt" to "text/plain", "md" to "text/markdown",
    )

    fun isAllowed(mime: String?): Boolean {
        // Explicit reject of generic binary stream — never write executables blindly.
        if (mime == null) return false
        val normalised = mime.substringBefore(';').trim().lowercase()
        if (normalised == "application/octet-stream") return false
        val (type, subtype) = normalised.split('/', limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return false
        }
        return when (type) {
            "image" -> subtype in IMAGE_SUBTYPES
            "video" -> subtype in VIDEO_SUBTYPES
            "audio" -> subtype in AUDIO_SUBTYPES
            "text" -> subtype in TEXT_SUBTYPES
            "application" -> normalised in APPLICATION_WHITELIST
            else -> false
        }
    }

    fun extensionFor(mime: String?): String? {
        if (mime == null) return null
        val normalised = mime.substringBefore(';').trim().lowercase()
        return EXTENSION_TO_MIME.entries.firstOrNull { it.value == normalised }?.key
    }

    fun mimeForExtension(extension: String?): String? {
        if (extension.isNullOrBlank()) return null
        return EXTENSION_TO_MIME[extension.trim('.').lowercase()]
    }
}
