package com.sza.fastmediasorter.wear.ui.common

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
 */
internal fun playerRouteFor(fileId: Long, mimeType: String): String = when {
    mimeType.startsWith("image/") -> WearRoutes.imageViewer(fileId)
    mimeType.startsWith("video/") -> WearRoutes.videoPlayer(fileId)
    else -> WearRoutes.audioPlayer(fileId)
}
