package com.sza.fastmediasorter.wear.domain.usecase

import javax.inject.Inject

/**
 * S1708: Scheme/kind classification for stream sources on Wear OS.
 */
class ClassifyWearStreamMediaKindUseCase @Inject constructor() {

    fun isSupportedScheme(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("rtsp://")
    }

    /**
     * Returns RTSP / VIDEO / AUDIO. RTSP wins on scheme; a known video/HLS/DASH extension yields
     * VIDEO; otherwise AUDIO is the radio default.
     */
    fun classify(url: String): String {
        val trimmed = url.trim()
        if (trimmed.lowercase().startsWith("rtsp://")) return RTSP
        return if (extensionOf(trimmed) in VIDEO_EXTENSIONS) VIDEO else AUDIO
    }

    private fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val lastSegment = path.substringAfterLast('/')
        if (!lastSegment.contains('.')) return ""
        return lastSegment.substringAfterLast('.').lowercase()
    }

    companion object {
        const val RTSP = "RTSP"
        const val VIDEO = "VIDEO"
        const val AUDIO = "AUDIO"

        val VIDEO_EXTENSIONS = setOf("m3u8", "mpd", "mp4", "mkv", "webm", "ts", "mov")
    }
}
