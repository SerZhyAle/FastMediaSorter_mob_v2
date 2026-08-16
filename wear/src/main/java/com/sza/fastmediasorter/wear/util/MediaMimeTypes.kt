package com.sza.fastmediasorter.wear.util

import android.webkit.MimeTypeMap

/**
 * Extension-to-MIME inference for network listings.
 *
 * On a network source the file name is the only source of truth - no protocol the watch speaks
 * reports a MIME type - so every listing derives it here. S1690: this used to be written three
 * times in two different versions, and the two that lacked the fallback table dropped .mkv, .flac,
 * .heic and .wma from FTP and SFTP listings entirely, because an unresolved type is filtered out.
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
        "m4a" to "audio/aac",
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
        return if (extension.isEmpty()) {
            null
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: FALLBACK[extension]
        }
    }
}
