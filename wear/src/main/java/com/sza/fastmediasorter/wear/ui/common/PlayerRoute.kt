package com.sza.fastmediasorter.wear.ui.common

import com.sza.fastmediasorter.wear.domain.documents.WearDocumentFormatPolicy
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import timber.log.Timber

private val documentFormatPolicy = WearDocumentFormatPolicy()

/**
 * Which player renders a file the caller has already resolved.
 *
 * The file's own mime type decides, never the list that led to it: a folder reached under the `all`
 * entrance mixes kinds, and every other entrance already picks its player the same way. The routes
 * here are the watch's content entrances in full - S2532 made the reader the fourth of them, and a
 * fifth would duplicate one that exists.
 *
 * S1884: lifted out of `FavouritesScreen` and `PhoneResourceScreen`, which each held a private copy,
 * because a third caller was about to appear. The prefixes keep their trailing slash: a bare "image"
 * also matches a type that merely starts with those letters.
 *
 * @param fallback the media type of the list that led here, consulted only when the mime type
 * classifies nothing. Its null default preserves today's behaviour for `FavouritesScreen` and
 * `PhoneResourceScreen`, which have no screen-wide media type to fall back on and send an
 * unclassifiable file to the audio player.
 * @param fileName the file's own name, when the caller holds one. S2532: a share reports no mime
 * type at all, so without the name a readable text file there would be classified as nothing and
 * sent to the audio player. Its null default keeps the callers that hold no name compiling and
 * behaving exactly as before.
 */
internal fun playerRouteFor(
    fileId: Long,
    mimeType: String?,
    fallback: MediaType? = null,
    fileName: String? = null
): String = when {
    mimeType?.startsWith("image/") == true -> WearRoutes.imageViewer(fileId)
    mimeType?.startsWith("video/") == true -> WearRoutes.videoPlayer(fileId)
    mimeType?.startsWith("audio/") == true -> WearRoutes.audioPlayer(fileId)
    // S2006: a document classifies positively, so it never falls through to the audio player.
    // The unclassifiable case below is a different thing and keeps its audio fallback.
    documentFormatPolicy.isDocument(mimeType, fileName) -> documentRoute(fileId, mimeType, fileName)
    fallback != null -> routeForMediaType(fileId, fallback)
    else -> WearRoutes.audioPlayer(fileId)
}

/**
 * S2532: the reader for a document the watch renders, the refusal for one it does not.
 *
 * The format is asked for twice over - once to know this is a document at all, once to name it -
 * because the two questions have different consequences and only the policy may answer either.
 */
private fun documentRoute(fileId: Long, mimeType: String?, fileName: String?): String {
    val format = documentFormatPolicy.formatFor(mimeType, fileName)
    Timber.d("S2532: document tapped mime=%s name=%s format=%s", mimeType, fileName, format)
    return if (format.readableOnWatch) {
        WearRoutes.documentViewer(fileId)
    } else {
        WearRoutes.unsupportedFile(format)
    }
}

private fun routeForMediaType(fileId: Long, mediaType: MediaType): String = when (mediaType) {
    MediaType.MUSIC -> WearRoutes.audioPlayer(fileId)
    MediaType.VIDEO -> WearRoutes.videoPlayer(fileId)
    MediaType.PHOTO -> WearRoutes.imageViewer(fileId)
}
