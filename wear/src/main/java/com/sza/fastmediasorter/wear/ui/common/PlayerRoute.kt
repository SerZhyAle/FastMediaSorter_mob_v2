package com.sza.fastmediasorter.wear.ui.common

import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes

/**
 * Which player renders a file the caller has already resolved.
 *
 * The file's own mime type decides, never the list that led to it: a folder reached under the `all`
 * entrance mixes kinds, and every other entrance already picks its player the same way. The three
 * routes are the watch's existing player entrances - a fourth would duplicate one of them.
 *
 * S1884: lifted out of `FavouritesScreen` and `PhoneResourceScreen`, which each held a private copy,
 * because a third caller was about to appear. The prefixes keep their trailing slash: a bare "image"
 * also matches a type that merely starts with those letters.
 *
 * @param fallback the media type of the list that led here, consulted only when the mime type
 * classifies nothing. Its null default preserves today's behaviour for `FavouritesScreen` and
 * `PhoneResourceScreen`, which have no screen-wide media type to fall back on and send an
 * unclassifiable file to the audio player.
 */
internal fun playerRouteFor(fileId: Long, mimeType: String?, fallback: MediaType? = null): String = when {
    mimeType?.startsWith("image/") == true -> WearRoutes.imageViewer(fileId)
    mimeType?.startsWith("video/") == true -> WearRoutes.videoPlayer(fileId)
    mimeType?.startsWith("audio/") == true -> WearRoutes.audioPlayer(fileId)
    // S2006: a document classifies positively, so it is refused rather than sent to the audio player.
    // The unclassifiable case below is a different thing and keeps its audio fallback.
    isDocument(mimeType) -> WearRoutes.UNSUPPORTED_FILE
    fallback != null -> routeForMediaType(fileId, fallback)
    else -> WearRoutes.audioPlayer(fileId)
}

/**
 * A mime type this watch has no player for at all, as opposed to one it simply could not read.
 *
 * Only a positively classified document is refused. A null or unrecognised mime type keeps the audio
 * fallback, because that branch also carries audio files whose source reported no type - refusing
 * those would turn a working case into a dead end.
 */
private fun isDocument(mimeType: String?): Boolean =
    mimeType?.startsWith("application/") == true || mimeType?.startsWith("text/") == true

private fun routeForMediaType(fileId: Long, mediaType: MediaType): String = when (mediaType) {
    MediaType.MUSIC -> WearRoutes.audioPlayer(fileId)
    MediaType.VIDEO -> WearRoutes.videoPlayer(fileId)
    MediaType.PHOTO -> WearRoutes.imageViewer(fileId)
}
