package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.domain.usecase.streams.StreamMediaKindClassifier

/**
 * Decides how the Cast engine should handle a media source by its path.
 *
 * A live stream cannot be downloaded to a temp file and served via the local proxy (the proxy path
 * used for local/network/cloud files): the stream is unbounded. Instead its URL is handed to the
 * receiver directly. RTSP is excluded because the Default Media Receiver does not accept it.
 */
sealed interface CastStreamDecision {
    /** Not a stream URL - keep the existing local/network/cloud proxy path. */
    object NotAStream : CastStreamDecision

    /** Stream protocol the Cast receiver cannot play (RTSP) - report instead of attempting. */
    object UnsupportedProtocol : CastStreamDecision

    /** http(s) stream - cast the URL directly as live with this content-type. */
    data class Direct(val contentType: String) : CastStreamDecision
}

/**
 * Maps a media path to a [CastStreamDecision]. Pure and Android-free so it is unit-testable; the
 * scheme gate is reused from [StreamMediaKindClassifier] so the stream-scheme list is not duplicated.
 */
class CastStreamResolver(
    private val classifier: StreamMediaKindClassifier = StreamMediaKindClassifier()
) {

    fun resolve(path: String): CastStreamDecision {
        if (!classifier.isSupportedScheme(path)) return CastStreamDecision.NotAStream
        if (path.trim().lowercase().startsWith("rtsp://")) return CastStreamDecision.UnsupportedProtocol
        return CastStreamDecision.Direct(contentTypeFor(path))
    }

    private fun contentTypeFor(url: String): String = when (extensionOf(url)) {
        "m3u8" -> "application/x-mpegurl"
        "mpd" -> "application/dash+xml"
        "mp4", "mov" -> "video/mp4"
        "webm" -> "video/webm"
        "ts" -> "video/mp2t"
        // WHY: most extension-less live URLs are HLS playlists; the receiver still sniffs the body,
        // but declaring HLS gives the right hint for the dominant live case.
        else -> "application/x-mpegurl"
    }

    private fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val lastSegment = path.substringAfterLast('/')
        if (!lastSegment.contains('.')) return ""
        return lastSegment.substringAfterLast('.').lowercase()
    }
}
