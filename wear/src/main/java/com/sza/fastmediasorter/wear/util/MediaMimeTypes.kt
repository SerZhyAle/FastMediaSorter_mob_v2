package com.sza.fastmediasorter.wear.util

import android.webkit.MimeTypeMap
import timber.log.Timber

/**
 * Extension-to-MIME inference for the whole `wear` module.
 *
 * On a network source the file name is the only source of truth - no protocol the watch speaks
 * reports a MIME type - so every listing derives it here. S1690: this used to be written three
 * times in two different versions, and the two that lacked the fallback table dropped .mkv, .flac,
 * .heic and .wma from FTP and SFTP listings entirely, because an unresolved type is filtered out.
 * S2443: the two that had grown back - the voice-note publisher and the file sender - delegate here
 * again, so this is the module's only resolver and one extension has one answer.
 */
object MediaMimeTypes {

    // MimeTypeMap's table varies by OEM and by platform version, so the formats the app cares
    // about are pinned here rather than trusted to it.
    private val FALLBACK = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
        "heic" to "image/heic",
        "heif" to "image/heic",
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "avi" to "video/avi",
        "mov" to "video/quicktime",
        "wmv" to "video/x-ms-wmv",
        "flv" to "video/x-flv",
        "3gp" to "video/3gpp",
        "mp3" to "audio/mpeg",
        // .m4a is an MPEG-4 container (RFC 4337), not a raw ADTS stream, so audio/mp4 rather than
        // audio/aac - and it is what the rest of the module already answers for a voice note.
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "flac" to "audio/flac",
        "wma" to "audio/x-ms-wma",
        "pdf" to "application/pdf",
        "epub" to "application/epub+zip"
    )

    fun fromFileName(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) {
            return null
        }
        return platformMimeType(extension) ?: FALLBACK[extension]
    }

    // The platform table is a stub outside an Android runtime and throws rather than answering, and
    // since S2443 every caller in the module reaches it through here, so the throw is absorbed once.
    private fun platformMimeType(extension: String): String? =
        try {
            MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(extension)
        } catch (e: IllegalStateException) {
            Timber.w(e, "MediaMimeTypes: MimeTypeMap unavailable for extension %s", extension)
            null
        }
}
