package com.sza.fastmediasorter.ui.player.dispatch

import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.domain.model.MediaType

/**
 * The four specialized standalone-player entry points an external intent can be routed to.
 *
 * Grouping mirrors S0380's split: images/GIF/video share one surface, audio is standalone,
 * PDF/EPUB/office documents share the document surface, plain text gets the lightest surface.
 */
enum class MediaFamily {
    PHOTO_VIDEO,
    AUDIO,
    DOCUMENT,
    TEXT,
}

/**
 * Resolves an incoming external intent to a [MediaFamily] so the dispatcher can forward typeless
 * or generic wildcard intents to the right specialized activity.
 *
 * Pure logic: it reuses [MediaTypeUtils.getMediaTypeFromMimeOrExtension] (the same detector the
 * standalone player already uses) and only groups the resulting [MediaType] into a family. It must
 * stay free of heavy media SDKs (ExoPlayer / Glide / PDF / WebView) so the dispatcher trampoline
 * never loads them just to route. The caller supplies the MIME from `ContentResolver.getType(uri)`
 * (or the intent type) and the display name / last path segment for the extension fallback.
 */
object MediaFamilyResolver {

    /** Returns the target family, or null when the type is unsupported (binary / unknown). */
    fun resolve(mimeType: String?, fileName: String?): MediaFamily? {
        val mediaType = MediaTypeUtils.getMediaTypeFromMimeOrExtension(mimeType, fileName ?: "")
            ?: return null
        return familyOf(mediaType)
    }

    /** Maps a resolved [MediaType] to its specialized-activity family, or null when unsupported. */
    fun familyOf(mediaType: MediaType): MediaFamily? = when (mediaType) {
        MediaType.IMAGE, MediaType.GIF, MediaType.VIDEO -> MediaFamily.PHOTO_VIDEO
        MediaType.AUDIO -> MediaFamily.AUDIO
        MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT -> MediaFamily.DOCUMENT
        MediaType.TEXT -> MediaFamily.TEXT
        MediaType.BINARY_ARCHIVE,
        MediaType.BINARY_DISK,
        MediaType.BINARY_EXECUTABLE,
        MediaType.BINARY_OTHER -> null
    }
}
