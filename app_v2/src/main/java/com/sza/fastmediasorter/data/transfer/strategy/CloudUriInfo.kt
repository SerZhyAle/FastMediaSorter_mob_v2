package com.sza.fastmediasorter.data.transfer.strategy

import android.net.Uri
import com.sza.fastmediasorter.data.cloud.CloudProvider
import timber.log.Timber

/** Parsed `cloud://<provider>/<id-or-path>` reference. */
internal data class CloudUriInfo(
    val provider: CloudProvider,
    val idOrPath: String,
    val segments: List<String>
) {
    val fileIdForDownload: String
        get() = segments.lastOrNull() ?: idOrPath
}

internal fun parseCloudUri(rawPath: String): CloudUriInfo? {
    val normalized = if (rawPath.startsWith("cloud:/") && !rawPath.startsWith("cloud://")) {
        rawPath.replaceFirst("cloud:/", "cloud://")
    } else {
        rawPath
    }
    if (!normalized.startsWith("cloud://")) return null

    return runCatching {
        val uri = Uri.parse(normalized)
        val provider = uri.host?.lowercase()?.let(::cloudProviderOf)
        val idOrPath = uri.path?.removePrefix("/").orEmpty()
        if (provider == null || idOrPath.isBlank()) {
            null
        } else {
            CloudUriInfo(provider, idOrPath, uri.pathSegments)
        }
    }.onFailure { Timber.e(it, "CloudUriInfo: failed to parse cloud uri: $rawPath") }.getOrNull()
}

private fun cloudProviderOf(host: String): CloudProvider? = when (host) {
    "google_drive", "googledrive", "google" -> CloudProvider.GOOGLE_DRIVE
    "dropbox" -> CloudProvider.DROPBOX
    "onedrive" -> CloudProvider.ONEDRIVE
    else -> null
}

/**
 * A single segment is ambiguous - it can be either a folder id or a file id - so it is reported as
 * "no parent" rather than guessed at.
 */
internal fun splitParentAndName(info: CloudUriInfo): Pair<String, String?> {
    if (info.segments.size < 2) return "" to null

    val parentSegments = info.segments.dropLast(1)
    val parentIdOrPath = when (info.provider) {
        CloudProvider.DROPBOX -> "/" + parentSegments.joinToString("/")
        else -> parentSegments.joinToString("/")
    }
    return parentIdOrPath to info.segments.last()
}

internal fun guessMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }
}
