package com.sza.fastmediasorter.domain.usecase.streams

import javax.inject.Inject

/**
 * Shared scheme/kind classification for stream sources. Defined once and reused by both
 * [AddStreamSourceUseCase] and [ImportStreamPlaylistUseCase] so the classification body is not
 * duplicated across the manual-add and playlist-import paths.
 */
class StreamMediaKindClassifier @Inject constructor() {

    /** Only http/https/rtsp are launchable stream schemes; everything else is rejected upstream. */
    fun isSupportedScheme(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("rtsp://")
    }

    /**
     * Returns RTSP / VIDEO / AUDIO. RTSP wins on scheme; a known video/HLS/DASH extension yields
     * VIDEO; otherwise AUDIO is the radio default (audio extension or a pathless URL).
     */
    fun classify(url: String): String {
        val trimmed = url.trim()
        if (trimmed.lowercase().startsWith("rtsp://")) return RTSP
        return if (extensionOf(trimmed) in VIDEO_EXTENSIONS) VIDEO else AUDIO
    }

    /**
     * S3374 (STREAM-BANK 2.1 item M): a declared kind this app does not act on is worth exactly as much
     * as a blank one, so both degrade to the URL classifier. Storing an unrecognised value verbatim left
     * every downstream comparison to answer it by whichever side of its own test it fell on.
     */
    fun resolve(declared: String, url: String): String {
        val normalized = declared.trim().uppercase()
        return if (normalized in RECOGNISED_KINDS) normalized else classify(url)
    }

    private fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val lastSegment = path.substringAfterLast('/')
        if (!lastSegment.contains('.')) return ""
        return lastSegment.substringAfterLast('.').lowercase()
    }

    private companion object {
        const val RTSP = "RTSP"
        const val VIDEO = "VIDEO"
        const val AUDIO = "AUDIO"

        val RECOGNISED_KINDS = setOf(RTSP, VIDEO, AUDIO)
        val VIDEO_EXTENSIONS = setOf("m3u8", "mpd", "mp4", "mkv", "webm", "ts", "mov")
    }
}
